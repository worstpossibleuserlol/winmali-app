package com.winlator.container;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

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
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers(",virgl"));
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers("unknown,invalid"));
    }

    @Test
    public void preservesLegacySingleDriverSelections() {
        assertArrayEquals(new String[]{GraphicsDrivers.TURNIP, GraphicsDrivers.DEFAULT_OPENGL_DRIVER},
            GraphicsDrivers.parseIdentifiers("turnip"));
        assertArrayEquals(new String[]{GraphicsDrivers.DEFAULT_VULKAN_DRIVER, GraphicsDrivers.ZINK},
            GraphicsDrivers.parseIdentifiers("zink"));
    }
}
