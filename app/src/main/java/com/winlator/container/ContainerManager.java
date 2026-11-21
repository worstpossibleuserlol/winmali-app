package com.winlator.container;

import android.content.Context;
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

    public void removeContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler(context.getMainLooper());
        OPERATIONS.execute(() -> {
            try {
                removeContainer(container);
            }
            catch (RuntimeException ignored) {}
            if (callback != null) handler.post(callback);
        });
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

    private synchronized void removeContainer(Container container) {
        if (FileUtils.delete(container.getRootDir())) containers.remove(container);
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
            for (Container container : containers) {
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

    private void copyCommonDlls(String srcName, String dstName, JSONObject commonDlls, File containerDir) throws JSONException {
        File srcDir = new File(RootFS.find(context).getRootDir(), "/opt/wine/lib/wine/"+srcName);
        JSONArray dlnames = commonDlls.getJSONArray(dstName);

        for (int i = 0; i < dlnames.length(); i++) {
            String dlname = dlnames.getString(i);
            File dstFile = new File(containerDir, ".wine/drive_c/windows/"+dstName+"/"+dlname);
            FileUtils.copy(new File(srcDir, dlname), dstFile);
        }
    }

    private boolean extractContainerPatternFile(String wineVersion, File containerDir) {
        if (WineInfo.isMainWineVersion(wineVersion)) {
            boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "container_pattern.tzst", containerDir);

            if (result) {
                try {
                    JSONObject commonDlls = new JSONObject(FileUtils.readString(context, "common_dlls.json"));
                    copyCommonDlls("x86_64-windows", "system32", commonDlls, containerDir);
                    copyCommonDlls("i386-windows", "syswow64", commonDlls, containerDir);
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
