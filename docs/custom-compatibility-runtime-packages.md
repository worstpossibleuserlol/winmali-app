# Custom compatibility runtime packages

WinMali 2.0.0 can import user-provided **Box64** runtimes from Settings. The package is a compressed TAR archive in Zstandard, XZ, or GZIP format and must contain one JSON package manifest plus the runtime payload.

## Import steps

1. Open **Settings**.
2. Find **Box64 Version** and tap its install control.
3. Choose **Open file** and select the runtime package.
4. Select the imported version in the Box64 Version list.
5. Press **Confirm** to save the selection.

The import is stored in the app's private component directory. The original selected archive is copied to app cache during validation and removed afterwards.

## Package manifest

Include a JSON file at the archive root. It should be the only JSON manifest in the archive so the importer can identify it. Use this schema:

```json
{
  "type": "BOX64",
  "versionName": "0.4.6",
  "files": [
    {
      "source": "usr/local/bin/box64",
      "target": "usr/local/bin/box64"
    },
    {
      "source": "usr/local/lib/libexample.so",
      "target": "usr/local/lib/libexample.so"
    }
  ]
}
```

- `type` must be `BOX64`.
- `versionName` must be a safe version identifier such as `0.4.6` or `0.4.6-rc1`.
- Each `source` is a relative path inside the archive.
- Each `target` is a relative path in the WinMali root filesystem. Box64 imports are restricted to `usr/local/bin/`, `usr/local/lib/`, `usr/lib/`, `lib/`, `etc/`, and `usr/share/box64/`.
- The final package must provide `usr/local/bin/box64` as a 64-bit, little-endian AArch64 ELF executable.

The importer rejects absolute paths, path traversal, duplicate paths, missing manifest payload files, oversized imports, and Box64 binaries with a different ELF architecture. The archive is extracted into a temporary staging directory and only compressed into the installed component catalog after validation succeeds.

## Runtime support boundary

The current Wine launcher executes Box64 from `/usr/local/bin/box64`. This feature therefore enables importing and selecting compatible **Box64** builds. Box86, FEX-Emu, Proton, and other runtimes need separate launch adapters and ABI integration before WinMali can safely select them as active execution runtimes; importing their files as though they were Box64 would not make them usable.
