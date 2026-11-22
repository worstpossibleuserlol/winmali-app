package com.winlator.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.*;

public class CompatibilityRuntimeManifestTest {
    private JSONObject manifest(String engine, String host, String guests, boolean arm64ec) throws Exception {
        JSONArray architectures = new JSONArray();
        for (String value : guests.split(",")) architectures.put(value);
        return new JSONObject()
            .put("engine", engine)
            .put("version", "11.22")
            .put("hostAbi", host)
            .put("entrypoint", engine.equals("PROTON") ? "proton" : "bin/wine")
            .put("wineExecutable", engine.equals("PROTON") ? "files/bin/wine" : "bin/wine")
            .put("wineLibraryRoot", engine.equals("PROTON") ? "files/lib/wine" : "lib/wine")
            .put("guestArchitectures", architectures)
            .put("arm64ecVerified", arm64ec);
    }

    @Test
    public void acceptsSeparateArm64HostAndX86GuestAbis() throws Exception {
        CompatibilityRuntimeManifest parsed = CompatibilityRuntimeManifest.parse(
            manifest("WINE", "arm64-v8a", "x86,x86_64", false));
        assertNotNull(parsed);
        assertEquals(CompatibilityRuntimeManifest.HOST_ABI_ARM64, parsed.hostAbi);
        assertTrue(parsed.guestArchitectures.contains("x86"));
        assertTrue(parsed.guestArchitectures.contains("x86_64"));
    }

    @Test
    public void arm64ecMustBeExplicitlyVerified() throws Exception {
        assertNull(CompatibilityRuntimeManifest.parse(
            manifest("WINE", "arm64-v8a", "x86,x86_64,arm64ec", false)));
        assertNotNull(CompatibilityRuntimeManifest.parse(
            manifest("WINE", "arm64-v8a", "x86,x86_64,arm64ec", true)));
    }

    @Test
    public void rejectsDesktopHostAbiAndUnsafePaths() throws Exception {
        assertNull(CompatibilityRuntimeManifest.parse(
            manifest("WINE", "x86_64", "x86,x86_64", false)));
        JSONObject invalidPath = manifest("WINE", "arm64-v8a", "x86", false);
        invalidPath.put("wineExecutable", "../bin/wine");
        invalidPath.put("entrypoint", "../bin/wine");
        assertNull(CompatibilityRuntimeManifest.parse(invalidPath));
    }

    @Test
    public void protonIsNotMistakenForAnIntegratedWineLauncher() throws Exception {
        CompatibilityRuntimeManifest parsed = CompatibilityRuntimeManifest.parse(
            manifest("PROTON", "arm64-v8a", "x86,x86_64", false));
        assertNotNull(parsed);
        assertFalse(parsed.isLaunchableByCurrentWinMali());
    }

    @Test
    public void detectsOnlyDirectoriesPresentInTheRuntime() throws Exception {
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
