package com.winlator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pinned, auditable runtime downloads supported by Runtime Center.
 *
 * The Android host ABI is arm64-v8a. Guest Windows architecture labels describe
 * the PE/Wine runtime target and must never be confused with the Android ABI.
 */
public final class RuntimeCenterCatalog {
    public static final String ANDROID_HOST_ABI = "arm64-v8a";
    public static final String DEFAULT_RUNTIME_ID = "proton-wine-11.0-2-x86_64";
    public static final String METADATA_FILE = "winmali-runtime-center.json";

    public enum Family {
        PROTON_WINE("Proton Wine"),
        CACHYOS_WINE("CachyOS Wine");

        public final String label;

        Family(String label) {
            this.label = label;
        }
    }

    public enum GuestArchitecture {
        X86_64("x86_64", "x86-64"),
        ARM64EC("arm64ec", "ARM64EC");

        public final String label;
        public final String displayName;

        GuestArchitecture(String label, String displayName) {
            this.label = label;
            this.displayName = displayName;
        }
    }

    public static final class Runtime {
        public final String id;
        public final Family family;
        public final String version;
        public final GuestArchitecture guestArchitecture;
        public final String displayName;
        public final String fileName;
        public final String assetUrl;
        public final String releaseUrl;
        public final long fileSizeBytes;
        public final String sha256;
        public final String installSuffix;
        public final boolean supportedOnCurrentLauncher;
        public final String warning;

        private Runtime(String id, Family family, String version,
                GuestArchitecture guestArchitecture, String displayName,
                String fileName, String assetUrl, String releaseUrl,
                long fileSizeBytes, String sha256, String installSuffix,
                boolean supportedOnCurrentLauncher, String warning) {
            this.id = id;
            this.family = family;
            this.version = version;
            this.guestArchitecture = guestArchitecture;
            this.displayName = displayName;
            this.fileName = fileName;
            this.assetUrl = assetUrl;
            this.releaseUrl = releaseUrl;
            this.fileSizeBytes = fileSizeBytes;
            this.sha256 = sha256;
            this.installSuffix = installSuffix;
            this.supportedOnCurrentLauncher = supportedOnCurrentLauncher;
            this.warning = warning;
        }

        public String getArchitectureLabel() {
            return guestArchitecture.displayName;
        }
    }

    private static final String PROTON_RELEASE =
        "https://github.com/GameNative/proton-wine/releases/tag/proton-11.0-2-20261120";
    private static final String PROTON_ASSET_BASE =
        "https://github.com/GameNative/proton-wine/releases/download/proton-11.0-2-20261120/";
    private static final String CACHYOS_RELEASE =
        "https://github.com/skynetigor/winlator-skynet-components/releases/tag/proton-cachyos-11.0-20261005";
    private static final String CACHYOS_ASSET_BASE =
        "https://github.com/skynetigor/winlator-skynet-components/releases/download/proton-cachyos-11.0-20261005/";

    private static final String PROTON_WARNING =
        "Experimental Android/Bionic Proton Wine build. The upstream publisher warns that testing builds may be unstable or crash.";
    private static final String CACHYOS_WARNING =
        "Third-party Android/Bionic rebuild; not an official CachyOS release. ARM64EC was not device-tested by the publisher.";

    private static final List<Runtime> RUNTIMES;

    static {
        ArrayList<Runtime> runtimes = new ArrayList<>();
        runtimes.add(new Runtime(
            "proton-wine-11.0-2-x86_64", Family.PROTON_WINE, "11.0-2",
            GuestArchitecture.X86_64, "Proton Wine 11.0-2 (x86-64)",
            "proton-wine-11.0-2-x86_64.wcp.xz",
            PROTON_ASSET_BASE+"proton-wine-11.0-2-x86_64.wcp.xz",
            PROTON_RELEASE, 59877772L,
            "649b2b0f837f5a6be35e1a98d478cbaa063f3ef2afb488925f03c31c40bf69f9",
            "proton-x86.64", true, PROTON_WARNING
        ));
        runtimes.add(new Runtime(
            "proton-wine-11.0-2-arm64ec", Family.PROTON_WINE, "11.0-2",
            GuestArchitecture.ARM64EC, "Proton Wine 11.0-2 (ARM64EC)",
            "proton-wine-11.0-2-arm64ec.wcp.xz",
            PROTON_ASSET_BASE+"proton-wine-11.0-2-arm64ec.wcp.xz",
            PROTON_RELEASE, 90812276L,
            "3da4c1aa17f41746ef2ab4d452abf44e1f600000df028adbfc368789db27edc5",
            "proton-arm64ec", false,
            PROTON_WARNING+" ARM64EC cannot be launched by the current Box64-only execution path."
        ));
        runtimes.add(new Runtime(
            "cachyos-wine-11.0-20261005-x86_64", Family.CACHYOS_WINE, "11.0-20261005",
            GuestArchitecture.X86_64, "CachyOS Wine 11.0-20261005 (x86-64)",
            "exp-cachyos-11.0-20261005-x86_64.wcp",
            CACHYOS_ASSET_BASE+"exp-cachyos-11.0-20261005-x86_64.wcp",
            CACHYOS_RELEASE, 60715604L,
            "36de3c0418ac93267ff1df1cdca911b2c725bd8ad6af5b0c664659c2387296f7",
            "cachyos-x86.64", true, CACHYOS_WARNING
        ));
        runtimes.add(new Runtime(
            "cachyos-wine-11.0-20261005-arm64ec", Family.CACHYOS_WINE, "11.0-20261005",
            GuestArchitecture.ARM64EC, "CachyOS Wine 11.0-20261005 (ARM64EC)",
            "exp-cachyos-11.0-20261005-arm64ec.wcp",
            CACHYOS_ASSET_BASE+"exp-cachyos-11.0-20261005-arm64ec.wcp",
            CACHYOS_RELEASE, 88689392L,
            "1326af4e378a073cf28ec51d459a981f8feb7c8e371c7b9d607079816c77c92d",
            "cachyos-arm64ec", false,
            CACHYOS_WARNING+" ARM64EC cannot be launched by the current Box64-only execution path."
        ));
        RUNTIMES = Collections.unmodifiableList(runtimes);
    }

    private RuntimeCenterCatalog() {}

    public static List<Runtime> getRuntimes() {
        return RUNTIMES;
    }

    public static Runtime getRuntime(String id) {
        if (id == null) return null;
        for (Runtime runtime : RUNTIMES) {
            if (runtime.id.equals(id)) return runtime;
        }
        return null;
    }

    public static boolean isSelectable(Runtime runtime, boolean installed) {
        return runtime != null && installed && runtime.supportedOnCurrentLauncher;
    }
}
