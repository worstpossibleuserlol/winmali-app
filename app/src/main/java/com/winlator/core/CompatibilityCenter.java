package com.winlator.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

import com.winlator.box64.Box64PresetManager;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;

/**
 * WinMali's capability and compatibility service.
 *
 * The service separates capability discovery from launch validation. A reported
 * OpenGL/Vulkan API is not treated as proof that a Windows Direct3D workload renders.
 * ARM64EC remains gated until the app has an FEX/ARM64EC execution adapter.
 */
public final class CompatibilityCenter {
    public enum ExecutableArchitecture {
        X86("x86"), X86_64("x86-64"), ARM64("ARM64"), ARM64EC("ARM64EC"), UNKNOWN("Unknown");
        public final String label;
        ExecutableArchitecture(String label) { this.label = label; }
    }

    public static final class GpuScan {
        public final String renderer;
        public final String vendor;
        public final String openGLVersion;
        public final int vulkanApiVersion;
        public final String[] vulkanExtensions;
        public final GpuCompatibilityProfile profile;

        private GpuScan(String renderer, String vendor, String openGLVersion,
                int vulkanApiVersion, String[] vulkanExtensions,
                GpuCompatibilityProfile profile) {
            this.renderer = renderer;
            this.vendor = vendor;
            this.openGLVersion = openGLVersion;
            this.vulkanApiVersion = vulkanApiVersion;
            this.vulkanExtensions = vulkanExtensions != null ? vulkanExtensions.clone() : new String[0];
            this.profile = profile;
        }

        public boolean hasOpenGLContext() {
            return renderer != null && !renderer.trim().isEmpty() &&
                openGLVersion != null && !openGLVersion.trim().isEmpty();
        }

        public boolean hasVulkanApi() {
            return vulkanApiVersion > 0;
        }

        public boolean hasExtension(String name) {
            if (name == null) return false;
            for (String extension : vulkanExtensions) if (name.equals(extension)) return true;
            return false;
        }
    }

    public static final class ContainerValidation {
        public final boolean valid;
        public final List<String> findings;

        private ContainerValidation(boolean valid, List<String> findings) {
            this.valid = valid;
            this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
        }

        public String toReport() {
            StringBuilder report = new StringBuilder(valid ? "Configuration checks passed." : "Problems found:");
            for (String finding : findings) report.append("\n• ").append(finding);
            return report.toString();
        }
    }

    private static final String DATABASE_PREFS = "winmali_compatibility_database";
    private static final String DATABASE_KEY = "entries";
    private static final String GAME_PROFILE_KEY = "winmali_game_profiles";
    private static final int MAX_PE_HEADER_BYTES = 4 * 1024 * 1024;

    private CompatibilityCenter() {}

    public static GpuScan scanGpu(Context context) {
        String renderer = "";
        String vendor = "";
        String glVersion = "";
        int vkVersion = 0;
        String[] extensions = new String[0];

        if (context != null) {
            try {
                renderer = GPUHelper.glGetRenderer(context);
                vendor = GPUHelper.glGetVendor(context);
                glVersion = GPUHelper.glGetVersion(context);
            }
            catch (LinkageError | RuntimeException error) {
                android.util.Log.w("WinMali-Compatibility", "OpenGL capability probe failed", error);
            }
        }

        try {
            vkVersion = GPUHelper.vkGetApiVersion();
            String[] detected = GPUHelper.vkGetDeviceExtensions();
            if (detected != null) extensions = detected;
        }
        catch (LinkageError | RuntimeException error) {
            android.util.Log.w("WinMali-Compatibility", "Vulkan capability probe failed", error);
        }

        GpuCompatibilityProfile profile = GpuCompatibilityProfile.resolve(
            renderer, vendor, vkVersion, extensions);
        return new GpuScan(renderer, vendor, glVersion, vkVersion, extensions, profile);
    }

