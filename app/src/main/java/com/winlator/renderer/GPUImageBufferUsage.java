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
     * Initial implementation retained for the regression test: this deliberately
     * reflects the old allocation policy and is corrected in the follow-up fix.
     */
    static long forCpuAccess(boolean cpuAccess) {
        return GPU_SAMPLED_IMAGE | (cpuAccess ? CPU_WRITE_OFTEN : GPU_COLOR_OUTPUT);
    }
}
