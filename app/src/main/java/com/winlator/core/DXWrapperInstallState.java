package com.winlator.core;

import com.winlator.container.DXWrappers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Lists every file required before a Direct3D wrapper cache can be reused. */
public final class DXWrapperInstallState {
    private static final String[] WINED3D_DLLS = {
        "d3d8.dll", "d3d9.dll", "d3d10.dll", "d3d10_1.dll",
        "d3d10core.dll", "d3d11.dll", "dxgi.dll", "wined3d.dll"
    };
    private static final String[] DXVK_DLLS = {
        "d3d8.dll", "d3d9.dll", "d3d10.dll", "d3d10_1.dll",
        "d3d10core.dll", "d3d11.dll", "dxgi.dll"
    };
    private static final String[] VKD3D_DLLS = {"d3d12.dll", "d3d12core.dll"};

    private DXWrapperInstallState() {}

    public static File[] getRequiredDllFiles(File windowsDir, String dxWrapper,
            String ddrawWrapper) {
        if (windowsDir == null || dxWrapper == null || ddrawWrapper == null) return null;
        if (!DXWrappers.WINED3D.equals(dxWrapper) && !DXWrappers.DXVK.equals(dxWrapper)) return null;
        if (!DXWrappers.WINED3D.equals(ddrawWrapper) &&
            !DXWrappers.CNC_DDRAW.equals(ddrawWrapper) &&
            !DXWrappers.D7VK.equals(ddrawWrapper)) return null;

        List<File> required = new ArrayList<>();
        addBothArchitectures(required, windowsDir,
            DXWrappers.DXVK.equals(dxWrapper) ? DXVK_DLLS : WINED3D_DLLS);
        addBothArchitectures(required, windowsDir, VKD3D_DLLS);

        File system32 = new File(windowsDir, "system32");
        File syswow64 = new File(windowsDir, "syswow64");
        required.add(new File(system32, "ddraw.dll"));
        required.add(new File(syswow64, "ddraw.dll"));
        if (DXWrappers.D7VK.equals(ddrawWrapper)) {
            required.add(new File(syswow64, "ddraw_.dll"));
        }
        else if (DXWrappers.CNC_DDRAW.equals(ddrawWrapper)) {
            File driveC = windowsDir.getParentFile();
            if (driveC == null) return null;
            File programData = new File(driveC, "ProgramData/cnc-ddraw");
            required.add(new File(programData, "ddraw.ini"));
            required.add(new File(programData, "Shaders/nearest-neighbor.glsl"));
        }
        return required.toArray(new File[0]);
    }

    private static void addBothArchitectures(List<File> files, File windowsDir,
            String[] dllNames) {
        File system32 = new File(windowsDir, "system32");
        File syswow64 = new File(windowsDir, "syswow64");
        for (String dllName : dllNames) {
            files.add(new File(system32, dllName));
            files.add(new File(syswow64, dllName));
        }
    }
}
