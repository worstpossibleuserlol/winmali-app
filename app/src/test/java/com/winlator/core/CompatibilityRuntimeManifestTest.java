package com.winlator.core;

import org.junit.Test;

import java.io.File;
import java.util.Set;

import static org.junit.Assert.*;

public class CompatibilityRuntimeManifestTest {
    @Test
    public void separatesAndroidHostAbiFromWindowsGuestAbis() {
        assertTrue(CompatibilityRuntimeManifest.isSupportedHostAbi("arm64-v8a"));
        assertFalse(CompatibilityRuntimeManifest.isSupportedHostAbi("x86_64"));
        Set<String> guests = CompatibilityRuntimeManifest.parseGuestArchitectures(
            new String[]{"x86", "x86_64"}, false);
        assertNotNull(guests);
        assertTrue(guests.contains("x86"));
        assertTrue(guests.contains("x86_64"));
    }

    @Test
    public void arm64ecMustBeExplicitlyVerified() {
        assertNull(CompatibilityRuntimeManifest.parseGuestArchitectures(
            new String[]{"x86", "x86_64", "arm64ec"}, false));
        Set<String> guests = CompatibilityRuntimeManifest.parseGuestArchitectures(
            new String[]{"x86", "x86_64", "arm64ec"}, true);
        assertNotNull(guests);
        assertTrue(guests.contains("arm64ec"));
    }

    @Test
    public void rejectsUnknownAndDuplicateGuestArchitectures() {
        assertNull(CompatibilityRuntimeManifest.parseGuestArchitectures(
            new String[]{"x86", "riscv64"}, false));
        assertNull(CompatibilityRuntimeManifest.parseGuestArchitectures(
            new String[]{"x86", "x86"}, false));
        assertNull(CompatibilityRuntimeManifest.parseGuestArchitectures(
            new String[0], false));
    }

    @Test
    public void protonIsNotMistakenForAnIntegratedWineLauncher() {
        assertTrue(CompatibilityRuntimeManifest.isEngineLaunchable(
            CompatibilityRuntimeManifest.Engine.WINE));
        assertFalse(CompatibilityRuntimeManifest.isEngineLaunchable(
            CompatibilityRuntimeManifest.Engine.PROTON));
    }

    @Test
    public void detectsOnlyGuestDirectoriesPresentInTheRuntime() throws Exception {
        File root = File.createTempFile("winmali-runtime", "test");
        assertTrue(root.delete());
        assertTrue(root.mkdirs());
        File wineRoot = new File(root, "lib/wine");
        assertTrue(new File(wineRoot, "i386-windows").mkdirs());
        assertTrue(new File(wineRoot, "x86_64-windows").mkdirs());
        Set<String> found = CompatibilityRuntimeManifest.detectGuestArchitectures(root);
        assertTrue(found.contains("x86"));
        assertTrue(found.contains("x86_64"));
        assertFalse(found.contains("arm64ec"));
        delete(root);
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
