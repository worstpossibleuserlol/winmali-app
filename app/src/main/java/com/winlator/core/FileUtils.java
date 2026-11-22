package com.winlator.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Stack;
import java.util.UUID;

public abstract class FileUtils {
    public static byte[] read(Context context, String assetFile) {
        if (context == null || assetFile == null) return null;
        try (InputStream inStream = context.getAssets().open(assetFile)) {
            return StreamUtils.copyToByteArray(inStream);
        }
        catch (IOException e) {
            return null;
        }
    }

    public static byte[] read(File file) {
        if (file == null || !file.isFile()) return null;
        try (InputStream inStream = new BufferedInputStream(new FileInputStream(file))) {
            return StreamUtils.copyToByteArray(inStream);
        }
        catch (IOException e) {
            return null;
        }
    }

    public static String readString(Context context, String assetFile) {
        byte[] data = read(context, assetFile);
        return data != null ? new String(data, StandardCharsets.UTF_8) : "";
    }

    public static String readString(File file) {
        byte[] data = read(file);
        return data != null ? new String(data, StandardCharsets.UTF_8) : "";
    }

    public static String readString(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            // Preserve final newlines and CRLF rather than reconstructing text line-by-line.
            return new String(StreamUtils.copyToByteArray(input), StandardCharsets.UTF_8);
        }
        catch (IOException | SecurityException error) {
            return null;
        }
    }

    public static boolean write(File file, byte[] data) {
        if (file == null || data == null || isSymlink(file)) return false;
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.flush();
            return true;
        }
        catch (IOException | RuntimeException error) {
            android.util.Log.w("WinMali-FileUtils", "Unable to write file: "+file, error);
            return false;
        }
    }

    public static boolean writeString(File file, String data) {
        if (file == null || data == null || isSymlink(file)) return false;
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (BufferedWriter writer = new BufferedWriter(
                new java.io.OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(data);
            writer.flush();
            return true;
        }
        catch (IOException | RuntimeException error) {
            android.util.Log.w("WinMali-FileUtils", "Unable to write text file: "+file, error);
            return false;
        }
    }

    public static boolean symlink(File linkTarget, File linkFile) {
        if (linkTarget == null || linkFile == null) return false;
        return symlink(linkTarget.getAbsolutePath(), linkFile.getAbsolutePath());
    }

    public static boolean symlink(String linkTarget, String linkFile) {
        if (linkTarget == null || linkFile == null || linkTarget.isEmpty() || linkFile.isEmpty()) return false;
        File destination = new File(linkFile).getAbsoluteFile();
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;

        File temporary = new File(parent, destination.getName()+".link-"+UUID.randomUUID());
        try {
            Files.createSymbolicLink(temporary.toPath(), Paths.get(linkTarget));
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return isSymlink(destination);
        }
        catch (IOException | RuntimeException error) {
            // Logging must never turn an expected filesystem failure into another failure.
            try {
                android.util.Log.w("WinMali-FileUtils", "Unable to atomically replace symlink: "+destination, error);
            }
            catch (RuntimeException ignored) {}
            return false;
        }
        finally {
            if (isSymlink(temporary)) temporary.delete();
        }
    }

    public static boolean isSymlink(File file) {
        return file != null && Files.isSymbolicLink(file.toPath());
    }

    public static boolean delete(File targetFile) {
        if (targetFile == null) return false;
        if (targetFile.isDirectory()) {
            if (!isSymlink(targetFile)) if (!clear(targetFile)) return false;
        }
        return targetFile.delete();
    }

    public static boolean clear(File targetFile) {
        if (targetFile == null || !targetFile.isDirectory() || isSymlink(targetFile)) return false;
        File[] files = targetFile.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (!delete(file)) return false;
        }
        return true;
    }

    public static boolean isEmpty(File targetFile) {
        if (targetFile == null || (!targetFile.exists() && !isSymlink(targetFile))) return true;
        if (isSymlink(targetFile)) return false;
        if (targetFile.isDirectory()) {
            String[] files = targetFile.list();
            return files != null && files.length == 0;
        }
        return targetFile.isFile() && targetFile.length() == 0;
    }

    public static boolean isAscendantOf(File srcFile, File dstFile) {
        if (srcFile == null || dstFile == null) return false;
        try {
            File source = srcFile.getCanonicalFile();
            File parent = dstFile.getCanonicalFile().getParentFile();
            while (parent != null) {
                if (parent.equals(source)) return true;
                parent = parent.getParentFile();
            }
            return false;
        }
        catch (IOException error) {
            // Fail closed: a failed canonical-path check must not permit recursive self-copy.
            return true;
        }
    }

    private static boolean copyStreamAtomically(InputStream source, File destination) {
        if (source == null || destination == null) return false;
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;

        File temporary = null;
        boolean moved = false;
        try {
            temporary = File.createTempFile(".winmali-copy-", ".tmp", parent);
            long copied = 0;
            try (BufferedOutputStream output = new BufferedOutputStream(
                    new FileOutputStream(temporary), StreamUtils.BUFFER_SIZE)) {
                byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                int count;
                while ((count = source.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    copied += count;
                }
                output.flush();
            }

            if (!temporary.isFile() || temporary.length() != copied) return false;
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            moved = destination.isFile() && destination.length() == copied;
            return moved;
        }
        catch (IOException | RuntimeException error) {
            android.util.Log.w("WinMali-FileUtils", "Atomic file copy failed: "+destination, error);
            return false;
        }
        finally {
            if (temporary != null && temporary.exists()) temporary.delete();
        }
    }

    public static boolean copy(File srcFile, File dstFile) {
        return copy(srcFile, dstFile, null);
    }

    public static boolean copy(File srcFile, File dstFile, Callback<File> callback) {
        if (srcFile == null || dstFile == null) return false;
        try {
            // Copying a directory into itself must be a no-op, not a recursive walk.
            if (srcFile.getCanonicalFile().equals(dstFile.getCanonicalFile())) return true;
        }
        catch (IOException error) {
            return false;
        }
        if (isSymlink(srcFile)) {
            String linkTarget = readSymlink(srcFile);
            if (linkTarget == null || linkTarget.isEmpty()) return false;
            File parent = dstFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            return symlink(linkTarget, dstFile.getPath());
        }

        try {
            // FileOutputStream truncates a file before a self-copy can read it.
            if (srcFile.getCanonicalFile().equals(dstFile.getCanonicalFile())) return srcFile.exists();
        }
        catch (IOException error) {
            return false;
        }

        if (srcFile.isDirectory()) {
            // Remove a destination directory symlink itself, never recurse into its target.
            if (isSymlink(dstFile) && !delete(dstFile)) return false;
            if (isAscendantOf(srcFile, dstFile) || (!dstFile.isDirectory() && !dstFile.mkdirs())) return false;
            if (callback != null) callback.call(dstFile);

            String[] filenames = srcFile.list();
            if (filenames == null) return false;
            for (String filename : filenames) {
                if (!copy(new File(srcFile, filename), new File(dstFile, filename), callback)) {
                    return false;
                }
            }
        }
        else {
            if (!srcFile.isFile()) return false;

            try (InputStream inStream = new BufferedInputStream(new FileInputStream(srcFile), StreamUtils.BUFFER_SIZE)) {
                if (!copyStreamAtomically(inStream, dstFile) || dstFile.length() != srcFile.length()) return false;
                if (callback != null) callback.call(dstFile);
                return true;
            }
            catch (IOException | RuntimeException error) {
                return false;
            }
        }
        return true;
    }

    public static boolean copy(Context context, String assetFile, File dstFile) {
        if (context == null || assetFile == null || dstFile == null || isSymlink(dstFile)) return false;
        if (isDirectory(context, assetFile)) {
            if (!dstFile.isDirectory() && !dstFile.mkdirs()) return false;
            try {
                String[] filenames = context.getAssets().list(assetFile);
                if (filenames == null) return false;
                for (String filename : filenames) {
                    String relativePath = StringUtils.addEndSlash(assetFile)+filename;
                    if (!copy(context, relativePath, new File(dstFile, filename))) return false;
                }
                return true;
            }
            catch (IOException e) {
                return false;
            }
        }

        if (dstFile.isDirectory()) dstFile = new File(dstFile, FileUtils.getName(assetFile));
        if (isSymlink(dstFile)) return false;
        try (InputStream inStream = context.getAssets().open(assetFile)) {
            return copyStreamAtomically(inStream, dstFile);
        }
        catch (IOException | RuntimeException error) {
            return false;
        }
    }

    public static ArrayList<String> readLines(File file) {
        return readLines(file, false);
    }

    public static ArrayList<String> readLines(File file, boolean skipEmptyLines) {
        ArrayList<String> lines = new ArrayList<>();
        if (file == null || !file.isFile()) return lines;
        try (FileInputStream fis = new FileInputStream(file)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (skipEmptyLines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                }
                lines.add(line);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    public static String getName(String path) {
        if (path == null) return "";
        path = StringUtils.removeEndSlash(path);
        int index = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(index + 1);
    }

    public static String getBasename(String path) {
        String name = getName(path);
        int extensionDot = name.lastIndexOf('.');
        return extensionDot <= 0 ? name : name.substring(0, extensionDot);
    }

    public static String getDirname(String path) {
        if (path == null) return "";
        path = StringUtils.removeEndSlash(path);
        int index = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (index < 0) return "";
        if (index == 0) return path.substring(0, 1);
        return path.substring(0, index);
    }

    public static void chmod(File file, int mode) {
        try {
            Os.chmod(file.getAbsolutePath(), mode);
        }
        catch (ErrnoException | RuntimeException e) {}
    }

    public static File createTempFile(File parent, String prefix) {
        File tempFile = null;
        boolean exists = true;
        while (exists) {
            tempFile = new File(parent, prefix+"-"+ UUID.randomUUID().toString().replace("-", "")+".tmp");
            exists = tempFile.exists();
        }
        return tempFile;
    }

    public static String getFilePathFromUri(Uri uri) {
        if (uri == null || !"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        String segment = uri.getLastPathSegment();
        if (segment == null) return null;

        String[] parts = segment.split(":", 2);
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("primary")) return null;

        File storageRoot = Environment.getExternalStorageDirectory();
        File resolved = new File(storageRoot, parts[1]);
        try {
            String rootPath = storageRoot.getCanonicalPath();
            String resolvedPath = resolved.getCanonicalPath();
            if (!resolvedPath.equals(rootPath) && !resolvedPath.startsWith(rootPath + File.separator)) return null;
            return resolvedPath;
        }
        catch (IOException e) {
            return null;
        }
    }

    public static boolean contentEquals(File origin, File target) {
        if (origin == null || target == null) return false;
        boolean originLink = isSymlink(origin);
        boolean targetLink = isSymlink(target);
        if (originLink || targetLink) {
            if (!originLink || !targetLink) return false;
            String originTarget = readSymlink(origin);
            String targetTarget = readSymlink(target);
            return originTarget != null && originTarget.equals(targetTarget);
        }
        if (origin.isDirectory() && target.isDirectory()) {
            File[] originFiles = origin.listFiles();
            File[] targetFiles = target.listFiles();
            if (originFiles == null || targetFiles == null || originFiles.length != targetFiles.length) return false;

            for (File originChild : originFiles) {
                File targetChild = new File(target, originChild.getName());
                if (!targetChild.exists() && !isSymlink(targetChild)) return false;
                if (!contentEquals(originChild, targetChild)) return false;
            }
            return true;
        }
        else if (origin.isFile() && target.isFile()) {
            if (origin.length() != target.length()) return false;

            try (InputStream inStream1 = new BufferedInputStream(new FileInputStream(origin), StreamUtils.BUFFER_SIZE);
                 InputStream inStream2 = new BufferedInputStream(new FileInputStream(target), StreamUtils.BUFFER_SIZE)) {
                byte[] buffer1 = new byte[StreamUtils.BUFFER_SIZE];
                byte[] buffer2 = new byte[StreamUtils.BUFFER_SIZE];
                while (true) {
                    int count1 = inStream1.read(buffer1);
                    int count2 = inStream2.read(buffer2);
                    if (count1 != count2) return false;
                    if (count1 < 0) return true;
                    for (int i = 0; i < count1; i++) {
                        if (buffer1[i] != buffer2[i]) return false;
                    }
                }
            }
            catch (IOException e) {
                return false;
            }
        }
        else return false;
    }

    public static void getSizeAsync(File file, Callback<Long> callback) {
        if (file == null || callback == null) return;
        Thread worker = new Thread(() -> getSize(file, callback), "WinMali-file-size");
        worker.setDaemon(true);
        worker.start();
    }

    private static void getSize(File file, Callback<Long> callback) {
        if (file == null || callback == null || isSymlink(file)) return;
        if (file.isFile()) {
            callback.call(file.length());
            return;
        }
        if (!file.isDirectory()) return;

        Stack<File> stack = new Stack<>();
        stack.push(file);

        while (!stack.isEmpty()) {
            File current = stack.pop();
            File[] files = current.listFiles();
            if (files == null) continue;
            for (File child : files) {
                // Do not follow symlinked directories: they can point to an ancestor
                // and make the scan loop forever or count files outside this tree.
                if (isSymlink(child)) continue;
                if (child.isDirectory()) {
                    stack.push(child);
                }
                else if (child.isFile()) {
                    long length = child.length();
                    if (length > 0) callback.call(length);
                }
            }
        }
    }

    public static long getSize(Context context, String assetFile) {
        if (context == null || assetFile == null) return 0;
        try (InputStream inStream = context.getAssets().open(assetFile)) {
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            long size = 0;
            int count;
            while ((count = inStream.read(buffer)) != -1) size += count;
            return size;
        }
        catch (IOException e) {
            return 0;
        }
    }

    public static long getInternalStorageSize() {
        File dataDir = Environment.getDataDirectory();
        StatFs stat = new StatFs(dataDir.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return totalBlocks * blockSize;
    }

    public static boolean isDirectory(Context context, String assetFile) {
        if (context == null || assetFile == null || assetFile.isEmpty()) return false;
        try {
            String[] files = context.getAssets().list(assetFile);
            if (files == null) return false;
            if (files.length > 0) return true;

            int separator = assetFile.lastIndexOf('/');
            String parent = separator >= 0 ? assetFile.substring(0, separator) : "";
            String name = separator >= 0 ? assetFile.substring(separator + 1) : assetFile;
            String[] siblings = context.getAssets().list(parent);
            if (siblings == null) return false;
            for (String sibling : siblings) if (name.equals(sibling)) {
                try (InputStream ignored = context.getAssets().open(assetFile)) {
                    return false;
                }
                catch (IOException notAFile) {
                    return true;
                }
            }
            return false;
        }
        catch (IOException e) {
            return false;
        }
    }

    public static String toRelativePath(String basePath, String fullPath) {
        return StringUtils.removeEndSlash((fullPath.startsWith("/") ? "/" : "")+(new File(basePath).toURI().relativize(new File(fullPath).toURI()).getPath()));
    }

    public static int readInt(String path) {
        if (path == null || path.isEmpty()) return 0;
        try (RandomAccessFile reader = new RandomAccessFile(path, "r")) {
            String line = reader.readLine();
            return line != null && !line.trim().isEmpty() ? Integer.parseInt(line.trim()) : 0;
        }
        catch (IOException | NumberFormatException | SecurityException e) {
            return 0;
        }
    }

    public static String readSymlink(File file) {
        if (file == null || !isSymlink(file)) return "";
        try {
            return Files.readSymbolicLink(file.toPath()).toString();
        }
        catch (IOException | SecurityException error) {
            return "";
        }
    }

    public static String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex <= 0 || dotIndex == filename.length()-1 ? "" : filename.substring(dotIndex + 1);
    }

    public static void openIntent(Activity activity, String path) {
        Intent intent;
        if (path.startsWith("file://")) {
            File file = new File(Uri.decode(path.replace("file://", "")));
            intent = new Intent(Intent.ACTION_VIEW, FileProvider.getUriForFile(activity, "com.winlator.FileProvider", file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        else intent = new Intent(Intent.ACTION_VIEW, Uri.parse(path));
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }
}
