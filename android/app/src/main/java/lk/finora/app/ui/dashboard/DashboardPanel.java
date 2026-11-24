package lk.finora.app.ui.dashboard;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.repo.DashboardRepository;

public final class DashboardPanel {
    private static final int[] PALETTE = {0xff5ee7af, 0xff72aafa, 0xffffb465, 0xffe793d9, 0xffdae878, 0xffa78bfa};
    private final DashboardViewModel model;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final LinearLayout results;
    private final TextView month;
    private final Button previous;
    private final Button next;

    public DashboardPanel(AppCompatActivity activity, DashboardViewModel model) {
        this.model = model;
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        var navigation = new LinearLayout(activity);
        previous = button(R.string.dashboard_previous, () -> model.step(-1));
        next = button(R.string.dashboard_next, () -> model.step(1));
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(navigation);
        month = line(content, "", true);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        model.work.changes.observe(activity, ignored -> {
            model.refreshIfQueued();
            render();
        });
    }

    public ScrollView view() { return scroll; }

    private android.content.Context getContext() { return scroll.getContext(); }

    private android.content.res.Resources getResources() { return scroll.getResources(); }

    private Button button(int label, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView line(LinearLayout target, CharSequence text, boolean heading) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(getContext().getColor(R.color.finora_on_navy));
        view.setTextSize(heading ? 20 : 16);
        int spacing = dp(6);
        view.setPadding(0, spacing, 0, spacing);
        ViewCompat.setAccessibilityHeading(view, heading);
        target.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void render() {
        results.removeAllViews();
        if (!model.work.isCurrent()) return;
        month.setText(monthLabel(model.month));
        previous.setEnabled(!model.work.busy() && model.canStep(-1));
        next.setEnabled(!model.work.busy() && model.canStep(1));
        if (model.work.busy()) {
            line(results, getContext().getText(R.string.records_loading), false);
        } else if (model.work.error != 0) {
            line(results, getContext().getText(model.work.error), false);
            results.addView(button(R.string.records_retry, model::refresh));
        } else if (model.snapshot != null) {
            showSnapshot(model.snapshot);
        }
    }

    private void showSnapshot(DashboardRepository.Snapshot value) {
        line(results, getContext().getString(R.string.dashboard_income, amount(value.income())), false);
        line(results, getContext().getString(R.string.dashboard_expense, amount(value.expense())), false);
        line(results, getContext().getString(R.string.dashboard_net, amount(value.net())), false);
        if (value.income().signum() == 0 && value.expense().signum() == 0) {
            line(results, getContext().getText(R.string.dashboard_empty), false);
        }
        line(results, getContext().getText(R.string.dashboard_categories), true);
        if (value.byCategory().isEmpty()) {
            line(results, getContext().getText(R.string.dashboard_no_expenses), false);
        } else {
            results.addView(new Chart(value, true), new LinearLayout.LayoutParams(-1, dp(160)));
            String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
            for (int i = 0; i < value.byCategory().size(); i++) {
                var row = value.byCategory().get(i);
                String name = row.name() == null ? getContext().getString(R.string.dashboard_uncategorized)
                        : "si".equals(language) && row.nameSi() != null && !row.nameSi().isBlank()
                                ? row.nameSi() : row.name();
                line(results, getContext().getString(R.string.dashboard_category_amount, name, amount(row.total())),
                        false).setTextColor(PALETTE[i % PALETTE.length]);
            }
        }
        line(results, getContext().getText(R.string.dashboard_trend), true);
        results.addView(new Chart(value, false), new LinearLayout.LayoutParams(-1, dp(160)));
        for (var row : value.trend()) {
            line(results, monthLabel(YearMonth.parse(row.month())), true);
            line(results, getContext().getString(R.string.dashboard_income, amount(row.income())), false);
            line(results, getContext().getString(R.string.dashboard_expense, amount(row.expense())), false);
            line(results, getContext().getString(R.string.dashboard_net, amount(row.net())), false);
        }
    }

    private String monthLabel(YearMonth value) {
        return value.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy",
                getResources().getConfiguration().getLocales().get(0)));
    }

    private String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class Chart extends View {
        private final DashboardRepository.Snapshot snapshot;
        private final boolean pie;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Chart(DashboardRepository.Snapshot snapshot, boolean pie) {
            super(DashboardPanel.this.getContext());
            this.snapshot = snapshot;
            this.pie = pie;
            // The exact labeled values immediately after the graphic are the accessible equivalent.
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (pie) drawPie(canvas); else drawTrend(canvas);
        }

        private void drawPie(Canvas canvas) {
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(8);
            if (radius <= 0) return;
            float x = getWidth() / 2f;
            float y = getHeight() / 2f;
            var bounds = new RectF(x - radius, y - radius, x + radius, y + radius);
            float angle = -90f;
            List<DashboardRepository.Category> categories = snapshot.byCategory();
            for (int i = 0; i < categories.size(); i++) {
                float sweep = i == categories.size() - 1 ? 270f - angle
                        : 360f * chartRatio(categories.get(i).total(), snapshot.expense());
                paint.setColor(PALETTE[i % PALETTE.length]);
                canvas.drawArc(bounds, angle, sweep, true, paint);
                angle += sweep;
            }
        }

        private void drawTrend(Canvas canvas) {
            BigDecimal max = BigDecimal.ONE;
            for (var item : snapshot.trend()) max = max.max(item.net().abs());
            float width = getWidth() / 6f;
            float mid = getHeight() / 2f;
            paint.setColor(Color.LTGRAY);
            canvas.drawLine(0, mid, getWidth(), mid, paint);
            for (int i = 0; i < 6; i++) {
                BigDecimal net = snapshot.trend().get(i).net();
                float end = mid - chartRatio(net, max) * (mid - dp(8));
                paint.setColor(net.signum() < 0 ? PALETTE[2] : PALETTE[0]);
                canvas.drawRect(width * (i + 0.18f), Math.min(mid, end),
                        width * (i + 0.82f), Math.max(mid, end), paint);
            }
        }

        private float chartRatio(BigDecimal value, BigDecimal max) {
            return value.divide(max, MathContext.DECIMAL32).floatValue();
        }
    }
}
