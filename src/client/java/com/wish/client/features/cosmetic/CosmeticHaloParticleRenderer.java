package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

final class CosmeticHaloParticleRenderer {
    private static final ThreadLocal<float[]> HP_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> HP_ORB_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> HP_SPARK_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> HP_POS_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final int FULL_BRIGHT = 15728880;

    private CosmeticHaloParticleRenderer() {
    }

    static void renderHalo(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int haloId,
            float floatY,
            float ageTicks
    ) {
        CosmeticHaloRenderer.renderHalo(pose, vc, haloId, floatY, ageTicks, true, true);
    }

    static void renderHaloSolid(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int haloId,
            float floatY,
            float ageTicks
    ) {
        CosmeticHaloRenderer.renderHalo(pose, vc, haloId, floatY, ageTicks, true, false);
    }

    static void renderHaloGlow(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int haloId,
            float floatY,
            float ageTicks
    ) {
        CosmeticHaloRenderer.renderHalo(pose, vc, haloId, floatY, ageTicks, false, true);
    }

    static void renderParticles(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int particleId,
            float ageTicks
    ) {
        float[] uv = HP_UV_SCRATCH.get();
        float[] orbUv = HP_ORB_UV_SCRATCH.get();
        float[] sparkUv = HP_SPARK_UV_SCRATCH.get();
        float[] posAlpha = HP_POS_SCRATCH.get();
        int cell = switch (particleId) {
            case 1 -> CosmeticAtlasMath.CELL_HEART;
            case 2 -> CosmeticAtlasMath.CELL_EMBER;
            case 3 -> CosmeticAtlasMath.CELL_NOTE;
            case 4 -> CosmeticAtlasMath.CELL_DIAMOND;
            case 5 -> CosmeticAtlasMath.CELL_SNOWFLAKE;
            case 6 -> CosmeticAtlasMath.CELL_LIGHTNING;
            case 7 -> CosmeticAtlasMath.CELL_RING_RUNE;
            case 8 -> CosmeticAtlasMath.CELL_PETAL;
            case 9 -> CosmeticAtlasMath.CELL_STAR_4PT;
            case 10 -> CosmeticAtlasMath.CELL_CRESCENT;
            default -> CosmeticAtlasMath.CELL_ORB;
        };
        CosmeticAtlasMath.getCellUv(cell, uv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        int count = switch (particleId) {
            case 1, 3, 10 -> 18;
            case 4, 6 -> 20;
            default -> 22;
        };
        int baseRgb = PlayerCosmeticsManager.getParticleDefaultRgb(particleId);
        int r = (baseRgb >>> 16) & 0xFF;
        int g = (baseRgb >>> 8) & 0xFF;
        int b = baseRgb & 0xFF;

        for (int i = 0; i < count; i++) {
            PlayerCosmeticsManager.computeParticleOrbitPoint(particleId, i, count, ageTicks, posAlpha);
            int alpha = Math.max(0, Math.min(255, Math.round(posAlpha[3] * 255.0f)));
            if (alpha <= 8) {
                continue;
            }
            float roll = switch (particleId) {
                case 1, 3 -> Mth.sin(ageTicks * 0.14f + i) * 0.20f;
                case 2, 4, 7, 9 -> ageTicks * 0.06f + i * 0.5f;
                case 5, 8 -> ageTicks * 0.07f + i * 0.7f;
                case 6 -> Mth.sin(ageTicks * 0.25f + i * 1.4f) * 0.35f;
                default -> 0.0f;
            };
            float halfSize = switch (particleId) {
                case 1, 8, 10 -> 0.082f;
                case 4, 5, 6 -> 0.088f;
                case 2, 3, 7, 9 -> 0.090f;
                default -> 0.082f;
            };

            float[] mainUv = uv;
            int pr = r, pg = g, pb = b;
            int haloR = r, haloG = g, haloB = b;
            int coreR = Math.min(255, pr + 90), coreG = Math.min(255, pg + 95), coreB = Math.min(255, pb + 90);
            if (particleId == 1) {
                if (i % 3 == 1) { pr = 255; pg = 102; pb = 136; }
                else if (i % 3 == 2) { pr = 255; pg = 58; pb = 92; }
                haloR = pr; haloG = pg; haloB = pb;
                coreR = Math.min(255, pr + 90); coreG = Math.min(255, pg + 95); coreB = Math.min(255, pb + 90);
            } else if (particleId == 2 && i % 2 == 1) {
                pr = 255; pg = 184; pb = 77;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 255; coreG = 240; coreB = 180;
            } else if (particleId == 3 && i % 2 == 1) {
                pr = 255; pg = 240; pb = 168;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 255; coreG = 252; coreB = 220;
            } else if (particleId == 4 && i % 2 == 1) {
                pr = 153; pg = 255; pb = 209;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 225; coreG = 255; coreB = 242;
            } else if (particleId == 5 && i % 2 == 1) {
                pr = 221; pg = 251; pb = 255;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 245; coreG = 255; coreB = 255;
            } else if (particleId == 6 && i % 2 == 1) {
                pr = 153; pg = 194; pb = 255;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 225; coreG = 242; coreB = 255;
            } else if (particleId == 7 && i % 2 == 1) {
                pr = 213; pg = 128; pb = 255;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 245; coreG = 205; coreB = 255;
            } else if (particleId == 8 && i % 2 == 1) {
                pr = 255; pg = 194; pb = 222;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 255; coreG = 235; coreB = 245;
            } else if (particleId == 9 && i % 2 == 1) {
                pr = 216; pg = 232; pb = 255;
                haloR = pr; haloG = pg; haloB = pb;
                coreR = 255; coreG = 255; coreB = 255;
            } else if (particleId == 10) {
                pr = 36; pg = 30; pb = 48;
                haloR = 115; haloG = 105; haloB = 148;
                coreR = 128; coreG = 118; coreB = 162;
            }

            CosmeticGeometryHelper.emitCameraBillboardQuad(
                    pose, vc, FULL_BRIGHT,
                    posAlpha[0], posAlpha[1], posAlpha[2],
                    halfSize * 1.85f, 0.0f, orbUv, haloR, haloG, haloB,
                    Math.min(255, Math.round(alpha * 0.42f))
            );
            CosmeticGeometryHelper.emitCameraBillboardQuad(
                    pose, vc, FULL_BRIGHT,
                    posAlpha[0], posAlpha[1], posAlpha[2],
                    halfSize, roll, mainUv, pr, pg, pb, alpha
            );
            CosmeticGeometryHelper.emitCameraBillboardQuad(
                    pose, vc, FULL_BRIGHT,
                    posAlpha[0], posAlpha[1], posAlpha[2],
                    halfSize * 0.48f, roll, (particleId == 1 || particleId == 3 || particleId == 8) ? orbUv : mainUv,
                    coreR, coreG, coreB,
                    Math.min(255, Math.round(alpha * 0.88f))
            );
        }

        int sparkleR = (particleId == 10) ? 138 : Math.min(255, r + 45);
        int sparkleG = (particleId == 10) ? 128 : Math.min(255, g + 45);
        int sparkleB = (particleId == 10) ? 172 : Math.min(255, b + 45);
        int sparkleCount = 10;
        for (int s = 0; s < sparkleCount; s++) {
            PlayerCosmeticsManager.computeParticleOrbitPoint(particleId, s, sparkleCount, ageTicks + 14.5f, posAlpha);
            float sx = posAlpha[0] * 1.14f;
            float sy = posAlpha[1] + 0.06f * Mth.cos(ageTicks * 0.17f + s);
            float sz = posAlpha[2] * 1.14f;
            float twinkle = 0.5f + 0.5f * Mth.sin(ageTicks * 0.28f + s * 1.9f);
            int sAlpha = Math.max(0, Math.min(255, Math.round(posAlpha[3] * (0.45f + 0.55f * twinkle) * 235.0f)));
            if (sAlpha <= 10) {
                continue;
            }
            float sSize = 0.048f + 0.020f * twinkle;
            CosmeticHaloRenderer.emitGlowBillboard(pose, vc, sx, sy, sz, sSize, ageTicks * 0.10f + s,
                    orbUv, sparkUv, sparkleR, sparkleG, sparkleB, sAlpha);
        }
    }
}
