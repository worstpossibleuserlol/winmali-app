# Per-shortcut compatibility profiles

WinMali's compatibility profiles are starting points for an individual Windows application shortcut. They do not change other shortcuts or the container's global settings. All selected settings remain editable.

## Profiles

| Profile | Graphics and Direct3D starting point | Box64 preset | Additional environment |
| --- | --- | --- | --- |
| Custom | Keep the shortcut's current settings | Keep current | Keep current |
| Compatibility | Detected device driver; WineD3D | Stability | `mesa_glthread=false` |
| Balanced | Detected device driver and recommended Direct3D wrapper | Intermediate | `mesa_glthread=false` |
| Performance | Detected device driver and recommended Direct3D wrapper | Performance | `mesa_glthread=true` |
| Experimental DXVK | Detected device driver; explicitly select DXVK | Intermediate | `mesa_glthread=false` |

The Performance preset uses more aggressive Box64 optimizations and may be less stable on some applications. Experimental DXVK is deliberately opt-in; it may fail to start or render incorrectly on some Mali devices.

## Device selection

- **Mali:** automatic recommendations use Vortek for Vulkan and Gladio for OpenGL. WineD3D is the conservative Direct3D starting point. Turnip is not selected.
- **Adreno:** Turnip may be recommended when the detected Vulkan API and swapchain capability meet the existing checks. DXVK can be recommended for this detected path.
- **Unknown or other GPUs:** use conservative Vortek/Gladio and WineD3D starting points instead of guessing that an Adreno-only driver is supported.

Manual shortcut selections are preserved by launch-time automatic fallback. The automatic fallback may adjust only inherited container defaults; explicit per-shortcut driver and wrapper selections remain in effect.

## What a profile cannot guarantee

Compatibility varies with the exact GPU model and Android driver, Wine and Box64 versions, available RAM, graphics API support, application dependencies, and the application's own requirements. A profile is not a compatibility certification or an FPS promise.

For troubleshooting, change one setting at a time and record the device, WinMali version, driver and wrapper selection, Box64 preset, application build, and relevant logs. Keep a known-working shortcut configuration before experimenting with the Experimental DXVK profile.
