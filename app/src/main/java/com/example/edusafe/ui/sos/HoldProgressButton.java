package com.example.edusafe.ui.sos;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Glowing wave SOS control. Only a completed hold invokes the listener. */
public class HoldProgressButton extends View {
    public interface OnHoldCompleteListener { void onHoldComplete(); }
    private static final long FRAME_MS = 16L;
    private long holdDurationMs = 4000L;
    private long startMs;
    private boolean holding;
    private float progress;
    private OnHoldCompleteListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private final RectF arc = new RectF();
    private float cx, cy, radius, innerRadius;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!holding) return;
            progress = Math.min(1f, (SystemClock.uptimeMillis() - startMs) / (float) holdDurationMs);
            invalidate();
            if (progress >= 1f) {
                holding = false;
                progress = 0f;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) listener.onHoldComplete();
                invalidate();
            } else handler.postDelayed(this, FRAME_MS);
        }
    };
    public HoldProgressButton(Context context) { super(context); init(); }
    public HoldProgressButton(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public HoldProgressButton(Context context, AttributeSet attrs, int style) { super(context, attrs, style); init(); }
    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null); // needed for the soft ring glows
        setClickable(true);
        setFocusable(true);
        setContentDescription("Hold for help for four seconds to activate emergency SOS");
    }
    public void setHoldDurationMs(long ms) { holdDurationMs = Math.max(1000L, ms); }
    public void setOnHoldCompleteListener(OnHoldCompleteListener l) { listener = l; }
    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(Math.min(w, h) * 0.475f, dp(183));
        innerRadius = radius * 0.48f;
    }
    private void ring(Canvas c, float r, int color, float width, float glow) {
        p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.STROKE);
        p.setColor(color); p.setStrokeWidth(width); p.setStrokeCap(Paint.Cap.ROUND);
        if (glow > 0) p.setShadowLayer(glow, 0, 0, color);
        c.drawCircle(cx, cy, r, p);
        p.clearShadowLayer();
    }
    private void waveRing(Canvas c, float r, float amplitude, int color, float width, float glow, float phase) {
        wave.reset();
        for (int i = 0; i <= 360; i += 2) {
            double a = Math.toRadians(i);
            float rr = r + (float)Math.sin(a * 9 + phase) * amplitude
                    + (float)Math.sin(a * 5 - phase * 0.6) * amplitude * 0.3f;
            float x = cx + (float)Math.cos(a) * rr;
            float y = cy + (float)Math.sin(a) * rr;
            if (i == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        wave.close();
        p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width); p.setColor(color);
        if (glow > 0) p.setShadowLayer(glow, 0, 0, color);
        c.drawPath(wave, p);
        p.clearShadowLayer();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0) return;
        // Broad red illumination behind the wavy outer rings.
        p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, radius * 1.08f,
                new int[]{0x66FF151B, 0x55EF0013, 0x227E0009, 0x007E0009},
                new float[]{0f, 0.48f, 0.79f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 1.08f, p); p.setShader(null);
        waveRing(canvas, radius * .95f, dp(7.0f), 0x66FF3840, dp(1.2f), dp(8), 0.4f);
        waveRing(canvas, radius * .87f, dp(5.3f), 0x88FF2B35, dp(1.5f), dp(9), 1.1f);
        waveRing(canvas, radius * .79f, dp(4.1f), 0xBBFF3C43, dp(1.7f), dp(10), 2.1f);
        waveRing(canvas, radius * .71f, dp(2.7f), 0xFFFF5559, dp(2.1f), dp(10), 2.9f);
        ring(canvas, radius * .65f, 0xFFFF2A32, dp(9), dp(16));
        ring(canvas, radius * .62f, 0xFFFF6D71, dp(1.8f), dp(8));
        ring(canvas, radius * .57f, 0xFFDC0C20, dp(8), dp(11));
        ring(canvas, radius * .54f, 0xFFFF4C56, dp(1.7f), dp(6));
        // White centre, soft edge shadow and subtle shading.
        p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFF7F7); p.setShadowLayer(dp(12), 0, dp(2), 0xFFFF3342);
        canvas.drawCircle(cx, cy, innerRadius, p); p.clearShadowLayer();
        p.setShader(new RadialGradient(cx - innerRadius * .23f, cy - innerRadius * .3f,
                innerRadius * 1.55f, 0xFFFFFFFF, 0xFFECE6E7, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, innerRadius * .97f, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.2f)); p.setColor(0xFFFFCED0);
        canvas.drawCircle(cx, cy, innerRadius, p);
        // Text remains legible on the small SOS centre.
        p.reset(); p.setAntiAlias(true); p.setColor(0xFFD41122);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        if (holding) {
            int seconds = Math.max(1, (int)Math.ceil((holdDurationMs * (1f - progress)) / 1000f));
            p.setTextSize(innerRadius * .73f);
            canvas.drawText(String.valueOf(seconds), cx, cy + innerRadius * .13f, p);
            p.setTextSize(innerRadius * .20f);
            canvas.drawText("KEEP HOLDING", cx, cy + innerRadius * .49f, p);
        } else {
            p.setTextSize(innerRadius * .35f);
            canvas.drawText("HOLD", cx, cy - innerRadius * .02f, p);
            p.setTextSize(innerRadius * .26f);
            canvas.drawText("FOR HELP", cx, cy + innerRadius * .34f, p);
        }
        if (progress > 0f) {
            p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(5)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.WHITE);
            float rr = radius * .61f;
            arc.set(cx - rr, cy - rr, cx + rr, cy + rr);
            canvas.drawArc(arc, -90, 360 * progress, false, p);
        }
    }
    private boolean inside(float x, float y) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= innerRadius * innerRadius;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!inside(event.getX(), event.getY())) return false;
                holding = true; progress = 0f; startMs = SystemClock.uptimeMillis();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                handler.removeCallbacks(ticker); handler.post(ticker); invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (holding && !inside(event.getX(), event.getY())) cancelHold();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelHold(); return true;
            default: return super.onTouchEvent(event);
        }
    }
    private void cancelHold() {
        holding = false; progress = 0f; handler.removeCallbacks(ticker); invalidate();
    }
    @Override protected void onDetachedFromWindow() {
        cancelHold(); super.onDetachedFromWindow();
    }
}


