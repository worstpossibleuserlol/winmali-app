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
import java.util.Iterator;
import java.util.regex.Pattern;

public abstract class Box64PresetManager {
    private static final Pattern CUSTOM_PRESET_ID = Pattern.compile("CUSTOM-[1-9][0-9]{0,8}");

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

    static String[] parseCustomPresetEntry(String entry) {
        if (entry == null || entry.isEmpty()) return null;
        String[] fields = entry.split("\\|", 3);
        if (fields.length != 3 || !CUSTOM_PRESET_ID.matcher(fields[0]).matches() ||
            fields[1].trim().isEmpty()) return null;
        return fields;
    }

    private static ArrayList<String[]> customPresetsIterator(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String encoded = preferences.getString("box64_custom_presets", "");
        ArrayList<String[]> presets = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return presets;

        for (String entry : encoded.split(",", -1)) {
            String[] parsed = parseCustomPresetEntry(entry);
            if (parsed != null) presets.add(parsed);
        }
        return presets;
    }

    public static int getNextPresetId(Context context) {
        int maxId = 0;
        for (String[] preset : customPresetsIterator(context)) {
            try {
                int id = Integer.parseInt(preset[0].substring((Box64Preset.CUSTOM+"-").length()));
                maxId = Math.max(maxId, id);
            }
            catch (NumberFormatException ignored) {}
        }
        return maxId == Integer.MAX_VALUE ? 1 : maxId + 1;
    }

    public static void editPreset(Context context, String id, String name, EnvVars envVars) {
        final String key = "box64_custom_presets";
        ArrayList<String[]> presets = customPresetsIterator(context);
        boolean updated = false;

        if (id != null) {
            for (String[] preset : presets) {
                if (preset[0].equals(id)) {
                    preset[1] = name;
                    preset[2] = envVars != null ? envVars.toString() : "";
                    updated = true;
                    break;
                }
            }
            if (!updated) return;
        }
        else {
            presets.add(new String[]{Box64Preset.CUSTOM+"-"+getNextPresetId(context),
                name, envVars != null ? envVars.toString() : ""});
        }

        ArrayList<String> encodedPresets = new ArrayList<>();
        for (String[] preset : presets) {
            encodedPresets.add(preset[0]+"|"+preset[1]+"|"+preset[2]);
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
        final String key = "box64_custom_presets";
        ArrayList<String> encodedPresets = new ArrayList<>();
        for (String[] preset : customPresetsIterator(context)) {
            if (!preset[0].equals(id)) encodedPresets.add(preset[0]+"|"+preset[1]+"|"+preset[2]);
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
