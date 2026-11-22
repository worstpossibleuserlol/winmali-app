package com.winlator.box64;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.core.EnvVars;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Base64;
import java.util.regex.Pattern;

public abstract class Box64PresetManager {
    private static final Pattern CUSTOM_PRESET_ID = Pattern.compile("CUSTOM-[1-9][0-9]{0,8}");
    private static final String ENCODED_PRESET_MARKER = "B64";

    public static EnvVars getEnvVars(Context context, String id) {
        EnvVars envVars = new EnvVars();
        if (id == null) return envVars;

        if (id.equals(Box64Preset.STABILITY)) {
            envVars.put("BOX64_DYNAREC_SAFEFLAGS", "2");
            envVars.put("BOX64_DYNAREC_FASTNAN", "0");
            envVars.put("BOX64_DYNAREC_FASTROUND", "0");
            envVars.put("BOX64_DYNAREC_X87DOUBLE", "1");
            envVars.put("BOX64_DYNAREC_BIGBLOCK", "0");
            envVars.put("BOX64_DYNAREC_STRONGMEM", "2");
            envVars.put("BOX64_DYNAREC_FORWARD", "128");
            envVars.put("BOX64_DYNAREC_CALLRET", "0");
            envVars.put("BOX64_DYNAREC_WAIT", "0");
            envVars.put("BOX64_DYNAREC_NATIVEFLAGS", "0");
            envVars.put("BOX64_DYNAREC_WEAKBARRIER", "0");
        }
        else if (id.equals(Box64Preset.CONSERVATIVE)) {
            envVars.put("BOX64_DYNAREC_SAFEFLAGS", "2");
            envVars.put("BOX64_DYNAREC_FASTNAN", "0");
            envVars.put("BOX64_DYNAREC_FASTROUND", "0");
            envVars.put("BOX64_DYNAREC_X87DOUBLE", "1");
            envVars.put("BOX64_DYNAREC_BIGBLOCK", "1");
            envVars.put("BOX64_DYNAREC_STRONGMEM", "1");
            envVars.put("BOX64_DYNAREC_FORWARD", "128");
            envVars.put("BOX64_DYNAREC_CALLRET", "0");
            envVars.put("BOX64_DYNAREC_WAIT", "1");
            envVars.put("BOX64_DYNAREC_NATIVEFLAGS", "0");
            envVars.put("BOX64_DYNAREC_WEAKBARRIER", "1");
        }
        else if (id.equals(Box64Preset.INTERMEDIATE)) {
            envVars.put("BOX64_DYNAREC_SAFEFLAGS", "2");
            envVars.put("BOX64_DYNAREC_FASTNAN", "1");
            envVars.put("BOX64_DYNAREC_FASTROUND", "0");
            envVars.put("BOX64_DYNAREC_X87DOUBLE", "1");
            envVars.put("BOX64_DYNAREC_BIGBLOCK", "2");
            envVars.put("BOX64_DYNAREC_STRONGMEM", "0");
            envVars.put("BOX64_DYNAREC_FORWARD", "128");
            envVars.put("BOX64_DYNAREC_CALLRET", "0");
            envVars.put("BOX64_DYNAREC_WAIT", "1");
            envVars.put("BOX64_DYNAREC_NATIVEFLAGS", "0");
            envVars.put("BOX64_DYNAREC_WEAKBARRIER", "2");
        }
        else if (id.equals(Box64Preset.PERFORMANCE)) {
            envVars.put("BOX64_DYNAREC_SAFEFLAGS", "1");
            envVars.put("BOX64_DYNAREC_FASTNAN", "1");
            envVars.put("BOX64_DYNAREC_FASTROUND", "1");
            envVars.put("BOX64_DYNAREC_X87DOUBLE", "0");
            envVars.put("BOX64_DYNAREC_BIGBLOCK", "3");
            envVars.put("BOX64_DYNAREC_STRONGMEM", "0");
            envVars.put("BOX64_DYNAREC_FORWARD", "512");
            envVars.put("BOX64_DYNAREC_CALLRET", "1");
            envVars.put("BOX64_DYNAREC_WAIT", "1");
            envVars.put("BOX64_DYNAREC_NATIVEFLAGS", "1");
            envVars.put("BOX64_DYNAREC_WEAKBARRIER", "2");
        }
        else if (id.startsWith(Box64Preset.CUSTOM)) {
            for (String[] preset : customPresetsIterator(context)) {
                if (preset[0].equals(id)) {
                    envVars.putAll(preset[2]);
                    break;
                }
            }
        }

        return envVars;
    }

    public static ArrayList<Box64Preset> getPresets(Context context) {
        ArrayList<Box64Preset> presets = new ArrayList<>();
        presets.add(new Box64Preset(Box64Preset.STABILITY, context.getString(R.string.stability)));
        presets.add(new Box64Preset(Box64Preset.CONSERVATIVE, context.getString(R.string.conservative)));
        presets.add(new Box64Preset(Box64Preset.INTERMEDIATE, context.getString(R.string.intermediate)));
        presets.add(new Box64Preset(Box64Preset.PERFORMANCE, context.getString(R.string.performance)));
        for (String[] preset : customPresetsIterator(context)) presets.add(new Box64Preset(preset[0], preset[1]));
        return presets;
    }

    public static Box64Preset getPreset(Context context, String id) {
        if (id == null) return null;
        for (Box64Preset preset : getPresets(context)) if (preset.id.equals(id)) return preset;
        return null;
    }

