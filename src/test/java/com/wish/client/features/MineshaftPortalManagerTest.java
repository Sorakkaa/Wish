package com.wish.client.features;

import com.wish.client.config.ModConfig;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineshaftPortalManagerTest {

    private final boolean originalEnableGlow = ModConfig.INSTANCE.enableMineshaftPortalGlow;
    private final String originalGlowColor = ModConfig.INSTANCE.mineshaftPortalGlowColor;

    @AfterEach
    void cleanup() {
        ModConfig.INSTANCE.enableMineshaftPortalGlow = originalEnableGlow;
        ModConfig.INSTANCE.mineshaftPortalGlowColor = originalGlowColor;
        MineshaftPortalManager.reset();
    }

    @Test
    void isMineshaftPortalNameDetectsHypixelFormatsAndRejectsUnrelatedNames() {
        assertTrue(MineshaftPortalManager.isMineshaftPortalName("\u00A7b[MVP\u00A7d+\u00A7b] \u00A7d\u00A7lMeow4Ma\u00EB\u00A7a's Mineshaft Portal"));
        assertTrue(MineshaftPortalManager.isMineshaftPortalName("[VIP]\u00A0Sorakkaa\u2019s Mineshaft Portal"));
        assertTrue(MineshaftPortalManager.isMineshaftPortalName("  Mineshaft Portal  "));

        assertFalse(MineshaftPortalManager.isMineshaftPortalName(null));
        assertFalse(MineshaftPortalManager.isMineshaftPortalName(""));
        assertFalse(MineshaftPortalManager.isMineshaftPortalName("\u00A7eClick to enter!"));
        assertFalse(MineshaftPortalManager.isMineshaftPortalName("Super Automaton"));
        assertFalse(MineshaftPortalManager.isMineshaftPortalName("Portal to Dwarven Mines"));
        assertFalse(MineshaftPortalManager.isMineshaftPortalName("Meow4Ma\u00EB's Golden Dragon"));
    }

    @Test
    void isClickToEnterLabelMatchesPortalSubLineOnly() {
        assertTrue(MineshaftPortalManager.isClickToEnterLabel("\u00A7eClick to enter!"));
        assertTrue(MineshaftPortalManager.isClickToEnterLabel("  \u00A0Click to enter "));

        assertFalse(MineshaftPortalManager.isClickToEnterLabel(null));
        assertFalse(MineshaftPortalManager.isClickToEnterLabel(""));
        assertFalse(MineshaftPortalManager.isClickToEnterLabel("Meow4Ma\u00EB's Mineshaft Portal"));
    }

    @Test
    void parseAndGetGlowColorRgbRespectValidHexAndFallbackSafely() {
        assertEquals(0xAA55FF, MineshaftPortalManager.parseGlowColorRgb("#AA55FF"));
        assertEquals(0x55FFAA, MineshaftPortalManager.parseGlowColorRgb(" #55ffaa "));
        assertEquals(MineshaftPortalManager.DEFAULT_GLOW_RGB, MineshaftPortalManager.parseGlowColorRgb(null));
        assertEquals(MineshaftPortalManager.DEFAULT_GLOW_RGB, MineshaftPortalManager.parseGlowColorRgb(""));
        assertEquals(MineshaftPortalManager.DEFAULT_GLOW_RGB, MineshaftPortalManager.parseGlowColorRgb("invalid"));
        assertEquals(MineshaftPortalManager.DEFAULT_GLOW_RGB, MineshaftPortalManager.parseGlowColorRgb("#123"));

        ModConfig.INSTANCE.mineshaftPortalGlowColor = "#00FFCC";
        assertEquals(0x00FFCC, MineshaftPortalManager.getGlowColorRgb());
        ModConfig.INSTANCE.mineshaftPortalGlowColor = "#FF3366";
        assertEquals(0xFF3366, MineshaftPortalManager.getGlowColorRgb());
    }

    @Test
    void brightenRgbClampsAndBlendsTowardWhite() {
        assertEquals(0xAA55FF, MineshaftPortalManager.brightenRgb(0xAA55FF, -1.0f));
        assertEquals(0x808080, MineshaftPortalManager.brightenRgb(0x000000, 0.5f));
        assertEquals(0xFFFFFF, MineshaftPortalManager.brightenRgb(0xAA55FF, 2.0f));
    }

    @Test
    void resolvePortalBoundsHandlesMarkerSmallNormalAndCompanionEntities() {
        AABB markerBounds = MineshaftPortalManager.resolvePortalBounds(
                10.5, 102.0, -24.5, 0.0, true, 101.72, null, null
        );
        assertEquals(9.95, markerBounds.minX, 1e-6);
        assertEquals(11.05, markerBounds.maxX, 1e-6);
        assertEquals(-25.05, markerBounds.minZ, 1e-6);
        assertEquals(-23.95, markerBounds.maxZ, 1e-6);
        assertEquals(101.64, markerBounds.maxY, 1e-6);
        assertEquals(99.79, markerBounds.minY, 1e-6);

        AABB companionBounds = MineshaftPortalManager.resolvePortalBounds(
                10.5, 102.0, -24.5, 0.0, true, 101.72, 100.0, 101.975
        );
        assertEquals(99.95, companionBounds.minY, 1e-6);
        assertEquals(102.025, companionBounds.maxY, 1e-6);

        AABB normalBounds = MineshaftPortalManager.resolvePortalBounds(
                0.0, 50.0, 0.0, 1.975, false, null, null, null
        );
        assertEquals(49.95, normalBounds.minY, 1e-6);
        assertEquals(52.025, normalBounds.maxY, 1e-6);

        AABB smallBounds = MineshaftPortalManager.resolvePortalBounds(
                0.0, 51.0, 0.0, 0.9875, false, 50.7, null, null
        );
        assertEquals(50.0, smallBounds.minY, 1e-6);
        assertEquals(51.95, smallBounds.maxY, 1e-6);
    }
}
