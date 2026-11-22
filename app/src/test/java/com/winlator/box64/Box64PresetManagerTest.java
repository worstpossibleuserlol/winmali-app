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
    public void preservesDelimiterTextInsideEnvironmentVariableField() {
        assertArrayEquals(new String[]{"CUSTOM-3", "Custom", "A=B|C=D"},
            Box64PresetManager.parseCustomPresetEntry("CUSTOM-3|Custom|A=B|C=D"));
    }
}
