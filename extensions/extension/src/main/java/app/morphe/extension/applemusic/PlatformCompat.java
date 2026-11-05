package app.morphe.extension.applemusic;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Path;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;

import java.util.Collections;
import java.util.List;

/**
 * Shims for the API 30 platform calls that Apple Music 7.0.0 performs without an SDK_INT guard
 * (the app was built with minSdk 30, so R8 removed androidx's own fallbacks). On the DiLink
 * (Android 10 / API 29) every one of these throws {@code NoSuchMethodError} or
 * {@code NoSuchFieldError} when reached.
 *
 * <p>All platform references live in {@link Api30}, loaded only after the SDK check, so this
 * class itself is loadable on API 29. On API 30+ the calls behave exactly like the original
 * bytecode; on API 29 they degrade to null/false/empty/no-op.
 */
public final class PlatformCompat {

    private PlatformCompat() {
    }

    public static void setWindowInsetsAnimationCallback(Object view, Object callback) {
        if (Build.VERSION.SDK_INT >= 30 && view != null) {
            Api30.setWindowInsetsAnimationCallback(view, callback);
        }
    }

    public static void setFitInsetsSides(Object layoutParams, int sides) {
        if (Build.VERSION.SDK_INT >= 30 && layoutParams != null) {
            Api30.setFitInsetsSides(layoutParams, sides);
        }
    }

    public static void setFitInsetsTypes(Object layoutParams, int types) {
        if (Build.VERSION.SDK_INT >= 30 && layoutParams != null) {
            Api30.setFitInsetsTypes(layoutParams, types);
        }
    }

    public static void setPath(Object outline, Object path) {
        if (Build.VERSION.SDK_INT >= 30 && outline != null && path != null) {
            Api30.setPath(outline, path);
        }
    }

    public static String getAttributionTag(Object context) {
        return Build.VERSION.SDK_INT >= 30 && context != null
                ? Api30.getAttributionTag(context)
                : null;
    }

    public static List<?> getHistoricalProcessExitReasons(
            Object activityManager, String packageName, int pid, int maxNum) {
        return Build.VERSION.SDK_INT >= 30 && activityManager != null
                ? Api30.getHistoricalProcessExitReasons(activityManager, packageName, pid, maxNum)
                : Collections.emptyList();
    }

    public static boolean isShowingLayoutBounds(Object view) {
        return Build.VERSION.SDK_INT >= 30 && view != null && Api30.isShowingLayoutBounds(view);
    }

    public static void setDecorFitsSystemWindows(Object window, boolean fitSystemWindows) {
        if (Build.VERSION.SDK_INT >= 30 && window != null) {
            Api30.setDecorFitsSystemWindows(window, fitSystemWindows);
        }
    }

    private static final class Api30 {

        private Api30() {
        }

        static void setWindowInsetsAnimationCallback(Object view, Object callback) {
            ((View) view).setWindowInsetsAnimationCallback((WindowInsetsAnimation.Callback) callback);
        }

        static void setFitInsetsSides(Object layoutParams, int sides) {
            ((WindowManager.LayoutParams) layoutParams).setFitInsetsSides(sides);
        }

        static void setFitInsetsTypes(Object layoutParams, int types) {
            ((WindowManager.LayoutParams) layoutParams).setFitInsetsTypes(types);
        }

        static void setPath(Object outline, Object path) {
            ((Outline) outline).setPath((Path) path);
        }

        static String getAttributionTag(Object context) {
            return ((Context) context).getAttributionTag();
        }

        static List<?> getHistoricalProcessExitReasons(
                Object activityManager, String packageName, int pid, int maxNum) {
            return ((ActivityManager) activityManager)
                    .getHistoricalProcessExitReasons(packageName, pid, maxNum);
        }

        static boolean isShowingLayoutBounds(Object view) {
            return ((View) view).isShowingLayoutBounds();
        }

        static void setDecorFitsSystemWindows(Object window, boolean fitSystemWindows) {
            ((Window) window).setDecorFitsSystemWindows(fitSystemWindows);
        }
    }
}
