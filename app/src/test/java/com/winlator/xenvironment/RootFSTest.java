package com.winlator.xenvironment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RootFSTest {
    private static byte[] x86_64ElfHeader() {
        byte[] header = new byte[20];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = 2;
        header[5] = 1;
        header[18] = 62; // EM_X86_64
        return header;
    }

    private static void prepareWineLibraries(File wine) throws Exception {
        File x64 = new File(wine, "lib/wine/x86_64-windows");
        File x86 = new File(wine, "lib/wine/i386-windows");
        assertTrue(x64.mkdirs());
        assertTrue(x86.mkdirs());
        Files.write(new File(x64, "kernel32.dll").toPath(), new byte[]{1});
        Files.write(new File(x86, "kernel32.dll").toPath(), new byte[]{1});
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsNullRoot() {
        assertFalse(RootFS.hasCoreWineFiles(null));
    }

    @Test
    public void rejectsMissingRootDirectory() {
        assertFalse(RootFS.hasCoreWineFiles(new File(temporaryFolder.getRoot(), "missing")));
    }

    @Test
    public void rejectsEmptyRootDirectory() {
        assertFalse(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void rejectsMissingWineExecutable() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "lib/wine/x86_64-windows").mkdirs());
        assertTrue(new File(wine, "lib/wine/i386-windows").mkdirs());
        assertFalse(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void rejectsEmptyWineExecutable() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        prepareWineLibraries(wine);
        assertTrue(new File(wine, "bin/wine").createNewFile());
        assertFalse(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void rejectsMissingArchitectureLibraryDirectory() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        File x64 = new File(wine, "lib/wine/x86_64-windows");
        assertTrue(x64.mkdirs());
        Files.write(new File(x64, "kernel32.dll").toPath(), new byte[]{1});
        Files.write(new File(wine, "bin/wine").toPath(), x86_64ElfHeader());
        assertFalse(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void rejectsAarch64WineBecauseBox64RequiresX86_64Wine() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        prepareWineLibraries(wine);
        byte[] header = x86_64ElfHeader();
        header[18] = (byte)183; // EM_AARCH64
        Files.write(new File(wine, "bin/wine").toPath(), header);
        assertFalse(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void acceptsCompleteWineRoot() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        prepareWineLibraries(wine);
        Files.write(new File(wine, "bin/wine").toPath(), x86_64ElfHeader());
        assertTrue(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void acceptsWine64WhenWineShimIsAbsent() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        prepareWineLibraries(wine);
        Files.write(new File(wine, "bin/wine64").toPath(), x86_64ElfHeader());
        assertTrue(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }
}
