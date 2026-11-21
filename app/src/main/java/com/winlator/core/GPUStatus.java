package com.winlator.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Best-effort GPU utilization reader. Android does not expose a universal GPU
 * utilization API, so unsupported or permission-restricted drivers return -1.
 */
public abstract class GPUStatus {
    private static final String[] DIRECT_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/class/misc/mali0/device/utilization",
        "/sys/devices/platform/mali.0/utilization",
        "/sys/devices/platform/mali/utilization"
    };

    public static int getUsagePercent() {
        for (String path : DIRECT_PATHS) {
            int value = readUsage(path, path.endsWith("gpubusy"));
            if (value >= 0) return value;
        }

        // Some Mali vendors expose utilization through a named devfreq node.
        File devfreq = new File("/sys/class/devfreq");
        File[] devices = devfreq.listFiles();
        if (devices != null) {
            for (File device : devices) {
                String name = device.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("mali") && !name.contains("gpu")) continue;
                int value = readUsage(new File(device, "load").getPath(), false);
                if (value >= 0) return value;
                value = readUsage(new File(device, "utilization").getPath(), false);
                if (value >= 0) return value;
            }
        }
        return -1;
    }

    private static int readUsage(String path, boolean busyTotal) {
        File file = new File(path);
        if (!file.isFile()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseUtilization(reader.readLine(), busyTotal);
        }
        catch (IOException | SecurityException ignored) {
            return -1;
        }
    }

    static int parseUtilization(String raw, boolean busyTotal) {
        if (raw == null) return -1;
        String value = raw.trim();
        if (value.isEmpty()) return -1;
        try {
            if (value.contains("@")) value = value.substring(0, value.indexOf('@')).trim();
            String[] fields = value.split("\\s+");
            if (busyTotal && fields.length >= 2) {
                long busy = Long.parseLong(fields[0]);
                long total = Long.parseLong(fields[1]);
                if (busy < 0 || total <= 0 || busy > total) return -1;
                return (int)Math.max(0, Math.min(100, Math.round(busy * 100.0d / total)));
            }
            int percent = Integer.parseInt(fields[0]);
            return percent >= 0 && percent <= 100 ? percent : -1;
        }
        catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
