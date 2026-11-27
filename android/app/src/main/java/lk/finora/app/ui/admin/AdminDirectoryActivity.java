package lk.finora.app.ui.admin;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import java.util.List;
import lk.finora.app.R;
import lk.finora.app.databinding.ActivityFinoraScreenBinding;
import lk.finora.app.ui.ScreenControls;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AdminDirectoryRepository;
import lk.finora.app.data.repo.AdminDirectoryRepository.Category;
import lk.finora.app.data.repo.AdminDirectoryRepository.User;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class AdminDirectoryActivity extends SecureActivity {
    static final String CATEGORIES = "categories";
    static final String ADMINS = "admins";
    private static final String QUERY = "query";
    private static final String[] REASONS = {"SECURITY", "POLICY", "SUPPORT", "RESTORED"};
    private static final int[] REASON_LABELS = {R.string.admin_reason_security, R.string.admin_reason_policy,
            R.string.admin_reason_support, R.string.admin_reason_restored};
    private Work work;
    private AdminDirectoryRepository repository;
    private LinearLayout rows;
    private EditText search;
    private AlertDialog dialog;
    private boolean categories;
    private boolean admins;
    private List<AdminDirectoryRepository.Admin> adminRows = List.of();
    private List<User> users = List.of();
    private List<Category> defaults = List.of();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        admins = getIntent().getBooleanExtra(ADMINS, false);
        categories = getIntent().getBooleanExtra(CATEGORIES, false);
        work = new Work(AccountRepository.ProtectedRequests.currentAdmin(), () -> {
            users = List.of();
            defaults = List.of();
            adminRows = List.of();
        });
        if (!work.isCurrent()) { finish(); return; }
        repository = new AdminDirectoryRepository(work.requests);
        var binding = ActivityFinoraScreenBinding.inflate(getLayoutInflater());
        var page = binding.screenPage;
        var heading = ScreenControls.text(this);
        heading.setText(admins ? R.string.admin_manage_admins
                : categories ? R.string.admin_default_categories : R.string.admin_manage_users);
        ViewCompat.setAccessibilityHeading(heading, true);
        page.addView(heading);
        if (!categories && !admins) {
            search = field(R.string.admin_user_search, state == null ? "" : state.getString(QUERY, ""));
            search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
            ScreenControls.addField(page, search);
            button(page, R.string.admin_search, null, this::load);
        }
        rows = column();
        page.addView(rows);
        setContentView(binding.getRoot());
        work.changes.observe(this, ignored -> render());
    }

    @Override protected void onStart() { super.onStart(); if (!isFinishing()) load(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (search != null) state.putString(QUERY, text(search));
        super.onSaveInstanceState(state);
    }

    private void load() {
        if (admins) {
            work.run(repository::admins, response -> List.of(AccountRepository.read(response,
                    AdminDirectoryRepository.Admin[].class)), data -> adminRows = data, false);
        } else if (categories) {
            work.run(repository::categories, response -> List.of(AccountRepository.read(response, Category[].class)),
                    data -> defaults = data, false);
        } else {
            String query = text(search);
            work.run(() -> repository.users(query), response -> List.of(AccountRepository.read(response, User[].class)),
                    data -> users = data, false);
        }
    }

    private void render() {
        rows.removeAllViews();
        if (!work.isCurrent()) {
            if (dialog != null) dialog.dismiss();
            finish();
            return;
        }
        if (work.busy()) { label(getString(R.string.records_loading)); return; }
        if (work.error != 0) {
            label(getString(work.error));
            button(rows, R.string.records_retry, null, this::load);
            return;
        }
        if (admins) {
            button(rows, R.string.admin_create_admin, null, this::createAdmin);
            if (adminRows.isEmpty()) label(getString(R.string.records_empty));
            for (var admin : adminRows) {
                String summary = admin.fullName() + " · " + admin.email() + " · "
                        + getString(admin.enabled() ? R.string.support_active : R.string.admin_inactive);
                label(summary);
                int action = admin.enabled() ? R.string.admin_disable_user : R.string.admin_enable_user;
                button(rows, action, summary, () -> changeAdmin(admin, action));
            }
        } else if (categories) {
            button(rows, R.string.records_add, null, () -> editCategory(null));
            if (defaults.isEmpty()) label(getString(R.string.records_empty));
            for (var category : defaults) {
                String summary = category.name() + " · " + category.nameSi() + " · "
                        + getString(category.active() ? R.string.support_active : R.string.admin_inactive);
                label(summary);
                if (!category.active()) continue;
                button(rows, R.string.records_edit, summary, () -> editCategory(category));
                button(rows, R.string.records_remove, summary, () -> confirmRemove(category, summary));
            }
        } else {
            if (users.isEmpty()) label(getString(R.string.records_empty));
            for (var user : users) {
                boolean active = "ACTIVE".equals(user.status());
                String summary = user.name() + " · " + user.email() + " · "
                        + getString(active ? R.string.support_active : R.string.admin_inactive);
                label(summary);
                int action = active ? R.string.admin_disable_user : R.string.admin_enable_user;
                button(rows, action, summary, () -> confirmStatus(user, action, active));
                if (active) button(rows, R.string.admin_make_staff, summary, () -> confirmPromotion(user));
            }
        }
    }

    private void confirmPromotion(User user) {
        if (!work.isCurrent()) return;
        var form = column();
        var warning = ScreenControls.text(this);
        warning.setText(getString(R.string.admin_promote_warning, user.email()));
        form.addView(warning);
        var approval = field(R.string.admin_promote_confirmation, "");
        ScreenControls.addField(form, approval);
        dialog = showSecure(new AlertDialog.Builder(this).setTitle(R.string.admin_make_staff).setView(form)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            String confirmation = text(approval);
            if (!"DELETE_FINANCIAL_DATA".equals(confirmation)) {
                approval.setError(getString(R.string.records_invalid));
                return;
            }
            work.run(() -> repository.promote(user.id(), confirmation), Work::empty, ignored -> load(), false);
            dialog.dismiss();
        });
    }

    private void changeAdmin(AdminDirectoryRepository.Admin admin, int action) {
        if (!work.isCurrent()) return;
        dialog = showSecure(new AlertDialog.Builder(this).setTitle(action).setMessage(admin.email())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (prompt, which) ->
                        work.run(() -> repository.setAdminEnabled(admin.id(), !admin.enabled()),
                                Work::empty, ignored -> load(), false)).create());
    }

    private void createAdmin() {
        if (!work.isCurrent()) return;
        var form = column();
        var address = field(R.string.admin_new_email, "");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        var name = field(R.string.admin_new_name, "");
        var password = field(R.string.admin_new_password, "");
        var repeated = field(R.string.admin_confirm_password, "");
        for (var secret : new EditText[]{password, repeated}) {
            secret.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            secret.setSaveEnabled(false);
        }
        for (var input : new EditText[]{address, name, password, repeated}) ScreenControls.addField(form, input);
        var show = new androidx.appcompat.widget.AppCompatCheckBox(this);
        show.setText(R.string.login_show_password);
        show.setOnCheckedChangeListener((button, visible) -> {
            for (var secret : new EditText[]{password, repeated}) {
                secret.setTransformationMethod(visible ? null : android.text.method.PasswordTransformationMethod.getInstance());
                secret.setSelection(secret.length());
            }
        });
        form.addView(show);
        var scroll = new ScrollView(this);
        scroll.addView(form);
        dialog = showSecure(new AlertDialog.Builder(this).setTitle(R.string.admin_create_admin).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            String email = text(address);
            String fullName = text(name);
            String pass = password.getText().toString();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() || email.length() > 254) {
                address.setError(getString(R.string.invalid_email)); return;
            }
            if (fullName.isBlank() || fullName.length() > 100) { name.setError(getString(R.string.records_invalid)); return; }
            if (!pass.matches("^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$")
                    || pass.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                password.setError(getString(R.string.admin_password_rule)); return;
            }
            if (!pass.contentEquals(repeated.getText())) {
                repeated.setError(getString(R.string.admin_password_mismatch)); return;
            }
            password.setText("");
            repeated.setText("");
            work.run(() -> repository.createAdmin(email, fullName, pass), Work::empty, ignored -> load(), true);
            dialog.dismiss();
        });
    }

    private void confirmStatus(User user, int action, boolean disable) {
        if (!work.isCurrent()) return;
        var reasons = new CharSequence[REASON_LABELS.length];
        for (int index = 0; index < reasons.length; index++) reasons[index] = getString(REASON_LABELS[index]);
        var form = column();
        var target = ScreenControls.text(this);
        target.setText(user.email());
        form.addView(target);
        var reason = new android.widget.Spinner(this);
        reason.setContentDescription(getString(R.string.admin_status_reason));
        reason.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reasons));
        form.addView(reason);
        dialog = showSecure(new AlertDialog.Builder(this).setTitle(action).setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (prompt, which) -> {
                    String selected = REASONS[reason.getSelectedItemPosition()];
                    work.run(() -> repository.changeStatus(user.id(), disable ? "DISABLED" : "ACTIVE", selected),
                            Work::empty, ignored -> load(), false);
                }).create());
    }

    private void editCategory(Category category) {
        if (!work.isCurrent()) return;
        var form = column();
        var english = field(R.string.admin_category_name_en, category == null ? "" : category.name());
        var sinhala = field(R.string.admin_category_name_si, category == null ? "" : category.nameSi());
        ScreenControls.addField(form, english);
        ScreenControls.addField(form, sinhala);
        var kind = new android.widget.Spinner(this);
        if (category == null) {
            kind.setContentDescription(getString(R.string.admin_category_kind));
            kind.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                    new String[]{getString(R.string.admin_income_kind), getString(R.string.admin_expense_kind)}));
            form.addView(kind);
        }
        dialog = showSecure(new AlertDialog.Builder(this)
                .setTitle(category == null ? R.string.records_add : R.string.records_edit).setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null).create());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = text(english);
            String nameSi = text(sinhala);
            if (name.isEmpty() || name.length() > 60) { english.setError(getString(R.string.records_invalid)); return; }
            if (nameSi.isEmpty() || nameSi.length() > 60) { sinhala.setError(getString(R.string.records_invalid)); return; }
            String value = kind.getSelectedItemPosition() == 0 ? "INCOME" : "EXPENSE";
            work.run(() -> repository.saveCategory(category == null ? null : category.id(), name, nameSi, value),
                    Work::empty, ignored -> load(), category == null);
            dialog.dismiss();
        });
    }

    private void confirmRemove(Category category, String summary) {
        if (!work.isCurrent()) return;
        dialog = showSecure(new AlertDialog.Builder(this).setTitle(R.string.records_remove).setMessage(summary)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (prompt, which) ->
                        work.run(() -> repository.removeCategory(category.id()), Work::empty, ignored -> load(), false))
                .create());
    }

    private LinearLayout column() {
        var layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private EditText field(int hint, String value) {
        var field = ScreenControls.field(this);
        field.setHint(hint);
        field.setText(value);
        field.setSingleLine(true);
        return field;
    }

    private static String text(EditText field) { return field.getText().toString().trim(); }

    private void label(String value) {
        var text = ScreenControls.text(this);
        text.setText(value);
        rows.addView(text);
    }

    private void button(LinearLayout parent, int title, String description, Runnable run) {
        var button = ScreenControls.action(this);
        button.setText(title);
        if (description != null) button.setContentDescription(getString(title) + ". " + description);
        button.setOnClickListener(view -> run.run());
        parent.addView(button);
    }

    @Override protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        if (work != null) work.close();
        super.onDestroy();
    }
}
