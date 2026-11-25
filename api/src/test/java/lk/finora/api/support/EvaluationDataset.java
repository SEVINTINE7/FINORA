package lk.finora.api.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** T55 v1: invented LKR records, not sampled bank data. No clock, randomness, IO or financial forecasts. */
public final class EvaluationDataset {
    public static final YearMonth START = YearMonth.of(2025, 12);
    public static final List<String> PROFILES = List.of("worker", "irregular");
    public record Entry(String profile, LocalDate date, String type, String category, BigDecimal amount) {}
    private EvaluationDataset() {}

    public static BigDecimal money(String amount) { return new BigDecimal(amount).setScale(2); }

    public static BigDecimal shopping(String profile, int month) {
        if (profile.equals("irregular")) return money(Integer.toString(500 + month % 3 * 500));
        return money(switch (month) { case 0 -> "0"; case 1 -> "4000"; case 2 -> "3999.99";
            case 6 -> "50000"; default -> "5000"; });
    }

    public static List<Entry> entries() {
        List<Entry> rows = new ArrayList<>();
        for (String profile : PROFILES) for (int i = 0; i < 12; i++) {
            YearMonth month = START.plusMonths(i);
            boolean worker = profile.equals("worker");
            if (!worker && i == 9) continue; // September 2026: no records, not proof of no real income/spending.
            if (worker) add(rows, profile, month.atDay(1), "INCOME", "Salary", money("60000"));
            else {
                add(rows, profile, month.atDay(3), "INCOME", "Part-time", money(Integer.toString(10000 + i * 500)));
                add(rows, profile, month.atDay(20), "INCOME", "Allowance", money(Integer.toString(7500 + i * 250)));
            }
            for (int day = 1; day <= month.lengthOfMonth(); day++)
                add(rows, profile, month.atDay(day), "EXPENSE", "Food", money(worker ? (i == 0 ? "100" : "500") : "200"));
            add(rows, profile, month.atDay(5), "EXPENSE", worker ? "Rent" : "Transport", money(worker ? "15000" : "2000"));
            add(rows, profile, month.atDay(10), "EXPENSE", worker ? "Utilities" : "Mobile/Internet", money(worker ? "2000" : "1000"));
            BigDecimal wants = shopping(profile, i);
            if (wants.signum() > 0) add(rows, profile, month.atDay(15), "EXPENSE", "Shopping", wants);
            add(rows, profile, month.atEndOfMonth(), "EXPENSE", "Savings", money(worker ? (i == 0 ? "1000" : "3000") : "500"));
            if (!worker && i == 4) add(rows, profile, month.atDay(12), "EXPENSE", null, money("99.99"));
        }
        return List.copyOf(rows);
    }

    private static void add(List<Entry> rows, String profile, LocalDate date, String type, String category, BigDecimal amount) {
        rows.add(new Entry(profile, date, type, category, amount));
    }

    /** Canonical transaction-content digest excludes database IDs, passwords and generated timestamps. */
    public static String transactionDigest() {
        return transactionDigest(entries());
    }

    static String transactionDigest(List<Entry> rows) {
        try {
            StringBuilder text = new StringBuilder("FINORA-T55-v1\n");
            for (Entry e : rows) text.append(e.profile()).append('|').append(e.date()).append('|')
                    .append(e.type()).append('|').append(e.category() == null ? "NULL" : e.category())
                    .append('|').append(e.amount().toPlainString()).append('\n');
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
}
