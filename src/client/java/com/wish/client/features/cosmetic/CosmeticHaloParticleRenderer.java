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
            case 2 -> CosmeticAtlasMath.CELL_PETAL;
            case 3, 4 -> CosmeticAtlasMath.CELL_SPARK;
            case 5 -> CosmeticAtlasMath.CELL_NOTE;
            case 6 -> CosmeticAtlasMath.CELL_SNOWFLAKE;
            case 7 -> CosmeticAtlasMath.CELL_HEART;
            case 8 -> CosmeticAtlasMath.CELL_LIGHTNING;
            case 10 -> CosmeticAtlasMath.CELL_DIAMOND;
            default -> CosmeticAtlasMath.CELL_ORB;
        };
        CosmeticAtlasMath.getCellUv(cell, uv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        int count = switch (particleId) {
            case 1, 5, 7 -> 18;
            case 8, 10 -> 20;
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
                case 2, 6 -> ageTicks * 0.07f + i * 0.7f;
                case 4, 10 -> ageTicks * 0.06f + i * 0.5f;
                case 5, 7 -> Mth.sin(ageTicks * 0.14f + i) * 0.20f;
                case 8 -> Mth.sin(ageTicks * 0.25f + i * 1.4f) * 0.35f;
                default -> 0.0f;
            };
            float halfSize = switch (particleId) {
                case 1, 2, 7 -> 0.082f;
                case 6, 8, 10 -> 0.088f;
                case 3, 4, 5, 9 -> 0.090f;
                default -> 0.082f;
            };

            float[] mainUv = (particleId == 9 && i % 2 == 1) ? sparkUv : uv;
            int pr = r, pg = g, pb = b;
            if (particleId == 5) {
                int mod = i % 3;
                if (mod == 1) { pr = 115; pg = 235; pb = 255; }
                else if (mod == 2) { pr = 255; pg = 215; pb = 110; }
            } else if (particleId == 6) {
                if (i % 2 == 1) { pr = 220; pg = 250; pb = 255; }
            } else if (particleId == 7) {
                if (i % 3 == 1) { pr = 255; pg = 155; pb = 205; }
                else if (i % 3 == 2) { pr = 255; pg = 55; pb = 115; }
            } else if (particleId == 8) {
                if (i % 3 == 0) { pr = 255; pg = 232; pb = 105; }
            } else if (particleId == 9) {
                if (i % 2 == 1) { pr = 255; pg = 210; pb = 78; }
            } else if (particleId == 10) {
                if (i % 2 == 1) { pr = 145; pg = 255; pb = 210; }
            }

            CosmeticGeometryHelper.emitCameraBillboardQuad(
                    pose, vc, FULL_BRIGHT,
                    posAlpha[0], posAlpha[1], posAlpha[2],
                    halfSize * 1.85f, 0.0f, orbUv, pr, pg, pb,
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
                    halfSize * 0.48f, roll, (particleId == 5 || particleId == 2 || particleId == 7) ? orbUv : mainUv,
                    Math.min(255, pr + 90), Math.min(255, pg + 95), Math.min(255, pb + 90),
                    Math.min(255, Math.round(alpha * 0.88f))
            );
        }

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
                    orbUv, sparkUv, Math.min(255, r + 45), Math.min(255, g + 45), Math.min(255, b + 45), sAlpha);
        }
    }
}
