package com.winlator.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Validation helpers for user-supplied compatibility-runtime packages.
 *
 * The current launcher executes an AArch64 Box64 binary from
 * usr/local/bin/box64. Other translators need their own launch adapters
 * before they can be selected as active runtimes.
 */
public final class CompatibilityRuntimePackage {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][A-Za-z0-9][A-Za-z0-9.-]*)?"
    );

    private CompatibilityRuntimePackage() {}

    public static boolean isValidVersionName(String version) {
        return version != null && VERSION_PATTERN.matcher(version).matches() &&
            !version.contains("..") && !version.endsWith(".");
    }

    public static String normalizeRelativePath(String path) {
        if (path == null || path.isEmpty()) return null;
        String normalized = path.replace('\\\\', '/');
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        if (normalized.isEmpty() || normalized.startsWith("/") ||
            normalized.matches("^[A-Za-z]:.*") || normalized.indexOf('\u0000') >= 0) return null;

        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return null;
        }
        return normalized;
    }

    /**
     * Returns a safe path relative to the runtime root, or null when a target
     * would escape the root or overwrite an unrelated part of the container.
     */
    public static String getSafeTargetPath(String componentType, String target) {
        String normalized = normalizeRelativePath(target);
        if (normalized == null || componentType == null) return null;

        if ("BOX64".equalsIgnoreCase(componentType)) {
            String[] allowedPrefixes = {
                "usr/local/bin/", "usr/local/lib/", "usr/lib/", "lib/",
                "etc/", "usr/share/box64/"
            };
            for (String prefix : allowedPrefixes) {
                if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                    return normalized;
                }
            }
            return null;
        }

        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("syswow64/")) {
            String name = normalized.substring(normalized.lastIndexOf('/') + 1);
            return isSafeFilename(name) ? "syswow64/" + name : null;
        }
        if (lower.contains("system32/")) {
            String name = normalized.substring(normalized.lastIndexOf('/') + 1);
            return isSafeFilename(name) ? "system32/" + name : null;
        }
        return null;
    }

    private static boolean isSafeFilename(String name) {
        return name != null && name.matches("[A-Za-z0-9_.-]+") &&
            !name.equals(".") && !name.equals("..");
    }

    public static boolean isAarch64Elf(File file) {
        if (file == null || !file.isFile() || file.length() < 20) return false;
        byte[] header = new byte[20];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            return (header[0] & 0xff) == 0x7f &&
                header[1] == 'E' && header[2] == 'L' && header[3] == 'F' &&
                (header[4] & 0xff) == 2 && // ELF64
                (header[5] & 0xff) == 1 && // little-endian
                (header[18] & 0xff) == 183 && (header[19] & 0xff) == 0; // EM_AARCH64
        }
        catch (IOException error) {
            return false;
        }
    }
}
