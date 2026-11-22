package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZipUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File createZip(String entryName, String payload) throws Exception {
        File zipFile = temporaryFolder.newFile("input.zip");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zipFile))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(payload.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return zipFile;
    }

    @Test
    public void extractsNormalRelativeEntries() throws Exception {
        File zipFile = createZip("lib/libvulkan.so", "library");
        File destination = new File(temporaryFolder.getRoot(), "driver");
        assertTrue(ZipUtils.extract(zipFile, destination));
        assertEquals("library", FileUtils.readString(new File(destination, "lib/libvulkan.so")));
    }

    @Test
    public void rejectsParentDirectoryTraversal() throws Exception {
        File zipFile = createZip("../escape.txt", "outside");
        File destination = new File(temporaryFolder.getRoot(), "driver");
        assertFalse(ZipUtils.extract(zipFile, destination));
        assertFalse(new File(temporaryFolder.getRoot(), "escape.txt").exists());
    }

    @Test
    public void rejectsBackslashTraversal() throws Exception {
        File zipFile = createZip("..\\escape.txt", "outside");
        File destination = new File(temporaryFolder.getRoot(), "driver");
        assertFalse(ZipUtils.extract(zipFile, destination));
        assertFalse(new File(temporaryFolder.getRoot(), "escape.txt").exists());
    }

    @Test
    public void rejectsAbsoluteZipEntries() throws Exception {
        File zipFile = createZip("/tmp/winmali-escape.txt", "outside");
        File destination = new File(temporaryFolder.getRoot(), "driver");
        assertFalse(ZipUtils.extract(zipFile, destination));
        assertFalse(new File("/tmp/winmali-escape.txt").exists());
    }

    @Test
    public void failedImportPreservesPreviouslyInstalledDriver() throws Exception {
        File destination = new File(temporaryFolder.getRoot(), "driver");
        assertTrue(destination.mkdirs());
        File existing = new File(destination, "keep.txt");
        assertTrue(FileUtils.writeString(existing, "working-driver"));

        File zipFile = createZip("../escape.txt", "outside");
        assertFalse(ZipUtils.extract(zipFile, destination));
        assertTrue(existing.isFile());
        assertEquals("working-driver", FileUtils.readString(existing));
    }
}
