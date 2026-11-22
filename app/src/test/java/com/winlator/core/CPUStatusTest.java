package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CPUStatusTest {
    @Test
    public void parsesCelsiusAndDeciCelsius() {
        assertEquals(42, CPUStatus.parseTemperature("42"));
        assertEquals(42, CPUStatus.parseTemperature("420"));
    }

    @Test
    public void parsesCentiCelsiusReadings() {
        assertEquals(42, CPUStatus.parseTemperature("4200"));
        assertEquals(85, CPUStatus.parseTemperature("8500"));
    }

    @Test
    public void parsesMilliCelsiusAndRejectsInvalidReadings() {
        assertEquals(42, CPUStatus.parseTemperature("42000"));
        assertEquals(-1, CPUStatus.parseTemperature("not-a-number"));
        assertEquals(-1, CPUStatus.parseTemperature("0"));
        assertEquals(-1, CPUStatus.parseTemperature("200000"));
    }
}
