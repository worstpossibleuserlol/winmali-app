# Runtime Center — design specification

## Goal

Replace the static Compatibility Runtime Bundle information dialog in WinMali 2.0.0 with an in-app Runtime Center where users can inspect, download, verify, install, select, update, and remove compatible Wine-family runtimes without changing the app version or bundling 60–100 MB runtime archives inside the APK.

## User-facing catalog for the first iteration

| Runtime | Guest architecture | Upstream package | 2.0.0 status |
| --- | --- | --- | --- |
| Proton Wine 11.0-2 | x86_64 | GameNative `.wcp.xz` | Download, verify, install, select through the existing Wine/Box64 path |
| Proton Wine 11.0-2 | ARM64EC | GameNative `.wcp.xz` | Download and verify; not selectable until an ARM64EC/FEX launcher is integrated |
| CachyOS Wine 11.0-20261005 | x86_64 | skynetigor `.wcp` | Download, verify, install, select through the existing Wine/Box64 path |
| CachyOS Wine 11.0-20261005 | ARM64EC | skynetigor `.wcp` | Download and verify; not selectable until an ARM64EC/FEX launcher is integrated |

The first catalog uses pinned upstream releases and their published SHA-256 digests. Runtime source, version, file size, architecture, and upstream warning are displayed before download. The GameNative release labels its `.wcp.xz` packages as Wine-type packages for Winlator-family clients; the underlying engine is Proton Wine. The CachyOS package is a third-party Android/Bionic rebuild, not an official CachyOS binary.

## Architecture and behaviour

1. Settings opens Runtime Center instead of the old informational alert.
2. Each catalog entry has a stable ID, engine family, display label, guest architecture, source/release URL, asset URL, exact byte size, SHA-256, format, support state, and upstream caveats.
3. Download runs off the UI thread through the existing bounded HTTP downloader. A staging file is used; expected size and SHA-256 are checked before it is promoted to the retained download.
4. A bad, truncated, oversized, or mismatched file is deleted and never appears as installed.
5. An x86_64 package is extracted into a staging directory using the existing safe TAR/XZ extractor, then validated using the existing Wine executable detection and Wine version probe before it can be installed.
6. Installation uses the existing Wine-prefix generation path and a unique runtime identifier. The app never overwrites another runtime or the bundled Wine directory.
7. Runtime Center distinguishes Downloaded, Installed, Selected, Update available, and Unsupported on this launcher. It only enables selection for an installed runtime that the current Box64 launcher can execute.
8. ARM64EC packages can be downloaded and integrity-checked, but remain clearly marked as unavailable for activation until the app contains a supported ARM64EC Wine/FEX execution path and required FEXCore dependencies.
9. The preferred runtime is stored separately from the per-container Wine selection. Existing containers are not silently changed. New-container defaults only change after the selected runtime is actually installed.
10. Removing a runtime is blocked while any container references it. The bundled Wine is never removable from Runtime Center.
11. All runtime content is stored in app-private storage. Downloads are resumable only if the existing downloader safely supports it; otherwise failed or cancelled temporary files are discarded.

## Safety, compatibility, and trust

- Do not treat a package extension or file name as proof of compatibility.
- Verify HTTPS source, exact file length, SHA-256, archive format, safe extraction, host ELF ABI, and expected Wine binaries.
- Preserve the bundled Wine as a recovery fallback.
- Never enable ARM64EC selection merely because its archive downloaded successfully.
- Do not use the GameNative-only `.wcp` asset as a Winlator package; use the upstream Winlator-oriented package variants.
- Surface upstream experimental warnings and the single-device/untested status attached to third-party builds.
- Preserve the app's 2.0.0 versionName, versionCode, and APK filename.

## Out of scope for 2.0.0

- Implementing the FEXCore/ARM64EC launcher and making ARM64EC runtimes executable.
- Downloading desktop Proton packages or Steam runtime files intended for Linux/glibc.
- Silently replacing existing container Wine versions.
- Automatically downloading large runtimes without user action.
- Claiming that a package has been tested on the user's device.

## Acceptance criteria

- Runtime Center opens from Settings.
- All four first-catalog variants have accurate labels, source links, sizes, SHA-256, and architecture states.
- A download is not retained as valid unless both size and SHA-256 match.
- X86_64 runtime imports are validated before being offered for installation.
- Installed runtime IDs do not collide across engine/architecture variants.
- Only supported and installed runtimes can be selected as defaults.
- Existing containers and bundled Wine remain unchanged.
- ARM64EC options visibly explain why they cannot yet be selected.
- Unit tests cover catalog validation, stable IDs, digest/size checking, and capability gating.
- WinMali remains version 2.0.0.
