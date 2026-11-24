package lk.finora.app.ui.transactions;

import java.math.BigDecimal;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.TransactionRepository.Edit;

final class TransactionFormValidator {
    enum Field { ACCOUNT, CATEGORY, TYPE, AMOUNT, DATE, MERCHANT, NOTE }

    record Draft(Long account, Long category, String type, String amount, String date,
                 String merchant, String note) {}

    record Validation(Edit request, Map<Field, Integer> errors) {}

    static Validation validate(Draft draft, char separator, List<AccountDto> accounts,
            List<CategoryDto> categories, Long originalAccount) {
        Map<Field, Integer> errors = new EnumMap<>(Field.class);
        if (!accounts.stream().anyMatch(account -> Objects.equals(draft.account(), account.id())
                && isSelectable(account, originalAccount))) {
            errors.put(Field.ACCOUNT, R.string.transaction_invalid_selection);
        }
        if (!"INCOME".equals(draft.type()) && !"EXPENSE".equals(draft.type())) {
            errors.put(Field.TYPE, R.string.transaction_required);
        }
        if (draft.category() != null && !categories.stream().anyMatch(category ->
                Objects.equals(draft.category(), category.id()) && Objects.equals(draft.type(), category.kind()))) {
            errors.put(Field.CATEGORY, R.string.transaction_category_mismatch);
        }
        BigDecimal amount = parseAmount(draft.amount(), separator);
        if (amount == null) errors.put(Field.AMOUNT, R.string.transaction_amount_invalid);
        String date = null;
        try {
            date = TransactionInput.date(draft.date());
        } catch (DateTimeParseException invalid) {
            errors.put(Field.DATE, R.string.transaction_required);
        }
        if (draft.merchant().length() > 100) errors.put(Field.MERCHANT, R.string.transaction_merchant_too_long);
        if (draft.note().length() > 255) errors.put(Field.NOTE, R.string.transaction_note_too_long);
        Edit request = errors.isEmpty() ? new Edit(draft.account(), draft.category(), draft.type(),
                amount, date, optional(draft.merchant()), optional(draft.note())) : null;
        return new Validation(request, errors);
    }

    static boolean isSelectable(AccountDto account, Long originalAccount) {
        return !account.archived() || Objects.equals(account.id(), originalAccount);
    }

    static BigDecimal parseAmount(String input, char separator) {
        String decimal = input.trim();
        if (separator != '.' && decimal.indexOf(separator) >= 0) {
            if (decimal.indexOf('.') >= 0) return null;
            decimal = decimal.replace(separator, '.');
        }
        if (!decimal.matches("[0-9]{1,13}(\\.[0-9]{1,2})?")) return null;
        BigDecimal amount = new BigDecimal(decimal);
        return amount.signum() > 0 ? amount.setScale(2) : null;
    }

    private static String optional(String input) {
        String value = input.trim();
        return value.isEmpty() ? null : value;
    }

    static Field serverField(String name) {
        if (name == null) return null;
        return switch (name) {
            case "accountId" -> Field.ACCOUNT;
            case "categoryId" -> Field.CATEGORY;
            case "txnDate" -> Field.DATE;
            case "type" -> Field.TYPE;
            case "amount" -> Field.AMOUNT;
            case "merchant" -> Field.MERCHANT;
            case "note" -> Field.NOTE;
            default -> null;
        };
    }
}
