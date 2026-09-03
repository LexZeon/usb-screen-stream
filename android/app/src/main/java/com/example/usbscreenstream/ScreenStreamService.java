package com.example.usbscreenstream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenStreamService extends Service {
    public static final String ACTION_START = "com.example.usbscreenstream.START";
    public static final String ACTION_STOP = "com.example.usbscreenstream.STOP";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String TAG = "UsbScreenStream";
    private static final String CHANNEL_ID = "screen_stream";
    private static final int NOTIFICATION_ID = 7;
    private static final int PORT = 27183;
    private static final String MIME_TYPE = "video/avc";
    private static final byte[] START_CODE = new byte[]{0, 0, 0, 1};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private MediaProjection mediaProjection;
    private ServerSocket serverSocket;
    private MediaCodec encoder;
    private VirtualDisplay virtualDisplay;
    private Surface inputSurface;
    private ImageReader imageReader;
    private byte[] codecConfig;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand action=" + (intent == null ? "<null>" : intent.getAction()));
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopStreaming();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            try {
                Notification notification = createNotification("Waiting for Windows client");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
                int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
                Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
                MediaProjectionManager manager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mediaProjection = manager.getMediaProjection(resultCode, resultData);
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.i(TAG, "MediaProjection stopped by system");
                        running.set(false);
                        if (serverSocket != null) {
                            try {
                                serverSocket.close();
                            } catch (IOException ignored) {
                            }
                        }
                        stopEncoderOnly();
                        stopSelf();
                    }
                }, new Handler(Looper.getMainLooper()));
                startServer();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to start screen stream service", ex);
                stopSelf();
            }
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopStreaming();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void startServer() {
        if (mediaProjection == null || !running.compareAndSet(false, true)) {
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try (ServerSocket listener = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))) {
                    serverSocket = listener;
                    Log.i(TAG, "Listening on 127.0.0.1:" + PORT);
                while (running.get()) {
                    try (Socket client = listener.accept()) {
                        client.setTcpNoDelay(true);
                        client.setSendBufferSize(64 * 1024);
                        handleClient(client);
                    } catch (IOException clientError) {
                        if (running.get()) {
                            Log.w(TAG, "Client failed", clientError);
                        }
                    } catch (RuntimeException runtimeError) {
                        Log.e(TAG, "Streaming runtime failure", runtimeError);
                    } finally {
                        stopEncoderOnly();
                    }
                    }
                } catch (IOException serverError) {
                    Log.e(TAG, "Server failed", serverError);
                } finally {
                    running.set(false);
                    stopSelf();
                }
            }
        });
    }

    private void handleClient(Socket client) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                client.getInputStream(), StandardCharsets.US_ASCII));
        String configLine = reader.readLine();
        StreamConfig config = StreamConfig.parse(configLine);
        Log.i(TAG, "Client connected mode=" + config.mode + " config " + config.width + "x" + config.height
                + " bitrate=" + config.bitrate + " fps=" + config.fps + " quality=" + config.quality);
        updateNotification("Streaming " + config.width + "x" + config.height + " @ " + config.fps + " FPS");
        if ("h264".equals(config.mode)) {
            startEncoder(config);
            drainEncoder(client.getOutputStream());
        } else {
            startMjpeg(config);
            drainMjpeg(client.getOutputStream(), config);
        }
    }

    private void startMjpeg(StreamConfig config) {
        imageReader = ImageReader.newInstance(config.width, config.height, PixelFormat.RGBA_8888, 2);
        int density = getResources().getDisplayMetrics().densityDpi;
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "UsbScreenStreamMjpeg",
                config.width,
                config.height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null);
        Log.i(TAG, "MJPEG virtual display created at density " + density);
    }

    private void drainMjpeg(OutputStream output, StreamConfig config) throws IOException {
        long minFrameGapMs = Math.max(1, 1000 / config.fps);
        long frameCount = 0;
        long byteCount = 0;
        long lastFrameMs = 0;
        long lastLogMs = System.currentTimeMillis();

        while (running.get()) {
            long now = System.currentTimeMillis();
            if (now - lastFrameMs < minFrameGapMs) {
                sleepQuietly(2);
                continue;
            }

            Image image = imageReader.acquireLatestImage();
            if (image == null) {
                sleepQuietly(2);
                continue;
            }

            try {
                byte[] jpeg = imageToJpeg(image, config.width, config.height, config.quality);
                writeInt(output, jpeg.length);
                output.write(jpeg);
                output.flush();
                frameCount++;
                byteCount += jpeg.length;
                lastFrameMs = now;
                if (now - lastLogMs >= 1000) {
                    Log.i(TAG, "MJPEG frames=" + frameCount + " bytes=" + byteCount);
                    lastLogMs = now;
                }
            } finally {
                image.close();
            }
        }
    }

    private byte[] imageToJpeg(Image image, int width, int height, int quality) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        int bitmapWidth = width + rowPadding / pixelStride;

        Bitmap padded = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap bitmap = bitmapWidth == width ? padded : Bitmap.createBitmap(padded, 0, 0, width, height);

        ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        if (bitmap != padded) {
            bitmap.recycle();
        }
        padded.recycle();
        return out.toByteArray();
    }

    private void writeInt(OutputStream output, int value) throws IOException {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void startEncoder(StreamConfig config) throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, config.width, config.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        if (Build.VERSION.SDK_INT >= 23) {
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
            format.setInteger(MediaFormat.KEY_OPERATING_RATE, config.fps);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            format.setInteger("max-bframes", 0);
        }

        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = encoder.createInputSurface();
        encoder.start();
        Log.i(TAG, "Encoder started: " + format);

        int density = getResources().getDisplayMetrics().densityDpi;
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "UsbScreenStream",
                config.width,
                config.height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null);
        Log.i(TAG, "Virtual display created at density " + density);
    }

    private void drainEncoder(OutputStream output) throws IOException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long frameCount = 0;
        long byteCount = 0;
        long lastLogMs = System.currentTimeMillis();

        while (running.get()) {
            int index = encoder.dequeueOutputBuffer(info, 10_000);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                codecConfig = collectCodecConfig(encoder.getOutputFormat());
                Log.i(TAG, "Output format changed: " + encoder.getOutputFormat());
                if (codecConfig != null) {
                    output.write(codecConfig);
                    output.flush();
                }
                continue;
            }
            if (index < 0) {
                continue;
            }

            ByteBuffer encoded = encoder.getOutputBuffer(index);
            if (encoded != null && info.size > 0) {
                byte[] accessUnit = new byte[info.size];
                encoded.position(info.offset);
                encoded.limit(info.offset + info.size);
                encoded.get(accessUnit);

                boolean isConfig = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                boolean isKeyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                if (isConfig) {
                    codecConfig = withStartCodes(accessUnit);
                    output.write(codecConfig);
                } else {
                    if (isKeyFrame && codecConfig != null) {
                        output.write(codecConfig);
                        byteCount += codecConfig.length;
                    }
                    byte[] frame = withStartCodes(accessUnit);
                    output.write(frame);
                    byteCount += frame.length;
                }
                output.flush();
                frameCount++;
                long now = System.currentTimeMillis();
                if (now - lastLogMs >= 1000) {
                    Log.i(TAG, "Encoded frames=" + frameCount + " bytes=" + byteCount);
                    lastLogMs = now;
                }
            }

            encoder.releaseOutputBuffer(index, false);
        }
    }

    private byte[] collectCodecConfig(MediaFormat format) {
        byte[] sps = getByteBuffer(format, "csd-0");
        byte[] pps = getByteBuffer(format, "csd-1");
        if (sps == null && pps == null) {
            return null;
        }
        byte[] left = sps == null ? new byte[0] : withStartCodes(sps);
        byte[] right = pps == null ? new byte[0] : withStartCodes(pps);
        byte[] combined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }

    private byte[] getByteBuffer(MediaFormat format, String key) {
        if (!format.containsKey(key)) {
            return null;
        }
        ByteBuffer buffer = format.getByteBuffer(key);
        if (buffer == null) {
            return null;
        }
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    private byte[] withStartCodes(byte[] data) {
        if (hasStartCode(data)) {
            return data;
        }

        if (looksLengthPrefixed(data)) {
            return convertLengthPrefixedToAnnexB(data);
        }

        byte[] result = Arrays.copyOf(START_CODE, START_CODE.length + data.length);
        System.arraycopy(data, 0, result, START_CODE.length, data.length);
        return result;
    }

    private boolean hasStartCode(byte[] data) {
        return data.length >= 4 && data[0] == 0 && data[1] == 0
                && ((data[2] == 1) || (data[2] == 0 && data[3] == 1));
    }

    private boolean looksLengthPrefixed(byte[] data) {
        if (data.length < 5) {
            return false;
        }
        int length = ((data[0] & 0xff) << 24)
                | ((data[1] & 0xff) << 16)
                | ((data[2] & 0xff) << 8)
                | (data[3] & 0xff);
        return length > 0 && length <= data.length - 4;
    }

    private byte[] convertLengthPrefixedToAnnexB(byte[] data) {
        byte[] result = new byte[data.length];
        int source = 0;
        int target = 0;
        while (source + 4 <= data.length) {
            int length = ((data[source] & 0xff) << 24)
                    | ((data[source + 1] & 0xff) << 16)
                    | ((data[source + 2] & 0xff) << 8)
                    | (data[source + 3] & 0xff);
            source += 4;
            if (length <= 0 || source + length > data.length) {
                break;
            }
            System.arraycopy(START_CODE, 0, result, target, START_CODE.length);
            target += START_CODE.length;
            System.arraycopy(data, source, result, target, length);
            target += length;
            source += length;
        }
        return Arrays.copyOf(result, target);
    }

    private void stopStreaming() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        stopEncoderOnly();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void stopEncoderOnly() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (encoder != null) {
            try {
                encoder.stop();
            } catch (Exception ignored) {
            }
            encoder.release();
            encoder = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        codecConfig = null;
        updateNotification("Waiting for Windows client");
    }

    private Notification createNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen streaming",
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("USB Screen Stream")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setColor(getResources().getColor(com.example.usbscreenstream.R.color.notification_color))
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification(text));
    }
}
