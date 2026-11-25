cmake_minimum_required(VERSION 3.22.1)

project(WinMaliNative LANGUAGES C CXX)

# NDK r24 defaults to 4 KB ELF load-segment alignment. Use 16 KB alignment
# for every native target built from this CMake project so it can load on
# Android devices configured with 16 KB memory pages.
add_link_options(
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

add_subdirectory(winlator)
add_subdirectory(vortekrenderer)
add_subdirectory(virglrenderer)
add_subdirectory(midihandler)
add_subdirectory(libadrenotools)
add_subdirectory(gladiorenderer)