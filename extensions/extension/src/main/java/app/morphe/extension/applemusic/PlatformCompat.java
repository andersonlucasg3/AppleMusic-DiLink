package app.morphe.extension.applemusic;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Path;
import android.os.Build;
import android.os.ext.SdkExtensions;
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

    /** TEMP debug: log every ADI/Anisette status code with its call stack. */
    public static void logAdi(int code) {
        android.util.Log.e("AdiDebug", "ADI status=" + code, new Exception("ADI call site"));
    }

    public static void logAdiStart(long dsid, byte[] spim) {
        android.util.Log.e("AdiDebug", "ADI start dsid=" + dsid + " spim=" + (spim == null ? -1 : spim.length));
    }

    public static void logAdiEnd(byte[] ptm, byte[] tk) {
        android.util.Log.e("AdiDebug", "ADI end ptm=" + (ptm == null ? -1 : ptm.length) + " tk=" + (tk == null ? -1 : tk.length));
    }

    public static void logAdiOtp(long dsid) {
        android.util.Log.e("AdiDebug", "ADI otp dsid=" + dsid);
    }

    public static void logAdiErase(long dsid) {
        android.util.Log.e("AdiDebug", "ADI erase dsid=" + dsid);
    }

    public static void logAdiProvisioned() {
        try {
            Object companion = adiCompanion();
            java.lang.reflect.Method method =
                    companion.getClass().getMethod("ADIIsMachineProvisioned", long.class);
            Object result = method.invoke(companion, -1L);
            android.util.Log.e("AdiDebug", "ADIIsMachineProvisioned(-1)=" + result);
            android.util.Log.e("AdiDebug", "ADIIsMachineProvisioned(0)=" + method.invoke(companion, 0L));
            android.util.Log.e("AdiDebug", "ADIIsMachineProvisioned(1)=" + method.invoke(companion, 1L));
        } catch (Throwable t) {
            android.util.Log.e("AdiDebug", "ADIIsMachineProvisioned failed", t);
        }
    }

    private static Object adiCompanion() throws Exception {
        Class<?> clazz = Class.forName("com.apple.android.music.foothill.javanative.ADIInterface");
        java.lang.reflect.Field field = clazz.getDeclaredField("a");
        field.setAccessible(true);
        return field.get(null);
    }

    /** Reload the ADI library pointing at the APK native library dir instead of filesDir. */
    public static void reloadAdiLibrary() {
        try {
            Object companion = adiCompanion();
            java.lang.reflect.Method method =
                    companion.getClass().getMethod("ADILoadLibraryWithPath", String.class);
            Object result = method.invoke(companion, nativeLibraryDir());
            android.util.Log.e("AdiDebug", "ADILoadLibraryWithPath(nativeLibraryDir)=" + result);
        } catch (Throwable t) {
            android.util.Log.e("AdiDebug", "ADILoadLibraryWithPath reload failed", t);
        }
    }

    public static String nativeLibraryDir() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            android.content.Context context = (android.content.Context) application;
            return context.getApplicationInfo().nativeLibraryDir;
        } catch (Throwable t) {
            return null;
        }
    }

    private static volatile String[] cachedAnisette;
    private static volatile long cachedAnisetteAt;

    /** Replace the local ADI headers with ones from a remote Anisette v3 server. */
    public static void applyAnisetteHeaders(Object request) {
        try {
            String[] headers = anisetteHeaders();
            if (headers == null || headers.length == 0) {
                return;
            }
            Object map = request.getClass().getMethod("getHeaders").invoke(request);
            java.lang.reflect.Method set = map.getClass().getMethod("set", String.class, Object.class);
            for (int i = 0; i < headers.length; i += 2) {
                set.invoke(map, headers[i], headers[i + 1]);
            }
            android.util.Log.e("AdiDebug", "applied anisette headers=" + (headers.length / 2));
        } catch (Throwable t) {
            android.util.Log.e("AdiDebug", "applyAnisetteHeaders failed", t);
        }
    }

    private static String[] anisetteHeaders() {
        String[] cached = cachedAnisette;
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAnisetteAt < 120_000L) {
            return cached;
        }
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL("https://ani.sidestore.io/").openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            java.io.InputStream in = connection.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            in.close();
            org.json.JSONObject json = new org.json.JSONObject(out.toString("UTF-8"));
            java.util.List<String> pairs = new java.util.ArrayList<>();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                pairs.add(key);
                pairs.add(json.getString(key));
            }
            String[] result = pairs.toArray(new String[0]);
            cachedAnisette = result;
            cachedAnisetteAt = now;
            return result;
        } catch (Throwable t) {
            android.util.Log.e("AdiDebug", "anisette fetch failed", t);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** TEMP debug: list the user scripts the WebContainer will inject into the page. */
    public static void logScripts(Object context, Object scripts) {
        try {
            android.util.Log.e(
                    "AmsDebug",
                    "scripts arg=" + (scripts == null ? "null" : scripts.getClass().getName())
                            + " ctx=" + (context == null ? "null" : context.getClass().getName()));
            if (scripts instanceof Iterable) {
                int index = 0;
                for (Object item : (Iterable<?>) scripts) {
                    String text = String.valueOf(item);
                    android.util.Log.e(
                            "AmsDebug",
                            "script[" + index + "] len=" + text.length()
                                    + " head=" + text.substring(0, Math.min(120, text.length())));
                    index++;
                }
                android.util.Log.e("AmsDebug", "scripts count=" + index);
            } else if (scripts instanceof Object[]) {
                Object[] array = (Object[]) scripts;
                for (int i = 0; i < array.length; i++) {
                    String text = String.valueOf(array[i]);
                    android.util.Log.e(
                            "AmsDebug",
                            "script[" + i + "] len=" + text.length()
                                    + " head=" + text.substring(0, Math.min(120, text.length())));
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("AmsDebug", "logScripts failed", t);
        }
    }

    /**
     * TEMP: the native AMS bridge script uses ES2020 optional chaining/nullish coalescing, which
     * the DiLink WebView (Chromium 74) cannot parse, so window.AMS is never defined. Rewrite the
     * three affected snippets to ES5/ES2018 before the scripts are injected.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void fixScripts(Object context, Object scripts) {
        try {
            if (!(scripts instanceof java.util.List)) {
                logScripts(context, scripts);
                return;
            }
            java.util.List list = (java.util.List) scripts;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof String)) {
                    continue;
                }
                String original = (String) item;
                String fixed = original
                        .replace(
                                "window.webkit?.messageHandlers?.callback",
                                "(window.webkit&&window.webkit.messageHandlers&&window.webkit.messageHandlers.callback)")
                        .replace("...o.userInfo??{}", "...(o.userInfo!=null?o.userInfo:{})");
                if (!fixed.equals(original)) {
                    list.set(i, fixed);
                    android.util.Log.e(
                            "AmsDebug",
                            "transpiled script[" + i + "] " + original.length() + "->" + fixed.length());
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("AmsDebug", "fixScripts failed", t);
        }
    }

    /** TEMP debug: enable WebView DevTools so the console exception can be inspected over CDP. */
    public static void enableWebViewDebugging() {
        try {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true);
            android.util.Log.e("AmsDebug", "webview debugging enabled");
        } catch (Throwable t) {
            android.util.Log.e("AmsDebug", "enableWebViewDebugging failed", t);
        }
    }

    private static volatile java.util.Map<String, String> dlAssets;

    /** Serve the build-time transpiled Apple JS assets on the DiLink WebView (Chromium 74). */

    /** Fetch fresh Anisette v3 headers. Each provider request needs a new OTP. */
    private static org.json.JSONObject anisetteJson() {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL("https://ani.sidestore.io/").openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            java.io.InputStream in = connection.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            in.close();
            org.json.JSONObject json = new org.json.JSONObject(out.toString("UTF-8"));
            return json;
        } catch (Throwable t) {
            android.util.Log.e("AmsDebug", "anisetteJson failed", t);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Expected.success(Unit) for the Anisette methods we replace wholesale. */
    public static Object expectedUnit() {
        try {
            Class<?> unitClass = Class.forName("kotlin.Unit");
            Object unit = null;
            try {
                java.lang.reflect.Field field = unitClass.getDeclaredField("INSTANCE");
                field.setAccessible(true);
                unit = field.get(null);
            } catch (Throwable ignored) {
                // R8 may remove the INSTANCE field; fall through to a reflective instance.
            }
            if (unit == null) {
                java.lang.reflect.Constructor<?> constructor = unitClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                unit = constructor.newInstance();
            }
            return expectedSuccess(unit);
        } catch (Throwable t) {
            android.util.Log.e("AmsDebug", "expectedUnit failed", t);
            return null;
        }
    }

    private static Object expectedSuccess(Object value) throws Exception {
        Class<?> successClass = Class.forName("com.apple.mediaservices.amskit.bindings.Expected$Success");
        java.lang.reflect.Constructor<?> constructor = successClass.getDeclaredConstructor(Object.class);
        constructor.setAccessible(true);
        return constructor.newInstance(value);
    }

    private static Object expectedInstance(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            for (java.lang.reflect.Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                Object[] arguments = new Object[parameters.length];
                boolean supported = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i] == long.class) {
                        arguments[i] = 0L;
                    } else if (parameters[i] == int.class) {
                        arguments[i] = 0;
                    } else if (parameters[i] == byte[].class) {
                        arguments[i] = new byte[0];
                    } else if (parameters[i] == Object.class) {
                        arguments[i] = null;
                    } else if (parameters[i].getName().startsWith("kotlin.jvm.internal.")) {
                        arguments[i] = null;
                    } else {
                        supported = false;
                        break;
                    }
                }
                if (supported) {
                    constructor.setAccessible(true);
                    return expectedSuccess(constructor.newInstance(arguments));
                }
            }
            android.util.Log.e("AmsDebug", "no usable constructor for " + className);
            return null;
        } catch (Throwable t) {
            android.util.Log.e("AmsDebug", "expectedInstance failed " + className, t);
            return null;
        }
    }

    public static Object expectedStartProvisioning() {
        return expectedInstance(
                "com.apple.mediaservices.amskit.fairplay.INativeAnisetteProvider$StartProvisionResponse");
    }

    public static Object expectedSync() {
        return expectedInstance("com.apple.mediaservices.amskit.fairplay.INativeAnisetteProvider$SyncResponse");
    }

    /** Replacement for the app's IAnisetteProvider.request: the local ADI provisioning fails with
     * -45061 on a re-signed build, so return the remote Anisette v3 values instead. AMSCore
     * base64-encodes mid/otp into its X-Apple-AMD / X-Apple-I-MD headers.
     */
    public static Object remoteOtpResponse(long dsid) {
        try {
            org.json.JSONObject json = anisetteJson();
            if (json == null) {
                return null;
            }
            byte[] md = android.util.Base64.decode(json.getString("X-Apple-I-MD"), android.util.Base64.DEFAULT);
            byte[] mdm = android.util.Base64.decode(json.getString("X-Apple-I-MD-M"), android.util.Base64.DEFAULT);
            // V1.5 (AMD/AMD-M): try otp=MD, mid=MD-M swapped relative to the v3 mapping.
            byte[] mid = md;
            byte[] otp = mdm;
            Class<?> otpClass = Class.forName(
                    "com.apple.mediaservices.amskit.fairplay.INativeAnisetteProvider$OTPResponse");
            java.lang.reflect.Constructor<?> constructor =
                    otpClass.getDeclaredConstructor(byte[].class, byte[].class);
            constructor.setAccessible(true);
            Object otpResponse = constructor.newInstance(mid, otp);
            Class<?> successClass = Class.forName("com.apple.mediaservices.amskit.bindings.Expected$Success");
            java.lang.reflect.Constructor<?> successConstructor =
                    successClass.getDeclaredConstructor(Object.class);
            successConstructor.setAccessible(true);
            Object result = successConstructor.newInstance(otpResponse);
            android.util.Log.e("AmsDebug", "remoteOtpResponse ok dsid=" + dsid);
            return result;
        } catch (Throwable t) {
            android.util.Log.e("AmsDebug", "remoteOtpResponse failed", t);
            return null;
        }
    }

    public static android.webkit.WebResourceResponse interceptWebResource(Object request) {
        try {
            Object uri = request.getClass().getMethod("getUrl").invoke(request);
            String url = String.valueOf(uri);
            String code = dlAssets().get(url);
            if (code == null) {
                return null;
            }
            android.webkit.WebResourceResponse response = new android.webkit.WebResourceResponse(
                    "application/javascript",
                    "UTF-8",
                    new java.io.ByteArrayInputStream(code.getBytes("UTF-8")));
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-cache");
            response.setResponseHeaders(headers);
            android.util.Log.e("AmsDebug", "served transpiled asset " + url);
            return response;
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.util.Map<String, String> dlAssets() {
        java.util.Map<String, String> cached = dlAssets;
        if (cached != null) {
            return cached;
        }
        synchronized (PlatformCompat.class) {
            if (dlAssets != null) {
                return dlAssets;
            }
            java.util.Map<String, String> map = new java.util.HashMap<>();
            try {
                Object application = Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null);
                android.content.Context context = (android.content.Context) application;
                int id = context.getResources().getIdentifier("js_ams_adaptor", "raw", context.getPackageName());
                java.io.InputStream in = context.getResources().openRawResource(id);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                in.close();
                String text = out.toString("UTF-8");
                String marker = "window.__DL_ASSETS_B64__=\"";
                int start = text.indexOf(marker);
                int end = text.indexOf("\";/*__DL_END__*/", start);
                String encoded = text.substring(start + marker.length(), end);
                String json = new String(
                        android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), "UTF-8");
                org.json.JSONObject object = new org.json.JSONObject(json);
                java.util.Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, object.getString(key));
                }
                android.util.Log.e("AmsDebug", "loaded transpiled assets=" + map.size());
            } catch (Throwable t) {
                android.util.Log.e("AmsDebug", "dlAssets failed", t);
            }
            dlAssets = map;
            return map;
        }
    }

    /** API 30 static used by p507z3.C7059k.<clinit>; on API 29 return the platform default. */
    public static int getSuggestedMaxIpcSizeBytes() {
        return 1024 * 1024;
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

    /**
     * android.os.ext.SdkExtensions (API 30). The app calls it unconditionally from
     * androidx.core/MediaDrm paths; on API 29 the class does not exist, so the extension version
     * is 0 (the value the platform reports for an absent extension).
     */
    public static int getExtensionVersion(int extension) {
        return Build.VERSION.SDK_INT >= 30 ? Api30.getExtensionVersion(extension) : 0;
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

        static int getExtensionVersion(int extension) {
            return SdkExtensions.getExtensionVersion(extension);
        }
    }
}
