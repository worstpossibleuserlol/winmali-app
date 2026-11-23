package com.winlator.renderer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GPUImageBufferUsageTest {
    @Test
    public void vulkanOutputBufferCanAlsoBeSampledByTheOpenGlDisplayRenderer() {
        long usage = GPUImageBufferUsage.forCpuAccess(false);

        assertTrue("Vulkan must be allowed to write the shared buffer",
            (usage & GPUImageBufferUsage.GPU_COLOR_OUTPUT) != 0);
        assertTrue("OpenGL must be allowed to sample the shared buffer as a texture",
            (usage & GPUImageBufferUsage.GPU_SAMPLED_IMAGE) != 0);
        assertFalse("The GPU-only path must not request CPU write access",
            (usage & GPUImageBufferUsage.CPU_WRITE_OFTEN) != GPUImageBufferUsage.CPU_WRITE_OFTEN);
    }

    @Test
    public void cpuAccessibleBufferCanAlsoBeSampledByTheOpenGlDisplayRenderer() {
        long usage = GPUImageBufferUsage.forCpuAccess(true);

        assertTrue("CPU access must retain its write permission",
            (usage & GPUImageBufferUsage.CPU_WRITE_OFTEN) == GPUImageBufferUsage.CPU_WRITE_OFTEN);
        assertTrue("CPU-accessible buffers are also consumed by the display renderer",
            (usage & GPUImageBufferUsage.GPU_SAMPLED_IMAGE) != 0);
    }
}
