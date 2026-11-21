package com.winlator.container;

import com.winlator.core.FileUtils;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContainerSaveDataTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void refusesToSaveWithoutContainerDirectory() {
        Container container = new Container(1);
        container.setRootDir(new File(temporaryFolder.getRoot(), "missing"));
        assertFalse(container.saveData());
    }

    @Test
    public void savesReadableConfiguration() throws Exception {
        Container container = new Container(7);
        container.setRootDir(temporaryFolder.getRoot());
        container.setName("Test container");
        assertTrue(container.saveData());

        File config = container.getConfigFile();
        assertTrue(config.isFile());
        JSONObject data = new JSONObject(FileUtils.readString(config));
        assertEquals(7, data.getInt("id"));
        assertEquals("Test container", data.getString("name"));
    }

    @Test
    public void replacesExistingConfigurationWithoutLeavingTemporaryFile() throws Exception {
        Container container = new Container(3);
        container.setRootDir(temporaryFolder.getRoot());
        container.setName("Before");
        assertTrue(container.saveData());

        container.setName("After");
        assertTrue(container.saveData());
        assertEquals("After", new JSONObject(FileUtils.readString(container.getConfigFile())).getString("name"));
        assertFalse(new File(temporaryFolder.getRoot(), ".container.tmp").exists());
    }

    @Test
    public void failedSaveDoesNotCreateFinalConfig() {
        Container container = new Container(4);
        File blockedRoot = new File(temporaryFolder.getRoot(), "not-a-directory");
        assertTrue(FileUtils.writeString(blockedRoot, "file"));
        container.setRootDir(blockedRoot);
        assertFalse(container.saveData());
        assertFalse(container.getConfigFile().exists());
    }
}
