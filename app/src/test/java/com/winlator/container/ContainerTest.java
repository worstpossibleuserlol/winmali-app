package com.winlator.container;

import com.winlator.core.WineInfo;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContainerTest {
    @Test
    public void invalidSavedAppVersionDoesNotAbortContainerMigration() throws Exception {
        JSONObject data = new JSONObject();
        JSONObject extra = new JSONObject();
        extra.put("appVersion", "damaged");
        data.put("extraData", extra);
        data.put("wincomponents", "direct3d=0");

        Container.checkObsoleteOrMissingProperties(data);

        String migrated = data.getString("wincomponents");
        assertTrue(migrated.contains("direct3d=0"));
        assertTrue(migrated.contains("directsound=1"));
    }

    @Test
    public void nullWineVersionResolvesToMainWine() {
        Container container = new Container(1);
        container.setWineVersion(null);
        assertEquals(WineInfo.MAIN_WINE_INFO.identifier(), container.getWineVersion());
    }
}
