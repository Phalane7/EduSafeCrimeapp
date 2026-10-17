package com.example.edusafe.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * NotchedBarDrawable
 * ----------------------------------------------------------------------
 * The shape behind the bottom navigation bar: a flat bar with rounded top
 * corners and a round "scoop" cut out of the top edge, centred, where the
 * raised SOS button sits. The corners where the scoop meets the top edge
 * are softly rounded too.
 *
 * It is a plain Drawable, so it can be used as any view's background.
 * ----------------------------------------------------------------------
 */
public class NotchedBarDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arcRect = new RectF();

    private final float cornerRadius;  // outer top corners
    private final float notchRadius;   // the scoop around the SOS button
    private final float filletRadius;  // softening where the scoop meets the top edge

    public NotchedBarDrawable(Context context, int fillColor) {
        float density = context.getResources().getDisplayMetrics().density;
        cornerRadius = 28f * density;
        notchRadius = 46f * density;
        filletRadius = 12f * density;

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(density);
        edgePaint.setColor(Color.argb(55, 255, 255, 255));
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        buildPath(bounds);
    }

    private void buildPath(Rect b) {
        path.reset();

        float left = b.left, top = b.top, right = b.right, bottom = b.bottom;
        float cx = (left + right) / 2f;
        float cr = cornerRadius, n = notchRadius, f = filletRadius;

        // Geometry of the small rounding circles sitting either side of the scoop.
        // Each is tangent to the top edge and to the scoop circle.
        float a = (float) Math.toDegrees(Math.asin(f / (n + f)));
        float xf = (float) Math.sqrt((n + f) * (n + f) - f * f);

        path.moveTo(left, bottom);
        path.lineTo(left, top + cr);

        // top-left outer corner
        arcRect.set(left, top, left + 2 * cr, top + 2 * cr);
        path.arcTo(arcRect, 180f, 90f);

        // along the top edge to the left rounding circle
        path.lineTo(cx - xf, top);

        // left rounding circle: from its top, clockwise, to where it meets the scoop
        arcRect.set(cx - xf - f, top, cx - xf + f, top + 2 * f);
        path.arcTo(arcRect, 270f, 90f - a);

        // the scoop itself, counter-clockwise through its lowest point
        arcRect.set(cx - n, top - n, cx + n, top + n);
        path.arcTo(arcRect, 180f - a, -(180f - 2f * a));

        // right rounding circle: from the scoop back up to the top edge
        arcRect.set(cx + xf - f, top, cx + xf + f, top + 2 * f);
        path.arcTo(arcRect, 180f + a, 90f - a);

        // along the top edge, then the top-right outer corner
        path.lineTo(right - cr, top);
        arcRect.set(right - 2 * cr, top, right, top + 2 * cr);
        path.arcTo(arcRect, 270f, 90f);

        path.lineTo(right, bottom);
        path.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(path, fillPaint);
        canvas.drawPath(path, edgePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
