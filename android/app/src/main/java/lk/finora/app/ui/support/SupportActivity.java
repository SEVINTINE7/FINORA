package lk.finora.app.ui.support;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import lk.finora.app.R;
import lk.finora.app.data.repo.SupportRepository.Topic;
import lk.finora.app.ui.SecureActivity;

public final class SupportActivity extends SecureActivity {
    private SupportViewModel model;
    private LinearLayout rows;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(SupportViewModel.class);
        if (!model.work.isCurrent()) {
            finish();
            return;
        }
        var scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        rows.setPadding(padding, padding, padding, padding);
        scroll.addView(rows);
        setContentView(scroll);
        model.work.changes.observe(this, ignored -> render());
        model.load();
    }

    private void render() {
        if (!model.work.isCurrent()) {
            rows.removeAllViews();
            finish();
            return;
        }
        rows.removeAllViews();
        int title = model.showCatalog ? R.string.support_catalog : R.string.support_tickets;
        ViewCompat.setAccessibilityHeading(label(getString(title)), true);
        if (model.work.busy()) {
            label(getString(R.string.records_loading));
            return;
        }
        if (model.work.error != 0) label(getString(model.work.error));
        if (model.notice != 0) label(getString(model.notice));
        button(R.string.records_retry, model::load);
        if (model.showCatalog) {
            button(R.string.support_tickets, () -> model.catalog(false));
            button(R.string.support_draft, () -> topics(true));
            for (var revision : model.catalog) {
                String titleText = revision.topic().label(this) + " · " + state(revision.state());
                String copy = revision.english() + "\n\n" + revision.sinhala();
                label(titleText + "\n" + copy);
                if ("DRAFT".equals(revision.state())) button(R.string.support_approve,
                        () -> confirm(R.string.support_approve, copy, () -> model.approve(revision.id())));
                if (!"RETIRED".equals(revision.state())) button(R.string.support_retire,
                        () -> confirm(R.string.support_retire, copy, () -> model.retire(revision.id())));
            }
            return;
        }
        if (model.viewedTicket > 0) {
            button(R.string.support_tickets, () -> model.catalog(false));
            if (model.replies.isEmpty()) label(getString(R.string.records_empty));
            for (var reply : model.replies) label(reply.body());
            return;
        }
        if (model.staff) button(R.string.support_catalog, () -> model.catalog(true));
        else {
            label(getString(R.string.support_privacy));
            button(R.string.support_create, () -> topics(false));
        }
        if (model.tickets.isEmpty() && model.work.error == 0) label(getString(R.string.records_empty));
        for (var ticket : model.tickets) {
            var topic = Topic.matching(ticket.subject());
            String summary = getString(R.string.support_ticket_summary, ticket.id(),
                    topic == null ? getString(R.string.support_tickets) : topic.label(this), state(ticket.status()));
            label(summary);
            if (!model.staff) rowAction(R.string.support_replies, summary, () -> model.openReplies(ticket.id()));
            else if ("OPEN".equals(ticket.status()) && ticket.subject() == null) {
                rowAction(R.string.support_claim, summary,
                        () -> confirm(R.string.support_claim, summary, () -> model.claim(ticket.id())));
            } else if ("IN_PROGRESS".equals(ticket.status())) {
                rowAction(R.string.support_close, summary,
                        () -> confirm(R.string.support_close, summary, () -> model.close(ticket.id())));
                rowAction(R.string.support_reply, summary, () -> chooseReply(ticket.id()));
            }
        }
        if (model.staff) button(R.string.support_load_replies, model::browseReplies);
    }

    private void chooseReply(long ticket) {
        var choices = model.catalog.stream().filter(item -> "ACTIVE".equals(item.state()))
                .collect(java.util.stream.Collectors.toList());
        if (choices.isEmpty()) {
            label(getString(R.string.support_no_reply));
            return;
        }
        String[] names = choices.stream().map(item -> item.topic().label(this)).toArray(String[]::new);
        showSecure(new AlertDialog.Builder(this).setTitle(R.string.support_reply)
                .setItems(names, (dialog, which) -> {
                    var selected = choices.get(which);
                    if (!model.work.isCurrent()) return;
                    showSecure(new AlertDialog.Builder(this).setTitle(R.string.support_reply)
                            .setItems(new String[]{selected.english(), selected.sinhala()}, (language, index) -> {
                                if (!model.work.isCurrent()) return;
                                confirm(R.string.support_reply, index == 0 ? selected.english() : selected.sinhala(),
                                        () -> model.reply(ticket, selected.id(), index == 0 ? "EN" : "SI"));
                            }).create());
                }).create());
    }

    private void topics(boolean drafting) {
        Topic[] topics = Topic.values();
        String[] names = java.util.Arrays.stream(topics).map(topic -> topic.label(this)).toArray(String[]::new);
        int title = drafting ? R.string.support_draft : R.string.support_create;
        showSecure(new AlertDialog.Builder(this).setTitle(title).setItems(names, (dialog, which) -> {
            if (!model.work.isCurrent()) return;
            String message = names[which] + (drafting ? "" : "\n\n" + getString(R.string.support_privacy));
            confirm(title, message, () -> {
                if (drafting) model.draft(topics[which]);
                else model.create(topics[which]);
            });
        }).create());
    }

    private void confirm(int title, String message, Runnable action) {
        showSecure(new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (model.work.isCurrent()) action.run();
                }).create());
    }

    private String state(String key) {
        return getString(switch (key) {
            case "OPEN" -> R.string.sms_diagnostic_open;
            case "IN_PROGRESS" -> R.string.support_in_progress;
            case "CLOSED" -> R.string.support_closed;
            case "DRAFT" -> R.string.support_draft_state;
            case "ACTIVE" -> R.string.support_active;
            case "RETIRED" -> R.string.support_retired;
            default -> R.string.sms_diagnostic_unknown;
        });
    }

    private TextView label(String text) {
        var view = new TextView(this);
        view.setText(text);
        rows.addView(view);
        return view;
    }

    private Button button(int id, Runnable action) {
        var button = new Button(this);
        button.setText(id);
        button.setOnClickListener(view -> action.run());
        rows.addView(button);
        return button;
    }

    private void rowAction(int id, String summary, Runnable action) {
        button(id, action).setContentDescription(getString(id) + ". " + summary);
    }
}
