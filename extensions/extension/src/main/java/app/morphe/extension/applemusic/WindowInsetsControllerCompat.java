package app.morphe.extension.applemusic;

import android.os.Build;
import android.view.Window;
import android.view.WindowInsetsController;

/**
 * Null-safe shim for the API 30 {@link WindowInsetsController} calls that Apple Music 7.0.0
 * performs without an SDK_INT guard (androidx's WindowInsetsControllerCompat had its
 * "&lt; 30" fallback stripped by R8, so on API 29 the controller field is null).
 *
 * <p>The controller parameter is declared as {@link Object} on purpose: this class has to be
 * loadable on API 29, where {@code android.view.WindowInsetsController} does not exist. Only the
 * method bodies reference the missing type, and they only run when the controller is non-null,
 * i.e. on API 30+ where the call behaves exactly like the original bytecode.
 */
public final class WindowInsetsControllerCompat {

    private WindowInsetsControllerCompat() {
    }

    /**
     * API 29 stand-in for {@link Window#getInsetsController()} (API 30), used by the
     * x2/W$a(Window, x2/v) constructor. Returns null on API &lt; 30, which is exactly what the
     * removed Impl20/23/26 constructors left in the controller field. The return type is Object
     * so this class keeps loading on API 29; the call site casts it back to
     * WindowInsetsController right after the call.
     */
    public static Object getInsetsController(Window window) {
        return Build.VERSION.SDK_INT >= 30 ? window.getInsetsController() : null;
    }

    public static void setSystemBarsAppearance(Object controller, int appearance, int mask) {
        if (controller != null) {
            ((WindowInsetsController) controller).setSystemBarsAppearance(appearance, mask);
        }
    }

    public static int getSystemBarsAppearance(Object controller) {
        return controller == null
                ? 0
                : ((WindowInsetsController) controller).getSystemBarsAppearance();
    }

    public static void setSystemBarsBehavior(Object controller, int behavior) {
        if (controller != null) {
            ((WindowInsetsController) controller).setSystemBarsBehavior(behavior);
        }
    }
}
