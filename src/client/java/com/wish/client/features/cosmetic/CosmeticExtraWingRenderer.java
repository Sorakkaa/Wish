package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

final class CosmeticExtraWingRenderer {
    private static final ThreadLocal<float[]> EXTRA_UV_A = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> EXTRA_UV_B = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> EXTRA_ORB_UV = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> EXTRA_SPARK_UV = ThreadLocal.withInitial(() -> new float[4]);

    private CosmeticExtraWingRenderer() {
    }

    static void renderExtraWingSide(
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
            case 6 -> renderFrostCrystalWingSide(pose, vc, light, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            case 7 -> renderPhoenixFlameWingSide(pose, vc, light, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            case 8 -> renderVoidRiftWingSide(pose, vc, light, baseFlap, ageTicks, sideSign, renderSolid, renderGlow);
            case 9 -> renderSakuraSpiritWingSide(pose, vc, light, baseFlap, tipFold, ageTicks, sideSign, renderSolid, renderGlow);
            case 10 -> renderMechaValkyrieWingSide(pose, vc, light, baseFlap, ageTicks, sideSign, renderSolid, renderGlow);
            default -> {}
        }
    }

    private static void renderFrostCrystalWingSide(
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
        float[] bladeUv = EXTRA_UV_A.get();
        float[] snowUv = EXTRA_UV_B.get();
        float[] orbUv = EXTRA_ORB_UV.get();
        float[] sparkUv = EXTRA_SPARK_UV.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_HALO_BAND, bladeUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SNOWFLAKE, snowUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.08f, ry = 0.13f, rz = 0.130f;
        float sweep = 0.15f + baseFlap * 0.25f;
        float ex = rx + sideSign * Mth.cos(sweep) * 0.30f;
        float ey = ry - 0.18f;
        float ez = rz + Mth.sin(sweep) * 0.12f + 0.03f;

        float outerYaw = sweep + tipFold * 0.25f;
        float wx = ex + sideSign * Mth.cos(outerYaw) * 0.32f;
        float wy = ey - 0.12f;
        float wz = ez + Mth.sin(outerYaw) * 0.12f + 0.03f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, rx, ry, rz, 0.022f, ex, ey, ez, 0.016f, 165, 228, 255, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, ex, ey, ez, 0.016f, wx, wy, wz, 0.010f, 215, 248, 255, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, wx, wy, wz, 0.012f,
                    wx + sideSign * 0.08f, wy - 0.15f, wz - 0.01f, 0.002f, 225, 252, 255, 255);
        }

