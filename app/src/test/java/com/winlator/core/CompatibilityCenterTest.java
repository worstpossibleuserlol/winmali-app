package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class CompatibilityCenterTest {
    private byte[] peImage(int machine) {
        byte[] image = new byte[256];
        image[0] = 'M';
        image[1] = 'Z';
        image[0x3c] = (byte)0x80;
        image[0x80] = 'P';
        image[0x81] = 'E';
        image[0x82] = 0;
        image[0x83] = 0;
        image[0x84] = (byte)(machine & 0xff);
        image[0x85] = (byte)((machine >>> 8) & 0xff);
        return image;
    }

    @Test
    public void detectsX86AndX64PeExecutables() {
        assertEquals(CompatibilityCenter.ExecutableArchitecture.X86,
            CompatibilityCenter.detectPEArchitecture(peImage(0x014c)));
        assertEquals(CompatibilityCenter.ExecutableArchitecture.X86_64,
            CompatibilityCenter.detectPEArchitecture(peImage(0x8664)));
    }

    @Test
    public void detectsArm64AndArm64EcWithoutEnablingArm64EcExecution() {
        assertEquals(CompatibilityCenter.ExecutableArchitecture.ARM64,
            CompatibilityCenter.detectPEArchitecture(peImage(0xaa64)));
        assertEquals(CompatibilityCenter.ExecutableArchitecture.ARM64EC,
            CompatibilityCenter.detectPEArchitecture(peImage(0xa641)));
    }

    @Test
    public void rejectsMalformedOrUnknownPeHeaders() {
        assertEquals(CompatibilityCenter.ExecutableArchitecture.UNKNOWN,
            CompatibilityCenter.detectPEArchitecture(new byte[128]));
        assertEquals(CompatibilityCenter.ExecutableArchitecture.UNKNOWN,
            CompatibilityCenter.detectPEArchitecture(peImage(0x1234)));
        byte[] invalid = peImage(0x8664);
        invalid[0x80] = 'X';
        assertEquals(CompatibilityCenter.ExecutableArchitecture.UNKNOWN,
            CompatibilityCenter.detectPEArchitecture(invalid));
    }

    @Test
    public void crashAnalyzerHighlightsVulkanDeviceLossAndSafeFallback() {
        String report = CompatibilityCenter.analyzeCrashLog("VK_ERROR_DEVICE_LOST while creating swapchain");
        assertTrue(report.contains("WineD3D/OpenGL"));
        assertTrue(report.contains("not a confirmed"));
    }

    @Test
    public void crashAnalyzerHighlightsBox64TranslationFailures() {
        String report = CompatibilityCenter.analyzeCrashLog("Box64: unsupported instruction");
        assertTrue(report.contains("Stability or Conservative"));
    }

    @Test
    public void direct3dDependencySetsAreArchitectureIndependentNames() {
        assertArrayEquals(new String[]{"d3d11.dll", "dxgi.dll"},
            CompatibilityCenter.getDirect3DDependencies(11));
        assertArrayEquals(new String[0], CompatibilityCenter.getDirect3DDependencies(13));
    }
}
