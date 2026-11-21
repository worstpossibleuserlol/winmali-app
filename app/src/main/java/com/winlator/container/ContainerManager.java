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
import com.winlator.xenvironment.RootFS;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContainerManager {
    private static final ExecutorService OPERATIONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WinMali-container-operations");
        thread.setDaemon(true);
        return thread;
    });

    private final ArrayList<Container> containers = new ArrayList<>();
    private int maxContainerId = 0;
    private final File homeDir;
    private final Context context;

    public ContainerManager(Context context) {
        this.context = context;
        File rootDir = RootFS.find(context).getRootDir();
        homeDir = new File(rootDir, "home");
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
            catch (RuntimeException ignored) {}
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

    public void exportContainerAsync(Container container, Uri destination, Callback<Boolean> callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            boolean exported = false;
            try {
                exported = exportContainer(container, destination);
            }
            catch (RuntimeException ignored) {}
            final boolean result = exported;
            if (callback != null) handler.post(() -> callback.call(result));
        });
    }

    private boolean exportContainer(Container container, Uri destination) {
        if (container == null || destination == null || container.getRootDir() == null ||
            !container.getRootDir().isDirectory() || isActiveContainer(container)) return false;

        File archive = null;
        try {
            archive = File.createTempFile("winmali-container-", ".tzst", context.getCacheDir());
            if (!TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD, container.getRootDir(), archive)) return false;

            try (InputStream input = new FileInputStream(archive);
                 OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) return false;
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
                return true;
            }
        }
        catch (IOException | SecurityException e) {
            return false;
        }
        finally {
            if (archive != null && archive.exists()) archive.delete();
        }
    }

    public void importContainerAsync(Uri source, Callback<Container> callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            Container imported = null;
            try {
                imported = importContainer(source);
            }
            catch (RuntimeException ignored) {}
            final Container result = imported;
            if (callback != null) handler.post(() -> callback.call(result));
        });
    }

    private synchronized Container importContainer(Uri source) {
        if (source == null) return null;
        File staging = null;
        File destination = null;
        try {
            TarCompressorUtils.Type type = TarCompressorUtils.detectType(context, source);
            if (type == null) return null;

            staging = File.createTempFile("winmali-import-", "", context.getCacheDir());
            if (!staging.delete() || !staging.mkdirs()) return null;
            if (!TarCompressorUtils.extract(type, context, source, staging)) return null;

            File candidate = null;
            File[] entries = staging.listFiles();
            if (entries == null) return null;
            for (File entry : entries) {
                if (entry.isDirectory() && !FileUtils.isSymlink(entry) &&
                    new File(entry, ".container").isFile()) {
                    if (candidate != null) return null;
                    candidate = entry;
                }
            }
            if (candidate == null && new File(staging, ".container").isFile()) candidate = staging;
            if (candidate == null) return null;

            JSONObject data = new JSONObject(FileUtils.readString(new File(candidate, ".container")));
            if (!data.has("name") || data.optString("name").trim().isEmpty()) return null;

            int id = nextAvailableContainerId();
            destination = new File(homeDir, RootFS.USER + "-" + id);
            if (!destination.mkdirs()) return null;
            if (!FileUtils.copy(candidate, destination, (file) -> FileUtils.chmod(file, 0771))) {
                FileUtils.delete(destination);
                return null;
            }

            data.put("id", id);
            Container container = new Container(id);
            container.setRootDir(destination);
            container.loadData(data);
            container.saveData();
            if (!container.getConfigFile().isFile()) {
                FileUtils.delete(destination);
                return null;
            }

            maxContainerId = Math.max(maxContainerId, id);
            containers.add(container);
            destination = null;
            return container;
        }
        catch (IOException | JSONException | SecurityException e) {
            return null;
        }
        finally {
            if (destination != null && destination.exists()) FileUtils.delete(destination);
            if (staging != null && staging.exists()) FileUtils.delete(staging);
        }
    }

    private synchronized Container createContainer(JSONObject data) {
        try {
            if (data == null) return null;
            JSONObject containerData = new JSONObject(data.toString());
            int id = nextAvailableContainerId();
            containerData.put("id", id);

            File containerDir = new File(homeDir, RootFS.USER+"-"+id);
            if (!containerDir.mkdirs()) return null;

            Container container = new Container(id);
            container.setRootDir(containerDir);
            container.loadData(containerData);

            boolean isMainWineVersion = !containerData.has("wineVersion") || WineInfo.isMainWineVersion(containerData.getString("wineVersion"));
            if (!isMainWineVersion) container.setWineVersion(containerData.getString("wineVersion"));

            if (!extractContainerPatternFile(container.getWineVersion(), containerDir)) {
                FileUtils.delete(containerDir);
                return null;
            }

            container.saveData();
            maxContainerId = Math.max(maxContainerId, id);
            containers.add(container);
            return container;
        }
        catch (JSONException e) {}
        return null;
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

    private boolean isActiveContainer(Container container) {
        File activeLink = new File(homeDir, RootFS.USER);
        if (!FileUtils.isSymlink(activeLink)) return false;

        String linkTarget = FileUtils.readSymlink(activeLink);
        if (linkTarget.isEmpty()) return false;
        File target = new File(linkTarget);
        if (!target.isAbsolute()) target = new File(homeDir, linkTarget);

        try {
            return target.getCanonicalFile().equals(container.getRootDir().getCanonicalFile());
        }
        catch (IOException e) {
            return false;
        }
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
            File srcFile = new File(srcDir, dlname);
            File dstFile = new File(containerDir, ".wine/drive_c/windows/"+dstName+"/"+dlname);
            if (!FileUtils.copy(srcFile, dstFile)) return false;
        }
        return true;
    }

    private boolean extractContainerPatternFile(String wineVersion, File containerDir) {
        if (WineInfo.isMainWineVersion(wineVersion)) {
            boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "container_pattern.tzst", containerDir);

            if (result) {
                try {
                    JSONObject commonDlls = new JSONObject(FileUtils.readString(context, "common_dlls.json"));
                    if (!copyCommonDlls("x86_64-windows", "system32", commonDlls, containerDir) ||
                        !copyCommonDlls("i386-windows", "syswow64", commonDlls, containerDir)) {
                        return false;
                    }
                }
                catch (JSONException e) {
                    return false;
                }
            }

            return result;
        }
        else {
            File installedWineDir = RootFS.find(context).getInstalledWineDir();
            WineInfo wineInfo = WineInfo.fromIdentifier(context, wineVersion);
            File file = new File(installedWineDir, "container-pattern-"+wineInfo.fullVersion()+".tzst");
            return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, file, containerDir);
        }
    }
}
