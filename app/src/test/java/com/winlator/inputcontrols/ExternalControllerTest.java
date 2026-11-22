package com.winlator.inputcontrols;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExternalControllerTest {
    @Test
    public void unidentifiedControllersCanBeComparedSafely() {
        ExternalController first = new ExternalController();
        ExternalController second = new ExternalController();

        assertTrue(first.equals(first));
        assertFalse(first.equals(second));
        assertFalse(first.equals(null));
    }

    @Test
    public void controllerBindingInsertionIgnoresNull() {
        ExternalController controller = new ExternalController();
        controller.addControllerBinding(null);
        assertEquals(0, controller.getControllerBindingCount());
    }
}
