package lk.finora.app.ui;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class FinancialDatePicker {
    private static final String TAG = "financial_date_picker";
    private static final String RANGE_TAG = "financial_range_picker";

    private FinancialDatePicker() {}

    /** Register before activity creation so restored picker windows are protected as well. */
    public static void protect(AppCompatActivity host) {
        host.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override public void onFragmentViewCreated(FragmentManager manager, Fragment fragment,
                                                                 View view, Bundle state) {
                        if (fragment instanceof MaterialDatePicker<?> picker
                                && (TAG.equals(picker.getTag()) || RANGE_TAG.equals(picker.getTag()))) {
                            picker.requireDialog().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                        }
                    }
                }, false);
    }

    public static void restore(AppCompatActivity host, Consumer<LocalDate> select) {
        var existing = host.getSupportFragmentManager().findFragmentByTag(TAG);
        if (existing instanceof MaterialDatePicker<?> picker) {
            @SuppressWarnings("unchecked")
            var datePicker = (MaterialDatePicker<Long>) picker;
            bind(datePicker, select);
        }
    }

    public static void show(AppCompatActivity host, int title, LocalDate current, Consumer<LocalDate> select) {
        FragmentManager fragments = host.getSupportFragmentManager();
        if (fragments.isStateSaved() || fragments.findFragmentByTag(TAG) != null) return;
        var bounds = new CalendarConstraints.Builder().setStart(utc(LocalDate.of(1, 1, 1)))
                .setEnd(utc(LocalDate.of(9999, 12, 31))).setOpenAt(utc(current)).build();
        var picker = MaterialDatePicker.Builder.datePicker().setTitleText(title)
                .setSelection(utc(current)).setCalendarConstraints(bounds).build();
        bind(picker, select);
        picker.showNow(fragments, TAG);
    }

    private static void bind(MaterialDatePicker<Long> picker, Consumer<LocalDate> select) {
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(instant ->
                select.accept(Instant.ofEpochMilli(instant).atOffset(ZoneOffset.UTC).toLocalDate()));
    }

    private static long utc(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /** T62: a protected date-range picker for the Records filter; both ends are inclusive. */
    public static void showRange(AppCompatActivity host, int title, LocalDate from, LocalDate to,
                                 BiConsumer<LocalDate, LocalDate> select) {
        FragmentManager fragments = host.getSupportFragmentManager();
        if (fragments.isStateSaved() || fragments.findFragmentByTag(RANGE_TAG) != null) return;
        var bounds = new CalendarConstraints.Builder().setStart(utc(LocalDate.of(1, 1, 1)))
                .setEnd(utc(LocalDate.of(9999, 12, 31))).setOpenAt(utc(to == null ? LocalDate.now() : to)).build();
        var builder = MaterialDatePicker.Builder.dateRangePicker().setTitleText(title).setCalendarConstraints(bounds);
        if (from != null && to != null) builder.setSelection(androidx.core.util.Pair.create(utc(from), utc(to)));
        var picker = builder.build();
        bindRange(picker, select);
        picker.showNow(fragments, RANGE_TAG);
    }

    public static void restoreRange(AppCompatActivity host, BiConsumer<LocalDate, LocalDate> select) {
        var existing = host.getSupportFragmentManager().findFragmentByTag(RANGE_TAG);
        if (existing instanceof MaterialDatePicker<?> picker) {
            @SuppressWarnings("unchecked")
            var rangePicker = (MaterialDatePicker<androidx.core.util.Pair<Long, Long>>) picker;
            bindRange(rangePicker, select);
        }
    }

    private static void bindRange(MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker,
                                  BiConsumer<LocalDate, LocalDate> select) {
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(range -> {
            if (range == null || range.first == null || range.second == null) return;
            select.accept(Instant.ofEpochMilli(range.first).atOffset(ZoneOffset.UTC).toLocalDate(),
                    Instant.ofEpochMilli(range.second).atOffset(ZoneOffset.UTC).toLocalDate());
        });
    }
}
