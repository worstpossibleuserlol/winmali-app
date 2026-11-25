package com.winlator.container;

import android.content.Context;

import com.winlator.core.GPUHelper;
import com.winlator.core.KeyValueSet;

public abstract class GraphicsDrivers {
    public static final String TURNIP = "turnip";
    public static final String VORTEK = "vortek";
    public static final String ZINK = "zink";
    public static final String VIRGL = "virgl";
    public static final String GLADIO = "gladio";
    public static final String DEFAULT_VULKAN_DRIVER = VORTEK;
    public static final String DEFAULT_OPENGL_DRIVER = GLADIO;

    public static String getName(String identifier) {
        if (identifier == null) return "None";
        switch (identifier) {
            case TURNIP: return "Turnip";
            case VORTEK: return "Vortek";
            case ZINK: return "Zink";
            case VIRGL: return "VirGL";
            case GLADIO: return "Gladio";
            default: return "None";
        }
    }

    public static boolean isVulkanDriver(String identifier) {
        return identifier != null && (identifier.equals(TURNIP) || identifier.equals(VORTEK));
    }

    public static boolean isOpenGLDriver(String identifier) {
        return identifier != null && (identifier.equals(ZINK) || identifier.equals(VIRGL) || identifier.equals(GLADIO));
    }

    public static boolean isMaliGPU(Context context) {
        if (context == null) return false;
        String renderer = GPUHelper.glGetRenderer(context).toLowerCase(java.util.Locale.ENGLISH);
        String vendor = GPUHelper.glGetVendor(context).toLowerCase(java.util.Locale.ENGLISH);
        return renderer.contains("mali") || vendor.contains("arm");
    }

    public static String[] getItems(String apiName) {
        if (apiName == null) return new String[0];
        if (apiName.equalsIgnoreCase("VULKAN")) {
            return new String[]{getName(TURNIP), getName(VORTEK)};
        }
        else if (apiName.equalsIgnoreCase("OPENGL")) {
            return new String[]{getName(ZINK), getName(VIRGL), getName(GLADIO)};
        }
        else return new String[0];
    }

    /**
     * WineD3D OpenGL remains the safer compatibility default. The Vulkan
     * renderer is available as an explicit user choice, but is not yet at
     * feature parity with the OpenGL renderer.
     */
    public static String getDefaultWineD3DRenderer(boolean maliGpu) {
        return "gl";
    }

    public static String getDefaultWineD3DOffscreenRenderingMode(boolean maliGpu) {
        return "fbo";
    }

    public static boolean supportsTurnip(Context context) {
        return context != null && GPUHelper.getAdrenoModelId(context) > 0;
    }

    public static String[] getItems(String apiName, Context context) {
        if (apiName == null) return new String[0];
        if (apiName.equalsIgnoreCase("VULKAN") && !supportsTurnip(context)) {
            // Unknown and Mali GPUs must not be treated as Turnip-compatible.
            return new String[]{getName(VORTEK)};
        }
        return getItems(apiName);
    }

    public static String[] parseIdentifiers(String graphicsDriver) {
        if (graphicsDriver == null || graphicsDriver.trim().isEmpty()) {
            return new String[]{DEFAULT_VULKAN_DRIVER, DEFAULT_OPENGL_DRIVER};
        }

        if (graphicsDriver.contains(",")) {
            String[] identifiers = graphicsDriver.split(",", -1);
            String vulkan = identifiers.length > 0 && isVulkanDriver(identifiers[0].trim()) ?
                identifiers[0].trim() : DEFAULT_VULKAN_DRIVER;
            String openGL = identifiers.length > 1 && isOpenGLDriver(identifiers[1].trim()) ?
                identifiers[1].trim() : DEFAULT_OPENGL_DRIVER;
            return new String[]{vulkan, openGL};
        }

        if (isVulkanDriver(graphicsDriver)) {
            return new String[]{graphicsDriver, DEFAULT_OPENGL_DRIVER};
        }
        else if (isOpenGLDriver(graphicsDriver)) {
            return new String[]{DEFAULT_VULKAN_DRIVER, graphicsDriver};
        }
        else return new String[]{DEFAULT_VULKAN_DRIVER, DEFAULT_OPENGL_DRIVER};
    }

    public static KeyValueSet[] parseConfigs(String graphicsDriver, String graphicsDriverConfig) {
        if (graphicsDriverConfig == null || graphicsDriverConfig.isEmpty()) return new KeyValueSet[]{new KeyValueSet(), new KeyValueSet()};
        int separator = graphicsDriverConfig.indexOf("|");
        if (separator != -1) {
            String first = graphicsDriverConfig.substring(0, separator);
            String second = graphicsDriverConfig.substring(separator + 1);
            return new KeyValueSet[]{new KeyValueSet(first), new KeyValueSet(second)};
        }
        else {
            if (isVulkanDriver(graphicsDriver)) {
                return new KeyValueSet[]{new KeyValueSet(graphicsDriverConfig), new KeyValueSet()};
            }
            else if (isOpenGLDriver(graphicsDriver)) {
                return new KeyValueSet[]{new KeyValueSet(), new KeyValueSet(graphicsDriverConfig)};
            }
            else return new KeyValueSet[]{new KeyValueSet(), new KeyValueSet()};
        }
    }

    public static String getDefaultDriver(Context context) {
        return context != null && GPUHelper.getAdrenoModelId(context) > 0 ?
            GraphicsDrivers.TURNIP+","+GraphicsDrivers.DEFAULT_OPENGL_DRIVER :
            GraphicsDrivers.VORTEK+","+GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
    }
}
