package com.winlator.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtils {
    public static final int BUFFER_SIZE = 64 * 1024;

    public static int skip(InputStream inStream, int bytesToSkip) {
        if (inStream == null || bytesToSkip <= 0) return 0;
        int bytesSkipped = 0;
        try {
            long initiallySkipped = inStream.skip(bytesToSkip);
            bytesSkipped = (int)Math.max(0L, Math.min((long)bytesToSkip, initiallySkipped));

            if (bytesSkipped < bytesToSkip) {
                final byte[] skipBuffer = new byte[1024];
                while (bytesSkipped < bytesToSkip) {
                    int bytesRead = inStream.read(skipBuffer, 0,
                        Math.min(skipBuffer.length, bytesToSkip - bytesSkipped));
                    if (bytesRead < 0) break;
                    if (bytesRead == 0) {
                        if (inStream.read() < 0) break;
                        bytesRead = 1;
                    }
                    bytesSkipped += bytesRead;
                }
            }
        }
        catch (IOException error) {
            android.util.Log.w("WinMali-Stream", "Unable to skip requested input bytes", error);
        }
        return bytesSkipped;
    }

    public static byte[] copyToByteArray(InputStream inStream) {
        if (inStream == null) return new byte[0];

        ByteArrayOutputStream outStream = new ByteArrayOutputStream(BUFFER_SIZE);
        copy(inStream, outStream);
        return outStream.toByteArray();
    }

    public static boolean copy(InputStream inStream, OutputStream outStream) {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int amountRead;
            while ((amountRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, amountRead);
            }
            outStream.flush();
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }
}
