package com.example.edusafe.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.example.edusafe.R;

/**
 * NotchedNavBar
 * ----------------------------------------------------------------------
 * A vertical LinearLayout that paints itself as the navy, notched bar
 * from NotchedBarDrawable. Use it in XML exactly like a LinearLayout:
 *
 *   <com.eduvos.edusafe.ui.common.NotchedNavBar ...> ...children... </...>
 * ----------------------------------------------------------------------
 */
public class NotchedNavBar extends LinearLayout {

    public NotchedNavBar(Context context) {
        super(context);
        init(context);
    }

    public NotchedNavBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NotchedNavBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setBackground(new NotchedBarDrawable(context, ContextCompat.getColor(context, R.color.navy)));
    }
}
