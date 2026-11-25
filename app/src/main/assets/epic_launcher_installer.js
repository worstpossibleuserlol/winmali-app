// WinMali Epic Games Launcher installer.
// Downloads the official Windows MSI from Epic and launches it with Wine's msiexec.

(function () {
    var installerUrl = "https://launcher-public-service-prod06.ol.epicgames.com/launcher/api/installer/download/EpicGamesLauncherInstaller.msi";
    var launcherPath = "C:\\Program Files (x86)\\Epic Games\\Launcher\\Portal\\Binaries\\Win64\\EpicGamesLauncher.exe";
    var shell = new ActiveXObject("WScript.Shell");
    var fso = new ActiveXObject("Scripting.FileSystemObject");
    var stream = null;

    function showMessage(message, title, icon) {
        shell.Popup(message, 0, title || "WinMali - Epic Games Launcher", icon || 64);
    }

    function launchInstalledLauncher() {
        if (!fso.FileExists(launcherPath)) return false;
        shell.Run('"' + launcherPath + '"', 1, false);
        return true;
    }

    if (launchInstalledLauncher()) return;

    var tempDirectory = shell.ExpandEnvironmentStrings("%TEMP%");
    var installerPath = fso.BuildPath(tempDirectory, "EpicGamesLauncherInstaller.msi");

    try {
        showMessage("Downloading the official Epic Games Launcher installer. This may take a few minutes.");

        var request = new ActiveXObject("WinHttp.WinHttpRequest.5.1");
        request.SetTimeouts(15000, 15000, 30000, 300000);
        request.Open("GET", installerUrl, false);
        request.Send();

        if (request.Status !== 200) {
            throw new Error("Epic's download server returned HTTP " + request.Status + ".");
        }

        stream = new ActiveXObject("ADODB.Stream");
        stream.Type = 1;
        stream.Open();
        stream.Write(request.ResponseBody);
        stream.SaveToFile(installerPath, 2);
        stream.Close();
        stream = null;

        if (!fso.FileExists(installerPath) || fso.GetFile(installerPath).Size < 5 * 1024 * 1024) {
            throw new Error("The downloaded installer is missing or incomplete.");
        }

        showMessage("Download complete. Starting the Epic Games Launcher setup.", "WinMali - Epic Games Launcher", 64);
        var exitCode = shell.Run('"C:\\windows\\system32\\msiexec.exe" /i "' + installerPath + '"', 1, true);
        if (exitCode !== 0 && exitCode !== 3010) {
            throw new Error("Windows Installer returned exit code " + exitCode + ".");
        }

        if (!launchInstalledLauncher()) {
            showMessage(
                "Setup finished, but the launcher was not found at its default install path. Check the installer window for errors.",
                "WinMali - Epic Games Launcher",
                48
            );
            return;
        }

        try { fso.DeleteFile(installerPath, true); } catch (cleanupError) {}
    }
    catch (error) {
        if (stream !== null) {
            try { stream.Close(); } catch (closeError) {}
        }
        showMessage(
            "Epic Games Launcher could not be installed.\n\n" + (error.description || error.message || error),
            "WinMali - Epic Games Launcher",
            16
        );
    }
})();
