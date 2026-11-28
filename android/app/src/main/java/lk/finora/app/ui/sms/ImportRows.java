package lk.finora.app.ui.sms;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.databinding.ItemImportTxnBinding;
import lk.finora.app.sms.SmsParseResult;
import lk.finora.app.ui.Money;

/** T67/D71 views shared by SMS access, Paste SMS, the review sheet and CSV import. */
public final class ImportRows {
    private ImportRows() {}

    public static String date(Context context, LocalDate date) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale));
    }

    /** Title for a parsed message: the merchant, or "Money received" / "Payment" when the message has none. */
    static String title(Context context, SmsParseResult.Parsed parsed) {
        if (parsed.merchant() != null && !parsed.merchant().isBlank()) return parsed.merchant();
        return context.getString(income(parsed) ? R.string.sms_money_received : R.string.sms_money_spent);
    }

    static boolean income(SmsParseResult.Parsed parsed) {
        return parsed.direction() == SmsParseResult.Direction.CREDIT;
    }

    /** One transaction row with direction icon and signed amount; returned unattached. */
    public static ItemImportTxnBinding row(Context context, ViewGroup parent, boolean income, String title, String meta,
                                           BigDecimal amount) {
        var row = ItemImportTxnBinding.inflate(LayoutInflater.from(context), parent, false);
        bind(row, context, income, title, meta, amount);
        return row;
    }

    public static void bind(ItemImportTxnBinding row, Context context, boolean income, String title, String meta,
                            BigDecimal amount) {
        row.importIcon.setImageResource(income ? R.drawable.finora_ic_arrow_up : R.drawable.finora_ic_arrow_down);
        row.importIcon.setBackgroundTintList(ColorStateList.valueOf(context.getColor(
                income ? R.color.finora_success_surface : R.color.finora_neutral_surface)));
        row.importIcon.setImageTintList(ColorStateList.valueOf(context.getColor(
                income ? R.color.finora_green : R.color.finora_ink)));
        row.importTitle.setText(title);
        row.importMeta.setText(meta);
        row.importMeta.setVisibility(meta == null || meta.isEmpty() ? View.GONE : View.VISIBLE);
        row.importAmount.setText(Money.signed(context, amount, income));
        row.importAmount.setTextColor(context.getColor(income ? R.color.finora_green : R.color.finora_ink));
    }

    /** A parsed message as a row; its spoken text is the same summary the app showed before (T67 keeps tests). */
    static View parsed(Context context, ViewGroup parent, SmsParseResult.Parsed parsed) {
        var row = row(context, parent, income(parsed), title(context, parsed), date(context, parsed.date()), parsed.amount());
        row.getRoot().setFocusable(true);
        row.getRoot().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.getRoot().setContentDescription(SmsLocalResults.text(context, parsed));
        for (int i = 0; i < row.getRoot().getChildCount(); i++) {
            row.getRoot().getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        return row.getRoot();
    }

    /** Adds a view to a list card with a hairline between rows. */
    public static void add(ViewGroup list, View row) {
        if (list.getChildCount() > 0) {
            var line = new View(list.getContext());
            line.setBackgroundColor(list.getContext().getColor(R.color.finora_card_line));
            list.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        list.addView(row);
    }

    static void link(View include, int icon, int title, int text) {
        ((ImageView) include.findViewById(R.id.link_icon)).setImageResource(icon);
        ((TextView) include.findViewById(R.id.link_title)).setText(title);
        ((TextView) include.findViewById(R.id.link_text)).setText(text);
    }

    /** A privacy point: tinted icon, bold title and a muted line. */
    static void point(Context context, LinearLayout parent, int icon, int title, int text) {
        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 12), 0, 0);
        row.setFocusable(true);
        var image = new ImageView(context);
        image.setImageResource(icon);
        image.setBackgroundResource(R.drawable.finora_circle);
        image.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.finora_success_surface)));
        image.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.finora_green)));
        int pad = dp(context, 9);
        image.setPadding(pad, pad, pad, pad);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(image, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
        var words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        var head = new TextView(context);
        head.setText(title);
        head.setTextSize(14);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        head.setTextColor(context.getColor(R.color.finora_ink));
        var body = new TextView(context);
        body.setText(text);
        body.setTextSize(13);
        body.setTextColor(context.getColor(R.color.finora_muted));
        words.addView(head);
        words.addView(body);
        var params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMarginStart(dp(context, 12));
        row.addView(words, params);
        parent.addView(row);
    }

    /** A numbered step for Paste SMS. */
    static void step(Context context, LinearLayout parent, int number, int text) {
        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, parent.getChildCount() == 0 ? 0 : dp(context, 10), 0, 0);
        var badge = new TextView(context);
        badge.setText(String.valueOf(number));
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(14);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setTextColor(context.getColor(R.color.finora_green));
        badge.setBackgroundResource(R.drawable.finora_circle);
        badge.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.finora_success_surface)));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(badge, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));
        var words = new TextView(context);
        words.setText(context.getString(text));
        words.setTextSize(14);
        words.setTextColor(context.getColor(R.color.finora_ink));
        var params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMarginStart(dp(context, 12));
        row.addView(words, params);
        parent.addView(row);
    }

    /** A label / value line for the parsed fields card. */
    static void field(Context context, LinearLayout parent, int label, String value) {
        var row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 44));
        var name = new TextView(context);
        name.setText(label);
        name.setTextSize(14);
        name.setTextColor(context.getColor(R.color.finora_muted));
        var text = new TextView(context);
        text.setText(value);
        text.setTextSize(15);
        text.setGravity(Gravity.END);
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setTextColor(context.getColor(R.color.finora_ink));
        row.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        var params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMarginStart(dp(context, 12));
        row.addView(text, params);
        add(parent, row);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
