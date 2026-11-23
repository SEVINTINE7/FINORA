package lk.finora.api.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lk.finora.api.account.Account;
import lk.finora.api.account.AccountRepository;
import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;
import lk.finora.api.importer.dto.CsvConfirmRequest;
import lk.finora.api.importer.dto.CsvConfirmResponse;
import lk.finora.api.importer.dto.CsvConfirmRow;
import lk.finora.api.importer.dto.CsvPreviewResponse;
import lk.finora.api.importer.dto.CsvPreviewRow;
import lk.finora.api.transaction.TransactionRepository;
import lk.finora.api.transaction.TransactionService;
import lk.finora.api.transaction.dto.TransactionRequest;

/**
 * CSV import (T12, D25, template in docs/csv-template.md). Preview parses and checks a file without saving;
 * confirm saves the rows the user picked. The file itself is never stored.
 */
@Service
public class CsvImportService {

    static final long MAX_BYTES = 1024 * 1024;
    static final int MAX_ROWS = 2000;
    static final List<String> HEADER = List.of("date", "type", "amount", "category", "account", "merchant", "note");

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    // 1250 · 1250.5 · 1250.50 · 1,250.50 (grouping must be in threes)
    private static final Pattern AMOUNT = Pattern.compile("(\\d+|\\d{1,3}(,\\d{3})+)(\\.\\d{1,2})?");

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    public CsvImportService(TransactionRepository transactionRepository, TransactionService transactionService,
            AccountRepository accountRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
    }

    // ---------- preview ----------

    @Transactional(readOnly = true)
    public CsvPreviewResponse preview(Long userId, MultipartFile file) {
        List<CsvParser.Record> records = readRecords(file);

        List<Account> accounts = accountRepository.findByUserIdOrderById(userId);
        List<Category> categories = categoryRepository.findVisible(userId);

        List<CsvPreviewRow> rows = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (CsvParser.Record record : records) {
            rows.add(checkRow(record, accounts, categories, seen));
        }

        // Flag rows already saved by an earlier import
        Map<String, Integer> hashToIndex = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            CsvPreviewRow row = rows.get(i);
            if (row.errors().isEmpty()) {
                hashToIndex.put(hashOf(row.txnDate(), row.type(), row.amount(), row.accountId(), row.merchant(),
                        row.occurrence()), i);
            }
        }
        if (!hashToIndex.isEmpty()) {
            for (String existing : transactionRepository.findExistingDedupHashes(userId, hashToIndex.keySet())) {
                int i = hashToIndex.get(existing);
                CsvPreviewRow r = rows.get(i);
                rows.set(i, new CsvPreviewRow(r.line(), r.txnDate(), r.type(), r.amount(), r.accountId(),
                        r.accountName(), r.categoryId(), r.categoryName(), r.merchant(), r.note(), r.occurrence(),
                        true, r.errors()));
            }
        }