    public static String createDeviceReport(Context context, ContainerManager manager) {
        GpuScan scan = scanGpu(context);
        GpuCompatibilityProfile profile = scan.profile;
        StringBuilder report = new StringBuilder();
        report.append("WINMALI COMPATIBILITY REPORT\n");
        report.append("GPU: ").append(profile.getVendorLabel()).append("\n");
        report.append("Renderer: ").append(notDetected(scan.renderer)).append("\n");
        report.append("Vendor: ").append(notDetected(scan.vendor)).append("\n");
        report.append("OpenGL: ").append(notDetected(scan.openGLVersion)).append("\n");
        report.append("OpenGL test: ").append(scan.hasOpenGLContext()
            ? "EGL context and renderer information were obtained."
            : "No usable EGL/OpenGL context was reported.").append("\n");
        report.append("Vulkan API: ").append(profile.getVulkanVersionLabel()).append("\n");
        report.append("Vulkan test: ").append(scan.hasVulkanApi()
            ? "Native API version query succeeded; this does not create a device or render a frame."
            : "Vulkan API was not reported.").append("\n");
        report.append("Vulkan extensions: ").append(scan.vulkanExtensions.length).append("\n");
        report.append("Vulkan swapchain: ").append(scan.hasExtension("VK_KHR_swapchain") ? "present" : "missing").append("\n\n");

        report.append("GPU / DRIVER COMPATIBILITY\n");
        report.append("Recommended Vulkan driver: ").append(profile.vulkanDriver).append("\n");
        report.append("Recommended OpenGL driver: ").append(profile.openGLDriver).append("\n");
        report.append("Automatic renderer: ").append(profile.dxWrapper).append("\n");
        report.append("Fallback: WineD3D remains the conservative path when Vulkan translation is not supported.\n");
        if (profile.vendor == GpuCompatibilityProfile.Vendor.MALI) {
            report.append("Mali safety: Turnip is not selected; automatic DXVK is disabled.\n");
        }
        report.append("Box64 preset suggestion: ").append(recommendedBox64Preset(profile)).append("\n\n");

        report.append("DIRECT3D PREFLIGHT\n");
        report.append("Direct3D 9: WineD3D fallback selected for broad legacy compatibility; DLL presence is checked per container.\n");
        report.append("Direct3D 10/11: DXVK is only a candidate when the GPU profile explicitly reports support; otherwise use WineD3D.\n");
        report.append("Direct3D 12: ").append(profile.supportsVkd3dBaseline
            ? "Vulkan baseline detected; VKD3D runtime, device features, and rendering still require a real test."
            : "VKD3D baseline not met; not auto-selected.").append("\n");
        report.append("DirectDraw: DDraw wrapper availability is checked in each container's system32/syswow64 folders.\n");
        report.append("D3DX dependencies: Wine system libraries are checked before reporting a dependency as available.\n");
        report.append("Shader compatibility: Vulkan shader-related extensions are enumerated; shader compilation is not claimed.\n\n");

        report.append("RUNTIME / ARCHITECTURE\n");
        report.append("Android host ABI: ").append(android.os.Build.SUPPORTED_ABIS.length > 0
            ? android.os.Build.SUPPORTED_ABIS[0] : "unknown").append("\n");
        report.append("Guest x86 and x86-64: PE architecture detection is available in the executable checker.\n");
        report.append("ARM64EC: package inspection is allowed, execution is disabled until FEX/ARM64EC is integrated.\n");
        File wineRoot = context != null ? new File(RootFS.find(context).getRootDir(), "opt/wine") : null;
        report.append("Bundled Wine files: ").append(checkWineRuntimeFiles(wineRoot) ? "present" : "incomplete / not detected").append("\n");
        if (context != null) {
            int installedRuntimes = 0;
            RuntimeCenterManager runtimeManager = new RuntimeCenterManager(context);
            for (RuntimeCenterCatalog.Runtime runtime : RuntimeCenterCatalog.getRuntimes()) {
                if (runtimeManager.isInstalled(runtime)) installedRuntimes++;
            }
            report.append("Runtime Center installs: ").append(installedRuntimes)
                .append(" of ").append(RuntimeCenterCatalog.getRuntimes().size()).append("\n");
            try {
                report.append("Box64 profiles available: ")
                    .append(Box64PresetManager.getPresets(context).size()).append("\n");
            }
            catch (RuntimeException ignored) {}
        }

        report.append("\nTEST LIMITATIONS\n");
        report.append("These are host API, file, and configuration preflight checks. They do not launch a Windows Direct3D test program, compile a shader, or prove game compatibility. Run the in-container test application on the target phone for rendering confirmation.\n");
        if (manager != null) {
            report.append("\nCONTAINERS: ").append(manager.getContainers().size())
                .append(" managed container(s) detected.");
        }
        return report.toString();
    }

