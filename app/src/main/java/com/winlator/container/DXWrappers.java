package com.winlator.container;

import com.winlator.core.KeyValueSet;

public abstract class DXWrappers {
    public static final String WINED3D = "wined3d";
    public static final String DXVK = "dxvk";
    public static final String VKD3D = "vkd3d";
    public static final String CNC_DDRAW = "cnc-ddraw";
    public static final String D7VK = "d7vk";

    private static final String INSTALL_CACHE_SCHEMA = "direct3d-v2";

    /** Resolve missing or blank saved component versions to the packaged default. */
    public static String resolveVersion(String configuredVersion, String defaultVersion) {
        if (configuredVersion == null || configuredVersion.trim().isEmpty()) {
            return defaultVersion != null ? defaultVersion.trim() : "";
        }
        return configuredVersion.trim();
    }

    /** Build a versioned cache key so older, unchecked installs are retried once. */
    public static String createInstallCacheId(String wrapper, String wrapperVersion,
            String vkd3dVersion, String ddrawWrapper) {
        return INSTALL_CACHE_SCHEMA + "|" + cachePart(wrapper) + "|" +
            cachePart(wrapperVersion) + "|" + cachePart(vkd3dVersion) + "|" +
            cachePart(ddrawWrapper);
    }

    private static String cachePart(String value) {
        return value != null ? value.trim() : "";
    }

    /** All required layers must install before their cache key may be persisted. */
    public static boolean isInstallComplete(boolean wrapperInstalled,
            boolean vkd3dInstalled, boolean ddrawInstalled) {
        return wrapperInstalled && vkd3dInstalled && ddrawInstalled;
    }

    /** Parse the optional DXVK PCI device override without trusting saved data. */
    public static String[] parseCustomDevice(String customDevice) {
        if (customDevice == null || customDevice.isEmpty()) return null;
        String[] parts = customDevice.split(":", 3);
        if (parts.length != 3 || !parts[0].matches("(?i)[0-9a-f]{1,4}") ||
                !parts[1].matches("(?i)[0-9a-f]{1,4}") || parts[2].trim().isEmpty() ||
                parts[2].indexOf('"') >= 0 || parts[2].contains("\n") ||
                parts[2].contains("\r")) return null;
        return new String[]{parts[0], parts[1], parts[2].trim()};
    }

    public static String getName(String identifier) {
        if (identifier == null) return "None";
        switch (identifier) {
            case WINED3D: return "WineD3D";
            case DXVK: return "DXVK";
            case VKD3D: return "VKD3D";
            case CNC_DDRAW: return "CNC DDraw";
            case D7VK: return "D7VK";
            default: return "None";
        }
    }

    public static String parseIdentifier(String dxwrapper) {
        if (dxwrapper == null || dxwrapper.isEmpty()) return Container.DEFAULT_DXWRAPPER;
        return dxwrapper.equals(WINED3D) || dxwrapper.equals(DXVK) || dxwrapper.equals(VKD3D) ?
            dxwrapper : Container.DEFAULT_DXWRAPPER;
    }

    public static KeyValueSet[] parseConfigs(String dxwrapper, String dxwrapperConfig) {
        if (dxwrapperConfig == null || dxwrapperConfig.isEmpty()) return new KeyValueSet[]{new KeyValueSet(), new KeyValueSet()};
        int separator = dxwrapperConfig.indexOf("|");
        if (separator != -1) {
            String first = dxwrapperConfig.substring(0, separator);
            String second = dxwrapperConfig.substring(separator + 1);
            return new KeyValueSet[]{new KeyValueSet(first), new KeyValueSet(second)};
        }
        else if (dxwrapper != null) {
            if (dxwrapper.equals(WINED3D) || dxwrapper.equals(DXVK)) {
                return new KeyValueSet[]{new KeyValueSet(dxwrapperConfig), new KeyValueSet()};
            }
            else if (dxwrapper.equals(VKD3D)) {
                return new KeyValueSet[]{new KeyValueSet(), new KeyValueSet(dxwrapperConfig)};
            }
        }
        return new KeyValueSet[]{new KeyValueSet(), new KeyValueSet()};
    }
}
