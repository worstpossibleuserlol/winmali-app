# SDD ledger — plan: docs/superpowers/plans/2026-11-23-mali-black-screen.md

Base: `6fb14d2126c8260caea0cd2f83fe23904b442090`.

Ruling: Keep the Mali WineD3D renderer default at Vulkan — the value is present in WinMali's WineD3D UI and the bug found is in the Android Vulkan/OpenGL shared-buffer contract, not evidence that the WineD3D renderer name is invalid. Cost if wrong: the renderer default itself could still be incompatible with the bundled Wine/Mesa versions; device test will decide.

Ruling: Do not add PostHog SDK or send telemetry — the connected project's schema reports no events seen in the last 30 days and no WinMali SDK is present in this Android project. Cost if wrong: we will miss production crash evidence until the user configures the app-side SDK and consents to diagnostics.

Task 1: RED observed — CI run 45189438089, commit 4dc0718: 183 tests completed and the two new GPUImageBufferUsageTest cases failed because GPU_SAMPLED_IMAGE was absent. This confirms the regression test reproduces the old allocation policy.

Task 2: implemented the combined GPU_SAMPLED_IMAGE + GPU_COLOR_OUTPUT / CPU_WRITE_OFTEN allocation policy; passed that usage mask across Java/JNI; added null/error handling for buffer allocation and CPU lock, EGL client-buffer/image creation, GL image attachment, and balanced AHardwareBuffer reference release.

Task 3: hardened Vortek's Vulkan import against missing/zero-sized AHardwareBuffers and checked the VkResult from vkGetAndroidHardwareBufferPropertiesANDROID. Hardened ring-buffer setup to check allocations before dereferencing, close partial file descriptors, and clean up partial setup; hardened RingBuffer_create against calloc failure.
