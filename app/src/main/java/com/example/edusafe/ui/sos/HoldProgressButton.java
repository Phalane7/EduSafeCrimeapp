package com.example.edusafe.ui.sos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.edusafe.R;

/**
 * HoldProgressButton
 * ----------------------------------------------------------------------
 * The whole "HOLD FOR HELP" control as ONE self-contained view. It draws,
 * from the outside in:
 *
 *   1. a faint frosted disc
 *   2. a ring of small dots
 *   3. a glass bezel ring (this is also where the hold progress fills in)
 *   4. a thin dark gap
 *   5. the glossy red sphere, with its shine
 *   6. the "HOLD / FOR HELP" text
 *   7. the "Hold for 4 seconds..." caption underneath
 *
 * Everything is sized from the view's own width/height, so it looks the
 * same on small and large phones. The button + caption are centred as one
 * block inside whatever space the layout gives this view.
 *
 * Only a touch that lands INSIDE the round button starts a hold. Letting
 * go early resets everything, so a stray tap never sends an alert.
 *
 * Usage (unchanged from before):
 *   HoldProgressButton hold = findViewById(R.id.holdProgressRing);
 *   hold.setHoldDurationMs(4000);
 *   hold.setOnHoldCompleteListener(() -> { ...send the alert... });
 * ----------------------------------------------------------------------
 */
public class HoldProgressButton extends View {

    public interface OnHoldCompleteListener {
        void onHoldComplete();
    }

    private static final long TICK_MS = 16L; // ~60 frames a second
    private static final int DOT_COUNT = 60;

    // Proportions, as fractions of the outer disc radius (R0)
    private static final float DOT_RING_R = 0.89f;
    private static final float BEZEL_OUTER_R = 0.83f;
    private static final float BEZEL_INNER_R = 0.76f;
    private static final float SPHERE_R = 0.735f;

    // Sizing rules
    private static final float MAX_DIAMETER_DP = 290f;
    private static final float WIDTH_FRACTION = 0.84f;
    private static final float CAPTION_GAP_DP = 14f;
    private static final float BLOCK_VERTICAL_BIAS = 0.4f;
    private static final float PRESSED_SCALE = 0.97f;

