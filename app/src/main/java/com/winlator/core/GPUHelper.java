package com.winlator.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.EGL14;
import android.os.Looper;

import androidx.collection.ArrayMap;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import dalvik.annotation.optimization.CriticalNative;

public abstract class GPUHelper {
    public enum VkPresentMode {
        IMMEDIATE, MAILBOX, FIFO, FIFO_RELAXED;

        public String value() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }
    public static int VK_API_VERSION_1_3 = GPUHelper.vkMakeVersion(1, 3, 0);

    static {
        System.loadLibrary("winlator");
    }

    private static final long GPU_INFO_WAIT_MS = 500;
    private static volatile FutureTask<ArrayMap<String, String>> gpuInfoTask;

    private static ArrayMap<String, String> emptyGPUInformation() {
        ArrayMap<String, String> info = new ArrayMap<>();
        info.put("renderer", "");
        info.put("vendor", "");
        info.put("version", "");
        return info;
    }

    private static ArrayMap<String, String> loadGPUInformation(Context context) {
        FutureTask<ArrayMap<String, String>> task;
        synchronized (GPUHelper.class) {
            if (gpuInfoTask == null) {
                Context appContext = context.getApplicationContext();
                if (appContext == null) appContext = context;
                final Context queryContext = appContext;
                gpuInfoTask = new FutureTask<>(() -> queryGPUInformation(queryContext));
                Thread worker = new Thread(gpuInfoTask, "WinMali-gpu-detection");
                worker.setDaemon(true);
                worker.start();
            }
            task = gpuInfoTask;
        }

        // EGL probing must never block Android's UI thread.
        if (Looper.myLooper() == Looper.getMainLooper() && !task.isDone()) return emptyGPUInformation();
        try {
            return task.get(GPU_INFO_WAIT_MS, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return emptyGPUInformation();
        }
        catch (TimeoutException e) {
            return emptyGPUInformation();
        }
        catch (ExecutionException e) {
            synchronized (GPUHelper.class) {
                if (gpuInfoTask == task) gpuInfoTask = null;
            }
            return emptyGPUInformation();
        }
    }

    private static ArrayMap<String, String> queryGPUInformation(Context context) {
        ArrayMap<String, String> info = emptyGPUInformation();
        EGL10 egl = null;
        EGLDisplay display = EGL10.EGL_NO_DISPLAY;
        EGLContext eglContext = EGL10.EGL_NO_CONTEXT;
        EGLSurface surface = EGL10.EGL_NO_SURFACE;
        boolean initialized = false;
        try {
            egl = (EGL10)EGLContext.getEGL();
            display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (display == EGL10.EGL_NO_DISPLAY) return info;

            int[] version = new int[2];
            if (!egl.eglInitialize(display, version)) return info;
            initialized = true;

            int[] attrs = {
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 0,
                EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] configCount = new int[1];
            if (!egl.eglChooseConfig(display, attrs, configs, 1, configCount) ||
                configCount[0] < 1 || configs[0] == null) return info;

            int[] surfaceAttrs = {EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE};
            surface = egl.eglCreatePbufferSurface(display, configs[0], surfaceAttrs);
            if (surface == EGL10.EGL_NO_SURFACE) return info;

            attrs = new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
            eglContext = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, attrs);
            if (eglContext == EGL10.EGL_NO_CONTEXT) return info;
            if (!egl.eglMakeCurrent(display, surface, surface, eglContext)) return info;

            GL10 gl = (GL10)eglContext.getGL();
            String renderer = Objects.toString(gl.glGetString(GL10.GL_RENDERER), "");
            String vendor = Objects.toString(gl.glGetString(GL10.GL_VENDOR), "");
            String glVersion = Objects.toString(gl.glGetString(GL10.GL_VERSION), "");
            info.put("renderer", renderer);
            info.put("vendor", vendor);
            info.put("version", glVersion);

            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("gpu_renderer", renderer)
                .putString("gpu_vendor", vendor)
                .putString("gpu_version", glVersion)
                .apply();
        }
        catch (RuntimeException ignored) {
            // EGL may not expose a pbuffer-capable config on every device.
        }
        finally {
            if (egl != null && display != EGL10.EGL_NO_DISPLAY) {
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                if (surface != EGL10.EGL_NO_SURFACE) egl.eglDestroySurface(display, surface);
                if (eglContext != EGL10.EGL_NO_CONTEXT) egl.eglDestroyContext(display, eglContext);
                if (initialized) egl.eglTerminate(display);
            }
        }
        return info;
    }

    public static String glGetRenderer(Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString("gpu_renderer", "");
        if (!value.isEmpty()) return value;

        ArrayMap<String, String> gpuInfo = loadGPUInformation(context);
        return gpuInfo.get("renderer");
    }

    public static String glGetVendor(Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString("gpu_vendor", "");
        if (!value.isEmpty()) return value;

        ArrayMap<String, String> gpuInfo = loadGPUInformation(context);
        return gpuInfo.get("vendor");
    }

    public static String glGetVersion(Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString("gpu_version", "");
        if (!value.isEmpty()) return value;

        ArrayMap<String, String> gpuInfo = loadGPUInformation(context);
        return gpuInfo.get("version");
    }

    public static short getAdrenoModelId(Context context) {
        Matcher matcher = Pattern.compile("adreno[^678]*([678][0-9]{2})", Pattern.CASE_INSENSITIVE).matcher(glGetRenderer(context));
        return (short)(matcher.find() ? Integer.parseInt(matcher.group(1)) : 0);
    }

    public static int vkMakeVersion(String value) {
        final Pattern pattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            try {
                int major = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
                int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
                int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
                if (matcher.group(1) == null && patch == 0) patch = minor;
                return vkMakeVersion(major, minor, patch);
            }
            catch (NumberFormatException e) {
                return 0;
            }
        }
        else return 0;
    }

    public static int vkMakeVersion(int major, int minor, int patch) {
        return  ((major) << 22) | ((minor) << 12) | (patch);
    }

    public static int vkVersionMajor(int version) {
        return (version) >> 22;
    }

    public static int vkVersionMinor(int version) {
        return ((version) >> 12) & 0x3FF;
    }

    public static int vkVersionPatch(int version) {
        return (version) & 0xFFF;
    }

    public static native String[] vkGetDeviceExtensions();

    @CriticalNative
    public static native int vkGetApiVersion();

    public static native void setGlobalEGLContext();

    public static native long createOffscreenEGLContext(boolean sharedContext);

    public static native void destroyOffscreenEGLContext(long contextPtr);
}
