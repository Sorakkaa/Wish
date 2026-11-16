package com.wish.client.features.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatPawTrailMathTest {

    @Test
    void pawSdfMatchesSquatTrilobedMainPadAndFourCompactToeBeansWithCleanGap() {
        assertTrue(CatPawTrailMath.pawSignedDistance(0.0f, 0.05f) < 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(-0.25f, 0.12f) < 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(0.25f, 0.12f) < 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(0.0f, -0.10f) < 0.0f);

        assertTrue(CatPawTrailMath.pawSignedDistance(-0.45f, -0.30f) < 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(-0.18f, -0.50f) < 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(0.18f, -0.50f) < 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(0.45f, -0.30f) < 0.0f);

        assertTrue(CatPawTrailMath.pawSignedDistance(0.0f, -0.30f) > 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(-0.18f, -0.30f) > 0.0f);
        assertTrue(CatPawTrailMath.pawSignedDistance(0.18f, -0.30f) > 0.0f);
    }

    @Test
    void atlasSamplingProducesNonZeroSpritesAndZeroAlphaOnQuadrantBorders() {
        int q0Alpha = (CatPawTrailMath.sampleAtlasPixelAbgr(64, 66) >>> 24) & 0xFF;
        assertTrue(q0Alpha > 0);

        int q1Alpha = (CatPawTrailMath.sampleAtlasPixelAbgr(128 + 64, 66) >>> 24) & 0xFF;
        assertTrue(q1Alpha > 150);

        int q2Alpha = (CatPawTrailMath.sampleAtlasPixelAbgr(64, 128 + 66) >>> 24) & 0xFF;
        assertTrue(q2Alpha > 100);

        int rippleRingAlpha = (CatPawTrailMath.sampleAtlasPixelAbgr(160 + 22, 160) >>> 24) & 0xFF;
        assertTrue(rippleRingAlpha > 150);

        int starAlpha = (CatPawTrailMath.sampleAtlasPixelAbgr(224, 160) >>> 24) & 0xFF;
        assertTrue(starAlpha > 200);

        int heartAlpha = (CatPawTrailMath.sampleAtlasPixelAbgr(160, 224) >>> 24) & 0xFF;
        assertTrue(heartAlpha > 200);

        int[] borderCoords = { 0, 127, 128, 255 };
        for (int b : borderCoords) {
            assertEquals(0, (CatPawTrailMath.sampleAtlasPixelAbgr(b, 64) >>> 24) & 0xFF);
            assertEquals(0, (CatPawTrailMath.sampleAtlasPixelAbgr(64, b) >>> 24) & 0xFF);
            assertEquals(0, (CatPawTrailMath.sampleAtlasPixelAbgr(b, 192) >>> 24) & 0xFF);
            assertEquals(0, (CatPawTrailMath.sampleAtlasPixelAbgr(192, b) >>> 24) & 0xFF);
        }
    }

    @Test
    void customColorDerivesVividGlowBodyAndCoreWithFixedGlowAndScale() {
        assertEquals(1.0f, CatPawTrailMath.FIXED_GLOW_MULT, 1e-5f);
        assertEquals(1.0f, CatPawTrailMath.FIXED_SCALE, 1e-5f);

        int defaultGlow = CatPawTrailMath.resolveGlowRgb(CatPawTrailMath.DEFAULT_COLOR_RGB);
        int defaultBody = CatPawTrailMath.resolveBodyRgb(CatPawTrailMath.DEFAULT_COLOR_RGB);
        int defaultCore = CatPawTrailMath.resolveCoreRgb(CatPawTrailMath.DEFAULT_COLOR_RGB);
        assertEquals(0xFF, (defaultGlow >> 16) & 0xFF);
        assertTrue(((defaultGlow >> 8) & 0xFF) < 0x70);
        assertTrue(((defaultBody >> 16) & 0xFF) >= ((CatPawTrailMath.DEFAULT_COLOR_RGB >> 16) & 0xFF));
        assertTrue(((defaultCore >> 8) & 0xFF) > ((defaultBody >> 8) & 0xFF));

        int cyanInput = 0x77DDFF;
        int cyanGlow = CatPawTrailMath.resolveGlowRgb(cyanInput);
        int cyanBody = CatPawTrailMath.resolveBodyRgb(cyanInput);
        int cyanCore = CatPawTrailMath.resolveCoreRgb(cyanInput);
        assertTrue((cyanGlow & 0xFF) >= ((cyanGlow >> 16) & 0xFF));
        assertTrue(((cyanBody >> 16) & 0xFF) >= ((cyanInput >> 16) & 0xFF));
        assertTrue(((cyanCore >> 16) & 0xFF) > ((cyanBody >> 16) & 0xFF));

        int whiteGlow = CatPawTrailMath.resolveGlowRgb(0xFFFFFF);
        assertTrue(((whiteGlow >> 16) & 0xFF) == 0xFF);
    }

    @Test
    void bounceScaleAndFadeAlphaCurvesMatchKawaiiTiming() {
        float duration = 3.0f;
        assertEquals(0.60f, CatPawTrailMath.computeBounceScale(0.0f, duration), 1e-4f);
        assertEquals(1.13f, CatPawTrailMath.computeBounceScale(0.12f, duration), 1e-4f);
        assertEquals(1.00f, CatPawTrailMath.computeBounceScale(0.20f, duration), 1e-4f);
        assertTrue(CatPawTrailMath.computeBounceScale(duration, duration) <= 0.901f);

        assertEquals(0.0f, CatPawTrailMath.computeFootprintAlpha(-0.1f, duration), 1e-4f);
        assertEquals(0.0f, CatPawTrailMath.computeFootprintAlpha(0.0f, duration), 1e-4f);
        float earlyAlpha = CatPawTrailMath.computeFootprintAlpha(0.15f, duration);
        float lateAlpha = CatPawTrailMath.computeFootprintAlpha(2.40f, duration);
        assertTrue(earlyAlpha > 0.85f);
        assertTrue(lateAlpha > 0.0f && lateAlpha < earlyAlpha);
        assertEquals(0.0f, CatPawTrailMath.computeFootprintAlpha(duration, duration), 1e-4f);
    }

    @Test
    void alternatingStepOffsetsAndOutwardYawMatchDesign() {
        assertEquals(-0.18f, CatPawTrailMath.computeStepLateralOffset(true), 1e-5f);
        assertEquals(0.18f, CatPawTrailMath.computeStepLateralOffset(false), 1e-5f);

        float baseYaw = 1.25f;
        float leftYaw = CatPawTrailMath.computeStepYawRad(baseYaw, true);
        float rightYaw = CatPawTrailMath.computeStepYawRad(baseYaw, false);
        assertEquals(-Math.toRadians(7.0), leftYaw - baseYaw, 1e-5);
        assertEquals(Math.toRadians(7.0), rightYaw - baseYaw, 1e-5);
    }
}
