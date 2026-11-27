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
import java.util.function.Consumer;

public final class FinancialDatePicker {
    private static final String TAG = "financial_date_picker";

    private FinancialDatePicker() {}

    /** Register before activity creation so restored picker windows are protected as well. */
    public static void protect(AppCompatActivity host) {
        host.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override public void onFragmentViewCreated(FragmentManager manager, Fragment fragment,
                                                                 View view, Bundle state) {
                        if (fragment instanceof MaterialDatePicker<?> picker && TAG.equals(picker.getTag())) {
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
}
