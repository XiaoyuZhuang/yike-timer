package com.yike.timer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    private Ui() {}

    static final int BG = Color.BLACK;
    static final int TEXT = 0xFFECECEC;
    static final int MUTED = 0xFF858585;
    static final int LINE = 0xFF303030;

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static float dpf(Context c, float value) {
        return value * c.getResources().getDisplayMetrics().density;
    }

    static TextView tv(Context c, String text, float sizeSp, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        return v;
    }

    static GradientDrawable box(Context c, int fill, int strokeColor, float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        if (strokeColor != 0) d.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        d.setCornerRadius(dpf(c, radiusDp));
        return d;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpw(float weight, int h) {
        return new LinearLayout.LayoutParams(0, h, weight);
    }

    static void margins(View v, float l, float t, float r, float b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) raw;
        p.setMargins(dp(v.getContext(), l), dp(v.getContext(), t), dp(v.getContext(), r), dp(v.getContext(), b));
        v.setLayoutParams(p);
    }

    static View spacer(Context c, int widthDp, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), dp(c, heightDp)));
        return v;
    }

    /** Fill color with a single bottom hairline. */
    static class LineDrawable extends Drawable {
        private final int fill;
        private final int line;
        private final int lineWidth;
        private final Paint paint = new Paint();

        LineDrawable(Context c, int fill, int line, float lineDp) {
            this.fill = fill;
            this.line = line;
            this.lineWidth = Math.max(1, dp(c, lineDp));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            paint.setColor(fill);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(b, paint);
            paint.setColor(line);
            canvas.drawRect(b.left, b.bottom - lineWidth, b.right, b.bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    /** Fixed-column wrapping grid with optional column spans. */
    static class GridWrapLayout extends ViewGroup {
        static class LP extends MarginLayoutParams {
            int span = 1;

            LP(int span) {
                super(MATCH_PARENT, WRAP_CONTENT);
                this.span = span;
            }
        }

        private final int columns;
        private final int hGap;
        private final int vGap;
        private int[] xs = new int[0];
        private int[] ys = new int[0];
        private int[] ws = new int[0];

        GridWrapLayout(Context c, int columns, float hGapDp, float vGapDp) {
            super(c);
            this.columns = columns;
            this.hGap = dp(c, hGapDp);
            this.vGap = dp(c, vGapDp);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int avail = width - getPaddingLeft() - getPaddingRight();
            int count = getChildCount();
            if (xs.length < count) {
                xs = new int[count];
                ys = new int[count];
                ws = new int[count];
            }
            int totalHeight = getPaddingTop() + getPaddingBottom();
            int i = 0;
            while (i < count) {
                int col = 0;
                int rowHeight = 0;
                int start = i;
                int startY = totalHeight;
                while (i < count && col < columns) {
                    View child = getChildAt(i);
                    LP lp = child.getLayoutParams() instanceof LP ? (LP) child.getLayoutParams() : new LP(1);
                    if (child.getLayoutParams() != lp) child.setLayoutParams(lp);
                    int span = Math.max(1, Math.min(columns - col, lp.span));
                    int cellW = (avail - hGap * (columns - 1)) / columns;
                    int cw = cellW * span + hGap * (span - 1);
                    child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, cw), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightSpec), MeasureSpec.AT_MOST));
                    xs[i] = getPaddingLeft() + col * (cellW + hGap);
                    ys[i] = startY;
                    ws[i] = cw;
                    rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
                    col += span;
                    i++;
                }
                int rowH = 0;
                for (int k = start; k < i; k++) rowH = Math.max(rowH, getChildAt(k).getMeasuredHeight());
                for (int k = start; k < i; k++) {
                    // rows keep their own max height for vertical centering
                    ys[k] = startY + (rowH - getChildAt(k).getMeasuredHeight()) / 2;
                }
                totalHeight = startY + rowH + (i < count ? vGap : 0);
            }
            int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY
                    ? MeasureSpec.getSize(heightSpec) : totalHeight;
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.layout(xs[i], ys[i], xs[i] + ws[i], ys[i] + child.getMeasuredHeight());
            }
        }

        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return new LP(1);
        }

        @Override
        public LayoutParams generateLayoutParams(AttributeSet attrs) {
            return new LP(1);
        }

        @Override
        protected boolean checkLayoutParams(LayoutParams p) {
            return p instanceof LP;
        }
    }

    /** Wrapping row layout: every child keeps its natural width. */
    static class FlowLayout extends ViewGroup {
        private final int hGap;
        private final int vGap;
        private int[] xs = new int[0];
        private int[] ys = new int[0];

        FlowLayout(Context c, float hGapDp, float vGapDp) {
            super(c);
            this.hGap = dp(c, hGapDp);
            this.vGap = dp(c, vGapDp);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int avail = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            int count = getChildCount();
            if (xs.length < count) {
                xs = new int[count];
                ys = new int[count];
            }
            int x = 0;
            int y = getPaddingTop();
            int rowH = 0;
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                MarginLayoutParams lp = child.getLayoutParams() instanceof MarginLayoutParams
                        ? (MarginLayoutParams) child.getLayoutParams()
                        : new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                child.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                if (x > 0 && x + cw > avail) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }
                xs[i] = getPaddingLeft() + x + lp.leftMargin;
                ys[i] = y + lp.topMargin;
                x += cw + hGap;
                rowH = Math.max(rowH, ch);
            }
            int measured = y + rowH + getPaddingBottom();
            int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY
                    ? MeasureSpec.getSize(heightSpec) : measured;
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.layout(xs[i], ys[i], xs[i] + child.getMeasuredWidth(),
                        ys[i] + child.getMeasuredHeight());
            }
        }

        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }

        @Override
        public LayoutParams generateLayoutParams(AttributeSet attrs) {
            return new MarginLayoutParams(getContext(), attrs);
        }

        @Override
        protected boolean checkLayoutParams(LayoutParams p) {
            return p instanceof MarginLayoutParams;
        }
    }

    /** Large stroked countdown digits, mirroring the -webkit-text-stroke layers. */
    static class DigitView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface typeface = Typeface.create("sans-serif", Typeface.NORMAL);
        private String text = "";
        private boolean longText = false;
        private boolean portrait = true;
        private int weightLevel = 1;
        private int screenWidthDp = 390;
        private boolean solid = false;

        DigitView(Context c) {
            super(c);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            fillPaint.setStyle(Paint.Style.FILL);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setState(String text, boolean portrait, int screenWidthDp, int weightLevel) {
            this.text = text == null ? "" : text;
            this.longText = this.text.length() > 5;
            setMetrics(portrait, screenWidthDp, weightLevel);
        }

        void setMetrics(boolean portrait, int screenWidthDp, int weightLevel) {
            this.portrait = portrait;
            this.screenWidthDp = screenWidthDp;
            this.weightLevel = Math.max(1, Math.min(5, weightLevel));
            invalidate();
        }

        void setSolid(boolean solid) {
            if (this.solid == solid) return;
            this.solid = solid;
            invalidate();
        }

        private float textSizeDp() {
            if (portrait) {
                return longText ? clamp(screenWidthDp * 0.14f, 50, 68) : clamp(screenWidthDp * 0.23f, 76, 104);
            }
            return longText ? clamp(screenWidthDp * 0.13f, 64, 112) : clamp(screenWidthDp * 0.20f, 90, 168);
        }

        private float[] strokes() {
            float[] front = {0.55f, 0.85f, 1.15f, 1.45f, 1.75f};
            float[] back = {1f, 1.3f, 1.6f, 1.9f, 2.2f};
            int idx = weightLevel - 1;
            return new float[]{front[idx], back[idx]};
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (text.isEmpty()) return;
            float sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, textSizeDp(), getResources().getDisplayMetrics());
            float tracking = -0.09f * sizePx;
            fillPaint.setTypeface(typeface);
            fillPaint.setTextSize(sizePx);
            fillPaint.setFontFeatureSettings("tnum");
            Paint.FontMetrics fm = fillPaint.getFontMetrics();
            float baseline = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            float cx = getWidth() / 2f;
            if (solid) {
                fillPaint.setColor(0xFFE8E8E8);
                drawTracked(canvas, text, cx, baseline, tracking, fillPaint);
                return;
            }
            float[] s = strokes();
            strokePaint.setTypeface(typeface);
            strokePaint.setTextSize(sizePx);
            strokePaint.setFontFeatureSettings("tnum");
            fillPaint.setColor(0xFF040404);

            canvas.save();
            canvas.scale(1.014f, 1.014f, getWidth() / 2f, getHeight() / 2f);
            strokePaint.setColor(0xFF484848);
            strokePaint.setStrokeWidth(dpf(getContext(), s[1]));
            drawTracked(canvas, text, cx, baseline, tracking, fillPaint);
            drawTracked(canvas, text, cx, baseline, tracking, strokePaint);
            canvas.restore();

            strokePaint.setColor(0xFFE1E1E1);
            strokePaint.setStrokeWidth(dpf(getContext(), s[0]));
            drawTracked(canvas, text, cx, baseline, tracking, fillPaint);
            drawTracked(canvas, text, cx, baseline, tracking, strokePaint);
        }

        private void drawTracked(Canvas canvas, String value, float cx, float baseline, float tracking, Paint paint) {
            float width = 0;
            for (int i = 0; i < value.length(); i++) {
                width += paint.measureText(value, i, i + 1) + tracking;
            }
            width -= tracking;
            float x = cx - width / 2f;
            for (int i = 0; i < value.length(); i++) {
                canvas.drawText(value, i, i + 1, x, baseline, paint);
                x += paint.measureText(value, i, i + 1) + tracking;
            }
        }
    }

    static class IconView extends View {
        static final int HOME = 0;
        static final int RECORDS = 1;
        static final int SETTINGS = 2;
        static final int PAUSE = 3;
        static final int PLAY = 4;
        static final int END = 5;
        static final int BELL = 6;
        static final int VIBRATE_PHONE = 7;
        static final int BELL_VIBRATE = 8;
        static final int ORIENT_PORTRAIT = 9;
        static final int ORIENT_LANDSCAPE = 10;
        static final int ORIENT_ROTATE = 11;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private int kind;
        private int color = 0xFF696969;

        IconView(Context c, int kind) {
            super(c);
            this.kind = kind;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            if (kind >= PAUSE) {
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
            }
        }

        void setKind(int kind) {
            this.kind = kind;
            invalidate();
        }

        void setIconColor(int color) {
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight());
            float scale = size / 24f;
            canvas.save();
            canvas.translate((getWidth() - 24f * scale) / 2f, (getHeight() - 24f * scale) / 2f);
            canvas.scale(scale, scale);
            paint.setColor(color);
            path.reset();
            switch (kind) {
                case HOME:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.35f);
                    path.moveTo(4, 11.5f);
                    path.lineTo(12, 4);
                    path.lineTo(20, 11.5f);
                    path.moveTo(6.5f, 10);
                    path.lineTo(6.5f, 20);
                    path.lineTo(17.5f, 20);
                    path.lineTo(17.5f, 10);
                    path.moveTo(10, 20);
                    path.lineTo(10, 14);
                    path.lineTo(14, 14);
                    path.lineTo(14, 20);
                    canvas.drawPath(path, paint);
                    break;
                case RECORDS:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.35f);
                    canvas.drawCircle(12, 12, 8.5f, paint);
                    path.moveTo(12, 7);
                    path.lineTo(12, 12);
                    path.lineTo(15.5f, 14);
                    canvas.drawPath(path, paint);
                    break;
                case SETTINGS:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.35f);
                    path.moveTo(5, 7);
                    path.lineTo(19, 7);
                    path.moveTo(5, 12);
                    path.lineTo(19, 12);
                    path.moveTo(5, 17);
                    path.lineTo(19, 17);
                    canvas.drawPath(path, paint);
                    canvas.drawCircle(9, 7, 1.7f, paint);
                    canvas.drawCircle(15, 12, 1.7f, paint);
                    canvas.drawCircle(10, 17, 1.7f, paint);
                    break;
                case PAUSE:
                    paint.setStrokeWidth(1.4f);
                    path.moveTo(9.5f, 7);
                    path.lineTo(9.5f, 17);
                    path.moveTo(14.5f, 7);
                    path.lineTo(14.5f, 17);
                    canvas.drawPath(path, paint);
                    break;
                case PLAY:
                    paint.setStrokeWidth(1.4f);
                    path.moveTo(9.5f, 7.5f);
                    path.lineTo(16.5f, 12);
                    path.lineTo(9.5f, 16.5f);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                case END:
                    paint.setStrokeWidth(1.4f);
                    path.moveTo(8, 8);
                    path.lineTo(16, 16);
                    path.moveTo(16, 8);
                    path.lineTo(8, 16);
                    canvas.drawPath(path, paint);
                    break;
                case BELL:
                    paint.setStrokeWidth(1.35f);
                    bellPath();
                    canvas.drawPath(path, paint);
                    path.reset();
                    clapperPath();
                    canvas.drawPath(path, paint);
                    break;
                case VIBRATE_PHONE:
                    paint.setStrokeWidth(1.35f);
                    rect.set(9.1f, 3.2f, 14.9f, 20.8f);
                    canvas.drawRoundRect(rect, 1.8f, 1.8f, paint);
                    path.reset();
                    path.moveTo(10.9f, 5.6f);
                    path.lineTo(13.1f, 5.6f);
                    path.moveTo(6.9f, 7.8f);
                    path.lineTo(5.3f, 7.8f);
                    path.moveTo(6.2f, 12f);
                    path.lineTo(4.5f, 12f);
                    path.moveTo(6.9f, 16.2f);
                    path.lineTo(5.3f, 16.2f);
                    path.moveTo(17.1f, 7.8f);
                    path.lineTo(18.7f, 7.8f);
                    path.moveTo(17.8f, 12f);
                    path.lineTo(19.5f, 12f);
                    path.moveTo(17.1f, 16.2f);
                    path.lineTo(18.7f, 16.2f);
                    canvas.drawPath(path, paint);
                    break;
                case BELL_VIBRATE:
                    paint.setStrokeWidth(1.3f);
                    canvas.save();
                    canvas.scale(0.78f, 0.78f, 12f, 11f);
                    bellPath();
                    canvas.drawPath(path, paint);
                    path.reset();
                    clapperPath();
                    canvas.drawPath(path, paint);
                    canvas.restore();
                    path.reset();
                    path.moveTo(2.6f, 8.6f);
                    path.lineTo(4.4f, 8.6f);
                    path.moveTo(2.6f, 12f);
                    path.lineTo(4.4f, 12f);
                    path.moveTo(2.6f, 15.4f);
                    path.lineTo(4.4f, 15.4f);
                    path.moveTo(19.6f, 8.6f);
                    path.lineTo(21.4f, 8.6f);
                    path.moveTo(19.6f, 12f);
                    path.lineTo(21.4f, 12f);
                    path.moveTo(19.6f, 15.4f);
                    path.lineTo(21.4f, 15.4f);
                    canvas.drawPath(path, paint);
                    break;
                case ORIENT_PORTRAIT:
                    paint.setStrokeWidth(1.35f);
                    rect.set(8f, 3f, 16f, 21f);
                    canvas.drawRoundRect(rect, 1.8f, 1.8f, paint);
                    path.reset();
                    path.moveTo(10.6f, 5.4f);
                    path.lineTo(13.4f, 5.4f);
                    canvas.drawPath(path, paint);
                    break;
                case ORIENT_LANDSCAPE:
                    paint.setStrokeWidth(1.35f);
                    rect.set(3f, 8f, 21f, 16f);
                    canvas.drawRoundRect(rect, 1.8f, 1.8f, paint);
                    path.reset();
                    path.moveTo(5.4f, 10.6f);
                    path.lineTo(5.4f, 13.4f);
                    canvas.drawPath(path, paint);
                    break;
                case ORIENT_ROTATE:
                    paint.setStrokeWidth(1.4f);
                    rect.set(5.2f, 5.2f, 18.8f, 18.8f);
                    canvas.drawArc(rect, 50f, 280f, false, paint);
                    path.reset();
                    path.moveTo(16.33f, 9.5f);
                    path.lineTo(18.79f, 10.16f);
                    path.lineTo(19.45f, 7.7f);
                    canvas.drawPath(path, paint);
                    break;
                default:
                    break;
            }
            canvas.restore();
        }

        private void bellPath() {
            path.reset();
            path.moveTo(5.9f, 17.1f);
            path.cubicTo(7.2f, 15.7f, 7.7f, 14.4f, 7.7f, 11.7f);
            path.cubicTo(7.7f, 8.1f, 9.5f, 5.2f, 12f, 4.2f);
            path.cubicTo(14.5f, 5.2f, 16.3f, 8.1f, 16.3f, 11.7f);
            path.cubicTo(16.3f, 14.4f, 16.8f, 15.7f, 18.1f, 17.1f);
            path.lineTo(5.9f, 17.1f);
            path.close();
        }

        private void clapperPath() {
            path.moveTo(10.3f, 19.5f);
            path.cubicTo(10.5f, 20.8f, 13.5f, 20.8f, 13.7f, 19.5f);
        }
    }

    interface OnChecked {
        void onChecked(boolean checked);
    }

    static class SwitchView extends View {
        private boolean checked;
        private OnChecked listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwitchView(Context c) {
            super(c);
            setClickable(true);
            setOnClickListener(v -> {
                checked = !checked;
                invalidate();
                if (listener != null) listener.onChecked(checked);
            });
        }

        void setChecked(boolean value) {
            checked = value;
            invalidate();
        }

        void setOnChecked(OnChecked l) {
            listener = l;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float stroke = dpf(getContext(), 1);
            float radius = h / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(checked ? 0xFFBBBBBB : 0xFF444444);
            Rect r = new Rect((int) (stroke / 2f), (int) (stroke / 2f), (int) (w - stroke / 2f), (int) (h - stroke / 2f));
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, paint);

            float knob = h - dpf(getContext(), 8);
            float left = dpf(getContext(), 3) + (checked ? dpf(getContext(), 16) : 0);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked ? 0xFFE5E5E5 : 0xFF666666);
            canvas.drawCircle(left + knob / 2f, h / 2f, knob / 2f, paint);
        }
    }

    static class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx = Integer.MAX_VALUE;

        MaxHeightScrollView(Context c) {
            super(c);
        }

        void setMaxHeightPx(int px) {
            maxHeightPx = px;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            if (getMeasuredHeight() > maxHeightPx) {
                setMeasuredDimension(getMeasuredWidth(), maxHeightPx);
            }
        }
    }

    static class PixelChart extends View {
        private int cells = 1;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        PixelChart(Context c) {
            super(c);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(BG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1, dp(c, 1)));
            stroke.setColor(0xFFD8D8D8);
        }

        void setCells(int cells) {
            this.cells = Math.max(1, cells);
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            float cell = dpf(getContext(), 7);
            float gap = dpf(getContext(), 2);
            int width = (int) Math.max(dpf(getContext(), 88), cells * cell + (cells - 1) * gap + 2);
            setMeasuredDimension(width, Math.round(cell) + 2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cell = dpf(getContext(), 7);
            float gap = dpf(getContext(), 2);
            float total = cells * cell + (cells - 1) * gap;
            float x = getWidth() - total;
            float top = (getHeight() - cell) / 2f;
            for (int i = 0; i < cells; i++) {
                canvas.drawRect(x, top, x + cell, top + cell, fill);
                canvas.drawRect(x, top, x + cell, top + cell, stroke);
                x += cell + gap;
            }
        }
    }
}
