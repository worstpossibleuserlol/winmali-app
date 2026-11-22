package com.winlator.winhandler;

import org.junit.Test;

import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertFalse;

public class WinHandlerTest {
    @Test
    public void failedSocketBindLeavesHandlerStopped() throws Exception {
        try (DatagramSocket blocker = new DatagramSocket(null)) {
            blocker.setReuseAddress(true);
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
}
