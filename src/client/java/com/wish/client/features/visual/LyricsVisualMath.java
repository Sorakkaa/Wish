package com.wish.client.features.visual;

public final class LyricsVisualMath {

    public enum KaraokeRole {
        SUNG,
        ACTIVE,
        UPCOMING
    }

    private static final float FOREGROUND_SCALE_UNIT = 0.022f;
    private static final float BACKGROUND_SCALE_UNIT = 0.0208f;
    private static final float BACK_OVERSHOOT_C1 = 1.15f;
    private static final float BACK_OVERSHOOT_C3 = BACK_OVERSHOOT_C1 + 1.0f;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;

    private LyricsVisualMath() {}

    public static float clamp01(float value) {
        if (Float.isNaN(value) || value <= 0.0f) return 0.0f;
        return Math.min(1.0f, value);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    public static float easeOutCubic(float t) {
        float c = clamp01(t);
        float inv = 1.0f - c;
        return 1.0f - (inv * inv * inv);
    }

    public static float easeInCubic(float t) {
        float c = clamp01(t);
        return c * c * c;
    }

    public static float easeOutBack(float t) {
        float c = clamp01(t);
        float u = c - 1.0f;
        return 1.0f + BACK_OVERSHOOT_C3 * u * u * u + BACK_OVERSHOOT_C1 * u * u;
    }

    public static float easeInOutSine(float t) {
        float c = clamp01(t);
        return (float) (-(Math.cos(Math.PI * c) - 1.0) * 0.5);
    }

    public static int blendRgb(int rgbA, int rgbB, float t) {
        float k = clamp01(t);
        int ar = (rgbA >> 16) & 0xFF;
        int ag = (rgbA >> 8) & 0xFF;
        int ab = rgbA & 0xFF;

        int br = (rgbB >> 16) & 0xFF;
        int bg = (rgbB >> 8) & 0xFF;
        int bb = rgbB & 0xFF;

        int r = clampByte(Math.round(ar + (br - ar) * k));
        int g = clampByte(Math.round(ag + (bg - ag) * k));
        int b = clampByte(Math.round(ab + (bb - ab) * k));
        return (r << 16) | (g << 8) | b;
    }

    public static int scaleRgb(int rgb, float factor) {
        float safeFactor = Float.isNaN(factor) ? 1.0f : Math.max(0.0f, factor);
        int r = clampByte(Math.round(((rgb >> 16) & 0xFF) * safeFactor));
        int g = clampByte(Math.round(((rgb >> 8) & 0xFF) * safeFactor));
        int b = clampByte(Math.round((rgb & 0xFF) * safeFactor));
        return (r << 16) | (g << 8) | b;
    }

    public static int computeSkyHarmonyRgb(int customSkyMode) {
        return switch (customSkyMode) {
            case 1 -> 0xE89CFF;
            case 2 -> 0x7AD7FF;
            case 3 -> 0xC7E0FF;
            case 4 -> 0xFFB088;
            case 5 -> 0x66FFE0;
            case 6 -> 0xFF9ED2;
            case 7 -> 0x6CFF9E;
            case 8 -> 0xFF6B7A;
            case 9 -> 0xFFD966;
            default -> 0xD6C4FF;
        };
    }

    public static int computeDynamicTextRgb(
            int userBaseRgb,
            boolean customSkyEnabled,
            int customSkyMode,
            float timeSec,
            float phase,
            boolean isBgSymbol
    ) {
        int cleanBase = userBaseRgb & 0xFFFFFF;
        float wave = 0.5f + 0.5f * (float) Math.sin(timeSec * 0.9f + phase);
        if (!customSkyEnabled) {
            if (isBgSymbol) {
                int starlight = blendRgb(cleanBase, 0xFFF2FF, 0.36f + 0.14f * wave);
                return blendRgb(starlight, 0xFFFFFF, 0.24f);
            }
            return blendRgb(cleanBase, 0xD8E4FF, 0.04f * wave);
        }
        int skyAccent = computeSkyHarmonyRgb(customSkyMode);
        if (isBgSymbol) {
            int harmonized = blendRgb(cleanBase, skyAccent, 0.44f + 0.14f * wave);
            return blendRgb(harmonized, 0xFFFFFF, 0.28f);
        }
        return blendRgb(cleanBase, skyAccent, 0.16f + 0.08f * wave);
    }

    public static int computeKaraokeWordRgb(int baseRgb, int accentRgb, KaraokeRole role) {
        int cleanBase = baseRgb & 0xFFFFFF;
        int cleanAccent = accentRgb & 0xFFFFFF;
        if (role == null) return cleanBase;
        return switch (role) {
            case ACTIVE -> {
                int energized = blendRgb(cleanBase, cleanAccent, 0.28f);
                yield blendRgb(energized, 0xFFFFFF, 0.44f);
            }
            case SUNG -> blendRgb(cleanBase, cleanAccent, 0.16f);
            case UPCOMING -> blendRgb(scaleRgb(cleanBase, 0.62f), 0xB8C4D8, 0.18f);
        };
    }

    public static int computeContrastOutlineColor(int textRgb, int alphaByte) {
        int safeAlpha = clampByte(Math.round(clampByte(alphaByte) * 0.88f));
        int clean = textRgb & 0xFFFFFF;
        int r = clampByte(Math.round(((clean >> 16) & 0xFF) * 0.14f) + 6);
        int g = clampByte(Math.round(((clean >> 8) & 0xFF) * 0.10f) + 6);
        int b = clampByte(Math.round((clean & 0xFF) * 0.18f) + 10);
        return (safeAlpha << 24) | (r << 16) | (g << 8) | b;
    }

    public static float wrapDegrees(float degrees) {
        if (Float.isNaN(degrees)) return 0.0f;
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    public static float computeYawDegFromRel(double relX, double relZ) {
        return (float) (-Math.atan2(relX, relZ) * RAD_TO_DEG);
    }

    public static float computePitchDegFromRel(double relX, double relY, double relZ) {
        double horiz = Math.hypot(relX, relZ);
        return (float) (Math.atan2(relY, Math.max(1e-4, horiz)) * RAD_TO_DEG);
    }

    public static float computeAngularHalfExtentDeg(float pixelExtent, float worldScale, double distance) {
        if (pixelExtent <= 0.0f || worldScale <= 0.0f || distance <= 0.1) {
            return 0.0f;
        }
        double halfWorld = pixelExtent * worldScale * 0.5;
        return (float) (Math.atan2(halfWorld, distance) * RAD_TO_DEG);
    }

    public static float computeBoxClearanceMargin(
            float yawA,
            float pitchA,
            float halfYawA,
            float halfPitchA,
            float yawB,
            float pitchB,
            float halfYawB,
            float halfPitchB,
            float padYawDeg,
            float padPitchDeg
    ) {
        float reqYaw = Math.max(1.0f, halfYawA + halfYawB + padYawDeg);
        float reqPitch = Math.max(1.0f, halfPitchA + halfPitchB + padPitchDeg);
        float sepYaw = (Math.abs(wrapDegrees(yawA - yawB)) - reqYaw) / reqYaw;
        float sepPitch = (Math.abs(pitchA - pitchB) - reqPitch) / reqPitch;
        return Math.max(sepYaw, sepPitch);
    }

    public static boolean doAngularBoxesOverlap(
            float yawA,
            float pitchA,
            float halfYawA,
            float halfPitchA,
            float yawB,
            float pitchB,
            float halfYawB,
            float halfPitchB,
            float padYawDeg,
            float padPitchDeg
    ) {
        return computeBoxClearanceMargin(
                yawA, pitchA, halfYawA, halfPitchA,
                yawB, pitchB, halfYawB, halfPitchB,
                padYawDeg, padPitchDeg
        ) < 0.0f;
    }

    public static float computeWordWorldScale(
            float configScale,
            float layerScale,
            boolean isBgSymbol,
            float animScale
    ) {
        float safeConfig = Float.isNaN(configScale) ? 3.0f : Math.max(0.5f, Math.min(6.0f, configScale));
        float safeLayer = Float.isNaN(layerScale) ? 1.0f : Math.max(0.25f, Math.min(3.0f, layerScale));
        float safeAnim = Float.isNaN(animScale) ? 1.0f : Math.max(0.25f, Math.min(2.0f, animScale));
        float unit = isBgSymbol ? BACKGROUND_SCALE_UNIT : FOREGROUND_SCALE_UNIT;
        return unit * safeConfig * safeLayer * safeAnim;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
