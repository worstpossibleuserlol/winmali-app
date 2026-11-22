package com.winlator.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompatibilityRuntimePackageTest {
    @Test
    public void acceptsNormalVersionIdentifiers() {
        assertTrue(CompatibilityRuntimePackage.isValidVersionName("0.4.6"));
        assertTrue(CompatibilityRuntimePackage.isValidVersionName("1.2.3.4"));
        assertTrue(CompatibilityRuntimePackage.isValidVersionName("0.4.6-rc1"));
        assertTrue(CompatibilityRuntimePackage.isValidVersionName("0.4.6+vendor1"));
        // The supported VKD3D repository includes the upstream "3.0b" release tag.
        assertTrue(CompatibilityRuntimePackage.isValidVersionName("3.0b"));
    }

    @Test
    public void rejectsPathLikeOrMalformedVersionIdentifiers() {
        assertFalse(CompatibilityRuntimePackage.isValidVersionName(null));
        assertFalse(CompatibilityRuntimePackage.isValidVersionName(""));
        assertFalse(CompatibilityRuntimePackage.isValidVersionName("../box64"));
        assertFalse(CompatibilityRuntimePackage.isValidVersionName("0.4/6"));
        assertFalse(CompatibilityRuntimePackage.isValidVersionName("0.4.6.."));
        assertFalse(CompatibilityRuntimePackage.isValidVersionName("0.4.6."));
    }

    @Test
    public void comparesTaggedVersionsByTheirNumericSegments() {
        assertTrue(CompatibilityRuntimePackage.compareVersionNames("3.0b", "2.14.1") > 0);
        assertTrue(CompatibilityRuntimePackage.compareVersionNames("3.0b", "3.0") < 0);
        assertTrue(CompatibilityRuntimePackage.compareVersionNames("10.0", "2.9") > 0);
        assertTrue(CompatibilityRuntimePackage.compareVersionNames("3.0-rc2", "3.0-rc1") > 0);
        assertEquals(0, CompatibilityRuntimePackage.compareVersionNames("3.0", "3.0.0"));
        assertTrue(CompatibilityRuntimePackage.compareVersionNames(
            "100000000000000000000000.0", "99999999999999999999999.9") > 0);
    }

    @Test
    public void normalizesDotPrefixedArchivePaths() {
        assertEquals("usr/local/bin/box64",
            CompatibilityRuntimePackage.normalizeRelativePath("./usr/local/bin/box64"));
    }

    @Test
    public void rejectsArchivePathTraversalAndAbsolutePaths() {
        assertNull(CompatibilityRuntimePackage.normalizeRelativePath("../outside"));
        assertNull(CompatibilityRuntimePackage.normalizeRelativePath("usr/../../outside"));
        assertNull(CompatibilityRuntimePackage.normalizeRelativePath("/data/outside"));
        assertNull(CompatibilityRuntimePackage.normalizeRelativePath("C:/Windows/system32"));
    }

    @Test
    public void limitsBox64PackageTargetsToRuntimeDirectories() {
        assertEquals("usr/local/bin/box64",
            CompatibilityRuntimePackage.getSafeTargetPath("BOX64", "usr/local/bin/box64"));
        assertEquals("usr/local/lib/libexample.so",
            CompatibilityRuntimePackage.getSafeTargetPath("BOX64", "usr/local/lib/libexample.so"));
        assertNull(CompatibilityRuntimePackage.getSafeTargetPath("BOX64", "opt/wine/bin/wine"));
        assertNull(CompatibilityRuntimePackage.getSafeTargetPath("BOX64", "../usr/local/bin/box64"));
        assertNull(CompatibilityRuntimePackage.getSafeTargetPath("BOX64", "/usr/local/bin/box64"));
    }

    @Test
    public void mapsWrapperTargetsIntoWindowsDirectories() {
        assertEquals("system32/d3d11.dll",
            CompatibilityRuntimePackage.getSafeTargetPath("DXVK", "drive_c/windows/system32/d3d11.dll"));
        assertEquals("syswow64/d3d9.dll",
            CompatibilityRuntimePackage.getSafeTargetPath("DXVK", "drive_c/windows/syswow64/d3d9.dll"));
        assertNull(CompatibilityRuntimePackage.getSafeTargetPath("DXVK", "drive_c/windows/startup/payload.dll"));
    }
}
