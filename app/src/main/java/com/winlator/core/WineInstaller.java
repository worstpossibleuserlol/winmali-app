package com.winlator.core;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class WineInstaller {
    private static final ExecutorService OPERATIONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WinMali-wine-installer");
        thread.setDaemon(true);
        return thread;
    });

    static boolean restoreHomeLink(File linkFile, boolean hadOriginalSymlink, String originalTarget) {
        boolean restored;
        if (hadOriginalSymlink) {
            restored = originalTarget != null && !originalTarget.isEmpty() &&
                FileUtils.symlink(originalTarget, linkFile.getPath());
        }
        else {
            restored = !linkFile.exists() && !FileUtils.isSymlink(linkFile) || FileUtils.delete(linkFile);
        }

        if (!restored) {
            android.util.Log.e("WinMali-Wine", "Unable to restore rootfs home link: "+linkFile);
            // Never leave HOME pointing into a staging directory that will be deleted.
            if (FileUtils.isSymlink(linkFile)) FileUtils.delete(linkFile);
        }
        return restored;
    }

    private static void clearWinePreinstall(File installedWineDir) {
        File preinstall = new File(installedWineDir, "preinstall");
        if ((preinstall.exists() || FileUtils.isSymlink(preinstall)) && !FileUtils.delete(preinstall)) {
            android.util.Log.e("WinMali-Wine", "Unable to remove Wine staging directory: "+preinstall);
        }
    }

    public static void generateWineprefix(WineInfo wineInfo, XEnvironment environment) {
        if (wineInfo == null || wineInfo.path == null || environment == null ||
            environment.getContext() == null || environment.getRootFS() == null) return;
        if (!(environment.getContext() instanceof Activity)) return;

        Activity activity = (Activity)environment.getContext();
        RootFS rootFS = environment.getRootFS();
        final File rootDir = rootFS.getRootDir();
        final File installedWineDir = rootFS.getInstalledWineDir();
        if (rootDir == null || installedWineDir == null) return;
        rootFS.setWinePath(wineInfo.path);

        final File containerPatternDir = new File(installedWineDir, "/preinstall/container-pattern");
        if (FileUtils.isSymlink(containerPatternDir) || containerPatternDir.exists()) {
            if (!FileUtils.delete(containerPatternDir)) {
                AppUtils.showToast(activity, R.string.unable_to_install_wine);
                return;
            }
        }
        if (!containerPatternDir.mkdirs() || !containerPatternDir.isDirectory()) {
            AppUtils.showToast(activity, R.string.unable_to_install_wine);
            return;
        }

        GuestProgramLauncherComponent guestProgramLauncherComponent = environment.getComponent(GuestProgramLauncherComponent.class);
        if (guestProgramLauncherComponent == null) {
            FileUtils.delete(new File(installedWineDir, "/preinstall"));
            AppUtils.showToast(activity, R.string.unable_to_install_wine);
            return;
        }

        File linkFile = new File(rootDir, RootFS.HOME_PATH);
        final boolean hadOriginalHomeSymlink = FileUtils.isSymlink(linkFile);
        final String originalHomeTarget = hadOriginalHomeSymlink ? FileUtils.readSymlink(linkFile) : null;
        if (!hadOriginalHomeSymlink && linkFile.exists()) {
            clearWinePreinstall(installedWineDir);
            AppUtils.showToast(activity, R.string.unable_to_install_wine);
            return;
        }
        if (hadOriginalHomeSymlink && (originalHomeTarget == null || originalHomeTarget.isEmpty())) {
            clearWinePreinstall(installedWineDir);
            AppUtils.showToast(activity, R.string.unable_to_install_wine);
            return;
        }
        if (!FileUtils.symlink(containerPatternDir.getPath(), linkFile.getPath())) {
            clearWinePreinstall(installedWineDir);
            AppUtils.showToast(activity, R.string.unable_to_install_wine);
            return;
        }
        guestProgramLauncherComponent.setBox64Preset(Box64Preset.STABILITY);
        guestProgramLauncherComponent.setGuestExecutable("wine explorer /desktop=shell,"+ Container.DEFAULT_SCREEN_SIZE+" C:\\windows\\system32\\winecfg.exe");

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        guestProgramLauncherComponent.setTerminationCallback((status) -> OPERATIONS.execute(() -> {
            if (status != 0) {
                restoreHomeLink(linkFile, hadOriginalHomeSymlink, originalHomeTarget);
                clearWinePreinstall(installedWineDir);
                AppUtils.showToast(activity, R.string.unable_to_install_wine);
                AppUtils.restartApplication(activity);
                return;
            }

            preloaderDialog.showOnUiThread(R.string.finishing_installation);
            if (!FileUtils.writeString(new File(rootDir, RootFS.WINEPREFIX+"/.update-timestamp"), "disable\n")) {
                restoreHomeLink(linkFile, hadOriginalHomeSymlink, originalHomeTarget);
                clearWinePreinstall(installedWineDir);
                AppUtils.showToast(activity, R.string.unable_to_install_wine);
                preloaderDialog.closeOnUiThread();
                AppUtils.restartApplication(activity);
                return;
            }

            File userDir = new File(rootDir, RootFS.WINEPREFIX+"/drive_c/users/xuser");
            File[] userFiles = userDir.listFiles();
            if (userFiles != null) {
                for (File userFile : userFiles) {
                    if (FileUtils.isSymlink(userFile)) {
                        String path = userFile.getPath();
                        if (!FileUtils.delete(userFile) || !(new File(path)).mkdirs()) {
                            restoreHomeLink(linkFile, hadOriginalHomeSymlink, originalHomeTarget);
                            clearWinePreinstall(installedWineDir);
                            AppUtils.showToast(activity, R.string.unable_to_install_wine);
                            preloaderDialog.closeOnUiThread();
                            AppUtils.restartApplication(activity);
                            return;
                        }
                    }
                }
            }

            File containerPatternFile = new File(installedWineDir, "/preinstall/container-pattern-"+wineInfo.fullVersion()+".tzst");
            File installedPatternFile = new File(installedWineDir, containerPatternFile.getName());
            File installedWinePath = new File(installedWineDir, wineInfo.identifier());
            boolean archiveCreated = TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD,
                new File(rootDir, RootFS.WINEPREFIX), containerPatternFile, MainActivity.CONTAINER_PATTERN_COMPRESSION_LEVEL);
            boolean installationSucceeded = archiveCreated && !installedPatternFile.exists() &&
                !installedWinePath.exists() && containerPatternFile.renameTo(installedPatternFile) &&
                new File(wineInfo.path).renameTo(installedWinePath);

            if (!installationSucceeded) {
                restoreHomeLink(linkFile, hadOriginalHomeSymlink, originalHomeTarget);
                FileUtils.delete(installedPatternFile);
                FileUtils.delete(containerPatternFile);
                clearWinePreinstall(installedWineDir);
                AppUtils.showToast(activity, R.string.unable_to_install_wine);
                preloaderDialog.closeOnUiThread();
                AppUtils.restartApplication(activity);
                return;
            }

            // Restore the active container link before deleting the temporary prefix.
            restoreHomeLink(linkFile, hadOriginalHomeSymlink, originalHomeTarget);
            clearWinePreinstall(installedWineDir);

            preloaderDialog.closeOnUiThread();
            AppUtils.RestartApplicationOptions options = new AppUtils.RestartApplicationOptions();
            options.selectedMenuItemId = R.id.menu_item_settings;
            AppUtils.restartApplication(activity, options);
        }));
    }

    public static void extractWineFileForInstallAsync(Context context, Uri uri, Callback<File> callback) {
        OPERATIONS.execute(() -> {
            File destination = new File(RootFS.find(context).getInstalledWineDir(), "/preinstall/wine");
            if ((FileUtils.isSymlink(destination) || destination.exists()) && !FileUtils.delete(destination)) {
                if (callback != null) callback.call(null);
                return;
            }
            boolean prepared = destination.mkdirs() && destination.isDirectory();
            TarCompressorUtils.Type archiveType = prepared ? TarCompressorUtils.detectType(context, uri) : null;
            boolean success = archiveType != null && TarCompressorUtils.extract(archiveType, context, uri, destination);
            if (!success) FileUtils.delete(destination);
            if (callback != null) callback.call(success ? destination : null);
        });
    }

    private static File resolveWineRuntimeRoot(File extractedDir) {
        return findWineRuntimeRoot(extractedDir, 3);
    }

    private static File findWineRuntimeRoot(File directory, int remainingDepth) {
        if (directory == null || !directory.isDirectory()) return null;
        if (hasWineRuntimeBinaries(directory)) return directory;
        if (remainingDepth <= 0) return null;

        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (!child.isDirectory() || FileUtils.isSymlink(child)) continue;
            File result = findWineRuntimeRoot(child, remainingDepth - 1);
            if (result != null) return result;
        }
        return null;
    }

    static File findWineExecutable(File root) {
        if (root == null) return null;
        File bin = new File(root, "bin");
        File wine64 = new File(bin, "wine64");
        if (CompatibilityRuntimePackage.isX86_64Elf(wine64)) return wine64;

        File wine = new File(bin, "wine");
        return CompatibilityRuntimePackage.isX86_64Elf(wine) ? wine : null;
    }

    private static boolean hasWineRuntimeBinaries(File root) {
        return findWineExecutable(root) != null;
    }

    public static void findWineVersionAsync(Context context, File wineDir, Callback<WineInfo> callback) {
        if (callback == null) return;
        if (context == null || wineDir == null || !wineDir.isDirectory()) {
            callback.call(null);
            return;
        }
        wineDir = resolveWineRuntimeRoot(wineDir);
        if (wineDir == null) {
            callback.call(null);
            return;
        }

        File wineExecutable = findWineExecutable(wineDir);
        if (wineExecutable == null) {
            callback.call(null);
            return;
        }

        RootFS rootFS = RootFS.find(context);
        File rootDir = rootFS.getRootDir();
        String wineBinPath = wineExecutable.getPath();
        final String winePath = wineDir.getPath();

        final AtomicReference<WineInfo> wineInfoRef = new AtomicReference<>();
        Callback<String> outputCallback = (line) -> {
            Pattern pattern = Pattern.compile("^wine\\-([0-9\\.]+)\\-?([0-9\\.]+)?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String version = matcher.group(1);
                String subversion = matcher.group(2);
                wineInfoRef.set(new WineInfo(version, subversion, winePath));
            }
        };

        File linkFile = new File(rootDir, RootFS.HOME_PATH);
        boolean hadOriginalSymlink = FileUtils.isSymlink(linkFile);
        String originalTarget = hadOriginalSymlink ? FileUtils.readSymlink(linkFile) : null;
        if (!hadOriginalSymlink && (linkFile.exists() || !linkFile.getParentFile().isDirectory())) {
            callback.call(null);
            return;
        }
        if (!FileUtils.symlink(wineDir, linkFile)) {
            callback.call(null);
            return;
        }

        XEnvironment environment = new XEnvironment(context, rootFS);
        GuestProgramLauncherComponent launcher = new GuestProgramLauncherComponent();
        launcher.setOutputCallback(outputCallback);
        launcher.setGuestExecutable(wineBinPath+" --version");
        launcher.setTerminationCallback((status) -> {
            if (hadOriginalSymlink && originalTarget != null && !originalTarget.isEmpty()) {
                FileUtils.symlink(originalTarget, linkFile.getPath());
            }
            else FileUtils.delete(linkFile);
            callback.call(status == 0 ? wineInfoRef.get() : null);
        });
        environment.addComponent(launcher);
        environment.startEnvironmentComponents();
    }

    public static ArrayList<WineInfo> getInstalledWineInfos(Context context) {
        ArrayList<WineInfo> wineInfos = new ArrayList<>();
        wineInfos.add(WineInfo.MAIN_WINE_INFO);
        File installedWineDir = RootFS.find(context).getInstalledWineDir();

        File[] files = installedWineDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.startsWith("wine-") || !file.isDirectory() || FileUtils.isSymlink(file)) continue;
                if (findWineExecutable(file) == null) continue;

                WineInfo wineInfo = WineInfo.fromIdentifier(context, name);
                if (wineInfo != WineInfo.MAIN_WINE_INFO) wineInfos.add(wineInfo);
            }
        }

        return wineInfos;
    }
}
