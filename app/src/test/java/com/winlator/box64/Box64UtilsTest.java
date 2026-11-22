package com.winlator.box64;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class Box64UtilsTest {
    private static final byte[] MARKER = "Box64 arm64 v".getBytes(StandardCharsets.US_ASCII);

    private byte[] binaryWithVersionAt(int offset, String version) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < offset; i++) output.write(0x7f);
        output.write(MARKER);
        output.write(version.getBytes(StandardCharsets.US_ASCII));
        output.write(' ');
        output.write("build-info".getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }

    @Test
    public void extractsVersionFromOrdinaryBuffer() throws IOException {
        assertEquals("0.4.4", Box64Utils.extractVersion(
            new ByteArrayInputStream(binaryWithVersionAt(128, "0.4.4"))));
    }

    @Test
    public void findsMarkerThatCrossesTheFourKilobyteBoundary() throws IOException {
        assertEquals("0.4.6", Box64Utils.extractVersion(
            new ByteArrayInputStream(binaryWithVersionAt(4092, "0.4.6"))));
    }

    @Test
    public void readsVersionDelimiterWhenItCrossesTheFourKilobyteBoundary() throws IOException {
        assertEquals("26.1.0", Box64Utils.extractVersion(
            new ByteArrayInputStream(binaryWithVersionAt(4080, "26.1.0"))));
    }

    @Test
    public void returnsEmptyWhenMarkerIsMissing() throws IOException {
        assertEquals("", Box64Utils.extractVersion(
            new ByteArrayInputStream("not a box64 binary".getBytes(StandardCharsets.US_ASCII))));
    }
}
