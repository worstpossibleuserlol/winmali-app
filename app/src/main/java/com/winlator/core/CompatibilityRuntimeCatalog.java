package com.winlator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot of upstream compatibility-runtime releases used by the 2.0.0 beta UI.
 *
 * These are upstream reference versions, not bundled Android binaries. An
 * installable WinMali runtime must still be built for the Android AArch64 host.
 */
public final class CompatibilityRuntimeCatalog {
    public static final String SNAPSHOT_DATE = "2026-11-22";
    public static final String ANDROID_HOST_ABI = "arm64-v8a";

    public enum Engine { WINE, PROTON }
    public enum Channel { STABLE, DEVELOPMENT }

    public static final class Release {
        public final Engine engine;
        public final Channel channel;
        public final String version;
        public final String sourceUrl;
        public final String note;

        private Release(Engine engine, Channel channel, String version, String sourceUrl, String note) {
            this.engine = engine;
            this.channel = channel;
            this.version = version;
            this.sourceUrl = sourceUrl;
            this.note = note;
        }

        public String getTitle() {
            return (engine == Engine.WINE ? "Wine " : "Proton ") + version +
                (channel == Channel.STABLE ? " (Stable)" : " (Development)");
        }
    }

    private static final List<Release> RELEASES;

    static {
        ArrayList<Release> releases = new ArrayList<>();
        releases.add(new Release(Engine.WINE, Channel.STABLE, "11.0",
            "https://www.winehq.org/", "Latest stable Wine reference at the catalog snapshot date."));
        releases.add(new Release(Engine.WINE, Channel.DEVELOPMENT, "11.22",
            "https://www.winehq.org/", "Latest Wine development reference at the catalog snapshot date."));
        releases.add(new Release(Engine.PROTON, Channel.STABLE, "11.0-2e",
            "https://github.com/ValveSoftware/Proton/releases",
            "Latest stable Proton reference found at the catalog snapshot date."));
        RELEASES = Collections.unmodifiableList(releases);
    }

    private CompatibilityRuntimeCatalog() {}

    public static List<Release> getReleases() {
        return RELEASES;
    }

    public static Release getLatest(Engine engine, Channel channel) {
        if (engine == null || channel == null) return null;
        for (int i = RELEASES.size() - 1; i >= 0; i--) {
            Release release = RELEASES.get(i);
            if (release.engine == engine && release.channel == channel) return release;
        }
        return null;
    }

    /**
     * The requested Windows guest ABI matrix. Presence in this list does not
     * mean an upstream desktop archive supports it on Android.
     */
    public static String[] getGuestArchitectureTargets() {
        return new String[]{"x86", "x86_64", "arm64ec"};
    }

    public static String getArchitectureExplanation() {
        return "Android host: arm64-v8a (AArch64). Windows guest targets: x86, x86_64, and ARM64EC. " +
            "ARM64EC is a Windows guest ABI, not an Android/Linux host ABI.";
    }
}
