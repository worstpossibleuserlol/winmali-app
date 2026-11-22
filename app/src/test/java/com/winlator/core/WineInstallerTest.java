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

    private File createElf(File file, int elfClass, int machine) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory()) parent.mkdirs();
        byte[] header = new byte[52];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = (byte)elfClass;
        header[5] = 1;
        header[18] = (byte)(machine & 0xff);
        header[19] = (byte)((machine >> 8) & 0xff);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(header);
        }
        return file;
    }

    private File createAarch64Elf(File file) throws IOException {
        return createElf(file, 2, 183);
    }

    private File createArm32Elf(File file) throws IOException {
        return createElf(file, 1, 40);
    }

    @Test
    public void chooses64BitWineWhenWine64FileIsActually32Bit() throws IOException {
        File root = temporaryFolder.getRoot();
        File wine = createAarch64Elf(new File(root, "bin/wine"));
        createArm32Elf(new File(root, "bin/wine64"));

        assertEquals(wine, WineInstaller.findWineExecutable(root));
    }

    @Test
    public void rejectsX8664WineEvenThoughItIsA64BitElf() throws IOException {
        File root = temporaryFolder.getRoot();
        createElf(new File(root, "bin/wine"), 2, 62);
        createElf(new File(root, "bin/wine64"), 2, 62);

        assertNull(WineInstaller.findWineExecutable(root));
    }

    @Test
    public void rejectsRuntimeWithOnly32BitWineBinaries() throws IOException {
        File root = temporaryFolder.getRoot();
        createArm32Elf(new File(root, "bin/wine"));
        createArm32Elf(new File(root, "bin/wine64"));

        assertNull(WineInstaller.findWineExecutable(root));
    }
}
