package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvVarsTest {
    @Test
    public void parsesEscapedSpacesAndPreservesWindowsBackslashes() {
        EnvVars vars = new EnvVars("APP_PATH=C:\\Program\\ Files\\Game FLAG=1");
        assertEquals("C:\\Program Files\\Game", vars.get("APP_PATH"));
        assertEquals("1", vars.get("FLAG"));
    }

    @Test
    public void ignoresMalformedEntriesInsteadOfCrashing() {
        EnvVars vars = new EnvVars();
        vars.putAll(new String[]{"VALID=1", "BROKEN", "=empty-name", null, "ALSO_VALID=2"});

        assertEquals("1", vars.get("VALID"));
        assertEquals("2", vars.get("ALSO_VALID"));
        assertFalse(vars.has("BROKEN"));
        assertFalse(vars.has(""));
    }

    @Test
    public void escapedSerializationRoundTripsValuesWithSpaces() {
        EnvVars original = new EnvVars();
        original.put("PATH_VALUE", "C:\\Program Files\\Game");
        original.put("FLAG", "yes");

        EnvVars restored = new EnvVars(original.toEscapedString());
        assertEquals(original.get("PATH_VALUE"), restored.get("PATH_VALUE"));
        assertEquals(original.get("FLAG"), restored.get("FLAG"));
    }

    @Test
    public void toleratesNullCopyAndRepeatedWhitespace() {
        EnvVars vars = new EnvVars("A=1   B=2");
        vars.putAll((EnvVars)null);

        assertEquals("1", vars.get("A"));
        assertEquals("2", vars.get("B"));
        assertTrue(vars.has("A"));
    }
}
