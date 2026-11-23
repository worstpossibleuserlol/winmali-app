package com.winlator.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class RuntimeCenterCatalogTest {
    @Test
    public void exposesFourPinnedRuntimeVariants() {
        List<RuntimeCenterCatalog.Runtime> runtimes = RuntimeCenterCatalog.getRuntimes();
        assertEquals(4, runtimes.size());
        assertNotNull(RuntimeCenterCatalog.getRuntime("proton-wine-11.0-2-x86_64"));
        assertNotNull(RuntimeCenterCatalog.getRuntime("proton-wine-11.0-2-arm64ec"));
        assertNotNull(RuntimeCenterCatalog.getRuntime("cachyos-wine-11.0-20261005-x86_64"));
        assertNotNull(RuntimeCenterCatalog.getRuntime("cachyos-wine-11.0-20261005-arm64ec"));
    }

    @Test
    public void onlyInstalledX8664RuntimesAreSelectableOnTheBox64Launcher() {
        for (RuntimeCenterCatalog.Runtime runtime : RuntimeCenterCatalog.getRuntimes()) {
            assertFalse(RuntimeCenterCatalog.isSelectable(runtime, false));
            assertEquals(runtime.guestArchitecture == RuntimeCenterCatalog.GuestArchitecture.X86_64,
                RuntimeCenterCatalog.isSelectable(runtime, true));
        }
    }

    @Test
    public void reportsWindowsGuestAbiSeparatelyFromAndroidHostAbi() {
        RuntimeCenterCatalog.Runtime protonX64 =
            RuntimeCenterCatalog.getRuntime("proton-wine-11.0-2-x86_64");
        RuntimeCenterCatalog.Runtime protonArm64ec =
            RuntimeCenterCatalog.getRuntime("proton-wine-11.0-2-arm64ec");
        assertEquals("arm64-v8a", RuntimeCenterCatalog.ANDROID_HOST_ABI);
        assertEquals("x86_64", protonX64.guestArchitecture.label);
        assertEquals("arm64ec", protonArm64ec.guestArchitecture.label);
        assertNotEquals(RuntimeCenterCatalog.ANDROID_HOST_ABI, protonArm64ec.guestArchitecture.label);
    }

    @Test
    public void exposesPublishedSizesAndSha256Values() {
        for (RuntimeCenterCatalog.Runtime runtime : RuntimeCenterCatalog.getRuntimes()) {
            assertTrue(runtime.fileSizeBytes > 0);
            assertEquals(64, runtime.sha256.length());
            assertTrue(runtime.assetUrl.startsWith("https://"));
            assertTrue(runtime.releaseUrl.startsWith("https://"));
        }
    }

    @Test
    public void usesWinlatorCompatiblePackageVariants() {
        assertTrue(RuntimeCenterCatalog.getRuntime("proton-wine-11.0-2-x86_64").fileName.endsWith(".wcp.xz"));
        assertTrue(RuntimeCenterCatalog.getRuntime("proton-wine-11.0-2-arm64ec").fileName.endsWith(".wcp.xz"));
        assertTrue(RuntimeCenterCatalog.getRuntime("cachyos-wine-11.0-20261005-x86_64").fileName.endsWith(".wcp"));
        assertTrue(RuntimeCenterCatalog.getRuntime("cachyos-wine-11.0-20261005-arm64ec").fileName.endsWith(".wcp"));
    }
}
