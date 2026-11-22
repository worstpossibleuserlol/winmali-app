package com.winlator.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * WinMali compatibility-runtime package manifest.
 *
 * Android host executables must be AArch64 ELF. Windows guest architectures
 * are declared separately and checked against the package's Wine library tree.
 */
public final class CompatibilityRuntimeManifest {
    public static final String FILE_NAME = "winmali-runtime.json";
    public static final String HOST_ABI_ARM64 = "arm64-v8a";
    public static final String GUEST_X86 = "x86";
    public static final String GUEST_X86_64 = "x86_64";
    public static final String GUEST_ARM64EC = "arm64ec";

    public enum Engine { WINE, PROTON }

    public final Engine engine;
    public final String version;
    public final String hostAbi;
    public final String entrypoint;
    public final String wineExecutable;
    public final String wineLibraryRoot;
    public final Set<String> guestArchitectures;
    public final boolean arm64ecVerified;

    private CompatibilityRuntimeManifest(Engine engine, String version, String hostAbi,
            String entrypoint, String wineExecutable, String wineLibraryRoot,
            Set<String> guestArchitectures, boolean arm64ecVerified) {
        this.engine = engine;
        this.version = version;
        this.hostAbi = hostAbi;
        this.entrypoint = entrypoint;
        this.wineExecutable = wineExecutable;
        this.wineLibraryRoot = wineLibraryRoot;
        this.guestArchitectures = guestArchitectures;
        this.arm64ecVerified = arm64ecVerified;
    }

    public static boolean isSupportedHostAbi(String hostAbi) {
        return HOST_ABI_ARM64.equals(hostAbi);
    }

    public static Set<String> parseGuestArchitectures(String[] values, boolean arm64ecVerified) {
        if (values == null || values.length == 0) return null;
        LinkedHashSet<String> guests = new LinkedHashSet<>();
        for (String value : values) {
            String guest = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
            if (!GUEST_X86.equals(guest) && !GUEST_X86_64.equals(guest) &&
                !GUEST_ARM64EC.equals(guest)) return null;
            if (!guests.add(guest)) return null;
        }
        if (guests.contains(GUEST_ARM64EC) && !arm64ecVerified) return null;
        return Collections.unmodifiableSet(guests);
    }

    public static boolean isEngineLaunchable(Engine engine) {
        return isEngineLaunchable(engine);
    }

    public static CompatibilityRuntimeManifest read(File runtimeRoot) {
        if (runtimeRoot == null || !runtimeRoot.isDirectory() || FileUtils.isSymlink(runtimeRoot)) return null;
        File file = new File(runtimeRoot, FILE_NAME);
        if (!file.isFile() || FileUtils.isSymlink(file)) return null;
        try {
            return parse(new JSONObject(FileUtils.readString(file)));
        }
        catch (JSONException | RuntimeException error) {
            return null;
        }
    }

    public static CompatibilityRuntimeManifest parse(JSONObject json) {
        if (json == null) return null;
        try {
            String engineName = json.optString("engine", "").trim().toUpperCase(Locale.ROOT);
            Engine engine;
            try {
                engine = Engine.valueOf(engineName);
            }
            catch (IllegalArgumentException error) {
                return null;
            }

            String version = json.optString("version", "").trim();
            String hostAbi = json.optString("hostAbi", "").trim();
            String entrypoint = CompatibilityRuntimePackage.normalizeRelativePath(
                json.optString("entrypoint", ""));
            String wineExecutable = CompatibilityRuntimePackage.normalizeRelativePath(
                json.optString("wineExecutable", ""));
            String wineLibraryRoot = CompatibilityRuntimePackage.normalizeRelativePath(
                json.optString("wineLibraryRoot", ""));

            if ( !CompatibilityRuntimePackage.isValidVersionName(version) ||
                !isSupportedHostAbi(hostAbi) || entrypoint == null ||
                wineExecutable == null || wineLibraryRoot == null) return null;

            JSONArray architectures = json.optJSONArray("guestArchitectures");
            if (architectures == null || architectures.length() == 0) return null;
            boolean arm64ecVerified = json.optBoolean("arm64ecVerified", false);
            String[] architectureNames = new String[architectures.length()];
            for (int i = 0; i < architectures.length(); i++) {
                architectureNames[i] = architectures.optString(i, "");
            }
            Set<String> guests = parseGuestArchitectures(architectureNames, arm64ecVerified);
            if (guests == null) return null;

            if (engine == Engine.WINE && !entrypoint.equals(wineExecutable)) return null;
            return new CompatibilityRuntimeManifest(engine, version, hostAbi, entrypoint,
                wineExecutable, wineLibraryRoot, guests, arm64ecVerified);
        }
        catch (RuntimeException error) {
            return null;
        }
    }

