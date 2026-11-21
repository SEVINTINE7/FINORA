package lk.finora.app.ui.common;

import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import lk.finora.app.R;

public final class StateView {
    private final View container;
    private final ProgressBar progress;
    private final ImageView icon;
    private final TextView message;

    public StateView(View container) {
        this.container = container;
        progress = container.findViewById(R.id.state_progress);
        icon = container.findViewById(R.id.state_icon);
        message = container.findViewById(R.id.state_message);
    }

    public void loading(int text) {
        render(text, R.drawable.finora_state_neutral, R.color.finora_ink, 0);
    }

    public void empty(int text) {
        render(text, R.drawable.finora_state_neutral, R.color.finora_ink, R.drawable.finora_ic_empty);
    }

    public void success(int text) {
        render(text, R.drawable.finora_state_success, R.color.finora_green, R.drawable.finora_ic_check);
    }

    public void error(int text) {
        render(text, R.drawable.finora_state_error, R.color.finora_error, R.drawable.finora_ic_alert);
    }

    public void clear() {
        container.setVisibility(View.GONE);
    }

    private void render(int text, int surface, int color, int symbol) {
        int tint = ContextCompat.getColor(container.getContext(), color);
        container.setBackgroundResource(surface);
        progress.setVisibility(symbol == 0 ? View.VISIBLE : View.GONE);
        icon.setVisibility(symbol == 0 ? View.GONE : View.VISIBLE);
        if (symbol != 0) {
            icon.setImageResource(symbol);
            icon.setColorFilter(tint);
        }
        message.setTextColor(tint);
        message.setText(text);
        container.setVisibility(View.VISIBLE);
    }
}
