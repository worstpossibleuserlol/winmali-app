package com.winlator.inputcontrols;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.JsonReader;

import androidx.preference.PreferenceManager;

import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

public class InputControlsManager {
    private final Context context;
    private ArrayList<ControlsProfile> profiles = new ArrayList<>();
    private int maxProfileId;
    private boolean profilesLoaded = false;

    public InputControlsManager(Context context) {
        this.context = context;
    }

    public static File getProfilesDir(Context context) {
        File profilesDir = new File(context.getFilesDir(), "profiles");
        if (!profilesDir.isDirectory()) profilesDir.mkdir();
        return profilesDir;
    }

    public ArrayList<ControlsProfile> getProfiles() {
        return getProfiles(false);
    }

    public ArrayList<ControlsProfile> getProfiles(boolean ignoreTemplates) {
        if (!profilesLoaded) loadProfiles(ignoreTemplates);
        return profiles;
    }

    private void copyAssetProfilesIfNeeded() {
        File profilesDir = InputControlsManager.getProfilesDir(context);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int newVersion = AppUtils.getVersionCode(context);

        if (FileUtils.isEmpty(profilesDir)) {
            if (FileUtils.copy(context, "inputcontrols/profiles", profilesDir)) {
                preferences.edit().putInt("inputcontrols_app_version", newVersion).apply();
            }
            return;
        }

        int oldVersion = preferences.getInt("inputcontrols_app_version", 0);
        if (oldVersion == newVersion) return;

        File[] files = profilesDir.listFiles();
        if (files == null) return;
        boolean succeeded = true;

        try {
            AssetManager assetManager = context.getAssets();
            String[] assetFiles = assetManager.list("inputcontrols/profiles");
            if (assetFiles == null) return;
            for (String assetFile : assetFiles) {
                String assetPath = "inputcontrols/profiles/"+assetFile;
                ControlsProfile originProfile;
                try (InputStream input = assetManager.open(assetPath)) {
                    originProfile = loadProfile(context, input);
                }
                if (originProfile == null) {
                    succeeded = false;
                    continue;
                }

                File targetFile = null;
                for (File file : files) {
                    ControlsProfile targetProfile = loadProfile(context, file);
                    if (targetProfile != null && originProfile.id == targetProfile.id &&
                        originProfile.getName().equals(targetProfile.getName())) {
                        targetFile = file;
                        break;
                    }
                }

                if (targetFile != null && !FileUtils.copy(context, assetPath, targetFile)) {
                    succeeded = false;
                }
            }
        }
        catch (IOException | RuntimeException error) {
            android.util.Log.w("WinMali-Controls", "Unable to update bundled control profiles", error);
            succeeded = false;
        }

        // Only record the migration after every matching asset profile was copied.
        // A transient storage error must be retried on the next launch.
        if (succeeded) preferences.edit().putInt("inputcontrols_app_version", newVersion).apply();
    }

