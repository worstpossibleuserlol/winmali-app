package com.winlator.winhandler;

import org.junit.Test;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WinHandlerTest {
    @Test
    public void failedSocketBindLeavesHandlerStopped() throws Exception {
        try (DatagramSocket blocker = new DatagramSocket(null)) {
            // Do not enable SO_REUSEADDR: the handler's listener must not
            // be able to share this port with an already-bound process.
            blocker.setReuseAddress(false);
            blocker.bind(new InetSocketAddress(7947));

            WinHandler handler = new WinHandler(null);
            try {
                assertFalse("start must fail when the UDP port is already occupied", handler.start());

                Field runningField = WinHandler.class.getDeclaredField("running");
                runningField.setAccessible(true);
                assertFalse("WinHandler must not report itself running when its UDP port could not be bound",
                    runningField.getBoolean(handler));
            }
            finally {
                handler.stop();
            }
        }
    }
    @Test
    public void fullProcessMessageIsParsedAfterOneByteInitHandshake() throws Exception {
        WinHandler handler = new WinHandler(null);
        CountDownLatch processReceived = new CountDownLatch(1);
        AtomicReference<ProcessInfo> processInfo = new AtomicReference<>();
        handler.setOnGetProcessInfoListener((index, count, info) -> {
            processInfo.set(info);
            processReceived.countDown();
        });

        try (DatagramSocket sender = new DatagramSocket()) {
            assertTrue("WinHandler should bind before start returns", handler.start());
            InetAddress loopback = InetAddress.getByName("127.0.0.1");

            byte[] init = new byte[]{RequestCodes.INIT};
            sender.send(new DatagramPacket(init, init.length, loopback, 7947));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!handler.initReceived && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertTrue("one-byte INIT handshake should be received", handler.initReceived);

            byte[] name = new byte[32];
            byte[] encodedName = "sample.exe".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(encodedName, 0, name, 0, encodedName.length);

            ByteBuffer message = ByteBuffer.allocate(1 + 4 + 2 + 2 + 4 + 8 + 4 + 1 + name.length)
                .order(ByteOrder.LITTLE_ENDIAN);
            message.put(RequestCodes.GET_PROCESS);
            message.putInt(53);
            message.putShort((short)1);
            message.putShort((short)0);
            message.putInt(1234);
            message.putLong(4096L);
            message.putInt(3);
            message.put((byte)1);
            message.put(name);

            byte[] request = message.array();
            sender.send(new DatagramPacket(request, request.length, loopback, 7947));

            assertTrue("full GET_PROCESS message should be parsed after INIT",
                processReceived.await(2, TimeUnit.SECONDS));
            assertNotNull(processInfo.get());
            assertEquals("sample.exe", processInfo.get().name);
            assertEquals(1234, processInfo.get().pid);
            assertEquals(4096L, processInfo.get().memoryUsage);
            assertTrue(processInfo.get().wow64Process);
        }
        finally {
            handler.stop();
        }
    }
}
