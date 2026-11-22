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

    private static void addFile(ZipOutputStream zip, File file, String entryName) {
        try {
            ZipEntry entry = new ZipEntry(entryName);
            entry.setTime(file.lastModified());
            zip.putNextEntry(entry);

            try (BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(file), StreamUtils.BUFFER_SIZE)) {
                StreamUtils.copy(inStream, zip);
            }
        }
        catch (Exception e) {}
    }

    private static void addDirectory(ZipOutputStream zip, File folder, String basePath) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (FileUtils.isSymlink(file)) continue;
            if (file.isDirectory()) {
                String entryName = basePath+file.getName() + "/";
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(file.lastModified());
                zip.putNextEntry(entry);
                addDirectory(zip, file, entryName);
            }
            else addFile(zip, file, basePath+file.getName());
        }
    }

    public static void compress(File file, File destination) {
        compress(new File[]{file}, destination);
    }

    public static void compress(File[] files, File destination) {
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE))) {
                for (File file : files) {
                    if (FileUtils.isSymlink(file)) continue;
                    if (file.isDirectory()) {
                        String basePath = file.getName() + "/";
                        zip.putNextEntry(new ZipEntry(basePath));
                        addDirectory(zip, file, basePath);
                    }
                    else addFile(zip, file, file.getName());
                }
            }
        }
        catch (IOException e) {}
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
        try (ZipFile zipFile = new ZipFile(source)) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isUnixSymlink()) return false;

                String entryName = normalizeEntryName(entry.getName(), entry.isDirectory());
                if (entryName == null) return false;
                if (entryName.isEmpty()) continue;

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
        if (!staging.mkdirs()) return false;

        boolean success = false;
        try {
            if (!extractToDirectory(source, staging)) return false;
            if (destination.exists() && !FileUtils.delete(destination)) return false;
            try {
                Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(staging.toPath(), destination.toPath());
            }
            success = destination.isDirectory();
            return success;
        }
        catch (IOException | RuntimeException e) {
            return false;
        }
        finally {
            if (staging.exists()) FileUtils.delete(staging);
            if (!success && destination.exists() && destination.isDirectory()) {
                // A failed activation must not leave a half-written directory.
                FileUtils.delete(destination);
            }
        }
    }

    public static void extract(Context context, String assetFile, File destination) {
        try (ZipInputStream zip = new ZipInputStream(context.getAssets().open(assetFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File file = new File(destination, entry.getName());

                if (entry.isDirectory()) {
                    if (!file.isDirectory()) file.mkdirs();
                }
                else {
                    try (BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file), StreamUtils.BUFFER_SIZE)) {
                        StreamUtils.copy(zip, outStream);
                        zip.closeEntry();
                    }
                }

                FileUtils.chmod(file, 0771);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
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
