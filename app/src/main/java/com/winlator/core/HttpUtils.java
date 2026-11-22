package com.winlator.core;

import android.app.Activity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class HttpUtils {
    private static final ExecutorService DOWNLOAD_OPERATIONS = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "WinMali-download");
        thread.setDaemon(true);
        return thread;
    });

    private static void downloadAsync(String url, Callback<String> onDownloadComplete) {
        HttpURLConnection connection = null;
        String result = null;
        try {
            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                byte[] bytes;
                try (InputStream inStream = connection.getInputStream()) {
                    bytes = StreamUtils.copyToByteArray(inStream);
                }
                result = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        catch (Exception ignored) {
            result = null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }

        if (onDownloadComplete != null) onDownloadComplete.call(result);
    }

    public static void download(final String url, final Callback<String> onDownloadComplete) {
        DOWNLOAD_OPERATIONS.execute(() -> downloadAsync(url, onDownloadComplete));
    }

    private static void downloadAsync(String url, File destination, AtomicBoolean interruptRef,
            Callback<Integer> onPublishProgress, Callback<Boolean> onDownloadComplete) {
        HttpURLConnection connection = null;
        boolean success = false;
        try {
            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                long contentLength = connection.getContentLengthLong();
                try (InputStream inStream = new BufferedInputStream(connection.getInputStream(), StreamUtils.BUFFER_SIZE);
                     OutputStream outStream = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    long totalSize = 0;
                    int bytesRead;
                    while (!interruptRef.get() && (bytesRead = inStream.read(buffer)) != -1) {
                        totalSize += bytesRead;
                        if (onPublishProgress != null && contentLength > 0) {
                            int progress = (int)Math.min(100L, (totalSize * 100L) / contentLength);
                            onPublishProgress.call(progress);
                        }
                        outStream.write(buffer, 0, bytesRead);
                    }
                    outStream.flush();
                    success = !interruptRef.get() && (contentLength < 0 || totalSize == contentLength);
                }
            }
        }
        catch (Exception ignored) {
            success = false;
        }
        finally {
            if (connection != null) connection.disconnect();
        }

        if (onDownloadComplete != null) onDownloadComplete.call(success);
    }

    public static void download(final Activity activity, final String url, final File destination, final Callback<Boolean> onDownloadComplete) {
        final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
        final AtomicBoolean interruptRef = new AtomicBoolean(false);
        dialog.show(() -> interruptRef.set(true));
        DOWNLOAD_OPERATIONS.execute(() -> {
            downloadAsync(url, destination, interruptRef, (progress) -> {
                activity.runOnUiThread(() -> dialog.setProgress(progress));
            }, (success) -> {
                if (!success && destination.isFile()) destination.delete();
                activity.runOnUiThread(() -> {
                    dialog.close();
                    onDownloadComplete.call(success);
                });
            });
        });
    }
}
