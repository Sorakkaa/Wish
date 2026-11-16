package com.wish.client.features.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wish.client.color.ColorUtils;
import com.wish.client.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public final class LyricsRenderer {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float PHRASE_IN_SEC = 0.28f;
    private static final float PHRASE_OUT_SEC = 0.25f;
    private static final float WORD_IN_SEC = 0.24f;
    private static final float WORD_OUT_SEC = 0.45f;
    private static final float WORD_FORCE_OUT_SEC = 0.24f;
    private static final float LINE_SPAWN_DIST = 10.5f;
    private static final float LINE_REUSE_RADIUS_SQ = 18.0f * 18.0f;
    private static final float MAX_RENDER_DIST_SQ = 60.0f * 60.0f;
    private static final float MIN_RENDER_DIST_SQ = 2.0f * 2.0f;
    private static final float NEAR_FADE_START_DIST = 4.2f;
    private static final float WORD_BOX_PAD_YAW_DEG = 3.8f;
    private static final float WORD_BOX_PAD_PITCH_DEG = 3.0f;
    private static final float SYMBOL_BOX_PAD_YAW_DEG = 4.2f;
    private static final float SYMBOL_BOX_PAD_PITCH_DEG = 3.2f;
    private static final int LINE_WRAP_MAX_CHARS = 36;
    private static final int LINE_WRAP_MIN_WORDS = 6;
    private static final int MAX_FOREGROUND_WORDS = 5;
    private static final int MAX_FOREGROUND_WORDS_HARD = 7;
    private static final int MAX_ACTIVE_ITEMS = 30;
    private static final int MAX_BG_SYMBOLS = 18;
    private static final long BG_SPAWN_INTERVAL_MS = 250L;
    private static final float IDLE_FADE_DELAY_SEC = 3.5f;
    private static final Pattern WHITESPACE_SPLIT_PATTERN = Pattern.compile("\\s+");
    private static final String[] IDLE_SYMBOLS = { "♪", "♫", "✦", "★", "✧", "♩", "♬" };
    private static final Random RNG = new Random();

    private static final class SkySector {
        final float yawOffsetDeg;
        final float pitchElevDeg;
        final float minDist;
        final float maxDist;
        final float baseScale;
        final boolean backgroundOnly;

        SkySector(float yawOffsetDeg, float pitchElevDeg, float minDist, float maxDist, float baseScale, boolean backgroundOnly) {
            this.yawOffsetDeg = yawOffsetDeg;
            this.pitchElevDeg = pitchElevDeg;
            this.minDist = minDist;
            this.maxDist = maxDist;
            this.baseScale = baseScale;
            this.backgroundOnly = backgroundOnly;
        }
    }

    private static final SkySector[] SECTORS = {
        new SkySector(-19.0f, 22.0f, 10.6f, 12.4f, 1.00f, false),
        new SkySector(  0.0f, 22.5f, 10.8f, 12.6f, 1.02f, false),
        new SkySector( 19.0f, 22.0f, 10.6f, 12.4f, 1.00f, false),
        new SkySector(-22.0f, 13.5f, 10.0f, 11.8f, 1.00f, false),
        new SkySector( -8.0f, 14.0f,  9.8f, 11.6f, 1.04f, false),
        new SkySector(  8.0f, 14.0f,  9.8f, 11.6f, 1.04f, false),
        new SkySector( 22.0f, 13.5f, 10.0f, 11.8f, 1.00f, false),
        new SkySector(-15.0f,  5.5f,  9.6f, 11.2f, 1.00f, false),
        new SkySector( 15.0f,  5.5f,  9.6f, 11.2f, 1.00f, false),

        new SkySector(-32.0f, 28.0f,  9.8f, 12.4f, 1.04f, true),
        new SkySector(-17.0f, 29.5f, 10.2f, 12.8f, 1.02f, true),
        new SkySector( -5.0f, 30.5f, 10.4f, 13.0f, 1.00f, true),
        new SkySector(  5.0f, 30.5f, 10.4f, 13.0f, 1.00f, true),
        new SkySector( 17.0f, 29.5f, 10.2f, 12.8f, 1.02f, true),
        new SkySector( 32.0f, 28.0f,  9.8f, 12.4f, 1.04f, true),
        new SkySector(-38.0f, 18.0f,  9.6f, 12.2f, 1.06f, true),
        new SkySector( 38.0f, 18.0f,  9.6f, 12.2f, 1.06f, true),
        new SkySector(-34.0f,  7.0f,  9.5f, 11.8f, 1.04f, true),
        new SkySector( 34.0f,  7.0f,  9.5f, 11.8f, 1.04f, true),
        new SkySector(-26.0f, -1.0f,  9.4f, 11.6f, 1.02f, true),
        new SkySector( 26.0f, -1.0f,  9.4f, 11.6f, 1.02f, true),
        new SkySector(-11.0f, -1.5f,  9.5f, 11.6f, 1.00f, true),
        new SkySector( 11.0f, -1.5f,  9.5f, 11.6f, 1.00f, true),
        new SkySector(-42.0f, 25.0f, 10.0f, 12.6f, 1.04f, true),
        new SkySector( 42.0f, 25.0f, 10.0f, 12.6f, 1.04f, true),
        new SkySector(-24.0f, 34.5f, 10.6f, 13.0f, 1.00f, true),
        new SkySector( 24.0f, 34.5f, 10.6f, 13.0f, 1.00f, true)
    };

    private static final class FloatingItem {
        final String line1Text;
        final String line2Text;
        final String[] line1Words;
        final String[] line2Words;
        final int line2WordOffset;
        final int wordIndexInLine;
        final long sourceLineTimeMs;
        final int sectorIndex;
        final double worldX;
        final double worldY;
        final double worldZ;
        final float layerScale;
        final float maxAlpha;
        final float maxLifeSec;
        final float driftSpeedY;
        final float driftSwayX;
        final float swayPhase;
        final float baseRollDeg;
        final boolean backgroundSymbol;
        final boolean phraseItem;
        final long spawnTimeMs;

        final FormattedCharSequence line1UniformSeq;
        final FormattedCharSequence line1BoldSeq;
        final FormattedCharSequence line2UniformSeq;

        float cachedWidth1 = -1.0f;
        float cachedWidth2 = -1.0f;
        FormattedCharSequence cachedSeq1;
        FormattedCharSequence cachedSeq2;
        int cachedHighlightIdx = Integer.MIN_VALUE;
        int cachedKeyRgb = -1;
        int cachedSkyMode = Integer.MIN_VALUE;
        boolean cachedSkyEnabled = false;

        boolean exiting = false;
        long exitStartMs = -1L;

        FloatingItem(
                String line1Text,
                String line2Text,
                String[] line1Words,
                String[] line2Words,
                int line2WordOffset,
                int wordIndexInLine,
                long sourceLineTimeMs,
                int sectorIndex,
                double worldX,
                double worldY,
                double worldZ,
                float layerScale,
                float maxAlpha,
                float maxLifeSec,
                float driftSpeedY,
                float driftSwayX,
                float swayPhase,
                float baseRollDeg,
                boolean backgroundSymbol,
                boolean phraseItem,
                long spawnTimeMs
        ) {
            this.line1Text = line1Text;
            this.line2Text = line2Text;
            this.line1Words = line1Words;
            this.line2Words = line2Words;
            this.line2WordOffset = line2WordOffset;
            this.wordIndexInLine = wordIndexInLine;
            this.sourceLineTimeMs = sourceLineTimeMs;
            this.sectorIndex = sectorIndex;
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.layerScale = layerScale;
            this.maxAlpha = maxAlpha;
            this.maxLifeSec = maxLifeSec;
            this.driftSpeedY = driftSpeedY;
            this.driftSwayX = driftSwayX;
            this.swayPhase = swayPhase;
            this.baseRollDeg = baseRollDeg;
            this.backgroundSymbol = backgroundSymbol;
            this.phraseItem = phraseItem;
            this.spawnTimeMs = spawnTimeMs;
            this.line1UniformSeq = FormattedCharSequence.forward(line1Text, Style.EMPTY);
            this.line1BoldSeq = (!backgroundSymbol && !phraseItem)
                    ? FormattedCharSequence.forward(line1Text, Style.EMPTY.withBold(true))
                    : this.line1UniformSeq;
            this.line2UniformSeq = line2Text != null ? FormattedCharSequence.forward(line2Text, Style.EMPTY) : null;
        }
    }

    private static final List<FloatingItem> ACTIVE_ITEMS = new ArrayList<>(MAX_ACTIVE_ITEMS + 4);
    private static final boolean[] OCCUPIED_SECTORS = new boolean[SECTORS.length];
    private static final int[] CANDIDATE_SECTORS = new int[SECTORS.length];
    private static final Quaternionf REUSABLE_ROLL_QUAT = new Quaternionf();
    private static final StringBuilder SCRATCH_BUILDER = new StringBuilder(80);

    private static int lastRenderedMode = -1;
    private static long lastLineTime = -1L;
    private static int lastWordIndex = -1;
    private static int lastSpawnSector = -1;
    private static long lastLyricActivityMs = 0L;
    private static long lastBgSpawnMs = 0L;
    private static volatile boolean pendingDisconnectClear = false;

    private LyricsRenderer() {}

    public static void onDisconnect() {
        pendingDisconnectClear = true;
    }

    public static void renderWorld(LevelRenderContext context) {
        if (!ModConfig.INSTANCE.enableLyrics) {
            clearAllState();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.font == null || context == null || context.levelState() == null) {
            return;
        }
        CameraRenderState camState = context.levelState().cameraRenderState;
        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack poseStack = context.poseStack();
        if (camState == null || !camState.initialized || camState.pos == null || camState.orientation == null || collector == null || poseStack == null) {
            return;
        }
        if (pendingDisconnectClear) {
            clearAllState();
            pendingDisconnectClear = false;
        }

        int currentMode = ModConfig.INSTANCE.lyricsMode == 1 ? 1 : 0;
        if (lastRenderedMode != -1 && lastRenderedMode != currentMode) {
            clearForegroundItems();
            lastLineTime = -1L;
            lastWordIndex = -1;
        }
        lastRenderedMode = currentMode;

        long nowMs = System.currentTimeMillis();
        float timeSec = (nowMs & 0xFFFFFL) * 0.001f;
        int userBaseRgb = ColorUtils.parseHexRgb(ModConfig.INSTANCE.lyricsColor, 0xFFFFFF);
        double playerMinY = mc.player.getY();
        float configScale = ModConfig.INSTANCE.lyricsScale;
        LyricsManager.LyricState state = LyricsManager.getActiveLyricState();

        updateSpawners(state, camState, mc.font, playerMinY, currentMode, configScale, nowMs);
        renderActiveItems(poseStack, collector, mc.font, camState, state, nowMs, timeSec, userBaseRgb);
    }

    private static void clearAllState() {
        ACTIVE_ITEMS.clear();
        lastRenderedMode = -1;
        lastLineTime = -1L;
        lastWordIndex = -1;
        lastSpawnSector = -1;
        lastLyricActivityMs = 0L;
        lastBgSpawnMs = 0L;
    }

    private static void clearForegroundItems() {
        for (int i = ACTIVE_ITEMS.size() - 1; i >= 0; i--) {
            if (!ACTIVE_ITEMS.get(i).backgroundSymbol) {
                ACTIVE_ITEMS.remove(i);
            }
        }
    }

    private static void updateSpawners(
            LyricsManager.LyricState state,
            CameraRenderState camState,
            Font font,
            double playerFeetY,
            int mode,
            float configScale,
            long nowMs
    ) {
        boolean playingMusic = LyricsManager.isPlaying || (state != null && state.active);
        if (playingMusic && nowMs - lastBgSpawnMs > BG_SPAWN_INTERVAL_MS && countBackgroundItems() < MAX_BG_SYMBOLS) {
            spawnAtmosphericSymbol(camState, font, playerFeetY, configScale, nowMs);
            lastBgSpawnMs = nowMs;
        }

        if (state == null || !state.active || state.currentLine == null) {
            return;
        }

        LyricsManager.LyricLine line = state.currentLine;
        lastLyricActivityMs = nowMs;
        boolean lineChanged = (line.timeMs != lastLineTime);
        if (lineChanged) {
            lastLineTime = line.timeMs;
            lastWordIndex = -1;
        }

        if (mode == 1) {
            if (lineChanged) {
                transitionToSinglePhrase(line, camState, font, playerFeetY, nowMs);
            }
            return;
        }

        if (line.words.isEmpty()) return;
        int targetIdx = Math.max(0, Math.min(state.activeWordIndex, line.words.size() - 1));
        if (targetIdx > lastWordIndex) {
            for (int w = lastWordIndex + 1; w <= targetIdx; w++) {
                String word = sanitizeRenderText(line.words.get(w));
                if (!word.isEmpty()) {
                    spawnCloseWord(word, line.timeMs, w, camState, font, playerFeetY, configScale, nowMs);
                }
            }
            lastWordIndex = targetIdx;
        }
    }

    private static void transitionToSinglePhrase(
            LyricsManager.LyricLine line,
            CameraRenderState camState,
            Font font,
            double playerFeetY,
            long nowMs
    ) {
        String cleanFull = sanitizeRenderText(line.text);
        if (cleanFull.isEmpty()) return;

        FloatingItem activeAnchorSource = null;
        for (int i = ACTIVE_ITEMS.size() - 1; i >= 0; i--) {
            FloatingItem item = ACTIVE_ITEMS.get(i);
            if (!item.backgroundSymbol && item.phraseItem) {
                if (item.exiting) {
                    ACTIVE_ITEMS.remove(i);
                } else {
                    item.exiting = true;
                    item.exitStartMs = nowMs;
                    if (activeAnchorSource == null) {
                        activeAnchorSource = item;
                    }
                }
            }
        }

        double spawnX;
        double spawnY;
        double spawnZ;
        if (activeAnchorSource != null && distSq(activeAnchorSource.worldX - camState.pos.x,
                activeAnchorSource.worldY - camState.pos.y, activeAnchorSource.worldZ - camState.pos.z) <= LINE_REUSE_RADIUS_SQ) {
            spawnX = activeAnchorSource.worldX;
            spawnY = activeAnchorSource.worldY;
            spawnZ = activeAnchorSource.worldZ;
        } else {
            double yawRad = -camState.yRot * DEG_TO_RAD;
            spawnX = camState.pos.x + Math.sin(yawRad) * LINE_SPAWN_DIST;
            spawnY = Math.max(playerFeetY + 1.50, camState.pos.y + 1.25);
            spawnZ = camState.pos.z + Math.cos(yawRad) * LINE_SPAWN_DIST;
        }

        String[] tokens = WHITESPACE_SPLIT_PATTERN.split(cleanFull);
        String line1Text = cleanFull;
        String line2Text = null;
        String[] line1Words = tokens;
        String[] line2Words = null;
        int line2Offset = 0;

        if (cleanFull.length() > LINE_WRAP_MAX_CHARS && tokens.length >= LINE_WRAP_MIN_WORDS) {
            int mid = (tokens.length + 1) / 2;
            line1Words = Arrays.copyOfRange(tokens, 0, mid);
            line2Words = Arrays.copyOfRange(tokens, mid, tokens.length);
            line1Text = joinTokens(line1Words);
            line2Text = joinTokens(line2Words);
            line2Offset = mid;
        }

        FloatingItem phrase = new FloatingItem(
                line1Text, line2Text, line1Words, line2Words, line2Offset, -1, line.timeMs,
                -1, spawnX, spawnY, spawnZ, 1.02f, 1.0f, 16.0f,
                0.0f, 0.04f, 0.0f, 0.0f, false, true, nowMs
        );
        if (font != null) {
            phrase.cachedWidth1 = font.width(line1Text);
            if (line2Text != null) {
                phrase.cachedWidth2 = font.width(line2Text);
            }
        }
        ACTIVE_ITEMS.add(phrase);
    }

    private static void renderActiveItems(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Font font,
            CameraRenderState camState,
            LyricsManager.LyricState state,
            long nowMs,
            float timeSec,
            int userBaseRgb
    ) {
        if (ACTIVE_ITEMS.isEmpty()) return;

        boolean skyEnabled = ModConfig.INSTANCE.enableCustomSky;
        int skyMode = ModConfig.INSTANCE.customSkyMode;
        float configScale = ModConfig.INSTANCE.lyricsScale;
        boolean idleFade = (!LyricsManager.isPlaying) && (nowMs - lastLyricActivityMs > (long) (IDLE_FADE_DELAY_SEC * 1000.0f));

        for (int i = ACTIVE_ITEMS.size() - 1; i >= 0; i--) {
            FloatingItem item = ACTIVE_ITEMS.get(i);
            float ageSec = (nowMs - item.spawnTimeMs) * 0.001f;
            if (ageSec < 0.0f || ageSec >= item.maxLifeSec) {
                ACTIVE_ITEMS.remove(i);
                continue;
            }
            if (item.exiting) {
                float exitAge = (nowMs - item.exitStartMs) * 0.001f;
                float exitLimit = item.phraseItem ? PHRASE_OUT_SEC : WORD_FORCE_OUT_SEC;
                if (exitAge >= exitLimit) {
                    ACTIVE_ITEMS.remove(i);
                    continue;
                }
            }

            float configAlpha = Float.isNaN(ModConfig.INSTANCE.lyricsAlpha)
                    ? 1.0f
                    : LyricsVisualMath.clamp01(ModConfig.INSTANCE.lyricsAlpha);
            float alpha = computeItemAlpha(item, ageSec, nowMs, idleFade) * configAlpha;
            if (alpha < 0.04f) {
                continue;
            }

            submitItem(poseStack, collector, font, camState, state, item, ageSec, nowMs, alpha,
                    timeSec, userBaseRgb, skyEnabled, skyMode, configScale);
        }
    }

    private static float computeItemAlpha(FloatingItem item, float ageSec, long nowMs, boolean idleFade) {
        if (item.phraseItem) {
            float baseAlpha;
            if (item.exiting) {
                float outT = Math.max(0.0f, Math.min(1.0f, (nowMs - item.exitStartMs) * 0.001f / PHRASE_OUT_SEC));
                baseAlpha = 1.0f - LyricsVisualMath.easeInCubic(outT);
            } else if (ageSec < PHRASE_IN_SEC) {
                baseAlpha = LyricsVisualMath.easeOutCubic(ageSec / PHRASE_IN_SEC);
            } else {
                baseAlpha = 1.0f;
            }
            return (idleFade ? baseAlpha * 0.45f : baseAlpha) * item.maxAlpha;
        }

        float alphaFactor;
        if (item.exiting) {
            float outT = Math.max(0.0f, Math.min(1.0f, (nowMs - item.exitStartMs) * 0.001f / WORD_FORCE_OUT_SEC));
            alphaFactor = 1.0f - LyricsVisualMath.easeInCubic(outT);
        } else if (ageSec < WORD_IN_SEC) {
            alphaFactor = LyricsVisualMath.easeOutCubic(ageSec / WORD_IN_SEC);
        } else {
            float fadeStart = Math.max(WORD_IN_SEC, item.maxLifeSec - WORD_OUT_SEC);
            if (ageSec > fadeStart) {
                float outT = (ageSec - fadeStart) / Math.max(0.12f, item.maxLifeSec - fadeStart);
                alphaFactor = 1.0f - LyricsVisualMath.easeInCubic(outT);
            } else {
                alphaFactor = 1.0f;
            }
        }
        float alpha = item.maxAlpha * alphaFactor;
        return idleFade ? alpha * 0.65f : alpha;
    }

    private static void submitItem(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Font font,
            CameraRenderState camState,
            LyricsManager.LyricState state,
            FloatingItem item,
            float ageSec,
            long nowMs,
            float alpha,
            float timeSec,
            int userBaseRgb,
            boolean skyEnabled,
            int skyMode,
            float configScale
    ) {
        if (item.cachedWidth1 < 0.0f) {
            item.cachedWidth1 = font.width(item.line1Text);
        }
        if (item.line2Text != null && item.cachedWidth2 < 0.0f) {
            item.cachedWidth2 = font.width(item.line2Text);
        }

        float driftY = computeDriftY(item, ageSec, nowMs, timeSec);
        float driftX = item.phraseItem ? 0.0f : (float) Math.sin(ageSec * 0.9f + item.swayPhase) * item.driftSwayX;
        double relX = (item.worldX + driftX) - camState.pos.x;
        double relY = (item.worldY + driftY) - camState.pos.y;
        double relZ = item.worldZ - camState.pos.z;
        double dSq = distSq(relX, relY, relZ);
        if (dSq > MAX_RENDER_DIST_SQ || dSq < MIN_RENDER_DIST_SQ) {
            return;
        }

        double dist = Math.sqrt(dSq);
        float effectiveAlpha = alpha;
        if (dist < NEAR_FADE_START_DIST) {
            effectiveAlpha *= LyricsVisualMath.clamp01((float) ((dist - 2.0) / (NEAR_FADE_START_DIST - 2.0)));
            if (effectiveAlpha < 0.04f) {
                return;
            }
        }
        if (item.backgroundSymbol) {
            effectiveAlpha *= computeSymbolClearanceAttenuation(camState, relX, relY, relZ, dist, configScale, item.layerScale);
            if (effectiveAlpha < 0.04f) {
                return;
            }
        }

        boolean wordActiveKaraoke = !item.backgroundSymbol && !item.phraseItem
                && state != null && state.active && state.currentLine != null
                && state.currentLine.timeMs == item.sourceLineTimeMs
                && state.activeWordIndex == item.wordIndexInLine;

        float animScale = computeMotionScale(item, ageSec, nowMs, timeSec, wordActiveKaraoke);
        float worldScale = LyricsVisualMath.computeWordWorldScale(configScale, item.layerScale, item.backgroundSymbol, animScale);
        float rollRad = item.phraseItem ? 0.0f : (item.baseRollDeg + (float) Math.sin(ageSec * 1.15f + item.swayPhase) * 1.2f) * DEG_TO_RAD;

        int dynamicRgb = LyricsVisualMath.computeDynamicTextRgb(userBaseRgb, skyEnabled, skyMode, timeSec, item.swayPhase, item.backgroundSymbol);
        int alphaByte = Math.max(12, Math.min(255, Math.round(effectiveAlpha * 255.0f)));
        int accentRgb = skyEnabled ? LyricsVisualMath.computeSkyHarmonyRgb(skyMode) : 0xD6C4FF;
        int itemFgRgb = resolveWordColor(item, dynamicRgb, accentRgb, wordActiveKaraoke);
        int outlineColor = LyricsVisualMath.computeContrastOutlineColor(itemFgRgb, alphaByte);

        updateKaraokeCacheIfNeeded(item, state, userBaseRgb, skyEnabled, skyMode);
        int fgArgb = item.phraseItem ? ARGB.color(alphaByte, 0xFFFFFF) : ARGB.color(alphaByte, itemFgRgb);

        poseStack.pushPose();
        poseStack.translate((float) relX, (float) relY, (float) relZ);
        poseStack.mulPose(camState.orientation);
        if (Math.abs(rollRad) > 0.001f) {
            poseStack.mulPose(REUSABLE_ROLL_QUAT.rotationZ(rollRad));
        }

        poseStack.scale(worldScale, -worldScale, worldScale);
        submitForegroundPass(poseStack, collector, font, item, fgArgb, outlineColor, wordActiveKaraoke);
        poseStack.popPose();
    }

    private static int resolveWordColor(
            FloatingItem item,
            int dynamicRgb,
            int accentRgb,
            boolean wordActiveKaraoke
    ) {
        if (item.backgroundSymbol || item.phraseItem) {
            return dynamicRgb;
        }
        LyricsVisualMath.KaraokeRole role = wordActiveKaraoke
                ? LyricsVisualMath.KaraokeRole.ACTIVE
                : LyricsVisualMath.KaraokeRole.SUNG;
        return LyricsVisualMath.computeKaraokeWordRgb(dynamicRgb, accentRgb, role);
    }

    private static float computeSymbolClearanceAttenuation(
            CameraRenderState camState,
            double symRelX,
            double symRelY,
            double symRelZ,
            double symDist,
            float configScale,
            float symLayerScale
    ) {
        float symYaw = LyricsVisualMath.computeYawDegFromRel(symRelX, symRelZ);
        float symPitch = LyricsVisualMath.computePitchDegFromRel(symRelX, symRelY, symRelZ);
        float symWorldScale = LyricsVisualMath.computeWordWorldScale(configScale, symLayerScale, true, 1.0f);
        float symHalfYaw = Math.max(1.4f, LyricsVisualMath.computeAngularHalfExtentDeg(8.0f, symWorldScale, symDist));
        float symHalfPitch = Math.max(1.4f, LyricsVisualMath.computeAngularHalfExtentDeg(8.0f, symWorldScale, symDist));
        float minMargin = computeMinClearanceAgainstText(
                symYaw, symPitch, symHalfYaw, symHalfPitch,
                camState, configScale, true, SYMBOL_BOX_PAD_YAW_DEG, SYMBOL_BOX_PAD_PITCH_DEG
        );
        return minMargin < 0.0f ? Math.max(0.0f, 1.0f + minMargin * 3.0f) : 1.0f;
    }

    private static float computeMotionScale(
            FloatingItem item,
            float ageSec,
            long nowMs,
            float timeSec,
            boolean wordActiveKaraoke
    ) {
        if (item.phraseItem) {
            if (item.exiting) {
                float outT = Math.max(0.0f, Math.min(1.0f, (nowMs - item.exitStartMs) * 0.001f / PHRASE_OUT_SEC));
                return LyricsVisualMath.lerp(1.0f, 0.86f, LyricsVisualMath.easeInCubic(outT));
            }
            float inT = Math.min(1.0f, ageSec / PHRASE_IN_SEC);
            float pop = LyricsVisualMath.lerp(0.84f, 1.0f, LyricsVisualMath.easeOutBack(inT));
            return pop * (1.0f + 0.012f * (float) Math.sin(timeSec * 1.6f));
        }
        float inT = Math.min(1.0f, ageSec / WORD_IN_SEC);
        float pop = LyricsVisualMath.lerp(0.75f, 1.0f, LyricsVisualMath.easeOutBack(inT));
        float karaokeBoost = wordActiveKaraoke ? 1.16f : 1.0f;
        return pop * karaokeBoost * (1.0f + 0.018f * (float) Math.sin(timeSec * 1.8f + item.swayPhase));
    }

    private static float computeDriftY(FloatingItem item, float ageSec, long nowMs, float timeSec) {
        if (item.phraseItem) {
            if (item.exiting) {
                float outT = Math.max(0.0f, Math.min(1.0f, (nowMs - item.exitStartMs) * 0.001f / PHRASE_OUT_SEC));
                return 0.34f * LyricsVisualMath.easeOutCubic(outT);
            }
            float inT = Math.min(1.0f, ageSec / PHRASE_IN_SEC);
            float enterSlide = -0.24f * (1.0f - LyricsVisualMath.easeOutCubic(inT));
            float gentleFloat = 0.04f * (float) Math.sin(timeSec * 1.4f);
            return enterSlide + gentleFloat;
        }
        float driftProgress = LyricsVisualMath.easeOutCubic(Math.min(1.0f, ageSec / Math.max(0.8f, item.maxLifeSec)));
        return item.driftSpeedY * ageSec * (1.15f - 0.35f * driftProgress);
    }

    private static void submitOutlineOffsets(
            PoseStack poseStack,
            net.minecraft.client.renderer.OrderedSubmitNodeCollector order,
            FormattedCharSequence seq,
            float drawX,
            float drawY,
            float offsetPx,
            int outlineArgb
    ) {
        order.submitText(poseStack, drawX - offsetPx, drawY, seq, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, outlineArgb, 0, 0);
        order.submitText(poseStack, drawX + offsetPx, drawY, seq, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, outlineArgb, 0, 0);
        order.submitText(poseStack, drawX, drawY - offsetPx, seq, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, outlineArgb, 0, 0);
        order.submitText(poseStack, drawX, drawY + offsetPx, seq, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, outlineArgb, 0, 0);
    }

    private static void submitForegroundPass(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Font font,
            FloatingItem item,
            int fgArgb,
            int outlineColor,
            boolean wordActiveKaraoke
    ) {
        FormattedCharSequence seq1 = (item.phraseItem && item.cachedSeq1 != null)
                ? item.cachedSeq1
                : (wordActiveKaraoke ? item.line1BoldSeq : item.line1UniformSeq);
        FormattedCharSequence seq2 = (item.phraseItem && item.cachedSeq2 != null) ? item.cachedSeq2 : item.line2UniformSeq;
        boolean twoLines = item.line2Text != null;
        float x1 = -item.cachedWidth1 * 0.5f;
        float y1 = getLineY(font, twoLines, true);

        var outlineOrder = collector.order(0);
        if (item.backgroundSymbol) {
            int symOutline = ARGB.color(Math.max(12, ((outlineColor >>> 24) * 3) / 4), outlineColor & 0xFFFFFF);
            submitOutlineOffsets(poseStack, outlineOrder, seq1, x1, y1, 0.78f, symOutline);
        } else {
            FormattedCharSequence outlineSeq1 = wordActiveKaraoke ? item.line1BoldSeq : item.line1UniformSeq;
            submitOutlineOffsets(poseStack, outlineOrder, outlineSeq1, x1, y1, 0.85f, outlineColor);
            if (item.line2UniformSeq != null) {
                submitOutlineOffsets(poseStack, outlineOrder, item.line2UniformSeq, -item.cachedWidth2 * 0.5f, getLineY(font, true, false), 0.85f, outlineColor);
            }
        }

        var fgOrder = collector.order(1);
        fgOrder.submitText(poseStack, x1, y1, seq1, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, fgArgb, 0, 0);
        if (seq2 != null) {
            fgOrder.submitText(poseStack, -item.cachedWidth2 * 0.5f, getLineY(font, true, false), seq2, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, fgArgb, 0, 0);
        }
    }

    private static float getLineY(Font font, boolean twoLines, boolean firstLine) {
        if (!twoLines) {
            return -font.lineHeight * 0.5f;
        }
        return firstLine ? -(font.lineHeight + 1.0f) : 1.0f;
    }

    private static void updateKaraokeCacheIfNeeded(
            FloatingItem item,
            LyricsManager.LyricState state,
            int userBaseRgb,
            boolean skyEnabled,
            int skyMode
    ) {
        if (!item.phraseItem) return;

        int localActive;
        if (state != null && state.active && state.currentLine != null && state.currentLine.timeMs == item.sourceLineTimeMs) {
            int totalWords = item.line1Words.length + (item.line2Words != null ? item.line2Words.length : 0);
            localActive = Math.max(-1, Math.min(totalWords, state.activeWordIndex));
        } else {
            localActive = item.line1Words.length + (item.line2Words != null ? item.line2Words.length : 0);
        }

        if (item.cachedSeq1 != null
                && item.cachedHighlightIdx == localActive
                && item.cachedKeyRgb == userBaseRgb
                && item.cachedSkyEnabled == skyEnabled
                && item.cachedSkyMode == skyMode) {
            return;
        }

        int accentRgb = skyEnabled ? LyricsVisualMath.computeSkyHarmonyRgb(skyMode) : 0xD6C4FF;
        item.cachedSeq1 = buildLineSequence(item.line1Words, 0, localActive, userBaseRgb, accentRgb);
        item.cachedSeq2 = item.line2Words != null
                ? buildLineSequence(item.line2Words, item.line2WordOffset, localActive, userBaseRgb, accentRgb)
                : null;
        item.cachedHighlightIdx = localActive;
        item.cachedKeyRgb = userBaseRgb;
        item.cachedSkyEnabled = skyEnabled;
        item.cachedSkyMode = skyMode;
    }

    private static FormattedCharSequence buildLineSequence(
            String[] words,
            int wordOffset,
            int globalActiveIdx,
            int userBaseRgb,
            int accentRgb
    ) {
        MutableComponent comp = Component.empty();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) comp.append(" ");
            int wordIdx = wordOffset + i;
            boolean bold = false;
            LyricsVisualMath.KaraokeRole role;
            if (wordIdx < globalActiveIdx) {
                role = LyricsVisualMath.KaraokeRole.SUNG;
            } else if (wordIdx == globalActiveIdx) {
                role = LyricsVisualMath.KaraokeRole.ACTIVE;
                bold = true;
            } else {
                role = LyricsVisualMath.KaraokeRole.UPCOMING;
            }
            int spanRgb = LyricsVisualMath.computeKaraokeWordRgb(userBaseRgb, accentRgb, role);
            Style style = Style.EMPTY.withColor(spanRgb);
            if (bold) style = style.withBold(true);
            comp.append(Component.literal(words[i]).withStyle(style));
        }
        return comp.getVisualOrderText();
    }

    private static void spawnCloseWord(
            String word,
            long lineTimeMs,
            int wordIdx,
            CameraRenderState camState,
            Font font,
            double playerFeetY,
            float configScale,
            long nowMs
    ) {
        spawnRadialItem(word, lineTimeMs, wordIdx, camState, font, playerFeetY, configScale, false, nowMs);
    }

    private static void spawnAtmosphericSymbol(
            CameraRenderState camState,
            Font font,
            double playerFeetY,
            float configScale,
            long nowMs
    ) {
        String symbol = IDLE_SYMBOLS[RNG.nextInt(IDLE_SYMBOLS.length)];
        spawnRadialItem(symbol, -1L, -1, camState, font, playerFeetY, configScale, true, nowMs);
    }

    private static void spawnRadialItem(
            String text,
            long sourceLineTimeMs,
            int wordIdx,
            CameraRenderState camState,
            Font font,
            double playerFeetY,
            float configScale,
            boolean isBgSymbol,
            long nowMs
    ) {
        if (text == null || text.isEmpty()) return;
        if (!isBgSymbol) {
            trimExcessForegroundWords(nowMs);
        }

        float textWidthPx = font != null ? font.width(text) : (text.length() * 6.0f);
        int sectorIdx = isBgSymbol
                ? pickAvailableSymbolSector(camState, configScale)
                : pickAvailableWordSector(textWidthPx, camState, configScale);
        if (sectorIdx < 0) return;

        SkySector sector = SECTORS[sectorIdx];
        if (!isBgSymbol) {
            lastSpawnSector = sectorIdx;
        }

        float dist = sector.minDist + RNG.nextFloat() * (sector.maxDist - sector.minDist);
        float worldScale = LyricsVisualMath.computeWordWorldScale(configScale, sector.baseScale, isBgSymbol, 1.0f);
        float halfYawDeg = Math.max(isBgSymbol ? 1.4f : 2.2f,
                LyricsVisualMath.computeAngularHalfExtentDeg(textWidthPx, worldScale, dist));
        float halfPitchDeg = Math.max(isBgSymbol ? 1.4f : 1.6f,
                LyricsVisualMath.computeAngularHalfExtentDeg(9.0f, worldScale, dist));

        float yawDeg = camState.yRot + sector.yawOffsetDeg;
        float elevDeg = Math.max(-2.0f, Math.min(34.0f, sector.pitchElevDeg));
        float yawJitterSpan = isBgSymbol ? 7.0f : 3.0f;
        float pitchJitterSpan = isBgSymbol ? 4.0f : 2.0f;
        for (int attempt = 0; attempt < 4; attempt++) {
            float candYaw = camState.yRot + sector.yawOffsetDeg + (RNG.nextFloat() * yawJitterSpan - yawJitterSpan * 0.5f);
            float candElev = Math.max(-2.0f, Math.min(34.0f, sector.pitchElevDeg + (RNG.nextFloat() * pitchJitterSpan - pitchJitterSpan * 0.5f)));
            float margin = computeMinClearanceAgainstText(
                    candYaw, candElev, halfYawDeg, halfPitchDeg, camState, configScale, isBgSymbol,
                    isBgSymbol ? SYMBOL_BOX_PAD_YAW_DEG : WORD_BOX_PAD_YAW_DEG,
                    isBgSymbol ? SYMBOL_BOX_PAD_PITCH_DEG : WORD_BOX_PAD_PITCH_DEG
            );
            if (margin >= 0.0f) {
                yawDeg = candYaw;
                elevDeg = candElev;
                break;
            }
        }

        double yawRad = -yawDeg * DEG_TO_RAD;
        double elevRad = elevDeg * DEG_TO_RAD;
        double cosElev = Math.cos(elevRad);

        double worldX = camState.pos.x + Math.sin(yawRad) * cosElev * dist;
        double worldY = Math.max(playerFeetY + 1.20, camState.pos.y + Math.sin(elevRad) * dist);
        double worldZ = camState.pos.z + Math.cos(yawRad) * cosElev * dist;

        float maxLifeSec = isBgSymbol ? (4.4f + RNG.nextFloat() * 1.4f) : (2.3f + RNG.nextFloat() * 0.6f);
        float maxAlpha = isBgSymbol ? (0.90f + RNG.nextFloat() * 0.10f) : 1.0f;
        float driftY = isBgSymbol ? (0.12f + RNG.nextFloat() * 0.10f) : (0.14f + RNG.nextFloat() * 0.10f);
        float swayX = isBgSymbol ? (0.16f + RNG.nextFloat() * 0.12f) : (0.10f + RNG.nextFloat() * 0.08f);
        float swayPhase = RNG.nextFloat() * (float) (Math.PI * 2.0);
        float rollDeg = isBgSymbol ? (RNG.nextFloat() * 14.0f - 7.0f) : (RNG.nextFloat() * 5.0f - 2.5f);

        trimExcessItems(isBgSymbol);
        FloatingItem spawned = new FloatingItem(
                text, null, null, null, 0, wordIdx, sourceLineTimeMs, sectorIdx,
                worldX, worldY, worldZ, sector.baseScale, maxAlpha, maxLifeSec,
                driftY, swayX, swayPhase, rollDeg, isBgSymbol, false, nowMs
        );
        spawned.cachedWidth1 = textWidthPx;
        ACTIVE_ITEMS.add(spawned);
    }

    private static int pickAvailableWordSector(float wordWidthPx, CameraRenderState camState, float configScale) {
        markOccupiedSectors();
        int clearCount = 0;
        int bestFallbackIdx = -1;
        float bestFallbackMargin = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < SECTORS.length; i++) {
            SkySector sector = SECTORS[i];
            if (sector.backgroundOnly || i == lastSpawnSector) continue;

            float midDist = (sector.minDist + sector.maxDist) * 0.5f;
            float worldScale = LyricsVisualMath.computeWordWorldScale(configScale, sector.baseScale, false, 1.0f);
            float halfYaw = Math.max(2.2f, LyricsVisualMath.computeAngularHalfExtentDeg(wordWidthPx, worldScale, midDist));
            float halfPitch = Math.max(1.6f, LyricsVisualMath.computeAngularHalfExtentDeg(9.0f, worldScale, midDist));
            float margin = computeMinClearanceAgainstText(
                    camState.yRot + sector.yawOffsetDeg, sector.pitchElevDeg,
                    halfYaw, halfPitch, camState, configScale, false,
                    WORD_BOX_PAD_YAW_DEG, WORD_BOX_PAD_PITCH_DEG
            );

            if (!OCCUPIED_SECTORS[i] && margin >= 0.0f) {
                CANDIDATE_SECTORS[clearCount++] = i;
            }
            float score = OCCUPIED_SECTORS[i] ? (margin - 2.0f) : margin;
            if (score > bestFallbackMargin) {
                bestFallbackMargin = score;
                bestFallbackIdx = i;
            }
        }

        if (clearCount > 0) {
            return CANDIDATE_SECTORS[RNG.nextInt(clearCount)];
        }
        return bestFallbackIdx >= 0 ? bestFallbackIdx : 0;
    }

    private static int pickAvailableSymbolSector(CameraRenderState camState, float configScale) {
        markOccupiedSectors();
        int emptyClearCount = 0;
        for (int i = 0; i < SECTORS.length; i++) {
            if (!SECTORS[i].backgroundOnly || OCCUPIED_SECTORS[i]) continue;
            if (isSymbolSectorClearOfText(SECTORS[i], camState, configScale)) {
                CANDIDATE_SECTORS[emptyClearCount++] = i;
            }
        }
        if (emptyClearCount > 0) {
            return CANDIDATE_SECTORS[RNG.nextInt(emptyClearCount)];
        }

        int anyClearCount = 0;
        for (int i = 0; i < SECTORS.length; i++) {
            if (SECTORS[i].backgroundOnly && isSymbolSectorClearOfText(SECTORS[i], camState, configScale)) {
                CANDIDATE_SECTORS[anyClearCount++] = i;
            }
        }
        return anyClearCount > 0 ? CANDIDATE_SECTORS[RNG.nextInt(anyClearCount)] : -1;
    }

    private static void markOccupiedSectors() {
        Arrays.fill(OCCUPIED_SECTORS, false);
        for (int i = 0; i < ACTIVE_ITEMS.size(); i++) {
            int idx = ACTIVE_ITEMS.get(i).sectorIndex;
            if (idx >= 0 && idx < OCCUPIED_SECTORS.length) {
                OCCUPIED_SECTORS[idx] = true;
            }
        }
    }

    private static boolean isSymbolSectorClearOfText(SkySector sector, CameraRenderState camState, float configScale) {
        float midDist = (sector.minDist + sector.maxDist) * 0.5f;
        float symWorldScale = LyricsVisualMath.computeWordWorldScale(configScale, sector.baseScale, true, 1.0f);
        float symHalfYaw = Math.max(1.4f, LyricsVisualMath.computeAngularHalfExtentDeg(8.0f, symWorldScale, midDist));
        float symHalfPitch = Math.max(1.4f, LyricsVisualMath.computeAngularHalfExtentDeg(8.0f, symWorldScale, midDist));
        return computeMinClearanceAgainstText(
                camState.yRot + sector.yawOffsetDeg, sector.pitchElevDeg,
                symHalfYaw, symHalfPitch, camState, configScale, true,
                SYMBOL_BOX_PAD_YAW_DEG, SYMBOL_BOX_PAD_PITCH_DEG
        ) >= 0.0f;
    }

    private static float computeMinClearanceAgainstText(
            float candYawDeg,
            float candPitchDeg,
            float candHalfYawDeg,
            float candHalfPitchDeg,
            CameraRenderState camState,
            float configScale,
            boolean includePhrases,
            float padYawDeg,
            float padPitchDeg
    ) {
        float minMargin = Float.POSITIVE_INFINITY;
        for (int i = 0; i < ACTIVE_ITEMS.size(); i++) {
            FloatingItem other = ACTIVE_ITEMS.get(i);
            if (other.backgroundSymbol || (!includePhrases && other.phraseItem)) continue;

            double tx = other.worldX - camState.pos.x;
            double ty = other.worldY - camState.pos.y;
            double tz = other.worldZ - camState.pos.z;
            double tdSq = distSq(tx, ty, tz);
            if (tdSq < 0.25) continue;

            double otherDist = Math.sqrt(tdSq);
            float otherYaw = LyricsVisualMath.computeYawDegFromRel(tx, tz);
            float otherPitch = LyricsVisualMath.computePitchDegFromRel(tx, ty, tz);
            float otherWorldScale = LyricsVisualMath.computeWordWorldScale(configScale, other.layerScale, false, 1.0f);
            float widthPx = Math.max(18.0f, Math.max(other.cachedWidth1, other.cachedWidth2));
            float heightPx = other.line2Text != null ? 20.0f : 10.0f;
            float otherHalfYaw = LyricsVisualMath.computeAngularHalfExtentDeg(widthPx, otherWorldScale, otherDist);
            float otherHalfPitch = LyricsVisualMath.computeAngularHalfExtentDeg(heightPx, otherWorldScale, otherDist);

            float margin = LyricsVisualMath.computeBoxClearanceMargin(
                    candYawDeg, candPitchDeg, candHalfYawDeg, candHalfPitchDeg,
                    otherYaw, otherPitch, otherHalfYaw, otherHalfPitch,
                    padYawDeg, padPitchDeg
            );
            if (margin < minMargin) {
                minMargin = margin;
            }
        }
        return minMargin;
    }

    private static void trimExcessForegroundWords(long nowMs) {
        int activeWords = 0;
        int totalWords = 0;
        for (int i = 0; i < ACTIVE_ITEMS.size(); i++) {
            FloatingItem item = ACTIVE_ITEMS.get(i);
            if (item.backgroundSymbol || item.phraseItem) continue;
            totalWords++;
            if (!item.exiting) activeWords++;
        }

        while (activeWords >= MAX_FOREGROUND_WORDS) {
            int oldestIdx = findOldestForegroundWordIndex(false);
            if (oldestIdx < 0) break;
            FloatingItem oldest = ACTIVE_ITEMS.get(oldestIdx);
            oldest.exiting = true;
            oldest.exitStartMs = nowMs;
            activeWords--;
        }

        while (totalWords >= MAX_FOREGROUND_WORDS_HARD) {
            int removeIdx = findOldestForegroundWordIndex(true);
            if (removeIdx < 0) removeIdx = findOldestForegroundWordIndex(false);
            if (removeIdx < 0) break;
            ACTIVE_ITEMS.remove(removeIdx);
            totalWords--;
        }
    }

    private static int findOldestForegroundWordIndex(boolean onlyExiting) {
        for (int i = 0; i < ACTIVE_ITEMS.size(); i++) {
            FloatingItem item = ACTIVE_ITEMS.get(i);
            if (!item.backgroundSymbol && !item.phraseItem && (!onlyExiting || item.exiting)) {
                return i;
            }
        }
        return -1;
    }

    private static void trimExcessItems(boolean isBgSymbol) {
        if (ACTIVE_ITEMS.size() < MAX_ACTIVE_ITEMS) return;
        for (int i = 0; i < ACTIVE_ITEMS.size(); i++) {
            FloatingItem item = ACTIVE_ITEMS.get(i);
            if (!item.phraseItem && item.backgroundSymbol == isBgSymbol) {
                ACTIVE_ITEMS.remove(i);
                return;
            }
        }
        if (!ACTIVE_ITEMS.isEmpty()) {
            ACTIVE_ITEMS.remove(0);
        }
    }

    private static int countBackgroundItems() {
        int count = 0;
        for (int i = 0; i < ACTIVE_ITEMS.size(); i++) {
            if (ACTIVE_ITEMS.get(i).backgroundSymbol) count++;
        }
        return count;
    }

    private static String joinTokens(String[] tokens) {
        SCRATCH_BUILDER.setLength(0);
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) SCRATCH_BUILDER.append(' ');
            SCRATCH_BUILDER.append(tokens[i]);
        }
        return SCRATCH_BUILDER.toString();
    }

    private static double distSq(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz;
    }

    private static String sanitizeRenderText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        SCRATCH_BUILDER.setLength(0);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (c >= ' ' && c != 127) {
                SCRATCH_BUILDER.append(c);
            }
        }
        return SCRATCH_BUILDER.toString().trim();
    }
}
