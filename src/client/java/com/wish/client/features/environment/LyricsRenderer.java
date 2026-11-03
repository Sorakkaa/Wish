package com.wish.client.features.environment;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class LyricsRenderer {

    private static final Random RNG = new Random();

    // Floating idle musical notes & stars during instrumental, pauses, or low word density
    private static final String[] IDLE_SYMBOLS = {
        "♪", "♫", "✦", "★", "✧", "♩", "♬"
    };

    private static int getLyricsColor() {
        String hex = ModConfig.INSTANCE.lyricsColor;
        if (hex != null && hex.length() == 7 && hex.startsWith("#")) {
            try {
                return Integer.parseInt(hex.substring(1), 16);
            } catch (Exception ignored) {}
        }
        return 0xFFFFFF;
    }

    private static class Sector {
        final float yawOffsetDeg;   // horizontal angle in degrees from camera forward
        final float pitchOffsetDeg; // vertical angle in degrees (strictly negative = above horizon/crosshair)
        final float rollAngleDeg;   // visual tilt in degrees
        final float sizeScale;
        final boolean isCenter;

        Sector(float yaw, float pitch, float roll, float size, boolean isCenter) {
            this.yawOffsetDeg = yaw;
            this.pitchOffsetDeg = pitch;
            this.rollAngleDeg = roll;
            this.sizeScale = size;
            this.isCenter = isCenter;
        }
    }

    // 16 expansive sectors spanning the upper sky and wide flanks (matching Valence / LyricsFlow montage aesthetic)
    // In Minecraft coordinates: negative pitch = UP in the sky; pitch 0 = crosshair level.
    // Every single sector is strictly negative (above crosshair/horizon) so words NEVER appear on the ground/floor!
    private static final Sector[] SECTORS = {
        // --- Row 1: High Sky (Top of screen, wide majestic banner across the clouds) ---
        new Sector(-38.0f, -25.0f, -6.0f, 1.10f, false), // 0: Sky Upper Left
        new Sector(  0.0f, -26.0f,  0.0f, 1.15f, true),  // 1: Sky Upper Center
        new Sector( 38.0f, -25.0f,  6.0f, 1.10f, false), // 2: Sky Upper Right

        // --- Row 2: Upper Flanks (Wide aesthetic, high left and right) ---
        new Sector(-46.0f, -18.0f, -10.0f, 1.10f, false), // 3: Far Upper Left
        new Sector(-24.0f, -18.0f,  -4.0f, 1.05f, false), // 4: Mid Upper Left
        new Sector( 24.0f, -18.0f,   4.0f, 1.05f, false), // 5: Mid Upper Right
        new Sector( 46.0f, -18.0f,  10.0f, 1.10f, false), // 6: Far Upper Right

        // --- Row 3: Mid Flanks (Eye level flanks, wide sides, spacious) ---
        new Sector(-48.0f, -11.0f,  -8.0f, 1.05f, false), // 7: Mid Far Left
        new Sector(-28.0f, -11.0f,  -3.0f, 1.00f, false), // 8: Inner Mid Left
        new Sector( 28.0f, -11.0f,   3.0f, 1.00f, false), // 9: Inner Mid Right
        new Sector( 48.0f, -11.0f,   8.0f, 1.05f, false), // 10: Mid Far Right

        // --- Row 4: Lower Flanks (Wide sides, slightly above crosshair level, NEVER on floor) ---
        new Sector(-44.0f,  -4.0f,  -6.0f, 0.95f, false), // 11: Lower Far Left
        new Sector(-22.0f,  -4.0f,  -2.0f, 0.95f, false), // 12: Lower Mid Left
        new Sector( 22.0f,  -4.0f,   2.0f, 0.95f, false), // 13: Lower Mid Right
        new Sector( 44.0f,  -4.0f,   6.0f, 0.95f, false), // 14: Lower Far Right

        // --- Center Upper (Above crosshair, floating gracefully in forward view) ---
        new Sector(  0.0f, -14.0f,   0.0f, 1.10f, true)   // 15: Center Upper
    };

    private static class FloatingWord {
        final int sectorIdx;
        float yawOffset;
        float pitchOffset;
        double dist;

        // Fixed in-world 3D coordinates
        double worldX, worldY, worldZ;

        String text;
        List<String> lines;
        long spawnTime;
        long durationMs;
        int color;
        float sizeMult;
        float rollAngle;
        boolean isCenterSymbol;
        boolean isPhrase;

        boolean isFadingOut = false;
        long fadeOutStartTime = 0;
        long fadeOutDurationMs = 180;

        FloatingWord(int sectorIdx, float yawOffset, float pitchOffset, double dist,
                     double worldX, double worldY, double worldZ,
                     String text, List<String> lines, int color, float sizeMult, float rollAngle,
                     long durationMs, boolean isCenterSymbol, boolean isPhrase) {
            this.sectorIdx = sectorIdx;
            this.yawOffset = yawOffset;
            this.pitchOffset = pitchOffset;
            this.dist = dist;
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.text = text;
            this.lines = lines;
            this.spawnTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.color = color;
            this.sizeMult = sizeMult;
            this.rollAngle = rollAngle;
            this.isCenterSymbol = isCenterSymbol;
            this.isPhrase = isPhrase;
        }

        void triggerDespawn(long fadeDur) {
            if (!this.isFadingOut) {
                this.isFadingOut = true;
                this.fadeOutStartTime = System.currentTimeMillis();
                this.fadeOutDurationMs = Math.max(50, fadeDur);
            }
        }
    }

    private static final List<FloatingWord> floatingWords = new ArrayList<>();
    private static String lastSpawnedLine = "";
    private static long lastSpawnedLineTime = -1;
    private static int lastSpawnedWordIdx = -1;
    private static long lastWordSpawnTime = 0;
    private static long lastCenterNoteTime = 0;
    private static int lastUsedSectorIdx = -1;
    private static int lastPhrasePosition = 0;

    private static final int MAX_ACTIVE_WORDS = 10;
    private static final int MAX_TOTAL_SCREEN_ITEMS = 20; // Max 20 words and emojis combined

    private LyricsRenderer() {}

    public static void render(GuiGraphicsExtractor g, Minecraft mc, int screenW, int screenH) {
        if (!ModConfig.INSTANCE.enableLyrics || mc.level == null || mc.player == null) return;

        LyricsManager.LyricState state = LyricsManager.getActiveLyricState();
        if (!state.active) {
            floatingWords.clear();
            lastSpawnedLine = "";
            lastSpawnedLineTime = -1;
            lastSpawnedWordIdx = -1;
            return;
        }

        long now = System.currentTimeMillis();
        var camera = mc.gameRenderer.mainCamera();
        var camPos = camera.position();
        Font font = mc.font;

        boolean hasWords = (state.currentLine != null && !state.currentLine.words.isEmpty());
        String curText = hasWords ? state.currentLine.text : "";
        long curLineTime = (state.currentLine != null) ? state.currentLine.timeMs : -1;

        int configuredColor = getLyricsColor();
        boolean isSpeedUp = LyricsManager.isSpeedUpTrack(state.title);

        // 1. Spawning lyrics according to selected mode: Mots (0) vs Phrases (1)
        if (ModConfig.INSTANCE.lyricsMode == 0) {
            // Mode Mots: Word by word
            if (!curText.equals(lastSpawnedLine) || curLineTime != lastSpawnedLineTime) {
                lastSpawnedLine = curText;
                lastSpawnedLineTime = curLineTime;
                lastSpawnedWordIdx = -1;
            }

            if (hasWords && state.activeWordIndex > lastSpawnedWordIdx) {
                int startIdx = Math.max(0, lastSpawnedWordIdx + 1);
                int endIdx = Math.min(state.currentLine.words.size() - 1, state.activeWordIndex);
                for (int wi = startIdx; wi <= endIdx; wi++) {
                    String rawWord = state.currentLine.words.get(wi);
                    String clean = rawWord.replaceAll("[,.!?\"'();:]+", "").trim().toUpperCase();
                    if (!clean.isEmpty()) {
                        long gap = now - lastWordSpawnTime;
                        lastWordSpawnTime = now;
                        spawnWord(clean, configuredColor, 1.0f, gap, isSpeedUp, mc);
                    }
                }
                lastSpawnedWordIdx = state.activeWordIndex;
            }
        } else {
            // Mode Phrases: Full phrase / line centered in view, disappearing fast as requested
            if (hasWords && (!curText.equals(lastSpawnedLine) || curLineTime != lastSpawnedLineTime)) {
                lastSpawnedLine = curText;
                lastSpawnedLineTime = curLineTime;
                lastWordSpawnTime = now;
                long lineDur = (state.nextLine != null && state.nextLine.timeMs > state.currentLine.timeMs)
                    ? (state.nextLine.timeMs - state.currentLine.timeMs) : (isSpeedUp ? 2200 : 2800);
                long phraseDuration = isSpeedUp
                    ? Math.max(1300, Math.min(2300, (long)(lineDur * 0.80)))
                    : Math.max(1500, Math.min(2800, lineDur));
                spawnPhrase(curText, configuredColor, phraseDuration, mc);
            }
        }

        // 2. Constant floating musical notes & stars whenever screen isn't crowded (< 15 items max)
        int totalOnScreen = floatingWords.size();
        long emojiInterval = (!hasWords || (now - lastWordSpawnTime > 1000)) ? 220 : (isSpeedUp ? 320 : 420);

        if (totalOnScreen < MAX_TOTAL_SCREEN_ITEMS && (now - lastCenterNoteTime >= emojiInterval)) {
            lastCenterNoteTime = now;
            spawnIdleParticle(configuredColor, mc);
        }

        // 3. Render all active 3D floating words and notes (Floating mode)
        Quaternionf invRot = new Quaternionf(camera.rotation()).conjugate();
        double fovRad = Math.toRadians(mc.options.fov().get());
        double halfScreenH = screenH / 2.0;
        double halfScreenW = screenW / 2.0;
        double focalLength = halfScreenH / Math.tan(fovRad / 2.0);

        Iterator<FloatingWord> wit = floatingWords.iterator();
        while (wit.hasNext()) {
            FloatingWord fw = wit.next();
            long age = now - fw.spawnTime;
            if (fw.isFadingOut) {
                long fadeAge = now - fw.fadeOutStartTime;
                if (fadeAge >= fw.fadeOutDurationMs) {
                    wit.remove();
                    continue;
                }
            } else if (age >= fw.durationMs) {
                wit.remove();
                continue;
            }

            // Floating mode: anchored at fixed 3D coordinates in the world, floating gently upward
            double upwardDrift = (age / 1000.0) * (fw.isCenterSymbol ? 0.45 : 0.30);
            double curX = fw.worldX;
            double curY = fw.worldY + upwardDrift;
            double curZ = fw.worldZ;

            double cdx = curX - camPos.x;
            double cdy = curY - camPos.y;
            double cdz = curZ - camPos.z;

            Vector3f camSpace = invRot.transform(new Vector3f((float) cdx, (float) cdy, (float) cdz));
            float depth = -camSpace.z;
            if (depth <= 0.45f) continue; // Behind camera or clipping into player face

            double projX = halfScreenW + (camSpace.x / depth) * focalLength;
            double projY = halfScreenH - (camSpace.y / depth) * focalLength;

            float baseMult = fw.isCenterSymbol ? 0.024f : (fw.isPhrase ? 0.022f : 0.026f);
            float scale = (float) ((focalLength / depth) * baseMult * fw.sizeMult * 3.0f);

            if (projX < -400 || projX > screenW + 400 || projY < -400 || projY > screenH + 400) continue;

            float fadeIn = Math.min(1.0f, age / 120.0f);
            float fadeOut;
            if (fw.isFadingOut) {
                fadeOut = Math.max(0.0f, 1.0f - ((float) (now - fw.fadeOutStartTime) / fw.fadeOutDurationMs));
                scale *= (0.80f + 0.20f * fadeOut);
            } else {
                fadeOut = Math.min(1.0f, (fw.durationMs - age) / (fw.isPhrase ? 200.0f : 500.0f));
            }
            float alphaPct = Math.max(0.0f, Math.min(1.0f, fadeIn * fadeOut));
            int alpha = (int) (alphaPct * 255);
            if (alpha <= 4) continue;

            float bounce = 0.0f;
            if (age < 160) {
                float popIn = age / 160.0f;
                bounce = (float) (Math.sin(popIn * Math.PI) * 4.5f);
            }

            int col = (alpha << 24) | (configuredColor & 0x00FFFFFF);

            if (fw.lines != null && fw.lines.size() > 1) {
                int maxLineW = 0;
                List<Component> lineComps = new ArrayList<>(fw.lines.size());
                for (String ln : fw.lines) {
                    Component lc = Component.literal(ln);
                    lineComps.add(lc);
                    int lw = font.width(lc);
                    if (lw > maxLineW) maxLineW = lw;
                }

                float maxAllowedW = screenW * 0.68f;
                float screenWPixels = maxLineW * scale;
                if (screenWPixels > maxAllowedW && screenWPixels > 0) {
                    scale *= (maxAllowedW / screenWPixels);
                }

                g.pose().pushMatrix();
                g.pose().translate((float) projX, (float) projY - bounce);
                if (fw.rollAngle != 0.0f) {
                    g.pose().rotate((float) Math.toRadians(fw.rollAngle));
                }
                g.pose().scale(scale, scale);

                int lineStep = font.lineHeight + 3;
                int totalH = fw.lines.size() * lineStep - 3;
                int startY = -totalH / 2;

                for (int i = 0; i < lineComps.size(); i++) {
                    Component lc = lineComps.get(i);
                    int lw = font.width(lc);
                    g.text(font, lc, -lw / 2, startY + i * lineStep, col, true);
                }

                g.pose().popMatrix();
            } else {
                Component textComp = Component.literal(fw.text);
                int wW = font.width(textComp);

                float maxAllowedW = screenW * (fw.isPhrase ? 0.68f : 0.38f);
                float screenWPixels = wW * scale;
                if (screenWPixels > maxAllowedW && screenWPixels > 0) {
                    scale *= (maxAllowedW / screenWPixels);
                }

                g.pose().pushMatrix();
                g.pose().translate((float) projX, (float) projY - bounce);
                if (fw.rollAngle != 0.0f) {
                    g.pose().rotate((float) Math.toRadians(fw.rollAngle));
                }
                g.pose().scale(scale, scale);

                g.text(font, textComp, -wW / 2, -font.lineHeight / 2, col, true);

                g.pose().popMatrix();
            }
        }
    }

    private static List<String> wrapPhraseLines(String text, int maxCharsPerLine) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        String trimmed = text.trim();
        if (trimmed.length() <= maxCharsPerLine) {
            result.add(trimmed);
            return result;
        }
        String[] words = trimmed.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() == 0) {
                current.append(w);
            } else if (current.length() + 1 + w.length() <= maxCharsPerLine) {
                current.append(" ").append(w);
            } else {
                result.add(current.toString());
                current.setLength(0);
                current.append(w);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static void spawnWord(String text, int color, float baseScaleMult, long gap, boolean isSpeedUp, Minecraft mc) {
        float lengthScale;
        int len = text.length();
        if (len <= 4) {
            lengthScale = 1.15f;
        } else if (len <= 7) {
            lengthScale = 1.00f;
        } else if (len <= 10) {
            lengthScale = 0.88f;
        } else {
            lengthScale = 0.75f;
        }

        // Dynamic word duration: adapts swiftly to song tempo and rap
        long durationMs;
        if (isSpeedUp) {
            durationMs = 2400 + RNG.nextInt(400);
        } else if (gap < 300) {
            durationMs = 2800 + RNG.nextInt(400);
        } else if (gap < 600) {
            durationMs = 3400 + RNG.nextInt(500);
        } else {
            durationMs = 4000 + RNG.nextInt(600);
        }

        // Rolling FIFO queue: at most MAX_ACTIVE_WORDS (10) on screen simultaneously.
        // When there is an 11th word, immediately despawn the 1st word that spawned!
        long now = System.currentTimeMillis();
        List<FloatingWord> activeWords = new ArrayList<>();
        for (FloatingWord fw : floatingWords) {
            if (!fw.isCenterSymbol && !fw.isPhrase && !fw.isFadingOut) {
                activeWords.add(fw);
            }
        }
        activeWords.sort(Comparator.comparingLong(w -> w.spawnTime));
        while (activeWords.size() >= MAX_ACTIVE_WORDS) {
            FloatingWord oldest = activeWords.remove(0);
            oldest.triggerDespawn(180); // Smooth 180ms fade-out
        }

        // Strict anti-collision sector selection in angular space
        final float R_YAW = 20.0f;
        final float R_PITCH = 8.0f;

        List<Integer> safeSectors = new ArrayList<>();
        double bestDistSq = -1.0;
        int bestFallbackSector = -1;

        for (int i = 0; i < SECTORS.length; i++) {
            if (i == lastUsedSectorIdx) continue;
            Sector sec = SECTORS[i];
            double minDistSq = Double.MAX_VALUE;

            for (FloatingWord fw : floatingWords) {
                if (fw.isCenterSymbol || fw.isFadingOut) continue;
                float dYaw = Math.abs(sec.yawOffsetDeg - fw.yawOffset);
                if (dYaw > 180.0f) dYaw = 360.0f - dYaw;
                float dPitch = Math.abs(sec.pitchOffsetDeg - fw.pitchOffset);

                double normX = dYaw / R_YAW;
                double normY = dPitch / R_PITCH;
                double dSq = (normX * normX) + (normY * normY);
                if (dSq < minDistSq) {
                    minDistSq = dSq;
                }
            }

            if (minDistSq >= 1.0) {
                safeSectors.add(i);
            }
            if (minDistSq > bestDistSq) {
                bestDistSq = minDistSq;
                bestFallbackSector = i;
            }
        }

        int chosenSectorIdx = -1;
        if (!safeSectors.isEmpty()) {
            // Balance sides: if last word was on left (yaw < -5°), prefer right (yaw > 5°) or center, and vice-versa
            float lastYaw = (lastUsedSectorIdx >= 0 && lastUsedSectorIdx < SECTORS.length) ? SECTORS[lastUsedSectorIdx].yawOffsetDeg : 0.0f;
            List<Integer> balanced = new ArrayList<>();
            for (int sIdx : safeSectors) {
                float y = SECTORS[sIdx].yawOffsetDeg;
                if ((lastYaw > 5.0f && y < -5.0f) || (lastYaw < -5.0f && y > 5.0f) || Math.abs(y) <= 5.0f) {
                    balanced.add(sIdx);
                }
            }
            if (!balanced.isEmpty()) {
                chosenSectorIdx = balanced.get(RNG.nextInt(balanced.size()));
            } else {
                chosenSectorIdx = safeSectors.get(RNG.nextInt(safeSectors.size()));
            }
        } else if (bestFallbackSector >= 0) {
            chosenSectorIdx = bestFallbackSector;
            // Accelerate despawn of any word near this sector to guarantee ZERO visual overlap
            Sector sec = SECTORS[chosenSectorIdx];
            for (FloatingWord fw : floatingWords) {
                if (fw.isCenterSymbol || fw.isFadingOut) continue;
                float dYaw = Math.abs(sec.yawOffsetDeg - fw.yawOffset);
                if (dYaw > 180.0f) dYaw = 360.0f - dYaw;
                float dPitch = Math.abs(sec.pitchOffsetDeg - fw.pitchOffset);
                double normX = dYaw / R_YAW;
                double normY = dPitch / R_PITCH;
                if ((normX * normX) + (normY * normY) < 1.0) {
                    fw.triggerDespawn(120);
                }
            }
        } else {
            chosenSectorIdx = RNG.nextInt(SECTORS.length);
        }

        lastUsedSectorIdx = chosenSectorIdx;
        Sector sec = SECTORS[chosenSectorIdx];

        float yawOffset = sec.yawOffsetDeg + (RNG.nextFloat() - 0.5f) * 2.0f;
        float pitchOffset = sec.pitchOffsetDeg + (RNG.nextFloat() - 0.5f) * 1.5f;

        var camera = mc.gameRenderer.mainCamera();
        var camPos = camera.position();

        // Floating mode: 10.0 to 12.0 blocks ahead
        double spawnDist = 10.0 + RNG.nextDouble() * 2.0;
        double worldYawRad = Math.toRadians(camera.yRot() + yawOffset);
        double worldX = camPos.x - Math.sin(worldYawRad) * spawnDist;
        double worldZ = camPos.z + Math.cos(worldYawRad) * spawnDist;

        double heightAboveEyes = 1.8 + (Math.abs(pitchOffset) / 26.0) * 1.8;
        double worldY = camPos.y + heightAboveEyes;

        float roll = sec.rollAngleDeg + (RNG.nextFloat() - 0.5f) * 2.5f;
        float finalScale = baseScaleMult * sec.sizeScale * lengthScale;

        floatingWords.add(new FloatingWord(chosenSectorIdx, yawOffset, pitchOffset, spawnDist,
            worldX, worldY, worldZ, text, null, color, finalScale, roll, durationMs, false, false));
    }

    private static void spawnPhrase(String text, int color, long durationMs, Minecraft mc) {
        // Fast fade-out of previous phrase (180ms) so phrases don't linger
        for (FloatingWord fw : floatingWords) {
            if (fw.isPhrase) {
                fw.triggerDespawn(180);
            }
        }

        var camera = mc.gameRenderer.mainCamera();
        var camPos = camera.position();

        // Split long phrases into multiple wrapped lines (max ~28 chars per line)
        List<String> wrappedLines = wrapPhraseLines(text, 28);

        lastPhrasePosition = (lastPhrasePosition == 0) ? 1 : 0;
        float pitch = (lastPhrasePosition == 0) ? -14.0f : -17.0f;
        float yawOffset = (RNG.nextFloat() - 0.5f) * 2.0f;

        // Floating mode: 11.5 blocks ahead
        double spawnDist = 11.5;
        double worldYawRad = Math.toRadians(camera.yRot() + yawOffset);
        double worldX = camPos.x - Math.sin(worldYawRad) * spawnDist;
        double worldZ = camPos.z + Math.cos(worldYawRad) * spawnDist;
        double worldY = camPos.y + 2.6;

        floatingWords.add(new FloatingWord(15, yawOffset, pitch, spawnDist,
            worldX, worldY, worldZ, text, wrappedLines, color, 0.90f, 0.0f, durationMs, false, true));
    }

    private static void spawnIdleParticle(int color, Minecraft mc) {
        int wordCount = 0;
        int idleCount = 0;
        int[] sectorIdleCounts = new int[SECTORS.length];
        for (FloatingWord fw : floatingWords) {
            if (fw.isCenterSymbol && !fw.isFadingOut) {
                idleCount++;
                if (fw.sectorIdx >= 0 && fw.sectorIdx < SECTORS.length) {
                    sectorIdleCounts[fw.sectorIdx]++;
                }
            } else if (!fw.isCenterSymbol && !fw.isFadingOut) {
                wordCount++;
            }
        }
        int maxIdleAllowed = Math.min(14, MAX_TOTAL_SCREEN_ITEMS - wordCount);
        if (idleCount >= maxIdleAllowed) return;

        // Find sectors with 0 idle symbols so notes and stars are evenly scattered across the full view
        List<Integer> emptySectors = new ArrayList<>();
        for (int i = 0; i < SECTORS.length; i++) {
            if (sectorIdleCounts[i] == 0) {
                emptySectors.add(i);
            }
        }

        int chosenSectorIdx;
        if (!emptySectors.isEmpty()) {
            chosenSectorIdx = emptySectors.get(RNG.nextInt(emptySectors.size()));
        } else {
            chosenSectorIdx = RNG.nextInt(SECTORS.length);
        }

        var camera = mc.gameRenderer.mainCamera();
        var camPos = camera.position();

        Sector sec = SECTORS[chosenSectorIdx];
        float yawOffset = sec.yawOffsetDeg + (RNG.nextFloat() - 0.5f) * 4.0f;
        float pitchOffset = sec.pitchOffsetDeg + (RNG.nextFloat() - 0.5f) * 2.0f;

        float roll = (RNG.nextFloat() - 0.5f) * 8.0f;
        long duration = 3000 + RNG.nextInt(800);
        String symbol = IDLE_SYMBOLS[RNG.nextInt(IDLE_SYMBOLS.length)];

        // Floating mode: 9.0 to 12.0 blocks ahead
        double spawnDist = 9.0 + RNG.nextDouble() * 3.0;
        double worldYawRad = Math.toRadians(camera.yRot() + yawOffset);
        double worldX = camPos.x - Math.sin(worldYawRad) * spawnDist;
        double worldZ = camPos.z + Math.cos(worldYawRad) * spawnDist;
        double worldY = camPos.y + 1.8 + (Math.abs(pitchOffset) / 26.0) * 1.8;

        floatingWords.add(new FloatingWord(chosenSectorIdx, yawOffset, pitchOffset, spawnDist,
            worldX, worldY, worldZ, symbol, null, color, 1.05f, roll, duration, true, false));
    }
}
