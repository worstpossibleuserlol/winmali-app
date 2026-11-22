package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VulkanVersionTest {
    @Test
    public void packsAndUnpacksVersionComponents() {
        int version = VulkanVersion.makeVersion(1, 3, 280);
        assertEquals(1, VulkanVersion.major(version));
        assertEquals(3, VulkanVersion.minor(version));
        assertEquals(280, VulkanVersion.patch(version));
    }

    @Test
    public void parsesStrictVersionStrings() {
        assertEquals(VulkanVersion.makeVersion(1, 3, 0), VulkanVersion.makeVersion("1.3"));
        assertEquals(VulkanVersion.makeVersion(1, 3, 280), VulkanVersion.makeVersion("v1.3.280"));
        assertEquals(0, VulkanVersion.makeVersion("version 1.3.280 invalid"));
        assertEquals(0, VulkanVersion.makeVersion(null));
    }

    @Test
    public void rejectsOutOfRangeVersionParts() {
        assertEquals(0, VulkanVersion.makeVersion(128, 0, 0));
        assertEquals(0, VulkanVersion.makeVersion(1, 1024, 0));
        assertEquals(0, VulkanVersion.makeVersion(1, 0, 4096));
        assertEquals(0, VulkanVersion.makeVersion(-1, 0, 0));
    }
}
