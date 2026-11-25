package com.winlator.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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

    private File createX86_64Elf(File file) throws IOException {
        return createElf(file, 2, 62);
    }

    private File createAarch64Elf(File file) throws IOException {
        return createElf(file, 2, 183);
    }

    private File createArm32Elf(File file) throws IOException {
        return createElf(file, 1, 40);
    }

    @Test
    public void restoresOriginalRelativeHomeSymlinkAfterWineInstall() throws IOException {
        File root = temporaryFolder.getRoot();
        File home = new File(root, "home/xuser");
        assertTrue(home.getParentFile().mkdirs());
        assertTrue(FileUtils.symlink("xuser-3", home.getPath()));
        assertTrue(FileUtils.symlink("preinstall/container-pattern", home.getPath()));

        assertTrue(WineInstaller.restoreHomeLink(home, true, "xuser-3"));
        assertTrue(FileUtils.isSymlink(home));
        assertEquals("xuser-3", FileUtils.readSymlink(home));
    }

    @Test
    public void removesTemporaryHomeSymlinkWhenThereWasNoOriginal() throws IOException {
        File home = new File(temporaryFolder.getRoot(), "home/xuser");
        assertTrue(home.getParentFile().mkdirs());
        assertTrue(FileUtils.symlink("preinstall/container-pattern", home.getPath()));

        assertTrue(WineInstaller.restoreHomeLink(home, false, null));
        assertEquals("", FileUtils.readSymlink(home));
        assertFalse(home.exists());
    }

    @Test
    public void doesNotSilentlyReplaceARealHomeDirectory() throws IOException {
        File home = new File(temporaryFolder.getRoot(), "home/xuser");
        assertTrue(home.mkdirs());
        Files.write(new File(home, "keep.txt").toPath(), "keep".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertFalse(FileUtils.symlink("preinstall/container-pattern", home.getPath()));
        assertTrue(new File(home, "keep.txt").isFile());
    }

    @Test
    public void chooses64BitWineWhenWine64FileIsActually32Bit() throws IOException {
        File root = temporaryFolder.getRoot();
        File wine = createX86_64Elf(new File(root, "bin/wine"));
        createArm32Elf(new File(root, "bin/wine64"));

        assertEquals(wine, WineInstaller.findWineExecutable(root));
    }

    @Test
    public void acceptsX8664WineAndPrefersWine64() throws IOException {
        File root = temporaryFolder.getRoot();
        createX86_64Elf(new File(root, "bin/wine"));
        File wine64 = createX86_64Elf(new File(root, "bin/wine64"));

        assertEquals(wine64, WineInstaller.findWineExecutable(root));
    }

    @Test
    public void rejectsAarch64WineBecauseWineRunsThroughBox64() throws IOException {
        File root = temporaryFolder.getRoot();
        createAarch64Elf(new File(root, "bin/wine"));
        createAarch64Elf(new File(root, "bin/wine64"));

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
