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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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
        if (pid > 0) Process.sendSignal(pid, OsConstants.SIGSTOP);
    }

    public static void resumeProcess(int pid) {
        if (pid > 0) Process.sendSignal(pid, OsConstants.SIGCONT);
    }

    public static void killProcess(int pid) {
        if (pid > 0) Process.sendSignal(pid, OsConstants.SIGKILL);
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
        return exec(command, envVars, workingDir, null, terminationCallback);
    }

    public static int exec(String command, EnvVars envVars, File workingDir,
            Callback<String> outputCallback, Callback<Integer> terminationCallback) {
        try {
            String[] arguments = splitCommand(command);
            if (arguments.length == 0) return -1;

            boolean captureOutput = outputCallback != null || hasDebugCallbacks();
            ProcessBuilder processBuilder = new ProcessBuilder(arguments).directory(workingDir);
            if (!captureOutput) processBuilder.redirectOutput(new File("/dev/null")).redirectErrorStream(true);

            Map<String, String> environment = processBuilder.environment();
            if (envVars != null) {
                for (String name : envVars) environment.put(name, envVars.get(name));
            }

            java.lang.Process process = processBuilder.start();
            // This launcher has no API for writing to child stdin. Close the
            // pipe immediately so commands waiting for EOF do not hang forever.
            try {
                process.getOutputStream().close();
            }
            catch (IOException error) {
                process.destroyForcibly();
                throw error;
            }
            int processId = getProcessId(process);
            if (processId <= 0) {
                process.destroyForcibly();
                return -1;
            }

            Thread stdoutWorker = null;
            Thread stderrWorker = null;
            if (captureOutput) {
                stdoutWorker = createDebugThread(process.getInputStream(), outputCallback);
                stderrWorker = createDebugThread(process.getErrorStream(), outputCallback);
            }
            if (terminationCallback != null) {
                createWaitForThread(process, stdoutWorker, stderrWorker, terminationCallback);
            }
            return processId;
        }
        catch (Exception error) {
            android.util.Log.e("WinMali-Process", "Unable to start guest process", error);
            return -1;
        }
    }

    private static int getProcessId(java.lang.Process process) {
        // Android's java.lang.Process does not expose Process.pid() on every
        // supported API level. Try the public method when present, then use the
        // ProcessBuilder implementation's PID field as a compatibility fallback.
        try {
            // Process.pid() is public on Android API 26+ and avoids reflective
            // access to hidden fields on java.lang.Process implementation classes.
            long pid = process.pid();
            if (pid > 0 && pid <= Integer.MAX_VALUE) return (int)pid;
        }
        catch (UnsupportedOperationException | SecurityException ignored) {}

        Class<?> processClass = process.getClass();
        while (processClass != null) {
            try {
                Field pidField = processClass.getDeclaredField("pid");
                pidField.setAccessible(true);
                Object value = pidField.get(process);
                if (value instanceof Number) {
                    long pid = ((Number)value).longValue();
                    if (pid > 0 && pid <= Integer.MAX_VALUE) return (int)pid;
                }
                return -1;
            }
            catch (NoSuchFieldException error) {
                processClass = processClass.getSuperclass();
            }
            catch (ReflectiveOperationException | RuntimeException error) {
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

    private static Thread createDebugThread(final InputStream inputStream,
            final Callback<String> outputCallback) {
        Thread worker = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputCallback != null) {
                        try {
                            outputCallback.call(line);
                        }
                        catch (RuntimeException error) {
                            android.util.Log.w("WinMali-Process", "Process output callback failed", error);
                        }
                    }
                    else {
                        ArrayList<Callback<String>> callbacks;
                        synchronized (debugCallbacks) {
                            callbacks = new ArrayList<>(debugCallbacks);
                        }
                        for (Callback<String> callback : callbacks) {
                            try {
                                callback.call(line);
                            }
                            catch (RuntimeException ignored) {}
                        }
                        if (callbacks.isEmpty() && MainActivity.DEBUG_MODE) System.out.println(line);
                    }
                }
            }
            catch (IOException ignored) {}
        }, "WinMali-process-output");
        worker.setDaemon(true);
        worker.start();
        return worker;
    }

    private static void createWaitForThread(java.lang.Process process, Thread stdoutWorker,
            Thread stderrWorker, final Callback<Integer> terminationCallback) {
        Thread worker = new Thread(() -> {
            try {
                int exitCode = process.waitFor();

                // Drain short-lived stdout/stderr readers before notifying consumers.
                // Wine --version output is small, but the reader can otherwise lose a
                // race against waitFor() and report a null version intermittently.
                if (stdoutWorker != null) stdoutWorker.join(2500);
                if (stderrWorker != null) stderrWorker.join(2500);

                terminationCallback.call(exitCode);
            }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            catch (RuntimeException error) {
                android.util.Log.w("WinMali-Process", "Process termination callback failed", error);
            }
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
        if (callback == null) return;
        synchronized (debugCallbacks) {
            if (!debugCallbacks.contains(callback)) debugCallbacks.add(callback);
        }
    }

    public static void removeDebugCallback(Callback<String> callback) {
        if (callback == null) return;
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

    public static int getAffinityMaskAsInt(String cpuList) {
        if (cpuList == null || cpuList.trim().isEmpty()) return 0;
        int affinityMask = 0;
        for (String value : cpuList.split(",")) {
            try {
                int index = Integer.parseInt(value.trim());
                if (index >= 0 && index < Integer.SIZE) affinityMask |= (1 << index);
            }
            catch (NumberFormatException ignored) {}
        }
        return affinityMask;
    }

    public static long getAffinityMaskAsLong(String cpuList) {
        if (cpuList == null || cpuList.trim().isEmpty()) return 0L;
        long affinityMask = 0L;
        for (String value : cpuList.split(",")) {
            try {
                int index = Integer.parseInt(value.trim());
                if (index >= 0 && index < Long.SIZE) affinityMask |= (1L << index);
            }
            catch (NumberFormatException ignored) {}
        }
        return affinityMask;
    }

    public static String getAffinityMaskAsHexString(String cpuList) {
        return Long.toHexString(getAffinityMaskAsLong(cpuList));
    }

    public static int getAffinityMask(String cpuList) {
        return getAffinityMaskAsInt(cpuList);
    }

    public static int getAffinityMask(boolean[] cpuList) {
        if (cpuList == null) return 0;
        int affinityMask = 0;
        for (int i = 0; i < cpuList.length && i < Integer.SIZE; i++) {
            if (cpuList[i]) affinityMask |= (1 << i);
        }
        return affinityMask;
    }

    public static int getAffinityMask(int from, int to) {
        int affinityMask = 0;
        int start = Math.max(0, from);
        int end = Math.min(Integer.SIZE, to);
        for (int i = start; i < end; i++) affinityMask |= (1 << i);
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

    static List<String> parseCommandLine(String commandLine) {
        if (commandLine == null || commandLine.isEmpty()) return Collections.emptyList();
        ArrayList<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < commandLine.length(); i++) {
            if (commandLine.charAt(i) == '\0') {
                result.add(commandLine.substring(start, i));
                start = i + 1;
            }
        }
        if (start < commandLine.length()) result.add(commandLine.substring(start));
        return result;
    }

    public static List<String> getProcessCmdLine(int pid) {
        try {
            return parseCommandLine(FileUtils.readString(new File("/proc/"+pid+"/cmdline")));
        }
        catch (RuntimeException error) {
            return Collections.emptyList();
        }
    }

    static PStat parseProcessStat(String statLine) {
        if (statLine == null) return null;
        int openParen = statLine.indexOf('(');
        int closeParen = statLine.lastIndexOf(')');
        if (openParen <= 0 || closeParen <= openParen) return null;

        try {
            PStat pstat = new PStat();
            pstat.pid = Integer.parseInt(statLine.substring(0, openParen).trim());
            pstat.shortName = statLine.substring(openParen + 1, closeParen);
            String remaining = statLine.substring(closeParen + 1).trim();
            String[] fields = remaining.split("\\s+");
            if (fields.length < 2) return null;

            switch (fields[0]) {
                case "R": pstat.state = PState.RUNNING; break;
                case "S": pstat.state = PState.SLEEPING; break;
                case "D": pstat.state = PState.WAITING; break;
                case "Z": pstat.state = PState.ZOMBIE; break;
                case "T": pstat.state = PState.STOPPED; break;
                case "X":
                case "x": pstat.state = PState.DEAD; break;
                default: pstat.state = PState.OTHER; break;
            }
            pstat.parentPID = Integer.parseInt(fields[1]);
            return pstat;
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    static List<PStat> collectDescendants(List<PStat> processes, int rootPID) {
        if (processes == null || processes.isEmpty()) return Collections.emptyList();

        Map<Integer, ArrayList<PStat>> childrenByParent = new HashMap<>();
        for (PStat process : processes) {
            if (process == null || process.pid <= 0 || process.pid == rootPID) continue;
            childrenByParent.computeIfAbsent(process.parentPID, key -> new ArrayList<>()).add(process);
        }

        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        ArrayList<PStat> descendants = new ArrayList<>();
        visited.add(rootPID);
        pending.add(rootPID);

        while (!pending.isEmpty()) {
            ArrayList<PStat> children = childrenByParent.get(pending.removeFirst());
            if (children == null) continue;
            for (PStat child : children) {
                if (visited.add(child.pid)) {
                    descendants.add(child);
                    pending.addLast(child.pid);
                }
            }
        }

        descendants.sort(Comparator.comparingInt(process -> process.pid));
        return descendants;
    }

    public static List<PStat> getChildProcesses() {
        File procFile = new File("/proc");
        String[] pids = procFile.list((file, name) -> (new File(file, name)).isDirectory() && name.matches("[0-9]+"));
        if (pids == null) return Collections.emptyList();

        ArrayList<PStat> processes = new ArrayList<>();
        for (String pid : pids) {
            try {
                PStat pstat = parseProcessStat(FileUtils.readString(new File("/proc/" + pid + "/stat")));
                if (pstat != null) processes.add(pstat);
            }
            catch (RuntimeException ignored) {
                // A process may exit after /proc was enumerated. Keep the other results.
            }
        }

        List<PStat> descendants = collectDescendants(processes, Os.getpid());
        for (PStat process : descendants) {
            process.name = getProcessName(process.pid);
            if (process.name.isEmpty()) process.name = process.shortName;
            String normalizedName = process.name.toLowerCase(java.util.Locale.ENGLISH);
            process.guestProcess = normalizedName.contains("wine") || normalizedName.contains(".exe");
        }
        return descendants;
    }
}
