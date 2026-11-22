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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class HttpUtils {
    private static final long MAX_DOWNLOAD_BYTES = 1024L * 1024L * 1024L;

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

        if (onDownloadComplete != null) {
            try {
                onDownloadComplete.call(result);
            }
            catch (RuntimeException error) {
                android.util.Log.w("WinMali-Download", "Text download callback failed", error);
            }
        }
    }

    public static void download(final String url, final Callback<String> onDownloadComplete) {
        DOWNLOAD_OPERATIONS.execute(() -> downloadAsync(url, onDownloadComplete));
    }

    private static void downloadAsync(String url, File destination, AtomicBoolean interruptRef,
            Callback<Integer> onPublishProgress, Callback<Boolean> onDownloadComplete) {
        HttpURLConnection connection = null;
        boolean success = false;
        try {
            if (url == null || destination == null || interruptRef == null) {
                throw new IOException("Download arguments are invalid");
            }

            File parent = destination.getAbsoluteFile().getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                throw new IOException("Unable to prepare download destination");
            }

            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                long contentLength = connection.getContentLengthLong();
                if (contentLength == 0 || contentLength > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Download size is invalid or exceeds the configured limit");
                }

                try (InputStream inStream = new BufferedInputStream(
                        connection.getInputStream(), StreamUtils.BUFFER_SIZE);
                     OutputStream outStream = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    long totalSize = 0;
                    int bytesRead;
                    while (!interruptRef.get() && (bytesRead = inStream.read(buffer)) != -1) {
                        totalSize += bytesRead;
                        if (totalSize > MAX_DOWNLOAD_BYTES) {
                            throw new IOException("Download exceeds the configured size limit");
                        }
                        if (onPublishProgress != null && contentLength > 0) {
                            int progress = (int)Math.min(100L, (totalSize * 100L) / contentLength);
                            onPublishProgress.call(progress);
                        }
                        outStream.write(buffer, 0, bytesRead);
                    }
                    outStream.flush();
                    success = !interruptRef.get() && totalSize > 0 &&
                        (contentLength < 0 || totalSize == contentLength);
                }
            }
            else {
                throw new IOException("HTTP download returned status "+connection.getResponseCode());
            }
        }
        catch (Exception error) {
            android.util.Log.w("WinMali-Download", "File download failed", error);
            success = false;
        }
        finally {
            if (connection != null) connection.disconnect();
        }

        if (onDownloadComplete != null) {
            try {
                onDownloadComplete.call(success);
            }
            catch (RuntimeException error) {
                android.util.Log.w("WinMali-Download", "File download callback failed", error);
            }
        }
    }

    public static void download(final Activity activity, final String url, final File destination,
            final Callback<Boolean> onDownloadComplete) {
        if (activity == null || destination == null) {
            if (onDownloadComplete != null) onDownloadComplete.call(false);
            return;
        }

        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            if (onDownloadComplete != null) onDownloadComplete.call(false);
            return;
        }

        final File staging;
        try {
            staging = File.createTempFile(destination.getName()+".", ".download", parent);
        }
        catch (IOException error) {
            if (onDownloadComplete != null) onDownloadComplete.call(false);
            return;
        }

        final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
        final AtomicBoolean interruptRef = new AtomicBoolean(false);
        dialog.show(() -> interruptRef.set(true));

        DOWNLOAD_OPERATIONS.execute(() -> {
            downloadAsync(url, staging, interruptRef, (progress) -> {
                activity.runOnUiThread(() -> dialog.setProgress(progress));
            }, (downloaded) -> {
                boolean installed = false;
                try {
                    if (downloaded && staging.isFile() && staging.length() > 0) {
                        try {
                            Files.move(staging.toPath(), destination.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                        }
                        catch (AtomicMoveNotSupportedException unsupported) {
                            Files.move(staging.toPath(), destination.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        installed = destination.isFile() && destination.length() > 0;
                    }
                }
                catch (IOException | RuntimeException error) {
                    android.util.Log.w("WinMali-Download", "Unable to activate downloaded file", error);
                    installed = false;
                }
                finally {
                    if (staging.exists()) staging.delete();
                }

                final boolean result = installed;
                activity.runOnUiThread(() -> {
                    dialog.close();
                    if (onDownloadComplete != null) {
                        try {
                            onDownloadComplete.call(result);
                        }
                        catch (RuntimeException error) {
                            android.util.Log.w("WinMali-Download", "Download completion callback failed", error);
                        }
                    }
                });
            });
        });
    }
}
