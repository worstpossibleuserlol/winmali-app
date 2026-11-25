package com.winlator.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class HttpUtilsTest {
    @Test
    public void readsResponseWithinConfiguredLimit() throws IOException {
        byte[] payload = "component-index".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(payload, HttpUtils.readLimited(new ByteArrayInputStream(payload), payload.length));
    }

    @Test
    public void rejectsResponseLargerThanConfiguredLimit() {
        byte[] payload = "response-too-large".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () ->
            HttpUtils.readLimited(new ByteArrayInputStream(payload), payload.length - 1));
    }

    @Test
    public void rejectsNegativeResponseLimit() {
        assertThrows(IOException.class, () ->
            HttpUtils.readLimited(new ByteArrayInputStream(new byte[0]), -1));
    }

    @Test
    public void acceptsEmptyResponseWhenZeroBytesAreAllowed() throws IOException {
        assertArrayEquals(new byte[0],
            HttpUtils.readLimited(new ByteArrayInputStream(new byte[0]), 0));
    }
}
