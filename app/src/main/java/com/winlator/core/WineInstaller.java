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

    public static void generateWineprefix(WineInfo wineInfo, XEnvironment environment) {
        Activity activity = (Activity)environment.getContext();
        RootFS rootFS = environment.getRootFS();
        final File rootDir = rootFS.getRootDir();
        final File installedWineDir = rootFS.getInstalledWineDir();
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
        if (!FileUtils.symlink(containerPatternDir.getPath(), linkFile.getPath())) {
            FileUtils.delete(new File(installedWineDir, "/preinstall"));
            AppUtils.showToast(activity, R.string.unable_to_install_wine);
            return;
        }
        guestProgramLauncherComponent.setBox64Preset(Box64Preset.STABILITY);
        guestProgramLauncherComponent.setGuestExecutable("wine explorer /desktop=shell,"+ Container.DEFAULT_SCREEN_SIZE+" C:\\windows\\system32\\winecfg.exe");

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        guestProgramLauncherComponent.setTerminationCallback((status) -> OPERATIONS.execute(() -> {
            if (status != 0) {
                AppUtils.showToast(activity, R.string.unable_to_install_wine);
                FileUtils.delete(new File(installedWineDir, "/preinstall"));
                AppUtils.restartApplication(activity);
                return;
            }

            preloaderDialog.showOnUiThread(R.string.finishing_installation);
            if (!FileUtils.writeString(new File(rootDir, RootFS.WINEPREFIX+"/.update-timestamp"), "disable\n")) {
                AppUtils.showToast(activity, R.string.unable_to_install_wine);
                FileUtils.delete(new File(installedWineDir, "/preinstall"));
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
                            AppUtils.showToast(activity, R.string.unable_to_install_wine);
                            FileUtils.delete(new File(installedWineDir, "/preinstall"));
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
                FileUtils.delete(installedPatternFile);
                FileUtils.delete(containerPatternFile);
                FileUtils.delete(new File(installedWineDir, "/preinstall"));
                AppUtils.showToast(activity, R.string.unable_to_install_wine);
                preloaderDialog.closeOnUiThread();
                AppUtils.restartApplication(activity);
                return;
            }

            FileUtils.delete(new File(installedWineDir, "/preinstall"));

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
        if (CompatibilityRuntimePackage.isAarch64Elf(wine64)) return wine64;

        File wine = new File(bin, "wine");
        return CompatibilityRuntimePackage.isAarch64Elf(wine) ? wine : null;
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
