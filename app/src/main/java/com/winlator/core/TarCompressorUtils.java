package com.winlator.core;

import android.content.Context;
import android.net.Uri;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TarCompressorUtils {
    private static final int MAX_METADATA_READ_BYTES = 16 * 1024 * 1024;

    public enum Type {XZ, ZSTD, GZIP}

    public interface OnExtractFileListener {
        File onExtractFile(File destination, long size);
    }

    private static void addFile(ArchiveOutputStream tar, File file, String entryName) throws IOException {
        tar.putArchiveEntry(tar.createArchiveEntry(file, entryName));
        try (BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(file), StreamUtils.BUFFER_SIZE)) {
            if (!StreamUtils.copy(inStream, tar)) throw new IOException("Unable to read archive source: "+file);
        }
        finally {
            tar.closeArchiveEntry();
        }
    }

    private static void addLinkFile(ArchiveOutputStream tar, File file, String entryName) throws IOException {
        String linkTarget = FileUtils.readSymlink(file);
        if (linkTarget == null || linkTarget.isEmpty()) throw new IOException("Unable to read symbolic link: "+file);
        TarArchiveEntry entry = new TarArchiveEntry(entryName, TarConstants.LF_SYMLINK);
        entry.setLinkName(linkTarget);
        tar.putArchiveEntry(entry);
        tar.closeArchiveEntry();
    }

    private static void addDirectory(ArchiveOutputStream tar, File folder, String basePath) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) throw new IOException("Unable to list archive directory: "+folder);
        for (File file : files) {
            if (FileUtils.isSymlink(file)) {
                addLinkFile(tar, file, basePath+file.getName());
            }
            else if (file.isDirectory()) {
                String entryName = basePath+file.getName() + "/";
                tar.putArchiveEntry(tar.createArchiveEntry(file, entryName));
                tar.closeArchiveEntry();
                addDirectory(tar, file, entryName);
            }
            else addFile(tar, file, basePath+file.getName());
        }
    }

    public static boolean compress(Type type, File file, File destination) {
        return compress(type, file, destination, 3);
    }

    public static boolean compress(Type type, File file, File destination, int level) {
        return compress(type, new File[]{file}, destination, level);
    }

    public static boolean compress(Type type, File[] files, File destination, int level) {
        if (files == null || files.length == 0 || destination == null) return false;
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;

        File temporaryFile;
        try {
            String temporaryPrefix = destination.getName()+".";
            while (temporaryPrefix.length() < 3) temporaryPrefix += "_";
            temporaryFile = File.createTempFile(temporaryPrefix, ".partial", parent);
        }
        catch (IOException e) {
            return false;
        }

        boolean completed = false;
        try {
            try (OutputStream outStream = getCompressorOutputStream(type, temporaryFile, level);
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(outStream)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                boolean skipFirstEntry = files.length == 1 && files[0] != null && files[0].getName().equals(".");
                for (File file : files) {
                    if (file == null || (!file.exists() && !FileUtils.isSymlink(file))) {
                        throw new IOException("Archive source does not exist: "+file);
                    }
                    if (FileUtils.isSymlink(file)) {
                        addLinkFile(tar, file, file.getName());
                    }
                    else if (file.isDirectory()) {
                        String basePath = "";
                        if (!skipFirstEntry) {
                            basePath = file.getName() + "/";
                            tar.putArchiveEntry(tar.createArchiveEntry(file, basePath));
                            tar.closeArchiveEntry();
                        }
                        addDirectory(tar, file, basePath);
                    }
                    else if (file.isFile()) addFile(tar, file, file.getName());
                    else throw new IOException("Unsupported archive source: "+file);
                }
                tar.finish();
            }

            if (!temporaryFile.isFile() || temporaryFile.length() == 0) throw new IOException("Archive output is empty");
            try {
                Files.move(temporaryFile.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            completed = destination.isFile() && destination.length() > 0;
        }
        catch (IOException | RuntimeException e) {
            completed = false;
        }
        finally {
            if (temporaryFile.exists()) temporaryFile.delete();
            if (!completed && destination.exists() && destination.length() == 0) destination.delete();
        }
        return completed;
    }

    public static Type detectType(Context context, Uri source) {
        if (context == null || source == null) return null;
        try (InputStream input = context.getContentResolver().openInputStream(source);
             BufferedInputStream buffered = new BufferedInputStream(input)) {
            if (input == null) return null;
            buffered.mark(8);
            byte[] magic = new byte[6];
            int count = buffered.read(magic);
            buffered.reset();
            if (count >= 6 && (magic[0] & 0xff) == 0xfd && magic[1] == 0x37 &&
                magic[2] == 0x7a && magic[3] == 0x58 && magic[4] == 0x5a && magic[5] == 0x00) return Type.XZ;
            if (count >= 4 && (magic[0] & 0xff) == 0x28 && (magic[1] & 0xff) == 0xb5 &&
                (magic[2] & 0xff) == 0x2f && (magic[3] & 0xff) == 0xfd) return Type.ZSTD;
            if (count >= 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b) return Type.GZIP;
            return null;
        }
        catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static boolean extract(Type type, Context context, String assetFile, File destination) {
        return extract(type, context, assetFile, destination, null);
    }

    public static boolean extract(Type type, Context context, String assetFile, File destination, OnExtractFileListener onExtractFileListener) {
        try {
            return extract(type, context.getAssets().open(assetFile), destination, onExtractFileListener);
        }
        catch (IOException e) {
            return false;
        }
    }

    public static boolean extract(Type type, Context context, Uri source, File destination) {
        return extract(type, context, source, destination, null);
    }

    public static boolean extract(Type type, Context context, Uri source, File destination, OnExtractFileListener onExtractFileListener) {
        if (source == null) return false;
        try {
            return extract(type, context.getContentResolver().openInputStream(source), destination, onExtractFileListener);
        }
        catch (FileNotFoundException e) {
            return false;
        }
    }

    public static boolean extract(Type type, File source, File destination) {
        return extract(type, source, destination, null);
    }

    public static boolean extract(Type type, File source, File destination, OnExtractFileListener onExtractFileListener) {
        if (source == null || !source.isFile()) return false;
        try {
            return extract(type, new BufferedInputStream(new FileInputStream(source), StreamUtils.BUFFER_SIZE), destination, onExtractFileListener);
        }
        catch (FileNotFoundException e) {
            return false;
        }
    }

    private static boolean isWithinDirectory(File directory, File candidate) throws IOException {
        String rootPath = directory.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
    }

    private static boolean hasParentTraversal(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.matches("^[A-Za-z]:.*")) return true;
        String[] parts = path.replace((char)92, '/').split("/");
        for (String part : parts) {
            if ("..".equals(part)) return true;
        }
        return false;
    }

    private static boolean hasSymlinkParent(File root, File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        while (parent != null && !parent.equals(root)) {
            if (FileUtils.isSymlink(parent)) return true;
            parent = parent.getParentFile();
        }
        return false;
    }

    private static File resolveArchiveEntry(File root, String entryName) throws IOException {
        if (hasParentTraversal(entryName)) return null;
        File file = new File(root, entryName);
        if (!isWithinDirectory(root, file) || hasSymlinkParent(root, file)) return null;
        return file;
    }

    private static boolean extract(Type type, InputStream source, File destination, OnExtractFileListener onExtractFileListener) {
        if (source == null || destination == null) return false;
        try (InputStream sourceStream = source) {
            if (FileUtils.isSymlink(destination)) return false;
            if (!destination.isDirectory() && !destination.mkdirs()) return false;
            File root = destination.getCanonicalFile();

            try (InputStream inStream = getCompressorInputStream(type, sourceStream);
                 ArchiveInputStream tar = new TarArchiveInputStream(inStream)) {
                TarArchiveEntry entry;
                int entryCount = 0;
                while ((entry = (TarArchiveEntry)tar.getNextEntry()) != null) {
                    if (!tar.canReadEntryData(entry)) return false;
                    entryCount++;
                    if (entry.isLink() && !entry.isSymbolicLink()) return false;
                    if (!entry.isDirectory() && !entry.isSymbolicLink() && !entry.isFile()) return false;

                    File file = resolveArchiveEntry(root, entry.getName());
                    if (file == null) return false;

                    if (onExtractFileListener != null) {
                        file = onExtractFileListener.onExtractFile(file, entry.getSize());
                        if (file == null) {
                            if (entry.isFile() && entry.getSize() > 0) {
                                byte[] discard = new byte[StreamUtils.BUFFER_SIZE];
                                while (tar.read(discard) != -1) {}
                            }
                            continue;
                        }
                        file = file.getAbsoluteFile();
                        if (!isWithinDirectory(root, file) || hasSymlinkParent(root, file)) return false;
                    }

                    if (entry.isDirectory()) {
                        if (file.exists() && !file.isDirectory()) return false;
                        if (!file.isDirectory() && !file.mkdirs()) return false;
                    }
                    else if (entry.isSymbolicLink()) {
                        String linkName = entry.getLinkName();
                        if (linkName == null || linkName.isEmpty() || new File(linkName).isAbsolute()) return false;

                        File linkTarget = new File(file.getParentFile(), linkName);
                        if (!isWithinDirectory(root, linkTarget)) return false;
                        if (file.exists() || FileUtils.isSymlink(file)) {
                            if (!FileUtils.delete(file)) return false;
                        }
                        if (!FileUtils.symlink(linkName, file.getAbsolutePath())) return false;
                    }
                    else {
                        File parent = file.getParentFile();
                        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
                        if (!isWithinDirectory(root, file) || hasSymlinkParent(root, file)) return false;
                        if (FileUtils.isSymlink(file) || (file.exists() && !file.isFile())) return false;

                        try (BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file), StreamUtils.BUFFER_SIZE)) {
                            if (!StreamUtils.copy(tar, outStream)) return false;
                        }
                        if (entry.getSize() >= 0 && file.length() != entry.getSize()) return false;
                    }

                    if (!FileUtils.isSymlink(file)) FileUtils.chmod(file, 0771);
                }
                return entryCount > 0;
            }
        }
        catch (IOException | SecurityException e) {
            return false;
        }
    }

    public static long getContentLength(Type type, Context context, String assetFile, File destination) {
        AtomicLong totalSizeRef = new AtomicLong();
        extract(type, context, assetFile, destination, (file, size) -> {
            totalSizeRef.addAndGet(size);
            return null;
        });
        return totalSizeRef.get();
    }

    public static byte[] read(Type type, File source, String localPath) {
        if (type == null || source == null || !source.isFile() || localPath == null) return null;
        boolean pathIsPrefix = false;
        boolean pathIsSuffix = false;

        if (localPath.startsWith("*")) {
            pathIsSuffix = true;
        }
        else if (localPath.endsWith("*")) {
            pathIsPrefix = true;
        }

        localPath = localPath.replace("*", "");
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();

        try (InputStream inStream = getCompressorInputStream(type, new BufferedInputStream(new FileInputStream(source), StreamUtils.BUFFER_SIZE));
             ArchiveInputStream tar = new TarArchiveInputStream(inStream)) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry)tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) continue;
                String entryName = entry.getName();
                boolean match = pathIsSuffix ? entryName.endsWith(localPath) : (pathIsPrefix ? entryName.startsWith(localPath) : entryName.equals(localPath));

                if (match && !entry.isDirectory() && !entry.isSymbolicLink()) {
                    if (entry.getSize() > MAX_METADATA_READ_BYTES) return null;
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = tar.read(buffer)) != -1) {
                        if (dataOutputStream.size() + bytesRead > MAX_METADATA_READ_BYTES) return null;
                        dataOutputStream.write(buffer, 0, bytesRead);
                    }
                    return dataOutputStream.toByteArray();
                }
            }
            return null;
        }
        catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static InputStream getCompressorInputStream(Type type, InputStream source) throws IOException {
        if (type == Type.XZ) {
            return new XZCompressorInputStream(source);
        }
        else if (type == Type.ZSTD) {
            return new ZstdCompressorInputStream(source);
        }
        else if (type == Type.GZIP) {
            return new GzipCompressorInputStream(source);
        }
        throw new IOException("Unsupported archive compression type: "+type);
    }

    private static OutputStream getCompressorOutputStream(Type type, File destination, int level) throws IOException {
        if (type == Type.XZ) {
            return new XZCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE), level);
        }
        else if (type == Type.ZSTD) {
            return new ZstdCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE), level);
        }
        else if (type == Type.GZIP) {
            return new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE));
        }
        throw new IOException("Unsupported archive compression type: "+type);
    }
}
