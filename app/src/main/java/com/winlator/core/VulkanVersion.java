package com.winlator.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates and packs Vulkan API versions using the Vulkan 1.0 bit layout. */
public final class VulkanVersion {
    private static final Pattern VERSION_PATTERN =
        Pattern.compile("^\\s*v?([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?\\s*$");

    private VulkanVersion() {}

    public static int makeVersion(String value) {
        if (value == null) return 0;
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) return 0;
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            return makeVersion(major, minor, patch);
        }
        catch (NumberFormatException error) {
            return 0;
        }
    }

    public static int makeVersion(int major, int minor, int patch) {
        if (major < 0 || major > 0x7f || minor < 0 || minor > 0x3ff ||
            patch < 0 || patch > 0xfff) return 0;
        return (major << 22) | (minor << 12) | patch;
    }

    public static int major(int version) {
        return (version >>> 22) & 0x7f;
    }

    public static int minor(int version) {
        return (version >>> 12) & 0x3ff;
    }

    public static int patch(int version) {
        return version & 0xfff;
    }
}
