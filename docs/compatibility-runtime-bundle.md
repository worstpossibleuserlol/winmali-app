# WinMali compatibility runtime bundle (2.0.0 beta)

Catalog snapshot: **2026-11-22**.

## Upstream reference versions

| Runtime | Channel | Version | Android status |
| --- | --- | --- | --- |
| Wine | Stable | 11.0 | Upstream reference; import an Android/AArch64 build |
| Wine | Development | 11.22 | Upstream reference; import an Android/AArch64 build |
| Proton | Stable | 11.0-2e | Upstream reference; no Android Proton launcher is integrated yet |

These versions are not bundled into the APK and are not direct Android downloads. Upstream Linux desktop packages must not be installed as Android runtimes.

## Host and guest architecture model

- **Android host ABI:** `arm64-v8a` (AArch64 Linux/Android).
- **Windows guest ABIs:** `x86` (32-bit), `x86_64` (64-bit), and `arm64ec`.
- ARM64EC is a Windows guest ABI. It is not an Android host ABI and must never be validated by merely checking that a Linux executable is AArch64.

## Wine package manifest

An Android-compatible Wine package may include `winmali-runtime.json` at its runtime root:

```json
{
  "engine": "WINE",
  "version": "11.22",
  "hostAbi": "arm64-v8a",
  "entrypoint": "bin/wine",
  "wineExecutable": "bin/wine",
  "wineLibraryRoot": "lib/wine",
  "guestArchitectures": ["x86", "x86_64", "arm64ec"],
  "arm64ecVerified": true
}
```

The Wine executable must be a valid AArch64 ELF file. Each declared guest ABI must have its corresponding Windows library directory below `wineLibraryRoot`:

- `x86`: `i386-windows`
- `x86_64`: `x86_64-windows`
- `arm64ec`: `aarch64ec-windows`

ARM64EC is accepted only when `arm64ecVerified` is true and the matching library directory exists. A package author must not claim ARM64EC support solely because the Android host is AArch64.

Legacy Wine archives without this manifest remain importable through the existing Wine installer, but WinMali only reports guest ABIs it can detect from the package's Wine library directories. ARM64EC is not inferred for legacy packages.

## Proton status

The catalog records the upstream Proton release, but Proton uses Steam-oriented launch scripts, runtime expectations, and integration that are not equivalent to selecting a Wine executable. A future Android Proton package must declare `engine: "PROTON"`, `hostAbi: "arm64-v8a"`, a Wine executable path, a Proton entrypoint, and its verified guest ABIs. The current WinMali session launcher does not launch Proton packages, so manifest recognition must not be interpreted as Proton execution support.

## Safety and package handling

- Runtime archives must be built for Android's AArch64 host.
- Package metadata is validated before a Wine build is exposed to the existing container Wine selector.
- Guest architecture declarations are checked against real library directories.
- Unsafe relative paths, desktop host ABIs, duplicate guest ABI entries, and unverified ARM64EC declarations are rejected.
- The 2.0.0 beta does not silently replace the main Wine runtime or change existing container runtime selections.
