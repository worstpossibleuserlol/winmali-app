package com.winlator.winhandler;

import android.view.KeyEvent;
import android.view.MotionEvent;

import com.winlator.XServerDisplayActivity;
import com.winlator.core.Callback;
import com.winlator.core.DefaultVersion;
import com.winlator.core.FileUtils;
import com.winlator.core.GeneralComponents;
import com.winlator.core.StringUtils;
import com.winlator.xserver.XServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;

public class WinHandler {
    private static final int DEFAULT_SERVER_PORT = 7947;
    private static final short CLIENT_PORT = 7946;
    private final int serverPort;
    private volatile DatagramSocket socket;
    private Thread receiveThread;
    private Thread sendThread;
    private final Object sendLock = new Object();
    protected final ByteBuffer sendData = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
    protected final ByteBuffer receiveData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final DatagramPacket sendPacket = new DatagramPacket(sendData.array(), sendData.capacity());
    private final DatagramPacket receivePacket = new DatagramPacket(receiveData.array(), receiveData.capacity());
    private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
    protected volatile boolean initReceived = false;
    private volatile boolean running = false;
    private OnGetProcessInfoListener onGetProcessInfoListener;
    private OnPreExecListener onPreExecListener;
    private InetAddress localhost;
    protected final XServerDisplayActivity activity;
    private MIDIHandler midiHandler;
    public final GamepadHandler gamepadHandler = new GamepadHandler(this);
    private Callback<String> requestCallback;

    public WinHandler(XServerDisplayActivity activity) {
        this(activity, DEFAULT_SERVER_PORT);
    }

    WinHandler(XServerDisplayActivity activity, int serverPort) {
        if (serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("Invalid WinHandler UDP port: "+serverPort);
        }
        this.activity = activity;
        this.serverPort = serverPort;
    }

    protected boolean sendPacket(int port) {
        synchronized (sendLock) {
            DatagramSocket activeSocket = socket;
            if (activeSocket == null || activeSocket.isClosed()) return false;
            try {
                int size = sendData.position();
                if (size == 0) return false;
                sendPacket.setData(sendData.array(), 0, size);
                sendPacket.setAddress(localhost);
                sendPacket.setPort(port);
                activeSocket.send(sendPacket);
                return true;
            }
            catch (IOException | RuntimeException error) {
                return false;
            }
            finally {
                sendPacket.setData(sendData.array(), 0, sendData.capacity());
            }
        }
    }

    protected boolean sendPacket(int port, byte[] data) {
        if (data == null || data.length == 0) return false;
        synchronized (sendLock) {
            DatagramSocket activeSocket = socket;
            if (activeSocket == null || activeSocket.isClosed()) return false;
            try {
                sendPacket.setData(data);
                sendPacket.setAddress(localhost);
                sendPacket.setPort(port);
                activeSocket.send(sendPacket);
                return true;
            }
            catch (IOException | RuntimeException error) {
                return false;
            }
            finally {
                sendPacket.setData(sendData.array(), 0, sendData.capacity());
            }
        }
    }

