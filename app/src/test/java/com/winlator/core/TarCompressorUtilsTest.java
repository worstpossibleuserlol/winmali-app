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
    public void rejectsEmptyArchive() throws IOException {
        File archive = createArchive();
        File destination = new File(temporaryFolder.getRoot(), "empty-extract");

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
    }

    @Test
    public void listenerCanSkipEntriesWithoutWritingThem() throws IOException {
        File archive = createArchive(EntrySpec.file("skip-me.txt", "payload"));
        File destination = new File(temporaryFolder.getRoot(), "skip-extract");

        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination,
            (file, size) -> null));
        assertFalse(new File(destination, "skip-me.txt").exists());
    }

    @Test
    public void extractionTruncatesAnExistingLongerFile() throws IOException {
        File archive = createArchive(EntrySpec.file("replace.txt", "new"));
        File destination = new File(temporaryFolder.getRoot(), "replace-extract");
        assertTrue(destination.mkdirs());
        Files.write(new File(destination, "replace.txt").toPath(), "old-content-that-is-longer".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertEquals("new", FileUtils.readString(new File(destination, "replace.txt")));
    }

    @Test
    public void allowsRepeatedDirectoryHeadersFromLayeredRootfsArchives() throws IOException {
        File archive = createArchive(EntrySpec.directory("usr"),
            EntrySpec.directory("usr"), EntrySpec.file("usr/bin/tool", "payload"));
        File destination = new File(temporaryFolder.getRoot(), "repeated-directories");

        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertEquals("payload", FileUtils.readString(new File(destination, "usr/bin/tool")));
    }

    @Test
    public void rebasesAbsoluteGuestSymlinksInsideExtractionRoot() throws IOException {
        File archive = createArchive(EntrySpec.directory("usr"),
            EntrySpec.symlink("usr/bin", "/bin"));
        File destination = new File(temporaryFolder.getRoot(), "absolute-guest-link");

        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        File link = new File(destination, "usr/bin");
        assertTrue(Files.isSymbolicLink(link.toPath()));
        assertEquals(java.nio.file.Paths.get("../bin"), Files.readSymbolicLink(link.toPath()));
    }

    @Test
    public void rejectsAbsoluteGuestSymlinkThatTraversesAboveRoot() throws IOException {
        File archive = createArchive(EntrySpec.symlink("escape", "/../../outside"));
        File destination = new File(temporaryFolder.getRoot(), "absolute-link-escape");

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertFalse(new File(temporaryFolder.getRoot(), "outside").exists());
    }

    @Test
    public void extractsSafeHardLinkAsIndependentFileCopy() throws IOException {
        File archive = createArchive(EntrySpec.file("usr/bin/tool", "linked-payload"),
            new EntrySpec("usr/bin/tool-copy", TarArchiveEntry.LF_LINK,
                "usr/bin/tool", new byte[0], false));
        File destination = new File(temporaryFolder.getRoot(), "hard-link-copy");

        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        File copied = new File(destination, "usr/bin/tool-copy");
        assertTrue(copied.isFile());
        assertFalse(Files.isSymbolicLink(copied.toPath()));
        assertEquals("linked-payload", FileUtils.readString(copied));
    }

    @Test
    public void rejectsHardLinkTargetsOutsideExtractionRoot() throws IOException {
        File archive = createArchive(new EntrySpec("linked", TarArchiveEntry.LF_LINK,
            "../outside", new byte[0], false));
        File destination = new File(temporaryFolder.getRoot(), "hard-link-escape");

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertFalse(new File(temporaryFolder.getRoot(), "outside").exists());
    }

    @Test
    public void rejectsSymlinkThatEscapesExtractionRoot() throws IOException {
        File archive = createArchive(
            EntrySpec.symlink("escape", "../outside"),
            EntrySpec.file("escape/payload.txt", "outside"));
        File destination = new File(temporaryFolder.getRoot(), "symlink-escape");

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
        assertFalse(new File(temporaryFolder.getRoot(), "outside/payload.txt").exists());
    }

    @Test
    public void rejectsHardLinksWithMissingTargets() throws IOException {
        File archive = createArchive(new EntrySpec("hard-link", TarArchiveEntry.LF_LINK,
            "target", new byte[0], false));
        File destination = new File(temporaryFolder.getRoot(), "hard-link-extract");

        assertFalse(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, destination));
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
    @Test
    public void backupRoundTripPreservesRelativeSymbolicLinks() throws IOException {
        File source = new File(temporaryFolder.getRoot(), "container-backup");
        assertTrue(source.mkdirs());
        File target = new File(source, "drive_c");
        assertTrue(target.mkdirs());
        Files.write(new File(target, "marker.txt").toPath(),
            "container-data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.createSymbolicLink(new File(source, "c-drive").toPath(), java.nio.file.Paths.get("drive_c"));

        File archive = new File(temporaryFolder.getRoot(), "container-backup.tar.zst");
        assertTrue(TarCompressorUtils.compress(TarCompressorUtils.Type.XZ, source, archive, 3));

        File restored = new File(temporaryFolder.getRoot(), "restored");
        assertTrue(TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archive, restored));
        File restoredLink = new File(restored, "container-backup/c-drive");
        assertTrue(Files.isSymbolicLink(restoredLink.toPath()));
        assertEquals(java.nio.file.Paths.get("drive_c"), Files.readSymbolicLink(restoredLink.toPath()));
        assertEquals("container-data", FileUtils.readString(new File(restoredLink, "marker.txt")));
    }

}
