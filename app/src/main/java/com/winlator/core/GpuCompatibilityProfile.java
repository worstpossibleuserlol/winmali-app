package com.winlator.core;

import android.content.Context;

import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative device profile used to pre-fill new-container graphics settings.
 * The profile never assumes Turnip works on Mali/PowerVR/unknown GPUs and only
 * recommends DXVK when Vulkan 1.1+ and VK_KHR_swapchain are reported.
 */
public final class GpuCompatibilityProfile {
    public enum Vendor { MALI, ADRENO, POWERVR, OTHER, UNKNOWN }

    private static final ExecutorService DETECTION = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WinMali-gpu-profile");
        thread.setDaemon(true);
        return thread;
    });

    public final Vendor vendor;
    public final String renderer;
    public final String vendorName;
    public final int vulkanApiVersion;
    public final boolean hasSwapchainExtension;
    public final boolean supportsDxvk;
    public final boolean supportsVkd3dBaseline;
    public final String vulkanDriver;
    public final String openGLDriver;
    public final String dxWrapper;

    private GpuCompatibilityProfile(Vendor vendor, String renderer, String vendorName,
            int vulkanApiVersion, boolean hasSwapchainExtension, boolean supportsDxvk,
            boolean supportsVkd3dBaseline, String vulkanDriver, String openGLDriver,
            String dxWrapper) {
        this.vendor = vendor;
        this.renderer = renderer;
        this.vendorName = vendorName;
        this.vulkanApiVersion = vulkanApiVersion;
        this.hasSwapchainExtension = hasSwapchainExtension;
        this.supportsDxvk = supportsDxvk;
        this.supportsVkd3dBaseline = supportsVkd3dBaseline;
        this.vulkanDriver = vulkanDriver;
        this.openGLDriver = openGLDriver;
        this.dxWrapper = dxWrapper;
    }

    public static GpuCompatibilityProfile resolve(String renderer, String vendorName,
            int vulkanApiVersion, String[] extensions) {
        String safeRenderer = renderer != null ? renderer.trim() : "";
        String safeVendor = vendorName != null ? vendorName.trim() : "";
        String lowerRenderer = safeRenderer.toLowerCase(Locale.ROOT);
        String lowerVendor = safeVendor.toLowerCase(Locale.ROOT);

        Vendor detected;
        if (lowerRenderer.contains("mali") || lowerRenderer.contains("immortalis")) {
            detected = Vendor.MALI;
        }
        else if (hasAdrenoModel(safeRenderer)) {
            detected = Vendor.ADRENO;
        }
        else if (lowerRenderer.contains("powervr") || lowerVendor.contains("imgtec")) {
            detected = Vendor.POWERVR;
        }
        else if (safeRenderer.isEmpty() && safeVendor.isEmpty()) {
            detected = Vendor.UNKNOWN;
        }
        else detected = Vendor.OTHER;

        boolean swapchain = containsExtension(extensions, "VK_KHR_swapchain");
        boolean vulkan11 = isAtLeast(vulkanApiVersion, 1, 1);
        boolean vulkan12 = isAtLeast(vulkanApiVersion, 1, 2);
        boolean vulkan13 = isAtLeast(vulkanApiVersion, 1, 3);
        // The Vulkan version and swapchain extension alone are not enough to
        // assume upstream DXVK is usable on Android Mali/unknown mobile drivers.
        // Keep automatic DXVK selection to the known Adreno/Turnip path.
        boolean dxvk = detected == Vendor.ADRENO && vulkan11 && swapchain;

        // VKD3D needs more than an API version. This conservative baseline
        // requires commonly needed extensions; it is reported but never auto-selected.
        boolean vkd3d = vulkan13 && swapchain &&
            containsExtension(extensions, "VK_KHR_synchronization2") &&
            (vulkan12 || containsExtension(extensions, "VK_EXT_descriptor_indexing")) &&
            (vulkan12 || containsExtension(extensions, "VK_KHR_timeline_semaphore"));

        // Turnip is an Adreno Vulkan driver. It is never recommended for Mali.
        String vulkanDriver = detected == Vendor.ADRENO ?
            GraphicsDrivers.TURNIP : GraphicsDrivers.VORTEK;
        String openGLDriver = GraphicsDrivers.GLADIO;
        String dxWrapper = dxvk ? DXWrappers.DXVK : DXWrappers.WINED3D;

        return new GpuCompatibilityProfile(detected, safeRenderer, safeVendor,
            vulkanApiVersion, swapchain, dxvk, vkd3d, vulkanDriver, openGLDriver, dxWrapper);
    }

    public static void detectAsync(Context context, Callback<GpuCompatibilityProfile> callback) {
        if (context == null || callback == null) {
            if (callback != null) callback.call(resolve("", "", 0, null));
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;
        final Context queryContext = appContext;

        DETECTION.execute(() -> {
            String renderer = "";
            String vendor = "";
            int apiVersion = 0;
            String[] extensions = null;
            try {
                renderer = GPUHelper.glGetRenderer(queryContext);
                vendor = GPUHelper.glGetVendor(queryContext);
            }
            catch (LinkageError | RuntimeException ignored) {}

            try {
                apiVersion = GPUHelper.vkGetApiVersion();
                extensions = GPUHelper.vkGetDeviceExtensions();
            }
            catch (LinkageError | RuntimeException ignored) {}

            callback.call(resolve(renderer, vendor, apiVersion, extensions));
        });
    }

    /**
     * Applies the automatic-profile safety fallback to an existing container.
     * Explicit/manual choices are preserved when automatic GPU settings are off.
     */
    public static String resolveDXWrapperForLaunch(String selected, Vendor vendor,
            boolean automaticSettings) {
        String wrapper = DXWrappers.parseIdentifier(selected);
        if (automaticSettings && vendor == Vendor.MALI && DXWrappers.DXVK.equals(wrapper)) {
            return DXWrappers.WINED3D;
        }
        return wrapper;
    }

    public static String resolveVulkanDriverForLaunch(String selected, Vendor vendor,
            boolean automaticSettings) {
        String driver = GraphicsDrivers.isVulkanDriver(selected) ?
            selected : GraphicsDrivers.DEFAULT_VULKAN_DRIVER;
        if (automaticSettings && vendor == Vendor.MALI && GraphicsDrivers.TURNIP.equals(driver)) {
            return GraphicsDrivers.VORTEK;
        }
        return driver;
    }

    public String getVendorLabel() {
        switch (vendor) {
            case MALI: return "Mali";
            case ADRENO: return "Adreno";
            case POWERVR: return "PowerVR";
            case OTHER: return "Other GPU";
            default: return "GPU not identified";
        }
    }

    public String getVulkanVersionLabel() {
        if (vulkanApiVersion <= 0) return "Vulkan unavailable or not reported";
        return versionPart(vulkanApiVersion, 22, 0x7f)+"."+
            versionPart(vulkanApiVersion, 12, 0x3ff)+"."+
            versionPart(vulkanApiVersion, 0, 0xfff);
    }

    public String getSummary() {
        StringBuilder summary = new StringBuilder(getVendorLabel());
        if (!renderer.isEmpty()) summary.append(" — ").append(renderer);
        summary.append("\nVulkan: ").append(getVulkanVersionLabel());
        summary.append("\nDirect3D default: ")
            .append(supportsDxvk ? "DXVK (Vulkan swapchain detected)" : "WineD3D (conservative fallback)");
        if (vendor == Vendor.MALI) summary.append("\nTurnip is not selected for Mali.");
        if (supportsVkd3dBaseline) summary.append("\nVKD3D baseline checks passed; manual validation is still recommended.");
        return summary.toString();
    }

    public static boolean isAtLeast(int apiVersion, int major, int minor) {
        if (apiVersion <= 0) return false;
        int actualMajor = versionPart(apiVersion, 22, 0x7f);
        int actualMinor = versionPart(apiVersion, 12, 0x3ff);
        return actualMajor > major || (actualMajor == major && actualMinor >= minor);
    }

    private static int versionPart(int value, int shift, int mask) {
        return (value >>> shift) & mask;
    }

    private static boolean containsExtension(String[] extensions, String expected) {
        if (extensions == null) return false;
        for (String extension : extensions) {
            if (expected.equals(extension)) return true;
        }
        return false;
    }

    private static boolean hasAdrenoModel(String renderer) {
        if (renderer == null) return false;
        Matcher matcher = Pattern.compile("adreno[^0-9]*([0-9]{3,4})",
            Pattern.CASE_INSENSITIVE).matcher(renderer);
        return matcher.find();
    }
}
