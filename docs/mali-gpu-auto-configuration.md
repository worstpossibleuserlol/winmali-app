# Mali-first GPU auto-configuration

WinMali 2.0.0 beta can pre-fill graphics settings for a newly created container using the detected device GPU and Vulkan capabilities.

## Behavior

- GPU detection runs on a worker thread so EGL/Vulkan probing does not block the Android UI.
- Mali/Immortalis devices use the Vortek Vulkan path and Gladio OpenGL path as the conservative starting profile.
- Turnip is only recommended when a recognizable Adreno model is detected. Mali, PowerVR, and unknown GPUs never receive Turnip automatically.
- DXVK is recommended only when Vulkan 1.1 or newer and `VK_KHR_swapchain` are both reported. Otherwise WineD3D is selected as the conservative Direct3D fallback.
- VKD3D is never auto-selected. A small baseline capability check is shown for diagnostics, but a full Vulkan feature query is needed before treating DirectX 12 as supported.
- Automatic recommendations only pre-fill a new container. The user can turn automatic configuration off or change any driver/wrapper manually before saving.
- Existing containers retain their saved graphics choices. They are not silently reconfigured.

## Detection limits

Android vendors expose different EGL, Vulkan, and sysfs information. When renderer data, Vulkan API version, or extensions are not available, WinMali falls back conservatively and does not infer unsupported driver capabilities. GPU utilization telemetry may also remain unavailable on devices that restrict sysfs access.

## Tests

The GPU profile unit tests cover Mali Turnip exclusion, Adreno Turnip selection, DXVK Vulkan/swapchain gating, unknown-GPU fallback, and conservative VKD3D handling.
