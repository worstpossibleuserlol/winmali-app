# Runtime Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-app Runtime Center to WinMali 2.0.0 that lists, downloads, verifies, installs, selects, updates, and removes Android Wine-family runtime packages.

**Architecture:** A small immutable catalog defines four pinned runtime variants. A digest verifier checks downloaded assets before they are kept. A manager orchestrates downloads and hands compatible x86_64 archives to the existing Wine installer; the UI is a dedicated Runtime Center surface linked from Settings. ARM64EC packages are retained after verification but not selectable until an ARM64EC/FEX launcher exists.

**Tech Stack:** Java, AndroidX/Material UI, SharedPreferences, existing HttpUtils, TarCompressorUtils, WineInstaller, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-23-runtime-center-design.md`

## Global Constraints

- Preserve `versionCode 38`, `versionName "2.0.0"`, and `WinMali_2.0.0.apk`.
- Keep the work on `feature/runtime-center-2.0.0`, based on beta commit `3c3b2f3528dd0df1bc4ed8bc4f95832de1832f6b`.
- Keep the bundled Wine as a non-removable fallback.
- Only allow runtime selection after successful download, digest/size validation, archive extraction, Wine binary validation, and installation.
- ARM64EC is download-only on this code branch because the existing launcher is Box64-based and no FEXCore/ARM64EC execution adapter is present.
- Use pinned HTTPS release asset URLs and exact SHA-256 values; no runtime payload is bundled into the APK.
- Never silently rewrite the Wine version of existing containers.

## Review Focus

- A release asset returning a truncated or oversized body must be deleted rather than shown as downloaded.
- A correct-size archive with the wrong digest must be rejected.
- A malformed or path-traversal archive must fail extraction without escaping the staging directory.
- The same Wine version in two architectures or families must not collide in the installed-runtime identifier.
- An ARM64EC download must never be offered as selectable merely because the archive passed integrity checks.

---

### Task 1: Runtime catalog model

**Files:**
- Create: `app/src/main/java/com/winlator/core/RuntimeCenterCatalog.java`
- Test: `app/src/test/java/com/winlator/core/RuntimeCenterCatalogTest.java`

**Interfaces:**
- Produces immutable `RuntimeCenterCatalog.Runtime` records with `id`, `displayName`, `family`, `version`, `guestArchitecture`, `assetUrl`, `releaseUrl`, `fileName`, `fileSizeBytes`, `sha256`, `archiveType`, `supportedOnCurrentLauncher`, and `warning`.
- Provides `getRuntimes()`, `getRuntime(String id)`, and `isSelectable(Runtime runtime, boolean installed)`.

- [ ] **Step 1: Write failing catalog tests** for exactly four stable IDs:
  - `proton-wine-11.0-2-x86_64`
  - `proton-wine-11.0-2-arm64ec`
  - `cachyos-wine-11.0-20261005-x86_64`
  - `cachyos-wine-11.0-20261005-arm64ec`
  Assert that only installed x86_64 entries are selectable and that architecture labels are not conflated with Android host ABI.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'com.winlator.core.RuntimeCenterCatalogTest'`; expected initial failure because the class is absent.
- [ ] **Step 3: Implement** the four pinned entries from the design spec. Proton Wine assets use the Winlator-oriented `.wcp.xz` assets; CachyOS Wine assets use the Android/Bionic `.wcp` assets. Mark both ARM64EC entries as unsupported on this launcher.
- [ ] **Step 4: Run** the focused unit test; expected PASS.
- [ ] **Step 5: Commit** `feat: add pinned runtime catalog`.

### Task 2: Download integrity verifier

**Files:**
- Create: `app/src/main/java/com/winlator/core/RuntimePackageVerifier.java`
- Test: `app/src/test/java/com/winlator/core/RuntimePackageVerifierTest.java`

**Interfaces:**
- `static boolean verify(File file, long expectedSize, String expectedSha256)`
- `static String sha256(File file)`
- The verifier returns false for null/missing/empty files, invalid digest strings, size mismatches, read failures, or digest mismatches.

