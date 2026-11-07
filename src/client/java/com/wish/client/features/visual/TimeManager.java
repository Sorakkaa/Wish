package com.wish.client.features.visual;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;

public final class TimeManager {

    private static long unfrozenBaseTicks = 6000L;
    private static long lastRealTime = System.currentTimeMillis();

    private TimeManager() {}

    public static boolean isCustomTimeActive() {
        return ModConfig.INSTANCE.enableCustomTime && Minecraft.getInstance().level != null;
    }

    public static long getCustomTicks() {
        if (!ModConfig.INSTANCE.enableCustomTime) {
            return 0L;
        }

        long targetTicks = ModConfig.INSTANCE.customTimeTicks;

        if (ModConfig.INSTANCE.customTimeFrozen) {
            unfrozenBaseTicks = targetTicks;
            lastRealTime = System.currentTimeMillis();
            return targetTicks;
        } else {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRealTime;
            if (elapsed > 0) {
                long ticksToAdd = elapsed / 50L; // 20 ticks per second = 50ms per tick
                if (ticksToAdd > 0) {
                    unfrozenBaseTicks = (unfrozenBaseTicks + ticksToAdd) % 24000L;
                    lastRealTime = now - (elapsed % 50L);
                }
            }
            return unfrozenBaseTicks;
        }
    }

    public static void setCustomTicks(int ticks) {
        ticks = Math.max(0, Math.min(24000, ticks));
        ModConfig.INSTANCE.customTimeTicks = ticks;
        unfrozenBaseTicks = ticks;
        lastRealTime = System.currentTimeMillis();
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static void setFrozen(boolean frozen) {
        ModConfig.INSTANCE.customTimeFrozen = frozen;
        unfrozenBaseTicks = ModConfig.INSTANCE.customTimeTicks;
        lastRealTime = System.currentTimeMillis();
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static void invalidateCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            try {
                mc.level.environmentAttributes().invalidateTickCache();
                mc.level.updateSkyBrightness();
            } catch (Exception ignored) {}
        }
    }

    public static String formatTime(long ticks) {
        ticks = ((ticks % 24000L) + 24000L) % 24000L;
        // In Minecraft: 0 ticks = 06:00, 6000 ticks = 12:00, 18000 ticks = 00:00
        int totalMinutes = (int) (((ticks + 6000L) % 24000L) * 1440L / 24000L);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}
