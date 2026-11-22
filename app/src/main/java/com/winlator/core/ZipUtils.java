package com.winlator.core;

import android.content.Context;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public abstract class ZipUtils {
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_READ_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 100000;

    private static void addFile(ZipOutputStream zip, File file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zip.putNextEntry(entry);
        try (BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(file), StreamUtils.BUFFER_SIZE)) {
            if (!StreamUtils.copy(inStream, zip)) throw new IOException("Unable to read ZIP source: "+file);
        }
        finally {
            zip.closeEntry();
        }
    }

    private static void addDirectory(ZipOutputStream zip, File folder, String basePath) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) throw new IOException("Unable to list ZIP directory: "+folder);
        for (File file : files) {
            if (FileUtils.isSymlink(file)) continue;
            if (file.isDirectory()) {
                String entryName = basePath+file.getName() + "/";
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(file.lastModified());
                zip.putNextEntry(entry);
                zip.closeEntry();
                addDirectory(zip, file, entryName);
            }
            else if (file.isFile()) addFile(zip, file, basePath+file.getName());
            else throw new IOException("Unsupported ZIP source: "+file);
        }
    }

    public static boolean compress(File file, File destination) {
        return compress(new File[]{file}, destination);
    }

    public static boolean compress(File[] files, File destination) {
        if (files == null || files.length == 0 || destination == null) return false;
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
        File temporary = new File(parent, destination.getName()+".partial-"+UUID.randomUUID());
        boolean completed = false;
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temporary), StreamUtils.BUFFER_SIZE))) {
                for (File file : files) {
                    if (file == null || (!file.exists() && !FileUtils.isSymlink(file))) {
                        throw new IOException("ZIP source does not exist");
                    }
                    if (FileUtils.isSymlink(file)) continue;
                    if (file.isDirectory()) {
                        String basePath = file.getName() + "/";
                        zip.putNextEntry(new ZipEntry(basePath));
                        zip.closeEntry();
                        addDirectory(zip, file, basePath);
                    }
                    else if (file.isFile()) addFile(zip, file, file.getName());
                    else throw new IOException("Unsupported ZIP source: "+file);
                }
                zip.finish();
            }
            if (!temporary.isFile() || temporary.length() == 0) throw new IOException("ZIP archive is empty");
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            completed = destination.isFile() && destination.length() > 0;
            return completed;
        }
        catch (IOException | RuntimeException error) {
            android.util.Log.w("WinMali-ZIP", "ZIP compression failed", error);
            return false;
        }
        finally {
            if (temporary.exists()) temporary.delete();
            if (!completed && destination.exists() && destination.length() == 0) destination.delete();
        }
    }

    private static String normalizeEntryName(String entryName, boolean directory) {
        if (entryName == null || entryName.isEmpty() || entryName.indexOf('\u0000') >= 0) return null;
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        if (directory && normalized.equals(".")) return "";
        if (normalized.isEmpty() || normalized.startsWith("/") ||
            normalized.matches("^[A-Za-z]:.*")) return null;

        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return directory ? "" : null;

        String[] parts = normalized.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
        }
        return normalized;
    }

    private static boolean isWithinDirectory(File root, File candidate) throws IOException {
        String rootPath = root.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        return candidatePath.startsWith(rootPath + File.separator);
    }

    private static boolean hasSymlinkParent(File root, File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        while (parent != null && !parent.equals(root)) {
            if (FileUtils.isSymlink(parent)) return true;
            parent = parent.getParentFile();
        }
        return false;
    }

    private static boolean extractToDirectory(File source, File root) {
        long totalExtracted = 0;
        int entryCount = 0;
        java.util.HashSet<String> seenEntries = new java.util.HashSet<>();
        try (ZipFile zipFile = new ZipFile(source)) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (++entryCount > MAX_ARCHIVE_ENTRIES || entry.isUnixSymlink()) return false;

                String entryName = normalizeEntryName(entry.getName(), entry.isDirectory());
                if (entryName == null) return false;
                if (entryName.isEmpty()) continue;
                if (!seenEntries.add(entryName)) return false;

                File file = new File(root, entryName).getCanonicalFile();
                if (!isWithinDirectory(root, file) || hasSymlinkParent(root, file) || FileUtils.isSymlink(file)) {
                    return false;
                }

                if (entry.isDirectory()) {
                    if (file.exists() && !file.isDirectory()) return false;
                    if (!file.isDirectory() && !file.mkdirs()) return false;
                    FileUtils.chmod(file, 0771);
                    continue;
                }

                if (entry.getSize() > MAX_EXTRACTED_BYTES ||
                    (file.exists() && !file.isFile())) return false;
                File parent = file.getParentFile();
                if (parent == null || (!parent.isDirectory() && !parent.mkdirs()) ||
                    hasSymlinkParent(root, file) || !isWithinDirectory(root, file)) return false;

                try (InputStream inStream = new BufferedInputStream(zipFile.getInputStream(entry), StreamUtils.BUFFER_SIZE);
                     BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file), StreamUtils.BUFFER_SIZE)) {
                    byte[] buffer = new byte[32 * 1024];
                    long entryBytes = 0;
                    int count;
                    while ((count = inStream.read(buffer)) != -1) {
                        entryBytes += count;
                        totalExtracted += count;
                        if (totalExtracted > MAX_EXTRACTED_BYTES) return false;
                        outStream.write(buffer, 0, count);
                    }
                    outStream.flush();
                    if (entry.getSize() >= 0 && entryBytes != entry.getSize()) return false;
                }
                FileUtils.chmod(file, 0771);
            }
            return true;
        }
        catch (IOException | SecurityException e) {
            return false;
        }
    }

    /**
     * Extracts an untrusted ZIP into a private staging directory and activates it
     * only after every entry has passed path, symlink, and size validation.
     */
    public static boolean extract(File source, File destination) {
        if (source == null || !source.isFile() || destination == null ||
            FileUtils.isSymlink(destination)) return false;

        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
        File staging = new File(parent, destination.getName()+".zip-stage-"+UUID.randomUUID());
        File backup = new File(parent, destination.getName()+".zip-backup-"+UUID.randomUUID());
        if (!staging.mkdirs()) return false;
        boolean backupCreated = false;

        try {
            if (!extractToDirectory(source, staging)) return false;
            if (destination.exists()) {
                if (!destination.isDirectory()) return false;
                try {
                    Files.move(destination.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE);
                }
                catch (AtomicMoveNotSupportedException e) {
                    Files.move(destination.toPath(), backup.toPath());
                }
                backupCreated = true;
            }

            try {
                try {
                    Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
                }
                catch (AtomicMoveNotSupportedException e) {
                    Files.move(staging.toPath(), destination.toPath());
                }
            }
            catch (IOException activationError) {
                if (backupCreated && backup.exists() && !destination.exists()) {
                    Files.move(backup.toPath(), destination.toPath());
                    backupCreated = false;
                }
                return false;
            }

            if (!destination.isDirectory()) {
                if (backupCreated && backup.exists()) {
                    Files.move(backup.toPath(), destination.toPath());
                    backupCreated = false;
                }
                return false;
            }

            if (backupCreated) {
                FileUtils.delete(backup);
                backupCreated = false;
            }
            return true;
        }
        catch (IOException | RuntimeException e) {
            return false;
        }
        finally {
            if (staging.exists()) FileUtils.delete(staging);
            if (backupCreated && backup.exists()) {
                if (!destination.exists()) backup.renameTo(destination);
                else FileUtils.delete(backup);
            }
        }
    }

    public static boolean extract(Context context, String assetFile, File destination) {
        if (context == null || assetFile == null || destination == null) return false;
        File archive = null;
        try {
            archive = File.createTempFile("winmali-asset-", ".zip", context.getCacheDir());
            try (InputStream input = context.getAssets().open(assetFile);
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(archive))) {
                byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                long copied = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > MAX_EXTRACTED_BYTES) return false;
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
            return archive.isFile() && archive.length() > 0 && extract(archive, destination);
        }
        catch (IOException | RuntimeException error) {
            android.util.Log.w("WinMali-ZIP", "Asset ZIP extraction failed", error);
            return false;
        }
        finally {
            if (archive != null && archive.exists()) archive.delete();
        }
    }

    public static byte[] read(File source, String localPath) {
        if (source == null || !source.isFile() || localPath == null) return null;
        try {
            boolean pathIsPrefix = false;
            boolean pathIsSuffix = false;

            if (localPath.startsWith("*")) {
                pathIsSuffix = true;
            }
            else if (localPath.endsWith("*")) {
                pathIsPrefix = true;
            }

            localPath = localPath.replace("*", "");
            try (ZipFile zipFile = new ZipFile(source)) {
                Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    boolean match = pathIsSuffix ? entryName.endsWith(localPath) :
                        (pathIsPrefix ? entryName.startsWith(localPath) : entryName.equals(localPath));

                    if (match && !entry.isDirectory() && !entry.isUnixSymlink()) {
                        if (entry.getSize() > MAX_READ_BYTES) return null;
                        try (InputStream inStream = zipFile.getInputStream(entry);
                             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = inStream.read(buffer)) != -1) {
                                if (output.size() + count > MAX_READ_BYTES) return null;
                                output.write(buffer, 0, count);
                            }
                            return output.toByteArray();
                        }
                    }
                }
            }
            return null;
        }
        catch (Exception e) {
            return null;
        }
    }
}
