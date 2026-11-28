package lk.finora.app.ui.support;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import lk.finora.app.R;
import lk.finora.app.databinding.ActivityFinoraScreenBinding;
import lk.finora.app.ui.ScreenControls;
import lk.finora.app.data.repo.SupportRepository.Ticket;
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
        if (model.staff) {
            // STAFF keep the T47/T48 screen until T70.
            var binding = ActivityFinoraScreenBinding.inflate(getLayoutInflater());
            rows = binding.screenPage;
            setContentView(binding.getRoot());
        } else {
            bindUser();
        }
        model.work.changes.observe(this, ignored -> render());
        model.load();
    }

    private void render() {
        if (!model.work.isCurrent()) {
            if (rows != null) rows.removeAllViews();
            finish();
            return;
        }
        if (!model.staff) {
            renderUser();
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
        return getString(SupportRows.stateLabel(key));
    }

    private TextView label(String text) {
        var view = ScreenControls.text(this);
        view.setText(text);
        rows.addView(view);
        return view;
    }

    private Button button(int id, Runnable action) {
        var button = ScreenControls.action(this);
        button.setText(id);
        button.setOnClickListener(view -> action.run());
        rows.addView(button);
        return button;
    }

    private void rowAction(int id, String summary, Runnable action) {
        button(id, action).setContentDescription(getString(id) + ". " + summary);
    }

    // ---- T68.3/D72 USER screen: request cards, a thread view, and New support request ----

    private boolean resumedOnce;

    @Override protected void onResume() {
        super.onResume();
        // A request sent from New support request shows up when the list comes back.
        if (model != null && !model.staff && resumedOnce && model.viewedTicket == 0 && model.work.isCurrent()) {
            model.load();
        }
        resumedOnce = true;
    }

    private final OnBackPressedCallback backToList = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { model.catalog(false); }
    };

    private void bindUser() {
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_support);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.support_heading), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.support_list_title), true);
        findViewById(R.id.support_reload).setOnClickListener(view -> model.load());
        findViewById(R.id.support_retry).setOnClickListener(view -> model.load());
        findViewById(R.id.support_new).setOnClickListener(view ->
                startActivity(new Intent(this, SupportNewActivity.class)));
        getOnBackPressedDispatcher().addCallback(this, backToList);
    }

    private void renderUser() {
        boolean busy = model.work.busy();
        boolean thread = model.viewedTicket > 0;
        backToList.setEnabled(thread);
        ((TextView) findViewById(R.id.support_heading)).setText(thread
                ? getString(R.string.support_request_number, model.viewedTicket) : getString(R.string.support_tickets));
        findViewById(R.id.support_list_group).setVisibility(thread ? View.GONE : View.VISIBLE);
        findViewById(R.id.support_thread_group).setVisibility(thread ? View.VISIBLE : View.GONE);
        findViewById(R.id.support_new).setVisibility(thread ? View.GONE : View.VISIBLE);
        TextView status = findViewById(R.id.support_status);
        int message = busy ? R.string.records_loading : model.work.error != 0 ? model.work.error : model.notice;
        status.setText(message == 0 ? "" : getString(message));
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        findViewById(R.id.support_retry).setVisibility(!busy && model.work.error != 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.support_reload).setEnabled(!busy);
        if (thread) renderThread(busy);
        else renderTickets(busy);
    }

    private void renderTickets(boolean busy) {
        LinearLayout list = findViewById(R.id.support_list);
        list.removeAllViews();
        for (var ticket : model.tickets) {
            View card = getLayoutInflater().inflate(R.layout.item_support_ticket, list, false);
            bindTicket(card, ticket);
            card.setOnClickListener(view -> model.openReplies(ticket.id()));
            list.addView(card);
        }
        boolean empty = model.tickets.isEmpty() && !busy && model.work.error == 0;
        findViewById(R.id.support_empty).setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void bindTicket(View card, Ticket ticket) {
        var topic = Topic.matching(ticket.subject());
        ((ImageView) card.findViewById(R.id.link_icon)).setImageResource(SupportRows.icon(topic));
        String name = topic == null ? getString(R.string.support_tickets) : topic.label(this);
        ((TextView) card.findViewById(R.id.link_title)).setText(name);
        ((TextView) card.findViewById(R.id.link_text)).setText(getString(R.string.support_request_number, ticket.id()));
        SupportRows.status(card.findViewById(R.id.support_ticket_status), ticket.status());
        // TalkBack reads the same one-line summary the list always used.
        card.setContentDescription(getString(R.string.support_ticket_summary, ticket.id(), name, state(ticket.status())));
    }

    private void renderThread(boolean busy) {
        View card = findViewById(R.id.support_thread_card);
        card.setClickable(false);
        card.setFocusable(false);
        Ticket ticket = null;
        for (var item : model.tickets) if (item.id() == model.viewedTicket) ticket = item;
        card.setVisibility(ticket == null ? View.GONE : View.VISIBLE);
        if (ticket != null) bindTicket(card, ticket);
        // In the thread the card is a header: no chevron, and the request number is already the title.
        card.findViewById(R.id.support_ticket_chevron).setVisibility(View.GONE);
        card.findViewById(R.id.link_text).setVisibility(View.GONE);
        Topic topic = ticket == null ? null : Topic.matching(ticket.subject());
        TextView sent = findViewById(R.id.support_sent);
        sent.setText(topic == null ? "" : topic.body());
        int shown = topic == null ? View.GONE : View.VISIBLE;
        sent.setVisibility(shown);
        findViewById(R.id.support_sent_label).setVisibility(shown);
        LinearLayout replies = findViewById(R.id.support_replies);
        replies.removeAllViews();
        int pad = Math.round(14 * getResources().getDisplayMetrics().density);
        for (var reply : model.replies) {
            var label = new TextView(this);
            label.setText(R.string.support_team);
            label.setTextAppearance(R.style.FinoraText_Caption);
            label.setTextColor(getColor(R.color.finora_muted));
            label.setTypeface(label.getTypeface(), Typeface.BOLD);
            var labelParams = new LinearLayout.LayoutParams(-2, -2);
            labelParams.topMargin = pad;
            labelParams.setMarginStart(pad / 2);
            replies.addView(label, labelParams);
            var bubble = new TextView(this);
            bubble.setText(reply.body());
            bubble.setTextAppearance(R.style.FinoraText_Body);
            bubble.setBackgroundResource(R.drawable.finora_bubble_reply);
            bubble.setPadding(pad, pad * 6 / 7, pad, pad * 6 / 7);
            var bubbleParams = new LinearLayout.LayoutParams(-2, -2);
            bubbleParams.topMargin = pad / 4;
            bubbleParams.setMarginEnd(pad * 3);
            replies.addView(bubble, bubbleParams);
        }
        if (model.replies.isEmpty() && !busy && model.work.error == 0) {
            var none = new TextView(this);
            none.setText(R.string.support_no_replies);
            none.setTextAppearance(R.style.FinoraText_Body);
            none.setTextColor(getColor(R.color.finora_muted));
            var params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = pad;
            replies.addView(none, params);
        }
    }
}
