package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WineInstallerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File createElf(File file, int elfClass) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory()) parent.mkdirs();
        byte[] header = new byte[52];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = (byte)elfClass;
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(header);
        }
        return file;
    }

    @Test
    public void chooses64BitWineWhenWine64FileIsActually32Bit() throws IOException {
        File root = temporaryFolder.getRoot();
        File wine = createElf(new File(root, "bin/wine"), 2);
        createElf(new File(root, "bin/wine64"), 1);

        assertEquals(wine, WineInstaller.findWineExecutable(root));
    }

    @Test
    public void rejectsRuntimeWithOnly32BitWineBinaries() throws IOException {
        File root = temporaryFolder.getRoot();
        createElf(new File(root, "bin/wine"), 1);
        createElf(new File(root, "bin/wine64"), 1);

        assertNull(WineInstaller.findWineExecutable(root));
    }
}
