package com.winlator.core;

import com.winlator.container.DXWrappers;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

public class DXWrapperInstallStateTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void wineD3DRequiredFilesIncludeBoth32And64BitDllSets() {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        File[] required = DXWrapperInstallState.getRequiredDllFiles(
            windowsDir, DXWrappers.WINED3D, DXWrappers.WINED3D);

        assertNotNull(required);
        assertContains(required, new File(windowsDir, "system32/d3d9.dll"));
        assertContains(required, new File(windowsDir, "syswow64/d3d9.dll"));
        assertContains(required, new File(windowsDir, "system32/d3d11.dll"));
        assertContains(required, new File(windowsDir, "syswow64/d3d11.dll"));
        assertContains(required, new File(windowsDir, "system32/wined3d.dll"));
        assertContains(required, new File(windowsDir, "syswow64/wined3d.dll"));
        assertContains(required, new File(windowsDir, "system32/d3d12.dll"));
        assertContains(required, new File(windowsDir, "syswow64/d3d12.dll"));
    }

    @Test
    public void dxvkRequiredFilesIncludeD3DAndD3D12InBothArchitectures() {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        File[] required = DXWrapperInstallState.getRequiredDllFiles(
            windowsDir, DXWrappers.DXVK, DXWrappers.WINED3D);

        assertNotNull(required);
        assertContains(required, new File(windowsDir, "system32/dxgi.dll"));
        assertContains(required, new File(windowsDir, "syswow64/dxgi.dll"));
        assertContains(required, new File(windowsDir, "system32/d3d8.dll"));
        assertContains(required, new File(windowsDir, "syswow64/d3d8.dll"));
        assertContains(required, new File(windowsDir, "system32/d3d12core.dll"));
        assertContains(required, new File(windowsDir, "syswow64/d3d12core.dll"));
    }

    @Test
    public void d7vkRequiresItsReplacementAndPreservedBuiltinDdrawFiles() {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        File[] required = DXWrapperInstallState.getRequiredDllFiles(
            windowsDir, DXWrappers.WINED3D, DXWrappers.D7VK);

        assertNotNull(required);
        assertContains(required, new File(windowsDir, "system32/ddraw.dll"));
        assertContains(required, new File(windowsDir, "syswow64/ddraw.dll"));
        assertContains(required, new File(windowsDir, "syswow64/ddraw_.dll"));
    }

    @Test
    public void validBothArchitectureD3DFilesAllowCacheReuse() throws Exception {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        File[] required = DXWrapperInstallState.getRequiredDllFiles(
            windowsDir, DXWrappers.WINED3D, DXWrappers.WINED3D);
        for (File file : required) {
            File parent = file.getParentFile();
            assertTrue(parent.isDirectory() || parent.mkdirs());
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(1);
            }
        }

        assertFalse(GraphicsDriverInstallState.needsInstall("wine-11", "wine-11", required));
        File missing32Bit = new File(windowsDir, "syswow64/d3d9.dll");
        assertTrue(missing32Bit.delete());
        assertTrue(GraphicsDriverInstallState.needsInstall("wine-11", "wine-11", required));
    }

    @Test
    public void unknownWrapperDoesNotProduceAReusableFileSet() {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        assertNull(DXWrapperInstallState.getRequiredDllFiles(
            windowsDir, "not-a-wrapper", DXWrappers.WINED3D));
    }

    private static void assertContains(File[] files, File expected) {
        for (File file : files) {
            if (file.equals(expected)) return;
        }
        fail("Required Direct3D file was not included: " + expected);
    }
}
