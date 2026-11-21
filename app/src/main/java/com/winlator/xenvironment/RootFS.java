package com.winlator.xenvironment;

import android.content.Context;

import androidx.annotation.NonNull;

import com.winlator.core.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class RootFS {
    public static final String USER = "xuser";
    public static final String HOME_PATH = "/home/"+USER;
    public static final String USER_CACHE_PATH = "/home/"+USER+"/.cache";
    public static final String USER_CONFIG_PATH = "/home/"+USER+"/.config";
    public static final String WINEPREFIX = "/home/"+USER+"/.wine";
    private final File rootDir;
    private String winePath = "/opt/wine";

    private RootFS(File rootDir) {
        this.rootDir = rootDir;
    }

    public static RootFS find(Context context) {
        File legacyDir = new File(context.getFilesDir(), "imagefs");
        File rootDir = new File(context.getFilesDir(), "rootfs");

        // Prefer the current location, but never lose access to a legacy rootfs
        // when migration cannot rename it (for example, filesystem or storage errors).
        if (!rootDir.exists() && legacyDir.isDirectory() && !legacyDir.renameTo(rootDir)) {
            return new RootFS(legacyDir);
        }
        if (rootDir.isDirectory()) return new RootFS(rootDir);
        if (legacyDir.isDirectory()) return new RootFS(legacyDir);
        return new RootFS(rootDir);
    }

    public File getRootDir() {
        return rootDir;
    }

    public boolean isValid() {
        return rootDir.isDirectory() && getRFSVersionFile().isFile() && getVersion() > 0;
    }

    public int getVersion() {
        File rfsVersionFile = getRFSVersionFile();
        if (!rfsVersionFile.isFile()) return 0;
        try {
            java.util.ArrayList<String> lines = FileUtils.readLines(rfsVersionFile, true);
            return lines.isEmpty() ? 0 : Math.max(0, Integer.parseInt(lines.get(0).trim()));
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getFormattedVersion() {
        return String.format(Locale.ENGLISH, "%.1f", (float)getVersion());
    }

    public boolean createRFSVersionFile(int version) {
        if (version <= 0) return false;
        File infoDir = getImageInfoDir();
        if (!infoDir.isDirectory() && !infoDir.mkdirs()) return false;
        File file = getRFSVersionFile();
        File temporary = new File(infoDir, ".rfs_version.tmp");
        try {
            if (!FileUtils.writeString(temporary, String.valueOf(version)) ||
                !temporary.isFile() || temporary.length() == 0) return false;
            try {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return file.isFile() && getVersion() == version;
        }
        catch (IOException | RuntimeException error) {
            android.util.Log.e("WinMali-RootFS", "Unable to write rootfs version marker", error);
            return false;
        }
        finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    public String getWinePath() {
        return winePath;
    }

    public void setWinePath(String winePath) {
        this.winePath = FileUtils.toRelativePath(rootDir.getPath(), winePath);
    }

    private File getImageInfoDir() {
        return new File(rootDir, ".winlator");
    }

    public File getRFSVersionFile() {
        return new File(getImageInfoDir(), ".rfs_version");
    }

    public File getInstalledWineDir() {
        return new File(rootDir, "opt/installed-wine");
    }

    public File getTmpDir() {
        return new File(rootDir, "tmp");
    }

    public File getLibDir() {
        return new File(rootDir, "usr/lib");
    }

    @NonNull
    @Override
    public String toString() {
        return rootDir.getPath();
    }

    public static String getDosUserCachePath() {
        return "Z:"+USER_CACHE_PATH.replace("/", "\\");
    }

    public static String getDosUserConfigPath() {
        return "Z:"+USER_CONFIG_PATH.replace("/", "\\");
    }
}