    private long holdDurationMs = 4000L;
    private float progress = 0f;
    private boolean isHolding = false;
    private int secondsLeft = 0;
    private long holdStartTime = 0L;
    private OnHoldCompleteListener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Paints
    private final Paint discFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bezelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sphereShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holdText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint forHelpText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keepHoldingText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimOuter = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Geometry, recalculated in onSizeChanged
    private float cx, cy, outerR, sphereR, captionBaseline;
    private final RectF progressOval = new RectF();
    private final Path spherePath = new Path();
    private float shineCx, shineCy, shineR;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isHolding) return;
            long elapsed = System.currentTimeMillis() - holdStartTime;
            progress = Math.min(1f, elapsed / (float) holdDurationMs);
            int left = Math.max(1, (int) Math.ceil((holdDurationMs - elapsed) / 1000.0));
            if (left != secondsLeft) {
                secondsLeft = left;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            invalidate();
            if (elapsed >= holdDurationMs) {
                isHolding = false;
                progress = 0f;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) listener.onHoldComplete();
                invalidate();
            } else {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    public HoldProgressButton(Context context) {
        super(context);
        init(context);
    }

    public HoldProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        setFocusable(true);
        setContentDescription("Hold for 4 seconds to send an emergency alert");

        Typeface bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        Typeface regular = Typeface.DEFAULT;

        discEdge.setStyle(Paint.Style.STROKE);
        discEdge.setColor(Color.argb(70, 255, 255, 255));
        discEdge.setStrokeWidth(dp(1f));

        dotPaint.setColor(Color.argb(200, 255, 255, 255));

        bezelPaint.setStyle(Paint.Style.STROKE);

        gapPaint.setColor(Color.parseColor("#760113"));

        rimInner.setStyle(Paint.Style.STROKE);
        rimInner.setStrokeWidth(dp(1.2f));
        rimOuter.setStyle(Paint.Style.STROKE);
        rimOuter.setStrokeWidth(dp(1f));
        rimOuter.setColor(Color.argb(60, 255, 255, 255));

        shinePaint.setStyle(Paint.Style.FILL);

        holdText.setColor(Color.WHITE);
        holdText.setTypeface(bold);
        holdText.setTextAlign(Paint.Align.CENTER);

        forHelpText.setColor(Color.WHITE);
        forHelpText.setTypeface(bold);
        forHelpText.setTextAlign(Paint.Align.CENTER);

        captionText.setColor(ContextCompat.getColor(context, R.color.muted_pink_text));
        captionText.setTypeface(regular);
        captionText.setTextAlign(Paint.Align.CENTER);
        captionText.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12f, getResources().getDisplayMetrics()));

        countText.setColor(Color.WHITE);
        countText.setTypeface(bold);
        countText.setTextAlign(Paint.Align.CENTER);

        keepHoldingText.setColor(Color.WHITE);
        keepHoldingText.setTypeface(bold);
        keepHoldingText.setTextAlign(Paint.Align.CENTER);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setColor(Color.WHITE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setHoldDurationMs(long ms) {
        this.holdDurationMs = ms;
    }

    public void setOnHoldCompleteListener(OnHoldCompleteListener listener) {
        this.listener = listener;
    }

    // ------------------------------------------------------------------
    // Sizing
    // ------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredW = (int) dp(MAX_DIAMETER_DP);
        int desiredH = (int) (dp(MAX_DIAMETER_DP) + dp(CAPTION_GAP_DP) + dp(24f));
        setMeasuredDimension(resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float captionHeight = captionText.getFontSpacing();
        float gap = dp(CAPTION_GAP_DP);

        // The button is as big as the space allows, but never bigger than the design maximum.
        float diameter = Math.min(w * WIDTH_FRACTION, h - gap - captionHeight);
        diameter = Math.min(diameter, dp(MAX_DIAMETER_DP));
        diameter = Math.max(diameter, dp(120f));

        float blockHeight = diameter + gap + captionHeight;
        float top = Math.max(0f, (h - blockHeight) * BLOCK_VERTICAL_BIAS);

        outerR = diameter / 2f;
        cx = w / 2f;
        cy = top + outerR;
        sphereR = outerR * SPHERE_R;
        captionBaseline = top + diameter + gap + captionHeight - captionText.descent();

        // Frosted disc: slightly brighter toward the upper-left
        discFill.setShader(new LinearGradient(cx - outerR, cy - outerR, cx + outerR, cy + outerR,
                Color.argb(46, 255, 255, 255), Color.argb(14, 255, 255, 255), Shader.TileMode.CLAMP));

        // Dots
        dotPaint.setStyle(Paint.Style.FILL);

        // Glass bezel: bright at both ends of the diagonal, clearer in the middle
        float bezelWidth = (BEZEL_OUTER_R - BEZEL_INNER_R) * outerR;
        bezelPaint.setStrokeWidth(bezelWidth);
        bezelPaint.setShader(new LinearGradient(cx - outerR * 0.8f, cy - outerR * 0.8f,
                cx + outerR * 0.8f, cy + outerR * 0.8f,
                new int[]{Color.argb(72, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(100, 255, 255, 255)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        // Thin bright rim on the bezel's inner edge: strong upper-left, softer lower-right
        rimInner.setShader(new LinearGradient(cx - outerR * 0.8f, cy - outerR * 0.8f,
                cx + outerR * 0.8f, cy + outerR * 0.8f,
                Color.argb(200, 255, 255, 255), Color.argb(70, 255, 255, 255), Shader.TileMode.CLAMP));

        // Red sphere: lit from the upper-left
        spherePaint.setShader(new RadialGradient(cx - sphereR * 0.2f, cy - sphereR * 0.15f, sphereR * 1.3f,
                new int[]{Color.parseColor("#C4192B"), Color.parseColor("#B50218"),
                        Color.parseColor("#8D0314"), Color.parseColor("#7A0113")},
                new float[]{0f, 0.45f, 0.85f, 1f}, Shader.TileMode.CLAMP));

        // Edge shading, so it reads as a sphere and not a flat disc
        sphereShade.setShader(new RadialGradient(cx, cy, sphereR,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(95, 40, 0, 6)},
                new float[]{0f, 0.78f, 1f}, Shader.TileMode.CLAMP));

        // Glossy highlight: a soft glow in the upper part of the sphere (clipped to the sphere when drawn)
        spherePath.reset();
        spherePath.addCircle(cx, cy, sphereR, Path.Direction.CW);
        shineCx = cx;
        shineCy = cy - sphereR * 0.62f;
        shineR = sphereR * 0.95f;
        shinePaint.setShader(new RadialGradient(shineCx, shineCy, shineR,
                new int[]{Color.argb(158, 255, 255, 255), Color.argb(87, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        // Text sizes come from the sphere size
        holdText.setTextSize(sphereR * 0.385f);
        forHelpText.setTextSize(sphereR * 0.1815f);
        countText.setTextSize(sphereR * 0.72f);
        keepHoldingText.setTextSize(sphereR * 0.14f);

        // Progress arc rides on the bezel ring
        float ringRadius = (BEZEL_OUTER_R + BEZEL_INNER_R) / 2f * outerR;
        progressOval.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);
        progressPaint.setStrokeWidth(bezelWidth * 0.9f);
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. frosted disc
        canvas.drawCircle(cx, cy, outerR - 0.5f, discFill);
        canvas.drawCircle(cx, cy, outerR - 0.5f, discEdge);

        // 2. dotted ring
        float dotRadius = Math.max(dp(0.8f), outerR * 0.0072f);
        float dotRing = DOT_RING_R * outerR;
        for (int i = 0; i < DOT_COUNT; i++) {
            double angle = 2.0 * Math.PI * i / DOT_COUNT;
            canvas.drawCircle(cx + (float) Math.cos(angle) * dotRing,
                    cy + (float) Math.sin(angle) * dotRing, dotRadius, dotPaint);
        }

        // 3. glass bezel, with the hold progress filling it clockwise from the top
        float bezelCenterR = (BEZEL_OUTER_R + BEZEL_INNER_R) / 2f * outerR;
        canvas.drawCircle(cx, cy, bezelCenterR, bezelPaint);
        canvas.drawCircle(cx, cy, BEZEL_OUTER_R * outerR, rimOuter);
        canvas.drawCircle(cx, cy, BEZEL_INNER_R * outerR, rimInner);
        if (progress > 0f) {
            canvas.drawArc(progressOval, -90f, 360f * progress, false, progressPaint);
        }

        // 4. dark gap + 5. sphere (+ press-in effect) + 6. text
        canvas.save();
        if (isHolding) canvas.scale(PRESSED_SCALE, PRESSED_SCALE, cx, cy);

        canvas.drawCircle(cx, cy, BEZEL_INNER_R * outerR, gapPaint);
        canvas.drawCircle(cx, cy, sphereR, spherePaint);
        canvas.drawCircle(cx, cy, sphereR, sphereShade);
        canvas.save();
        canvas.clipPath(spherePath);
        canvas.drawCircle(shineCx, shineCy, shineR, shinePaint);
        canvas.restore();

        if (isHolding) {
            canvas.drawText(String.valueOf(secondsLeft), cx, cy + sphereR * 0.24f, countText);
            canvas.drawText("KEEP HOLDING", cx, cy + sphereR * 0.55f, keepHoldingText);
        } else {
            canvas.drawText("HOLD", cx, cy + sphereR * 0.014f, holdText);
            canvas.drawText("FOR HELP", cx, cy + sphereR * 0.31f, forHelpText);
        }
        canvas.restore();

        // 7. caption
        canvas.drawText("Hold for 4 seconds to send alert", cx, captionBaseline, captionText);
    }

    // ------------------------------------------------------------------
    // Touch: only the round button itself reacts
    // ------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isInsideButton(event.getX(), event.getY())) return false;
                startHolding();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopHolding();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private boolean isInsideButton(float x, float y) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= outerR * outerR;
    }

    private void startHolding() {
        isHolding = true;
        secondsLeft = (int) Math.ceil(holdDurationMs / 1000.0);
        holdStartTime = System.currentTimeMillis();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
        invalidate();
    }

    /** Releasing early always resets to zero: only a full hold fires the alert. */
    private void stopHolding() {
        isHolding = false;
        handler.removeCallbacks(tickRunnable);
        progress = 0f;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tickRunnable);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

