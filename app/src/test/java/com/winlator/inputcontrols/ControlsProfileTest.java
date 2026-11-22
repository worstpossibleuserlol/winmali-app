package com.winlator.inputcontrols;

import org.junit.Test;

import static org.junit.Assert.*;

public class ControlsProfileTest {
    @Test
    public void missingProfileNameDoesNotCrashTemplateChecks() {
        ControlsProfile profile = new ControlsProfile(null, 3);
        assertFalse(profile.isTemplate());
    }

    @Test
    public void templateDetectionIsCaseInsensitive() {
        ControlsProfile profile = new ControlsProfile(null, 3);
        profile.setName("My TEMPLATE");
        assertTrue(profile.isTemplate());
    }

    @Test
    public void savingAnUninitializedProfileFailsWithoutThrowing() {
        ControlsProfile profile = new ControlsProfile(null, 3);
        profile.setName("Controls");
        assertFalse(profile.save());
    }

    @Test
    public void controllerInsertionIgnoresNullAndEmptyIds() {
        ControlsProfile profile = new ControlsProfile(null, 3);
        assertNull(profile.addController(null));
        assertNull(profile.addController(""));
    }
}
