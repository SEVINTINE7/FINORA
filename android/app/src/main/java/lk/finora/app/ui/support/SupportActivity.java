package lk.finora.app.ui.support;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import lk.finora.app.R;
import lk.finora.app.data.repo.SupportRepository.Revision;
import lk.finora.app.data.repo.SupportRepository.Ticket;
import lk.finora.app.data.repo.SupportRepository.Topic;
import lk.finora.app.ui.SecureActivity;

public final class SupportActivity extends SecureActivity {
    /** Staff home "Saved replies" opens the catalog directly (T70.2). */
    public static final String CATALOG = "catalog";
    private SupportViewModel model;
    private StaffCards cards;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(SupportViewModel.class);
        if (!model.work.isCurrent()) {
            finish();
            return;
        }
        if (model.staff) {
            if (state == null && getIntent().getBooleanExtra(CATALOG, false)) model.showCatalog = true;
            bindStaff();
        } else {
            bindUser();
        }
        model.work.changes.observe(this, ignored -> render());
        model.load();
    }

    private void render() {
        if (!model.work.isCurrent()) {
            if (cards != null) cards.clear();
            finish();
            return;
        }
        if (!model.staff) {
            renderUser();
            return;
        }
        boolean busy = model.work.busy();
        StaffCards.status(this, busy, model.work.error, model.notice);
        cards.clear();
        if (busy) return;
        if (model.showCatalog) renderCatalog();
        else renderQueue();
    }

    // ---- T70.3/D72 STAFF screens: request queue and saved replies (catalog) ----

    private void bindStaff() {
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_staff_list);
        cards = new StaffCards(this);
        boolean catalog = model.showCatalog;
        StaffCards.frame(this, catalog ? R.string.support_catalog : R.string.support_tickets,
                getString(catalog ? R.string.staff_catalog_note : R.string.staff_queue_note),
                catalog ? R.drawable.finora_ic_shield : R.drawable.finora_ic_info);
        findViewById(R.id.staff_list_reload).setOnClickListener(view -> model.load());
        findViewById(R.id.staff_list_retry).setOnClickListener(view -> model.load());
        if (catalog) {
            var fab = (com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton)
                    findViewById(R.id.staff_list_fab);
            fab.setText(R.string.support_draft);
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(view -> draftTopic());
        }
    }

    private void renderQueue() {
        var waiting = new java.util.ArrayList<Ticket>();
        var mine = new java.util.ArrayList<Ticket>();
        for (var ticket : model.tickets) {
            if ("OPEN".equals(ticket.status()) && ticket.subject() == null) waiting.add(ticket);
            else mine.add(ticket);
        }
        if (model.work.error != 0 && model.tickets.isEmpty()) return;
        cards.section(getString(R.string.staff_waiting_section, waiting.size()));
        if (waiting.isEmpty()) cards.line(getString(R.string.staff_none_waiting));
        for (var ticket : waiting) {
            String summary = getString(R.string.support_ticket_summary, ticket.id(),
                    getString(R.string.support_tickets), state(ticket.status()));
            View card = cards.card(R.drawable.finora_ic_mail, StaffCards.Tone.ACCENT,
                    getString(R.string.staff_topic_hidden), getString(R.string.support_request_number, ticket.id()),
                    summary);
            StaffCards.chip(card, ticket.status());
            cards.action(card, R.string.support_claim, R.drawable.finora_ic_tick, true, summary,
                    () -> confirm(R.string.support_claim, summary, () -> model.claim(ticket.id())));
        }
        cards.section(getString(R.string.staff_assigned_section, mine.size()));
        if (mine.isEmpty()) cards.line(getString(R.string.staff_none_assigned));
        for (var ticket : mine) {
            var topic = Topic.matching(ticket.subject());
            String name = topic == null ? getString(R.string.support_tickets) : topic.label(this);
            String summary = getString(R.string.support_ticket_summary, ticket.id(), name, state(ticket.status()));
            View card = cards.card(SupportRows.icon(topic), StaffCards.Tone.ACCENT, name,
                    getString(R.string.support_request_number, ticket.id()), summary);
            StaffCards.chip(card, ticket.status());
            if ("IN_PROGRESS".equals(ticket.status())) {
                StaffCards.stack(card);
                cards.action(card, R.string.support_reply, R.drawable.finora_ic_mail, true, summary,
                        () -> startActivity(new Intent(this, StaffReplyActivity.class)
                                .putExtra(StaffReplyActivity.TICKET, ticket.id())
                                .putExtra(StaffReplyActivity.SUBJECT, ticket.subject())));
                cards.action(card, R.string.support_close, R.drawable.finora_ic_check, false, summary,
                        () -> confirm(R.string.support_close, summary, () -> model.close(ticket.id())));
            }
        }
    }

    private void renderCatalog() {
        if (model.work.error != 0 && model.catalog.isEmpty()) return;
        if (model.catalog.isEmpty()) {
            cards.line(getString(R.string.staff_no_saved_replies));
            return;
        }
        String[][] groups = {{"DRAFT"}, {"ACTIVE"}, {"RETIRED"}};
        int[] headings = {R.string.staff_section_draft, R.string.support_active, R.string.support_retired};
        for (int g = 0; g < groups.length; g++) {
            String key = groups[g][0];
            var items = model.catalog.stream().filter(item -> key.equals(item.state()))
                    .collect(java.util.stream.Collectors.toList());
            if (items.isEmpty()) continue;
            cards.section(getString(headings[g]));
            for (Revision revision : items) {
                String name = revision.topic().label(this);
                String copy = revision.english() + "\n\n" + revision.sinhala();
                String summary = name + " · " + state(revision.state());
                View card = cards.card(SupportRows.icon(revision.topic()), StaffCards.Tone.ACCENT, name,
                        getString(R.string.staff_revision_number, revision.id()), summary + "\n" + copy);
                StaffCards.chip(card, revision.state());
                cards.quote(card, revision.english());
                cards.quote(card, revision.sinhala());
                if ("DRAFT".equals(revision.state())) StaffCards.stack(card);
                if ("DRAFT".equals(revision.state())) cards.action(card, R.string.support_approve,
                        R.drawable.finora_ic_check, true, summary,
                        () -> confirm(R.string.support_approve, copy, () -> model.approve(revision.id())));
                if (!"RETIRED".equals(revision.state())) cards.action(card, R.string.support_retire,
                        R.drawable.finora_ic_archive, false, summary,
                        () -> confirm(R.string.support_retire, copy, () -> model.retire(revision.id())));
            }
        }
    }

    /** Draft a saved reply: pick a topic; the server fills the approved English and Sinhala wording. */
    private void draftTopic() {
        if (!model.work.isCurrent() || model.work.busy()) return;
        Topic[] topics = Topic.values();
        String[] names = java.util.Arrays.stream(topics).map(topic -> topic.label(this)).toArray(String[]::new);
        showSecure(new AlertDialog.Builder(this).setTitle(R.string.support_draft).setItems(names, (dialog, which) -> {
            if (!model.work.isCurrent()) return;
            confirm(R.string.support_draft, names[which], () -> model.draft(topics[which]));
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

    // ---- T68.3/D72 USER screen: request cards, a thread view, and New support request ----

    private boolean resumedOnce;

    @Override protected void onResume() {
        super.onResume();
        // A request sent from New support request shows up when the list comes back.
        if (model != null && resumedOnce && model.viewedTicket == 0 && model.work.isCurrent() && !model.work.busy()) {
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
