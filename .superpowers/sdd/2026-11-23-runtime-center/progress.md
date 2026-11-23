# Runtime Center implementation progress

Plan: `docs/superpowers/plans/2026-11-23-runtime-center.md`

## Environment

- Implementation is occurring through the connected GitHub repository API because this session has no local checkout/worktree access. A local git clone was attempted but the execution environment could not resolve GitHub DNS.
- Verification uses GitHub Actions Android CI from the draft PR; no local Gradle tests or local APK build are claimed.
- Figma was excluded because the user explicitly asked not to use it.
- Replit was checked for an existing WinMali workspace; none was found, so no duplicate app was created.
- Notion and GitHub are available; the WinMali Lab page will be updated after implementation.

## TDD checkpoint

- [x] Added catalog and package-verifier unit tests before implementation.
- [x] Triggered draft PR CI and observed a red build at :app:compileDebugUnitTestJavaWithJavac because the two implementation classes did not exist.
- [x] Corrected the SHA-256 test fixture before implementation.
- [ ] Add implementation and run green CI.
- [ ] Verify APK artifact and version metadata from CI.
- [ ] Update the WinMali Lab Notion page.
- [ ] Self-review the diff and finalize the PR summary.

## Rulings

- A package becomes selectable only after download, integrity check, and installation.
- ARM64EC stays download-only because this 2.0.0 branch's active guest launcher uses Box64 and has no integrated FEXCore/ARM64EC execution adapter.
- Proton x86-64 is the preferred default for new containers, but new containers use bundled Wine until Proton is installed.
- Existing containers are unchanged; their Wine selector stays disabled in edit mode.