    public void loadProfiles(boolean ignoreTemplates) {
        File profilesDir = InputControlsManager.getProfilesDir(context);
        copyAssetProfilesIfNeeded();

        ArrayList<ControlsProfile> profiles = new ArrayList<>();
        File[] files = profilesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                ControlsProfile profile = loadProfile(context, file);
                // Ignore corrupt or partially written profiles instead of crashing
                // the entire profile picker when one file cannot be parsed.
                if (profile == null) continue;
                if (!(ignoreTemplates && profile.isTemplate())) profiles.add(profile);
                maxProfileId = Math.max(maxProfileId, profile.id);
            }
        }

        Collections.sort(profiles);
        this.profiles = profiles;
        profilesLoaded = true;
    }

    public ControlsProfile createProfile(String name) {
        if (!profilesLoaded) loadProfiles(false);
        if (name == null || name.trim().isEmpty() || name.trim().length() > 128) return null;
        ControlsProfile profile = new ControlsProfile(context, ++maxProfileId);
        profile.setName(name.trim());
        if (!profile.save()) return null;
        profiles.add(profile);
        return profile;
    }

    public ControlsProfile duplicateProfile(ControlsProfile source) {
        if (source == null || source.getName() == null) return null;
        if (!profilesLoaded) loadProfiles(false);

        String newName;
        for (int i = 1;;i++) {
            newName = source.getName() + " ("+i+")";
            boolean found = false;
            for (ControlsProfile profile : profiles) {
                if (profile.getName().equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }

        int newId = ++maxProfileId;
        File newFile = ControlsProfile.getProfileFile(context, newId);
        try {
            JSONObject data = new JSONObject(FileUtils.readString(ControlsProfile.getProfileFile(context, source.id)));
            data.put("id", newId);
            data.put("name", newName);
            if (data.has("template")) data.remove("template");
            if (!FileUtils.writeString(newFile, data.toString())) {
                FileUtils.delete(newFile);
                return null;
            }
        }
        catch (JSONException | RuntimeException error) {
            return null;
        }

        ControlsProfile profile = loadProfile(context, newFile);
        if (profile == null) {
            FileUtils.delete(newFile);
            return null;
        }
        profiles.add(profile);
        return profile;
    }

    public void removeProfile(ControlsProfile profile) {
        if (profile == null) return;
        File file = ControlsProfile.getProfileFile(context, profile.id);
        if (file.isFile() && file.delete()) profiles.remove(profile);
    }

    public ControlsProfile importProfile(JSONObject data) {
        if (!InputControlsProfileUtils.isValidImportDocument(data)) return null;
        if (!profilesLoaded) loadProfiles(false);

        int newId = ++maxProfileId;
        File newFile = ControlsProfile.getProfileFile(context, newId);
        try {
            // Do not mutate the caller's JSONObject: download/import callers may
            // retain it for retries or other processing.
            JSONObject importedData = new JSONObject(data.toString());
            importedData.put("id", newId);
            if (!FileUtils.writeString(newFile, importedData.toString())) {
                FileUtils.delete(newFile);
                return null;
            }

            ControlsProfile newProfile = loadProfile(context, newFile);
            if (newProfile == null) {
                FileUtils.delete(newFile);
                return null;
            }

            int foundIndex = -1;
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (profile.getName().equals(newProfile.getName())) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex != -1) profiles.set(foundIndex, newProfile);
            else profiles.add(newProfile);
            return newProfile;
        }
        catch (JSONException | RuntimeException error) {
            FileUtils.delete(newFile);
            return null;
        }
    }

    public File exportProfile(ControlsProfile profile) {
        if (profile == null || context == null) return null;
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null || FileUtils.isSymlink(downloadsDir)) return null;
        File winlatorDirectory = new File(downloadsDir, "Winlator");
        File exportDirectory = new File(winlatorDirectory, "profiles");
        if (FileUtils.isSymlink(winlatorDirectory) || FileUtils.isSymlink(exportDirectory)) return null;
        if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) return null;

        String filename = InputControlsProfileUtils.safeExportFileName(profile.getName(), profile.id);
        File destination = new File(exportDirectory, filename);
        try {
            String downloadsPath = downloadsDir.getCanonicalPath();
            String exportPath = exportDirectory.getCanonicalPath();
            String targetPath = destination.getCanonicalPath();
            if (!exportPath.startsWith(downloadsPath + File.separator) ||
                !targetPath.startsWith(exportPath + File.separator)) return null;
        }
        catch (IOException | SecurityException error) {
            return null;
        }

        File source = ControlsProfile.getProfileFile(context, profile.id);
        if (!source.isFile() || FileUtils.isSymlink(source) ||
            !FileUtils.copy(source, destination) || !destination.isFile()) return null;
        MediaScannerConnection.scanFile(context, new String[]{destination.getAbsolutePath()}, null, null);
        return destination;
    }

    public static ControlsProfile loadProfile(Context context, File file) {
        if (context == null || file == null || !file.isFile() || FileUtils.isSymlink(file)) return null;
        try {
            return loadProfile(context, new FileInputStream(file));
        }
        catch (IOException | RuntimeException error) {
            return null;
        }
    }

    public static ControlsProfile loadProfile(Context context, InputStream inStream) {
        if (context == null || inStream == null) return null;
        try (JsonReader reader = new JsonReader(new InputStreamReader(inStream, StandardCharsets.UTF_8))) {
            int profileId = -1;
            String profileName = null;
            float cursorSpeed = 1.0f;
            boolean disableMouseInput = false;
            boolean hideMouseCursor = false;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();

                if (name.equals("id")) {
                    profileId = reader.nextInt();
                }
                else if (name.equals("name")) {
                    profileName = reader.nextString();
                }
                else if (name.equals("cursorSpeed")) {
                    cursorSpeed = (float)reader.nextDouble();
                }
                else if (name.equals("disableMouseInput")) {
                    disableMouseInput = reader.nextBoolean();
                }
                else if (name.equals("hideMouseCursor")) {
                    hideMouseCursor = reader.nextBoolean();
                }
                else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            if (profileId < 0 || profileName == null || profileName.trim().isEmpty() ||
                profileName.length() > 128 || !Float.isFinite(cursorSpeed)) return null;

            ControlsProfile profile = new ControlsProfile(context, profileId);
            profile.setName(profileName.trim());
            profile.setCursorSpeed(cursorSpeed);
            profile.setDisableMouseInput(disableMouseInput);
            profile.setHideMouseCursor(hideMouseCursor);
            return profile;
        }
        catch (IOException | RuntimeException error) {
            return null;
        }
    }

    public ControlsProfile getProfile(int id) {
        for (ControlsProfile profile : getProfiles()) if (profile.id == id) return profile;
        return null;
    }
}
