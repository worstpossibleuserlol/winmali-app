package com.winlator.container;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContainerTemplateUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void replacesStaleAbsoluteZDriveLinkWithCurrentRootFs() throws Exception {
        File rootFs = temporaryFolder.newFolder("rootfs");
        File container = new File(rootFs, "home/xuser-1");
        File dosDevices = new File(container, ".wine/dosdevices");
        assertTrue(dosDevices.mkdirs());

        File zDrive = new File(dosDevices, "z:");
        Files.createSymbolicLink(zDrive.toPath(), new File("/old/install/files/rootfs").toPath());
        assertTrue(ContainerTemplateUtils.installRootFsZDriveLink(container, rootFs));

        assertTrue(Files.isSymbolicLink(zDrive.toPath()));
        assertEquals(rootFs.getCanonicalFile(), zDrive.getCanonicalFile());
        assertFalse(Files.readSymbolicLink(zDrive.toPath()).isAbsolute());
    }

    @Test
    public void createsZDriveLinkWhenTemplateOmitsIt() throws Exception {
        File rootFs = temporaryFolder.newFolder("rootfs");
        File container = new File(rootFs, "home/xuser-2");
        File dosDevices = new File(container, ".wine/dosdevices");
        assertTrue(dosDevices.mkdirs());

        File zDrive = new File(dosDevices, "z:");
        assertTrue(ContainerTemplateUtils.installRootFsZDriveLink(container, rootFs));
        assertTrue(Files.isSymbolicLink(zDrive.toPath()));
        assertEquals(rootFs.getCanonicalFile(), zDrive.getCanonicalFile());
    }

    @Test
    public void refusesSymlinkedDosDevicesDirectory() throws Exception {
        File rootFs = temporaryFolder.newFolder("rootfs");
        File container = new File(rootFs, "home/xuser-3");
        File wine = new File(container, ".wine");
        assertTrue(wine.mkdirs());
        Files.createSymbolicLink(new File(wine, "dosdevices").toPath(), rootFs.toPath());

        assertFalse(ContainerTemplateUtils.installRootFsZDriveLink(container, rootFs));
    }
}