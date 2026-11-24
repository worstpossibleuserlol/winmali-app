package com.winlator.core;

import java.io.File;
import java.util.Objects;

/** Validates whether a cached graphics-driver install is safe to reuse. */
public final class GraphicsDriverInstallState {
    private GraphicsDriverInstallState() {}

    public static boolean needsInstall(String cachedDriverKey, String requestedDriverKey,
            File... requiredFiles) {
        if (requestedDriverKey == null || requestedDriverKey.trim().isEmpty() ||
            !Objects.equals(cachedDriverKey, requestedDriverKey)) return true;
        if (requiredFiles == null || requiredFiles.length == 0) return true;

        for (File file : requiredFiles) {
            if (file == null || !file.isFile() || file.length() <= 0) return true;
        }
        return false;
    }
}
