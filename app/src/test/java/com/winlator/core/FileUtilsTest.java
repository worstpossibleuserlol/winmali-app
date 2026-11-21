package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void copyPreservesRelativeSymbolicLinks() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "source");
        File destination = new File(temporaryFolder.getRoot(), "destination");
        assertTrue(source.mkdirs());

        File target = new File(source, "drive_c");
        assertTrue(target.mkdirs());
        Files.createSymbolicLink(new File(source, "c-drive").toPath(), Paths.get("drive_c"));

        assertTrue(FileUtils.copy(source, destination));

        File copiedLink = new File(destination, "c-drive");
        assertTrue(Files.isSymbolicLink(copiedLink.toPath()));
        assertEquals(Paths.get("drive_c"), Files.readSymbolicLink(copiedLink.toPath()));
        assertTrue(new File(copiedLink.getParentFile(), Files.readSymbolicLink(copiedLink.toPath()).toString()).isDirectory());
    }

    @Test
    public void copyPreservesAbsoluteSymbolicLinks() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "absolute-source");
        File destination = new File(temporaryFolder.getRoot(), "absolute-destination");
        assertTrue(source.mkdirs());

        File externalTarget = new File(temporaryFolder.getRoot(), "external-target");
        assertTrue(externalTarget.mkdirs());
        Files.createSymbolicLink(new File(source, "external").toPath(), externalTarget.toPath());

        assertTrue(FileUtils.copy(source, destination));

        File copiedLink = new File(destination, "external");
        assertTrue(Files.isSymbolicLink(copiedLink.toPath()));
        assertEquals(externalTarget.toPath(), Files.readSymbolicLink(copiedLink.toPath()));
    }
}
