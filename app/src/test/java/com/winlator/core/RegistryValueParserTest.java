package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RegistryValueParserTest {
    @Test
    public void parsesMaximumUnsignedDwordWithoutOverflow() {
        assertEquals(Integer.valueOf(-1), RegistryValueParser.parseDword("dword:ffffffff", null));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE),
            RegistryValueParser.parseDword("dword:80000000", null));
    }

    @Test
    public void parsesPositiveDwordValues() {
        assertEquals(Integer.valueOf(0), RegistryValueParser.parseDword("dword:00000000", null));
        assertEquals(Integer.valueOf(2147483647),
            RegistryValueParser.parseDword("dword:7fffffff", null));
    }

    @Test
    public void returnsFallbackForMalformedRegistryValues() {
        assertNull(RegistryValueParser.parseDword(null, null));
        assertNull(RegistryValueParser.parseDword("binary:ffffffff", null));
        assertEquals(Integer.valueOf(7), RegistryValueParser.parseDword("dword:xyz", 7));
        assertEquals(Integer.valueOf(7), RegistryValueParser.parseDword("dword:100000000", 7));
        assertEquals(Integer.valueOf(7), RegistryValueParser.parseDword("dword:", 7));
    }
}