    public static String recommendedBox64Preset(GpuCompatibilityProfile profile) {
        if (profile == null || profile.vendor == GpuCompatibilityProfile.Vendor.UNKNOWN) return "STABILITY";
        if (profile.vendor == GpuCompatibilityProfile.Vendor.MALI ||
            profile.vendor == GpuCompatibilityProfile.Vendor.POWERVR) return "CONSERVATIVE";
        return "PERFORMANCE";
    }

    private static boolean checkWineRuntimeFiles(File wineRoot) {
        if (wineRoot == null || FileUtils.isSymlink(wineRoot)) return false;
        File bin = new File(wineRoot, "bin");
        File wine = new File(bin, "wine");
        File wine64 = new File(bin, "wine64");
        File x64 = new File(wineRoot, "lib/wine/x86_64-windows");
        File x86 = new File(wineRoot, "lib/wine/i386-windows");
        return (wine.isFile() || wine64.isFile()) && x64.isDirectory() && x86.isDirectory();
    }

    public static String getRuntimeDependencyReport(Context context) {
        if (context == null) return "Runtime check unavailable: no application context.";
        File root = new File(RootFS.find(context).getRootDir(), "opt/wine");
        File bin = new File(root, "bin");
        File x64 = new File(root, "lib/wine/x86_64-windows");
        File x86 = new File(root, "lib/wine/i386-windows");
        StringBuilder result = new StringBuilder("RUNTIME DEPENDENCY CHECK\n");
        result.append("Wine executable: ").append(new File(bin, "wine64").isFile() || new File(bin, "wine").isFile() ? "present" : "missing").append("\n");
        result.append("64-bit Windows libraries: ").append(x64.isDirectory() && !FileUtils.isSymlink(x64) ? "present" : "missing").append("\n");
        result.append("32-bit Windows libraries: ").append(x86.isDirectory() && !FileUtils.isSymlink(x86) ? "present" : "missing").append("\n");
        result.append("Box64 launcher: ").append(new File(RootFS.find(context).getRootDir(), "usr/bin/box64").isFile() ? "present" : "check bundled runtime layout").append("\n");
        result.append("Dependency resolver: install or repair the matching Wine component before selecting a native DLL override.");
        return result.toString();
    }


    public static String checkD3DXDependencies(Container container) {
        if (container == null || container.getRootDir() == null) return "No container selected.";
        String[] required = {"d3dx9_43.dll", "d3dx10_43.dll", "d3dx11_43.dll", "d3dcompiler_43.dll"};
        File windows = new File(container.getRootDir(), ".wine/drive_c/windows");
        File system32 = new File(windows, "system32");
        File syswow64 = new File(windows, "syswow64");
        StringBuilder report = new StringBuilder("D3DX DEPENDENCY CHECK\\n");
        int missing64 = 0;
        int missing32 = 0;
        for (String name : required) {
            boolean x64 = new File(system32, name).isFile();
            boolean x86 = new File(syswow64, name).isFile();
            if (!x64) missing64++;
            if (!x86) missing32++;
            report.append(name).append(": system32=").append(x64 ? "present" : "missing")
                .append(", syswow64=").append(x86 ? "present" : "missing").append("\\n");
        }
        report.append("\\nMissing 64-bit DLLs: ").append(missing64)
            .append("\\nMissing 32-bit DLLs: ").append(missing32)
            .append("\\nEnable or repair the Direct3D Windows component to install bundled helper DLLs. ")
            .append("This check only reports file presence and does not install redistributables or test rendering.");
        return report.toString();
    }

    public static String getShaderCompatibilitySummary(GpuScan scan) {
        if (scan == null) return "No graphics capability scan is available.";
        StringBuilder summary = new StringBuilder();
        summary.append(scan.hasOpenGLContext() ? "OpenGL context detected" : "OpenGL context unavailable");
        summary.append("; Vulkan ");
        summary.append(scan.hasVulkanApi() ? "API reported" : "API unavailable");
        summary.append("; SPIR-V 1.4: ")
            .append(scan.hasExtension("VK_KHR_spirv_1_4") ? "reported" : "not reported");
        summary.append("; shader float controls: ")
            .append(scan.hasExtension("VK_KHR_shader_float_controls") ? "reported" : "not reported");
        summary.append("; shader object: ")
            .append(scan.hasExtension("VK_EXT_shader_object") ? "reported" : "not reported");
        summary.append(". Extension presence does not prove compiler or game-shader compatibility.");
        return summary.toString();
    }

