package com.winlator.core;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TarCompressorUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static class EntrySpec {
        final String name;
        final byte type;
        final String linkName;
        final byte[] data;
        final boolean preserveAbsolutePath;

        private EntrySpec(String name, byte type, String linkName, byte[] data, boolean preserveAbsolutePath) {
            this.name = name;
            this.type = type;
            this.linkName = linkName;
            this.data = data;
            this.preserveAbsolutePath = preserveAbsolutePath;
        }

        static EntrySpec file(String name, String data) {
            return new EntrySpec(name, TarArchiveEntry.LF_NORMAL, null, data.getBytes(java.nio.charset.StandardCharsets.UTF_8), false);
        }

        static EntrySpec directory(String name) {
            return new EntrySpec(name, TarArchiveEntry.LF_DIR, null, new byte[0], false);
        }

        static EntrySpec symlink(String name, String linkName) {
            return new EntrySpec(name, TarArchiveEntry.LF_SYMLINK, linkName, new byte[0], false);
        }

        static EntrySpec absoluteFile(String name, String data) {
            return new EntrySpec(name, TarArchiveEntry.LF_NORMAL, null,
                data.getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
        }
    }

    private File createArchive(EntrySpec... entries) throws IOException {
        File archive = new File(temporaryFolder.getRoot(), "input.tar.xz");
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(
                new XZCompressorOutputStream(new FileOutputStream(archive)))) {
            for (EntrySpec spec : entries) {
                String entryName = spec.type == TarArchiveEntry.LF_DIR && !spec.name.endsWith("/")
                    ? spec.name + "/" : spec.name;
                TarArchiveEntry entry = new TarArchiveEntry(entryName, spec.type, spec.preserveAbsolutePath);
                if (spec.type == TarArchiveEntry.LF_SYMLINK) {
                    entry.setLinkName(spec.linkName);
                }
                else if (spec.type != TarArchiveEntry.LF_DIR) {
                    entry.setSize(spec.data.length);
                }
                tar.putArchiveEntry(entry);
                if (spec.data.length > 0) tar.write(spec.data);
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
        return archive;
    }

    @Test
    public void createsCompleteArchiveAndCanExtractIt() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "source");
        assertTrue(source.mkdirs());
        File payload = new File(source, "payload.txt");
        Files.write(payload.toPath(), "complete-data".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        File archive = new File(temporaryFolder.getRoot(), "created.tar.xz");
        assertTrue(TarCompressorUtils.compress(TarCompressorUtils.Type.XZ, source, archive));
        assertTrue(archive.isFile());
        assertTrue(archive.length() > 0);

        File extracted = new File(temporaryFolder.getRoot(), "extracted");
        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, extracted));
        assertEquals("complete-data", FileUtils.readString(new File(extracted, "source/payload.txt")));
    }

    @Test
    public void createsAndExtractsGzipArchive() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "gzip-source");
        assertTrue(source.mkdirs());
        Files.write(new File(source, "payload.txt").toPath(),
            "gzip-runtime".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        File archive = new File(temporaryFolder.getRoot(), "runtime.tar.gz");
        assertTrue(TarCompressorUtils.compress(TarCompressorUtils.Type.GZIP, source, archive));
        File extracted = new File(temporaryFolder.getRoot(), "gzip-extracted");
        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.GZIP, archive, extracted));
        assertEquals("gzip-runtime", FileUtils.readString(new File(extracted, "gzip-source/payload.txt")));
    }

    @Test
    public void reportsMissingSourceInsteadOfCreatingAnArchive() {
        File missing = new File(temporaryFolder.getRoot(), "missing-source");
        File archive = new File(temporaryFolder.getRoot(), "should-not-exist.tar.xz");

        assertFalse(TarCompressorUtils.compress(TarCompressorUtils.Type.XZ, missing, archive));
        assertFalse(archive.exists());
    }

    @Test
    public void extractsNormalRelativeFiles() throws IOException {
        File destination = new File(temporaryFolder.getRoot(), "rootfs");
        File archive = createArchive(EntrySpec.directory("wine"),
            EntrySpec.file("wine/bin/wine", "wine-binary"));

        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertEquals("wine-binary", FileUtils.readString(new File(destination, "wine/bin/wine")));
    }

    @Test
    public void rejectsParentTraversalEntry() throws IOException {
        File destination = new File(temporaryFolder.getRoot(), "rootfs");
        File archive = createArchive(EntrySpec.file("../escaped.txt", "outside"));

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertFalse(new File(temporaryFolder.getRoot(), "escaped.txt").exists());
    }

    @Test
    public void rejectsAbsoluteArchiveEntry() throws IOException {
        File destination = new File(temporaryFolder.getRoot(), "rootfs");
        File escaped = new File(temporaryFolder.getRoot(), "absolute-escape.txt");
        File archive = createArchive(EntrySpec.absoluteFile(escaped.getAbsolutePath(), "outside"));

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertFalse(escaped.exists());
    }

    @Test
    public void rejectsSymlinkTargetOutsideDestination() throws IOException {
        File destination = new File(temporaryFolder.getRoot(), "rootfs");
        File archive = createArchive(EntrySpec.symlink("escape", "../../outside.txt"));

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertFalse(new File(temporaryFolder.getRoot(), "outside.txt").exists());
    }

    @Test
    public void refusesToWriteThroughExistingSymlinkParent() throws IOException {
        File destination = new File(temporaryFolder.getRoot(), "rootfs");
        File inside = new File(destination, "inside");
        assertTrue(inside.mkdirs());
        Files.createSymbolicLink(new File(destination, "alias").toPath(), inside.toPath());
        File archive = createArchive(EntrySpec.file("alias/payload.txt", "must-not-write"));

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertFalse(new File(inside, "payload.txt").exists());
    }
}
