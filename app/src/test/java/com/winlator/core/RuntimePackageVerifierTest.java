package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

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
            "c188fe0a8bb4aeed92b34d5f8e868a8a73801fcfdfc5a0c0ce9d8ad13e2ec68a"));
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
            "c188fe0a8bb4aeed92b34d5f8e868a8a73801fcfdfc5a0c0ce9d8ad13e2ec68a"));
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
}
