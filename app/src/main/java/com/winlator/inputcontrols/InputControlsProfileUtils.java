package com.winlator.inputcontrols;

import org.json.JSONObject;

/** Validation and filename helpers for imported/exported touchscreen profiles. */
final class InputControlsProfileUtils {
    private InputControlsProfileUtils() {}

    static boolean isValidImportDocument(JSONObject data) {
        if (data == null) return false;
        Object id = data.opt("id");
        Object name = data.opt("name");
        if (!(id instanceof Number) || !(name instanceof String)) return false;

        double numericId = ((Number)id).doubleValue();
        String profileName = ((String)name).trim();
        return Double.isFinite(numericId) && numericId >= 0 &&
            numericId <= Integer.MAX_VALUE && numericId == Math.rint(numericId) &&
            !profileName.isEmpty() && profileName.length() <= 128;
    }

    static String safeExportFileName(String profileName, int profileId) {
        String safeName = profileName == null ? "" : profileName.trim();
        safeName = safeName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        safeName = safeName.replace("..", ".");
        safeName = safeName.replaceAll("[. ]+$", "").trim();
        while (safeName.startsWith(".")) safeName = safeName.substring(1);
        if (safeName.replaceAll("[^\\\\p{L}\\\\p{N}]", "").isEmpty()) safeName = "";
        if (safeName.isEmpty()) safeName = "Controls-" + Math.max(0, profileId);
        if (safeName.length() > 100) safeName = safeName.substring(0, 100);
        return safeName + ".icp";
    }
}
