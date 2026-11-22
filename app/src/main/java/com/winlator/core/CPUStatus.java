package com.winlator.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;

public abstract class CPUStatus {
    private static long lastTotalCpuTicks = -1;
    private static long lastIdleCpuTicks = -1;

    public static synchronized int getUsagePercent() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return -1;

            String[] values = line.trim().split("\\s+");
            if (values.length < 5) return -1;

            long total = 0;
            for (int i = 1; i < values.length && i <= 8; i++) {
                total += Long.parseLong(values[i]);
            }

            long idle = Long.parseLong(values[4]);
            if (values.length > 5) idle += Long.parseLong(values[5]);

            if (lastTotalCpuTicks < 0 || lastIdleCpuTicks < 0) {
                lastTotalCpuTicks = total;
                lastIdleCpuTicks = idle;
                return -1;
            }

            long totalDelta = total - lastTotalCpuTicks;
            long idleDelta = idle - lastIdleCpuTicks;
            lastTotalCpuTicks = total;
            lastIdleCpuTicks = idle;

            if (totalDelta <= 0) return -1;
            long busyDelta = Math.max(0, totalDelta - Math.max(0, idleDelta));
            return (int)Math.max(0, Math.min(100, Math.round((busyDelta * 100.0d) / totalDelta)));
        }
        catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    private static ArrayList<Integer> getCpuIndices() {
        ArrayList<Integer> indices = new ArrayList<>();
        File cpuRoot = new File("/sys/devices/system/cpu");
        File[] entries = cpuRoot.listFiles((directory, name) -> name.matches("cpu[0-9]+"));
        if (entries != null) {
            for (File entry : entries) {
                try {
                    indices.add(Integer.parseInt(entry.getName().substring(3)));
                }
                catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(indices);
        if (indices.isEmpty()) {
            int count = Math.max(1, Runtime.getRuntime().availableProcessors());
            for (int i = 0; i < count; i++) indices.add(i);
        }
        return indices;
    }

    public static short[] getCurrentClockSpeeds() {
        ArrayList<Integer> indices = getCpuIndices();
        short[] clockSpeeds = new short[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            int frequency = FileUtils.readInt("/sys/devices/system/cpu/cpu"+indices.get(i)+"/cpufreq/scaling_cur_freq");
            clockSpeeds[i] = frequency > 0 ? (short)(frequency / 1000) : (short)-1;
        }
        return clockSpeeds;
    }

    public static short getMaxClockSpeed(int cpuIndex) {
        if (cpuIndex < 0) return -1;
        int maxFreq = FileUtils.readInt("/sys/devices/system/cpu/cpu"+cpuIndex+"/cpufreq/cpuinfo_max_freq");
        return maxFreq > 0 ? (short)(maxFreq / 1000) : (short)-1;
    }

    static int parseTemperature(String rawValue) {
        if (rawValue == null) return -1;
        try {
            long raw = Long.parseLong(rawValue.trim());
            if (raw <= 0) return -1;
            long celsius;
            if (raw <= 150) celsius = raw;
            else if (raw <= 1500) celsius = raw / 10;
            else if (raw <= 150000) celsius = raw / 1000;
            else return -1;
            return celsius >= 1 && celsius <= 150 ? (int)celsius : -1;
        }
        catch (NumberFormatException error) {
            return -1;
        }
    }

    private static int readTemperature(File file) {
        if (file == null || !file.isFile()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseTemperature(reader.readLine());
        }
        catch (IOException | SecurityException error) {
            return -1;
        }
    }

    public static int getTemperature() {
        // Prefer sensors whose kernel label identifies the CPU or the application processor.
        File thermalRoot = new File("/sys/class/thermal");
        File[] zones = thermalRoot.listFiles((directory, name) -> name.matches("thermal_zone[0-9]+"));
        if (zones != null) {
            for (File zone : zones) {
                try (BufferedReader reader = new BufferedReader(new FileReader(new File(zone, "type")))) {
                    String type = reader.readLine();
                    if (type == null) continue;
                    String label = type.toLowerCase(Locale.ENGLISH);
                    if (label.contains("cpu") || label.contains("soc") || label.equals("ap") || label.contains("ap_")) {
                        int temperature = readTemperature(new File(zone, "temp"));
                        if (temperature > 0) return temperature;
                    }
                }
                catch (IOException | SecurityException ignored) {}
            }
        }

        final String[] sensorFiles = {
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
            "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
            "/sys/devices/platform/tegra_tmon/temp1_input",
            "/sys/devices/platform/s5p-tmu/temperature",
            "/sys/devices/platform/s5p-tmu/curr_temp",
            "/sys/class/hwmon/hwmon0/device/temp1_input",
            "/sys/class/i2c-adapter/i2c-4/4-004c/temperature",
            "/sys/kernel/debug/tegra_thermal/temp_tj",
            "/sys/htc/cpu_temp",
            "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/ext_temperature",
            "/sys/devices/platform/tegra-tsensor/tsensor_temperature",
            "/sys/devices/virtual/sec/sec-lp-thermistor/temperature"
        };
        for (String path : sensorFiles) {
            int temperature = readTemperature(new File(path));
            if (temperature > 0) return temperature;
        }
        return -1;
    }

    public static String formatClockSpeed(int clockSpeed) {
        if (clockSpeed < 0) return "N/A";
        return clockSpeed >= 1000 ? String.format(Locale.ENGLISH, "%.2f", clockSpeed / 1000.0f)+" GHz" : clockSpeed+" MHz";
    }
}