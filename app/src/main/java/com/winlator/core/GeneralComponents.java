package com.winlator.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.xenvironment.RootFS;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public abstract class GeneralComponents {
    public enum InstallMode {DOWNLOAD, FILE, BOTH}
    private static final String INSTALLABLE_COMPONENTS_URL = "https://raw.githubusercontent.com/brunodev85/winlator/main/installable_components/%s";
    private static final long MAX_COMPONENT_IMPORT_BYTES = 512L * 1024L * 1024L;

    public enum Type {
        BOX64, TURNIP, DXVK, VKD3D, WINED3D, SOUNDFONT, ADRENOTOOLS_DRIVER;

        private String lowerName() {
            return name().toLowerCase(Locale.ENGLISH);
        }

        private String title() {
            switch (this) {
                case BOX64:
                    return "Box64";
                case TURNIP:
                    return "Turnip";
                case DXVK:
                    return "DXVK";
                case VKD3D:
                    return "VKD3D";
                case WINED3D:
                    return "WineD3D";
                case SOUNDFONT:
                    return "SoundFont";
                case ADRENOTOOLS_DRIVER:
                    return "Adrenotools Driver";
            }

            return "";
        }

        private String assetFolder() {
            switch (this) {
                case BOX64:
                    return "box64";
                case TURNIP:
                    return "graphics_driver";
                case WINED3D:
                case DXVK:
                case VKD3D:
                    return "dxwrapper";
                case SOUNDFONT:
                    return "soundfont";
            }

            return "";
        }

        private File getSource(Context context, String identifier) {
            File componentDir = getComponentDir(this, context);
            switch (this) {
                case SOUNDFONT:
                    return new File(componentDir, identifier+".sf2");
                case ADRENOTOOLS_DRIVER:
                    return new File(componentDir, identifier);
                default:
                    return new File(componentDir, lowerName()+"-"+identifier+".tzst");
            }
        }

        public File getDestination(Context context) {
            File rootDir = RootFS.find(context).getRootDir();
            switch (this) {
                case DXVK:
                case VKD3D:
                case WINED3D:
                    return new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");
                case SOUNDFONT:
                    File destination = new File(context.getCacheDir(), "soundfont");
                    if (!destination.isDirectory()) destination.mkdirs();
                    return destination;
                default:
                    return rootDir;
            }
        }

        private InstallMode getInstallMode() {
            InstallMode installMode;
            if (this == Type.SOUNDFONT || this == ADRENOTOOLS_DRIVER) {
                installMode = InstallMode.FILE;
            }
            else if (this == Type.BOX64 || this == Type.WINED3D || this == Type.DXVK || this == Type.VKD3D) {
                installMode = InstallMode.BOTH;
            }
            else installMode = InstallMode.DOWNLOAD;
            return installMode;
        }

        private boolean isVersioned() {
            return this == BOX64 || this == TURNIP || this == DXVK || this == VKD3D || this == WINED3D;
        }
    }

    public static ArrayList<String> getBuiltinComponentNames(Type type) {
        String[] items = new String[0];

        switch (type) {
            case BOX64:
                items = new String[]{DefaultVersion.BOX64};
                break;
            case TURNIP:
                items = new String[]{DefaultVersion.TURNIP};
                break;
            case DXVK:
                items = new String[]{DefaultVersion.MINOR_DXVK, DefaultVersion.MAJOR_DXVK};
                break;
            case VKD3D:
                items = new String[]{DefaultVersion.VKD3D};
                break;
            case WINED3D:
                items = new String[]{DefaultVersion.WINED3D};
                break;
            case SOUNDFONT:
                items = new String[]{DefaultVersion.SOUNDFONT};
                break;
            case ADRENOTOOLS_DRIVER:
                items = new String[]{"System"};
                break;
        }

        return new ArrayList<>(Arrays.asList(items));
    }

    public static File getComponentDir(Type type, Context context) {
        File file = new File(context.getFilesDir(), "/installed_components/"+type.lowerName());
        if (!file.isDirectory()) file.mkdirs();
        return file;
    }

    public static ArrayList<String> getInstalledComponentNames(Type type, Context context) {
        File componentDir = getComponentDir(type, context);
        ArrayList<String> result = new ArrayList<>();

        File[] entries = componentDir.listFiles();
        if (entries == null) return result;

        for (File entry : entries) {
            String name = entry.getName();
            boolean supported;
            if (type == Type.SOUNDFONT) {
                supported = entry.isFile() && name.toLowerCase(Locale.ENGLISH).endsWith(".sf2");
            }
            else if (type == Type.ADRENOTOOLS_DRIVER) {
                supported = entry.isDirectory();
            }
            else {
                supported = entry.isFile() &&
                    name.startsWith(type.lowerName()+"-") && name.endsWith(".tzst");
            }
            if (supported) result.add(parseDisplayText(type, name));
        }
        return result;
    }

    public static boolean isBuiltinComponent(Type type, String identifier) {
        for (String builtinComponentName : getBuiltinComponentNames(type)) {
            if (builtinComponentName.equalsIgnoreCase(identifier)) return true;
        }
        return false;
    }

    public static String getDefinitivePath(Type type, Context context, String identifier) {
        if (identifier.isEmpty()) return null;
        if (type == Type.SOUNDFONT && isBuiltinComponent(type, identifier)) {
            File destination = type.getDestination(context);
            FileUtils.clear(destination);

            String filename = identifier+".sf2";
            destination = new File(destination, filename);
            FileUtils.copy(context, type.assetFolder()+"/"+filename, destination);
            return destination.getPath();
        }
        else if (type == Type.ADRENOTOOLS_DRIVER) {
            if (isBuiltinComponent(type, identifier)) return null;
            File source = type.getSource(context, identifier);
            File[] manifestFiles = source.listFiles((file, name) -> name.endsWith(".json"));
            if (manifestFiles != null && manifestFiles.length > 0) {
                try {
                    JSONObject manifestJSONObject = new JSONObject(FileUtils.readString(manifestFiles[0]));
                    String libraryName = manifestJSONObject.optString("libraryName", "");
                    if (libraryName.isEmpty() || !new File(libraryName).getName().equals(libraryName)) return null;
                    File libraryFile = new File(source, libraryName);
                    return libraryFile.isFile() ? libraryFile.getPath() : null;
                }
                catch (JSONException e) {
                    return null;
                }
            }
        }

        return type.getSource(context, identifier).getPath();
    }

    public static void extractFile(Type type, Context context, String identifier, String defaultVersion) {
        extractFileResolvedVersion(type, context, identifier, defaultVersion, null);
    }

    public static void extractFile(Type type, Context context, String identifier, String defaultVersion, TarCompressorUtils.OnExtractFileListener onExtractFileListener) {
        extractFileResolvedVersion(type, context, identifier, defaultVersion, onExtractFileListener);
    }

    /**
     * Extracts the selected component, falling back to the built-in version.
     * Returns the version that was actually extracted, or null when both fail.
     */
    public static String extractFileResolvedVersion(Type type, Context context, String identifier,
            String defaultVersion, TarCompressorUtils.OnExtractFileListener onExtractFileListener) {
        if (type == null || context == null || identifier == null || identifier.isEmpty()) return null;
        File destination = type.getDestination(context);

        if (isBuiltinComponent(type, identifier)) {
            String sourcePath = type.assetFolder()+"/"+type.lowerName()+"-"+identifier+".tzst";
            return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, sourcePath, destination,
                onExtractFileListener) ? identifier : null;
        }

        File componentDir = getComponentDir(type, context);
        File source = new File(componentDir, type.lowerName()+"-"+identifier+".tzst");
        if (source.isFile() && TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, source, destination,
                onExtractFileListener)) return identifier;

        if (defaultVersion == null || defaultVersion.isEmpty()) return null;
        String fallbackPath = type.assetFolder()+"/"+type.lowerName()+"-"+defaultVersion+".tzst";
        return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, fallbackPath, destination,
            onExtractFileListener) ? defaultVersion : null;
    }

    private static String parseDisplayText(Type type, String filename) {
        return filename.replace(type.lowerName()+"-", "").replace(".tzst", "").replace(".sf2", "");
    }

    private static void downloadComponentFile(final Type type, final String filename, final Spinner spinner, final String defaultItem) {
        final Activity activity = (Activity)spinner.getContext();
        File destination = new File(getComponentDir(type, activity), filename);
        if (destination.isFile()) destination.delete();
        HttpUtils.download(activity, String.format(INSTALLABLE_COMPONENTS_URL, type.lowerName()+"/"+filename), destination, (success) -> {
            if (success) {
                loadSpinner(type, spinner, parseDisplayText(type, filename), defaultItem);
            }
            else {
                FileUtils.delete(destination);
                AppUtils.showToast(activity, R.string.a_network_error_occurred);
            }
        });
    }

    private static boolean installFromPackagedFile(Context context, TarCompressorUtils.Type compressedType,
            final Type type, File originFile, String identifier, JSONArray filesJSONArray) {
        if (filesJSONArray == null || filesJSONArray.length() == 0 ||
            !CompatibilityRuntimePackage.isValidVersionName(identifier)) return false;

        File componentDir = getComponentDir(type, context);
        File tempDir = new File(context.getCacheDir(), "component-stage-"+System.nanoTime());
        File output = new File(componentDir, type.lowerName()+"-"+identifier+".tzst");
        if (output.exists()) return false;
        if (!tempDir.mkdirs()) return false;

        try {
            Map<String, String> sourceTargets = new HashMap<>();
            Set<String> targetPaths = new HashSet<>();
            for (int i = 0; i < filesJSONArray.length(); i++) {
                JSONObject entry = filesJSONArray.getJSONObject(i);
                String source = CompatibilityRuntimePackage.normalizeRelativePath(entry.optString("source", ""));
                String target = CompatibilityRuntimePackage.getSafeTargetPath(type.name(), entry.optString("target", ""));
                if (source == null || target == null || sourceTargets.put(source, target) != null ||
                    !targetPaths.add(target)) return false;
            }

            if (sourceTargets.isEmpty()) return false;
            Set<String> extractedSources = new HashSet<>();
            AtomicLong extractedBytes = new AtomicLong();
            boolean[] overLimit = {false};
            boolean extracted = TarCompressorUtils.extract(compressedType, originFile, tempDir, (archiveFile, size) -> {
                try {
                    String rootPath = tempDir.getCanonicalPath();
                    String entryPath = archiveFile.getCanonicalPath();
                    if (!entryPath.startsWith(rootPath + File.separator)) return null;
                    String relative = entryPath.substring(rootPath.length()+1).replace(File.separatorChar, '/');
                    String target = sourceTargets.get(relative);
                    if (target == null) return null;

                    if (size > 0) {
                        long total = extractedBytes.addAndGet(size);
                        if (total > MAX_COMPONENT_IMPORT_BYTES) {
                            overLimit[0] = true;
                            return null;
                        }
                    }
                    extractedSources.add(relative);
                    return new File(tempDir, target);
                }
                catch (IOException error) {
                    overLimit[0] = true;
                    return null;
                }
            });

            if (!extracted || overLimit[0] || extractedSources.size() != sourceTargets.size()) return false;

            if (type == Type.BOX64) {
                File box64 = new File(tempDir, "usr/local/bin/box64");
                if (!CompatibilityRuntimePackage.isAarch64Elf(box64)) return false;
            }

            return TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD, new File(tempDir, "."),
                output, MainActivity.CONTAINER_PATTERN_COMPRESSION_LEVEL);
        }
        catch (JSONException error) {
            return false;
        }
        finally {
            FileUtils.delete(tempDir);
            if (output.exists() && output.length() == 0) FileUtils.delete(output);
        }
    }

    private static File copyUriToCache(Context context, Uri uri) {
        File temporary = null;
        try {
            temporary = File.createTempFile("winmali-import-", ".archive", context.getCacheDir());
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("Unable to open selected file");
            try (InputStream in = new BufferedInputStream(input);
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temporary))) {
                byte[] buffer = new byte[32768];
                long copied = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    copied += count;
                    if (copied > MAX_COMPONENT_IMPORT_BYTES) throw new IOException("Selected package is too large");
                    out.write(buffer, 0, count);
                }
                out.flush();
            }
            return temporary;
        }
        catch (Exception error) {
            FileUtils.delete(temporary);
            return null;
        }
    }

    private static String getUriDisplayName(Context context, Uri uri) {
        if (context != null && uri != null) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) return cursor.getString(index);
                }
            }
            catch (Exception ignored) {}
        }
        return uri != null ? uri.getLastPathSegment() : null;
    }

    private static void openFileForInstall(final MainActivity activity, final Type type, final Spinner spinner, final String defaultItem) {
        activity.setOpenFileCallback((uri) -> {
            File source = copyUriToCache(activity, uri);
            if (source == null) {
                AppUtils.showToast(activity, R.string.unable_to_import_component);
                return;
            }

            try {
                switch (type) {
                    case SOUNDFONT: {
                        String filename = FileUtils.getName(getUriDisplayName(activity, uri));
                        if (!filename.toLowerCase(Locale.ENGLISH).endsWith(".sf2")) {
                            AppUtils.showToast(activity, R.string.unable_to_import_component);
                            break;
                        }
                        File destination = new File(getComponentDir(type, activity), filename);
                        if (destination.isFile()) FileUtils.delete(destination);
                        if (FileUtils.copy(source, destination)) loadSpinner(type, spinner, parseDisplayText(type, filename), defaultItem);
                        else AppUtils.showToast(activity, R.string.unable_to_import_component);
                        break;
                    }
                    case ADRENOTOOLS_DRIVER: {
                        byte[] manifestData = ZipUtils.read(source, "*.json");
                        if (manifestData != null) {
                            JSONObject manifestJSONObject = new JSONObject(new String(manifestData, java.nio.charset.StandardCharsets.UTF_8));
                            String filename = manifestJSONObject.optString("name", manifestJSONObject.optString("libraryName", ""));
                            if (filename.isEmpty() || !filename.matches("[A-Za-z0-9._-]+") ||
                                filename.equals(".") || filename.equals("..")) {
                                AppUtils.showToast(activity, R.string.unable_to_import_component);
                                break;
                            }
                            File destination = new File(getComponentDir(type, activity), filename);
                            if (destination.exists()) FileUtils.delete(destination);
                            if (ZipUtils.extract(source, destination)) loadSpinner(type, spinner, filename, defaultItem);
                            else AppUtils.showToast(activity, R.string.unable_to_import_component);
                        }
                        else AppUtils.showToast(activity, R.string.unable_to_import_component);
                        break;
                    }
                    default: {
                        TarCompressorUtils.Type compressedType = null;
                        byte[] manifestData = null;
                        TarCompressorUtils.Type[] supportedTypes = {
                            TarCompressorUtils.Type.ZSTD, TarCompressorUtils.Type.XZ, TarCompressorUtils.Type.GZIP
                        };
                        for (TarCompressorUtils.Type candidate : supportedTypes) {
                            manifestData = TarCompressorUtils.read(candidate, source, "*.json");
                            if (manifestData != null) {
                                compressedType = candidate;
                                break;
                            }
                        }

                        if (manifestData == null || compressedType == null) {
                            AppUtils.showToast(activity, R.string.unable_to_import_component);
                            break;
                        }

                        JSONObject manifestJSONObject = new JSONObject(new String(manifestData, java.nio.charset.StandardCharsets.UTF_8));
                        String contentType = manifestJSONObject.optString("type", "").toUpperCase(Locale.ENGLISH);
                        String identifier = StringUtils.parseIdentifier(manifestJSONObject.optString("versionName", ""));
                        JSONArray filesJSONArray = manifestJSONObject.optJSONArray("files");

                        if (contentType.equals(type.name()) && CompatibilityRuntimePackage.isValidVersionName(identifier) &&
                            installFromPackagedFile(activity, compressedType, type, source, identifier, filesJSONArray)) {
                            loadSpinner(type, spinner, identifier, defaultItem);
                            AppUtils.showToast(activity, R.string.component_imported);
                        }
                        else AppUtils.showToast(activity, R.string.unable_to_import_component);
                        break;
                    }
                }
            }
            catch (Exception error) {
                AppUtils.showToast(activity, R.string.unable_to_import_component);
            }
            finally {
                FileUtils.delete(source);
            }
        });

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
    }

    private static void showDownloadableListDialog(Type type, final Spinner spinner, final String defaultItem) {
        final Activity activity = (Activity)spinner.getContext();
        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.loading);
        HttpUtils.download(String.format(INSTALLABLE_COMPONENTS_URL, type.lowerName()+"/index.txt"), (content) -> activity.runOnUiThread(() -> {
            preloaderDialog.close();
            if (content != null) {
                if (content.isEmpty()) {
                    AppUtils.showToast(activity, R.string.there_are_no_items_to_download);
                    return;
                }
                final String[] filenames = content.split("\n");
                final String[] items = filenames.clone();
                for (int i = 0; i < items.length; i++) {
                    items[i] = type.title()+" "+parseDisplayText(type, items[i]);
                }

                ContentDialog.showSelectionList(activity, R.string.install_component, items, false, (positions) -> {
                    if (!positions.isEmpty()) downloadComponentFile(type, filenames[positions.get(0)], spinner, defaultItem);
                });
            }
            else AppUtils.showToast(activity, R.string.a_network_error_occurred);
        }));
    }

    public static void initViews(final Type type, View toolbox, final Spinner spinner, final String selectedItem, final String defaultItem) {
        final Context context = spinner.getContext();
        toolbox.findViewWithTag("install").setOnClickListener((v) -> {
            InstallMode installMode = type.getInstallMode();
            switch (installMode) {
                case DOWNLOAD:
                    showDownloadableListDialog(type, spinner, defaultItem);
                    break;
                case FILE:
                    openFileForInstall((MainActivity)context, type, spinner, defaultItem);
                    break;
                case BOTH:
                    PopupMenu popupMenu = new PopupMenu(context, v);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popupMenu.setForceShowIcon(true);
                    popupMenu.inflate(R.menu.open_file_popup_menu);
                    popupMenu.setOnMenuItemClickListener((menuItem) -> {
                        int itemId = menuItem.getItemId();
                        if (itemId == R.id.menu_item_open_file) {
                            openFileForInstall((MainActivity)context, type, spinner, defaultItem);
                        }
                        else if (itemId == R.id.menu_item_download_file) {
                            showDownloadableListDialog(type, spinner, defaultItem);
                        }
                        return true;
                    });
                    popupMenu.show();
                    break;
            }
        });

        toolbox.findViewWithTag("remove").setOnClickListener((v) -> {
            String identifier = spinner.getSelectedItem().toString();

            if (isBuiltinComponent(type, identifier)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_component_version);
                return;
            }

            File source = type.getSource(context, identifier);
            if (source.exists()) {
                ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_component_version, () -> {
                    FileUtils.delete(source);
                    loadSpinner(type, spinner, selectedItem, defaultItem);
                });
            }
        });

        loadSpinner(type, spinner, selectedItem, defaultItem);
    }

    private static void loadSpinner(Type type, Spinner spinner, String selectedItem, String defaultItem) {
        ArrayList<String> items = getBuiltinComponentNames(type);
        items.addAll(getInstalledComponentNames(type, spinner.getContext()));

        if (type.isVersioned()) {
            items.sort((o1, o2) -> Integer.compare(GPUHelper.vkMakeVersion(o1), GPUHelper.vkMakeVersion(o2)));
        }

        spinner.setAdapter(new ArrayAdapter<>(spinner.getContext(), android.R.layout.simple_spinner_dropdown_item, items));

        if (selectedItem == null || selectedItem.isEmpty() || !AppUtils.setSpinnerSelectionFromValue(spinner, selectedItem)) {
            AppUtils.setSpinnerSelectionFromValue(spinner, defaultItem);
        }
    }
}