        int valid = 0;
        int duplicates = 0;
        for (CsvPreviewRow row : rows) {
            if (row.errors().isEmpty()) {
                valid++;
            }
            if (row.duplicate()) {
                duplicates++;
            }
        }
        return new CsvPreviewResponse(rows.size(), valid, rows.size() - valid, duplicates, rows);
    }

    /** Size, encoding, header and row-count checks. Returns the data records (header and blank lines removed). */
    private List<CsvParser.Record> readRecords(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw tooLarge();
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest("The file must be UTF-8 text");
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("The file could not be read");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }

        List<CsvParser.Record> all;
        try {
            all = CsvParser.parse(text);
        } catch (CsvParser.CsvFormatException e) {
            throw ApiException.badRequest(e.getMessage());
        }

        List<CsvParser.Record> data = new ArrayList<>();
        boolean headerSeen = false;
        for (CsvParser.Record record : all) {
            if (record.isBlank()) {
                continue;
            }
            if (!headerSeen) {
                checkHeader(record);
                headerSeen = true;
                continue;
            }
            data.add(record);
            if (data.size() > MAX_ROWS) {
                throw ApiException.badRequest("The file has more than " + MAX_ROWS + " rows");
            }
        }
        if (!headerSeen) {
            throw ApiException.badRequest("The file is empty");
        }
        if (data.isEmpty()) {
            throw ApiException.badRequest("The file has a header but no rows");
        }
        return data;
    }

    private void checkHeader(CsvParser.Record record) {
        List<String> names = new ArrayList<>();
        for (String cell : record.cells()) {
            names.add(cell.trim().toLowerCase(Locale.ROOT));
        }
        if (!names.equals(HEADER)) {
            throw ApiException.badRequest("The first line must be: " + String.join(",", HEADER));
        }
    }

    /** Reads one data row. Errors are collected per row so the user sees every problem at once. */
    private CsvPreviewRow checkRow(CsvParser.Record record, List<Account> accounts, List<Category> categories,
            Map<String, Integer> seen) {
        List<String> errors = new ArrayList<>();
        List<String> cells = record.cells();
        if (cells.size() != HEADER.size()) {
            errors.add("Expected " + HEADER.size() + " columns, found " + cells.size());
            return new CsvPreviewRow(record.line(), null, null, null, null, null, null, null, null, null, null,
                    false, errors);
        }

        // Formula injection: refuse the row before reading any value
        for (int c = 0; c < cells.size(); c++) {
            if (looksLikeFormula(cells.get(c))) {
                errors.add("Column '" + HEADER.get(c) + "' starts with a formula character (= + - @)");
            }
        }
        if (!errors.isEmpty()) {
            return new CsvPreviewRow(record.line(), null, null, null, null, null, null, null, null, null, null,
                    false, errors);
        }

        LocalDate date = parseDate(cells.get(0).trim(), errors);
        String type = parseType(cells.get(1).trim(), errors);
        BigDecimal amount = parseAmount(cells.get(2).trim(), errors);
        Account account = findAccount(cells.get(4).trim(), accounts, errors);
        Category category = findCategory(cells.get(3).trim(), type, categories, errors);
        String merchant = blankToNull(cells.get(5));
        String note = blankToNull(cells.get(6));
        if (merchant != null && merchant.length() > 100) {
            errors.add("Merchant is longer than 100 characters");
        }
        if (note != null && note.length() > 255) {
            errors.add("Note is longer than 255 characters");
        }

        Integer occurrence = null;
        if (errors.isEmpty()) {
            String key = identityKey(date, type, amount, account.getId(), merchant);
            occurrence = seen.merge(key, 1, Integer::sum);
        }
        return new CsvPreviewRow(record.line(), date, type, amount,
                account == null ? null : account.getId(), account == null ? null : account.getName(),
                category == null ? null : category.getId(), category == null ? null : category.getName(),
                merchant, note, occurrence, false, errors);
    }

    private LocalDate parseDate(String text, List<String> errors) {
        if (DATE.matcher(text).matches()) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                // falls through to the error below
            }
        }
        errors.add("Date must be a real date in YYYY-MM-DD form");
        return null;
    }

    private String parseType(String text, List<String> errors) {
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.equals("INCOME") || upper.equals("EXPENSE")) {
            return upper;
        }
        errors.add("Type must be INCOME or EXPENSE");
        return null;
    }

    private BigDecimal parseAmount(String text, List<String> errors) {
        if (AMOUNT.matcher(text).matches()) {
            BigDecimal amount = new BigDecimal(text.replace(",", "")).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.precision() - amount.scale() > 13) {
                errors.add("Amount is too large");
                return null;
            }
            if (amount.signum() <= 0) {
                errors.add("Amount must be more than 0");
                return null;
            }
            return amount;
        }
        errors.add("Amount must be a number like 1250.00 (at most 2 decimals, no sign)");
        return null;
    }

    private Account findAccount(String name, List<Account> accounts, List<String> errors) {
        if (name.isEmpty()) {
            errors.add("Account is required");
            return null;
        }
        Account match = null;
        for (Account account : accounts) {
            if (account.getName().equals(name)) {
                match = account;
                break;
            }
            if (match == null && account.getName().equalsIgnoreCase(name)) {
                match = account;
            }
        }
        if (match == null) {
            errors.add("Account '" + name + "' not found");
            return null;
        }
        if (match.isArchived()) {
            errors.add("Account '" + match.getName() + "' is archived");
            return null;
        }
        return match;
    }

    /** Optional. Matches the English or Sinhala name; a category of the row's type wins over one of the other. */
    private Category findCategory(String name, String type, List<Category> categories, List<String> errors) {
        if (name.isEmpty()) {
            return null;
        }
        Category sameKind = null;
        Category otherKind = null;
        for (Category category : categories) {
            boolean nameMatches = category.getName().equalsIgnoreCase(name)
                    || (category.getNameSi() != null && category.getNameSi().equals(name));
            if (!nameMatches) {
                continue;
            }
            if (type != null && category.getKind().name().equals(type)) {
                sameKind = sameKind == null ? category : sameKind;
            } else if (otherKind == null) {
                otherKind = category;
            }
        }
        if (sameKind != null) {
            return sameKind;
        }
        if (otherKind != null && type != null) {
            errors.add("Category '" + otherKind.getName() + "' is for " + otherKind.getKind() + ", not " + type);
        } else if (otherKind == null) {
            errors.add("Category '" + name + "' not found");
        }
        return null;
    }

    // ---------- confirm ----------

    /**
     * Saves the selected rows. Every row is checked again (same rules as POST /transactions); if any row is
     * invalid nothing is saved. Rows whose hash already exists, or that repeat in this request, are skipped.
     */
    @Transactional
    public CsvConfirmResponse confirm(Long userId, CsvConfirmRequest request) {
        List<String> hashes = new ArrayList<>();
        for (CsvConfirmRow row : request.rows()) {
            String formulaColumn = formulaColumn(row);
            if (formulaColumn != null) {
                throw ApiException.badRequest("Line " + row.line() + ": " + formulaColumn
                        + " starts with a formula character (= + - @)");
            }
            hashes.add(hashOf(row.txnDate(), row.type(), row.amount(), row.accountId(), blankToNull(row.merchant()),
                    row.occurrence()));
        }

        // Check every row before saving any, so one bad row saves nothing
        List<TransactionRequest> requests = new ArrayList<>();
        for (CsvConfirmRow row : request.rows()) {
            TransactionRequest txn = new TransactionRequest(row.accountId(), row.categoryId(), row.type(),
                    row.amount(), row.txnDate(), row.merchant(), row.note(), "CSV");
            try {
                transactionService.checkNew(userId, txn);
            } catch (ApiException e) {
                // Same check failed as for a single record; name the line so the app can point at it
                throw new ApiException(e.getCode(), e.getStatus(), "Line " + row.line() + ": " + e.getMessage());
            }
            requests.add(txn);
        }

        Set<String> taken = new HashSet<>(transactionRepository.findExistingDedupHashes(userId, new HashSet<>(hashes)));
        int saved = 0;
        List<Integer> skipped = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            String hash = hashes.get(i);
            if (!taken.add(hash)) {
                skipped.add(request.rows().get(i).line());
                continue;
            }
            transactionService.createImported(userId, requests.get(i), hash);
            saved++;
        }
        return new CsvConfirmResponse(saved, skipped);
    }

    private String formulaColumn(CsvConfirmRow row) {
        if (looksLikeFormula(row.merchant())) {
            return "merchant";
        }
        if (looksLikeFormula(row.note())) {
            return "note";
        }
        return null;
    }

    // ---------- shared helpers ----------

    /** A cell a spreadsheet could run as a formula (OWASP CSV injection list). */
    static boolean looksLikeFormula(String cell) {
        if (cell == null || cell.isEmpty()) {
            return false;
        }
        char first = cell.charAt(0);
        if (first == '\t' || first == '\r') {
            return true;
        }
        String trimmed = cell.stripLeading();
        if (trimmed.isEmpty()) {
            return false;
        }
        char c = trimmed.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@';
    }

    /** The fields that make two rows "the same record" (D25). Merchant compares case-insensitively. */
    private static String identityKey(LocalDate date, String type, BigDecimal amount, Long accountId,
            String merchant) {
        String merchantKey = merchant == null ? "" : merchant.trim().toLowerCase(Locale.ROOT);
        return date + "|" + type + "|" + amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString() + "|"
                + accountId + "|" + merchantKey;
    }

    /** dedup_hash = SHA-256 hex of "CSV|date|type|amount|accountId|merchant|n" (D25). */
    static String hashOf(LocalDate date, String type, BigDecimal amount, Long accountId, String merchant,
            int occurrence) {
        String input = "CSV|" + identityKey(date, type, amount, accountId, merchant) + "|" + occurrence;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static ApiException tooLarge() {
        return new ApiException(ErrorCodes.FILE_TOO_LARGE, HttpStatus.CONTENT_TOO_LARGE,
                "The file is larger than 1 MB");
    }
}
