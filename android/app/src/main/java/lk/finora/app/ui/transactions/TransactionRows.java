package lk.finora.app.ui.transactions;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.TransactionRepository.Row;
import lk.finora.app.databinding.ItemTransactionRowBinding;
import lk.finora.app.ui.CategoryIcons;
import lk.finora.app.ui.Money;

/** T62 shared transaction row: category icon, title, meta line and signed LKR amount. */
public final class TransactionRows {
    /** Segment of a grouped card, so consecutive rows read as one rounded card. */
    public enum Position { SINGLE, TOP, MIDDLE, BOTTOM }

    private TransactionRows() {}

    public static void bind(ItemTransactionRowBinding view, Row row, CategoryDto category, String meta,
                            Position position) {
        Context context = view.getRoot().getContext();
        boolean income = "INCOME".equals(row.type());
        String categoryName = category == null ? null : displayName(context, category);
        String title = row.merchant() != null && !row.merchant().isBlank() ? row.merchant()
                : categoryName != null ? categoryName
                : context.getString(income ? R.string.category_income : R.string.category_expense);
        view.rowTitle.setText(title);
        view.rowMeta.setText(meta);
        view.rowMeta.setVisibility(meta == null || meta.isEmpty() ? View.GONE : View.VISIBLE);
        view.rowAmount.setText(Money.signed(context, row.amount(), income));
        view.rowAmount.setTextColor(context.getColor(income ? R.color.finora_green : R.color.finora_ink));
        view.rowIcon.setImageResource(CategoryIcons.forCategory(category == null ? null : category.name(), income));
        view.rowIcon.setBackgroundTintList(ColorStateList.valueOf(context.getColor(
                income ? R.color.finora_success_surface : R.color.finora_error_surface)));
        view.rowIcon.setImageTintList(ColorStateList.valueOf(context.getColor(
                income ? R.color.finora_green : R.color.finora_error)));
        String spokenAmount = context.getString(income ? R.string.dashboard_income : R.string.dashboard_expense,
                Money.amount(row.amount()));
        view.getRoot().setContentDescription(meta == null || meta.isEmpty()
                ? context.getString(R.string.transaction_card_details, title, spokenAmount)
                : context.getString(R.string.transaction_row_spoken, title, meta, spokenAmount));
        view.getRoot().setBackgroundResource(switch (position) {
            case SINGLE -> R.drawable.finora_row_single;
            case TOP -> R.drawable.finora_row_top;
            case MIDDLE -> R.drawable.finora_row_middle;
            case BOTTOM -> R.drawable.finora_row_bottom;
        });
    }

    public static Position position(int index, int count) {
        if (count == 1) return Position.SINGLE;
        if (index == 0) return Position.TOP;
        return index == count - 1 ? Position.BOTTOM : Position.MIDDLE;
    }

    public static CategoryDto category(List<CategoryDto> categories, Long id) {
        if (id == null || categories == null) return null;
        for (var category : categories) if (category.id() == id) return category;
        return null;
    }

    public static AccountDto account(List<AccountDto> accounts, long id) {
        if (accounts == null) return null;
        for (var account : accounts) if (account.id() == id) return account;
        return null;
    }

    public static String displayName(Context context, CategoryDto category) {
        boolean sinhala = "si".equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage());
        return sinhala && category.nameSi() != null && !category.nameSi().isBlank()
                ? category.nameSi() : category.name();
    }

    /** "Today", "Yesterday" or a short localized date such as "Wed, 25 Nov". */
    public static String day(Context context, String isoDate, LocalDate today) {
        LocalDate date = LocalDate.parse(isoDate);
        if (date.equals(today)) return context.getString(R.string.records_today);
        if (date.equals(today.minusDays(1))) return context.getString(R.string.records_yesterday);
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        String pattern = date.getYear() == today.getYear() ? "EEE, d MMM" : "EEE, d MMM yyyy";
        return date.format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    /** A full localized date for the form's date field, e.g. "28 Nov 2026". */
    public static String fullDate(Context context, LocalDate date) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
    }
}
