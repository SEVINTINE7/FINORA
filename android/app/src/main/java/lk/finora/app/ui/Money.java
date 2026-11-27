package lk.finora.app.ui;

import android.content.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import lk.finora.app.R;

/**
 * T62/D65 display formatting for LKR amounts. Values stay exact {@link BigDecimal}s; only grouping
 * separators and the "Rs." prefix are added for display. Never round a stored amount.
 */
public final class Money {
    static final char MINUS = '−';

    private Money() {}

    /** {@code 85000} → {@code 85,000.00}; keeps every stored decimal place beyond two. */
    public static String amount(BigDecimal value) {
        BigDecimal abs = value.abs();
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setMaximumFractionDigits(Math.max(2, abs.scale()));
        format.setRoundingMode(RoundingMode.UNNECESSARY);
        String digits = format.format(abs);
        return value.signum() < 0 ? MINUS + digits : digits;
    }

    /** {@code 32660} → {@code 32.7k}, {@code -3200} → {@code −3.2k}, {@code 850} → {@code 850}. Chart labels only. */
    public static String compact(BigDecimal value) {
        BigDecimal abs = value.abs();
        String digits;
        if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            digits = abs.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP).stripTrailingZeros()
                    .toPlainString() + "M";
        } else if (abs.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            digits = abs.divide(BigDecimal.valueOf(1_000), 1, RoundingMode.HALF_UP).stripTrailingZeros()
                    .toPlainString() + "k";
        } else {
            digits = abs.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        return value.signum() < 0 ? MINUS + digits : digits;
    }

    /** {@code Rs. 85,000.00}; a negative value keeps its sign after the prefix. */
    public static String rupees(Context context, BigDecimal value) {
        return rupees(context.getString(R.string.form_rupees_prefix), value);
    }

    static String rupees(String prefix, BigDecimal value) {
        return prefix.trim() + " " + amount(value);
    }

    /** {@code + Rs. 85,000.00} for income, {@code − Rs. 2,340.00} for an expense amount. */
    public static String signed(Context context, BigDecimal amount, boolean income) {
        return signed(context.getString(R.string.form_rupees_prefix), amount, income);
    }

    static String signed(String prefix, BigDecimal amount, boolean income) {
        return (income ? "+ " : MINUS + " ") + rupees(prefix, amount.abs());
    }

    /** A net value: {@code + Rs. 84,320.00} or {@code − Rs. 3,190.00}; zero has no sign. */
    public static String net(Context context, BigDecimal value) {
        return net(context.getString(R.string.form_rupees_prefix), value);
    }

    static String net(String prefix, BigDecimal value) {
        return value.signum() == 0 ? rupees(prefix, value) : signed(prefix, value, value.signum() > 0);
    }
}
