package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

final class CosmeticHaloRenderer {
    private static final ThreadLocal<float[]> HALO_AUX_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> HALO_ORB_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> HALO_SPARK_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final int FULL_BRIGHT = 15728880;
    private static final float TAU = (float) (Math.PI * 2.0);

    private CosmeticHaloRenderer() {
    }

    static void renderHalo(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int haloId,
            float floatY,
            float ageTicks,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float centerY = -0.61f + floatY;
        switch (haloId) {
            case 1 -> renderAngelRingHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 2 -> renderVoidCrownHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 3 -> renderStarlightAstrolabeHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 4 -> renderSakuraWreathHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 5 -> renderRunicSigilHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 6 -> renderSolarFlareHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 7 -> renderFrostDiademHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 8 -> renderDemonHornsHalo(pose, vc, floatY, ageTicks, renderSolid, renderGlow);
            case 9 -> renderCyberVisorHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            case 10 -> renderEclipseHalo(pose, vc, centerY, ageTicks, renderSolid, renderGlow);
            default -> {}
        }
    }

    private static void renderAngelRingHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.025f;
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.27f, 0.020f, 24, spin, 0.08f, 255, 232, 115, 250);
            for (int i = 0; i < 4; i++) {
                float a = spin + (i * TAU) / 4.0f;
                float rx = Mth.cos(a) * 0.28f;
                float rz = Mth.sin(a) * 0.28f;
                float ry = centerY + Mth.sin(a) * 0.28f * Mth.sin(0.08f);
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT,
                        rx, ry, rz, 0.011f,
                        Mth.cos(a) * 0.37f, ry - 0.015f, Mth.sin(a) * 0.37f, 0.001f,
                        255, 245, 165, 235);
            }
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.27f, 0.042f, 24, spin, 0.08f, 255, 246, 175, 92);
            for (int i = 0; i < 8; i++) {
                float a = spin * 1.4f + (i * TAU) / 8.0f;
                float mx = Mth.cos(a) * 0.34f;
                float mz = Mth.sin(a) * 0.34f;
                float my = centerY - 0.025f + Mth.sin(ageTicks * 0.16f + i * 0.9f) * 0.030f;
                emitGlowBillboard(pose, vc, mx, my, mz, 0.042f, ageTicks * 0.05f + i, orbUv, sparkUv, 255, 240, 145, 210);
            }
        }
    }

    private static void renderVoidCrownHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.028f;
        float crownY = centerY + 0.04f;
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, crownY, 0.0f, 0.23f, 0.024f, 16, spin, 0.0f, 52, 18, 92, 255);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, crownY, 0.0f, 0.23f, 0.044f, 16, spin, 0.0f, 178, 76, 255, 96);
        }

        int peaks = 8;
        for (int i = 0; i < peaks; i++) {
            float a = spin + (i * TAU) / peaks;
            float bx = Mth.cos(a) * 0.23f;
            float bz = Mth.sin(a) * 0.23f;
            boolean mainPeak = (i % 2 == 0);
            float height = mainPeak ? 0.17f : 0.10f;
            float tiltOut = mainPeak ? 0.26f : 0.245f;
            float tx = Mth.cos(a) * tiltOut;
            float tz = Mth.sin(a) * tiltOut;

            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT,
                        bx, crownY, bz, mainPeak ? 0.024f : 0.017f,
                        tx, crownY - height, tz, 0.002f,
                        mainPeak ? 175 : 132, mainPeak ? 86 : 62, mainPeak ? 255 : 225, 255);
            }
            if (renderGlow) {
                float gemFloat = Mth.sin(ageTicks * 0.14f + i) * 0.015f;
                float gemSize = mainPeak ? 0.056f : 0.038f;
                emitGlowBillboard(pose, vc, tx, crownY - height - 0.040f + gemFloat, tz,
                        gemSize, spin * 2.0f + i, orbUv, sparkUv, 220, 145, 255, mainPeak ? 240 : 190);
            }
        }

        if (renderGlow) {
            for (int i = 0; i < 6; i++) {
                float a = -spin * 1.5f + (i * TAU) / 6.0f;
                float ox = Mth.cos(a) * 0.34f;
                float oz = Mth.sin(a) * 0.34f;
                float oy = crownY - 0.08f + Mth.sin(ageTicks * 0.18f + i * 1.2f) * 0.04f;
                emitGlowBillboard(pose, vc, ox, oy, oz, 0.044f, ageTicks * 0.07f + i, orbUv, sparkUv, 195, 95, 255, 215);
            }
        }
    }

    private static void renderStarlightAstrolabeHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spinA = ageTicks * 0.032f;
        float spinB = -ageTicks * 0.026f;
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.30f, 0.015f, 24, spinA, 0.28f, 110, 228, 232, 245);
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.25f, 0.014f, 24, spinB, -0.32f, 200, 245, 255, 240);
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.20f, 0.011f, 20, spinA * 1.4f, 0.14f, 155, 248, 255, 238);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.30f, 0.032f, 24, spinA, 0.28f, 125, 245, 255, 82);
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.25f, 0.028f, 24, spinB, -0.32f, 180, 250, 255, 76);

            emitGlowBillboard(pose, vc, 0.0f, centerY - 0.02f, 0.0f, 0.070f, ageTicks * 0.04f, orbUv, sparkUv, 200, 255, 255, 235);

            int starCount = 10;
            for (int i = 0; i < starCount; i++) {
                float a = spinA * 1.4f + (i * TAU) / starCount;
                float ringR = (i % 2 == 0) ? 0.36f : 0.28f;
                float rx = Mth.cos(a) * ringR;
                float rz = Mth.sin(a) * ringR;
                float ry = centerY + Mth.sin(a + spinB) * 0.09f;
                float pulse = 0.046f + 0.018f * Mth.sin(ageTicks * 0.22f + i * 1.3f);
                emitGlowBillboard(pose, vc, rx, ry, rz, pulse, ageTicks * 0.08f + i, orbUv, sparkUv, 165, 252, 255, 240);
            }
        }
    }

    private static void renderSakuraWreathHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.016f;
        float wreathY = centerY + 0.03f;
        float[] petalUv = HALO_AUX_UV_SCRATCH.get();
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_PETAL, petalUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, wreathY, 0.0f, 0.26f, 0.018f, 20, spin, 0.05f, 130, 82, 95, 255);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, wreathY, 0.0f, 0.26f, 0.036f, 20, spin, 0.05f, 255, 165, 210, 88);
        }

        int flowerCount = 8;
        for (int f = 0; f < flowerCount; f++) {
            float a = spin + (f * TAU) / flowerCount;
            float fx = Mth.cos(a) * 0.265f;
            float fz = Mth.sin(a) * 0.265f;
            float fy = wreathY + Mth.sin(a) * 0.05f * 0.265f - 0.015f;

            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT,
                        fx, fy + 0.01f, fz, 0.014f, fx, fy - 0.012f, fz, 0.014f, 255, 225, 130, 255);

                for (int p = 0; p < 5; p++) {
                    float pa = a + (p * TAU) / 5.0f;
                    float px = fx + Mth.cos(pa) * 0.052f;
                    float pz = fz + Mth.sin(pa) * 0.052f;
                    float py = fy - 0.014f;
                    float sideX = -Mth.sin(pa) * 0.026f;
                    float sideZ = Mth.cos(pa) * 0.026f;

                    CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, FULL_BRIGHT,
                            fx, fy, fz,
                            px - sideX, py, pz - sideZ,
                            px * 1.02f - fx * 0.02f, py - 0.008f, pz * 1.02f - fz * 0.02f,
                            px + sideX, py, pz + sideZ,
                            petalUv, 255, 155, 200, 245);
                }
            }
            if (renderGlow) {
                CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, FULL_BRIGHT,
                        fx, fy - 0.012f, fz, 0.058f, 0.0f, orbUv, 255, 185, 220, 120);
            }
        }

        if (renderGlow) {
            for (int i = 0; i < 8; i++) {
                float a = -spin * 1.4f + (i * TAU) / 8.0f;
                float pr = 0.34f + 0.04f * Mth.sin(ageTicks * 0.11f + i);
                float py = wreathY - 0.04f + Mth.sin(ageTicks * 0.15f + i * 1.3f) * 0.045f;
                emitGlowBillboard(pose, vc, Mth.cos(a) * pr, py, Mth.sin(a) * pr,
                        0.046f, ageTicks * 0.06f + i, orbUv, petalUv, 255, 175, 215, 215);
            }
        }
    }

    private static void renderRunicSigilHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.042f;
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.34f, 0.014f, 24, spin, 0.0f, 0, 245, 212, 248);
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.22f, 0.011f, 24, -spin, 0.0f, 155, 255, 240, 240);

            for (int i = 0; i < 8; i++) {
                float a0 = spin + (i * TAU) / 8.0f;
                float a1 = spin + ((i + 3) * TAU) / 8.0f;
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT,
                        Mth.cos(a0) * 0.33f, centerY, Mth.sin(a0) * 0.33f, 0.007f,
                        Mth.cos(a1) * 0.33f, centerY, Mth.sin(a1) * 0.33f, 0.007f,
                        110, 255, 230, 238);
            }
        }

        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.34f, 0.032f, 24, spin, 0.0f, 35, 255, 225, 92);
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.22f, 0.025f, 24, -spin, 0.0f, 120, 255, 238, 86);
        }

        for (int c = 0; c < 4; c++) {
            float ca = spin * 1.25f + (c * TAU) / 4.0f;
            float cx = Mth.cos(ca) * 0.40f;
            float cz = Mth.sin(ca) * 0.40f;
            float cy = centerY + Mth.sin(ageTicks * 0.18f + c) * 0.025f;
            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, cx, cy, cz, 0.024f, cx, cy - 0.045f, cz, 0.001f, 0, 245, 212, 255);
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, cx, cy, cz, 0.024f, cx, cy + 0.045f, cz, 0.001f, 180, 255, 245, 255);
            }
            if (renderGlow) {
                CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, FULL_BRIGHT,
                        cx, cy, cz, 0.072f, 0.0f, orbUv, 75, 255, 232, 130);
            }
        }
        if (renderGlow) {
            for (int i = 0; i < 8; i++) {
                float a = -spin * 1.3f + (i * TAU) / 8.0f;
                float sy = centerY + Mth.sin(ageTicks * 0.20f + i) * 0.032f;
                emitGlowBillboard(pose, vc, Mth.cos(a) * 0.39f, sy, Mth.sin(a) * 0.39f,
                        0.040f, ageTicks * 0.09f + i, orbUv, sparkUv, 110, 255, 235, 215);
            }
        }
    }

    private static void renderSolarFlareHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.030f;
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.28f, 0.019f, 24, spin, 0.06f, 255, 172, 32, 252);
            emitVolumetricTorus(pose, vc, 0.0f, centerY - 0.02f, 0.0f, 0.21f, 0.012f, 20, -spin, -0.06f, 255, 224, 96, 245);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.28f, 0.040f, 24, spin, 0.06f, 255, 162, 42, 92);
        }

        int rays = 12;
        for (int i = 0; i < rays; i++) {
            boolean major = (i % 2 == 0);
            float a = spin + (i * TAU) / rays;
            float rx = Mth.cos(a) * 0.28f;
            float rz = Mth.sin(a) * 0.28f;
            float ry = centerY + Mth.sin(a) * 0.28f * Mth.sin(0.06f);
            float outR = major ? 0.43f : 0.36f;
            float tx = Mth.cos(a) * outR;
            float tz = Mth.sin(a) * outR;
            float ty = ry - (major ? 0.045f : 0.020f);

            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT,
                        rx, ry, rz, major ? 0.016f : 0.011f,
                        tx, ty, tz, 0.002f,
                        255, major ? 215 : 148, major ? 76 : 28, 255);
            }
            if (renderGlow && major) {
                emitGlowBillboard(pose, vc, tx, ty, tz, 0.046f, ageTicks * 0.08f + i, orbUv, sparkUv, 255, 205, 78, 220);
            }
        }
    }

    private static void renderFrostDiademHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.022f;
        float diademY = centerY + 0.03f;
        float[] snowUv = HALO_AUX_UV_SCRATCH.get();
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SNOWFLAKE, snowUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, diademY, 0.0f, 0.25f, 0.017f, 20, spin, 0.0f, 155, 232, 255, 252);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, diademY, 0.0f, 0.25f, 0.036f, 20, spin, 0.0f, 175, 245, 255, 86);
        }

        int spires = 6;
        for (int i = 0; i < spires; i++) {
            float a = spin + (i * TAU) / spires;
            float bx = Mth.cos(a) * 0.25f;
            float bz = Mth.sin(a) * 0.25f;
            boolean tall = (i % 2 == 0);
            float h = tall ? 0.16f : 0.10f;
            float tx = Mth.cos(a) * 0.28f;
            float tz = Mth.sin(a) * 0.28f;

            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT,
                        bx, diademY, bz, tall ? 0.020f : 0.015f,
                        tx, diademY - h, tz, 0.002f,
                        tall ? 210 : 145, 246, 255, 255);
            }
            if (renderGlow) {
                emitGlowBillboard(pose, vc, tx, diademY - h - 0.03f, tz,
                        tall ? 0.052f : 0.038f, ageTicks * 0.05f + i, orbUv, snowUv, 195, 248, 255, 225);
            }
        }
    }

    private static void renderDemonHornsHalo(PoseStack.Pose pose, VertexConsumer vc, float floatY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float hornSpin = ageTicks * 0.035f;
        float ringY = -0.66f + floatY;
        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, ringY, 0.06f, 0.20f, 0.011f, 20, hornSpin, -0.18f, 220, 28, 72, 240);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, ringY, 0.06f, 0.20f, 0.025f, 20, hornSpin, -0.18f, 255, 58, 110, 95);
        }

        for (int s = -1; s <= 1; s += 2) {
            float sx = s;
            float p0x = sx * 0.20f, p0y = -0.44f + floatY, p0z = -0.14f;
            float p1x = sx * 0.29f, p1y = -0.57f + floatY, p1z = -0.09f;
            float p2x = sx * 0.34f, p2y = -0.71f + floatY, p2z = 0.01f;
            float p3x = sx * 0.30f, p3y = -0.84f + floatY, p3z = 0.13f;
            float p4x = sx * 0.23f, p4y = -0.92f + floatY, p4z = 0.21f;

            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, p0x, p0y, p0z, 0.042f, p1x, p1y, p1z, 0.033f, 52, 10, 20, 255);
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, p1x, p1y, p1z, 0.033f, p2x, p2y, p2z, 0.023f, 118, 16, 38, 255);
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, p2x, p2y, p2z, 0.023f, p3x, p3y, p3z, 0.013f, 208, 28, 72, 255);
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, p3x, p3y, p3z, 0.013f, p4x, p4y, p4z, 0.002f, 255, 76, 128, 255);
            }
            if (renderGlow) {
                float pulse = 0.055f + 0.014f * Mth.sin(ageTicks * 0.22f + s);
                emitGlowBillboard(pose, vc, p4x, p4y, p4z, pulse, ageTicks * 0.07f * sx, orbUv, sparkUv, 255, 68, 120, 235);
                emitGlowBillboard(pose, vc, p2x, p2y, p2z, 0.065f, 0.0f, orbUv, orbUv, 255, 42, 92, 115);
            }
        }
    }

    private static void renderCyberVisorHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.045f;
        float[] bladeUv = HALO_AUX_UV_SCRATCH.get();
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_HALO_BAND, bladeUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.31f, 0.014f, 6, spin, 0.14f, 0, 240, 255, 252);
            emitVolumetricTorus(pose, vc, 0.0f, centerY - 0.03f, 0.0f, 0.24f, 0.010f, 6, -spin * 1.2f, -0.10f, 220, 68, 255, 245);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.31f, 0.030f, 6, spin, 0.14f, 45, 248, 255, 92);
        }

        for (int i = 0; i < 4; i++) {
            float a = -spin + (i * TAU) / 4.0f;
            float rx = Mth.cos(a) * 0.37f;
            float rz = Mth.sin(a) * 0.37f;
            float ry = centerY + Mth.sin(a) * 0.05f;
            float tx = Mth.cos(a + 0.35f) * 0.37f;
            float tz = Mth.sin(a + 0.35f) * 0.37f;
            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT,
                        rx, ry, rz, 0.012f, tx, ry - 0.025f, tz, 0.004f,
                        (i % 2 == 0) ? 0 : 225, (i % 2 == 0) ? 245 : 75, 255, 255);
            }
            if (renderGlow) {
                emitGlowBillboard(pose, vc, rx, ry, rz, 0.044f, ageTicks * 0.09f + i, orbUv, sparkUv,
                        (i % 2 == 0) ? 80 : 235, (i % 2 == 0) ? 250 : 95, 255, 225);
            }
        }
    }

    private static void renderEclipseHalo(PoseStack.Pose pose, VertexConsumer vc, float centerY, float ageTicks, boolean renderSolid, boolean renderGlow) {
        float spin = ageTicks * 0.028f;
        float[] diamondUv = HALO_AUX_UV_SCRATCH.get();
        float[] orbUv = HALO_ORB_UV_SCRATCH.get();
        float[] sparkUv = HALO_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_DIAMOND, diamondUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        if (renderSolid) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.29f, 0.020f, 24, spin, 0.10f, 36, 18, 56, 255);
            emitVolumetricTorus(pose, vc, 0.0f, centerY - 0.02f, 0.0f, 0.33f, 0.010f, 24, -spin * 1.2f, -0.14f, 255, 215, 110, 248);
        }
        if (renderGlow) {
            emitVolumetricTorus(pose, vc, 0.0f, centerY, 0.0f, 0.29f, 0.040f, 24, spin, 0.10f, 185, 110, 255, 96);
            emitVolumetricTorus(pose, vc, 0.0f, centerY - 0.02f, 0.0f, 0.33f, 0.026f, 24, -spin * 1.2f, -0.14f, 255, 225, 130, 86);
        }

        for (int i = 0; i < 6; i++) {
            float a = spin * 1.5f + (i * TAU) / 6.0f;
            float rx = Mth.cos(a) * 0.37f;
            float rz = Mth.sin(a) * 0.37f;
            float ry = centerY - 0.02f + Mth.sin(ageTicks * 0.14f + i) * 0.032f;
            boolean gold = (i % 2 == 0);
            if (renderSolid) {
                CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, FULL_BRIGHT, rx, ry, rz,
                        0.048f, ageTicks * 0.06f + i, diamondUv,
                        gold ? 255 : 195, gold ? 218 : 118, gold ? 115 : 255, 245);
            }
            if (renderGlow) {
                emitGlowBillboard(pose, vc, rx, ry, rz, 0.046f, ageTicks * 0.08f + i, orbUv, sparkUv,
                        gold ? 255 : 205, gold ? 225 : 128, gold ? 135 : 255, 215);
            }
        }
    }

    static void emitGlowBillboard(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float x, float y, float z,
            float halfSize,
            float rollRad,
            float[] orbUv,
            float[] spriteUv,
            int r, int g, int b, int alpha
    ) {
        CosmeticGeometryHelper.emitCameraBillboardQuad(
                pose, vc, FULL_BRIGHT, x, y, z, halfSize * 1.65f, 0.0f,
                orbUv, r, g, b, Math.min(255, Math.round(alpha * 0.45f)));
        CosmeticGeometryHelper.emitCameraBillboardQuad(
                pose, vc, FULL_BRIGHT, x, y, z, halfSize, rollRad,
                spriteUv, Math.min(255, r + 40), Math.min(255, g + 40), Math.min(255, b + 40), alpha);
    }

    static void emitVolumetricTorus(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float cx, float cy, float cz,
            float majorR,
            float tubeR,
            int segments,
            float spinRad,
            float tiltRad,
            int r, int g, int b, int a
    ) {
        float prevTiltY = 0f;
        float prevOutX = 0f, prevOutZ = 0f, prevInX = 0f, prevInZ = 0f;
        for (int i = 0; i <= segments; i++) {
            float theta = spinRad + (i * TAU) / segments;
            float c = Mth.cos(theta);
            float s = Mth.sin(theta);
            float tiltY = cy + s * majorR * Mth.sin(tiltRad);

            float outR = majorR + tubeR;
            float inR = majorR - tubeR;
            float outX = cx + c * outR;
            float outZ = cz + s * outR;
            float inX = cx + c * inR;
            float inZ = cz + s * inR;

            if (i > 0) {
                CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_BRIGHT,
                        prevOutX, prevTiltY - tubeR, prevOutZ,
                        outX, tiltY - tubeR, outZ,
                        inX, tiltY - tubeR, inZ,
                        prevInX, prevTiltY - tubeR, prevInZ,
                        r, g, b, a);
                CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_BRIGHT,
                        prevInX, prevTiltY + tubeR, prevInZ,
                        inX, tiltY + tubeR, inZ,
                        outX, tiltY + tubeR, outZ,
                        prevOutX, prevTiltY + tubeR, prevOutZ,
                        Math.max(10, Math.round(r * 0.72f)), Math.max(10, Math.round(g * 0.72f)), Math.max(10, Math.round(b * 0.72f)), a);
                CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_BRIGHT,
                        prevOutX, prevTiltY + tubeR, prevOutZ,
                        outX, tiltY + tubeR, outZ,
                        outX, tiltY - tubeR, outZ,
                        prevOutX, prevTiltY - tubeR, prevOutZ,
                        Math.max(10, Math.round(r * 0.90f)), Math.max(10, Math.round(g * 0.90f)), Math.max(10, Math.round(b * 0.90f)), a);
                CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_BRIGHT,
                        prevInX, prevTiltY - tubeR, prevInZ,
                        inX, tiltY - tubeR, inZ,
                        inX, tiltY + tubeR, inZ,
                        prevInX, prevTiltY + tubeR, prevInZ,
                        Math.max(10, Math.round(r * 0.85f)), Math.max(10, Math.round(g * 0.85f)), Math.max(10, Math.round(b * 0.85f)), a);
            }
            prevTiltY = tiltY;
            prevOutX = outX;
            prevOutZ = outZ;
            prevInX = inX;
            prevInZ = inZ;
        }
    }
}
