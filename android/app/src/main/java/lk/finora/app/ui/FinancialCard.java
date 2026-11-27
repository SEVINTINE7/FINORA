package lk.finora.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.view.ViewCompat;

import com.google.android.material.card.MaterialCardView;

import lk.finora.app.R;

/** Shared presentation for financial rows. Existing actions remain inside the secure screen. */
public final class FinancialCard {
    private FinancialCard() {}

    public static MaterialCardView wrap(LinearLayout body, CharSequence name, int icon, View... actions) {
        Context context = body.getContext();
        var card = new MaterialCardView(context);
        card.setCardBackgroundColor(context.getColor(R.color.finora_field));
        card.setStrokeColor(context.getColor(R.color.finora_outline));
        card.setStrokeWidth(size(context, R.dimen.finora_outline_width));
        card.setRadius(size(context, R.dimen.finora_card_radius));
        card.setCardElevation(0);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = size(context, R.dimen.finora_space_s);
        card.setLayoutParams(params);

        var container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        // Put the icon and menu on their own row to leave long Sinhala names their full width.
        var controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        int inset = size(context, R.dimen.finora_space_s);
        controls.setPaddingRelative(inset, 0, inset, 0);
        var marker = new android.widget.ImageView(context);
        int markSize = size(context, R.dimen.finora_icon);
        int touchSize = size(context, R.dimen.finora_touch);
        marker.setImageResource(icon);
        marker.setColorFilter(context.getColor(R.color.finora_green));
        marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        controls.addView(marker, new LinearLayout.LayoutParams(markSize, touchSize));
        container.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        container.addView(body, new LinearLayout.LayoutParams(-1, -2));
        card.addView(container);

        if (actions.length > 0) {
            var spacer = new View(context);
            controls.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1));
            var menu = new AppCompatImageButton(context);
            menu.setId(R.id.record_actions);
            menu.setImageResource(R.drawable.finora_nav_more);
            menu.setColorFilter(context.getColor(R.color.finora_ink));
            menu.setContentDescription(context.getString(R.string.records_actions_named, name));
            menu.setTooltipText(menu.getContentDescription());
            for (View action : actions) action.setVisibility(View.GONE);
            ViewCompat.setStateDescription(menu, context.getText(R.string.records_actions_collapsed));
            controls.addView(menu, new LinearLayout.LayoutParams(touchSize, touchSize));
            menu.setEnabled(java.util.Arrays.stream(actions).anyMatch(View::isEnabled));
            menu.setOnClickListener(view -> {
                boolean expanded = actions[0].getVisibility() != View.VISIBLE;
                for (View action : actions) action.setVisibility(expanded ? View.VISIBLE : View.GONE);
                ViewCompat.setStateDescription(menu, context.getText(expanded
                        ? R.string.records_actions_expanded : R.string.records_actions_collapsed));
            });
        }
        return card;
    }

    public static void showState(android.widget.TextView status, int message, boolean error) {
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        status.setText(message == 0 ? "" : status.getContext().getText(message));
        status.setTextColor(status.getContext().getColor(error ? R.color.finora_error : R.color.finora_ink));
        status.setBackgroundResource(error ? R.drawable.finora_state_error : R.drawable.finora_state_neutral);
        int padding = size(status.getContext(), R.dimen.finora_space_s);
        status.setPadding(padding, padding, padding, padding);
    }

    private static int size(Context context, int dimension) {
        return context.getResources().getDimensionPixelSize(dimension);
    }
}
