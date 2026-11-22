package com.winlator.xenvironment;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.SettingsFragment;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.DownloadProgressDialog;
import com.winlator.core.FileUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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
    private static final Object INSTALL_LOCK = new Object();
    private static final ArrayList<Callback<Boolean>> INSTALL_CALLBACKS = new ArrayList<>();
    private static boolean installationRunning = false;

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

    public static boolean isReady(Context context) {
        if (context == null) return false;
        RootFS rootFS = RootFS.find(context);
        return rootFS.isValid() && rootFS.getVersion() >= LATEST_VERSION;
    }

    public static boolean isInstalling() {
        synchronized (INSTALL_LOCK) {
            return installationRunning;
        }
    }

    public static void install(final MainActivity activity) {
        // Explicit Settings action: force a clean reinstall even when the current
        // rootfs passes the readiness check.
        ensureInstalled(activity, null, true);
    }

    public static void installIfNeeded(final MainActivity activity) {
        ensureInstalled(activity, null, false);
    }

    public static void ensureInstalled(final Context context, final Callback<Boolean> callback) {
        ensureInstalled(context, callback, false);
    }

    private static void ensureInstalled(final Context context, final Callback<Boolean> callback,
            boolean forceReinstall) {
        if (context == null) {
            if (callback != null) callback.call(false);
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;

        boolean readyImmediately = false;
        synchronized (INSTALL_LOCK) {
            // Never report the old rootfs as ready while a forced reinstall is
            // replacing it. Wait for the active operation and share its result.
            if (installationRunning) {
                if (callback != null) INSTALL_CALLBACKS.add(callback);
                return;
            }

            if (!forceReinstall && isReady(appContext)) {
                readyImmediately = true;
            }
            else {
                if (callback != null) INSTALL_CALLBACKS.add(callback);
                installationRunning = true;
            }
        }

        if (readyImmediately) {
            if (callback != null) new Handler(context.getMainLooper()).post(() -> callback.call(true));
            return;
        }

        final Activity activity = context instanceof Activity ? (Activity)context : null;
        final DownloadProgressDialog dialog = activity != null ? new DownloadProgressDialog(activity) : null;
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && dialog != null) {
                    AppUtils.keepScreenOn(activity);
                    dialog.show(R.string.installing_system_files);
                }
            });
        }

        final Context installationContext = appContext;
        OPERATIONS.execute(() -> {
            boolean success = installNow(installationContext, activity, dialog);
            ArrayList<Callback<Boolean>> callbacks;
            synchronized (INSTALL_LOCK) {
                callbacks = new ArrayList<>(INSTALL_CALLBACKS);
                INSTALL_CALLBACKS.clear();
                installationRunning = false;
            }

            if (!success && activity != null && !activity.isFinishing()) {
                AppUtils.showToast(activity, R.string.unable_to_install_system_files);
            }

            Handler handler = new Handler(installationContext.getMainLooper());
            for (Callback<Boolean> pending : callbacks) {
                handler.post(() -> {
                    try {
                        pending.call(success);
                    }
                    catch (RuntimeException error) {
                        android.util.Log.e("WinMali-RootFS", "Installer completion callback failed", error);
                    }
                });
            }
        });
    }

    private static boolean hasValidRootFSAsset(Context context) {
        try (InputStream input = context.getAssets().open(FILENAME)) {
            byte[] magic = new byte[4];
            int read = 0;
            while (read < magic.length) {
                int count = input.read(magic, read, magic.length - read);
                if (count < 0) return false;
                read += count;
            }
            return (magic[0] & 0xff) == 0x28 && (magic[1] & 0xff) == 0xb5 &&
                (magic[2] & 0xff) == 0x2f && (magic[3] & 0xff) == 0xfd;
        }
        catch (Exception error) {
            android.util.Log.e("WinMali-RootFS", "RootFS archive is missing or unreadable", error);
            return false;
        }
    }

    private static boolean installNow(Context context, Activity activity, DownloadProgressDialog dialog) {
        RootFS rootFS = RootFS.find(context);
        final File rootDir = rootFS.getRootDir();
        final File parentDir = rootDir.getAbsoluteFile().getParentFile();

        File stagingDir = null;
        File backupDir = null;
        boolean success = false;
        try {
            if (!hasValidRootFSAsset(context)) {
                throw new java.io.IOException("Bundled rootfs.tzst is missing or is not a Zstandard archive");
            }
            if (parentDir == null || (!parentDir.isDirectory() && !parentDir.mkdirs())) {
                throw new java.io.IOException("Unable to prepare rootfs parent directory");
            }

            String token = UUID.randomUUID().toString();
            stagingDir = new File(parentDir, rootDir.getName()+".install-"+token);
            backupDir = new File(parentDir, rootDir.getName()+".backup-"+token);
            if (!stagingDir.mkdirs()) throw new java.io.IOException("Unable to create rootfs staging directory");

            final long contentLength = TarCompressorUtils.getContentLength(
                TarCompressorUtils.Type.ZSTD, context, FILENAME, stagingDir);
            AtomicLong totalSizeRef = new AtomicLong();
            boolean extracted = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                context, FILENAME, stagingDir, (file, size) -> {
                    if (size > 0) {
                        long totalSize = totalSizeRef.addAndGet(size);
                        final int progress = contentLength > 0 ?
                            Math.min(100, (int)(((double)totalSize / contentLength) * 100)) : 0;
                        if (activity != null && dialog != null && !activity.isFinishing()) {
                            activity.runOnUiThread(() -> dialog.setProgress(progress));
                        }
                    }
                    return file;
                });

            if (!extracted || !RootFS.hasCoreWineFiles(stagingDir)) {
                throw new java.io.IOException("Extracted rootfs is incomplete or missing Wine runtime files");
            }

            // Never follow a symlink when preserving the user's existing home tree.
            File oldHome = new File(rootDir, "home");
            File newHome = new File(stagingDir, "home");
            if (oldHome.isDirectory() && !FileUtils.isSymlink(oldHome) && !FileUtils.copy(oldHome, newHome)) {
                throw new java.io.IOException("Unable to preserve container data");
            }

            File oldInstalledWine = new File(rootDir, "opt/installed-wine");
            File newOpt = new File(stagingDir, "opt");
            File newInstalledWine = new File(newOpt, "installed-wine");
            if (oldInstalledWine.isDirectory() && !FileUtils.isSymlink(oldInstalledWine)) {
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

            RootFS installedRoot = RootFS.find(context);
            if (!installedRoot.isValid() || installedRoot.getVersion() != LATEST_VERSION ||
                !RootFS.hasCoreWineFiles(rootDir)) {
                FileUtils.delete(rootDir);
                if (hadOriginal) backupDir.renameTo(rootDir);
                throw new java.io.IOException("Rootfs integrity check failed after installation");
            }

            success = true;
            if (backupDir.exists()) FileUtils.delete(backupDir);
            if (activity instanceof AppCompatActivity) {
                SettingsFragment.resetPreferenceVersions((AppCompatActivity)activity);
            }
            resetContainerRFSVersions(context);
            return true;
        }
        catch (Exception error) {
            android.util.Log.e("WinMali-RootFS", "System files installation failed", error);
            return false;
        }
        finally {
            if (stagingDir != null && stagingDir.exists()) FileUtils.delete(stagingDir);
            if (!success && backupDir != null && backupDir.exists() && !rootDir.exists()) {
                backupDir.renameTo(rootDir);
            }
            if (dialog != null) dialog.closeOnUiThread();
        }
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
            File containerPatternDir = new File(activity.getCacheDir(), "container_pattern");
            try {
                File rootDir = RootFS.find(activity).getRootDir();
                File wineSystem32Dir = new File(rootDir, "opt/wine/lib/wine/x86_64-windows");
                File wineSysWoW64Dir = new File(rootDir, "opt/wine/lib/wine/i386-windows");

                FileUtils.delete(containerPatternDir);
                if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, activity,
                    "container_pattern.tzst", containerPatternDir)) {
                    throw new java.io.IOException("Unable to extract container pattern asset");
                }

                File containerSystem32Dir = new File(containerPatternDir, ".wine/drive_c/windows/system32");
                File containerSysWoW64Dir = new File(containerPatternDir, ".wine/drive_c/windows/syswow64");
                File[] dstSystem32 = containerSystem32Dir.listFiles();
                File[] srcSystem32 = wineSystem32Dir.listFiles();
                File[] dstSysWoW64 = containerSysWoW64Dir.listFiles();
                File[] srcSysWoW64 = wineSysWoW64Dir.listFiles();
                if (dstSystem32 == null || srcSystem32 == null || dstSysWoW64 == null || srcSysWoW64 == null) {
                    throw new java.io.IOException("Required Wine DLL directories are missing");
                }

                Set<String> system32Files = new HashSet<>();
                Set<String> syswow64Files = new HashSet<>();
                for (File dstFile : dstSystem32) {
                    for (File srcFile : srcSystem32) {
                        if (dstFile.getName().equals(srcFile.getName()) &&
                            FileUtils.contentEquals(srcFile, dstFile)) {
                            system32Files.add(srcFile.getName());
                            break;
                        }
                    }
                }
                for (File dstFile : dstSysWoW64) {
                    for (File srcFile : srcSysWoW64) {
                        if (dstFile.getName().equals(srcFile.getName()) &&
                            FileUtils.contentEquals(srcFile, dstFile)) {
                            syswow64Files.add(srcFile.getName());
                            break;
                        }
                    }
                }

                JSONObject data = new JSONObject();
                JSONArray system32JSONArray = new JSONArray();
                for (String name : system32Files) {
                    if (!FileUtils.delete(new File(containerSystem32Dir, name))) {
                        throw new java.io.IOException("Unable to remove shared system32 DLL: "+name);
                    }
                    system32JSONArray.put(name);
                }
                data.put("system32", system32JSONArray);

                JSONArray syswow64JSONArray = new JSONArray();
                for (String name : syswow64Files) {
                    if (!FileUtils.delete(new File(containerSysWoW64Dir, name))) {
                        throw new java.io.IOException("Unable to remove shared syswow64 DLL: "+name);
                    }
                    syswow64JSONArray.put(name);
                }
                data.put("syswow64", syswow64JSONArray);

                if (!FileUtils.writeString(new File(activity.getCacheDir(), "common_dlls.json"), data.toString())) {
                    throw new java.io.IOException("Unable to persist shared DLL metadata");
                }

                File outputFile = new File(activity.getCacheDir(), "container_pattern.tzst");
                FileUtils.delete(outputFile);
                if (!TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD,
                    new File(containerPatternDir, ".wine"), outputFile, 22) ||
                    !outputFile.isFile() || outputFile.length() == 0) {
                    throw new java.io.IOException("Unable to create compact container pattern");
                }
            }
            catch (Exception error) {
                android.util.Log.e("WinMali-RootFS", "Compact container pattern generation failed", error);
                AppUtils.showToast(activity, R.string.unable_to_install_system_files);
            }
            finally {
                FileUtils.delete(containerPatternDir);
                preloaderDialog.closeOnUiThread();
            }
        });
    }
}
