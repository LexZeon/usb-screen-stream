package com.example.usbscreenstream;

final class StreamConfig {
    final String mode;
    final int width;
    final int height;
    final int bitrate;
    final int fps;
    final int quality;

    private StreamConfig(String mode, int width, int height, int bitrate, int fps, int quality) {
        this.mode = mode == null ? "mjpeg" : mode;
        this.width = clamp(width, 320, 2400);
        this.height = clamp(height, 180, 2400);
        this.bitrate = clamp(bitrate, 250_000, 8_000_000);
        this.fps = clamp(fps, 10, 60);
        this.quality = clamp(quality, 20, 90);
    }

    static StreamConfig defaults() {
        return new StreamConfig("mjpeg", 360, 800, 600_000, 30, 45);
    }

    static StreamConfig parse(String line) {
        String mode = "mjpeg";
        int width = 360;
        int height = 800;
        int bitrate = 600_000;
        int fps = 30;
        int quality = 45;

        if (line != null) {
            String[] parts = line.trim().split(";");
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                String key = kv[0].trim().toLowerCase();
                if ("width".equals(key)) {
                    width = parseInt(kv[1], width);
                } else if ("height".equals(key)) {
                    height = parseInt(kv[1], height);
                } else if ("bitrate".equals(key)) {
                    bitrate = parseInt(kv[1], bitrate);
                } else if ("fps".equals(key)) {
                    fps = parseInt(kv[1], fps);
                } else if ("quality".equals(key)) {
                    quality = parseInt(kv[1], quality);
                } else if ("mode".equals(key)) {
                    mode = kv[1].trim().toLowerCase();
                }
            }
        }

        return new StreamConfig(mode, width, height, bitrate, fps, quality);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
