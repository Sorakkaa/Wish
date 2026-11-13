package com.wish.client.features.visual;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.biome.Biome;

import java.util.Random;

public final class WeatherManager {

    public static final String[] MODE_NAMES = { "None", "Rain", "Snow" };

    private static final Random WIND_RNG = new Random();
    private static final String[] COMPASS_LABELS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

    // Continuous organic wind simulation state
    private static double windClockSec = 0.0;
    private static double nextDirectionShiftSec = 10.0;
    private static float currentWindAngle = 0.6f;
    private static float targetWindAngle = 0.6f;
    private static float currentBaseSpeed = 0.85f;
    private static float targetBaseSpeed = 0.90f;
    private static float activeWindBlend = 0.0f;
    private static float cachedWindX = 0.0f;
    private static float cachedWindZ = 0.0f;

    private WeatherManager() {}

    public static boolean isCustomWeatherActive() {
        return ModConfig.INSTANCE.enableCustomWeather && Minecraft.getInstance().level != null;
    }

    public static int getMode() {
        int m = ModConfig.INSTANCE.customWeatherMode;
        if (m < 0) return 0;
        if (m >= MODE_NAMES.length) return MODE_NAMES.length - 1;
        return m;
    }

    public static float getCount() {
        float c = ModConfig.INSTANCE.customWeatherCount;
        if (Float.isNaN(c)) return 1.0f;
        return Math.max(0.1f, Math.min(3.0f, c));
    }

    public static float getSize() {
        float s = ModConfig.INSTANCE.customWeatherSize;
        if (Float.isNaN(s)) return 1.0f;
        return Math.max(0.25f, Math.min(2.5f, s));
    }

    public static boolean isWindEnabled() {
        return ModConfig.INSTANCE.customWeatherWind;
    }

    public static float getWindStrength() {
        float w = ModConfig.INSTANCE.customWeatherWindStrength;
        if (Float.isNaN(w)) return 1.0f;
        return Math.max(0.25f, Math.min(2.0f, w));
    }

    /**
     * Advances the dynamic wind simulation. Every 11-22 seconds the wind smoothly
     * picks a new azimuth direction and gust baseline, turning gradually over time.
     */
    public static void stepWind(float dtSeconds) {
        if (dtSeconds <= 0.0f) {
            return;
        }
        float dt = Math.min(0.1f, dtSeconds);
        windClockSec += dt;

        if (windClockSec >= nextDirectionShiftSec) {
            double shiftDeg = (WIND_RNG.nextBoolean() ? 1.0 : -1.0) * (45.0 + WIND_RNG.nextDouble() * 105.0);
            targetWindAngle += (float) Math.toRadians(shiftDeg);
            targetBaseSpeed = 0.55f + WIND_RNG.nextFloat() * 0.75f;
            nextDirectionShiftSec = windClockSec + 11.0 + WIND_RNG.nextDouble() * 11.0;
        }

        float turnLerp = 1.0f - (float) Math.exp(-dt * 0.42);
        float angleDiff = (float) Math.atan2(Math.sin(targetWindAngle - currentWindAngle), Math.cos(targetWindAngle - currentWindAngle));
        currentWindAngle += angleDiff * turnLerp;

        float speedLerp = 1.0f - (float) Math.exp(-dt * 0.55);
        currentBaseSpeed += (targetBaseSpeed - currentBaseSpeed) * speedLerp;

        float targetBlend = isWindEnabled() ? 1.0f : 0.0f;
        float blendLerp = 1.0f - (float) Math.exp(-dt * 2.5);
        activeWindBlend += (targetBlend - activeWindBlend) * blendLerp;

        double gust = 1.0 + 0.22 * Math.sin(windClockSec * 0.73) + 0.14 * Math.cos(windClockSec * 1.31);
        float mag = (float) (currentBaseSpeed * gust * getWindStrength() * activeWindBlend);
        cachedWindX = (float) Math.cos(currentWindAngle) * mag;
        cachedWindZ = (float) Math.sin(currentWindAngle) * mag;
    }

    public static float getWindX() {
        return cachedWindX;
    }

    public static float getWindZ() {
        return cachedWindZ;
    }

    public static String getWindDirectionLabel() {
        if (!isWindEnabled()) {
            return "Off";
        }
        // World +Z = South, +X = East, -Z = North, -X = West
        double deg = Math.toDegrees(Math.atan2(-cachedWindX, cachedWindZ));
        int idx = (int) Math.round(deg / 45.0) & 7;
        return COMPASS_LABELS[idx];
    }

    public static float getRainLevel() {
        return getMode() == 0 ? 0.0f : 1.0f;
    }

    public static float getThunderLevel() {
        return 0.0f;
    }

    public static Biome.Precipitation getForcedPrecipitation() {
        return switch (getMode()) {
            case 1 -> Biome.Precipitation.RAIN;
            case 2 -> Biome.Precipitation.SNOW;
            default -> Biome.Precipitation.NONE;
        };
    }

    public static void setEnabled(boolean enabled) {
        ModConfig.INSTANCE.enableCustomWeather = enabled;
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static void setMode(int mode) {
        int clamped = Math.max(0, Math.min(MODE_NAMES.length - 1, mode));
        ModConfig.INSTANCE.customWeatherMode = clamped;
        ModConfig.INSTANCE.enableCustomWeather = true;
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static void setCount(float count) {
        float clamped = Math.max(0.1f, Math.min(3.0f, count));
        ModConfig.INSTANCE.customWeatherCount = clamped;
        ModConfig.INSTANCE.save();
    }

    public static void setSize(float size) {
        float clamped = Math.max(0.25f, Math.min(2.5f, size));
        ModConfig.INSTANCE.customWeatherSize = clamped;
        ModConfig.INSTANCE.save();
    }

    public static void setWindEnabled(boolean windEnabled) {
        ModConfig.INSTANCE.customWeatherWind = windEnabled;
        ModConfig.INSTANCE.save();
    }

    public static void setWindStrength(float strength) {
        float clamped = Math.max(0.25f, Math.min(2.0f, strength));
        ModConfig.INSTANCE.customWeatherWindStrength = clamped;
        ModConfig.INSTANCE.save();
    }

    public static void cycleMode() {
        setMode((getMode() + 1) % MODE_NAMES.length);
    }

    public static void invalidateCache() {
        TimeManager.invalidateCache();
    }
}
