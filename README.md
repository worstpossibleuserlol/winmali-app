# WinMali

WinMali is an Android application that lets you run Windows (x86_64) applications with Wine and Box86/Box64, with a development focus on graphics compatibility for Mali GPUs.
This repository stores the latest updates for the WinMali app source.
For more information and releases, please visit the main repository: https://github.com/worstpossibleuserlol/WinMali

## Compatibility features

- Per-shortcut compatibility profiles for different performance and stability starting points.
- Independently configurable graphics drivers, Direct3D wrappers, Box64 presets, and environment variables.
- Mali-aware defaults that avoid automatically selecting Adreno-only graphics drivers.
- Experimental graphics options remain opt-in; compatibility depends on the device, driver, runtime, and application.

Profiles are starting points, not compatibility guarantees. Individual shortcut settings remain editable.

# Credits and Third-party apps

- GLIBC Patches by [Termux Pacman](https://github.com/termux-pacman/glibc-packages)
- Wine ([winehq.org](https://www.winehq.org/))
- Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
- Mesa (Turnip/Zink/VirGL) ([mesa3d.org](https://www.mesa3d.org))
- DXVK ([github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk))
- VKD3D ([gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d))
- CNC DDraw ([github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw))

Special thanks to all the developers involved in these projects.
Thank you to all the people who believe in this project.
