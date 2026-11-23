package com.winlator.core;

import com.winlator.box64.Box64Preset;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;

/**
 * Per-shortcut starting points for Windows game compatibility.
 *
 * These presets deliberately do not claim that a renderer or DirectX wrapper
 * will work for every title. They keep experimental DXVK opt-in and preserve
 * the user's existing container settings unless a preset is selected.
 */
public final class GameCompatibilityProfiles {
    public static final String CUSTOM = "CUSTOM";
    public static final String COMPATIBILITY = "COMPATIBILITY";
    public static final String BALANCED = "BALANCED";
    public static final String PERFORMANCE = "PERFORMANCE";
    public static final String EXPERIMENTAL_DXVK = "EXPERIMENTAL_DXVK";

    private GameCompatibilityProfiles() {}

    public static String[] getProfileIds() {
        return new String[]{CUSTOM, COMPATIBILITY, BALANCED, PERFORMANCE, EXPERIMENTAL_DXVK};
    }

    public static Preset resolve(String profileId, GpuCompatibilityProfile deviceProfile) {
        if (profileId == null || CUSTOM.equals(profileId)) return null;

        String graphicsDriver = deviceProfile != null ?
            deviceProfile.vulkanDriver + "," + deviceProfile.openGLDriver :
            GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO;
        String automaticWrapper = deviceProfile != null && deviceProfile.dxWrapper != null ?
            deviceProfile.dxWrapper : DXWrappers.WINED3D;

        switch (profileId) {
            case COMPATIBILITY:
                return new Preset(COMPATIBILITY, graphicsDriver, DXWrappers.WINED3D,
                    Box64Preset.STABILITY, "mesa_glthread=false", false);
            case BALANCED:
                return new Preset(BALANCED, graphicsDriver, automaticWrapper,
                    Box64Preset.INTERMEDIATE, "mesa_glthread=false", false);
            case PERFORMANCE:
                return new Preset(PERFORMANCE, graphicsDriver, automaticWrapper,
                    Box64Preset.PERFORMANCE, "mesa_glthread=true", false);
            case EXPERIMENTAL_DXVK:
                return new Preset(EXPERIMENTAL_DXVK, graphicsDriver, DXWrappers.DXVK,
                    Box64Preset.INTERMEDIATE, "mesa_glthread=false", true);
            default:
                return null;
        }
    }

    public static final class Preset {
        public final String id;
        public final String graphicsDriver;
        public final String dxWrapper;
        public final String box64Preset;
        public final String environmentVariables;
        public final boolean experimental;

        private Preset(String id, String graphicsDriver, String dxWrapper,
                String box64Preset, String environmentVariables, boolean experimental) {
            this.id = id;
            this.graphicsDriver = graphicsDriver;
            this.dxWrapper = dxWrapper;
            this.box64Preset = box64Preset;
            this.environmentVariables = environmentVariables;
            this.experimental = experimental;
        }
    }
}
