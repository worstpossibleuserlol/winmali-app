package com.winlator.box64;

import android.content.Context;

import com.winlator.core.ArrayUtils;
import com.winlator.core.StreamUtils;
import com.winlator.xenvironment.RootFS;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public abstract class Box64Utils {
    public static String extractBinVersion(Context context) {
        File binFile = new File(RootFS.find(context).getRootDir(), "/usr/local/bin/box64");
        try (BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(binFile), StreamUtils.BUFFER_SIZE)) {
            return extractVersion(inStream);
        }
        catch (IOException e) {
            return "";
        }
    }

    static String extractVersion(InputStream inStream) throws IOException {
        final byte[] marker = {'B','o','x','6','4',' ','a','r','m','6','4',' ','v'};
        byte[] buffer = new byte[4096 + marker.length - 1];
        int carry = 0;

        while (true) {
            int bytesRead = inStream.read(buffer, carry, 4096);
            if (bytesRead < 0) return "";
            if (bytesRead == 0) continue;

            int length = carry + bytesRead;
            int index = ArrayUtils.indexOf(buffer, 0, length, marker);
            if (index != ArrayUtils.INDEX_NOT_FOUND) {
                int start = index + marker.length;
                int end = ArrayUtils.indexOf(buffer, start, length, (byte)' ');
                ByteArrayOutputStream version = new ByteArrayOutputStream();
                if (end != ArrayUtils.INDEX_NOT_FOUND) {
                    version.write(buffer, start, end - start);
                    return version.toString(StandardCharsets.US_ASCII.name());
                }

                version.write(buffer, start, length - start);
                int value;
                while ((value = inStream.read()) != -1 && value != ' ') version.write(value);
                return value == -1 ? "" : version.toString(StandardCharsets.US_ASCII.name());
            }

            carry = Math.min(marker.length - 1, length);
            System.arraycopy(buffer, length - carry, buffer, 0, carry);
        }
    }
}
