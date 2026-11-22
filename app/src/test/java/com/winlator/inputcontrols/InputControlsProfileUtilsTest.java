package com.winlator.inputcontrols;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class InputControlsProfileUtilsTest {
    @Test
    public void acceptsAWellFormedProfileEnvelope() throws Exception {
        JSONObject data = new JSONObject()
            .put("id", 12)
            .put("name", "My controls");
        assertTrue(InputControlsProfileUtils.isValidImportDocument(data));
    }

    @Test
    public void rejectsNullMissingAndWronglyTypedProfileFields() throws Exception {
        assertFalse(InputControlsProfileUtils.isValidImportDocument(null));
        assertFalse(InputControlsProfileUtils.isValidImportDocument(new JSONObject().put("id", 1)));
        assertFalse(InputControlsProfileUtils.isValidImportDocument(
            new JSONObject().put("id", 1).put("name", JSONObject.NULL)));
        assertFalse(InputControlsProfileUtils.isValidImportDocument(
            new JSONObject().put("id", "1").put("name", "Controls")));
        assertFalse(InputControlsProfileUtils.isValidImportDocument(
            new JSONObject().put("id", -1).put("name", "Controls")));
        assertFalse(InputControlsProfileUtils.isValidImportDocument(
            new JSONObject().put("id", 1.5).put("name", "Controls")));
    }

    @Test
    public void rejectsBlankAndOverlongNames() throws Exception {
        assertFalse(InputControlsProfileUtils.isValidImportDocument(
            new JSONObject().put("id", 1).put("name", "   ")));
        assertFalse(InputControlsProfileUtils.isValidImportDocument(
            new JSONObject().put("id", 1).put("name", repeat('x', 129))));
    }

    @Test
    public void exportFilenameCannotEscapeTheProfileExportDirectory() {
        String filename = InputControlsProfileUtils.safeExportFileName("../../outside\\..\\payload", 7);
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains("\\"));
        assertFalse(filename.contains(".."));
        assertTrue(filename.endsWith(".icp"));
    }

    @Test
    public void unusableExportNamesFallBackToTheProfileId() {
        assertEquals("Controls-9.icp", InputControlsProfileUtils.safeExportFileName(" /// ", 9));
        assertEquals("Controls-0.icp", InputControlsProfileUtils.safeExportFileName(null, 0));
    }

    @Test
    public void exportFilenameIsBounded() {
        assertEquals(104, InputControlsProfileUtils.safeExportFileName(repeat('n', 160), 1).length());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
