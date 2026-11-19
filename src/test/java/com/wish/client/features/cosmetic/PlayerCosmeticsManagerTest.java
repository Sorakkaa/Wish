package com.wish.client.features.cosmetic;

import com.wish.client.color.CustomPlayerManager;
import com.wish.client.config.ModConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCosmeticsManagerTest {

    @Test
    void categoriesExposeTenNamedCosmeticsPlusNoneAndClampOutOfRangeIds() {
        assertEquals(10, PlayerCosmeticsManager.COUNT_PER_CATEGORY);
        assertEquals(11, PlayerCosmeticsManager.WING_NAMES.size());
        assertEquals(11, PlayerCosmeticsManager.HALO_NAMES.size());
        assertEquals(11, PlayerCosmeticsManager.PARTICLE_NAMES.size());

        assertEquals("None", PlayerCosmeticsManager.getWingName(-3));
        assertEquals("Demon Wings", PlayerCosmeticsManager.getWingName(1));
        assertEquals("Eclipse Seraph", PlayerCosmeticsManager.getWingName(2));
        assertEquals("Mecha Valkyrie", PlayerCosmeticsManager.getWingName(99));

        assertEquals("Angel Ring", PlayerCosmeticsManager.getHaloName(1));
        assertEquals("Runic Sigil", PlayerCosmeticsManager.getHaloName(5));
        assertEquals("Eclipse Halo", PlayerCosmeticsManager.getHaloName(10));

        assertEquals("Arcane Orbs", PlayerCosmeticsManager.getParticleName(1));
        assertEquals("Musical Notes", PlayerCosmeticsManager.getParticleName(5));
        assertEquals("Emerald Shards", PlayerCosmeticsManager.getParticleName(10));
    }

    @Test
    void resolveLoadoutHonorsVisibilityModesAndCustomColorOverrides() {
        ModConfig cfg = new ModConfig();
        cfg.cosmeticVisibility = 0;
        cfg.selectedWing = 2;
        cfg.selectedHalo = 7;
        cfg.selectedParticle = 9;
        cfg.cosmeticCustomColorEnabled = false;
        cfg.cosmeticCustomColor = "#12AB34";

        PlayerCosmeticsManager.CosmeticLoadout localDefault = PlayerCosmeticsManager.resolveLoadout(true, null, cfg);
        assertEquals(2, localDefault.wingId());
        assertEquals(7, localDefault.haloId());
        assertEquals(9, localDefault.particleId());
        assertFalse(localDefault.customColor());
        assertEquals(PlayerCosmeticsManager.getWingDefaultRgb(2), localDefault.accentRgb());

        cfg.cosmeticVisibility = 2;
        assertTrue(PlayerCosmeticsManager.resolveLoadout(true, null, cfg).isEmpty());
        assertTrue(PlayerCosmeticsManager.resolveLoadout(false, null, cfg).isEmpty());
        PlayerCosmeticsManager.beginPreviewIgnoreSize();
        try {
            assertTrue(PlayerCosmeticsManager.isPreviewIgnoringSize());
            PlayerCosmeticsManager.CosmeticLoadout preview = PlayerCosmeticsManager.resolveLoadout(true, null, cfg);
            assertFalse(preview.isEmpty());
            assertEquals(2, preview.wingId());
        } finally {
            PlayerCosmeticsManager.endPreviewIgnoreSize();
        }
        assertFalse(PlayerCosmeticsManager.isPreviewIgnoringSize());

        CustomPlayerManager.CustomPlayerData remoteData = new CustomPlayerManager.CustomPlayerData(
                UUID.randomUUID(), "", 0, 0.5, false, "\uE001", 1.0f, 1.0f, 1.0f, 0.0f,
                8, 10, 6, "#FF8800"
        );
        cfg.cosmeticVisibility = 0;
        PlayerCosmeticsManager.CosmeticLoadout remoteShown = PlayerCosmeticsManager.resolveLoadout(false, remoteData, cfg);
        assertEquals(8, remoteShown.wingId());
        assertEquals(10, remoteShown.haloId());
        assertEquals(6, remoteShown.particleId());
        assertTrue(remoteShown.customColor());
        assertEquals(0xFF8800, remoteShown.accentRgb());

        cfg.cosmeticVisibility = 1;
        assertTrue(PlayerCosmeticsManager.resolveLoadout(false, remoteData, cfg).isEmpty());
        assertFalse(PlayerCosmeticsManager.resolveLoadout(true, remoteData, cfg).isEmpty());

        cfg.cosmeticVisibility = 2;
        assertTrue(PlayerCosmeticsManager.resolveLoadout(true, remoteData, cfg).isEmpty());
        assertTrue(PlayerCosmeticsManager.resolveLoadout(false, remoteData, cfg).isEmpty());
    }

    @Test
    void particleOrbitMathProducesFiniteBoundedValuesInPlace() {
        float[] out = new float[4];
        for (int particleId = 1; particleId <= PlayerCosmeticsManager.COUNT_PER_CATEGORY; particleId++) {
            PlayerCosmeticsManager.computeParticleOrbitPoint(particleId, 2, 8, 42.5f, out);
            for (float v : out) {
                assertTrue(Float.isFinite(v));
            }
            assertTrue(out[3] >= 0.0f && out[3] <= 1.0f);
        }
    }

    @Test
    void proceduralAtlasGeneratesValidAbgrPixelsAndOpaqueBoneAttachmentEdges() {
        assertEquals(512, CosmeticAtlasMath.ATLAS_WIDTH);
        assertEquals(384, CosmeticAtlasMath.ATLAS_HEIGHT);
        assertEquals(128, CosmeticAtlasMath.CELL_SIZE);
        assertEquals(12, CosmeticAtlasMath.CELL_COUNT);

        int centerFeather = CosmeticAtlasMath.sampleAtlasPixelAbgr(64, 64);
        assertTrue((centerFeather >>> 24) > 0);
        int outsideCorner = CosmeticAtlasMath.sampleAtlasPixelAbgr(1, 1);
        assertEquals(0, outsideCorner >>> 24, "Transparent margin outside feather silhouette must stay empty");

        int ribCellX = CosmeticAtlasMath.CELL_RIB_WING * CosmeticAtlasMath.CELL_SIZE;
        int leftEdgePx = CosmeticAtlasMath.sampleAtlasPixelAbgr(ribCellX + Math.round(0.05f * CosmeticAtlasMath.CELL_SIZE), Math.round(0.08f * CosmeticAtlasMath.CELL_SIZE));
        int rightEdgePx = CosmeticAtlasMath.sampleAtlasPixelAbgr(ribCellX + Math.round(0.95f * CosmeticAtlasMath.CELL_SIZE), Math.round(0.08f * CosmeticAtlasMath.CELL_SIZE));
        int topEdgePx = CosmeticAtlasMath.sampleAtlasPixelAbgr(ribCellX + Math.round(0.50f * CosmeticAtlasMath.CELL_SIZE), Math.round(0.03f * CosmeticAtlasMath.CELL_SIZE));
        assertTrue((leftEdgePx >>> 24) > 200, "CELL_RIB_WING left bone edge must stay opaque");
        assertTrue((rightEdgePx >>> 24) > 200, "CELL_RIB_WING right bone edge must stay opaque");
        assertTrue((topEdgePx >>> 24) > 200, "CELL_RIB_WING top bone edge must stay opaque");

        int whiteX = Math.round(CosmeticAtlasMath.SOLID_WHITE_U * CosmeticAtlasMath.ATLAS_WIDTH);
        int whiteY = Math.round(CosmeticAtlasMath.SOLID_WHITE_V * CosmeticAtlasMath.ATLAS_HEIGHT);
        assertEquals(0xFFFFFFFF, CosmeticAtlasMath.sampleAtlasPixelAbgr(whiteX, whiteY),
                "SOLID_WHITE texel for 3D bones & halos must be opaque white");

        for (int c = 0; c < CosmeticAtlasMath.CELL_COUNT; c++) {
            int col = c % CosmeticAtlasMath.COLUMNS;
            int row = c / CosmeticAtlasMath.COLUMNS;
            boolean anyOpaque = false;
            for (int dy = 16; dy < 112; dy += 8) {
                for (int dx = 16; dx < 112; dx += 8) {
                    int px = CosmeticAtlasMath.sampleAtlasPixelAbgr(col * CosmeticAtlasMath.CELL_SIZE + dx, row * CosmeticAtlasMath.CELL_SIZE + dy);
                    if ((px >>> 24) > 128) {
                        anyOpaque = true;
                        break;
                    }
                }
                if (anyOpaque) break;
            }
            assertTrue(anyOpaque, "Atlas cell " + c + " must contain visible non-transparent art");
        }

        float[] uv = new float[4];
        for (int c = 0; c < CosmeticAtlasMath.CELL_COUNT; c++) {
            CosmeticAtlasMath.getCellUv(c, uv);
            assertTrue(uv[0] >= 0.0f && uv[0] < uv[2] && uv[2] <= 1.0f);
            assertTrue(uv[1] >= 0.0f && uv[1] < uv[3] && uv[3] <= 1.0f);
        }
    }
}
