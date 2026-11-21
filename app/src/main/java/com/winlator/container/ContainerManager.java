package com.winlator.container;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.system.ErrnoException;
import android.system.Os;

import com.winlator.R;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;
import com.winlator.xenvironment.RootFSInstaller;
import com.winlator.xenvironment.RootFS;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

public class ContainerManager {
    private static final Set<Integer> ACTIVE_CONTAINER_IDS = Collections.synchronizedSet(new HashSet<>());

    private static final ExecutorService OPERATIONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WinMali-container-operations");
        thread.setDaemon(true);
        return thread;
    });

    private final ArrayList<Container> containers = new ArrayList<>();
    private int maxContainerId = 0;
    private final File homeDir;
    private final Context context;
    private volatile int lastCreateError = R.string.container_create_failed;

    public int getLastCreateError() {
        return lastCreateError;
    }

    public ContainerManager(Context context) {
        this.context = context;
        File rootDir = RootFS.find(context).getRootDir();
        homeDir = new File(rootDir, "home");
        // Container creation uses home/xuser-N. Ensure its parent exists even on
        // freshly installed or partially migrated root filesystems.
        if (!homeDir.isDirectory()) homeDir.mkdirs();
        loadContainers();
    }

    public Context getContext() {
        return context;
    }

    public synchronized ArrayList<Container> getContainers() {
        return new ArrayList<>(containers);
    }

    private synchronized void loadContainers() {
        containers.clear();
        maxContainerId = 0;

        File[] files = homeDir.listFiles();
        if (files == null) return;

        String prefix = RootFS.USER+"-";
        for (File file : files) {
            if (!file.isDirectory() || !file.getName().startsWith(prefix)) continue;
            try {
                int id = Integer.parseInt(file.getName().substring(prefix.length()));
                if (id <= 0) continue;

                Container container = new Container(id);
                container.setRootDir(file);
                File configFile = container.getConfigFile();
                if (!configFile.isFile()) continue;

                JSONObject data = new JSONObject(FileUtils.readString(configFile));
                container.loadData(data);
                containers.add(container);
                maxContainerId = Math.max(maxContainerId, id);
            }
            catch (Exception ignored) {
                // Ignore a damaged container directory without hiding valid containers.
            }
        }
    }

    private int nextAvailableContainerId() {
        int id = maxContainerId + 1;
        while (new File(homeDir, RootFS.USER+"-"+id).exists()) id++;
        return id;
    }

    public synchronized boolean activateContainer(Container container) {
        if (container == null || getContainerById(container.id) == null) return false;

        File activeLink = new File(homeDir, RootFS.USER);
        File temporaryLink = new File(homeDir, RootFS.USER+".new");
        FileUtils.delete(temporaryLink);
        if (!FileUtils.symlink(RootFS.USER+"-"+container.id, temporaryLink.getPath())) return false;

        try {
            // Rename replaces the previous symlink atomically; no delete-before-create gap.
            Os.rename(temporaryLink.getPath(), activeLink.getPath());
            container.setRootDir(new File(homeDir, RootFS.USER+"-"+container.id));
            ACTIVE_CONTAINER_IDS.add(container.id);
            return true;
        }
        catch (ErrnoException e) {
            FileUtils.delete(temporaryLink);
            return false;
        }
    }

    public void createContainerAsync(final JSONObject data, Callback<Container> callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            Container created = null;
            try {
                created = createContainer(data);
            }
            catch (Exception error) {
                lastCreateError = R.string.container_create_unexpected_error;
                android.util.Log.e("WinMali-Containers", "Container creation failed", error);
            }
            final Container result = created;
            if (callback != null) handler.post(() -> callback.call(result));
        });
    }

    public void duplicateContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            try {
                duplicateContainer(container);
            }
            catch (RuntimeException ignored) {}
            if (callback != null) handler.post(callback);
        });
    }

    public void removeContainerAsync(Container container, Callback<Boolean> callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            boolean removed = false;
            try {
                removed = removeContainer(container);
            }
            catch (RuntimeException ignored) {}
            final boolean result = removed;
            if (callback != null) handler.post(() -> callback.call(result));
        });
    }

    public void exportContainerAsync(Container container, Uri destinationUri, Callback<Boolean> callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            boolean success = false;
            File archive = null;
            try {
                if (container != null && container.getRootDir() != null &&
                    container.getRootDir().isDirectory() && destinationUri != null &&
                    !isActiveContainer(container)) {
                    archive = new File(context.getCacheDir(), "winmali-export-"+UUID.randomUUID()+".tar.zst");
                    success = TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD,
                        container.getRootDir(), archive, 3);
                    if (success) {
                        try (InputStream input = new BufferedInputStream(new FileInputStream(archive), 64 * 1024);
                             OutputStream output = context.getContentResolver().openOutputStream(destinationUri, "w")) {
                            success = output != null && com.winlator.core.StreamUtils.copy(input, output);
                        }
                    }
                }
            }
            catch (Exception ignored) {
                success = false;
            }
            finally {
                if (archive != null) FileUtils.delete(archive);
            }
            final boolean result = success;
            if (callback != null) handler.post(() -> callback.call(result));
        });
    }

    public void importContainerAsync(Uri sourceUri, Callback<Container> callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            Container imported = null;
            File staging = null;
            try {
                TarCompressorUtils.Type type = TarCompressorUtils.detectType(context, sourceUri);
                if (type != null) {
                    staging = new File(context.getCacheDir(), "winmali-import-"+UUID.randomUUID());
                    if (staging.mkdirs() && TarCompressorUtils.extract(type, context, sourceUri, staging)) {
                        File backupRoot = findBackupRoot(staging, 2);
                        if (backupRoot != null) imported = installImportedContainer(backupRoot);
                    }
                }
            }
            catch (Exception ignored) {
                imported = null;
            }
            finally {
                if (staging != null) FileUtils.delete(staging);
            }
            final Container result = imported;
            if (callback != null) handler.post(() -> callback.call(result));
        });
    }

    private File findBackupRoot(File directory, int depth) {
        if (directory == null || !directory.isDirectory()) return null;
        File config = new File(directory, ".container");
        File wine = new File(directory, ".wine");
        File windows = new File(wine, "drive_c/windows");
        if (config.isFile() && !FileUtils.isSymlink(config) &&
            wine.isDirectory() && !FileUtils.isSymlink(wine) && windows.isDirectory()) return directory;
        if (depth <= 0) return null;
        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory() && !FileUtils.isSymlink(child)) {
                File found = findBackupRoot(child, depth - 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private synchronized Container installImportedContainer(File backupRoot) {
        if (backupRoot == null || !backupRoot.isDirectory()) return null;
        try {
            JSONObject data = new JSONObject(FileUtils.readString(new File(backupRoot, ".container")));
            Container container = new Container(nextAvailableContainerId());
            container.loadData(data);

            if (!WineInfo.isMainWineVersion(container.getWineVersion())) {
                WineInfo info = WineInfo.fromIdentifier(context, container.getWineVersion());
                File installedWineDir = RootFS.find(context).getInstalledWineDir();
                File runtime = new File(installedWineDir, container.getWineVersion());
                File pattern = new File(installedWineDir, "container-pattern-"+info.fullVersion()+".tzst");
                if (info.path == null || !info.identifier().equals(container.getWineVersion()) ||
                    !runtime.isDirectory() || !pattern.isFile()) return null;
            }

            File destination = new File(homeDir, RootFS.USER+"-"+container.id);
            if (destination.exists() || !destination.mkdirs()) return null;
            if (!FileUtils.copy(backupRoot, destination, (file) -> FileUtils.chmod(file, 0771))) {
                FileUtils.delete(destination);
                return null;
            }

            container.setRootDir(destination);
            container.saveData();
            containers.add(container);
            maxContainerId = Math.max(maxContainerId, container.id);
            return container;
        }
        catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    private synchronized Container createContainer(JSONObject data) {
        File stagingDir = null;
        lastCreateError = R.string.container_create_failed;
        try {
            if (data == null) {
                lastCreateError = R.string.container_create_invalid_settings;
                return null;
            }

            RootFS rootFS = RootFS.find(context);
            if (!rootFS.isValid() || rootFS.getVersion() < RootFSInstaller.LATEST_VERSION) {
                lastCreateError = R.string.container_create_system_files_missing;
                return null;
            }

            JSONObject containerData = new JSONObject(data.toString());
            int id = nextAvailableContainerId();
            containerData.put("id", id);

            if (!homeDir.isDirectory() && !homeDir.mkdirs()) {
                lastCreateError = R.string.container_create_storage_error;
                return null;
            }
            if (!homeDir.isDirectory() || !homeDir.canWrite()) {
                lastCreateError = R.string.container_create_storage_error;
                return null;
            }

            boolean mainWine = !containerData.has("wineVersion") ||
                WineInfo.isMainWineVersion(containerData.optString("wineVersion", null));
            String wineVersion = mainWine ? WineInfo.MAIN_WINE_INFO.identifier() :
                containerData.optString("wineVersion", "");
            if (mainWine) {
                if (!validateMainWineFiles(rootFS)) return null;
            }
            else if (!validateCustomWineFiles(rootFS, wineVersion)) return null;

            File destination = new File(homeDir, RootFS.USER+"-"+id);
            if (destination.exists()) {
                lastCreateError = R.string.container_create_destination_exists;
                return null;
            }

            stagingDir = new File(homeDir, ".winmali-create-"+UUID.randomUUID());
            if (!stagingDir.mkdirs()) {
                lastCreateError = R.string.container_create_storage_error;
                return null;
            }

            Container container = new Container(id);
            container.setRootDir(stagingDir);
            container.loadData(containerData);
            if (!mainWine) container.setWineVersion(wineVersion);

            if (!extractContainerPatternFile(container.getWineVersion(), stagingDir)) {
                if (lastCreateError == R.string.container_create_failed) {
                    lastCreateError = R.string.container_create_pattern_failed;
                }
                return null;
            }

            File winePrefix = new File(stagingDir, ".wine");
            File windowsDir = new File(winePrefix, "drive_c/windows");
            if (!winePrefix.isDirectory() || !windowsDir.isDirectory()) {
                lastCreateError = R.string.container_create_invalid_pattern;
                return null;
            }

            if (!container.saveData()) {
                lastCreateError = R.string.container_create_config_failed;
                return null;
            }

            if (!stagingDir.renameTo(destination)) {
                lastCreateError = R.string.container_create_storage_error;
                return null;
            }

            container.setRootDir(destination);
            containers.add(container);
            maxContainerId = Math.max(maxContainerId, id);
            return container;
        }
        catch (JSONException error) {
            lastCreateError = R.string.container_create_invalid_settings;
            android.util.Log.e("WinMali-Containers", "Invalid container settings", error);
            return null;
        }
        catch (IOException | RuntimeException error) {
            lastCreateError = R.string.container_create_unexpected_error;
            android.util.Log.e("WinMali-Containers", "Container creation error", error);
            return null;
        }
        finally {
            if (stagingDir != null && stagingDir.exists()) FileUtils.delete(stagingDir);
        }
    }

    private boolean validateMainWineFiles(RootFS rootFS) {
        File root = rootFS.getRootDir();
        File wineDir = new File(root, "opt/wine");
        File binDir = new File(wineDir, "bin");
        if (!new File(binDir, "wine").isFile() && !new File(binDir, "wine64").isFile()) {
            lastCreateError = R.string.container_create_wine_missing;
            return false;
        }

        File x64 = new File(wineDir, "lib/wine/x86_64-windows");
        File x86 = new File(wineDir, "lib/wine/i386-windows");
        if (!x64.isDirectory() || !x86.isDirectory()) {
            lastCreateError = R.string.container_create_wine_libraries_missing;
            return false;
        }

        try (InputStream pattern = context.getAssets().open("container_pattern.tzst");
             InputStream manifest = context.getAssets().open("common_dlls.json")) {
            if (pattern.read() < 0) {
                lastCreateError = R.string.container_create_pattern_missing;
                return false;
            }
            JSONObject commonDlls = new JSONObject(readAssetText(manifest));
            if (!hasAllDlls(x64, commonDlls.getJSONArray("system32")) ||
                !hasAllDlls(x86, commonDlls.getJSONArray("syswow64"))) {
                lastCreateError = R.string.container_create_wine_libraries_missing;
                return false;
            }
            return true;
        }
        catch (IOException | JSONException error) {
            lastCreateError = R.string.container_create_system_assets_missing;
            android.util.Log.e("WinMali-Containers", "Missing or invalid container assets", error);
            return false;
        }
    }

    private String readAssetText(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString("UTF-8");
    }

    private boolean hasAllDlls(File sourceDir, JSONArray names) throws JSONException {
        if (names.length() == 0) return false;
        for (int i = 0; i < names.length(); i++) {
            String name = names.getString(i);
            if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf((char)92) >= 0 ||
                !new File(sourceDir, name).isFile()) return false;
        }
        return true;
    }

    private boolean validateCustomWineFiles(RootFS rootFS, String wineVersion) {
        if (wineVersion == null || !wineVersion.startsWith("wine-") ||
            wineVersion.contains("/") || wineVersion.indexOf((char)92) >= 0 || wineVersion.contains("..")) {
            lastCreateError = R.string.container_create_custom_runtime_missing;
            return false;
        }
        WineInfo info = WineInfo.fromIdentifier(context, wineVersion);
        File runtime = new File(rootFS.getInstalledWineDir(), wineVersion);
        File pattern = new File(rootFS.getInstalledWineDir(), "container-pattern-"+info.fullVersion()+".tzst");
        File bin = new File(runtime, "bin");
        if (info.path == null || !runtime.isDirectory() ||
            (!new File(bin, "wine").isFile() && !new File(bin, "wine64").isFile()) ||
            !pattern.isFile() || pattern.length() == 0) {
            lastCreateError = R.string.container_create_custom_runtime_missing;
            return false;
        }
        return true;
    }

    private synchronized void duplicateContainer(Container srcContainer) {
        if (srcContainer == null || srcContainer.getRootDir() == null || !srcContainer.getRootDir().isDirectory()) return;
        int id = nextAvailableContainerId();

        File dstDir = new File(homeDir, RootFS.USER+"-"+id);
        if (!dstDir.mkdirs()) return;

        if (!FileUtils.copy(srcContainer.getRootDir(), dstDir, (file) -> FileUtils.chmod(file, 0771))) {
            FileUtils.delete(dstDir);
            return;
        }

        Container dstContainer = new Container(id);
        dstContainer.setRootDir(dstDir);
        dstContainer.setName(srcContainer.getName()+" ("+context.getString(R.string.copy)+")");
        dstContainer.setScreenSize(srcContainer.getScreenSize());
        dstContainer.setEnvVars(srcContainer.getEnvVars());
        dstContainer.setCPUList(srcContainer.getCPUList());
        dstContainer.setCPUListWoW64(srcContainer.getCPUListWoW64());
        dstContainer.setGraphicsDriver(srcContainer.getGraphicsDriver());
        dstContainer.setGraphicsDriverConfig(srcContainer.getGraphicsDriverConfig());
        dstContainer.setDXWrapper(srcContainer.getDXWrapper());
        dstContainer.setDXWrapperConfig(srcContainer.getDXWrapperConfig());
        dstContainer.setAudioDriver(srcContainer.getAudioDriver());
        dstContainer.setAudioDriverConfig(srcContainer.getAudioDriverConfig());
        dstContainer.setWinComponents(srcContainer.getWinComponents());
        dstContainer.setDrives(srcContainer.getDrives());
        dstContainer.setHUDMode(srcContainer.getHUDMode());
        dstContainer.setStartupSelection(srcContainer.getStartupSelection());
        dstContainer.setBox64Preset(srcContainer.getBox64Preset());
        dstContainer.setDesktopTheme(srcContainer.getDesktopTheme());
        dstContainer.saveData();

        maxContainerId = Math.max(maxContainerId, id);
        containers.add(dstContainer);
    }

    private synchronized boolean removeContainer(Container container) {
        if (container == null || container.getRootDir() == null ||
            FileUtils.isSymlink(container.getRootDir()) || isActiveContainer(container)) return false;

        if (FileUtils.delete(container.getRootDir())) {
            containers.remove(container);
            return true;
        }
        return false;
    }

    public synchronized void deactivateContainer(Container container) {
        if (container == null) return;
        ACTIVE_CONTAINER_IDS.remove(container.id);

        File activeLink = new File(homeDir, RootFS.USER);
        if (!FileUtils.isSymlink(activeLink)) return;
        String linkTarget = FileUtils.readSymlink(activeLink);
        if (linkTarget.isEmpty()) return;
        File target = new File(linkTarget);
        if (!target.isAbsolute()) target = new File(homeDir, linkTarget);

        try {
            if (container.getRootDir() != null &&
                target.getCanonicalFile().equals(container.getRootDir().getCanonicalFile())) {
                FileUtils.delete(activeLink);
            }
        }
        catch (IOException ignored) {}
    }

    private boolean isActiveContainer(Container container) {
        return container != null && ACTIVE_CONTAINER_IDS.contains(container.id);
    }

    public ArrayList<Shortcut> loadShortcuts(Shortcut selectedFolder) {
        ArrayList<Shortcut> shortcuts = new ArrayList<>();

        if (selectedFolder != null) {
            File[] files = selectedFolder.file.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".desktop") || file.isDirectory()) {
                        shortcuts.add(new Shortcut(selectedFolder.container, file));
                    }
                }
            }
        }
        else {
            ArrayList<Container> containerSnapshot;
            synchronized (this) {
                containerSnapshot = new ArrayList<>(containers);
            }
            for (Container container : containerSnapshot) {
                File desktopDir = new File(container.getUserDir(), "Desktop");
                File[] files = desktopDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".desktop") || file.isDirectory()) {
                            shortcuts.add(new Shortcut(container, file));
                        }
                    }
                }
            }
        }

        shortcuts.sort((a, b) -> {
            int value = Boolean.compare(b.file.isDirectory(), a.file.isDirectory());
            if (value == 0) value = a.name.compareTo(b.name);
            return value;
        });
        return shortcuts;
    }

    public ArrayList<FileInfo> loadFiles(Container container, FileInfo parent) {
        ArrayList<FileInfo> fileInfos = new ArrayList<>();

        if (parent != null) {
            fileInfos = parent.list();
        }
        else {
            String rootPath = container.getRootDir().getPath();
            fileInfos.add(new FileInfo(container, "C:", rootPath+"/.wine/drive_c", FileInfo.Type.DRIVE));
            for (Drive drive : container.drivesIterator()) {
                fileInfos.add(new FileInfo(container, drive.letter+":", drive.path, FileInfo.Type.DRIVE));
            }

            File userDir = container.getUserDir();
            File documentsDir = new File(userDir, "Documents");
            File favoritesDir = new File(userDir, "Favorites");

            fileInfos.add(new FileInfo(container, documentsDir.getName(), documentsDir.getPath(), FileInfo.Type.DIRECTORY));
            fileInfos.add(new FileInfo(container, favoritesDir.getName(), favoritesDir.getPath(), FileInfo.Type.DIRECTORY));

            Collections.sort(fileInfos);
        }
        return fileInfos;
    }

    public synchronized int getNextContainerId() {
        return nextAvailableContainerId();
    }

    public synchronized Container getContainerById(int id) {
        for (Container container : containers) if (container.id == id) return container;
        return null;
    }

    private boolean copyCommonDlls(String srcName, String dstName, JSONObject commonDlls, File containerDir) throws JSONException {
        File srcDir = new File(RootFS.find(context).getRootDir(), "opt/wine/lib/wine/"+srcName);
        if (!srcDir.isDirectory()) return false;

        JSONArray dlnames = commonDlls.getJSONArray(dstName);
        for (int i = 0; i < dlnames.length(); i++) {
            String dlname = dlnames.getString(i);
            if (dlname.isEmpty() || dlname.indexOf('/') >= 0 || dlname.indexOf((char)92) >= 0) return false;
            File srcFile = new File(srcDir, dlname);
            File dstFile = new File(containerDir, ".wine/drive_c/windows/"+dstName+"/"+dlname);
            if (!srcFile.isFile() || !FileUtils.copy(srcFile, dstFile)) return false;
        }
        return true;
    }

    private boolean extractContainerPatternFile(String wineVersion, File containerDir) {
        if (WineInfo.isMainWineVersion(wineVersion)) {
            boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "container_pattern.tzst", containerDir);
            if (!result) {
                lastCreateError = R.string.container_create_pattern_failed;
                return false;
            }

            try {
                JSONObject commonDlls = new JSONObject(FileUtils.readString(context, "common_dlls.json"));
                if (!copyCommonDlls("x86_64-windows", "system32", commonDlls, containerDir) ||
                    !copyCommonDlls("i386-windows", "syswow64", commonDlls, containerDir)) {
                    lastCreateError = R.string.container_create_wine_libraries_missing;
                    return false;
                }
            }
            catch (JSONException error) {
                lastCreateError = R.string.container_create_system_assets_missing;
                return false;
            }
            return true;
        }

        File installedWineDir = RootFS.find(context).getInstalledWineDir();
        WineInfo wineInfo = WineInfo.fromIdentifier(context, wineVersion);
        File file = new File(installedWineDir, "container-pattern-"+wineInfo.fullVersion()+".tzst");
        boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, file, containerDir);
        if (!result) lastCreateError = R.string.container_create_custom_runtime_missing;
        return result;
    }
}
