package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

public class GraphicsDriverInstallStateTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File writeFile(String name, String contents) throws Exception {
        File file = new File(temporaryFolder.getRoot(), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return file;
    }

    @Test
    public void matchingCacheAndAllRequiredFilesSkipReinstall() throws Exception {
        File vulkanLibrary = writeFile("libvulkan_vortek.so", "valid");
        File icdManifest = writeFile("vortek_icd.aarch64.json", "{}");
        assertFalse(GraphicsDriverInstallState.needsInstall(
            "vortek-2.1-gladio-1.1", "vortek-2.1-gladio-1.1",
            vulkanLibrary, icdManifest));
    }

    @Test
    public void changedDriverKeyRequiresInstallEvenWhenOldFilesExist() throws Exception {
        File library = writeFile("libGL.so.1.7.0", "valid");
        assertTrue(GraphicsDriverInstallState.needsInstall(
            "vortek-2.1-gladio-1.1", "vortek-2.1-virgl-23.1.9", library));
    }

    @Test
    public void missingRequiredFileForcesRecoveryEvenWhenCacheKeyMatches() throws Exception {
        File library = writeFile("libvulkan_vortek.so", "valid");
        File missingIcd = new File(temporaryFolder.getRoot(), "vortek_icd.aarch64.json");
        assertTrue(GraphicsDriverInstallState.needsInstall(
            "vortek-2.1-gladio-1.1", "vortek-2.1-gladio-1.1",
            library, missingIcd));
    }

    @Test
    public void emptyRequiredFileForcesRecoveryEvenWhenCacheKeyMatches() throws Exception {
        File emptyLibrary = writeFile("libvulkan_vortek.so", "");
        assertTrue(GraphicsDriverInstallState.needsInstall(
            "vortek-2.1-gladio-1.1", "vortek-2.1-gladio-1.1",
            emptyLibrary));
    }

    @Test
    public void missingRequestedKeyOrRequiredFileListRequiresInstall() throws Exception {
        File library = writeFile("libGL.so.1.7.0", "valid");
        assertTrue(GraphicsDriverInstallState.needsInstall(null, "gladio-1.1", library));
        assertTrue(GraphicsDriverInstallState.needsInstall("gladio-1.1", "", library));
        assertTrue(GraphicsDriverInstallState.needsInstall("gladio-1.1", "gladio-1.1"));
    }
}
