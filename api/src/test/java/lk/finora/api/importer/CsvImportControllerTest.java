package lk.finora.api.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** T12: CSV preview and confirm — valid file, bad values, limits, formula cells, duplicates, isolation. */
class CsvImportControllerTest extends IntegrationTest {

    private static final String HEADER = "date,type,amount,category,account,merchant,note\n";
    private static final String PREVIEW = "/api/v1/imports/csv/preview";
    private static final String CONFIRM = "/api/v1/imports/csv/confirm";

    @Autowired
    private JdbcTemplate jdbc;

    private User userA;
    private User userB;
    private Long cashA;

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
        cashA = createAccount(userA, "Cash");
        createAccount(userB, "Bank B");
    }

    private Long createAccount(User user, String name) {
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, ?, 'CASH')", user.getId(), name);
        return jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ? AND name = ?", Long.class,
                user.getId(), name);
    }

    private ResultActions preview(User user, String csv) throws Exception {
        return preview(user, csv.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions preview(User user, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.csv", "text/csv", bytes);
        return mockMvc.perform(multipart(PREVIEW).file(file).header("Authorization", "Bearer " + tokenFor(user)));
    }

    private JsonNode previewJson(User user, String csv) throws Exception {
        String body = preview(user, csv).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body);
    }

    private ResultActions confirm(User user, Object body) throws Exception {
        return mockMvc.perform(post(CONFIRM).header("Authorization", "Bearer " + tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    /** What the app does: send back every valid, non-duplicate preview row. */
    private ObjectNode confirmBodyFrom(JsonNode preview) {
        ObjectNode body = jsonMapper.createObjectNode();
        ArrayNode rows = body.putArray("rows");
        for (JsonNode row : preview.get("rows")) {
            if (row.get("errors").isEmpty() && !row.get("duplicate").asBoolean()) {
                ObjectNode r = rows.addObject();
                for (String f : List.of("line", "txnDate", "type", "amount", "accountId", "categoryId", "merchant",
                        "note", "occurrence")) {
                    r.set(f, row.get(f));
                }
            }
        }
        return body;
    }

    private int countCsvRecords(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ? AND source = 'CSV'",
                Integer.class, user.getId());
    }

    // ---------- valid file ----------

    @Test
    void validFilePreviewsThenSaves() throws Exception {
        String csv = "﻿" + HEADER
                + "2026-11-01,INCOME,85000.00,Salary,Bank B,,November salary\n" // not A's account
                + "2026-11-05,EXPENSE,1250.75,food,cash,Keells,\r\n"
                + "2026-11-06,expense,\"2,400.00\",Transport,Cash,PickMe,\"Airport, return\"\n"
                + "\n"
                + "2026-11-07,INCOME,500,,Cash,,\n";

        JsonNode result = previewJson(userA, csv);
        assertThat(result.get("totalRows").asInt()).isEqualTo(4);
        assertThat(result.get("validRows").asInt()).isEqualTo(3);
        assertThat(result.get("errorRows").asInt()).isEqualTo(1);
        assertThat(result.get("duplicateRows").asInt()).isEqualTo(0);

        JsonNode rows = result.get("rows");
        // Another user's account name is simply not found
        assertThat(rows.get(0).get("errors").get(0).asString()).isEqualTo("Account 'Bank B' not found");
        assertThat(rows.get(1).get("line").asInt()).isEqualTo(3);
        assertThat(rows.get(1).get("accountId").asLong()).isEqualTo(cashA);
        assertThat(rows.get(1).get("categoryName").asString()).isEqualTo("Food");
        assertThat(rows.get(2).get("amount").decimalValue()).isEqualByComparingTo("2400.00");
        assertThat(rows.get(2).get("note").asString()).isEqualTo("Airport, return");
        assertThat(rows.get(3).get("line").asInt()).isEqualTo(6); // blank line 5 skipped
        assertThat(rows.get(3).get("categoryId").isNull()).isTrue();

        // Preview saved nothing
        assertThat(countCsvRecords(userA)).isZero();

        confirm(userA, confirmBodyFrom(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(3))
                .andExpect(jsonPath("$.skippedDuplicateLines").isEmpty());

        assertThat(countCsvRecords(userA)).isEqualTo(3);
        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT amount, merchant, dedup_hash FROM transactions WHERE user_id = ? AND txn_date = '2026-11-05'",
                userA.getId());
        assertThat(saved.get("amount").toString()).isEqualTo("1250.75");
        assertThat(saved.get("dedup_hash").toString()).hasSize(64);
    }

    // ---------- bad values ----------

    @Test
    void badDatesAndAmountsAreRowErrors() throws Exception {
        String csv = HEADER
                + "2026-02-30,EXPENSE,100.00,,Cash,,\n"  // no such day
                + "05/11/2026,EXPENSE,100.00,,Cash,,\n"  // wrong format
                + "2026-11-05,EXPENSE,10.555,,Cash,,\n"  // 3 decimals
                + "2026-11-05,EXPENSE,0.00,,Cash,,\n"    // zero
                + "2026-11-05,EXPENSE,abc,,Cash,,\n"     // not a number
                + "2026-11-05,EXPENSE,Rs.100,,Cash,,\n"  // currency symbol
                + "2026-11-05,TRANSFER,100.00,,Cash,,\n" // bad type
                + "2026-11-05,EXPENSE,100.00,Salary,Cash,,\n" // income category on an expense
                + "2026-11-05,EXPENSE,100.00,,Cash\n";   // too few columns

        JsonNode rows = previewJson(userA, csv).get("rows");
        assertThat(rows.get(0).get("errors").get(0).asString()).startsWith("Date must be");
        assertThat(rows.get(1).get("errors").get(0).asString()).startsWith("Date must be");
        assertThat(rows.get(2).get("errors").get(0).asString()).startsWith("Amount must be a number");
        assertThat(rows.get(3).get("errors").get(0).asString()).isEqualTo("Amount must be more than 0");
        assertThat(rows.get(4).get("errors").get(0).asString()).startsWith("Amount must be a number");
        assertThat(rows.get(5).get("errors").get(0).asString()).startsWith("Amount must be a number");
        assertThat(rows.get(6).get("errors").get(0).asString()).isEqualTo("Type must be INCOME or EXPENSE");
        assertThat(rows.get(7).get("errors").get(0).asString()).isEqualTo("Category 'Salary' is for INCOME, not EXPENSE");
        assertThat(rows.get(8).get("errors").get(0).asString()).isEqualTo("Expected 7 columns, found 5");
        for (JsonNode row : rows) {
            assertThat(row.get("occurrence").isNull()).isTrue();
        }
    }

    @Test
    void badFilesAreRejected() throws Exception {
        preview(userA, "day,kind,amount\n2026-11-05,EXPENSE,1\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("The first line must be: " + HEADER.trim()));
        preview(userA, HEADER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file has a header but no rows"));
        preview(userA, new byte[0]).andExpect(status().isBadRequest());
        preview(userA, HEADER + "2026-11-05,EXPENSE,1,,Cash,\"Keells,\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Line 2: a quoted cell is never closed"));
        // Latin-1 byte that is not valid UTF-8
        preview(userA, (HEADER + "2026-11-05,EXPENSE,1,,Cash,Café,\n").getBytes(StandardCharsets.ISO_8859_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file must be UTF-8 text"));
    }

    // ---------- limits ----------

    @Test
    void oversizeFileGives413() throws Exception {
        byte[] big = new byte[(int) CsvImportService.MAX_BYTES + 1];
        java.util.Arrays.fill(big, (byte) 'a');
        preview(userA, big)
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void moreThan2000RowsIsRejected() throws Exception {
        StringBuilder ok = new StringBuilder(HEADER);
        for (int i = 0; i < CsvImportService.MAX_ROWS; i++) {
            ok.append("2026-11-05,EXPENSE,1.00,,Cash,,\n");
        }
        preview(userA, ok.toString()).andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(2000));

        ok.append("2026-11-05,EXPENSE,1.00,,Cash,,\n");
        preview(userA, ok.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file has more than 2000 rows"));
    }

    // ---------- formula injection ----------

    @Test
    void formulaCellsAreRejected() throws Exception {
        String csv = HEADER
                + "2026-11-05,EXPENSE,100.00,,Cash,=HYPERLINK(\"x\"),\n"
                + "2026-11-05,EXPENSE,100.00,,Cash,,+cmd\n"
                + "2026-11-05,EXPENSE,-100.00,,Cash,,\n"
                + "2026-11-05,EXPENSE,100.00,,Cash,  @SUM(A1),\n"
                + "2026-11-05,EXPENSE,100.00,,Cash,\"\tx\",\n"
                + "2026-11-05,EXPENSE,100.00,,Cash,Keells,a-b=c\n"; // fine: not at the start

        JsonNode result = previewJson(userA, csv);
        JsonNode rows = result.get("rows");
        assertThat(rows.get(0).get("errors").get(0).asString()).contains("'merchant'").contains("formula");
        assertThat(rows.get(1).get("errors").get(0).asString()).contains("'note'");
        assertThat(rows.get(2).get("errors").get(0).asString()).contains("'amount'");
        assertThat(rows.get(3).get("errors").get(0).asString()).contains("'merchant'");
        assertThat(rows.get(4).get("errors").get(0).asString()).contains("'merchant'");
        assertThat(rows.get(5).get("errors").isEmpty()).isTrue();
        assertThat(result.get("validRows").asInt()).isEqualTo(1);

        // Confirm checks again: a formula sent straight to confirm is refused and nothing is saved
        ObjectNode body = confirmBodyFrom(result);
        ((ObjectNode) body.get("rows").get(0)).put("merchant", "=cmd|' /C calc'!A0");
        confirm(userA, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Line 7: merchant starts with a formula character (= + - @)"));
        assertThat(countCsvRecords(userA)).isZero();
    }

    // ---------- duplicates ----------

    @Test
    void reimportFlagsDuplicates() throws Exception {
        String csv = HEADER
                + "2026-11-05,EXPENSE,450.00,Food,Cash,Cafe,\n"
                + "2026-11-05,EXPENSE,450.00,Food,Cash,CAFE,second coffee\n" // same record, 2nd time
                + "2026-11-06,EXPENSE,450.00,Food,Cash,Cafe,\n";

        JsonNode first = previewJson(userA, csv);
        assertThat(first.get("rows").get(0).get("occurrence").asInt()).isEqualTo(1);
        assertThat(first.get("rows").get(1).get("occurrence").asInt()).isEqualTo(2);
        assertThat(first.get("duplicateRows").asInt()).isZero();
        confirm(userA, confirmBodyFrom(first)).andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(3));

        // Same file again: every row is flagged
        JsonNode second = previewJson(userA, csv);
        assertThat(second.get("duplicateRows").asInt()).isEqualTo(3);
        for (JsonNode row : second.get("rows")) {
            assertThat(row.get("duplicate").asBoolean()).isTrue();
        }

        // A file with one more identical coffee: only the new one is not a duplicate
        JsonNode third = previewJson(userA, csv + "2026-11-05,EXPENSE,450.00,Food,Cash,Cafe,\n");
        assertThat(third.get("duplicateRows").asInt()).isEqualTo(3);
        assertThat(third.get("rows").get(3).get("duplicate").asBoolean()).isFalse();
        assertThat(third.get("rows").get(3).get("occurrence").asInt()).isEqualTo(3);

        // Sending already-imported rows to confirm skips them instead of failing
        ObjectNode resend = confirmBodyFrom(first);
        confirm(userA, resend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(0))
                .andExpect(jsonPath("$.skippedDuplicateLines.length()").value(3));
        assertThat(countCsvRecords(userA)).isEqualTo(3);

        // Another user importing the same rows is not a duplicate for them
        createAccount(userB, "Cash");
        assertThat(previewJson(userB, csv).get("duplicateRows").asInt()).isZero();
    }

    // ---------- confirm checks ----------

    @Test
    void confirmRejectsOtherUsersAccountAndSavesNothing() throws Exception {
        JsonNode result = previewJson(userA, HEADER
                + "2026-11-05,EXPENSE,100.00,,Cash,,\n"
                + "2026-11-06,EXPENSE,200.00,,Cash,,\n");
        ObjectNode body = confirmBodyFrom(result);
        Long accountB = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ?", Long.class, userB.getId());
        ((ObjectNode) body.get("rows").get(1)).put("accountId", accountB);

        confirm(userA, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Line 3: Account not found"));
        assertThat(countCsvRecords(userA)).isZero();
    }

    @Test
    void confirmValidatesBody() throws Exception {
        confirm(userA, Map.of("rows", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> row = new java.util.HashMap<>();
        row.put("line", 2);
        row.put("txnDate", "2026-11-05");
        row.put("type", "EXPENSE");
        row.put("amount", "10.555");
        row.put("accountId", cashA);
        row.put("occurrence", 1);
        confirm(userA, Map.of("rows", List.of(row)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("rows[0].amount"));

        List<Object> tooMany = new ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            tooMany.add(row);
        }
        confirm(userA, Map.of("rows", tooMany)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyUsersCanImport() throws Exception {
        User admin = createUser(Role.ADMIN);
        preview(admin, HEADER + "2026-11-05,EXPENSE,1.00,,Cash,,\n").andExpect(status().isForbidden());
        mockMvc.perform(multipart(PREVIEW).file(new MockMultipartFile("file", "a.csv", "text/csv", new byte[1])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previewScopesNamesAndConfirmRejectsForeignCategoryAtomically() throws Exception {
        jdbc.update("INSERT INTO categories(user_id,name,kind) VALUES (?, 'Synthetic private category', 'EXPENSE')",
                userB.getId());
        var ownPreview = previewJson(userB, HEADER
                + "2026-11-05,EXPENSE,101.00,synthetic private category,bank b,,\n");
        assertThat(ownPreview.get("validRows").asInt()).isEqualTo(1);
        long foreign = ownPreview.get("rows").get(0).get("categoryId").asLong();
        confirm(userB, confirmBodyFrom(ownPreview)).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1));

        var callerPreview = previewJson(userA, HEADER
                + "2026-11-05,EXPENSE,101.00,Synthetic private category,Bank B,,\n"
                + "2026-11-06,EXPENSE,102.00,food,cash,,\n");
        var rejected = callerPreview.get("rows").get(0);
        assertThat(rejected.get("accountId").isNull()).isTrue();
        assertThat(rejected.get("categoryId").isNull()).isTrue();
        assertThat(rejected.get("errors").get(0).asString()).isEqualTo("Account 'Bank B' not found");
        assertThat(rejected.get("errors").get(1).asString()).isEqualTo("Category 'Synthetic private category' not found");
        assertThat(callerPreview.get("rows").get(1).get("categoryName").asString()).isEqualTo("Food");

        var cleanPreview = previewJson(userA, HEADER
                + "2026-11-06,EXPENSE,102.00,Food,Cash,,\n"
                + "2026-11-07,EXPENSE,103.00,Food,Cash,,\n");
        var envelope = confirmBodyFrom(cleanPreview);
        var changedRow = (ObjectNode) envelope.get("rows").get(1);
        changedRow.put("categoryId", foreign);
        String scope = "SELECT * FROM transactions WHERE user_id IN (?,?) ORDER BY id";
        var before = jdbc.queryForList(scope, userA.getId(), userB.getId());
        confirm(userA, envelope).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Line 3: Category not found"));
        long food = cleanPreview.get("rows").get(1).get("categoryId").asLong();
        changedRow.put("categoryId", food);
        long archived = createAccount(userA, "Synthetic archived account");
        jdbc.update("UPDATE accounts SET archived=TRUE WHERE id=?", archived);
        long foreignAccount = ownPreview.get("rows").get(0).get("accountId").asLong();
        for (long accountId : new long[]{foreignAccount, Long.MAX_VALUE, archived}) {
            changedRow.put("accountId", accountId);
            confirm(userA, envelope).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(accountId == archived
                            ? "Line 3: This account is archived" : "Line 3: Account not found"));
        }
        assertThat(jdbc.queryForList(scope, userA.getId(), userB.getId())).isEqualTo(before);
        assertThat(countCsvRecords(userA)).isZero();
        assertThat(countCsvRecords(userB)).isEqualTo(1);
        confirm(userA, confirmBodyFrom(cleanPreview)).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(2));
    }

}
