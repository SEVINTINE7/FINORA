package lk.finora.app.ui.common;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import lk.finora.app.R;

public final class StateView {
    private final ProgressBar progress;
    private final TextView message;

    public StateView(View container) {
        progress = container.findViewById(R.id.state_progress);
        message = container.findViewById(R.id.state_message);
    }

    public void loading(int text) {
        render(true, text);
    }

    public void empty(int text) {
        render(false, text);
    }

    public void error(int text) {
        render(false, text);
    }

    public void clear() {
        render(false, 0);
    }

    private void render(boolean loading, int text) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        message.setVisibility(text == 0 ? View.GONE : View.VISIBLE);
        if (text != 0) {
            message.setText(text);
        }
    }
}
