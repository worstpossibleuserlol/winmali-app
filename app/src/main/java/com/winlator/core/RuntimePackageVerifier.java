package com.winlator.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Streaming integrity checks for Runtime Center download archives. */
public final class RuntimePackageVerifier {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final ExecutorService VERIFICATION_OPERATIONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WinMali-runtime-verification");
        thread.setDaemon(true);
        return thread;
    });

    private RuntimePackageVerifier() {}

    public static boolean verify(File file, long expectedSize, String expectedSha256) {
        if (file == null || expectedSize <= 0 || !file.isFile() ||
            file.length() != expectedSize || !isValidSha256(expectedSha256)) return false;
        try {
            byte[] actual = digest(file);
            byte[] expected = decodeHex(expectedSha256);
            return MessageDigest.isEqual(actual, expected);
        }
        catch (IOException | NoSuchAlgorithmException | IllegalArgumentException error) {
            return false;
        }
    }

    /**
     * Performs the potentially expensive archive digest away from the caller thread.
     * The callback is invoked on the verification worker thread.
     */
    public static void verifyAsync(File file, long expectedSize, String expectedSha256,
            Callback<Boolean> callback) {
        if (callback == null) return;
        VERIFICATION_OPERATIONS.execute(() -> {
            boolean verified = verify(file, expectedSize, expectedSha256);
            try {
                callback.call(verified);
            }
            catch (RuntimeException error) {
                android.util.Log.w("WinMali-Runtime", "Runtime verification callback failed", error);
            }
        });
    }

    public static String sha256(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            byte[] digest = digest(file);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
            return value.toString();
        }
        catch (IOException | NoSuchAlgorithmException error) {
            return null;
        }
    }

    private static byte[] digest(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return digest.digest();
    }

    private static boolean isValidSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
                    (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static byte[] decodeHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hexadecimal digest");
            result[i / 2] = (byte)((high << 4) | low);
        }
        return result;
    }
}
