package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void detectsDescendantsEvenWhenDestinationContainsDotDotSegments() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "source");
        assertTrue(source.mkdirs());
        File destination = new File(source, "../source/child");
        assertTrue(FileUtils.isAscendantOf(source, destination));
    }

    @Test
    public void copyingAFileOntoItselfDoesNotTruncateIt() throws IOException {
        File file = new File(temporaryFolder.getRoot(), "same-file.txt");
        Files.write(file.toPath(), "preserve-me".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(FileUtils.copy(file, file));
        assertEquals("preserve-me", FileUtils.readString(file));
    }

    @Test
    public void copyReplacesDestinationSymlinkWithoutTouchingItsTarget() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "source.txt");
        File target = new File(temporaryFolder.getRoot(), "external.txt");
        File link = new File(temporaryFolder.getRoot(), "destination-link");
        Files.write(source.toPath(), "new-data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(target.toPath(), "keep-data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.createSymbolicLink(link.toPath(), target.toPath());

        assertTrue(FileUtils.copy(source, link));
        assertFalse(Files.isSymbolicLink(link.toPath()));
        assertEquals("new-data", FileUtils.readString(link));
        assertEquals("keep-data", FileUtils.readString(target));
    }

    @Test
    public void basenameKeepsDotfilesAndStripsOnlyTheLastExtension() {
        assertEquals(".bashrc", FileUtils.getBasename(".bashrc"));
        assertEquals("game", FileUtils.getBasename("game.exe"));
        assertEquals("archive.tar", FileUtils.getBasename("archive.tar.gz"));
    }

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
