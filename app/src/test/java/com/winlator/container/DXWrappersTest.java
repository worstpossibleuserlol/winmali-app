package com.winlator.container;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
