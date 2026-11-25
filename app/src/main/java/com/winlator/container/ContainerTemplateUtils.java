package com.winlator.container;

import com.winlator.core.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Repairs the Wine Z: drive mapping after a container template is extracted.
 *
 * Template archives can contain a link to the private files directory of the
 * machine that produced the archive. A relative link keeps the mapping valid
 * after installation and across different Android app data paths.
 */
public final class ContainerTemplateUtils {
    private ContainerTemplateUtils() {}

    public static boolean installRootFsZDriveLink(File containerDir, File rootFsDir) throws IOException {
        if (containerDir == null || rootFsDir == null ||
            !containerDir.isDirectory() || !rootFsDir.isDirectory() ||
            FileUtils.isSymlink(containerDir) || FileUtils.isSymlink(rootFsDir)) {
            return false;
        }

        File wineDir = new File(containerDir, ".wine");
        File dosDevicesDir = new File(wineDir, "dosdevices");
        if (!wineDir.isDirectory() || !dosDevicesDir.isDirectory() ||
            FileUtils.isSymlink(wineDir) || FileUtils.isSymlink(dosDevicesDir)) {
            return false;
        }

        File canonicalRoot = rootFsDir.getCanonicalFile();
        File canonicalDosDevices = dosDevicesDir.getCanonicalFile();
        String relativeTarget = canonicalDosDevices.toPath().relativize(canonicalRoot.toPath()).toString();
        if (relativeTarget.isEmpty()) relativeTarget = ".";

        File zDrive = new File(dosDevicesDir, "z:");
        if ((zDrive.exists() || FileUtils.isSymlink(zDrive)) && !FileUtils.delete(zDrive)) {
            return false;
        }

        if (!FileUtils.symlink(relativeTarget, zDrive.getAbsolutePath())) return false;
        return FileUtils.isSymlink(zDrive) && zDrive.getCanonicalFile().equals(canonicalRoot);
    }
}