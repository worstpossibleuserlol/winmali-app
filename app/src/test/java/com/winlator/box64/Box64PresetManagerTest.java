package com.winlator.box64;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class Box64PresetManagerTest {
    @Test
    public void parsesValidCustomPresetEntries() {
        assertArrayEquals(new String[]{"CUSTOM-12", "Balanced", "BOX64_DYNAREC_SAFEFLAGS=2"},
            Box64PresetManager.parseCustomPresetEntry(
                "CUSTOM-12|Balanced|BOX64_DYNAREC_SAFEFLAGS=2"));
    }

    @Test
    public void rejectsMissingFieldsAndEmptyNames() {
        assertNull(Box64PresetManager.parseCustomPresetEntry("CUSTOM-2"));
        assertNull(Box64PresetManager.parseCustomPresetEntry("CUSTOM-2||BOX64_DYNAREC_SAFEFLAGS=2"));
        assertNull(Box64PresetManager.parseCustomPresetEntry(""));
        assertNull(Box64PresetManager.parseCustomPresetEntry(null));
    }

    @Test
    public void rejectsMalformedOrPathLikePresetIds() {
        assertNull(Box64PresetManager.parseCustomPresetEntry("CUSTOM-abc|Fast|A=B"));
        assertNull(Box64PresetManager.parseCustomPresetEntry("CUSTOM-0|Fast|A=B"));
        assertNull(Box64PresetManager.parseCustomPresetEntry("CUSTOM-1/../2|Fast|A=B"));
        assertNull(Box64PresetManager.parseCustomPresetEntry("OTHER-1|Fast|A=B"));
    }

    @Test
    public void roundTripsPresetNamesAndValuesContainingStorageDelimiters() {
        String name = "Balanced, fast | stable";
        String envVars = "BOX64_LOG=1 BOX64_CUSTOM=value,with|separators";
        String encoded = "B64|CUSTOM-4|"+
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"|"+
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(envVars.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertArrayEquals(new String[]{"CUSTOM-4", name, envVars},
            Box64PresetManager.parseCustomPresetEntry(encoded));
    }

    @Test
    public void serializerPreservesDelimiterTextAndRoundTrips() {
        String[] preset = new String[]{"CUSTOM-8", "Balanced, fast | stable", "A=one two,B=three|four"};
        String serialized = Box64PresetManager.serializeCustomPresetEntry(preset);
        assertArrayEquals(preset, Box64PresetManager.parseCustomPresetEntry(serialized));
    }

    @Test
    public void serializerRejectsInvalidEntriesAndOversizedFields() {
        assertNull(Box64PresetManager.serializeCustomPresetEntry(null));
        assertNull(Box64PresetManager.serializeCustomPresetEntry(new String[]{"CUSTOM-0", "No", "A=B"}));
        assertNull(Box64PresetManager.serializeCustomPresetEntry(
            new String[]{"CUSTOM-1", "Name", new String(new char[65537]).replace('\0', 'x')}));
    }

    @Test
    public void rejectsCorruptedEncodedPresetFields() {
        assertNull(Box64PresetManager.parseCustomPresetEntry("B64|CUSTOM-4|%%%|%%%"));
    }

    @Test
    public void preservesDelimiterTextInsideEnvironmentVariableField() {
        assertArrayEquals(new String[]{"CUSTOM-3", "Custom", "A=B|C=D"},
            Box64PresetManager.parseCustomPresetEntry("CUSTOM-3|Custom|A=B|C=D"));
    }
}
