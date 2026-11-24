package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class RuntimePackageVerifierTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File writeFile(String name, String value) throws Exception {
        File file = new File(temporaryFolder.getRoot(), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    @Test
    public void acceptsFileWithMatchingSizeAndSha256() throws Exception {
        File file = writeFile("runtime.wcp", "verified runtime package");
        assertTrue(RuntimePackageVerifier.verify(file, 24,
            "7dac3c033ef88bce05338d2fdf74696a1ab24f6d81523ef633b59f9c9c48818c"));
    }

    @Test
    public void rejectsFileWithIncorrectDigest() throws Exception {
        File file = writeFile("runtime.wcp", "verified runtime package");
        assertFalse(RuntimePackageVerifier.verify(file, 24,
            "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    public void rejectsFileWithIncorrectSize() throws Exception {
        File file = writeFile("runtime.wcp", "verified runtime package");
        assertFalse(RuntimePackageVerifier.verify(file, 25,
            "7dac3c033ef88bce05338d2fdf74696a1ab24f6d81523ef633b59f9c9c48818c"));
    }

    @Test
    public void rejectsMissingEmptyAndMalformedInputs() throws Exception {
        File empty = writeFile("empty.wcp", "");
        assertFalse(RuntimePackageVerifier.verify(empty, 0,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        assertFalse(RuntimePackageVerifier.verify(new File(temporaryFolder.getRoot(), "missing.wcp"), 1,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        File valid = writeFile("valid.wcp", "valid");
        assertFalse(RuntimePackageVerifier.verify(valid, 5, "not-a-sha256"));
    }
    @Test
    public void verifyAsyncRunsDigestWorkOffTheCallingThread() throws Exception {
        File file = writeFile("async-runtime.wcp", "verified runtime package");
        Thread callerThread = Thread.currentThread();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        RuntimePackageVerifier.verifyAsync(file, 24,
            "7dac3c033ef88bce05338d2fdf74696a1ab24f6d81523ef633b59f9c9c48818c",
            verified -> {
                result.set(verified);
                callbackThread.set(Thread.currentThread());
                completed.countDown();
            });

        assertTrue("verification callback should complete", completed.await(5, TimeUnit.SECONDS));
        assertEquals(Boolean.TRUE, result.get());
        assertNotNull(callbackThread.get());
        assertNotSame("verification must not block the caller thread", callerThread, callbackThread.get());
    }

}
