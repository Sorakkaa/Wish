package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

final class CosmeticWingRenderer {
    static final int FULL_BRIGHT = 15728880;
    private static final ThreadLocal<float[]> WING_UV_SCRATCH = ThreadLocal.withInitial(() -> new float[4]);
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
        int id = PlayerCosmeticsManager.clampId(wingId);
        if (id == 0) {
            return;
        }
        float sweep = 0.24f + baseFlap * 0.56f;
        float outerSweep = sweep + tipFold * 0.24f;

        float rx = sideSign * 0.08f;
        float rz = 0.135f;
        float ryTop = -0.46f;
        float ryBot = 0.78f;

        float innerSpan = 0.58f;
        float outerSpan = 0.62f;

        float mx = rx + sideSign * Mth.cos(sweep) * innerSpan;
        float mz = rz + Mth.sin(sweep) * innerSpan;
        float myTop = ryTop - 0.03f;
        float myBot = ryBot + 0.03f;

        float tx = mx + sideSign * Mth.cos(outerSweep) * outerSpan;
        float tz = mz + Mth.sin(outerSweep) * outerSpan;
        float tyTop = myTop - 0.01f;
        float tyBot = myBot + 0.01f;

        if (renderSolid) {
            float[] uv = WING_UV_SCRATCH.get();
            CosmeticWingAtlasMath.getWingUv(id, uv);
            float u0 = uv[0];
            float v0 = uv[1];
            float u1 = uv[2];
            float v1 = uv[3];
            float uMid = (u0 + u1) * 0.5f;

            CosmeticGeometryHelper.emitQuadDoubleSidedUv(pose, vc, light,
                    rx, ryTop, rz,
                    mx, myTop, mz,
                    mx, myBot, mz,
                    rx, ryBot, rz,
                    u0, v0, uMid, v1,
                    255, 255, 255, 255);
            CosmeticGeometryHelper.emitQuadDoubleSidedUv(pose, vc, light,
                    mx, myTop, mz,
                    tx, tyTop, tz,
                    tx, tyBot, tz,
                    mx, myBot, mz,
                    uMid, v0, u1, v1,
                    255, 255, 255, 255);
        }

        if (renderGlow) {
            float[] orbUv = WING_ORB_UV_SCRATCH.get();
            float[] sparkUv = WING_SPARK_UV_SCRATCH.get();
            CosmeticAtlasMath.getCellUv(CosmeticAtlasMath.CELL_ORB, orbUv);
            int accentCell = switch (id) {
                case 1 -> CosmeticAtlasMath.CELL_HEART;
                case 2 -> CosmeticAtlasMath.CELL_EMBER;
                case 4 -> CosmeticAtlasMath.CELL_RING_RUNE;
                case 5 -> CosmeticAtlasMath.CELL_SNOWFLAKE;
                case 6 -> CosmeticAtlasMath.CELL_LIGHTNING;
                case 8 -> CosmeticAtlasMath.CELL_PETAL;
                case 9 -> CosmeticAtlasMath.CELL_STAR_4PT;
                case 10 -> CosmeticAtlasMath.CELL_CRESCENT;
                default -> CosmeticAtlasMath.CELL_SPARK;
            };
            CosmeticAtlasMath.getCellUv(accentCell, sparkUv);
            int rgb = PlayerCosmeticsManager.getWingDefaultRgb(id);
            int r = (rgb >>> 16) & 0xFF;
            int g = (rgb >>> 8) & 0xFF;
            int b = rgb & 0xFF;
            float tipX = Mth.lerp(0.88f, mx, tx);
            float tipY = Mth.lerp(0.32f, tyTop, tyBot);
            float tipZ = Mth.lerp(0.88f, mz, tz);
            emitWingGlowNode(pose, vc, tipX, tipY, tipZ, 0.052f, ageTicks * 0.05f * sideSign,
                    orbUv, sparkUv, r, g, b, 200);
        }
    }

    static void emitWingGlowNode(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float x, float y, float z,
            float size,
            float roll,
            float[] orbUv,
            float[] sparkUv,
            int r, int g, int b, int a
    ) {
        CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, FULL_BRIGHT, x, y, z, size * 1.45f, 0.0f,
                orbUv, r, g, b, Math.min(255, Math.round(a * 0.42f)));
        CosmeticGeometryHelper.emitCameraBillboardQuad(pose, vc, FULL_BRIGHT, x, y, z, size * 0.72f, roll,
                sparkUv, Math.min(255, r + 55), Math.min(255, g + 55), Math.min(255, b + 55), a);
    }
}
