package com.winlator.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class StreamUtilsTest {
    @Test
    public void fallsBackToReadingWhenSkipReturnsZero() {
        byte[] source = new byte[]{1, 2, 3, 4, 5};
        InputStream input = new FilterInputStream(new ByteArrayInputStream(source)) {
            @Override
            public long skip(long count) {
                return 0;
            }
        };

        assertEquals(3, StreamUtils.skip(input, 3));
        try {
            assertEquals(4, input.read());
        }
        catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void returnsActualBytesSkippedWhenStreamEndsEarly() {
        InputStream input = new ByteArrayInputStream(new byte[]{1, 2});
        assertEquals(2, StreamUtils.skip(input, 5));
    }

    @Test
    public void nonPositiveSkipDoesNotConsumeInput() throws IOException {
        InputStream input = new ByteArrayInputStream(new byte[]{1, 2, 3});
        assertEquals(0, StreamUtils.skip(input, 0));
        assertEquals(0, StreamUtils.skip(input, -2));
        assertEquals(1, input.read());
    }
}
