package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

public class WinComponentInstallStateTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void requiredDllsAreCheckedInBothWindowsArchitectures() {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        File[] required = WinComponentInstallState.getRequiredFiles(
            windowsDir, new String[]{"d3dx9_43", "d3dcompiler_47.dll"});

        assertNotNull(required);
        assertEquals(4, required.length);
        assertContains(required, new File(windowsDir, "system32/d3dx9_43.dll"));
        assertContains(required, new File(windowsDir, "syswow64/d3dx9_43.dll"));
        assertContains(required, new File(windowsDir, "system32/d3dcompiler_47.dll"));
        assertContains(required, new File(windowsDir, "syswow64/d3dcompiler_47.dll"));
    }

    @Test
    public void emptyOrMissingDllsAreDetectedForRepair() throws Exception {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        String[] dlls = {"d3dx9_43", "d3dcompiler_47"};
        File[] required = WinComponentInstallState.getRequiredFiles(windowsDir, dlls);
        assertFalse(WinComponentInstallState.arePresent(windowsDir, dlls));

        for (File file : required) {
            File parent = file.getParentFile();
            assertTrue(parent.isDirectory() || parent.mkdirs());
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(1);
            }
        }
        assertTrue(WinComponentInstallState.arePresent(windowsDir, dlls));

        File missing32Bit = new File(windowsDir, "syswow64/d3dx9_43.dll");
        assertTrue(missing32Bit.delete());
        assertFalse(WinComponentInstallState.arePresent(windowsDir, dlls));
    }

    @Test
    public void unsafeOrEmptyDllNamesCannotPassValidation() {
        File windowsDir = new File(temporaryFolder.getRoot(), "windows");
        assertNull(WinComponentInstallState.getRequiredFiles(windowsDir, new String[]{"../d3dx9_43"}));
        assertNull(WinComponentInstallState.getRequiredFiles(windowsDir, new String[0]));
        assertFalse(WinComponentInstallState.arePresent(windowsDir, null));
    }

    private static void assertContains(File[] files, File expected) {
        for (File file : files) {
            if (file.equals(expected)) return;
        }
        fail("Required Direct3D file was not included: " + expected);
    }
}
