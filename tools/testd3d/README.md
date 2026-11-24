# WinMali Direct3D test

`TestDirect3D.cpp` is an original, minimal Direct3D 9 diagnostic for the WinMali Start Menu. It creates a windowed D3D9 HAL device with software vertex processing, clears the backbuffer, draws an animated coloured triangle, presents frames, then reads back the backbuffer and checks that the expected coloured triangle reached the centre pixel. It reports the failing stage, HRESULT, or observed pixel in a Windows message box and writes `WinMali-TestDirect3D.log` to the Windows temporary folder.

The executable is generated from source during the Android build. No downloaded or prebuilt third-party EXE is used.

Build prerequisite: Ubuntu/Debian `sudo apt-get install g++-mingw-w64-i686`. The Android Gradle `preBuild` task invokes `tools/testd3d/build.sh` automatically.

This tests Direct3D 9/WineD3D only. It does not claim Direct3D 10/11/12, DXVK, or VKD3D compatibility.
