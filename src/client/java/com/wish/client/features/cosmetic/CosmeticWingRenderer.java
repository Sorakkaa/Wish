package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

final class CosmeticWingRenderer {
    static final int FULL_BRIGHT = 15728880;
    private static final ThreadLocal<float[]> WING_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> WING_ACCENT_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> WING_ORB_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> WING_SPARK_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);

    private CosmeticWingRenderer() {
    }

    static void renderWings(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            int wingId,
            float baseFlap,
            float tipFold,
            float ageTicks
    ) {
        renderWingSide(pose, vc, light, wingId, baseFlap, tipFold, ageTicks, -1.0f, true, true);
        renderWingSide(pose, vc, light, wingId, baseFlap, tipFold, ageTicks, 1.0f, true, true);
    }

    static void renderWingsSolid(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            int wingId,
            float baseFlap,
            float tipFold,
            float ageTicks
    ) {
        renderWingSide(pose, vc, light, wingId, baseFlap, tipFold, ageTicks, -1.0f, true, false);
        renderWingSide(pose, vc, light, wingId, baseFlap, tipFold, ageTicks, 1.0f, true, false);
    }

    static void renderWingsGlow(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int wingId,
            float baseFlap,
            float tipFold,
            float ageTicks
    ) {
        renderWingSide(pose, vc, FULL_BRIGHT, wingId, baseFlap, tipFold, ageTicks, -1.0f, false, true);
        renderWingSide(pose, vc, FULL_BRIGHT, wingId, baseFlap, tipFold, ageTicks, 1.0f, false, true);
    }

    private static void renderWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            int wingId,
            float baseFlap,
            float tipFold,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        switch (wingId) {
            case 1 -> renderDemonWingSide(pose, vc, light, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            case 2 -> renderEclipseSeraphWingSide(pose, vc, light, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            case 3 -> renderDragonWingSide(pose, vc, light, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            case 4 -> renderButterflyWingSide(pose, vc, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            case 5 -> renderCyberWingSide(pose, vc, light, baseFlap, ageTicks, sideSign, renderSolid, renderGlow);
            case 6, 7, 8, 9, 10 -> CosmeticExtraWingRenderer.renderExtraWingSide(
                    pose, vc, light, wingId, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            default -> {}
        }
    }

    private static void renderDemonWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float baseFlap,
            float tipFold,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float[] uv = WING_UV_SCRATCH.get();
        float[] orbUv = WING_ORB_UV_SCRATCH.get();
        float[] sparkUv = WING_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_RIB_WING, uv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.075f, ry = 0.14f, rz = 0.130f;
        float sweep = 0.16f + baseFlap * 0.42f;

        float ex = rx + sideSign * Mth.cos(sweep) * 0.34f;
        float ey = ry - 0.24f - Mth.sin(sweep) * 0.03f;
        float ez = rz + Mth.sin(sweep) * 0.14f + 0.04f;

        float outerYaw = sweep + tipFold * 0.38f;
        float wx = ex + sideSign * Mth.cos(outerYaw) * 0.38f;
        float wy = ey - 0.14f + tipFold * 0.06f;
        float wz = ez + Mth.sin(outerYaw) * 0.15f + 0.04f;

        float f0x = wx + sideSign * 0.46f, f0y = wy + 0.28f, f0z = wz + 0.07f;
        float f1x = wx + sideSign * 0.22f, f1y = wy + 0.60f, f1z = wz + 0.10f;
        float f2x = ex + sideSign * 0.10f, f2y = ry + 0.46f, f2z = rz + 0.16f;
        float anchorX = rx + sideSign * 0.02f, anchorY = ry + 0.46f, anchorZ = rz + 0.03f;
        float clawX = wx + sideSign * 0.04f, clawY = wy - 0.13f, clawZ = wz - 0.02f;

        float m0x = fingerMidX(wx, f0x, sideSign * 0.03f), m0y = fingerMidY(wy, f0y, -0.04f), m0z = fingerMidZ(wz, f0z);
        float m1x = fingerMidX(wx, f1x, sideSign * 0.02f), m1y = fingerMidY(wy, f1y, -0.03f), m1z = fingerMidZ(wz, f1z);
        float m2x = fingerMidX(ex, f2x, sideSign * 0.01f), m2y = fingerMidY(ey, f2y, -0.02f), m2z = fingerMidZ(ez, f2z);
        float mAx = (rx + anchorX) * 0.5f, mAy = (ry + anchorY) * 0.5f, mAz = (rz + anchorZ) * 0.5f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry, rz, 0.024f, ex, ey, ez, 0.018f, 44, 10, 22, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, ex, ey, ez, 0.018f, wx, wy, wz, 0.016f, 58, 12, 26, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, wx, wy, wz, 0.014f, clawX, clawY, clawZ, 0.002f, 255, 58, 105, 255);

            emitCurvedFingerThroughMid(pose, vc, light, wx, wy, wz, 0.014f, m0x, m0y, m0z, f0x, f0y, f0z, 0.004f, 60, 12, 26);
            emitCurvedFingerThroughMid(pose, vc, light, wx, wy, wz, 0.012f, m1x, m1y, m1z, f1x, f1y, f1z, 0.004f, 56, 11, 24);
            emitCurvedFingerThroughMid(pose, vc, light, ex, ey, ez, 0.011f, m2x, m2y, m2z, f2x, f2y, f2z, 0.004f, 52, 10, 22);

            emitWatertightFanBay(pose, vc, light, uv,
                    wx, wy, wz,
                    m0x, m0y, m0z, f0x, f0y, f0z,
                    m1x, m1y, m1z, f1x, f1y, f1z,
                    0.06f, 0.022f, 206, 24, 68, 246);
            emitWatertightQuadBay(pose, vc, light, uv,
                    wx, wy, wz, ex, ey, ez,
                    m1x, m1y, m1z, f1x, f1y, f1z,
                    m2x, m2y, m2z, f2x, f2y, f2z,
                    0.08f, 0.028f, 184, 20, 58, 244);
            emitWatertightQuadBay(pose, vc, light, uv,
                    ex, ey, ez, rx, ry, rz,
                    m2x, m2y, m2z, f2x, f2y, f2z,
                    mAx, mAy, mAz, anchorX, anchorY, anchorZ,
                    0.07f, 0.024f, 158, 16, 48, 242);
        }

        if (renderGlow) {
            emitWatertightQuadBay(pose, vc, FULL_BRIGHT, uv,
                    wx, wy, wz + 0.012f, ex, ey, ez + 0.012f,
                    m1x, m1y, m1z + 0.012f, f1x, f1y, f1z + 0.012f,
                    m2x, m2y, m2z + 0.012f, f2x, f2y, f2z + 0.012f,
                    0.08f, 0.028f, 255, 42, 108, 72);
            float glowRoll = ageTicks * 0.05f * sideSign;
            emitWingGlowNode(pose, vc, clawX, clawY, clawZ, 0.068f, glowRoll, orbUv, sparkUv, 255, 58, 118, 225);
            emitWingGlowNode(pose, vc, f0x, f0y, f0z, 0.056f, -glowRoll, orbUv, sparkUv, 255, 48, 105, 205);
            emitWingGlowNode(pose, vc, f1x, f1y, f1z, 0.048f, glowRoll, orbUv, sparkUv, 240, 40, 96, 185);
            emitWingGlowNode(pose, vc, (ex + f1x) * 0.5f, (ey + f1y) * 0.5f, (ez + f1z) * 0.5f + 0.04f,
                    0.145f, 0.0f, orbUv, orbUv, 255, 36, 98, 92);
        }
    }

    private static void renderEclipseSeraphWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float baseFlap,
            float tipFold,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float[] bladeUv = WING_UV_SCRATCH.get();
        float[] diamondUv = WING_ACCENT_UV_SCRATCH.get();
        float[] orbUv = WING_ORB_UV_SCRATCH.get();
        float[] sparkUv = WING_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_HALO_BAND, bladeUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_DIAMOND, diamondUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.08f, ry = 0.14f, rz = 0.130f;
        float sweep = 0.15f + baseFlap * 0.24f;
        float ex = rx + sideSign * Mth.cos(sweep) * 0.28f;
        float ey = ry - 0.18f;
        float ez = rz + Mth.sin(sweep) * 0.11f + 0.03f;

        float outerYaw = sweep + tipFold * 0.26f;
        float wx = ex + sideSign * Mth.cos(outerYaw) * 0.34f;
        float wy = ey - 0.24f;
        float wz = ez + Mth.sin(outerYaw) * 0.13f + 0.03f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry, rz, 0.025f, ex, ey, ez, 0.019f, 32, 20, 48, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, ex, ey, ez, 0.019f, wx, wy, wz, 0.010f, 44, 26, 68, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, rx, ry + 0.018f, rz + 0.008f, 0.011f, ex, ey + 0.015f, ez + 0.008f, 0.009f, 255, 208, 92, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, ex, ey + 0.015f, ez + 0.008f, 0.009f, wx, wy - 0.05f, wz + 0.008f, 0.003f, 255, 224, 128, 255);
        }

        int vaneCount = 4;
        for (int v = 0; v < vaneCount; v++) {
            float u = (float) v / (vaneCount - 1);
            float bx = Mth.lerp(u, rx + sideSign * 0.06f, wx);
            float by = Mth.lerp(u, ry + 0.04f, wy - 0.04f);
            float bz = Mth.lerp(u, rz + 0.02f, wz + 0.02f);

            float pitch = -0.68f + u * 0.96f;
            float yaw = 0.14f + baseFlap * 0.22f;
            float len = 0.84f - u * 0.18f;

            float dirX = sideSign * Mth.cos(yaw) * Mth.cos(pitch);
            float dirY = Mth.sin(pitch);
            float dirZ = Mth.sin(yaw) * Mth.cos(pitch);

            float tx = bx + dirX * len;
            float ty = by + dirY * len;
            float tz = bz + dirZ * len;
            float halfW = 0.120f - u * 0.016f;
            float pxOff = -dirY * halfW * 0.26f;

            if (renderSolid) {
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, FULL_BRIGHT,
                        bx - pxOff, by - halfW, bz,
                        tx - pxOff * 0.18f, ty - halfW * 0.24f, tz,
                        tx + pxOff * 0.18f, ty + halfW * 0.24f, tz,
                        bx + pxOff, by + halfW, bz,
                        bladeUv, 134, 82, 255, 238);

                float goldW = halfW * 0.42f;
                float gxOff = -dirY * goldW * 0.26f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, FULL_BRIGHT,
                        bx - gxOff, by - goldW, bz + 0.007f,
                        tx - gxOff * 0.18f, ty - goldW * 0.22f, tz + 0.007f,
                        tx + gxOff * 0.18f, ty + goldW * 0.22f, tz + 0.007f,
                        bx + gxOff, by + goldW, bz + 0.007f,
                        bladeUv, 255, 215, 118, 248);
            }

            if (renderGlow) {
                float auraW = halfW * 1.65f;
                float axOff = -dirY * auraW * 0.26f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, FULL_BRIGHT,
                        bx - axOff, by - auraW, bz + 0.013f,
                        tx - axOff * 0.20f, ty - auraW * 0.28f, tz + 0.013f,
                        tx + axOff * 0.20f, ty + auraW * 0.28f, tz + 0.013f,
                        bx + axOff, by + auraW, bz + 0.013f,
                        bladeUv, 175, 115, 255, 76);
                emitWingGlowNode(pose, vc, tx, ty, tz, 0.052f, ageTicks * 0.06f + v, orbUv, sparkUv,
                        (v % 2 == 0) ? 255 : 185, (v % 2 == 0) ? 220 : 120, (v % 2 == 0) ? 130 : 255, 220);
            }
        }

        if (renderSolid) {
            CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, FULL_BRIGHT,
                    ex + sideSign * 0.02f, ey - 0.11f, ez + 0.02f,
                    0.065f, ageTicks * 0.05f * sideSign, diamondUv, 255, 218, 120, 245);
        }
        if (renderGlow) {
            emitWingGlowNode(pose, vc, ex + sideSign * 0.02f, ey - 0.11f, ez + 0.02f,
                    0.075f, -ageTicks * 0.04f, orbUv, sparkUv, 255, 220, 140, 210);
        }
    }

    private static void renderDragonWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float baseFlap,
            float tipFold,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float[] uv = WING_UV_SCRATCH.get();
        float[] orbUv = WING_ORB_UV_SCRATCH.get();
        float[] sparkUv = WING_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_RIB_WING, uv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.08f, ry = 0.13f, rz = 0.130f;
        float sweep = 0.18f + baseFlap * 0.44f;

        float ex = rx + sideSign * Mth.cos(sweep) * 0.36f;
        float ey = ry - 0.26f;
        float ez = rz + Mth.sin(sweep) * 0.15f + 0.04f;

        float outerYaw = sweep + tipFold * 0.42f;
        float wx = ex + sideSign * Mth.cos(outerYaw) * 0.44f;
        float wy = ey - 0.16f + tipFold * 0.07f;
        float wz = ez + Mth.sin(outerYaw) * 0.17f + 0.05f;

        float f0x = wx + sideSign * 0.50f, f0y = wy + 0.22f, f0z = wz + 0.08f;
        float f1x = wx + sideSign * 0.36f, f1y = wy + 0.54f, f1z = wz + 0.11f;
        float f2x = wx + sideSign * 0.12f, f2y = wy + 0.68f, f2z = wz + 0.13f;
        float f3x = ex + sideSign * 0.16f, f3y = ry + 0.50f, f3z = rz + 0.16f;
        float anchorX = rx + sideSign * 0.02f, anchorY = ry + 0.48f, anchorZ = rz + 0.03f;
        float hornX = wx + sideSign * 0.05f, hornY = wy - 0.18f, hornZ = wz - 0.02f;

        float m0x = fingerMidX(wx, f0x, sideSign * 0.03f), m0y = fingerMidY(wy, f0y, -0.04f), m0z = fingerMidZ(wz, f0z);
        float m1x = fingerMidX(wx, f1x, sideSign * 0.02f), m1y = fingerMidY(wy, f1y, -0.03f), m1z = fingerMidZ(wz, f1z);
        float m2x = fingerMidX(wx, f2x, sideSign * 0.01f), m2y = fingerMidY(wy, f2y, -0.02f), m2z = fingerMidZ(wz, f2z);
        float m3x = fingerMidX(ex, f3x, sideSign * 0.01f), m3y = fingerMidY(ey, f3y, -0.02f), m3z = fingerMidZ(ez, f3z);
        float mAx = (rx + anchorX) * 0.5f, mAy = (ry + anchorY) * 0.5f, mAz = (rz + anchorZ) * 0.5f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry, rz, 0.029f, ex, ey, ez, 0.022f, 38, 26, 40, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, ex, ey, ez, 0.022f, wx, wy, wz, 0.019f, 46, 30, 48, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, wx, wy, wz, 0.015f, hornX, hornY, hornZ, 0.002f, 255, 148, 48, 255);

            emitCurvedFingerThroughMid(pose, vc, light, wx, wy, wz, 0.015f, m0x, m0y, m0z, f0x, f0y, f0z, 0.004f, 48, 32, 52);
            emitCurvedFingerThroughMid(pose, vc, light, wx, wy, wz, 0.013f, m1x, m1y, m1z, f1x, f1y, f1z, 0.004f, 46, 30, 50);
            emitCurvedFingerThroughMid(pose, vc, light, wx, wy, wz, 0.012f, m2x, m2y, m2z, f2x, f2y, f2z, 0.004f, 44, 28, 48);
            emitCurvedFingerThroughMid(pose, vc, light, ex, ey, ez, 0.011f, m3x, m3y, m3z, f3x, f3y, f3z, 0.004f, 42, 26, 46);

            emitWatertightFanBay(pose, vc, light, uv,
                    wx, wy, wz,
                    m0x, m0y, m0z, f0x, f0y, f0z,
                    m1x, m1y, m1z, f1x, f1y, f1z,
                    0.06f, 0.022f, 232, 82, 24, 246);
            emitWatertightFanBay(pose, vc, light, uv,
                    wx, wy, wz,
                    m1x, m1y, m1z, f1x, f1y, f1z,
                    m2x, m2y, m2z, f2x, f2y, f2z,
                    0.06f, 0.024f, 212, 62, 22, 244);
            emitWatertightQuadBay(pose, vc, light, uv,
                    wx, wy, wz, ex, ey, ez,
                    m2x, m2y, m2z, f2x, f2y, f2z,
                    m3x, m3y, m3z, f3x, f3y, f3z,
                    0.08f, 0.028f, 190, 48, 22, 242);
            emitWatertightQuadBay(pose, vc, light, uv,
                    ex, ey, ez, rx, ry, rz,
                    m3x, m3y, m3z, f3x, f3y, f3z,
                    mAx, mAy, mAz, anchorX, anchorY, anchorZ,
                    0.07f, 0.024f, 166, 36, 20, 240);
        }

        if (renderGlow) {
            emitWatertightQuadBay(pose, vc, FULL_BRIGHT, uv,
                    wx, wy, wz + 0.012f, ex, ey, ez + 0.012f,
                    m2x, m2y, m2z + 0.012f, f2x, f2y, f2z + 0.012f,
                    m3x, m3y, m3z + 0.012f, f3x, f3y, f3z + 0.012f,
                    0.08f, 0.028f, 255, 118, 32, 74);
            float glowRoll = ageTicks * 0.06f * sideSign;
            emitWingGlowNode(pose, vc, hornX, hornY, hornZ, 0.072f, glowRoll, orbUv, sparkUv, 255, 168, 52, 235);
            emitWingGlowNode(pose, vc, f0x, f0y, f0z, 0.058f, -glowRoll, orbUv, sparkUv, 255, 128, 42, 215);
            emitWingGlowNode(pose, vc, f1x, f1y, f1z, 0.050f, glowRoll, orbUv, sparkUv, 255, 108, 36, 195);
            emitWingGlowNode(pose, vc, f2x, f2y, f2z, 0.044f, -glowRoll, orbUv, sparkUv, 255, 95, 32, 180);
            emitWingGlowNode(pose, vc, (ex + f1x) * 0.5f, (ey + f1y) * 0.5f, (ez + f1z) * 0.5f + 0.05f,
                    0.155f, 0.0f, orbUv, orbUv, 255, 110, 32, 95);
        }
    }

    private static void renderButterflyWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float baseFlap,
            float tipFold,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float[] uv = WING_UV_SCRATCH.get();
        float[] orbUv = WING_ORB_UV_SCRATCH.get();
        float[] sparkUv = WING_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ENERGY_WING, uv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.065f, ry = 0.16f, rz = 0.130f;
        float yawUpper = 0.12f + baseFlap * 0.30f + tipFold * 0.12f;
        float yawLower = 0.10f + baseFlap * 0.28f;

        float ux0 = rx + sideSign * Mth.cos(yawUpper) * 1.08f;
        float uy0 = ry - 0.54f;
        float uz0 = rz + Mth.sin(yawUpper) * 0.24f;

        float ux1 = rx + sideSign * Mth.cos(yawUpper) * 0.98f;
        float uy1 = ry + 0.22f;
        float uz1 = rz + Mth.sin(yawUpper) * 0.22f;

        float lx0 = rx + sideSign * Mth.cos(yawLower) * 0.84f;
        float ly0 = ry + 0.12f;
        float lz0 = rz + Mth.sin(yawLower) * 0.20f + 0.02f;

        float lx1 = rx + sideSign * Mth.cos(yawLower) * 0.72f;
        float ly1 = ry + 0.74f;
        float lz1 = rz + Mth.sin(yawLower) * 0.18f + 0.02f;

        if (renderSolid) {
            emitSubdividedMembraneBay(pose, vc, FULL_BRIGHT, uv,
                    rx, ry - 0.10f, rz, ux0, uy0, uz0,
                    rx, ry + 0.24f, rz + 0.02f, ux1, uy1, uz1,
                    0.0f, 0.025f, 95, 225, 255, 245);

            emitSubdividedMembraneBay(pose, vc, FULL_BRIGHT, uv,
                    rx, ry + 0.16f, rz + 0.02f, lx0, ly0, lz0,
                    rx + sideSign * 0.02f, ry + 0.56f, rz + 0.03f, lx1, ly1, lz1,
                    0.0f, 0.020f, 245, 95, 195, 240);
        }

        if (renderGlow) {
            emitSubdividedMembraneBay(pose, vc, FULL_BRIGHT, uv,
                    rx, ry - 0.06f, rz + 0.002f,
                    Mth.lerp(0.74f, rx, ux0), Mth.lerp(0.74f, ry - 0.06f, uy0), Mth.lerp(0.74f, rz, uz0) + 0.002f,
                    rx, ry + 0.20f, rz + 0.018f,
                    Mth.lerp(0.74f, rx, ux1), Mth.lerp(0.74f, ry + 0.20f, uy1), Mth.lerp(0.74f, rz, uz1) + 0.018f,
                    0.0f, 0.025f, 215, 130, 255, 155);

            emitSubdividedMembraneBay(pose, vc, FULL_BRIGHT, uv,
                    rx, ry - 0.16f, rz + 0.020f, ux0 + sideSign * 0.12f, uy0 - 0.06f, uz0 + 0.020f,
                    rx, ry + 0.28f, rz + 0.020f, ux1 + sideSign * 0.10f, uy1 + 0.04f, uz1 + 0.020f,
                    0.0f, 0.028f, 125, 245, 255, 68);
            emitSubdividedMembraneBay(pose, vc, FULL_BRIGHT, uv,
                    rx, ry + 0.12f, rz + 0.022f, lx0 + sideSign * 0.10f, ly0, lz0 + 0.022f,
                    rx + sideSign * 0.02f, ry + 0.62f, rz + 0.022f, lx1 + sideSign * 0.08f, ly1 + 0.05f, lz1 + 0.022f,
                    0.0f, 0.024f, 255, 130, 225, 65);
            for (int i = 0; i < 6; i++) {
                float t = (float) i / 5.0f;
                float fx = Mth.lerp(t, (ux0 + ux1) * 0.52f, (lx0 + lx1) * 0.52f) + sideSign * 0.08f;
                float fy = Mth.lerp(t, uy0 + 0.05f, ly1 - 0.05f) + Mth.sin(ageTicks * 0.15f + i * 1.3f) * 0.045f;
                float fz = Mth.lerp(t, uz0, lz1) + 0.05f;
                float pulse = 0.045f + 0.016f * Mth.sin(ageTicks * 0.21f + i);
                boolean aqua = (i % 2 == 0);
                emitWingGlowNode(pose, vc, fx, fy, fz, pulse, ageTicks * 0.07f + i, orbUv, sparkUv,
                        aqua ? 125 : 255, aqua ? 245 : 145, aqua ? 255 : 225, 220);
            }
        }
    }

    private static void renderCyberWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float baseFlap,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float[] uv = WING_UV_SCRATCH.get();
        float[] orbUv = WING_ORB_UV_SCRATCH.get();
        float[] sparkUv = WING_SPARK_UV_SCRATCH.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_HALO_BAND, uv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.085f, ry = 0.12f, rz = 0.132f;
        float px = rx + sideSign * 0.09f, py = ry + 0.18f, pz = rz + 0.05f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry - 0.03f, rz, 0.026f, px, py, pz, 0.020f, 32, 36, 48, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, FULL_BRIGHT, rx + sideSign * 0.02f, ry, rz + 0.015f, 0.014f, px + sideSign * 0.01f, py - 0.02f, pz + 0.018f, 0.010f, 0, 240, 215, 255);
        }
        if (renderGlow) {
            emitWingGlowNode(pose, vc, (rx + px) * 0.5f, (ry + py) * 0.5f, pz + 0.03f, 0.072f, ageTicks * 0.06f, orbUv, sparkUv, 95, 255, 240, 220);
        }

        int bladeCount = 6;
        for (int b = 0; b < bladeCount; b++) {
            float u = (float) b / (bladeCount - 1);
            float pulse = Mth.sin(ageTicks * 0.18f - b * 0.55f) * 0.030f;
            float bx = Mth.lerp(u, rx, px) + sideSign * (0.06f + u * 0.02f);
            float by = Mth.lerp(u, ry - 0.04f, py + 0.10f) + (u - 0.4f) * 0.12f;
            float bz = Mth.lerp(u, rz + 0.03f, pz + 0.02f) + 0.02f;

            float pitch = -0.58f + u * 1.14f;
            float yaw = 0.14f + baseFlap * 0.24f + pulse;
            float len = 0.78f - Math.abs(u - 0.32f) * 0.40f;

            float dirX = sideSign * Mth.cos(yaw) * Mth.cos(pitch);
            float dirY = Mth.sin(pitch);
            float dirZ = Mth.sin(yaw) * Mth.cos(pitch);

            float tx = bx + dirX * len;
            float ty = by + dirY * len;
            float tz = bz + dirZ * len;

            float halfW = 0.088f - u * 0.014f;
            if (renderSolid) {
                float pxOff = -dirY * halfW * 0.25f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, FULL_BRIGHT,
                        bx - pxOff, by - halfW, bz,
                        tx - pxOff * 0.20f, ty - halfW * 0.28f, tz,
                        tx + pxOff * 0.20f, ty + halfW * 0.28f, tz,
                        bx + pxOff, by + halfW, bz,
                        uv, 200, 62, 255, 232);

                float coreW = halfW * 0.48f;
                float cxOff = -dirY * coreW * 0.25f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, FULL_BRIGHT,
                        bx - cxOff, by - coreW, bz + 0.008f,
                        tx - cxOff * 0.18f, ty - coreW * 0.22f, tz + 0.008f,
                        tx + cxOff * 0.18f, ty + coreW * 0.22f, tz + 0.008f,
                        bx + cxOff, by + coreW, bz + 0.008f,
                        uv, 155, 255, 250, 252);
            }

            if (renderGlow) {
                float auraW = halfW * 1.75f;
                float axOff = -dirY * auraW * 0.25f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, FULL_BRIGHT,
                        bx - axOff, by - auraW, bz + 0.012f,
                        tx - axOff * 0.22f, ty - auraW * 0.30f, tz + 0.012f,
                        tx + axOff * 0.22f, ty + auraW * 0.30f, tz + 0.012f,
                        bx + axOff, by + auraW, bz + 0.012f,
                        uv, 210, 88, 255, 72);
                emitWingGlowNode(pose, vc, tx, ty, tz, 0.048f, ageTicks * 0.08f + b, orbUv, sparkUv,
                        (b % 2 == 0) ? 145 : 225, (b % 2 == 0) ? 255 : 120, 255, 210);
            }
        }
    }

    static float fingerMidX(float ax, float bx, float archOffsetX) {
        return (ax + bx) * 0.5f + archOffsetX;
    }

    static float fingerMidY(float ay, float by, float archOffsetY) {
        return (ay + by) * 0.5f + archOffsetY;
    }

    static float fingerMidZ(float az, float bz) {
        return (az + bz) * 0.5f - 0.010f;
    }

    static void emitCurvedFingerThroughMid(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float ax, float ay, float az, float r0,
            float mx, float my, float mz,
            float bx, float by, float bz, float r1,
            int r, int g, int b
    ) {
        float rMid = (r0 + r1) * 0.52f;
        CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, ax, ay, az, r0, mx, my, mz, rMid, r, g, b, 255);
        CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, mx, my, mz, rMid, bx, by, bz, r1,
                Math.max(10, r - 10), Math.max(8, g - 6), Math.max(8, b - 8), 255);
    }

    static void emitWatertightFanBay(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float[] uv,
            float rootX, float rootY, float rootZ,
            float midAx, float midAy, float midAz,
            float tipAx, float tipAy, float tipAz,
            float midBx, float midBy, float midBz,
            float tipBx, float tipBy, float tipBz,
            float archLiftY,
            float bulgeZ,
            int r, int g, int b, int a
    ) {
        float tAx = Mth.lerp(0.015f, rootX, midAx);
        float tAy = Mth.lerp(0.015f, rootY, midAy);
        float tAz = Mth.lerp(0.015f, rootZ, midAz);
        float tBx = Mth.lerp(0.015f, rootX, midBx);
        float tBy = Mth.lerp(0.015f, rootY, midBy);
        float tBz = Mth.lerp(0.015f, rootZ, midBz);
        emitWatertightQuadBay(pose, vc, light, uv,
                tAx, tAy, tAz, tBx, tBy, tBz,
                midAx, midAy, midAz, tipAx, tipAy, tipAz,
                midBx, midBy, midBz, tipBx, tipBy, tipBz,
                archLiftY, bulgeZ, r, g, b, a);
    }

    static void emitWatertightQuadBay(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float[] uv,
            float topAx, float topAy, float topAz,
            float topBx, float topBy, float topBz,
            float midAx, float midAy, float midAz,
            float tipAx, float tipAy, float tipAz,
            float midBx, float midBy, float midBz,
            float tipBx, float tipBy, float tipBz,
            float archLiftY,
            float bulgeZ,
            int r, int g, int b, int a
    ) {
        int cols = 4;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float vMid = (v0 + v1) * 0.5f;

        for (int c = 0; c < cols; c++) {
            float k0 = (float) c / cols;
            float k1 = (float) (c + 1) / cols;
            float arc0 = Mth.sin(k0 * (float) Math.PI);
            float arc1 = Mth.sin(k1 * (float) Math.PI);

            float top0x = Mth.lerp(k0, topAx, topBx);
            float top0y = Mth.lerp(k0, topAy, topBy);
            float top0z = Mth.lerp(k0, topAz, topBz);
            float top1x = Mth.lerp(k1, topAx, topBx);
            float top1y = Mth.lerp(k1, topAy, topBy);
            float top1z = Mth.lerp(k1, topAz, topBz);

            float mid0x = Mth.lerp(k0, midAx, midBx);
            float mid0y = Mth.lerp(k0, midAy, midBy);
            float mid0z = Mth.lerp(k0, midAz, midBz) + arc0 * bulgeZ;
            float mid1x = Mth.lerp(k1, midAx, midBx);
            float mid1y = Mth.lerp(k1, midAy, midBy);
            float mid1z = Mth.lerp(k1, midAz, midBz) + arc1 * bulgeZ;

            float bot0x = Mth.lerp(k0, tipAx, tipBx);
            float bot0y = Mth.lerp(k0, tipAy, tipBy) - arc0 * archLiftY;
            float bot0z = Mth.lerp(k0, tipAz, tipBz) + arc0 * bulgeZ * 0.6f;
            float bot1x = Mth.lerp(k1, tipAx, tipBx);
            float bot1y = Mth.lerp(k1, tipAy, tipBy) - arc1 * archLiftY;
            float bot1z = Mth.lerp(k1, tipAz, tipBz) + arc1 * bulgeZ * 0.6f;

            float su0 = Mth.lerp(k0, u0, u1);
            float su1 = Mth.lerp(k1, u0, u1);

            CosmeticGeometryHelper.emitQuadDoubleSidedUv(pose, vc, light,
                    top0x, top0y, top0z, top1x, top1y, top1z,
                    mid1x, mid1y, mid1z, mid0x, mid0y, mid0z,
                    su0, v0, su1, vMid, r, g, b, a);
            CosmeticGeometryHelper.emitQuadDoubleSidedUv(pose, vc, light,
                    mid0x, mid0y, mid0z, mid1x, mid1y, mid1z,
                    bot1x, bot1y, bot1z, bot0x, bot0y, bot0z,
                    su0, vMid, su1, v1, r, g, b, a);
        }
    }

    static void emitSubdividedMembraneBay(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float[] uv,
            float t0x, float t0y, float t0z,
            float t1x, float t1y, float t1z,
            float b0x, float b0y, float b0z,
            float b1x, float b1y, float b1z,
            float archLiftY,
            float bulgeZ,
            int r, int g, int b, int a
    ) {
        int strips = 4;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        for (int s = 0; s < strips; s++) {
            float k0 = (float) s / strips;
            float k1 = (float) (s + 1) / strips;
            float arc0 = Mth.sin(k0 * (float) Math.PI);
            float arc1 = Mth.sin(k1 * (float) Math.PI);

            float top0x = Mth.lerp(k0, t0x, t1x), top0y = Mth.lerp(k0, t0y, t1y), top0z = Mth.lerp(k0, t0z, t1z) + arc0 * bulgeZ;
            float top1x = Mth.lerp(k1, t0x, t1x), top1y = Mth.lerp(k1, t0y, t1y), top1z = Mth.lerp(k1, t0z, t1z) + arc1 * bulgeZ;

            float bot0x = Mth.lerp(k0, b0x, b1x), bot0y = Mth.lerp(k0, b0y, b1y) - arc0 * archLiftY, bot0z = Mth.lerp(k0, b0z, b1z) + arc0 * bulgeZ * 1.25f;
            float bot1x = Mth.lerp(k1, b0x, b1x), bot1y = Mth.lerp(k1, b0y, b1y) - arc1 * archLiftY, bot1z = Mth.lerp(k1, b0z, b1z) + arc1 * bulgeZ * 1.25f;

            float su0 = Mth.lerp(k0, u0, u1);
            float su1 = Mth.lerp(k1, u0, u1);

            CosmeticGeometryHelper.emitQuadDoubleSidedUv(pose, vc, light,
                    top0x, top0y, top0z,
                    top1x, top1y, top1z,
                    bot1x, bot1y, bot1z,
                    bot0x, bot0y, bot0z,
                    su0, v0, su1, v1, r, g, b, a);
        }
    }

    static void emitWingGlowNode(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float x, float y, float z,
            float halfSize,
            float rollRad,
            float[] orbUv,
            float[] sparkUv,
            int r, int g, int b, int alpha
    ) {
        CosmeticGeometryHelper.emitCameraBillboardQuad(
                pose, vc, FULL_BRIGHT, x, y, z, halfSize * 1.75f, 0.0f,
                orbUv, r, g, b, Math.min(255, Math.round(alpha * 0.44f)));
        CosmeticGeometryHelper.emitCameraBillboardQuad(
                pose, vc, FULL_BRIGHT, x, y, z, halfSize, rollRad,
                sparkUv, Math.min(255, r + 40), Math.min(255, g + 40), Math.min(255, b + 40), alpha);
    }
}
