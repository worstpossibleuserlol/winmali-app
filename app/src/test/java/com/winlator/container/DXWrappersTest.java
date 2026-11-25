package com.winlator.container;

import org.junit.Test;

import static org.junit.Assert.*;

public class DXWrappersTest {
    @Test
    public void preservesVKD3DAsASelectableWrapper() {
        assertEquals(DXWrappers.VKD3D, DXWrappers.parseIdentifier(DXWrappers.VKD3D));
    }

    @Test
    public void preservesWineD3DAndDXVK() {
        assertEquals(DXWrappers.WINED3D, DXWrappers.parseIdentifier(DXWrappers.WINED3D));
        assertEquals(DXWrappers.DXVK, DXWrappers.parseIdentifier(DXWrappers.DXVK));
    }

    @Test
    public void fallsBackSafelyForNullOrUnknownWrapper() {
        assertEquals(Container.DEFAULT_DXWRAPPER, DXWrappers.parseIdentifier(null));
        assertEquals(Container.DEFAULT_DXWRAPPER, DXWrappers.parseIdentifier("unknown"));
        assertEquals("None", DXWrappers.getName(null));
    }

    @Test
    public void resolvesBlankComponentVersionsToTheirDefaults() {
        assertEquals("2.4.1", DXWrappers.resolveVersion(null, "2.4.1"));
        assertEquals("2.4.1", DXWrappers.resolveVersion("", "2.4.1"));
        assertEquals("2.4.1", DXWrappers.resolveVersion("   ", "2.4.1"));
        assertEquals("1.10.3", DXWrappers.resolveVersion(" 1.10.3 ", "2.4.1"));
    }

    @Test
    public void usesNewInstallCacheSchemaToInvalidateUncheckedOlderMarkers() {
        String cacheId = DXWrappers.createInstallCacheId(
            DXWrappers.DXVK, "2.4.1", "2.14.1", DXWrappers.WINED3D);
        assertEquals("direct3d-v2|dxvk|2.4.1|2.14.1|wined3d", cacheId);
    }

    @Test
    public void refusesToCacheAnIncompleteDirect3DInstallation() {
        assertTrue(DXWrappers.isInstallComplete(true, true, true));
        assertFalse(DXWrappers.isInstallComplete(false, true, true));
        assertFalse(DXWrappers.isInstallComplete(true, false, true));
        assertFalse(DXWrappers.isInstallComplete(true, true, false));
    }

    @Test
    public void parsesValidCustomGpuIdsAndRejectsMalformedSavedValues() {
        assertArrayEquals(new String[]{"1002", "73ff", "AMD Radeon"},
            DXWrappers.parseCustomDevice("1002:73ff:AMD Radeon"));
        assertNull(DXWrappers.parseCustomDevice(null));
        assertNull(DXWrappers.parseCustomDevice(""));
        assertNull(DXWrappers.parseCustomDevice(":"));
        assertNull(DXWrappers.parseCustomDevice("1002:"));
        assertNull(DXWrappers.parseCustomDevice("xyz:73ff:GPU"));
        assertNull(DXWrappers.parseCustomDevice("1002:73ff:"));
        assertNull(DXWrappers.parseCustomDevice("1002:73ff:GPU\nDXVK_CONFIG=bad"));
        assertNull(DXWrappers.parseCustomDevice("1002:73ff:\"GPU\""));
    }
}