    /** Validates a Wine package before WinMali offers it to the Wine selector. */
    public boolean validateWineDirectory(File runtimeRoot) {
        if (engine != Engine.WINE || !isSafeDirectory(runtimeRoot)) return false;
        File wine = resolveSafeFile(runtimeRoot, wineExecutable);
        File launch = resolveSafeFile(runtimeRoot, entrypoint);
        File libraries = resolveSafeFile(runtimeRoot, wineLibraryRoot);
        if (!CompatibilityRuntimePackage.isAarch64Elf(wine) || launch == null ||
            !launch.isFile() || !libraries.isDirectory()) return false;

        for (String guest : guestArchitectures) {
            File guestDirectory = new File(libraries, getGuestDirectoryName(guest));
            if (!isChildOf(runtimeRoot, guestDirectory) || FileUtils.isSymlink(guestDirectory) ||
                !guestDirectory.isDirectory()) return false;
        }
        if (guestArchitectures.contains(GUEST_ARM64EC) && !arm64ecVerified) return false;
        return true;
    }

    /**
     * Detects guest architectures from a Wine library tree. ARM64EC is only
     * advertised when a package manifest explicitly attests that it was tested.
     */
    public static Set<String> detectGuestArchitectures(File runtimeRoot) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!isSafeDirectory(runtimeRoot)) return result;
        CompatibilityRuntimeManifest manifest = read(runtimeRoot);
        String libraryPath = manifest != null ? manifest.wineLibraryRoot : "lib/wine";
        File libraries = manifest != null ? resolveSafeFile(runtimeRoot, libraryPath) :
            resolveSafeFile(runtimeRoot, libraryPath);
        if (libraries == null || !libraries.isDirectory()) return result;

        if (new File(libraries, "i386-windows").isDirectory()) result.add(GUEST_X86);
        if (new File(libraries, "x86_64-windows").isDirectory()) result.add(GUEST_X86_64);
        if (manifest != null && manifest.arm64ecVerified &&
            manifest.guestArchitectures.contains(GUEST_ARM64EC) &&
            new File(libraries, "aarch64ec-windows").isDirectory()) result.add(GUEST_ARM64EC);
        return result;
    }

    public static String getArchitectureSummary(File runtimeRoot) {
        Set<String> architectures = detectGuestArchitectures(runtimeRoot);
        if (architectures.isEmpty()) return "guest ABI not detected";
        StringBuilder result = new StringBuilder();
        if (architectures.contains(GUEST_X86)) result.append("x86");
        if (architectures.contains(GUEST_X86_64)) append(result, "x86_64");
        if (architectures.contains(GUEST_ARM64EC)) append(result, "ARM64EC");
        return result.toString();
    }

    public boolean isLaunchableByCurrentWinMali() {
        // The current session launcher integrates Wine directly. Proton needs
        // a dedicated Android Proton launcher/runtime adapter before execution.
        return engine == Engine.WINE;
    }

    private static String getGuestDirectoryName(String guest) {
        if (GUEST_X86.equals(guest)) return "i386-windows";
        if (GUEST_X86_64.equals(guest)) return "x86_64-windows";
        return "aarch64ec-windows";
    }

    private static void append(StringBuilder value, String text) {
        if (value.length() > 0) value.append(", ");
        value.append(text);
    }

    private static boolean isSafeDirectory(File directory) {
        return directory != null && directory.isDirectory() && !FileUtils.isSymlink(directory);
    }

    private static File resolveSafeFile(File root, String relative) {
        if (!isSafeDirectory(root) ||
            CompatibilityRuntimePackage.normalizeRelativePath(relative) == null) return null;
        try {
            String canonicalRoot = root.getCanonicalPath();
            File target = new File(root, relative);
            String canonicalTarget = target.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalRoot + File.separator) ||
                FileUtils.isSymlink(target)) return null;
            return target;
        }
        catch (IOException | SecurityException error) {
            return null;
        }
    }

    private static boolean isChildOf(File root, File target) {
        try {
            return target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator);
        }
        catch (IOException | SecurityException error) {
            return false;
        }
    }
}
