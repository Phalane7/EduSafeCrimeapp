package com.example.edusafe.ui.common;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * SystemBars
 * ----------------------------------------------------------------------
 * Recent Android versions draw apps behind the status bar (clock, battery)
 * and the gesture bar. These helpers make room for both, so no screen has
 * its content hidden under them. Used by all three EduSafe screens.
 * ----------------------------------------------------------------------
 */
public final class SystemBars {

    private SystemBars() { }

    /**
     * For screens that end in the notched bottom bar (Dashboard, Crime Map).
     * The top gets padding for the status bar; the navy bar is stretched down
     * (via navInsetSpacer) so it reaches the very bottom edge of the screen.
     *
     * @param darkStatusIcons true on LIGHT screens (dark clock/battery icons),
     *                        false on dark/red screens (white icons)
     */
    public static void applyWithBottomNav(Activity activity, View root, View navInsetSpacer,
                                          boolean darkStatusIcons) {
        prepare(activity, root, darkStatusIcons);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, 0);
            ViewGroup.LayoutParams params = navInsetSpacer.getLayoutParams();
            params.height = bars.bottom;
            navInsetSpacer.setLayoutParams(params);
            return windowInsets;
        });
    }

    /** For full-screen pages with no bottom bar (SOS Active). Pads both top and bottom. */
    public static void applyPadded(Activity activity, View root, boolean darkStatusIcons) {
        prepare(activity, root, darkStatusIcons);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return windowInsets;
        });
    }

    private static void prepare(Activity activity, View root, boolean darkStatusIcons) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), root);
        controller.setAppearanceLightStatusBars(darkStatusIcons);
        controller.setAppearanceLightNavigationBars(false); // the bottom bar is always navy, so light icons
    }
}
