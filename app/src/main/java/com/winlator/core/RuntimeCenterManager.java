package com.winlator.core;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/**
 * Owns Runtime Center downloads, validation, metadata, and new-container defaults.
 * Runtime payloads stay in app-private storage and are never bundled in the APK.
 */
public final class RuntimeCenterManager {
    public interface InstallCallback {
        void onPrepared(WineInfo wineInfo);
    }

    private static final String PREFERENCES = "runtime_center";
    private static final String KEY_DEFAULT_RUNTIME = "default_runtime_id";
    private static final String MARKER_SUFFIX = ".verified";
    private final Context context;
    private final SharedPreferences preferences;

    public RuntimeCenterManager(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        this.context = context.getApplicationContext() != null ?
            context.getApplicationContext() : context;
        this.preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public File getDownloadFile(RuntimeCenterCatalog.Runtime runtime) {
        if (runtime == null) return null;
        File directory = new File(new File(context.getFilesDir(), "compatibility-runtimes/downloads"), runtime.id);
        return new File(directory, runtime.fileName);
    }

    private File getVerificationMarker(RuntimeCenterCatalog.Runtime runtime) {
        File archive = getDownloadFile(runtime);
        return archive == null ? null : new File(archive.getParentFile(), archive.getName()+MARKER_SUFFIX);
    }

    /**
     * Fast UI-state check. A verified marker is bound to the exact catalog digest,
     * expected length, and file modification time. Full digest verification is always
     * repeated asynchronously immediately before installation.
     */
    public boolean isDownloaded(RuntimeCenterCatalog.Runtime runtime) {
        if (runtime == null) return false;
        File archive = getDownloadFile(runtime);
        File marker = getVerificationMarker(runtime);
        if (archive == null || marker == null || !archive.isFile() ||
                archive.length() != runtime.fileSizeBytes || !marker.isFile()) return false;
        String expectedMarker = runtime.sha256+"\\n"+runtime.fileSizeBytes+"\\n"+archive.lastModified();
        return expectedMarker.equals(FileUtils.readString(marker));
    }

    private void verifyDownloadAsync(RuntimeCenterCatalog.Runtime runtime,
            Callback<Boolean> callback) {
        File archive = getDownloadFile(runtime);
        if (runtime == null || archive == null) {
            if (callback != null) callback.call(false);
            return;
        }
        RuntimePackageVerifier.verifyAsync(archive, runtime.fileSizeBytes, runtime.sha256, verified -> {
            File marker = getVerificationMarker(runtime);
            boolean marked = verified && marker != null && FileUtils.writeString(marker,
                runtime.sha256+"\\n"+runtime.fileSizeBytes+"\\n"+archive.lastModified());
            if (!marked && marker != null) FileUtils.delete(marker);
            if (callback != null) callback.call(marked);
        });
    }

    public void download(Activity activity, RuntimeCenterCatalog.Runtime runtime, Callback<Boolean> callback) {
        if (activity == null || runtime == null) {
            if (callback != null) callback.call(false);
            return;
        }
        File destination = getDownloadFile(runtime);
        if (destination == null || (!destination.getParentFile().isDirectory() &&
                !destination.getParentFile().mkdirs())) {
            if (callback != null) callback.call(false);
            return;
        }
        if (isDownloaded(runtime)) {
            if (callback != null) callback.call(true);
            return;
        }

        FileUtils.delete(destination);
        FileUtils.delete(getVerificationMarker(runtime));
        HttpUtils.download(activity, runtime.assetUrl, destination, downloaded -> {
            if (!downloaded) {
                FileUtils.delete(destination);
                FileUtils.delete(getVerificationMarker(runtime));
                if (callback != null) callback.call(false);
                return;
            }
            verifyDownloadAsync(runtime, verified -> {
                if (!verified) {
                    FileUtils.delete(destination);
                    FileUtils.delete(getVerificationMarker(runtime));
                }
                if (callback != null) callback.call(verified);
            });
        });
    }

    public void deleteDownload(RuntimeCenterCatalog.Runtime runtime) {
        File archive = getDownloadFile(runtime);
        if (archive == null) return;
        FileUtils.delete(archive);
        FileUtils.delete(getVerificationMarker(runtime));
    }

    public boolean isInstalled(RuntimeCenterCatalog.Runtime runtime) {
        return getInstalledWineInfo(runtime) != null;
    }

    public WineInfo getInstalledWineInfo(RuntimeCenterCatalog.Runtime runtime) {
        if (runtime == null) return null;
        ArrayList<WineInfo> installed = WineInstaller.getInstalledWineInfos(context);
        for (WineInfo wineInfo : installed) {
            if (runtime.id.equals(readRuntimeId(wineInfo.path))) return wineInfo;
        }
        return null;
    }

    public void prepareInstall(RuntimeCenterCatalog.Runtime runtime, InstallCallback callback) {
        if (runtime == null || !runtime.supportedOnCurrentLauncher) {
            if (callback != null) callback.onPrepared(null);
            return;
        }

        // Verify every install attempt, even when a prior download marker exists.
        // This prevents a replaced or corrupted app-private archive from being installed.
        verifyDownloadAsync(runtime, verified -> {
            if (!verified) {
                deleteDownload(runtime);
                if (callback != null) callback.onPrepared(null);
                return;
            }

            File archive = getDownloadFile(runtime);
            WineInstaller.extractWineFileForInstallAsync(context, archive, extractedDir -> {
                if (extractedDir == null) {
                    if (callback != null) callback.onPrepared(null);
                    return;
                }
                WineInstaller.findWineVersionAsync(context, extractedDir, detectedWine -> {
                    if (detectedWine == null) {
                        FileUtils.delete(extractedDir);
                        if (callback != null) callback.onPrepared(null);
                        return;
                    }
                    File executable = WineInstaller.findWineExecutable(new File(detectedWine.path));
                    if (!CompatibilityRuntimePackage.isX86_64Elf(executable)) {
                        FileUtils.delete(extractedDir);
                        android.util.Log.w("WinMali-Runtime",
                            "Runtime rejected: x86-64 package does not contain an x86-64 Wine executable");
                        if (callback != null) callback.onPrepared(null);
                        return;
                    }

                    WineInfo managedWineInfo = new WineInfo(detectedWine.version,
                        runtime.installSuffix, detectedWine.path, runtime.displayName);
                    if (!writeMetadata(new File(detectedWine.path), runtime, detectedWine.version)) {
                        FileUtils.delete(extractedDir);
                        if (callback != null) callback.onPrepared(null);
                        return;
                    }
                    if (callback != null) callback.onPrepared(managedWineInfo);
                });
            });
        });
    }

    public boolean isSelectable(RuntimeCenterCatalog.Runtime runtime, boolean installed) {
        return RuntimeCenterCatalog.isSelectable(runtime, installed);
    }

    public boolean setDefault(RuntimeCenterCatalog.Runtime runtime) {
        if (!isSelectable(runtime, isInstalled(runtime))) return false;
        preferences.edit().putString(KEY_DEFAULT_RUNTIME, runtime.id).apply();
        return true;
    }

    public String getDefaultRuntimeId() {
        return preferences.getString(KEY_DEFAULT_RUNTIME, RuntimeCenterCatalog.DEFAULT_RUNTIME_ID);
    }

    public boolean isDefault(RuntimeCenterCatalog.Runtime runtime) {
        return runtime != null && runtime.id.equals(getDefaultRuntimeId());
    }

    public void clearDefaultIfMatches(String runtimeId) {
        if (runtimeId != null && runtimeId.equals(preferences.getString(KEY_DEFAULT_RUNTIME, RuntimeCenterCatalog.DEFAULT_RUNTIME_ID))) {
            preferences.edit().remove(KEY_DEFAULT_RUNTIME).apply();
        }
    }

    /** Returns the installed Wine identifier to preselect for new containers, or null for built-in Wine. */
    public String getPreferredWineIdentifier() {
        RuntimeCenterCatalog.Runtime runtime = RuntimeCenterCatalog.getRuntime(getDefaultRuntimeId());
        if (runtime == null || !RuntimeCenterCatalog.isSelectable(runtime, isInstalled(runtime))) return null;
        WineInfo info = getInstalledWineInfo(runtime);
        return info != null ? info.identifier() : null;
    }

    public static String readRuntimeId(String runtimePath) {
        if (runtimePath == null) return null;
        return readRuntimeId(new File(runtimePath));
    }

    public static String readRuntimeId(File runtimeDirectory) {
        JSONObject metadata = readMetadata(runtimeDirectory);
        return metadata != null ? metadata.optString("id", null) : null;
    }

    public static String readDisplayName(File runtimeDirectory) {
        JSONObject metadata = readMetadata(runtimeDirectory);
        return metadata != null ? metadata.optString("displayName", null) : null;
    }

    private static JSONObject readMetadata(File runtimeDirectory) {
        if (runtimeDirectory == null || !runtimeDirectory.isDirectory() ||
            FileUtils.isSymlink(runtimeDirectory)) return null;
        File file = new File(runtimeDirectory, RuntimeCenterCatalog.METADATA_FILE);
        if (!file.isFile() || FileUtils.isSymlink(file)) return null;
        try {
            return new JSONObject(FileUtils.readString(file));
        }
        catch (JSONException | RuntimeException error) {
            return null;
        }
    }

    private static boolean writeMetadata(File runtimeDirectory,
            RuntimeCenterCatalog.Runtime runtime, String detectedVersion) {
        if (runtimeDirectory == null || !runtimeDirectory.isDirectory() ||
            runtime == null || detectedVersion == null) return false;
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("id", runtime.id);
            metadata.put("displayName", runtime.displayName);
            metadata.put("family", runtime.family.name());
            metadata.put("guestArchitecture", runtime.guestArchitecture.label);
            metadata.put("detectedWineVersion", detectedVersion);
            metadata.put("releaseUrl", runtime.releaseUrl);
            return FileUtils.writeString(
                new File(runtimeDirectory, RuntimeCenterCatalog.METADATA_FILE), metadata.toString());
        }
        catch (JSONException error) {
            return false;
        }
    }
}
