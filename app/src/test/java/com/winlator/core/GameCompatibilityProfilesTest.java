package com.winlator.core;

import com.winlator.box64.Box64Preset;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;

import org.junit.Test;

import static org.junit.Assert.*;

public class GameCompatibilityProfilesTest {
    private static int vk(int major, int minor, int patch) {
        return (major << 22) | (minor << 12) | patch;
    }

    private static GpuCompatibilityProfile maliProfile() {
        return GpuCompatibilityProfile.resolve(
            "Mali-G610 MC2", "ARM", vk(1, 3, 0),
            new String[]{"VK_KHR_swapchain"});
    }

    private static GpuCompatibilityProfile adrenoProfile() {
        return GpuCompatibilityProfile.resolve(
            "Adreno (TM) 740", "Qualcomm", vk(1, 3, 0),
            new String[]{"VK_KHR_swapchain"});
    }

    @Test
    public void compatibilityPresetUsesWineD3DAndStabilityPreset() {
        GameCompatibilityProfiles.Preset preset =
            GameCompatibilityProfiles.resolve(GameCompatibilityProfiles.COMPATIBILITY, maliProfile());

        assertNotNull(preset);
        assertEquals(DXWrappers.WINED3D, preset.dxWrapper);
        assertEquals(Box64Preset.STABILITY, preset.box64Preset);
        assertEquals("vortek,gladio", preset.graphicsDriver);
        assertEquals("mesa_glthread=false", preset.environmentVariables);
        assertFalse(preset.experimental);
    }

    @Test
    public void balancedPresetUsesTheDetectedDevicesRecommendedWrapper() {
        GameCompatibilityProfiles.Preset mali =
            GameCompatibilityProfiles.resolve(GameCompatibilityProfiles.BALANCED, maliProfile());
        GameCompatibilityProfiles.Preset adreno =
            GameCompatibilityProfiles.resolve(GameCompatibilityProfiles.BALANCED, adrenoProfile());

        assertEquals(DXWrappers.WINED3D, mali.dxWrapper);
        assertEquals(DXWrappers.DXVK, adreno.dxWrapper);
        assertEquals(Box64Preset.INTERMEDIATE, mali.box64Preset);
    }

    @Test
    public void performancePresetUsesPerformanceBox64PresetWithoutForcingMaliDxvk() {
        GameCompatibilityProfiles.Preset preset =
            GameCompatibilityProfiles.resolve(GameCompatibilityProfiles.PERFORMANCE, maliProfile());

        assertEquals(DXWrappers.WINED3D, preset.dxWrapper);
        assertEquals(Box64Preset.PERFORMANCE, preset.box64Preset);
        assertEquals("mesa_glthread=true", preset.environmentVariables);
    }

    @Test
    public void experimentalDxvkIsExplicitAndMarkedExperimentalOnMali() {
        GameCompatibilityProfiles.Preset preset =
            GameCompatibilityProfiles.resolve(GameCompatibilityProfiles.EXPERIMENTAL_DXVK, maliProfile());

        assertEquals(DXWrappers.DXVK, preset.dxWrapper);
        assertEquals(GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO, preset.graphicsDriver);
        assertEquals(Box64Preset.INTERMEDIATE, preset.box64Preset);
        assertTrue(preset.experimental);
    }

    @Test
    public void customOrUnknownProfilesDoNotChangeExistingSettings() {
        assertNull(GameCompatibilityProfiles.resolve(GameCompatibilityProfiles.CUSTOM, maliProfile()));
        assertNull(GameCompatibilityProfiles.resolve("NOT_A_PROFILE", maliProfile()));
        assertNull(GameCompatibilityProfiles.resolve(null, maliProfile()));
    }
}