    private static String decodePresetField(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String encodePresetField(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String serializeCustomPresetEntry(String[] preset) {
        if (preset == null || preset.length != 3 ||
            !CUSTOM_PRESET_ID.matcher(preset[0]).matches() ||
            preset[1] == null || preset[1].trim().isEmpty() || preset[1].length() > 128 ||
            preset[2] == null || preset[2].length() > 65536) return null;

        return ENCODED_PRESET_MARKER+"|"+preset[0]+"|"+
            encodePresetField(preset[1])+"|"+encodePresetField(preset[2]);
    }

    static String[] parseCustomPresetEntry(String entry) {
        if (entry == null || entry.isEmpty()) return null;

        String[] fields;
        if (entry.startsWith(ENCODED_PRESET_MARKER+"|")) {
            fields = entry.split("\\|", 4);
            if (fields.length != 4 || !ENCODED_PRESET_MARKER.equals(fields[0])) return null;
            String name = decodePresetField(fields[2]);
            String envVars = decodePresetField(fields[3]);
            if (name == null || envVars == null) return null;
            fields = new String[]{fields[1], name, envVars};
        }
        else {
            // Read legacy separator-based entries so upgrades do not drop old presets.
            fields = entry.split("\\|", 3);
        }

        if (fields.length != 3 || !CUSTOM_PRESET_ID.matcher(fields[0]).matches() ||
            fields[1].trim().isEmpty() || fields[1].length() > 128 || fields[2].length() > 65536) return null;
        return fields;
    }

    private static ArrayList<String[]> customPresetsIterator(Context context) {
        ArrayList<String[]> presets = new ArrayList<>();
        if (context == null) return presets;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String encoded = preferences.getString("box64_custom_presets", "");
        if (encoded == null || encoded.isEmpty()) return presets;

        Set<String> seenIds = new HashSet<>();
        for (String entry : encoded.split(",", -1)) {
            String[] parsed = parseCustomPresetEntry(entry);
            if (parsed != null && seenIds.add(parsed[0])) presets.add(parsed);
        }
        return presets;
    }

    public static int getNextPresetId(Context context) {
        Set<Integer> usedIds = new HashSet<>();
        for (String[] preset : customPresetsIterator(context)) {
            try {
                usedIds.add(Integer.parseInt(preset[0].substring((Box64Preset.CUSTOM+"-").length())));
            }
            catch (NumberFormatException ignored) {}
        }

        // By the pigeonhole principle, among N stored IDs one of 1..N+1 is free.
        int candidate = 1;
        while (candidate <= usedIds.size() + 1 && usedIds.contains(candidate)) candidate++;
        return candidate <= 999999999 ? candidate : -1;
    }

    public static void editPreset(Context context, String id, String name, EnvVars envVars) {
        if (context == null || name == null || name.trim().isEmpty() || name.length() > 128) return;
        String serializedEnvVars = envVars != null ? envVars.toEscapedString() : "";
        if (serializedEnvVars.length() > 65536) return;
        final String key = "box64_custom_presets";
        ArrayList<String[]> presets = customPresetsIterator(context);
        boolean updated = false;

        if (id != null) {
            if (!CUSTOM_PRESET_ID.matcher(id).matches()) return;
            for (String[] preset : presets) {
                if (preset[0].equals(id)) {
                    preset[1] = name.trim();
                    preset[2] = serializedEnvVars;
                    updated = true;
                    break;
                }
            }
            if (!updated) return;
        }
        else {
            int nextId = getNextPresetId(context);
            if (nextId <= 0) return;
            presets.add(new String[]{Box64Preset.CUSTOM+"-"+nextId,
                name.trim(), serializedEnvVars});
        }

        ArrayList<String> encodedPresets = new ArrayList<>();
        for (String[] preset : presets) {
            String encoded = serializeCustomPresetEntry(preset);
            if (encoded == null) return;
            encodedPresets.add(encoded);
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(key, String.join(",", encodedPresets)).apply();
    }

    public static void duplicatePreset(Context context, String id) {
        ArrayList<Box64Preset> presets = getPresets(context);
        Box64Preset originPreset = null;
        for (Box64Preset preset : presets) {
            if (preset.id.equals(id)) {
                originPreset = preset;
                break;
            }
        }
        if (originPreset == null) return;

        String newName;
        for (int i = 1;;i++) {
            newName = originPreset.name+" ("+i+")";
            boolean found = false;
            for (Box64Preset preset : presets) {
                if (preset.name.equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }

        editPreset(context, null, newName, getEnvVars(context, originPreset.id));
    }

    public static void removePreset(Context context, String id) {
        if (context == null || id == null || !CUSTOM_PRESET_ID.matcher(id).matches()) return;
        final String key = "box64_custom_presets";
        ArrayList<String> encodedPresets = new ArrayList<>();
        for (String[] preset : customPresetsIterator(context)) {
            if (!preset[0].equals(id)) {
                String encoded = serializeCustomPresetEntry(preset);
                if (encoded == null) return;
                encodedPresets.add(encoded);
            }
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(key, String.join(",", encodedPresets)).apply();
    }

    public static void loadSpinner(Spinner spinner, String selectedId) {
        Context context = spinner.getContext();
        ArrayList<Box64Preset> presets = getPresets(context);

        int selectedPosition = 0;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(selectedId)) {
                selectedPosition = i;
                break;
            }
        }

        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, presets));
        spinner.setSelection(selectedPosition);
    }

    public static String getSpinnerSelectedId(Spinner spinner) {
        SpinnerAdapter adapter = spinner.getAdapter();
        int selectedPosition = spinner.getSelectedItemPosition();
        if (adapter != null && adapter.getCount() > 0 && selectedPosition >= 0) {
            return ((Box64Preset)adapter.getItem(selectedPosition)).id;
        }
        else return Box64Preset.DEFAULT;
    }
}