    public void exec(final String filename, final String parameters) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.EXEC);
            byte[] filenameBytes = filename.getBytes();
            sendData.putInt(filenameBytes.length);
            sendData.put(filenameBytes);
            if (parameters != null) {
                byte[] parametersBytes = parameters.getBytes();
                sendData.putInt(parametersBytes.length);
                sendData.put(parametersBytes);
            }
            else sendData.putInt(0);
            sendPacket(CLIENT_PORT);
        });
    }

    public void exec(String command) {
        command = command.trim();
        if (command.isEmpty()) return;
        String[] cmdList = command.split(" ", 2);
        final String filename = cmdList[0];
        final String parameters = cmdList.length > 1 ? cmdList[1] : "";
        exec(filename, parameters);
    }

    public void killProcess(final String processName) {
        killProcess(processName, 0);
    }

    public void killProcess(final String processName, final int pid) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KILL_PROCESS);
            if (processName != null) {
                byte[] bytes = processName.getBytes();
                int minLength = Math.min(bytes.length, 247);
                sendData.putInt(minLength);
                sendData.put(bytes, 0, minLength);
            }
            else sendData.putInt(0);
            sendData.putInt(pid);
            sendPacket(CLIENT_PORT);
        });
    }

    public void listProcesses() {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.LIST_PROCESSES);
            sendData.putInt(0);

            if (!sendPacket(CLIENT_PORT) && onGetProcessInfoListener != null) {
                onGetProcessInfoListener.onGetProcessInfo(0, 0, null);
            }
        });
    }

    public void setProcessAffinity(final String processName, final int affinityMask) {
        addAction(() -> {
            byte[] bytes = processName.getBytes();
            sendData.rewind();
            sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            sendData.putInt(9 + bytes.length);
            sendData.putInt(0);
            sendData.putInt(affinityMask);
            sendData.put((byte)bytes.length);
            sendData.put(bytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void setProcessAffinity(final int pid, final int affinityMask) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            sendData.putInt(9);
            sendData.putInt(pid);
            sendData.putInt(affinityMask);
            sendData.put((byte)0);
            sendPacket(CLIENT_PORT);
        });
    }

    public void mouseEvent(int flags, int dx, int dy, int wheelDelta) {
        if (!initReceived) return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.MOUSE_EVENT);
            sendData.putInt(10);
            sendData.putInt(flags);
            sendData.putShort((short)dx);
            sendData.putShort((short)dy);
            sendData.putShort((short)wheelDelta);
            sendData.put((byte)((flags & MouseEventFlags.MOVE) != 0 ? 1 : 0)); // cursor pos feedback
            sendPacket(CLIENT_PORT);
        });
    }

    public void keyboardEvent(byte vkey, int flags) {
        if (!initReceived) return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KEYBOARD_EVENT);
            sendData.put(vkey);
            sendData.putInt(flags);
            sendPacket(CLIENT_PORT);
        });
    }

    public void bringToFront(final String processName) {
        bringToFront(processName, 0);
    }

    public void bringToFront(final String processName, final long handle) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.BRING_TO_FRONT);
            byte[] bytes = processName.getBytes();
            int minLength = Math.min(bytes.length, 242);
            sendData.putInt(minLength);
            sendData.put(bytes, 0, minLength);
            sendData.putLong(handle);
            sendPacket(CLIENT_PORT);
        });
    }

    public void showDesktop() {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.SHOW_DESKTOP);
            sendData.putInt(0);
            sendPacket(CLIENT_PORT);
        });
    }

    public void showWindow(final long handle, final int nCmdShow) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.SHOW_WINDOW);
            sendData.putLong(handle);
            sendData.putInt(nCmdShow);
            sendPacket(CLIENT_PORT);
        });
    }

    public void setClipboardData(final String data) {
        addAction(() -> {
            sendData.rewind();
            byte[] bytes = data.getBytes();
            int minLength = Math.min(bytes.length, 251);
            sendData.put(RequestCodes.SET_CLIPBOARD_DATA);
            sendData.putInt(minLength);
            sendData.put(bytes, 0, minLength);
            sendPacket(CLIENT_PORT);
        });
    }

    public String getExecutablePath(final int processId) {
        AtomicReference<String> pathRef = new AtomicReference<>("");
        requestCallback = pathRef::set;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.GET_EXECUTABLE_PATH);
            sendData.putInt(processId);
            if (!sendPacket(CLIENT_PORT)) {
                synchronized (requestCallback) {
                    requestCallback.notify();
                }
            }
        });
        synchronized (requestCallback) {
            try {
                requestCallback.wait(2000);
            }
            catch (InterruptedException e) {}
        }
        requestCallback = null;
        return pathRef.get();
    }

    protected void addAction(Runnable action) {
        synchronized (actions) {
            actions.add(action);
            actions.notify();
        }
    }

    public OnGetProcessInfoListener getOnGetProcessInfoListener() {
        return onGetProcessInfoListener;
    }

    public void setOnGetProcessInfoListener(OnGetProcessInfoListener onGetProcessInfoListener) {
        synchronized (actions) {
            this.onGetProcessInfoListener = onGetProcessInfoListener;
        }
    }

    public void setOnPreExecListener(OnPreExecListener onPreExecListener) {
        this.onPreExecListener = onPreExecListener;
    }

    private void startSendThread() {
        sendThread = new Thread(() -> {
            while (running) {
                synchronized (actions) {
                    while (running && initReceived && !actions.isEmpty()) {
                        actions.poll().run();
                    }
                    if (!running) break;
                    try {
                        actions.wait();
                    }
                    catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "WinMali-winhandler-send");
        sendThread.setDaemon(true);
        sendThread.start();
    }

    public synchronized void stop() {
        running = false;
        initReceived = false;

        DatagramSocket activeSocket = socket;
        socket = null;
        if (activeSocket != null) activeSocket.close();

        synchronized (actions) {
            actions.clear();
            actions.notifyAll();
        }

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        if (sendThread != null) {
            sendThread.interrupt();
            sendThread = null;
        }

        if (midiHandler != null) {
            midiHandler.close();
            midiHandler.destroy();
            midiHandler = null;
        }
    }

    private void handleRequest(byte requestCode, final int port) throws IOException {
        switch (requestCode) {
            case RequestCodes.INIT: {
                initReceived = true;

                synchronized (actions) {
                    actions.notify();
                }
                break;
            }
            case RequestCodes.GET_PROCESS: {
                if (onGetProcessInfoListener == null) return;
                receiveData.position(receiveData.position() + 4);
                int numProcesses = receiveData.getShort();
                int index = receiveData.getShort();
                int pid = receiveData.getInt();
                long memoryUsage = receiveData.getLong();
                int affinityMask = receiveData.getInt();
                boolean wow64Process = receiveData.get() == 1;

                byte[] bytes = new byte[32];
                receiveData.get(bytes);
                String name = StringUtils.fromANSIString(bytes);

                onGetProcessInfoListener.onGetProcessInfo(index, numProcesses, new ProcessInfo(pid, name, memoryUsage, affinityMask, wow64Process));
                break;
            }
            case RequestCodes.GET_GAMEPAD: {
                gamepadHandler.handleGetGamepadRequest(port);
                break;
            }
            case RequestCodes.RELEASE_GAMEPAD: {
                gamepadHandler.handleReleaseGamepadRequest(port);
                break;
            }
            case RequestCodes.SET_GAMEPAD_STATE: {
                gamepadHandler.handleSetGamepadStateRequest(port);
                break;
            }
            case RequestCodes.CURSOR_POS_FEEDBACK: {
                short x = receiveData.getShort();
                short y = receiveData.getShort();
                XServer xServer = activity.getXServer();
                xServer.pointer.setX(x);
                xServer.pointer.setY(y);
                activity.getXServerView().requestRender();
                break;
            }
            case RequestCodes.OPEN_URL: {
                int requestLength = receiveData.getInt();
                byte[] data = new byte[requestLength];
                socket.receive(new DatagramPacket(data, data.length));
                FileUtils.openIntent(activity, new String(data));
                break;
            }
            case RequestCodes.MIDI_OPEN: {
                boolean isMidiOut = receiveData.get() == 1;
                if (midiHandler == null) midiHandler = new MIDIHandler(this);
                if (isMidiOut) {
                    midiHandler.open(() -> {
                        if (midiHandler.init()) {
                            String soundfont = activity.getPreferences().getString("soundfont", DefaultVersion.SOUNDFONT);
                            midiHandler.loadSoundFont(GeneralComponents.getDefinitivePath(GeneralComponents.Type.SOUNDFONT, activity, soundfont));
                        }
                    });
                }
                else {
                    midiHandler.outputPortConnect();
                    midiHandler.addClient(port);
                }
                break;
            }
            case RequestCodes.MIDI_CLOSE: {
                if (midiHandler != null) {
                    midiHandler.outputPortDisconnect();
                    midiHandler.close();
                }
                break;
            }
            case RequestCodes.GET_EXECUTABLE_PATH: {
                int requestLength = receiveData.getInt();
                String path = "";
                if (requestLength > 0) {
                    byte[] data = new byte[requestLength];
                    socket.receive(new DatagramPacket(data, data.length));
                    path = new String(data, StandardCharsets.UTF_16LE);
                }
                if (requestCallback != null) {
                    synchronized (requestCallback) {
                        requestCallback.call(path);
                        requestCallback.notify();
                    }
                }
                break;
            }
            case RequestCodes.PRE_EXEC: {
                int requestLength = receiveData.getInt();
                boolean shouldExit = false;
                if (requestLength > 0) {
                    byte[] data = new byte[requestLength];
                    socket.receive(new DatagramPacket(data, data.length));
                    String path = new String(data, StandardCharsets.UTF_8);
                    if (onPreExecListener != null) shouldExit = onPreExecListener.onPreExec(path);
                }
                sendPacket(port, new byte[]{(byte)(shouldExit ? 1 : 0)});
                break;
            }
        }
    }

    private static void logSocketError(String message, Throwable error) {
        try {
            android.util.Log.e("WinMali-WinHandler", message, error);
        }
        catch (RuntimeException ignored) {
            // Android's Log facade is not implemented in the plain JVM unit-test
            // runtime. Logging must not turn a handled socket failure into a crash.
        }
    }

    /**
     * Binds the guest-control socket before returning. The guest's winhandler.exe
     * sends its INIT datagram as soon as Wine starts, so an asynchronous bind here
     * creates a startup race in which the first handshake is silently lost.
     */
    public synchronized boolean start() {
        if (running && socket != null && !socket.isClosed()) return true;

        DatagramSocket newSocket = null;
        try {
            localhost = InetAddress.getByName("127.0.0.1");
            newSocket = new DatagramSocket(null);
            // This is a single-instance control endpoint. Do not allow two
            // WinMali sessions to share the guest-control port.
            newSocket.setReuseAddress(false);
            newSocket.bind(new InetSocketAddress((InetAddress)null, serverPort));
            socket = newSocket;
            initReceived = false;
            running = true;

            startSendThread();
            final DatagramSocket boundSocket = newSocket;
            receiveThread = new Thread(() -> receiveLoop(boundSocket),
                "WinMali-winhandler-receive");
            receiveThread.setDaemon(true);
            receiveThread.start();
            return true;
        }
        catch (IOException | RuntimeException error) {
            running = false;
            initReceived = false;
            socket = null;
            if (newSocket != null) newSocket.close();
            logSocketError("Unable to bind guest-control UDP port "+serverPort, error);
            return false;
        }
    }

    private void receiveLoop(DatagramSocket activeSocket) {
        while (running && activeSocket == socket && !activeSocket.isClosed()) {
            try {
                // DatagramPacket keeps the length of the last received datagram.
                // Restore the full buffer before every receive or the one-byte INIT
                // packet truncates every subsequent guest-control message.
                receiveData.clear();
                receivePacket.setLength(receiveData.capacity());
                activeSocket.receive(receivePacket);
                if (receivePacket.getLength() < 1) continue;

                synchronized (actions) {
                    receiveData.position(0);
                    receiveData.limit(receivePacket.getLength());
                    byte requestCode = receiveData.get();
                    handleRequest(requestCode, receivePacket.getPort());
                    receiveData.clear();
                }
            }
            catch (SocketException error) {
                if (running) {
                    logSocketError("Guest-control socket stopped unexpectedly", error);
                }
                break;
            }
            catch (IOException | RuntimeException error) {
                if (running) {
                    logSocketError("Unable to process guest-control datagram", error);
                }
                receiveData.clear();
            }
        }
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        return gamepadHandler.onGenericMotionEvent(event);
    }

    public boolean onKeyEvent(KeyEvent event) {
        return gamepadHandler.onKeyEvent(event);
    }

    public MIDIHandler getMIDIhandler() {
        if (midiHandler == null) midiHandler = new MIDIHandler(this);
        return midiHandler;
    }
}
