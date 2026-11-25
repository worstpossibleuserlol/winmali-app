package com.winlator.core;

import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Container;

import org.junit.Test;

import static org.junit.Assert.*;

public class GpuCompatibilityProfileTest {
    private static int vk(int major, int minor, int patch) {
        return (major << 22) | (minor << 12) | patch;
    }

    @Test
    public void newContainersUseTheConservativeWineD3DDefault() {
        assertEquals(DXWrappers.WINED3D, Container.DEFAULT_DXWRAPPER);
    }

    @Test
    public void maliNeverReceivesTurnipOrAutomaticallySelectsDXVK() {
        GpuCompatibilityProfile profile = GpuCompatibilityProfile.resolve(
            "Mali-G610 MC2", "ARM", vk(1, 3, 0), new String[]{"VK_KHR_swapchain"});
        assertEquals(GpuCompatibilityProfile.Vendor.MALI, profile.vendor);
        assertEquals(GraphicsDrivers.VORTEK, profile.vulkanDriver);
        assertEquals(GraphicsDrivers.GLADIO, profile.openGLDriver);
        assertNotEquals(GraphicsDrivers.TURNIP, profile.vulkanDriver);
        assertEquals(DXWrappers.WINED3D, profile.dxWrapper);
    }

    @Test
    public void adrenoModelMayUseTurnip() {
        GpuCompatibilityProfile profile = GpuCompatibilityProfile.resolve(
            "Adreno (TM) 740", "Qualcomm", vk(1, 3, 0),
            new String[]{"VK_KHR_swapchain"});
        assertEquals(GpuCompatibilityProfile.Vendor.ADRENO, profile.vendor);
        assertEquals(GraphicsDrivers.TURNIP, profile.vulkanDriver);
        assertEquals(DXWrappers.DXVK, profile.dxWrapper);
    }

    @Test
    public void automaticMaliFallbackProtectsExistingContainersButPreservesManualChoice() {
        assertEquals(DXWrappers.WINED3D,
            GpuCompatibilityProfile.resolveDXWrapperForLaunch(
                DXWrappers.DXVK, GpuCompatibilityProfile.Vendor.MALI, true));
        assertEquals(DXWrappers.DXVK,
            GpuCompatibilityProfile.resolveDXWrapperForLaunch(
                DXWrappers.DXVK, GpuCompatibilityProfile.Vendor.MALI, false));
        assertEquals(GraphicsDrivers.VORTEK,
            GpuCompatibilityProfile.resolveVulkanDriverForLaunch(
                GraphicsDrivers.TURNIP, GpuCompatibilityProfile.Vendor.MALI, true));
        assertEquals(GraphicsDrivers.TURNIP,
            GpuCompatibilityProfile.resolveVulkanDriverForLaunch(
                GraphicsDrivers.TURNIP, GpuCompatibilityProfile.Vendor.MALI, false));
    }

    @Test
    public void dxvkRequiresVulkan11AndSwapchain() {
        GpuCompatibilityProfile withoutSwapchain = GpuCompatibilityProfile.resolve(
            "Mali-G710", "ARM", vk(1, 3, 0), new String[0]);
        GpuCompatibilityProfile oldVulkan = GpuCompatibilityProfile.resolve(
            "Mali-G710", "ARM", vk(1, 0, 0), new String[]{"VK_KHR_swapchain"});
        assertFalse(withoutSwapchain.supportsDxvk);
        assertFalse(oldVulkan.supportsDxvk);
        assertEquals(DXWrappers.WINED3D, withoutSwapchain.dxWrapper);
        assertEquals(DXWrappers.WINED3D, oldVulkan.dxWrapper);
    }

    @Test
    public void unknownGpuDoesNotGuessTurnip() {
        GpuCompatibilityProfile profile = GpuCompatibilityProfile.resolve(
            "", "", 0, null);
        assertEquals(GpuCompatibilityProfile.Vendor.UNKNOWN, profile.vendor);
        assertNotEquals(GraphicsDrivers.TURNIP, profile.vulkanDriver);
        assertEquals(DXWrappers.WINED3D, profile.dxWrapper);
    }

    @Test
    public void vkd3dIsNeverChosenAsTheAutomaticDefaultOnMali() {
        GpuCompatibilityProfile profile = GpuCompatibilityProfile.resolve(
            "Mali-G720", "ARM", vk(1, 3, 0),
            new String[]{"VK_KHR_swapchain", "VK_KHR_synchronization2",
                "VK_EXT_descriptor_indexing", "VK_KHR_timeline_semaphore"});
        assertTrue(profile.supportsVkd3dBaseline);
        assertEquals(DXWrappers.WINED3D, profile.dxWrapper);
        assertNotEquals(DXWrappers.VKD3D, profile.dxWrapper);
    }
}
