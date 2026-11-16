package com.wish.client.features.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsVisualMathTest {

    @Test
    void easingsRespectBoundsAndPopOvershoot() {
        assertEquals(0.0f, LyricsVisualMath.clamp01(-1.0f));
        assertEquals(0.0f, LyricsVisualMath.clamp01(Float.NaN));
        assertEquals(1.0f, LyricsVisualMath.clamp01(3.0f));

        assertEquals(0.0f, LyricsVisualMath.easeOutCubic(0.0f), 1e-4f);
        assertEquals(1.0f, LyricsVisualMath.easeOutCubic(1.0f), 1e-4f);
        assertEquals(0.0f, LyricsVisualMath.easeInCubic(0.0f), 1e-4f);
        assertEquals(1.0f, LyricsVisualMath.easeInCubic(1.0f), 1e-4f);
        assertEquals(0.0f, LyricsVisualMath.easeOutBack(0.0f), 1e-4f);
        assertEquals(1.0f, LyricsVisualMath.easeOutBack(1.0f), 1e-4f);
        assertTrue(LyricsVisualMath.easeOutBack(0.65f) > 1.0f);
    }

    @Test
    void colorBlendAndSkyHarmonyRemainInValidRange() {
        assertEquals(0xFF0000, LyricsVisualMath.blendRgb(0xFF0000, 0x00FF00, 0.0f));
        assertEquals(0x00FF00, LyricsVisualMath.blendRgb(0xFF0000, 0x00FF00, 1.0f));
        assertEquals(0x808080, LyricsVisualMath.blendRgb(0x000000, 0xFFFFFF, 0.5f));

        int base = 0xFFFFFF;
        int sakura = LyricsVisualMath.computeDynamicTextRgb(base, true, 6, 1.2f, 0.5f, false);
        assertNotEquals(base, sakura);
        assertEquals(0, sakura >>> 24);

        int defaultHarmony = LyricsVisualMath.computeSkyHarmonyRgb(0);
        int emeraldHarmony = LyricsVisualMath.computeSkyHarmonyRgb(7);
        int crimsonHarmony = LyricsVisualMath.computeSkyHarmonyRgb(8);
        int amberHarmony = LyricsVisualMath.computeSkyHarmonyRgb(9);
        assertNotEquals(defaultHarmony, emeraldHarmony);
        assertNotEquals(defaultHarmony, crimsonHarmony);
        assertNotEquals(defaultHarmony, amberHarmony);
        assertNotEquals(emeraldHarmony, crimsonHarmony);
        assertNotEquals(emeraldHarmony, amberHarmony);
        assertNotEquals(crimsonHarmony, amberHarmony);
    }

    @Test
    void karaokeRolesPrioritizeActiveAndSoftenUpcomingWords() {
        int base = 0x66CCFF;
        int accent = 0xFF9ED2;

        int active = LyricsVisualMath.computeKaraokeWordRgb(base, accent, LyricsVisualMath.KaraokeRole.ACTIVE);
        int sung = LyricsVisualMath.computeKaraokeWordRgb(base, accent, LyricsVisualMath.KaraokeRole.SUNG);
        int upcoming = LyricsVisualMath.computeKaraokeWordRgb(base, accent, LyricsVisualMath.KaraokeRole.UPCOMING);

        assertTrue(luminance(active) > luminance(sung));
        assertTrue(luminance(sung) > luminance(upcoming));
    }

    @Test
    void contrastOutlineScalesAlphaCleanly() {
        int outline = LyricsVisualMath.computeContrastOutlineColor(0xFFFFFF, 200);
        int outlineAlpha = outline >>> 24;
        assertEquals(176, outlineAlpha);
        assertTrue((outline & 0xFF) < 64);
    }

    @Test
    void worldScaleClampsInputsAndScalesBackgroundSmaller() {
        float fg = LyricsVisualMath.computeWordWorldScale(3.0f, 1.0f, false, 1.0f);
        float bg = LyricsVisualMath.computeWordWorldScale(3.0f, 1.0f, true, 1.0f);
        assertTrue(fg > bg);
        assertEquals(fg, LyricsVisualMath.computeWordWorldScale(Float.NaN, Float.NaN, false, Float.NaN), 1e-4f);
    }

    @Test
    void backgroundSymbolsAreBrightenedForVisibilityAgainstNightSky() {
        int dimBase = 0xAA6688;
        int fg = LyricsVisualMath.computeDynamicTextRgb(dimBase, false, 0, 1.0f, 0.0f, false);
        int bg = LyricsVisualMath.computeDynamicTextRgb(dimBase, false, 0, 1.0f, 0.0f, true);
        assertTrue(luminance(bg) > luminance(fg));
    }

    @Test
    void angularBoxOverlapDetectsHorizontalAndVerticalCollisionsCleanly() {
        assertTrue(LyricsVisualMath.doAngularBoxesOverlap(0.0f, 12.0f, 6.0f, 1.6f, 8.0f, 12.5f, 6.0f, 1.6f, 3.5f, 2.5f));
        assertFalse(LyricsVisualMath.doAngularBoxesOverlap(-12.0f, 12.0f, 6.0f, 1.6f, 10.0f, 12.0f, 6.0f, 1.6f, 3.5f, 2.5f));
        assertFalse(LyricsVisualMath.doAngularBoxesOverlap(0.0f, 14.0f, 6.0f, 1.6f, 0.0f, 21.0f, 6.0f, 1.6f, 3.5f, 2.5f));
        assertTrue(LyricsVisualMath.doAngularBoxesOverlap(-178.0f, 10.0f, 5.0f, 1.6f, 179.0f, 10.0f, 5.0f, 1.6f, 3.5f, 2.5f));
    }

    private static int luminance(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }
}
