package com.winlator.winhandler;

import org.junit.Test;

import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
                handler.start();

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
    public void receivePacketRestoresCapacityAfterShortDatagram() throws Exception {
        WinHandler handler = new WinHandler(null);
        Field initReceivedField = WinHandler.class.getDeclaredField("initReceived");
        Field receivePacketField = WinHandler.class.getDeclaredField("receivePacket");
        initReceivedField.setAccessible(true);
        receivePacketField.setAccessible(true);

        try (DatagramSocket firstSender = new DatagramSocket();
             DatagramSocket secondSender = new DatagramSocket()) {
            handler.start();
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            byte[] init = new byte[]{RequestCodes.INIT};
            DatagramPacket initPacket = new DatagramPacket(init, init.length, loopback, 7947);

            long deadline = System.nanoTime() + 2_000_000_000L;
            while (!initReceivedField.getBoolean(handler) && System.nanoTime() < deadline) {
                firstSender.send(initPacket);
                Thread.sleep(10);
            }
            assertTrue("initial one-byte handshake should reach WinHandler",
                initReceivedField.getBoolean(handler));

            byte[] command = new byte[]{(byte)127, 1, 2, 3, 4, 5, 6, 7};
            DatagramPacket commandPacket = new DatagramPacket(command, command.length, loopback, 7947);
            secondSender.send(commandPacket);

            DatagramPacket received = (DatagramPacket)receivePacketField.get(handler);
            deadline = System.nanoTime() + 2_000_000_000L;
            while (received.getPort() != secondSender.getLocalPort() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }

            assertEquals("subsequent messages must receive the full buffer length",
                command.length, received.getLength());
        }
        finally {
            handler.stop();
        }
    }

}
