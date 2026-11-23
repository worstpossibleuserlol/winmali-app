package com.winlator.renderer;

/**
 * Android hardware-buffer usage policy for WinMali's shared Vulkan/OpenGL images.
 *
 * The values mirror the Android NDK AHardwareBuffer usage flags.
 */
public final class GPUImageBufferUsage {
    static final long GPU_SAMPLED_IMAGE = 1L << 8;
    static final long GPU_COLOR_OUTPUT = 1L << 9;
    static final long CPU_WRITE_OFTEN = 3L << 4;

    private GPUImageBufferUsage() {}

    /**
     * The shared Vortek image is sampled by the host OpenGL display renderer
     * after Vulkan renders into it. CPU-accessible images retain CPU write
     * access and must still be declared as GPU-sampleable.
     */
    static long forCpuAccess(boolean cpuAccess) {
        return GPU_SAMPLED_IMAGE | (cpuAccess ? CPU_WRITE_OFTEN : GPU_COLOR_OUTPUT);
    }
}
