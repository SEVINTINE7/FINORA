package lk.finora.app.ui.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import lk.finora.app.R;

/**
 * T66/D70: a horizontal share bar (0–100%) with a dark limit marker. Decorative: the card states the same
 * numbers in words, and the view itself carries a spoken description set by the caller.
 */
public final class LimitBar extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float share;
    private float limit = -1;

    public LimitBar(Context context) { this(context, null); }

    public LimitBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setColor(context.getColor(R.color.finora_warn_fill));
        fill.setColor(context.getColor(R.color.finora_chart_3));
        marker.setColor(context.getColor(R.color.finora_ink));
    }

    /** @param share 0–1 filled; @param limit 0–1 marker position, or negative for none */
    public void set(float share, float limit) {
        this.share = Math.max(0, Math.min(1, share));
        this.limit = limit;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int height = Math.round(20 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float bar = 12 * density;
        float top = (getHeight() - bar) / 2f;
        float radius = bar / 2f;
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float left = getPaddingLeft();
        rect.set(left, top, left + width, top + bar);
        canvas.drawRoundRect(rect, radius, radius, track);
        if (share > 0) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float end = width * share;
            rect.set(rtl ? left + width - end : left, top, rtl ? left + width : left + end, top + bar);
            canvas.drawRoundRect(rect, radius, radius, fill);
        }
        if (limit >= 0) {
            float x = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? left + width * (1 - limit) : left + width * limit;
            canvas.drawRect(x - density, 0, x + density, getHeight(), marker);
        }
    }
}
