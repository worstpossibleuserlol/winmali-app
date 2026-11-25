package com.winlator.core;

import android.content.Context;

import com.winlator.container.Container;
import com.winlator.win32.MSLink;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public abstract class WineStartMenuCreator {
    private static int parseShowCommand(String value) {
        if (value.equals("SW_SHOWMAXIMIZED")) {
            return MSLink.SW_SHOWMAXIMIZED;
        }
        else if (value.equals("SW_SHOWMINNOACTIVE")) {
            return MSLink.SW_SHOWMINNOACTIVE;
        }
        else return MSLink.SW_SHOWNORMAL;
    }

    private static boolean installEpicLauncherScript(Context context, Container container) {
        if (context == null || container == null || container.getRootDir() == null) return false;

        File installerDir = new File(container.getRootDir(), ".wine/drive_c/ProgramData/WinMali");
        if (!installerDir.isDirectory() && !installerDir.mkdirs()) return false;

        File installerScript = new File(installerDir, "EpicGamesLauncherInstaller.js");
        File installerIcon = new File(installerDir, "EpicGamesLauncher.ico");
        return FileUtils.copy(context, "epic_launcher_installer.js", installerScript) &&
            FileUtils.copy(context, "epic_games_launcher.ico", installerIcon) &&
            installerScript.isFile() && installerScript.length() > 0 &&
            installerIcon.isFile() && installerIcon.length() > 0;
    }

    private static void createMenuEntry(JSONObject item, File currentDir, boolean epicInstallerReady) throws JSONException {
        if (item.has("children")) {
            currentDir = new File(currentDir, item.getString("name"));
            currentDir.mkdirs();

            JSONArray children = item.getJSONArray("children");
            for (int i = 0; i < children.length(); i++) {
                createMenuEntry(children.getJSONObject(i), currentDir, epicInstallerReady);
            }
        }
        else {
            if ("epic-games-launcher".equals(item.optString("installer")) && !epicInstallerReady) return;

            File outputFile = new File(currentDir, item.getString("name")+".lnk");
            MSLink.LinkInfo linkInfo = new MSLink.LinkInfo();
            linkInfo.targetPath = item.getString("path");
            linkInfo.arguments = item.optString("cmdArgs");
            linkInfo.iconLocation = item.optString("iconLocation", linkInfo.targetPath);
            linkInfo.iconIndex = item.optInt("iconIndex", 0);
            if (item.has("showCommand")) linkInfo.showCommand = parseShowCommand(item.getString("showCommand"));
            MSLink.createFile(linkInfo, outputFile);
        }
    }

    private static void removeMenuEntry(JSONObject item, File currentDir) throws JSONException {
        if (item.has("children")) {
            currentDir = new File(currentDir, item.getString("name"));

            JSONArray children = item.getJSONArray("children");
            for (int i = 0; i < children.length(); i++) removeMenuEntry(children.getJSONObject(i), currentDir);

            if (FileUtils.isEmpty(currentDir)) currentDir.delete();
        }
        else (new File(currentDir, item.getString("name")+".lnk")).delete();
    }

    private static void removeOldMenu(File containerStartMenuFile, File startMenuDir) {
        if (!containerStartMenuFile.isFile()) return;
        try {
            JSONArray data = new JSONArray(FileUtils.readString(containerStartMenuFile));
            for (int i = 0; i < data.length(); i++) removeMenuEntry(data.getJSONObject(i), startMenuDir);
        }
        catch (JSONException e) {
            containerStartMenuFile.delete();
        }
    }

    public static void create(Context context, Container container) {
        File startMenuDir = container.getStartMenuDir();
        File containerStartMenuFile = new File(container.getRootDir(), ".startmenu");
        removeOldMenu(containerStartMenuFile, startMenuDir);

        boolean epicInstallerReady = installEpicLauncherScript(context, container);
        try {
            JSONArray data = new JSONArray(FileUtils.readString(context, "wine_startmenu.json"));
            FileUtils.writeString(containerStartMenuFile, data.toString());
            for (int i = 0; i < data.length(); i++) {
                createMenuEntry(data.getJSONObject(i), startMenuDir, epicInstallerReady);
            }
        }
        catch (JSONException e) {}
    }
}
