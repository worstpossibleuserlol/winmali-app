# WinMali Direct3D test

`TestDirect3D.cpp` is an original Direct3D 9 diagnostic for the WinMali Start Menu. It creates a windowed D3D9 HAL device using software vertex processing and draws four large, high-contrast colour regions. This is deliberately more visible than a small triangle on a dark background.

The diagnostic waits 24 frames before checking output, then verifies four backbuffer sample points on three consecutive frames. After PASS, it rechecks the pattern every 120 presented frames and restarts verification after a device reset. It explicitly selects the fixed-function diffuse-colour pipeline, disables inherited shader/texture and state combinations that could hide the panels, validates the locked-surface pitch before reading pixels, and supports X8R8G8B8, A8R8G8B8 and R5G6B5 backbuffers.

Results:
- **PASS** means all four expected colours were read from the backbuffer on three frames and Direct3D Present succeeded.
- **FAILED** means a Direct3D operation failed or a readable backbuffer contained the wrong colour (including black output).
- **INCONCLUSIVE** means the platform did not support one of the pixel-readback operations. The diagnostic continues drawing and presenting so the window can still be visually inspected. It clears any earlier pixel-verification count, so losing readback support after a previous PASS cannot leave a stale PASS result. It does not mislabel an unsupported readback as a black-screen failure or as a PASS.

The diagnostic logs details to `WinMali-TestDirect3D.log` in the Windows temporary folder. A later colour mismatch changes the result to FAILED; if the graphics API stops supporting readback, the result changes to INCONCLUSIVE. The executable is generated from source during the Android build; no downloaded or prebuilt third-party EXE is used.

Build prerequisite: Ubuntu/Debian `sudo apt-get install g++-mingw-w64-i686`. The Android Gradle `preBuild` task invokes `tools/testd3d/build.sh` automatically.

This tests Direct3D 9/WineD3D only. It does not establish Direct3D 10/11/12, DXVK, VKD3D, or physical Mali-GPU compatibility; the latter still requires running the build on a Mali device.
