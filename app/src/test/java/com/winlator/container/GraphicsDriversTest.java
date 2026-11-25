package com.winlator.container;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class GraphicsDriversTest {
    @Test
    public void defaultsWhenConfigurationIsNullOrEmpty() {
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers(null));
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers(""));
    }

    @Test
    public void parsesCompleteDriverPair() {
        assertArrayEquals(new String[]{GraphicsDrivers.VORTEK, GraphicsDrivers.VIRGL},
            GraphicsDrivers.parseIdentifiers("vortek,virgl"));
    }

    @Test
    public void restoresMissingOpenGLDriverInIncompletePair() {
        assertArrayEquals(new String[]{GraphicsDrivers.VORTEK, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers("vortek,"));
    }

    @Test
    public void restoresBothDefaultsForUnknownOrMalformedPair() {
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.VIRGL},
            GraphicsDrivers.parseIdentifiers(",virgl"));
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers("unknown,invalid"));
    }

    @Test
    public void usesVulkanAndBackbufferForMaliWineD3D() {
        assertEquals("vulkan", GraphicsDrivers.getDefaultWineD3DRenderer(true));
        assertEquals("backbuffer", GraphicsDrivers.getDefaultWineD3DOffscreenRenderingMode(true));
    }

    @Test
    public void keepsOpenGLAndFboDefaultsForNonMaliWineD3D() {
        assertEquals("gl", GraphicsDrivers.getDefaultWineD3DRenderer(false));
        assertEquals("fbo", GraphicsDrivers.getDefaultWineD3DOffscreenRenderingMode(false));
    }

    @Test
    public void preservesLegacySingleDriverSelections() {
        assertArrayEquals(new String[]{GraphicsDrivers.TURNIP, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers("turnip"));
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.ZINK},
            GraphicsDrivers.parseIdentifiers("zink"));
    }
}
