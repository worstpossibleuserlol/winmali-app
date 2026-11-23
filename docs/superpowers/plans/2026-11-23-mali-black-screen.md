# WinMali Mali rendering and black-screen fix

## Baseline
- Repository: `worstpossibleuserlol/winmali-app`
- Branch: `feature/runtime-center-2.0.0`
- Base commit: `6fb14d2126c8260caea0cd2f83fe23904b442090`
- Device target: Mali-G610 MC2 / Android 16 (real-device verification remains required).

## Root-cause hypothesis confirmed by source path
The WineD3D automatic Mali profile selects the Vulkan renderer. Vortek exposes each guest window through an Android HardwareBuffer, then the host OpenGL ES renderer samples that same buffer as a texture. The allocation currently requests GPU color-output usage only (or CPU-write-only usage), omitting GPU sampled-image usage. That does not declare the buffer's second, read-as-texture use to Android's allocator.

The NDK distinguishes GPU color-output and GPU sampled-image usage. The allocation must request both for the GPU-only Vortek path, and sampled-image plus CPU-write for the CPU-accessible path.

## Tasks
1. Add a failing JUnit regression test for both allocation policies.
2. Correct the usage masks and pass the policy through the Java/JNI boundary.
3. Make AHardwareBuffer allocation, lock, EGLImage import and teardown fail safely; balance the AHardwareBuffer reference acquired for an EGLImage.
4. Make the Vortek window-buffer callback handle an allocation failure instead of dereferencing a null image.
5. Audit the renderer/display and Direct3D install path for additional provable defects; add regression tests for fixes.
6. Run the repository's exact CI test/build workflow, inspect the result, and self-review the final diff.

## Constraints
- Keep WinMali version 2.0.0 and preserve Runtime Center work.
- Do not change renderer defaults without evidence.
- Do not claim actual Mali-device rendering is verified by JVM tests or cloud CI.
- Do not add PostHog instrumentation without a configured WinMali project SDK/token and explicit telemetry consent.
- Preserve all existing containers and user data.
