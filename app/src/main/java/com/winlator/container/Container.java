package com.winlator.container;

import com.winlator.box64.Box64Preset;
import com.winlator.core.AppUtils;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.KeyValueSet;
import com.winlator.core.WineInfo;
import com.winlator.core.WineThemeManager;
import com.winlator.widget.FrameRating;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Container {
    public static final String DEFAULT_ENV_VARS = "ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=1 TU_DEBUG=noconform";
    public static final String DEFAULT_SCREEN_SIZE = "1280x720";
    public static final String DEFAULT_AUDIO_DRIVER = AudioDrivers.ALSA;
    // WineD3D is the conservative cross-vendor default; auto profiles may opt
    // supported GPUs into DXVK after checking the actual device family.
    public static final String DEFAULT_DXWRAPPER = DXWrappers.WINED3D;
    public static final String DEFAULT_WINCOMPONENTS = "direct3d=1,directsound=1,directmusic=1,directshow=0,directplay=0,xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1";
    public static final String FALLBACK_WINCOMPONENTS = "direct3d=0,directsound=0,directmusic=0,directshow=0,directplay=0,xaudio=0,vcrun2005=0,vcrun2010=0,wmdecoder=0";
    public static final String DEFAULT_DRIVES = "D:"+AppUtils.DIRECTORY_DOWNLOADS +"E:"+AppUtils.INTERNAL_STORAGE;
    public static final byte STARTUP_SELECTION_NORMAL = 0;
    public static final byte STARTUP_SELECTION_ESSENTIAL = 1;
    public static final byte STARTUP_SELECTION_AGGRESSIVE = 2;
    public static final byte MAX_DRIVE_LETTERS = 8;
    public final int id;
    private String name;
    private String screenSize = DEFAULT_SCREEN_SIZE;
    private String envVars = DEFAULT_ENV_VARS;
    private String graphicsDriver = GraphicsDrivers.DEFAULT_VULKAN_DRIVER+","+ GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
    private String dxwrapper = DEFAULT_DXWRAPPER;
    private String dxwrapperConfig = "";
    private String graphicsDriverConfig = "";
    private String audioDriverConfig = "";
    private String wincomponents = DEFAULT_WINCOMPONENTS;
    private String audioDriver = DEFAULT_AUDIO_DRIVER;
    private String drives = DEFAULT_DRIVES;
    private String wineVersion = WineInfo.MAIN_WINE_INFO.identifier();
    private byte hudMode = (byte)FrameRating.Mode.DISABLED.ordinal();
    private byte startupSelection = STARTUP_SELECTION_ESSENTIAL;
    private String cpuList;
    private String cpuListWoW64;
    private String desktopTheme = WineThemeManager.DEFAULT_DESKTOP_THEME;
    private String box64Preset = Box64Preset.DEFAULT;
    private File rootDir;
    private JSONObject extraData;

    public Container(int id) {
        this.id = id;
        this.name = "Container-"+id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "Container-"+id;
    }

    public String getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(String screenSize) {
        this.screenSize = screenSize;
    }

    public String getEnvVars() {
        return envVars;
    }

    public void setEnvVars(String envVars) {
        this.envVars = envVars != null ? envVars : "";
    }

    public String getGraphicsDriver() {
        return graphicsDriver;
    }

    public void setGraphicsDriver(String graphicsDriver) {
        this.graphicsDriver = graphicsDriver;
    }

    public String getDXWrapper() {
        return dxwrapper;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public String getGraphicsDriverConfig() {
        return graphicsDriverConfig;
    }

    public void setGraphicsDriverConfig(String graphicsDriverConfig) {
        this.graphicsDriverConfig = graphicsDriverConfig != null ? graphicsDriverConfig : "";
    }

    public String getDXWrapperConfig() {
        return dxwrapperConfig;
    }

    public void setDXWrapperConfig(String dxwrapperConfig) {
        this.dxwrapperConfig = dxwrapperConfig != null ? dxwrapperConfig : "";
    }

    public String getAudioDriverConfig() {
        return audioDriverConfig;
    }

    public void setAudioDriverConfig(String audioDriverConfig) {
        this.audioDriverConfig = audioDriverConfig != null ? audioDriverConfig : "";
    }

    public String getAudioDriver() {
        return audioDriver;
    }

    public void setAudioDriver(String audioDriver) {
        this.audioDriver = audioDriver;
    }

    public String getWinComponents() {
        return wincomponents;
    }

    public void setWinComponents(String wincomponents) {
        this.wincomponents = wincomponents;
    }

    public String getDrives() {
        return drives;
    }

    public void setDrives(String drives) {
        this.drives = drives != null ? drives : "";
    }

    public byte getHUDMode() {
        return hudMode;
    }

    public void setHUDMode(byte hudMode) {
        int modeCount = FrameRating.Mode.values().length;
        this.hudMode = hudMode >= 0 && hudMode < modeCount ?
            hudMode : (byte)FrameRating.Mode.DISABLED.ordinal();
    }

    public byte getStartupSelection() {
        return startupSelection;
    }

    public void setStartupSelection(byte startupSelection) {
        this.startupSelection = startupSelection >= STARTUP_SELECTION_NORMAL &&
            startupSelection <= STARTUP_SELECTION_AGGRESSIVE ?
            startupSelection : STARTUP_SELECTION_ESSENTIAL;
    }

    public String getCPUList() {
        return getCPUList(false);
    }

    public String getCPUList(boolean allowFallback) {
        return cpuList != null ? cpuList : (allowFallback ? getFallbackCPUList() : null);
    }

    public void setCPUList(String cpuList) {
        this.cpuList = cpuList != null && !cpuList.isEmpty() ? cpuList : null;
    }

    public String getCPUListWoW64() {
        return getCPUListWoW64(false);
    }

    public String getCPUListWoW64(boolean allowFallback) {
        return cpuListWoW64 != null ? cpuListWoW64 : (allowFallback ? getFallbackCPUList() : null);
    }

    public void setCPUListWoW64(String cpuListWoW64) {
        this.cpuListWoW64 = cpuListWoW64 != null && !cpuListWoW64.isEmpty() ? cpuListWoW64 : null;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public void setExtraData(JSONObject extraData) {
        this.extraData = extraData;
    }

    public String getExtra(String name) {
        return getExtra(name, "");
    }

    public String getExtra(String name, String fallback) {
        try {
            return extraData != null && extraData.has(name) ? extraData.getString(name) : fallback;
        }
        catch (JSONException e) {
            return fallback;
        }
    }

    public void putExtra(String name, Object value) {
        if (extraData == null) extraData = new JSONObject();
        try {
            if (value != null) {
                extraData.put(name, value);
            }
            else extraData.remove(name);
        }
        catch (JSONException e) {}
    }

    public String getWineVersion() {
        return wineVersion;
    }

    public void setWineVersion(String wineVersion) {
        this.wineVersion = wineVersion == null || wineVersion.trim().isEmpty() ?
            WineInfo.MAIN_WINE_INFO.identifier() : wineVersion.trim();
    }

    public File getConfigFile() {
        return new File(rootDir, ".container");
    }

    public File getUserDir() {
        return new File(rootDir, ".wine/drive_c/users/"+ RootFS.USER+"/");
    }

    public File getStartMenuDir() {
        return new File(rootDir, ".wine/drive_c/ProgramData/Microsoft/Windows/Start Menu/");
    }

    public File getIconsDir(int size) {
        return new File(rootDir, ".local/share/icons/hicolor/"+size+"x"+size+"/apps/");
    }

    public String getDesktopTheme() {
        return desktopTheme;
    }

    public void setDesktopTheme(String desktopTheme) {
        this.desktopTheme = desktopTheme;
    }

    public Iterable<Drive> drivesIterator() {
        return drivesIterator(drives);
    }

    private static int findNextDriveColon(String drives, int start) {
        int colon = drives.indexOf(":", Math.max(0, start));
        while (colon != -1) {
            if (colon > 0) {
                char driveLetter = Character.toUpperCase(drives.charAt(colon - 1));
                if (driveLetter >= 'A' && driveLetter <= 'Z') return colon;
            }
            colon = drives.indexOf(":", colon + 1);
        }
        return -1;
    }

    public static Iterable<Drive> drivesIterator(final String drivesValue) {
        final String drives = drivesValue != null ? drivesValue : "";
        return () -> new Iterator<Drive>() {
            private int index = findNextDriveColon(drives, 0);

            @Override
            public boolean hasNext() {
                return index != -1;
            }

            @Override
            public Drive next() {
                if (index == -1) throw new NoSuchElementException();
                int currentIndex = index;
                int nextIndex = findNextDriveColon(drives, currentIndex + 1);
                int pathEnd = nextIndex != -1 ? nextIndex - 1 : drives.length();
                if (pathEnd < currentIndex + 1) pathEnd = currentIndex + 1;

                String letter = String.valueOf(drives.charAt(currentIndex - 1));
                String path = drives.substring(currentIndex + 1, pathEnd);
                index = nextIndex;
                return new Drive(letter, path);
            }
        };
    }

    public boolean saveData() {
        if (rootDir == null || FileUtils.isSymlink(rootDir) || !rootDir.isDirectory()) return false;
        File destination = getConfigFile();
        File temporary;
        try {
            temporary = File.createTempFile(".container-", ".tmp", rootDir);
        }
        catch (IOException error) {
            return false;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("name", name);
            data.put("screenSize", screenSize);
            data.put("envVars", envVars);
            data.put("cpuList", cpuList);
            data.put("cpuListWoW64", cpuListWoW64);
            data.put("graphicsDriver", graphicsDriver);
            data.put("dxwrapper", dxwrapper);
            if (!dxwrapperConfig.isEmpty()) data.put("dxwrapperConfig", dxwrapperConfig);
            if (!graphicsDriverConfig.isEmpty()) data.put("graphicsDriverConfig", graphicsDriverConfig);
            if (!audioDriverConfig.isEmpty()) data.put("audioDriverConfig", audioDriverConfig);
            data.put("audioDriver", audioDriver);
            data.put("wincomponents", wincomponents);
            data.put("drives", drives);
            data.put("hudMode", hudMode);
            data.put("startupSelection", startupSelection);
            data.put("box64Preset", box64Preset);
            data.put("desktopTheme", desktopTheme);
            data.put("extraData", extraData);
            if (!WineInfo.isMainWineVersion(wineVersion)) data.put("wineVersion", wineVersion);

            if (!FileUtils.writeString(temporary, data.toString()) || !temporary.isFile() ||
                temporary.length() == 0) return false;
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return destination.isFile() && destination.length() > 0;
        }
        catch (JSONException | IOException | RuntimeException error) {
            android.util.Log.e("WinMali-Container", "Unable to save container configuration", error);
            return false;
        }
        finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    public void loadData(JSONObject data) throws JSONException {
        if (data == null) throw new JSONException("Container configuration is null");
        wineVersion = WineInfo.MAIN_WINE_INFO.identifier();
        dxwrapperConfig = "";
        graphicsDriverConfig = "";
        audioDriverConfig = "";

        checkObsoleteOrMissingProperties(data);

        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
            String key = it.next();
            switch (key) {
                case "name" :
                    setName(data.getString(key));
                    break;
                case "screenSize" :
                    setScreenSize(data.getString(key));
                    break;
                case "envVars" :
                    setEnvVars(data.getString(key));
                    break;
                case "cpuList" :
                    setCPUList(data.getString(key));
                    break;
                case "cpuListWoW64" :
                    setCPUListWoW64(data.getString(key));
                    break;
                case "graphicsDriver" :
                    setGraphicsDriver(data.getString(key));
                    break;
                case "wincomponents" :
                    setWinComponents(data.getString(key));
                    break;
                case "dxwrapper" :
                    setDXWrapper(data.getString(key));
                    break;
                case "dxwrapperConfig" :
                    setDXWrapperConfig(data.getString(key));
                    break;
                case "graphicsDriverConfig" :
                    setGraphicsDriverConfig(data.getString(key));
                    break;
                case "audioDriverConfig" :
                    setAudioDriverConfig(data.getString(key));
                    break;
                case "drives" :
                    setDrives(data.getString(key));
                    break;
                case "showFPS" :
                    setHUDMode((byte)(data.getBoolean(key) ? FrameRating.Mode.SIMPLE.ordinal() : FrameRating.Mode.DISABLED.ordinal()));
                    break;
                case "hudMode" :
                    setHUDMode((byte)data.getInt(key));
                    break;
                case "startupSelection" :
                    setStartupSelection((byte)data.getInt(key));
                    break;
                case "extraData" : {
                    JSONObject extraData = data.getJSONObject(key);
                    checkObsoleteOrMissingProperties(extraData);
                    setExtraData(extraData);
                    break;
                }
                case "wineVersion" :
                    setWineVersion(data.getString(key));
                    break;
                case "box64Preset" :
                    setBox64Preset(data.getString(key));
                    break;
                case "audioDriver" :
                    setAudioDriver(data.getString(key));
                    break;
                case "desktopTheme" :
                    setDesktopTheme(data.getString(key));
                    break;
            }
        }
    }

    public static void checkObsoleteOrMissingProperties(JSONObject data) {
        try {
            if (data.has("extraData")) {
                JSONObject extraData = data.getJSONObject("extraData");
                int appVersion = 0;
                try {
                    appVersion = Integer.parseInt(extraData.optString("appVersion", "0"));
                }
                catch (NumberFormatException ignored) {
                    // Damaged metadata is treated as an old container and migrated conservatively.
                }

                if (appVersion < 16 && data.has("envVars")) {
                    EnvVars defaultEnvVars = new EnvVars(DEFAULT_ENV_VARS);
                    EnvVars envVars = new EnvVars(data.getString("envVars"));
                    for (String name : defaultEnvVars) if (!envVars.has(name)) envVars.put(name, defaultEnvVars.get(name));
                    data.put("envVars", envVars.toString());
                }
            }

            KeyValueSet wincomponents1 = new KeyValueSet(DEFAULT_WINCOMPONENTS);
            KeyValueSet wincomponents2 = new KeyValueSet(data.optString("wincomponents", DEFAULT_WINCOMPONENTS));
            java.util.HashMap<String, String> savedComponents = new java.util.HashMap<>();
            for (String[] component : wincomponents2) {
                if (component.length >= 2) savedComponents.put(component[0], component[1]);
            }

            StringBuilder result = new StringBuilder();
            for (String[] component : wincomponents1) {
                if (component.length < 2) continue;
                if (result.length() > 0) result.append(',');
                result.append(component[0]).append('=')
                    .append(savedComponents.getOrDefault(component[0], component[1]));
            }
            data.put("wincomponents", result.toString());
        }
        catch (JSONException e) {}
    }

    public static String getFallbackCPUList() {
        String cpuList = "";
        int numProcessors = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < numProcessors; i++) cpuList += (!cpuList.isEmpty() ? "," : "")+i;
        return cpuList;
    }
}
