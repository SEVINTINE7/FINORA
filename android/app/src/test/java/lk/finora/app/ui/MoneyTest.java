package lk.finora.app.ui;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

public final class MoneyTest {
    private static final String PREFIX = "Rs. ";

    @Test public void groupsThousandsWithTwoDecimals() {
        assertEquals("85,000.00", Money.amount(new BigDecimal("85000")));
        assertEquals("1,250.75", Money.amount(new BigDecimal("1250.75")));
        assertEquals("0.00", Money.amount(BigDecimal.ZERO));
        assertEquals("999.50", Money.amount(new BigDecimal("999.5")));
        assertEquals("9,999,999,999,999.99", Money.amount(new BigDecimal("9999999999999.99")));
    }

    @Test public void neverRoundsAStoredAmount() {
        assertEquals("12.345", Money.amount(new BigDecimal("12.345")));
    }

    @Test public void negativeUsesUnicodeMinus() {
        assertEquals("−3,200.00", Money.amount(new BigDecimal("-3200")));
    }

    @Test public void rupeesAndSignedAmounts() {
        assertEquals("Rs. 85,000.00", Money.rupees(PREFIX, new BigDecimal("85000.00")));
        assertEquals("+ Rs. 85,000.00", Money.signed(PREFIX, new BigDecimal("85000.00"), true));
        assertEquals("− Rs. 2,340.00", Money.signed(PREFIX, new BigDecimal("2340.00"), false));
        assertEquals("− Rs. 2,340.00", Money.signed(PREFIX, new BigDecimal("-2340.00"), false));
    }

    @Test public void netShowsSignOnlyWhenNonZero() {
        assertEquals("+ Rs. 84,320.00", Money.net(PREFIX, new BigDecimal("84320.00")));
        assertEquals("− Rs. 3,190.00", Money.net(PREFIX, new BigDecimal("-3190.00")));
        assertEquals("Rs. 0.00", Money.net(PREFIX, new BigDecimal("0.00")));
    }

    @Test public void compactChartLabels() {
        assertEquals("32.7k", Money.compact(new BigDecimal("32660.00")));
        assertEquals("−3.2k", Money.compact(new BigDecimal("-3200")));
        assertEquals("850", Money.compact(new BigDecimal("850.40")));
        assertEquals("1k", Money.compact(new BigDecimal("1000")));
        assertEquals("1.3M", Money.compact(new BigDecimal("1250000")));
    }
}
