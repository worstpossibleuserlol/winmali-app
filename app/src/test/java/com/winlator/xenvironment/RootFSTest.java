package com.winlator.xenvironment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RootFSTest {
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
        assertTrue(new File(wine, "lib/wine/x86_64-windows").mkdirs());
        assertTrue(new File(wine, "lib/wine/i386-windows").mkdirs());
        assertTrue(new File(wine, "bin/wine").createNewFile());
        assertFalse(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void rejectsMissingArchitectureLibraryDirectory() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        assertTrue(new File(wine, "lib/wine/x86_64-windows").mkdirs());
        Files.write(new File(wine, "bin/wine").toPath(), new byte[]{1});
        assertFalse(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void acceptsCompleteWineRoot() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        assertTrue(new File(wine, "lib/wine/x86_64-windows").mkdirs());
        assertTrue(new File(wine, "lib/wine/i386-windows").mkdirs());
        Files.write(new File(wine, "bin/wine").toPath(), new byte[]{1, 2, 3});
        assertTrue(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }

    @Test
    public void acceptsWine64WhenWineShimIsAbsent() throws Exception {
        File wine = new File(temporaryFolder.getRoot(), "opt/wine");
        assertTrue(new File(wine, "bin").mkdirs());
        assertTrue(new File(wine, "lib/wine/x86_64-windows").mkdirs());
        assertTrue(new File(wine, "lib/wine/i386-windows").mkdirs());
        Files.write(new File(wine, "bin/wine64").toPath(), new byte[]{1, 2, 3});
        assertTrue(RootFS.hasCoreWineFiles(temporaryFolder.getRoot()));
    }
}