    public static String[] getDirect3DDependencies(int version) {
        switch (version) {
            case 9: return new String[]{"d3d9.dll", "d3dx9_43.dll", "dxgi.dll"};
            case 10: return new String[]{"d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "dxgi.dll"};
            case 11: return new String[]{"d3d11.dll", "dxgi.dll"};
            case 12: return new String[]{"d3d12.dll", "d3d12core.dll", "dxgi.dll"};
            default: return new String[0];
        }
    }

    public static String checkContainerDirect3DDependencies(Container container, int version) {
        if (container == null || container.getRootDir() == null) return "No container selected.";
        String[] required = getDirect3DDependencies(version);
        if (required.length == 0) return "Unsupported Direct3D version.";
        File windows = new File(container.getRootDir(), ".wine/drive_c/windows");
        File system32 = new File(windows, "system32");
        File syswow64 = new File(windows, "syswow64");
        StringBuilder result = new StringBuilder("Direct3D ").append(version).append(" DLL preflight\n");
        boolean complete64 = true;
        boolean complete32 = true;
        for (String dll : required) {
            boolean present64 = new File(system32, dll).isFile();
            boolean present32 = new File(syswow64, dll).isFile();
            if (!present64) complete64 = false;
            if (!present32) complete32 = false;
            result.append(dll).append(": 64-bit=").append(present64 ? "present" : "missing")
                .append(", 32-bit=").append(present32 ? "present" : "missing").append("\n");
        }
        result.append("64-bit prefix preflight: ").append(complete64 ? "complete" : "missing files").append("\n");
        result.append("32-bit prefix preflight: ").append(complete32 ? "complete" : "missing files").append("\n");
        result.append("This checks file presence only; it does not test Direct3D rendering.");
        return result.toString();
    }

    public static ExecutableArchitecture detectPEArchitecture(InputStream input) throws IOException {
        if (input == null) return ExecutableArchitecture.UNKNOWN;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int remaining = MAX_PE_HEADER_BYTES;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count < 0) break;
            if (count == 0) continue;
            output.write(buffer, 0, count);
            remaining -= count;
        }
        return detectPEArchitecture(output.toByteArray());
    }

    public static ExecutableArchitecture detectPEArchitecture(byte[] image) {
        if (image == null || image.length < 64 || image[0] != 'M' || image[1] != 'Z') {
            return ExecutableArchitecture.UNKNOWN;
        }
        int offset = readInt32(image, 0x3c);
        if (offset < 0 || offset > image.length - 6 ||
            image[offset] != 'P' || image[offset + 1] != 'E' ||
            image[offset + 2] != 0 || image[offset + 3] != 0) {
            return ExecutableArchitecture.UNKNOWN;
        }
        int machine = (image[offset + 4] & 0xff) | ((image[offset + 5] & 0xff) << 8);
        switch (machine) {
            case 0x014c: return ExecutableArchitecture.X86;
            case 0x8664: return ExecutableArchitecture.X86_64;
            case 0xaa64: return ExecutableArchitecture.ARM64;
            case 0xa641: return ExecutableArchitecture.ARM64EC;
            default: return ExecutableArchitecture.UNKNOWN;
        }
    }

    private static int readInt32(byte[] data, int offset) {
        if (offset < 0 || offset > data.length - 4) return -1;
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8) |
            ((data[offset + 2] & 0xff) << 16) | ((data[offset + 3] & 0xff) << 24);
    }

    public static ContainerValidation validateContainer(Context context, Container container) {
        ArrayList<String> findings = new ArrayList<>();
        if (context == null || container == null || container.getRootDir() == null) {
            findings.add("Container reference or application context is missing.");
            return new ContainerValidation(false, findings);
        }
        File root = container.getRootDir();
        if (FileUtils.isSymlink(root) || !root.isDirectory() || !isManagedRoot(context, container)) {
            findings.add("Container root is missing, unsafe, or outside WinMali's managed home directory.");
            return new ContainerValidation(false, findings);
        }
        File config = container.getConfigFile();
        if (FileUtils.isSymlink(config) || !config.isFile()) {
            findings.add("Container configuration file is missing or unsafe.");
        }
        else {
            try {
                new JSONObject(FileUtils.readString(config));
            }
            catch (JSONException | RuntimeException error) {
                findings.add("Container configuration JSON is invalid; repair will not overwrite it automatically.");
            }
        }
        File prefix = new File(root, ".wine");
        File driveC = new File(prefix, "drive_c");
        File windows = new File(driveC, "windows");
        if (FileUtils.isSymlink(prefix) || !prefix.isDirectory()) findings.add("Wine prefix folder is missing or unsafe.");
        if (FileUtils.isSymlink(driveC) || !driveC.isDirectory()) findings.add("drive_c folder is missing or unsafe.");
        if (FileUtils.isSymlink(windows) || !windows.isDirectory()) findings.add("Windows folder is missing or unsafe.");
        File system32 = new File(windows, "system32");
        File syswow64 = new File(windows, "syswow64");
        if (windows.isDirectory() && (!system32.isDirectory() || FileUtils.isSymlink(system32))) findings.add("system32 folder is missing or unsafe.");
        if (windows.isDirectory() && (!syswow64.isDirectory() || FileUtils.isSymlink(syswow64))) findings.add("syswow64 folder is missing or unsafe.");
        return new ContainerValidation(findings.isEmpty(), findings);
    }

    public static boolean isContainerActive(Context context, Container container) {
        if (context == null || container == null || container.getRootDir() == null) return false;
        File home = new File(RootFS.find(context).getRootDir(), "home");
        File active = new File(home, RootFS.USER);
        if (!FileUtils.isSymlink(active)) return false;
        String targetPath = FileUtils.readSymlink(active);
        if (targetPath == null || targetPath.isEmpty()) return false;
        File target = new File(targetPath);
        if (!target.isAbsolute()) target = new File(home, targetPath);
        try {
            return target.getCanonicalFile().equals(container.getRootDir().getCanonicalFile());
        }
        catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private static boolean isManagedRoot(Context context, Container container) {
        try {
            File home = new File(RootFS.find(context).getRootDir(), "home");
            File expected = new File(home, RootFS.USER+"-"+container.id).getCanonicalFile();
            return expected.equals(container.getRootDir().getCanonicalFile()) &&
                expected.getParentFile().equals(home.getCanonicalFile());
        }
        catch (IOException | RuntimeException error) {
            return false;
        }
    }

    /**
     * Repairs only absent, non-destructive directory/configuration pieces.
     * It never deletes a Wine prefix or replaces an invalid existing config file.
     */
    public static boolean repairContainer(Context context, Container container) {
        if (!isManagedRoot(context, container) || container.getRootDir() == null ||
            FileUtils.isSymlink(container.getRootDir())) return false;
        File root = container.getRootDir();
        File prefix = new File(root, ".wine");
        if (FileUtils.isSymlink(prefix) || !prefix.isDirectory()) return false;

        File driveC = new File(prefix, "drive_c");
        File windows = new File(driveC, "windows");
        if (FileUtils.isSymlink(driveC) || FileUtils.isSymlink(windows)) return false;
        if (!driveC.isDirectory() && !driveC.mkdirs()) return false;
        if (!windows.isDirectory() && !windows.mkdirs()) return false;
        File system32 = new File(windows, "system32");
        File syswow64 = new File(windows, "syswow64");
        if (FileUtils.isSymlink(system32) || FileUtils.isSymlink(syswow64)) return false;
        if (!system32.isDirectory() && !system32.mkdirs()) return false;
        if (!syswow64.isDirectory() && !syswow64.mkdirs()) return false;

        File config = container.getConfigFile();
        if (FileUtils.isSymlink(config)) return false;
        if (!config.exists() && !container.saveData()) return false;
        if (config.exists()) {
            try {
                new JSONObject(FileUtils.readString(config));
            }
            catch (JSONException | RuntimeException error) {
                return false;
            }
        }
        return validateContainer(context, container).valid;
    }

    public static boolean migrateContainer(Container container) {
        if (container == null || container.getConfigFile() == null ||
            FileUtils.isSymlink(container.getConfigFile()) || !container.getConfigFile().isFile()) return false;
        try {
            JSONObject data = new JSONObject(FileUtils.readString(container.getConfigFile()));
            Container.checkObsoleteOrMissingProperties(data);
            container.loadData(data);
            return container.saveData();
        }
        catch (JSONException | RuntimeException error) {
            android.util.Log.w("WinMali-Compatibility", "Container migration was not applied", error);
            return false;
        }
    }

    public static String detectNativeResolution(Context context) {
        if (context == null) return Container.DEFAULT_SCREEN_SIZE;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.max(metrics.widthPixels, metrics.heightPixels);
        int height = Math.min(metrics.widthPixels, metrics.heightPixels);
        return Math.max(1, width)+"x"+Math.max(1, height);
    }

    public static boolean applyNativeResolution(Context context, Container container) {
        if (container == null || !isManagedRoot(context, container)) return false;
        container.setScreenSize(detectNativeResolution(context));
        return container.saveData();
    }


    public static final class GameProfile {
        public final String dxWrapper;
        public final String box64Preset;
        public final String envVars;

        private GameProfile(String dxWrapper, String box64Preset, String envVars) {
            this.dxWrapper = dxWrapper;
            this.box64Preset = box64Preset;
            this.envVars = envVars;
        }
    }

    /**
     * Finds a profile using a shortcut's display name or executable path.
     * Settings are returned for this launch only; the container is not mutated.
     */
    public static GameProfile getGameProfile(Context context, Container container, String... gameKeys) {
        if (context == null || container == null || gameKeys == null) return null;
        try {
            JSONObject profiles = new JSONObject(container.getExtra(GAME_PROFILE_KEY, "{}"));
            for (String gameKey : gameKeys) {
                String key = normalizeGameKey(gameKey);
                if (key == null) continue;
                JSONObject profile = profiles.optJSONObject(key);
                if (profile == null) continue;
                String preset = profile.optString("box64Preset", "");
                if (Box64PresetManager.getPreset(context, preset) == null) continue;
                String wrapper = DXWrappers.parseIdentifier(
                    profile.optString("dxwrapper", DXWrappers.WINED3D));
                return new GameProfile(wrapper, preset, profile.optString("envVars", ""));
            }
        }
        catch (JSONException | RuntimeException error) {
            android.util.Log.w("WinMali-Compatibility", "Per-game profile could not be loaded", error);
        }
        return null;
    }

    public static boolean saveGameProfile(Container container, String gameKey,
            String dxWrapper, String box64Preset, String envVars) {
        String key = normalizeGameKey(gameKey);
        if (container == null || key == null || dxWrapper == null || box64Preset == null) return false;
        try {
            JSONObject profiles = new JSONObject(container.getExtra(GAME_PROFILE_KEY, "{}"));
            JSONObject profile = new JSONObject();
            profile.put("dxwrapper", DXWrappers.parseIdentifier(dxWrapper));
            profile.put("box64Preset", box64Preset);
            profile.put("envVars", envVars != null ? envVars : "");
            profiles.put(key, profile);
            container.putExtra(GAME_PROFILE_KEY, profiles.toString());
            return container.saveData();
        }
        catch (JSONException | RuntimeException error) {
            return false;
        }
    }

    public static List<String> getGameProfileKeys(Container container) {
        if (container == null) return Collections.emptyList();
        try {
            JSONObject profiles = new JSONObject(container.getExtra(GAME_PROFILE_KEY, "{}"));
            ArrayList<String> keys = new ArrayList<>();
            java.util.Iterator<String> iterator = profiles.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            return keys;
        }
        catch (JSONException | RuntimeException error) {
            return Collections.emptyList();
        }
    }

    public static boolean applyGameProfile(Context context, Container container, String gameKey) {
        GameProfile profile = getGameProfile(context, container, gameKey);
        if (profile == null) return false;
        container.setDXWrapper(profile.dxWrapper);
        container.setBox64Preset(profile.box64Preset);
        container.setEnvVars(profile.envVars);
        return container.saveData();
    }

    private static String normalizeGameKey(String value) {
        if (value == null) return null;
        String key = value.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.length() > 160) return null;
        return key.replace('\\', '/');
    }

    public static boolean recordCompatibility(Context context, String title, String notes) {
        String key = normalizeGameKey(title);
        if (context == null || key == null || notes == null || notes.length() > 4000) return false;
        try {
            SharedPreferences preferences = context.getSharedPreferences(DATABASE_PREFS, Context.MODE_PRIVATE);
            JSONObject database = new JSONObject(preferences.getString(DATABASE_KEY, "{}"));
            JSONObject entry = new JSONObject();
            entry.put("title", title.trim());
            entry.put("notes", notes.trim());
            entry.put("updatedAt", System.currentTimeMillis());
            database.put(key, entry);
            return preferences.edit().putString(DATABASE_KEY, database.toString()).commit();
        }
        catch (JSONException | RuntimeException error) {
            return false;
        }
    }

    public static String getCompatibilityDatabaseReport(Context context) {
        if (context == null) return "Compatibility database unavailable.";
        try {
            SharedPreferences preferences = context.getSharedPreferences(DATABASE_PREFS, Context.MODE_PRIVATE);
            JSONObject database = new JSONObject(preferences.getString(DATABASE_KEY, "{}"));
            StringBuilder report = new StringBuilder("LOCAL COMPATIBILITY DATABASE\n");
            if (database.length() == 0) return report.append("No user records saved yet.").toString();
            java.util.Iterator<String> iterator = database.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                JSONObject entry = database.optJSONObject(key);
                if (entry == null) continue;
                report.append("\n").append(entry.optString("title", key)).append("\n")
                    .append(entry.optString("notes", "")).append("\n");
            }
            report.append("\nRecords are stored only on this device; they are not verified community ratings.");
            return report.toString();
        }
        catch (JSONException | RuntimeException error) {
            return "Compatibility database could not be read: "+error.getMessage();
        }
    }

    public static String analyzeCrashLog(String logText) {
        if (logText == null || logText.trim().isEmpty()) return "Paste a WinMali or Wine log to analyze.";
        String log = logText.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder("LOG ANALYSIS\n");
        boolean identified = false;
        if (log.contains("vk_error_device_lost") || log.contains("device lost")) {
            result.append("Vulkan device-loss message detected. Try the conservative WineD3D/OpenGL profile and compare against the system driver.\n");
            identified = true;
        }
        if (log.contains("outofmemory") || log.contains("out of memory") || log.contains("vk_error_out_of_device_memory")) {
            result.append("Memory allocation failure detected. Reduce guest resolution and close other GPU-heavy applications.\n");
            identified = true;
        }
        if (log.contains("box64") && (log.contains("unsupported") || log.contains("unimplemented"))) {
            result.append("Box64 translation warning detected. Try the Stability or Conservative Box64 preset and check the executable architecture.\n");
            identified = true;
        }
        if (log.contains("d3d12") || log.contains("vkd3d")) {
            result.append("Direct3D 12/VKD3D path mentioned. Confirm Vulkan extensions and the installed VKD3D DLL set; API version alone is insufficient.\n");
            identified = true;
        }
        if (log.contains("d3d11") || log.contains("dxvk") || log.contains("dxgi")) {
            result.append("Direct3D 10/11 translation path mentioned. Validate DXVK files and try WineD3D as a comparison.\n");
            identified = true;
        }
        if (log.contains("no such file") || log.contains("not found") || log.contains("cannot open shared object")) {
            result.append("Missing-file or library-loader error detected. Run the runtime dependency and DLL preflight checks.\n");
            identified = true;
        }
        if (log.contains("permission denied") || log.contains("eacces")) {
            result.append("Filesystem permission error detected. Check container storage access and file ownership/permissions.\n");
            identified = true;
        }
        if (log.contains("wine: configuration") || log.contains("wineprefix")) {
            result.append("Wine prefix configuration is mentioned. Validate the container and create a recovery point before changing it.\n");
            identified = true;
        }
        if (!identified) result.append("No known signature matched. Keep the complete log and compare the first error before the final crash line.");
        result.append("\nThis is a rule-based hint, not a confirmed root-cause diagnosis.");
        return result.toString();
    }

    private static String notDetected(String value) {
        return value == null || value.trim().isEmpty() ? "Not detected" : value.trim();
    }
}
