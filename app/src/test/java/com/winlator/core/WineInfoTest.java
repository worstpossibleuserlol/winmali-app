package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WineInfoTest {
    @Test
    public void preservesTaggedVersionIdentifiers() {
        assertEquals("10.10-rc1", WineInfo.parseVersionIdentifier("wine-10.10-rc1"));
        assertEquals("10.10+staging.2", WineInfo.parseVersionIdentifier("wine-10.10+staging.2"));
    }

    @Test
    public void preservesLegacyNumericSubversionInIdentifier() {
        assertEquals("10.10-1", WineInfo.parseVersionIdentifier("wine-10.10-1"));
    }

    @Test
    public void rejectsUnsafeOrMalformedIdentifiers() {
        assertNull(WineInfo.parseVersionIdentifier("wine-../outside"));
        assertNull(WineInfo.parseVersionIdentifier("wine-latest"));
        assertNull(WineInfo.parseVersionIdentifier("other-10.10"));
    }

    @Test
    public void parsesTaggedWineVersionOutput() {
        assertEquals("10.10-rc1", WineInfo.parseVersionOutput("wine-10.10-rc1"));
        assertEquals("10.10+staging.2", WineInfo.parseVersionOutput("wine-10.10+staging.2 (Staging)"));
    }

    @Test
    public void parsesNumericWineVersionOutput() {
        assertEquals("10.10", WineInfo.parseVersionOutput("wine-10.10"));
        assertEquals("10.10.1", WineInfo.parseVersionOutput("wine-10.10.1"));
    }

    @Test
    public void rejectsMalformedWineVersionOutput() {
        assertNull(WineInfo.parseVersionOutput("wine-latest"));
        assertNull(WineInfo.parseVersionOutput("starting wine-rc1"));
    }
}
