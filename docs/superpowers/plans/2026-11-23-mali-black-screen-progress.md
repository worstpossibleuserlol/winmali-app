# SDD ledger — plan: docs/superpowers/plans/2026-11-23-mali-black-screen.md

Base: `6fb14d2126c8260caea0cd2f83fe23904b442090`.

Ruling: Keep the Mali WineD3D renderer default at Vulkan — the value is present in WinMali's WineD3D UI and the bug found is in the Android Vulkan/OpenGL shared-buffer contract, not evidence that the WineD3D renderer name is invalid. Cost if wrong: the renderer default itself could still be incompatible with the bundled Wine/Mesa versions; device test will decide.

Ruling: Do not add PostHog SDK or send telemetry — the connected project's schema reports no events seen in the last 30 days and no WinMali SDK is present in this Android project. Cost if wrong: we will miss production crash evidence until the user configures the app-side SDK and consents to diagnostics.

Task 1: RED observed — CI run 45189438089, commit 4dc0718: 183 tests completed and the two new GPUImageBufferUsageTest cases failed because GPU_SAMPLED_IMAGE was absent. This confirms the regression test reproduces the old allocation policy.

Task 2: implemented the combined GPU_SAMPLED_IMAGE + GPU_COLOR_OUTPUT / CPU_WRITE_OFTEN allocation policy; passed that usage mask across Java/JNI; added null/error handling for buffer allocation and CPU lock, EGL client-buffer/image creation, GL image attachment, and balanced AHardwareBuffer reference release.

Task 3: hardened Vortek's Vulkan import against missing/zero-sized AHardwareBuffers and checked the VkResult from vkGetAndroidHardwareBufferPropertiesANDROID. Hardened ring-buffer setup to check allocations before dereferencing, close partial file descriptors, and clean up partial setup; hardened RingBuffer_create against calloc failure.

CI note: commit 8774d84 passed both sampled-image-positive assertions but exposed an inverted negative assertion in the newly added test. Corrected the test to assert that the CPU-write mask is zero for the GPU-only allocation. No production-code change was required for this test failure.

Final review: self-review (no subagent tool). Reviewed the GPUImage Java/JNI contract, EGL image lifetime, Vortek Java callback and Vulkan AHardwareBuffer import, plus partial VkContext ring-buffer allocation/cleanup. Corrected a stale helper comment. Build/CI verifies source compilation and tests only; actual Mali-G610 presentation remains unverified until installed and tested on-device.

Task 4: fixed Vulkan AHardwareBuffer ownership — each XWindowSwapchain now takes its own reference to the imported AHardwareBuffer and releases it only after destroying the Vulkan image and freeing imported memory. This prevents the Java drawable from freeing a buffer still referenced by the Vulkan swapchain.

Task 5: hardened low-memory swapchain creation — check swapchain/image/command-buffer allocations, handle surface-format allocation failure, prevent cleanup from dereferencing a null image array, and check VkContext allocation before field access.

Task 6: command-buffer allocation failure now frees its host-side handle array and clears the pointer before swapchain teardown, so cleanup cannot pass zero/uninitialized handles to Vulkan.
