package com.wish.client.color;

import java.util.Locale;

public final class ColorUtils {

    private ColorUtils() {}

    public static float[] rgbIntToHsb(int rgb, float[] out) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return rgbToHsb(r, g, b, out);
    }

    public static float[] rgbToHsb(int r, int g, int b, float[] out) {
        if (out == null || out.length < 3) {
            out = new float[3];
        }
        int cmax = Math.max(r, Math.max(g, b));
        int cmin = Math.min(r, Math.min(g, b));
        float brightness = cmax / 255.0f;
        float saturation = (cmax != 0) ? ((float) (cmax - cmin)) / cmax : 0.0f;
        float hue;
        if (saturation == 0.0f) {
            hue = 0.0f;
        } else {
            float redc = ((float) (cmax - r)) / (cmax - cmin);
            float greenc = ((float) (cmax - g)) / (cmax - cmin);
            float bluec = ((float) (cmax - b)) / (cmax - cmin);
            if (r == cmax) {
                hue = bluec - greenc;
            } else if (g == cmax) {
                hue = 2.0f + redc - bluec;
            } else {
                hue = 4.0f + greenc - redc;
            }
            hue /= 6.0f;
            if (hue < 0.0f) {
                hue += 1.0f;
            }
        }
        out[0] = hue;
        out[1] = saturation;
        out[2] = brightness;
        return out;
    }

    public static int hsbToRgb(float hue, float saturation, float brightness) {
        float sat = Math.max(0.0f, Math.min(1.0f, saturation));
        float bri = Math.max(0.0f, Math.min(1.0f, brightness));
        int r = 0;
        int g = 0;
        int b = 0;
        if (sat == 0.0f) {
            r = g = b = (int) (bri * 255.0f + 0.5f);
        } else {
            double h = (hue - Math.floor(hue)) * 6.0;
            double f = h - Math.floor(h);
            double p = bri * (1.0 - sat);
            double q = bri * (1.0 - sat * f);
            double t = bri * (1.0 - sat * (1.0 - f));
            switch ((int) h) {
                case 0 -> {
                    r = (int) (bri * 255.0f + 0.5f);
                    g = (int) (t * 255.0 + 0.5);
                    b = (int) (p * 255.0 + 0.5);
                }
                case 1 -> {
                    r = (int) (q * 255.0 + 0.5);
                    g = (int) (bri * 255.0f + 0.5f);
                    b = (int) (p * 255.0 + 0.5);
                }
                case 2 -> {
                    r = (int) (p * 255.0 + 0.5);
                    g = (int) (bri * 255.0f + 0.5f);
                    b = (int) (t * 255.0 + 0.5);
                }
                case 3 -> {
                    r = (int) (p * 255.0 + 0.5);
                    g = (int) (q * 255.0 + 0.5);
                    b = (int) (bri * 255.0f + 0.5f);
                }
                case 4 -> {
                    r = (int) (t * 255.0 + 0.5);
                    g = (int) (p * 255.0 + 0.5);
                    b = (int) (bri * 255.0f + 0.5f);
                }
                case 5 -> {
                    r = (int) (bri * 255.0f + 0.5f);
                    g = (int) (p * 255.0 + 0.5);
                    b = (int) (q * 255.0 + 0.5);
                }
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int parseHexRgb(String hex, int fallbackRgb) {
        if (hex == null) {
            return fallbackRgb;
        }
        String trimmed = hex.trim();
        int start = trimmed.startsWith("#") ? 1 : 0;
        if (trimmed.length() - start != 6) {
            return fallbackRgb;
        }
        try {
            return Integer.parseInt(trimmed, start, trimmed.length(), 16);
        } catch (NumberFormatException ignored) {
            return fallbackRgb;
        }
    }

    public static String formatHexRgb(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static float wheelOffsetToHue(float dx, float dy) {
        double angle = Math.atan2(dy, dx);
        if (angle < 0.0) {
            angle += Math.PI * 2.0;
        }
        float hue = (float) (angle / (Math.PI * 2.0));
        return hue >= 1.0f ? 0.0f : hue;
    }

    public static float wheelOffsetToSaturation(float dx, float dy, float radius) {
        if (radius <= 0.0f) {
            return 0.0f;
        }
        return Math.min(1.0f, (float) Math.hypot(dx, dy) / radius);
    }

    public static float hsbToWheelX(float hue, float sat, float radius) {
        float clampedSat = Math.max(0.0f, Math.min(1.0f, sat));
        return (float) Math.cos(hue * Math.PI * 2.0) * clampedSat * radius;
    }

    public static float hsbToWheelY(float hue, float sat, float radius) {
        float clampedSat = Math.max(0.0f, Math.min(1.0f, sat));
        return (float) Math.sin(hue * Math.PI * 2.0) * clampedSat * radius;
    }
}
