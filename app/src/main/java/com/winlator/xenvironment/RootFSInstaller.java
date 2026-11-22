package com.winlator.xenvironment;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.SettingsFragment;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.DownloadProgressDialog;
import com.winlator.core.FileUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

public abstract class RootFSInstaller {
    private static final ExecutorService OPERATIONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WinMali-rootfs-operations");
        thread.setDaemon(true);
        return thread;
    });

    public static final byte LATEST_VERSION = 23; // TODO increment it on rootfs update
    public static final byte UPDATE_WINEPREFIX_VERSION = 16; // set it if main wine version change
    public static final String FILENAME = "rootfs.tzst";

    private static void resetContainerRFSVersions(Context context) {
        ContainerManager manager = new ContainerManager(context);
        for (Container container : manager.getContainers()) {
            String rfsVersion = container.getExtra("rfsVersion");
            String wineVersion = container.getWineVersion();
            if (!rfsVersion.isEmpty() && WineInfo.isMainWineVersion(wineVersion)) {
                try {
                    if (Short.parseShort(rfsVersion) <= UPDATE_WINEPREFIX_VERSION) {
                        container.putExtra("wineprefixNeedsUpdate", "t");
                    }
                }
                catch (NumberFormatException ignored) {}
            }

            container.putExtra("rfsVersion", null);
            container.saveData();
        }
    }

    public static void install(final MainActivity activity) {
        AppUtils.keepScreenOn(activity);
        RootFS rootFS = RootFS.find(activity);
        final File rootDir = rootFS.getRootDir();
        final File parentDir = rootDir.getAbsoluteFile().getParentFile();

        final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
        dialog.show(R.string.installing_system_files);
        OPERATIONS.execute(() -> {
            File stagingDir = null;
            File backupDir = null;
            boolean success = false;
            try {
                if (parentDir == null || (!parentDir.isDirectory() && !parentDir.mkdirs())) {
                    throw new java.io.IOException("Unable to prepare rootfs parent directory");
                }

                String token = UUID.randomUUID().toString();
                stagingDir = new File(parentDir, rootDir.getName()+".install-"+token);
                backupDir = new File(parentDir, rootDir.getName()+".backup-"+token);
                if (!stagingDir.mkdirs()) throw new java.io.IOException("Unable to create rootfs staging directory");

                final long contentLength = TarCompressorUtils.getContentLength(
                    TarCompressorUtils.Type.ZSTD, activity, FILENAME, stagingDir);
                AtomicLong totalSizeRef = new AtomicLong();
                boolean extracted = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    activity, FILENAME, stagingDir, (file, size) -> {
                        if (size > 0) {
                            long totalSize = totalSizeRef.addAndGet(size);
                            final int progress = contentLength > 0 ?
                                Math.min(100, (int)(((double)totalSize / contentLength) * 100)) : 0;
                            activity.runOnUiThread(() -> dialog.setProgress(progress));
                        }
                        return file;
                    });

                if (!extracted || !RootFS.hasCoreWineFiles(stagingDir)) {
                    throw new java.io.IOException("Extracted rootfs is incomplete or missing Wine runtime files");
                }

                // Preserve user containers and separately installed Wine runtimes.
                File oldHome = new File(rootDir, "home");
                File newHome = new File(stagingDir, "home");
                if (oldHome.isDirectory() && !FileUtils.copy(oldHome, newHome)) {
                    throw new java.io.IOException("Unable to preserve container data");
                }

                File oldInstalledWine = new File(rootDir, "opt/installed-wine");
                File newOpt = new File(stagingDir, "opt");
                File newInstalledWine = new File(newOpt, "installed-wine");
                if (oldInstalledWine.isDirectory()) {
                    if (!newOpt.isDirectory() && !newOpt.mkdirs()) throw new java.io.IOException("Unable to prepare Wine runtime directory");
                    if (!FileUtils.copy(oldInstalledWine, newInstalledWine)) {
                        throw new java.io.IOException("Unable to preserve installed Wine runtimes");
                    }
                }

                File infoDir = new File(stagingDir, ".winlator");
                if (!infoDir.isDirectory() && !infoDir.mkdirs()) throw new java.io.IOException("Unable to write rootfs version");
                if (!FileUtils.writeString(new File(infoDir, ".rfs_version"), String.valueOf(LATEST_VERSION))) {
                    throw new java.io.IOException("Unable to write rootfs version");
                }

                boolean hadOriginal = rootDir.exists();
                if (hadOriginal && !rootDir.renameTo(backupDir)) {
                    throw new java.io.IOException("Unable to move previous rootfs");
                }
                if (!stagingDir.renameTo(rootDir)) {
                    if (hadOriginal) backupDir.renameTo(rootDir);
                    throw new java.io.IOException("Unable to activate the new rootfs");
                }

                if (!RootFS.find(activity).isValid() ||
                    RootFS.find(activity).getVersion() != LATEST_VERSION ||
                    !RootFS.hasCoreWineFiles(rootDir)) {
                    FileUtils.delete(rootDir);
                    if (hadOriginal) backupDir.renameTo(rootDir);
                    throw new java.io.IOException("Rootfs integrity check failed after installation");
                }

                success = true;
                if (backupDir.exists()) FileUtils.delete(backupDir);
                SettingsFragment.resetPreferenceVersions(activity);
                resetContainerRFSVersions(activity);
            }
            catch (Exception error) {
                android.util.Log.e("WinMali-RootFS", "System files installation failed", error);
                AppUtils.showToast(activity, R.string.unable_to_install_system_files);
            }
            finally {
                if (stagingDir != null && stagingDir.exists()) FileUtils.delete(stagingDir);
                if (!success && backupDir != null && backupDir.exists() && !rootDir.exists()) {
                    backupDir.renameTo(rootDir);
                }
                dialog.closeOnUiThread();
            }
        });
    }

    public static void installIfNeeded(final MainActivity activity) {
        RootFS rootFS = RootFS.find(activity);
        if (!rootFS.isValid() || rootFS.getVersion() < LATEST_VERSION) install(activity);
    }

    private static void clearOptDir(File optDir) {
        File[] files = optDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals("installed-wine")) continue;
                FileUtils.delete(file);
            }
        }
    }

    private static void clearRootDir(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String name = file.getName();
                        if (name.equals("home") || name.equals("opt")) {
                            if (name.equals("opt")) clearOptDir(file);
                            continue;
                        }
                    }
                    FileUtils.delete(file);
                }
            }
        }
        else rootDir.mkdirs();
    }

    public static void generateCompactContainerPattern(final AppCompatActivity activity) {
        AppUtils.keepScreenOn(activity);
        PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.loading);
        OPERATIONS.execute(() -> {
            File[] srcFiles, dstFiles;
            File rootDir = RootFS.find(activity).getRootDir();
            File wineSystem32Dir = new File(rootDir, "/opt/wine/lib/wine/x86_64-windows");
            File wineSysWoW64Dir = new File(rootDir, "/opt/wine/lib/wine/i386-windows");

            File containerPatternDir = new File(activity.getCacheDir(), "container_pattern");
            FileUtils.delete(containerPatternDir);
            boolean patternExtracted = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, activity, "container_pattern.tzst", containerPatternDir);
            if (!patternExtracted) {
                FileUtils.delete(containerPatternDir);
                AppUtils.showToast(activity, R.string.unable_to_install_system_files);
                preloaderDialog.closeOnUiThread();
                return;
            }

            File containerSystem32Dir = new File(containerPatternDir, ".wine/drive_c/windows/system32");
            File containerSysWoW64Dir = new File(containerPatternDir, ".wine/drive_c/windows/syswow64");

            dstFiles = containerSystem32Dir.listFiles();
            srcFiles = wineSystem32Dir.listFiles();
            if (dstFiles == null || srcFiles == null) {
                FileUtils.delete(containerPatternDir);
                AppUtils.showToast(activity, R.string.unable_to_install_system_files);
                preloaderDialog.closeOnUiThread();
                return;
            }

            ArrayList<String> system32Files = new ArrayList<>();
            ArrayList<String> syswow64Files = new ArrayList<>();

            for (File dstFile : dstFiles) {
                for (File srcFile : srcFiles) {
                    if (dstFile.getName().equals(srcFile.getName())) {
                        if (FileUtils.contentEquals(srcFile, dstFile)) system32Files.add(srcFile.getName());
                        break;
                    }
                }
            }

            dstFiles = containerSysWoW64Dir.listFiles();
            srcFiles = wineSysWoW64Dir.listFiles();
            if (dstFiles == null || srcFiles == null) {
                FileUtils.delete(containerPatternDir);
                AppUtils.showToast(activity, R.string.unable_to_install_system_files);
                preloaderDialog.closeOnUiThread();
                return;
            }

            for (File dstFile : dstFiles) {
                for (File srcFile : srcFiles) {
                    if (dstFile.getName().equals(srcFile.getName())) {
                        if (FileUtils.contentEquals(srcFile, dstFile)) syswow64Files.add(srcFile.getName());
                        break;
                    }
                }
            }

            try {
                JSONObject data = new JSONObject();

                JSONArray system32JSONArray = new JSONArray();
                for (String name : system32Files) {
                    FileUtils.delete(new File(containerSystem32Dir, name));
                    system32JSONArray.put(name);
                }
                data.put("system32", system32JSONArray);

                JSONArray syswow64JSONArray = new JSONArray();
                for (String name : syswow64Files) {
                    FileUtils.delete(new File(containerSysWoW64Dir, name));
                    syswow64JSONArray.put(name);
                }
                data.put("syswow64", syswow64JSONArray);

                FileUtils.writeString(new File(activity.getCacheDir(), "common_dlls.json"), data.toString());

                File outputFile = new File(activity.getCacheDir(), "container_pattern.tzst");
                FileUtils.delete(outputFile);
                boolean archiveCreated = TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD,
                    new File(containerPatternDir, ".wine"), outputFile, 22);
                if (!archiveCreated) {
                    AppUtils.showToast(activity, R.string.unable_to_install_system_files);
                }

                FileUtils.delete(containerPatternDir);
                preloaderDialog.closeOnUiThread();
            }
            catch (JSONException e) {}
        });
    }
}
