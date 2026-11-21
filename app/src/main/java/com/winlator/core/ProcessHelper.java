package com.winlator.core;

import android.os.Process;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import com.winlator.MainActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

public abstract class ProcessHelper {
    public enum PState {RUNNING, SLEEPING, WAITING, ZOMBIE, STOPPED, DEAD, OTHER}
    private static final ArrayList<Callback<String>> debugCallbacks = new ArrayList<>();

    public static class PStat {
        public int pid = 0;
        public String name = "";
        public String shortName = "";
        public PState state = PState.OTHER;
        public int parentPID = 0;
        public boolean guestProcess = false;

        @NonNull
        @Override
        public String toString() {
            return pid+" "+name+" "+state+" "+parentPID+" "+guestProcess;
        }
    }

    public static void suspendProcess(int pid) {
        Process.sendSignal(pid, OsConstants.SIGSTOP);
    }

    public static void resumeProcess(int pid) {
        Process.sendSignal(pid, OsConstants.SIGCONT);
    }

    public static void killProcess(int pid) {
        Process.sendSignal(pid, OsConstants.SIGKILL);
    }

    public static int exec(String command) {
        return exec(command, null);
    }

    public static int exec(String command, EnvVars envVars) {
        return exec(command, envVars, null);
    }

    public static int exec(String command, EnvVars envVars, File workingDir) {
        return exec(command, envVars, workingDir, null);
    }

    public static int exec(String command, EnvVars envVars, File workingDir, Callback<Integer> terminationCallback) {
        try {
            String[] arguments = splitCommand(command);
            if (arguments.length == 0) return -1;

            boolean captureOutput = hasDebugCallbacks();
            ProcessBuilder processBuilder = new ProcessBuilder(arguments).directory(workingDir);
            if (!captureOutput) processBuilder.redirectOutput(new File("/dev/null")).redirectErrorStream(true);

            Map<String, String> environment = processBuilder.environment();
            if (envVars != null) {
                for (String name : envVars) environment.put(name, envVars.get(name));
            }

            java.lang.Process process = processBuilder.start();
            int processId = getProcessId(process);
            if (processId <= 0) {
                process.destroyForcibly();
                return -1;
            }

            if (captureOutput) {
                createDebugThread(process.getInputStream());
                createDebugThread(process.getErrorStream());
            }
            if (terminationCallback != null) createWaitForThread(process, terminationCallback);
            return (int)processId;
        }
        catch (Exception e) {
            return -1;
        }
    }