- [ ] **Step 1: Write failing tests** for matching file+digest, wrong digest, wrong size, missing file, empty file, and malformed expected digest.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'com.winlator.core.RuntimePackageVerifierTest'`; expected initial failure.
- [ ] **Step 3: Implement** streaming SHA-256 with a fixed buffer and constant-time comparison of decoded digest bytes; do not read the full runtime archive into memory.
- [ ] **Step 4: Run** the focused unit test; expected PASS.
- [ ] **Step 5: Commit** `feat: verify downloaded runtime archives`.

### Task 3: Runtime download and storage manager

**Files:**
- Create: `app/src/main/java/com/winlator/core/RuntimeCenterManager.java`
- Modify: `app/src/main/java/com/winlator/core/WineInstaller.java`
- Test: `app/src/test/java/com/winlator/core/RuntimeCenterManagerTest.java`

**Interfaces:**
- `RuntimeCenterManager(Context context)`
- `File getDownloadFile(Runtime runtime)`
- `boolean isDownloaded(Runtime runtime)`
- `void download(Activity activity, Runtime runtime, Callback<Boolean> callback)`
- `void deleteDownload(Runtime runtime)`
- `boolean canActivate(Runtime runtime)`
- Downloads use `HttpUtils.download`; validation uses Task 2; extraction uses `TarCompressorUtils`.
- Add `WineInstaller.extractWineFileForInstallAsync(Context, File, Callback<File>)` as a file-backed companion to the URI overload.

- [ ] **Step 1: Write failing tests** for stable per-runtime destinations, unsupported-runtime activation, and download-file cleanup decisions. Keep filesystem paths under a temporary test directory.
- [ ] **Step 2: Run** the focused test; expected initial failure.
- [ ] **Step 3: Implement** an app-private `files/compatibility-runtimes/downloads` store. Download into a temporary destination; check exact size and SHA-256; delete invalid results; only then publish the runtime as downloaded.
- [ ] **Step 4: Add** the `WineInstaller` File overload using `TarCompressorUtils.detectType` from the file header and the existing safe extractor, retaining the current extraction limits.
- [ ] **Step 5: Run** focused and existing Wine installer tests; expected PASS.
- [ ] **Step 6: Commit** `feat: add runtime download manager`.

### Task 4: Runtime Center UI

**Files:**
- Create: `app/src/main/res/layout/runtime_center_dialog.xml`
- Modify: `app/src/main/java/com/winlator/SettingsFragment.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/settings_fragment.xml`

**Interfaces:**
- Settings button opens `showRuntimeCenter(Context)`.
- Runtime rows display engine, release, architecture, file size, source, warning, and state.
- Row actions are Download, Install, Set as default, Remove, or disabled with an explanatory reason.

- [ ] **Step 1: Add** string/layout resources for Runtime Center, status labels, upstream warnings, and unsupported ARM64EC explanation.
- [ ] **Step 2: Replace** the old static Compatibility Runtime Bundle alert with the Runtime Center surface. Preserve the separate manual Wine import path.
- [ ] **Step 3: Implement** status refresh after every download/install/remove action; do not claim an action succeeded until its callback confirms success.
- [ ] **Step 4: Verify** resource IDs, layout inflation, and Settings event wiring through Android unit/resource build tasks.
- [ ] **Step 5: Commit** `feat: add Runtime Center settings UI`.

### Task 5: Install, selection, and container defaults

**Files:**
- Modify: `app/src/main/java/com/winlator/core/RuntimeCenterManager.java`
- Modify: `app/src/main/java/com/winlator/core/WineInfo.java`
- Modify: `app/src/main/java/com/winlator/SettingsFragment.java`
- Modify: the existing container creation/default Wine selection path identified from the beta source before editing
- Test: `app/src/test/java/com/winlator/core/RuntimeCenterSelectionTest.java`

**Interfaces:**
- Persist the selected runtime by stable runtime ID.
- Resolve a selected runtime to its installed `WineInfo.identifier()`.
- Existing containers retain their own `wine_version`.
- Runtime identifiers include family and architecture so two builds cannot overwrite each other.

- [ ] **Step 1: Write failing tests** for runtime ID uniqueness, uninstalled selection rejection, ARM64EC selection rejection, and preserving an existing container's Wine identifier.
- [ ] **Step 2: Implement** x86_64 install through the existing Wine version detection and Wine-prefix generation path. Give each family/architecture a unique installation identifier while preserving the detected Wine executable version.
- [ ] **Step 3: Implement** default selection for new containers only. Keep the bundled Wine as fallback until the Proton x86_64 entry is actually installed; never modify existing container settings.
- [ ] **Step 4: Implement** removal guards for any runtime referenced by a container; the bundled runtime is never removable.
- [ ] **Step 5: Run** focused selection tests and existing container/Wine tests; expected PASS.
- [ ] **Step 6: Commit** `feat: install and select managed runtimes`.

### Task 6: Documentation, CI, and branch review

**Files:**
- Create: `docs/runtime-center.md`
- Modify: `docs/compatibility-runtime-bundle.md`
- Test: existing CI workflow and all relevant unit tests

- [ ] **Step 1: Document** the four catalog packages, sources, integrity rules, launcher compatibility, and ARM64EC limitation.
- [ ] **Step 2: Verify** version metadata is still 2.0.0 and the APK filename is unchanged.
- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`; expected PASS.
- [ ] **Step 4: Inspect** the final diff for stale runtime dates, unsupported package formats, archive traversal, missing fallback behaviour, and accidental 2.0.1 changes.
- [ ] **Step 5: Commit** `docs: document Runtime Center support`.
- [ ] **Step 6: Open** a pull request from `feature/runtime-center-2.0.0` to `beta`; leave PR #15 untouched.
