package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GPUStatusTest {
    @Test
    public void parsesSinglePercentValue() {
        assertEquals(73, GPUStatus.parseUtilization("73", false));
    }

    @Test
    public void parsesMaliStylePercentAndFrequency() {
        assertEquals(41, GPUStatus.parseUtilization("41@600000000", false));
    }

    @Test
    public void parsesBusyOverTotalCounters() {
        assertEquals(25, GPUStatus.parseUtilization("250 1000", true));
    }

    @Test
    public void rejectsInvalidOrUnavailableValues() {
        assertEquals(-1, GPUStatus.parseUtilization(null, false));
        assertEquals(-1, GPUStatus.parseUtilization("unknown", false));
        assertEquals(-1, GPUStatus.parseUtilization("140", false));
        assertEquals(-1, GPUStatus.parseUtilization("100 50", true));
        assertEquals(-1, GPUStatus.parseUtilization("20 0", true));
    }
}