    private static int getProcessId(java.lang.Process process) {
        Class<?> processClass = process.getClass();
        while (processClass != null) {
            try {
                Field pidField = processClass.getDeclaredField("pid");
                pidField.setAccessible(true);
                int pid = pidField.getInt(process);
                return pid > 0 ? pid : -1;
            }
            catch (NoSuchFieldException e) {
                processClass = processClass.getSuperclass();
            }
            catch (ReflectiveOperationException | RuntimeException e) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean hasDebugCallbacks() {
        synchronized (debugCallbacks) {
            return !debugCallbacks.isEmpty();
        }
    }

    private static void createDebugThread(final InputStream inputStream) {
        Thread worker = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ArrayList<Callback<String>> callbacks;
                    synchronized (debugCallbacks) {
                        callbacks = new ArrayList<>(debugCallbacks);
                    }
                    if (!callbacks.isEmpty()) {
                        for (Callback<String> callback : callbacks) {
                            try {
                                callback.call(line);
                            }
                            catch (RuntimeException ignored) {}
                        }
                    }
                    else if (MainActivity.DEBUG_MODE) System.out.println(line);
                }
            }
            catch (IOException ignored) {}
        }, "WinMali-process-output");
        worker.setDaemon(true);
        worker.start();
    }

    private static void createWaitForThread(java.lang.Process process, final Callback<Integer> terminationCallback) {
        Thread worker = new Thread(() -> {
            try {
                terminationCallback.call(process.waitFor());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (RuntimeException ignored) {}
        }, "WinMali-process-wait");
        worker.setDaemon(true);
        worker.start();
    }

    public static void removeAllDebugCallbacks() {
        synchronized (debugCallbacks) {
            debugCallbacks.clear();
        }
    }

    public static void addDebugCallback(Callback<String> callback) {
        synchronized (debugCallbacks) {
            if (!debugCallbacks.contains(callback)) debugCallbacks.add(callback);
        }
    }

    public static void removeDebugCallback(Callback<String> callback) {
        synchronized (debugCallbacks) {
            debugCallbacks.remove(callback);
        }
    }

    public static String[] splitCommand(String command) {
        if (command == null || command.isEmpty()) return new String[0];

        ArrayList<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        char quote = 0;
        boolean tokenStarted = false;

        for (int i = 0; i < command.length(); i++) {
            char current = command.charAt(i);

            if (current == '\\' && i + 1 < command.length()) {
                char next = command.charAt(i + 1);
                if (next == ' ' || next == '"' || (next == '\'' && quote != '"')) {
                    value.append(next);
                    tokenStarted = true;
                    i++;
                    continue;
                }
            }

            if (current == '"' || current == '\'') {
                if (quote == 0) {
                    quote = current;
                    tokenStarted = true;
                    continue;
                }
                if (quote == current) {
                    quote = 0;
                    continue;
                }
            }

            if (Character.isWhitespace(current) && quote == 0) {
                if (tokenStarted) {
                    result.add(value.toString());
                    value.setLength(0);
                    tokenStarted = false;
                }
            }
            else {
                value.append(current);
                tokenStarted = true;
            }
        }

        if (quote != 0) throw new IllegalArgumentException("Unterminated quoted command argument");
        if (tokenStarted) result.add(value.toString());
        return result.toArray(new String[0]);
    }

    public static String getAffinityMaskAsHexString(String cpuList) {
        String[] values = cpuList.split(",");
        int affinityMask = 0;
        for (String value : values) {
            byte index = Byte.parseByte(value);
            affinityMask |= (int)Math.pow(2, index);
        }
        return Integer.toHexString(affinityMask);
    }

    public static int getAffinityMask(String cpuList) {
        if (cpuList == null || cpuList.isEmpty()) return 0;
        String[] values = cpuList.split(",");
        int affinityMask = 0;
        for (String value : values) {
            byte index = Byte.parseByte(value);
            affinityMask |= (int)Math.pow(2, index);
        }
        return affinityMask;
    }

    public static int getAffinityMask(boolean[] cpuList) {
        int affinityMask = 0;
        for (int i = 0; i < cpuList.length; i++) {
            if (cpuList[i]) affinityMask |= (int)Math.pow(2, i);
        }
        return affinityMask;
    }

    public static int getAffinityMask(int from, int to) {
        int affinityMask = 0;
        for (int i = from; i < to; i++) affinityMask |= (int)Math.pow(2, i);
        return affinityMask;
    }

    public static long getMemoryUsage(int pid) {
        try (Scanner scanner = new Scanner(new FileInputStream("/proc/"+pid+"/statm"))) {
            byte index = 0;
            long vmSize = 0;
            long resident = 0;

            while (scanner.hasNext() && index < 2) {
                byte column = index++;
                if (column == 0) {
                    vmSize = scanner.nextLong();
                }
                else resident = scanner.nextLong();
            }

            return (resident * Os.sysconf(OsConstants._SC_PAGESIZE));
        }
        catch (Exception e) {
            return 0;
        }
    }

    public static String getProcessName(int pid) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/"+pid+"/cmdline"))) {
            StringBuilder result = new StringBuilder();
            int chr;
            while ((chr = reader.read()) != -1 && chr != 0) {
                result.append((char)chr);
            }
            return result.length() > 0 && result.charAt(0) == '/' ? FileUtils.getName(result.toString()) : result.toString();
        }
        catch (IOException e) {
            return "";
        }
    }

    public static List<String> getProcessCmdLine(int pid) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/"+pid+"/cmdline"))) {
            StringBuilder sb = new StringBuilder();
            ArrayList<String> result = new ArrayList<>();

            int charsRead;
            char[] chars = new char[64];
            while ((charsRead = reader.read(chars)) != -1) {
                for (int i = 0; i < charsRead; i++) {
                    if (chars[i] == '\0') {
                        if (sb.length() == 0) break;
                        result.add(sb.toString());
                        sb = new StringBuilder();
                    }
                    else sb.append(chars[i]);
                }
            }
            return result;
        }
        catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static List<PStat> getChildProcesses() {
        File procFile = new File("/proc");
        String[] pids = procFile.list((file, name) -> (new File(file, name)).isDirectory() && name.matches("[0-9]+"));
        if (pids == null) return Collections.emptyList();
        ArrayList<PStat> result = new ArrayList<>();
        int parentPID = Os.getpid();

        for (String pid : pids) {
            try (Scanner scanner = new Scanner(new FileInputStream("/proc/"+pid+"/stat"))) {
                PStat pstat = new PStat();
                int index = 0;

                while (scanner.hasNext() && index < 4) {
                    switch (index++) {
                        case 0:
                            pstat.pid = scanner.nextInt();
                            break;
                        case 1:
                            Pattern oldDelimiter = scanner.delimiter();
                            scanner.useDelimiter("\\)");
                            pstat.shortName = scanner.hasNext() ? scanner.next().substring(2) : "";
                            scanner.useDelimiter(oldDelimiter);
                            if (scanner.hasNext()) scanner.next();
                            break;
                        case 2: {
                            switch (scanner.next()) {
                                case "R":
                                    pstat.state = PState.RUNNING;
                                    break;
                                case "S":
                                    pstat.state = PState.SLEEPING;
                                    break;
                                case "D":
                                    pstat.state = PState.WAITING;
                                    break;
                                case "Z":
                                    pstat.state = PState.ZOMBIE;
                                    break;
                                case "T":
                                    pstat.state = PState.STOPPED;
                                    break;
                                case "X":
                                    pstat.state = PState.DEAD;
                                    break;
                            }
                            break;
                        }
                        case 3:
                            pstat.parentPID = scanner.nextInt();
                            break;
                    }
                }

                if (pstat.parentPID == parentPID || pstat.pid > parentPID) {
                    pstat.name = getProcessName(pstat.pid);
                    if (pstat.name.isEmpty()) pstat.name = pstat.shortName;
                    pstat.guestProcess = pstat.name.contains("wine") || pstat.name.contains(".exe");
                    result.add(pstat);
                }
            }
            catch (Exception ignored) {
                // A process may exit after /proc was enumerated. Keep the other results.
            }
        }

        return result;
    }
}