        int shards = 7;
        for (int s = 0; s < shards; s++) {
            float u = (float) s / (shards - 1);
            float bx = (u < 0.45f) ? Mth.lerp(u / 0.45f, rx + sideSign * 0.04f, ex) : Mth.lerp((u - 0.45f) / 0.55f, ex, wx);
            float by = (u < 0.45f) ? Mth.lerp(u / 0.45f, ry, ey) : Mth.lerp((u - 0.45f) / 0.55f, ey, wy);
            float bz = (u < 0.45f) ? Mth.lerp(u / 0.45f, rz, ez) : Mth.lerp((u - 0.45f) / 0.55f, ez, wz);

            float pitch = 0.65f - u * 1.18f;
            float yaw = 0.16f + baseFlap * 0.24f;
            float len = 0.62f + Mth.sin(u * (float) Math.PI * 0.85f) * 0.28f;

            float dirX = sideSign * Mth.cos(yaw) * Mth.cos(pitch);
            float dirY = Mth.sin(pitch);
            float dirZ = Mth.sin(yaw) * Mth.cos(pitch);

            float tx = bx + dirX * len;
            float ty = by + dirY * len;
            float tz = bz + dirZ * len;
            float halfW = 0.085f - Math.abs(u - 0.5f) * 0.020f;
            float pxOff = -dirY * halfW * 0.25f;

            if (renderSolid) {
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        bx - pxOff, by - halfW, bz,
                        tx - pxOff * 0.16f, ty - halfW * 0.16f, tz,
                        tx + pxOff * 0.16f, ty + halfW * 0.16f, tz,
                        bx + pxOff, by + halfW, bz,
                        bladeUv, 105, 222, 255, 232);

                float coreW = halfW * 0.45f;
                float cxOff = -dirY * coreW * 0.25f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        bx - cxOff, by - coreW, bz + 0.007f,
                        tx - cxOff * 0.16f, ty - coreW * 0.16f, tz + 0.007f,
                        tx + cxOff * 0.16f, ty + coreW * 0.16f, tz + 0.007f,
                        bx + cxOff, by + coreW, bz + 0.007f,
                        bladeUv, 225, 252, 255, 250);
            }

            if (renderGlow) {
                float auraW = halfW * 1.65f;
                float axOff = -dirY * auraW * 0.25f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        bx - axOff, by - auraW, bz + 0.012f,
                        tx - axOff * 0.18f, ty - auraW * 0.22f, tz + 0.012f,
                        tx + axOff * 0.18f, ty + auraW * 0.22f, tz + 0.012f,
                        bx + axOff, by + auraW, bz + 0.012f,
                        bladeUv, 145, 245, 255, 68);
                if (s % 2 == 0) {
                    CosmeticWingRenderer.emitWingGlowNode(pose, vc, tx, ty, tz, 0.052f,
                            ageTicks * 0.06f + s, orbUv, snowUv, 185, 248, 255, 225);
                }
            }
        }

        if (renderGlow) {
            CosmeticWingRenderer.emitWingGlowNode(pose, vc, (ex + wx) * 0.5f, ey + 0.05f, ez + 0.08f,
                    0.065f, -ageTicks * 0.04f, orbUv, sparkUv, 200, 250, 255, 195);
        }
    }

    private static void renderPhoenixFlameWingSide(
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
        float[] plumeUv = EXTRA_UV_A.get();
        float[] orbUv = EXTRA_ORB_UV.get();
        float[] sparkUv = EXTRA_SPARK_UV.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_FEATHER_WING, plumeUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.075f, ry = 0.13f, rz = 0.130f;
        float sweep = 0.16f + baseFlap * 0.42f;
        float ex = rx + sideSign * Mth.cos(sweep) * 0.34f;
        float ey = ry - 0.24f;
        float ez = rz + Mth.sin(sweep) * 0.14f + 0.04f;

        float outerYaw = sweep + tipFold * 0.36f;
        float wx = ex + sideSign * Mth.cos(outerYaw) * 0.38f;
        float wy = ey - 0.10f + tipFold * 0.06f;
        float wz = ez + Mth.sin(outerYaw) * 0.15f + 0.04f;

        float tipX = wx + sideSign * Mth.cos(outerYaw + 0.14f) * 0.30f;
        float tipY = wy + 0.16f;
        float tipZ = wz + Mth.sin(outerYaw + 0.14f) * 0.10f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry, rz, 0.024f, ex, ey, ez, 0.018f, 92, 22, 12, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, ex, ey, ez, 0.018f, wx, wy, wz, 0.013f, 255, 138, 28, 255);

            emitFlamePlumeTier(pose, vc, plumeUv, rx, ry, rz, ex, ey, ez, wx, wy, wz, tipX, tipY, tipZ,
                    sideSign, 9, 0.05f, 0.98f, 0.48f, 0.72f, 0.155f, 0.022f, 255, 68, 20, 246);
            emitFlamePlumeTier(pose, vc, plumeUv, rx, ry - 0.02f, rz - 0.016f, ex, ey - 0.02f, ez - 0.016f,
                    wx, wy - 0.02f, wz - 0.016f, tipX, tipY - 0.02f, tipZ - 0.016f,
                    sideSign, 7, 0.02f, 0.85f, 0.32f, 0.48f, 0.142f, 0.012f, 255, 148, 28, 248);
            emitFlamePlumeTier(pose, vc, plumeUv, rx, ry - 0.03f, rz - 0.032f, ex, ey - 0.03f, ez - 0.032f,
                    wx, wy - 0.03f, wz - 0.032f, tipX, tipY - 0.03f, tipZ - 0.032f,
                    sideSign, 5, 0.0f, 0.65f, 0.20f, 0.30f, 0.130f, 0.0f, 255, 228, 86, 250);
        }

        if (renderGlow) {
            emitFlamePlumeTier(pose, vc, plumeUv, rx, ry, rz + 0.016f, ex, ey, ez + 0.016f,
                    wx, wy, wz + 0.016f, tipX, tipY, tipZ + 0.016f,
                    sideSign, 6, 0.08f, 0.98f, 0.54f, 0.78f, 0.172f, 0.024f, 255, 165, 45, 78);
            for (int i = 0; i < 6; i++) {
                float t = (float) i / 5.0f;
                float sx = Mth.lerp(t, ex + sideSign * 0.06f, tipX + sideSign * 0.12f);
                float sy = Mth.lerp(t, ey + 0.10f, tipY + 0.26f) + Mth.sin(ageTicks * 0.18f + i * 1.4f) * 0.045f;
                float sz = Mth.lerp(t, ez, tipZ) + 0.09f;
                float pulse = 0.048f + 0.016f * Mth.sin(ageTicks * 0.24f + i);
                CosmeticWingRenderer.emitWingGlowNode(pose, vc, sx, sy, sz, pulse,
                        ageTicks * 0.08f + i, orbUv, sparkUv, 255, (i % 2 == 0) ? 215 : 118, 42, 225);
            }
        }
    }

    private static void emitFlamePlumeTier(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float[] uv,
            float p0x, float p0y, float p0z,
            float p1x, float p1y, float p1z,
            float p2x, float p2y, float p2z,
            float p3x, float p3y, float p3z,
            float sideSign,
            int count,
            float tMin,
            float tMax,
            float minLen,
            float maxLen,
            float plumeWidth,
            float extraBackZ,
            int r, int g, int b, int a
    ) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float vMid = (v0 + v1) * 0.5f;
        for (int i = 0; i < count; i++) {
            float u = (count == 1) ? 0.5f : (float) i / (count - 1);
            float t = tMin + u * (tMax - tMin);

            float ax, ay, az;
            if (t < 0.33f) {
                float k = t / 0.33f;
                ax = Mth.lerp(k, p0x, p1x);
                ay = Mth.lerp(k, p0y, p1y);
                az = Mth.lerp(k, p0z, p1z);
            } else if (t < 0.68f) {
                float k = (t - 0.33f) / 0.35f;
                ax = Mth.lerp(k, p1x, p2x);
                ay = Mth.lerp(k, p1y, p2y);
                az = Mth.lerp(k, p1z, p2z);
            } else {
                float k = (t - 0.68f) / 0.32f;
                ax = Mth.lerp(k, p2x, p3x);
                ay = Mth.lerp(k, p2y, p3y);
                az = Mth.lerp(k, p2z, p3z);
            }
            az += (i % 2 == 0 ? 0.004f : -0.004f);

            float lenFactor = 0.74f + 0.26f * Mth.sin(u * (float) Math.PI * 0.85f);
            float fLen = Mth.lerp(u, minLen, maxLen) * lenFactor;
            float fanAngle = -0.04f + u * 0.88f;
            float dirX = sideSign * Mth.sin(fanAngle) * 0.76f;
            float dirY = Mth.cos(fanAngle) * 0.86f;
            float dirZ = 0.12f + extraBackZ * 1.4f + u * 0.06f;

            float mx = ax + dirX * fLen * 0.52f;
            float my = ay + dirY * fLen * 0.50f;
            float mz = az + dirZ * fLen * 0.42f - 0.014f;

            float tx = ax + dirX * fLen;
            float ty = ay + dirY * fLen;
            float tz = az + dirZ * fLen;

            float halfW = plumeWidth * 0.5f;
            float sidePerpX = sideSign * Mth.cos(fanAngle) * halfW;
            float sidePerpY = -Mth.sin(fanAngle) * halfW * 0.45f;
            float twistZ = halfW * 0.20f;

            CosmeticGeometryHelper.emitQuadDoubleSidedUv(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                    ax - sidePerpX, ay - sidePerpY, az - twistZ,
                    ax + sidePerpX, ay + sidePerpY, az + twistZ,
                    mx + sidePerpX * 0.96f, my + sidePerpY * 0.96f, mz + twistZ,
                    mx - sidePerpX * 0.96f, my - sidePerpY * 0.96f, mz - twistZ,
                    u0, v0, u1, vMid, r, g, b, a);
            CosmeticGeometryHelper.emitQuadDoubleSidedUv(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                    mx - sidePerpX * 0.96f, my - sidePerpY * 0.96f, mz - twistZ,
                    mx + sidePerpX * 0.96f, my + sidePerpY * 0.96f, mz + twistZ,
                    tx + sidePerpX * 0.82f, ty + sidePerpY * 0.82f, tz + twistZ,
                    tx - sidePerpX * 0.82f, ty - sidePerpY * 0.82f, tz - twistZ,
                    u0, vMid, u1, v1, r, Math.min(255, g + 35), b, a);
        }
    }

    private static void renderVoidRiftWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float baseFlap,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float[] bladeUv = EXTRA_UV_A.get();
        float[] diamondUv = EXTRA_UV_B.get();
        float[] orbUv = EXTRA_ORB_UV.get();
        float[] sparkUv = EXTRA_SPARK_UV.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_HALO_BAND, bladeUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_DIAMOND, diamondUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.085f, ry = 0.14f, rz = 0.130f;
        float bx = rx + sideSign * 0.24f, by = ry - 0.12f, bz = rz + 0.10f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry, rz, 0.022f, bx, by, bz, 0.015f, 32, 12, 54, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, bx, by, bz, 0.012f,
                    bx + sideSign * 0.06f, by - 0.14f, bz + 0.02f, 0.002f, 185, 92, 255, 255);
        }

        int plates = 5;
        float prevPx = bx, prevPy = by, prevPz = bz;
        for (int p = 0; p < plates; p++) {
            float u = (float) p / (plates - 1);
            float hover = Mth.sin(ageTicks * 0.14f + p * 1.15f) * 0.045f;
            float angle = -0.62f + u * 1.24f;
            float dist = 0.34f + Mth.sin(u * (float) Math.PI) * 0.24f;

            float cx = bx + sideSign * Mth.cos(angle) * dist + baseFlap * sideSign * 0.10f;
            float cy = by + Mth.sin(angle) * dist + hover;
            float cz = bz + 0.05f + u * 0.06f;

            float dirX = sideSign * Mth.cos(angle);
            float dirY = Mth.sin(angle);
            float halfLen = 0.24f - u * 0.02f;
            float halfW = 0.115f - Math.abs(u - 0.5f) * 0.025f;

            float ax = cx - dirX * halfLen * 0.55f;
            float ay = cy - dirY * halfLen * 0.55f;
            float tx = cx + dirX * halfLen;
            float ty = cy + dirY * halfLen;
            float pxOff = -dirY * halfW * 0.30f;

            if (renderSolid) {
                CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        prevPx, prevPy, prevPz, 0.006f, cx, cy, cz, 0.006f, 155, 65, 245, 235);
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        ax - pxOff, ay - halfW, cz,
                        tx - pxOff * 0.25f, ty - halfW * 0.25f, cz + 0.02f,
                        tx + pxOff * 0.25f, ty + halfW * 0.25f, cz + 0.02f,
                        ax + pxOff, ay + halfW, cz,
                        bladeUv, 95, 28, 175, 238);
                CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        cx, cy, cz + 0.02f, 0.065f, ageTicks * 0.05f + p, diamondUv, 215, 138, 255, 245);
            }
            if (renderGlow) {
                CosmeticWingRenderer.emitWingGlowNode(pose, vc, cx, cy, cz + 0.03f, 0.072f,
                        ageTicks * 0.07f + p, orbUv, sparkUv, 195, 105, 255, 215);
            }
            prevPx = cx;
            prevPy = cy;
            prevPz = cz;
        }
        if (renderGlow) {
            CosmeticWingRenderer.emitWingGlowNode(pose, vc, bx + sideSign * 0.14f, by + 0.04f, bz + 0.04f,
                    0.12f, ageTicks * 0.04f, orbUv, orbUv, 175, 72, 255, 140);
        }
    }

    private static void renderSakuraSpiritWingSide(
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
        float[] lobeUv = EXTRA_UV_A.get();
        float[] petalUv = EXTRA_UV_B.get();
        float[] orbUv = EXTRA_ORB_UV.get();
        float[] sparkUv = EXTRA_SPARK_UV.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ENERGY_WING, lobeUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_PETAL, petalUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.065f, ry = 0.16f, rz = 0.130f;
        float yawUpper = 0.12f + baseFlap * 0.28f + tipFold * 0.10f;
        float mx = rx + sideSign * Mth.cos(yawUpper) * 0.44f, my = ry - 0.28f, mz = rz + Mth.sin(yawUpper) * 0.12f;
        float bx = rx + sideSign * Mth.cos(yawUpper) * 0.96f, by = ry - 0.50f, bz = rz + Mth.sin(yawUpper) * 0.22f;

        float ux1 = rx + sideSign * Mth.cos(yawUpper) * 0.88f, uy1 = ry + 0.20f, uz1 = rz + Mth.sin(yawUpper) * 0.20f;
        float lx0 = rx + sideSign * Mth.cos(yawUpper) * 0.76f, ly0 = ry + 0.14f, lz0 = rz + Mth.sin(yawUpper) * 0.18f + 0.02f;
        float lx1 = rx + sideSign * Mth.cos(yawUpper) * 0.64f, ly1 = ry + 0.66f, lz1 = rz + Mth.sin(yawUpper) * 0.16f + 0.02f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry - 0.08f, rz, 0.020f, mx, my, mz, 0.014f, 88, 42, 58, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, mx, my, mz, 0.014f, bx, by, bz, 0.006f, 108, 52, 72, 255);

            CosmeticWingRenderer.emitSubdividedMembraneBay(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, lobeUv,
                    rx, ry - 0.08f, rz, bx, by, bz,
                    rx, ry + 0.24f, rz + 0.02f, ux1, uy1, uz1,
                    0.0f, 0.024f, 255, 165, 208, 236);
            CosmeticWingRenderer.emitSubdividedMembraneBay(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, lobeUv,
                    rx, ry + 0.16f, rz + 0.02f, lx0, ly0, lz0,
                    rx + sideSign * 0.02f, ry + 0.54f, rz + 0.03f, lx1, ly1, lz1,
                    0.0f, 0.020f, 255, 120, 178, 232);

            for (int p = 0; p < 4; p++) {
                float u = (float) p / 3.0f;
                float fx = Mth.lerp(u, rx + sideSign * 0.12f, bx);
                float fy = Mth.lerp(u, ry - 0.10f, by);
                float fz = Mth.lerp(u, rz, bz) + 0.02f;
                CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        fx, fy, fz, 0.068f, ageTicks * 0.05f + p, petalUv, 255, 195, 225, 248);
            }
        }

        if (renderGlow) {
            CosmeticWingRenderer.emitSubdividedMembraneBay(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, lobeUv,
                    rx, ry - 0.12f, rz + 0.018f, bx + sideSign * 0.08f, by - 0.05f, bz + 0.018f,
                    rx, ry + 0.28f, rz + 0.018f, ux1 + sideSign * 0.08f, uy1 + 0.04f, uz1 + 0.018f,
                    0.0f, 0.025f, 255, 185, 222, 68);
            for (int i = 0; i < 6; i++) {
                float t = (float) i / 5.0f;
                float px = Mth.lerp(t, (bx + ux1) * 0.50f, (lx0 + lx1) * 0.50f) + sideSign * 0.06f;
                float py = Mth.lerp(t, by + 0.06f, ly1 - 0.04f) + Mth.sin(ageTicks * 0.14f + i * 1.2f) * 0.045f;
                float pz = Mth.lerp(t, bz, lz1) + 0.05f;
                CosmeticWingRenderer.emitWingGlowNode(pose, vc, px, py, pz, 0.050f,
                        ageTicks * 0.07f + i, orbUv, (i % 2 == 0) ? petalUv : sparkUv, 255, 178, 218, 220);
            }
        }
    }

    private static void renderMechaValkyrieWingSide(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float baseFlap,
            float ageTicks,
            float sideSign,
            boolean renderSolid,
            boolean renderGlow
    ) {
        float[] bladeUv = EXTRA_UV_A.get();
        float[] orbUv = EXTRA_ORB_UV.get();
        float[] sparkUv = EXTRA_SPARK_UV.get();
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_HALO_BAND, bladeUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
        CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_SPARK, sparkUv);

        float rx = sideSign * 0.09f, ry = 0.12f, rz = 0.132f;
        float mx = rx + sideSign * 0.16f, my = ry - 0.10f, mz = rz + 0.06f;
        float px = mx + sideSign * 0.10f, py = my + 0.24f, pz = mz + 0.04f;

        if (renderSolid) {
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, rx, ry, rz, 0.028f, mx, my, mz, 0.024f, 232, 236, 248, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, light, mx, my, mz, 0.024f, px, py, pz, 0.018f, 215, 222, 238, 255);
            CosmeticGeometryHelper.emit3DBoneSegment(pose, vc, CosmeticWingRenderer.FULL_BRIGHT, rx, ry - 0.02f, rz + 0.01f, 0.014f, mx, my - 0.02f, mz + 0.01f, 0.011f, 255, 208, 72, 255);
        }
        if (renderGlow) {
            CosmeticWingRenderer.emitWingGlowNode(pose, vc, mx, my, mz + 0.04f, 0.080f, ageTicks * 0.06f, orbUv, sparkUv, 255, 218, 95, 225);
        }

        int feathers = 5;
        for (int b = 0; b < feathers; b++) {
            float u = (float) b / (feathers - 1);
            float bx = Mth.lerp(u, mx, px) + sideSign * 0.03f;
            float by = Mth.lerp(u, my - 0.04f, py + 0.06f);
            float bz = Mth.lerp(u, mz, pz) + 0.02f;

            float pitch = -0.54f + u * 1.10f;
            float yaw = 0.15f + baseFlap * 0.22f;
            float len = 0.86f - u * 0.16f;

            float dirX = sideSign * Mth.cos(yaw) * Mth.cos(pitch);
            float dirY = Mth.sin(pitch);
            float dirZ = Mth.sin(yaw) * Mth.cos(pitch);

            float tx = bx + dirX * len;
            float ty = by + dirY * len;
            float tz = bz + dirZ * len;
            float halfW = 0.102f - u * 0.012f;
            float pxOff = -dirY * halfW * 0.25f;

            if (renderSolid) {
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        bx - pxOff, by - halfW, bz,
                        tx - pxOff * 0.20f, ty - halfW * 0.24f, tz,
                        tx + pxOff * 0.20f, ty + halfW * 0.24f, tz,
                        bx + pxOff, by + halfW, bz,
                        bladeUv, 255, 198, 52, 238);

                float coreW = halfW * 0.45f;
                float cxOff = -dirY * coreW * 0.25f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        bx - cxOff, by - coreW, bz + 0.008f,
                        tx - cxOff * 0.18f, ty - coreW * 0.20f, tz + 0.008f,
                        tx + cxOff * 0.18f, ty + coreW * 0.20f, tz + 0.008f,
                        bx + cxOff, by + coreW, bz + 0.008f,
                        bladeUv, 255, 248, 195, 252);
            }

            if (renderGlow) {
                float auraW = halfW * 1.65f;
                float axOff = -dirY * auraW * 0.25f;
                CosmeticGeometryHelper.emitQuadDoubleSided(pose, vc, CosmeticWingRenderer.FULL_BRIGHT,
                        bx - axOff, by - auraW, bz + 0.012f,
                        tx - axOff * 0.22f, ty - auraW * 0.28f, tz + 0.012f,
                        tx + axOff * 0.22f, ty + auraW * 0.28f, tz + 0.012f,
                        bx + axOff, by + auraW, bz + 0.012f,
                        bladeUv, 255, 215, 88, 72);
                CosmeticWingRenderer.emitWingGlowNode(pose, vc, tx, ty, tz, 0.048f, ageTicks * 0.07f + b, orbUv, sparkUv,
                        255, 232, 130, 210);
            }
        }
    }
}
