package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetworkHelperTest {
    @Test
    public void formatsArbitraryIpv4PrefixLengths() {
        assertEquals("255.255.128.0", NetworkHelper.formatNetmask(17));
        assertEquals("255.255.255.254", NetworkHelper.formatNetmask(31));
        assertEquals("0.0.0.0", NetworkHelper.formatNetmask(0));
        assertEquals("", NetworkHelper.formatNetmask(33));
    }

    @Test
    public void formatsIpv6PrefixLengths() {
        assertEquals("ffff:ffff:ffff:ffff:0:0:0:0", NetworkHelper.formatNetmask(64, true));
        assertEquals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", NetworkHelper.formatNetmask(128, true));
        assertEquals("", NetworkHelper.formatNetmask(129, true));
    }
}
