package com.winlator.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/** Validates the installed 32-bit and 64-bit DLLs for a Wine component. */
public final class WinComponentInstallState {
    private WinComponentInstallState() {}

    public static File[] getRequiredFiles(File windowsDir, String[] dllNames) {
        if (windowsDir == null || dllNames == null || dllNames.length == 0) return null;

        File system32 = new File(windowsDir, "system32");
        File syswow64 = new File(windowsDir, "syswow64");
        ArrayList<File> required = new ArrayList<>(dllNames.length * 2);
        for (String dllName : dllNames) {
            if (dllName == null) return null;
            String fileName = dllName.trim();
            if (fileName.isEmpty() || !fileName.matches("[A-Za-z0-9_.-]+") ||
                fileName.equals(".") || fileName.equals("..")) return null;
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".dll") &&
                !fileName.toLowerCase(Locale.ROOT).endsWith(".exe")) {
                fileName += ".dll";
            }
            required.add(new File(system32, fileName));
            required.add(new File(syswow64, fileName));
        }
        return required.toArray(new File[0]);
    }

    public static boolean arePresent(File windowsDir, String[] dllNames) {
        File[] required = getRequiredFiles(windowsDir, dllNames);
        if (required == null) return false;
        for (File file : required) {
            if (!file.isFile() || file.length() <= 0) return false;
        }
        return true;
    }
}
