package com.winlator.xenvironment;

import com.winlator.core.TarCompressorUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class RootFSAssetIntegrationTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void bundledRootfsArchiveExtractsAndContainsRequiredWineFiles() throws Exception {
        File archive = new File("src/main/assets/rootfs.tzst");
        assertTrue("The bundled rootfs.tzst asset is missing", archive.isFile() && archive.length() > 0);

        File extractedRoot = temporaryFolder.newFolder("bundled-rootfs");
        assertTrue("The bundled rootfs archive could not be extracted",
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, archive, extractedRoot));
        assertTrue("The extracted rootfs is missing required Wine binaries or DLL directories",
            RootFS.hasCoreWineFiles(extractedRoot));
    }
}
