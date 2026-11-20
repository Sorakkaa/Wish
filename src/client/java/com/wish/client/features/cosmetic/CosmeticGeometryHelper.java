package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class CosmeticGeometryHelper {
    private static final ThreadLocal<Vector3f> TEMP_POS = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TEMP_NORM = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TEMP_RIGHT = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TEMP_UP = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TEMP_MODEL_UP = ThreadLocal.withInitial(Vector3f::new);

    private CosmeticGeometryHelper() {
    }

    static void putTransformedVertex(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float x, float y, float z,
            float u, float v,
            int r, int g, int b, int a,
            float nx, float ny, float nz
    ) {
        Vector3f pos = TEMP_POS.get();
        Vector3f norm = TEMP_NORM.get();
        pose.pose().transformPosition(x, y, z, pos);
        pose.normal().transform(nx, ny, nz, norm);
        float lenSq = norm.lengthSquared();
        if (lenSq > 1e-8f) {
            norm.div(Mth.sqrt(lenSq));
        } else {
            norm.set(0.0f, 1.0f, 0.0f);
        }
        vc.addVertex(pos.x, pos.y, pos.z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(norm.x, norm.y, norm.z);
    }

    static void emitQuadDoubleSided(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float[] uv,
            int r, int g, int b, int a
    ) {
        emitQuadDoubleSidedUv(pose, vc, light, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
                uv[0], uv[1], uv[2], uv[3], r, g, b, a);
    }

    static void emitQuadDoubleSidedUv(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u0, float v0, float u1, float v1,
            int r, int g, int b, int a
    ) {
        float ux = x1 - x0, uy = y1 - y0, uz = z1 - z0;
        float vx = x3 - x0, vy = y3 - y0, vz = z3 - z0;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float nLen = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (nLen > 1e-5f) {
            nx /= nLen;
            ny /= nLen;
            nz /= nLen;
        } else {
            nx = 0.0f;
            ny = 1.0f;
            nz = 0.0f;
        }

        putTransformedVertex(pose, vc, light, x0, y0, z0, u0, v0, r, g, b, a, nx, ny, nz);
        putTransformedVertex(pose, vc, light, x1, y1, z1, u1, v0, r, g, b, a, nx, ny, nz);
        putTransformedVertex(pose, vc, light, x2, y2, z2, u1, v1, r, g, b, a, nx, ny, nz);
        putTransformedVertex(pose, vc, light, x3, y3, z3, u0, v1, r, g, b, a, nx, ny, nz);
    }

    static void emitQuadWhiteDoubleSided(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            int r, int g, int b, int a
    ) {
        emitQuadDoubleSidedUv(pose, vc, light,
                x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
                CosmeticAtlasMath.SOLID_WHITE_U, CosmeticAtlasMath.SOLID_WHITE_V,
                CosmeticAtlasMath.SOLID_WHITE_U, CosmeticAtlasMath.SOLID_WHITE_V,
                r, g, b, a);
    }

    static void emit3DBoneSegment(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float ax, float ay, float az, float r0,
            float bx, float by, float bz, float r1,
            int r, int g, int b, int a
    ) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        float rx = Math.abs(dy) < 0.9f ? 0.0f : 1.0f;
        float ry = Math.abs(dy) < 0.9f ? 1.0f : 0.0f;
        float rz = 0.0f;

        float ux = dy * rz - dz * ry;
        float uy = dz * rx - dx * rz;
        float uz = dx * ry - dy * rx;
        float uLen = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen < 1e-4f) {
            return;
        }
        ux /= uLen;
        uy /= uLen;
        uz /= uLen;

        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;

        float a0x = ax + (ux + vx) * r0, a0y = ay + (uy + vy) * r0, a0z = az + (uz + vz) * r0;
        float a1x = ax + (-ux + vx) * r0, a1y = ay + (-uy + vy) * r0, a1z = az + (-uz + vz) * r0;
        float a2x = ax + (-ux - vx) * r0, a2y = ay + (-uy - vy) * r0, a2z = az + (-uz - vz) * r0;
        float a3x = ax + (ux - vx) * r0, a3y = ay + (uy - vy) * r0, a3z = az + (uz - vz) * r0;

        float b0x = bx + (ux + vx) * r1, b0y = by + (uy + vy) * r1, b0z = bz + (uz + vz) * r1;
        float b1x = bx + (-ux + vx) * r1, b1y = by + (-uy + vy) * r1, b1z = bz + (-uz + vz) * r1;
        float b2x = bx + (-ux - vx) * r1, b2y = by + (-uy - vy) * r1, b2z = bz + (-uz - vz) * r1;
        float b3x = bx + (ux - vx) * r1, b3y = by + (uy - vy) * r1, b3z = bz + (uz - vz) * r1;

        int shadeDarkR = Math.max(8, Math.round(r * 0.72f));
        int shadeDarkG = Math.max(8, Math.round(g * 0.72f));
        int shadeDarkB = Math.max(8, Math.round(b * 0.72f));
        int shadeMidR = Math.max(8, Math.round(r * 0.88f));
        int shadeMidG = Math.max(8, Math.round(g * 0.88f));
        int shadeMidB = Math.max(8, Math.round(b * 0.88f));

        emitQuadWhiteDoubleSided(pose, vc, light, a0x, a0y, a0z, a1x, a1y, a1z, b1x, b1y, b1z, b0x, b0y, b0z, r, g, b, a);
        emitQuadWhiteDoubleSided(pose, vc, light, a1x, a1y, a1z, a2x, a2y, a2z, b2x, b2y, b2z, b1x, b1y, b1z, shadeMidR, shadeMidG, shadeMidB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, a2x, a2y, a2z, a3x, a3y, a3z, b3x, b3y, b3z, b2x, b2y, b2z, shadeDarkR, shadeDarkG, shadeDarkB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, a3x, a3y, a3z, a0x, a0y, a0z, b0x, b0y, b0z, b3x, b3y, b3z, shadeMidR, shadeMidG, shadeMidB, a);

        if (r0 > 1e-4f) {
            emitQuadWhiteDoubleSided(pose, vc, light, a0x, a0y, a0z, a3x, a3y, a3z, a2x, a2y, a2z, a1x, a1y, a1z, shadeDarkR, shadeDarkG, shadeDarkB, a);
        }
        if (r1 > 1e-4f) {
            emitQuadWhiteDoubleSided(pose, vc, light, b0x, b0y, b0z, b1x, b1y, b1z, b2x, b2y, b2z, b3x, b3y, b3z, r, g, b, a);
        }
    }

    static void emitVoxelStrut(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float halfW,
            float halfD,
            int r, int g, int b, int a
    ) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        float rx = Math.abs(dz) < 0.9f ? 0.0f : 1.0f;
        float ry = 0.0f;
        float rz = Math.abs(dz) < 0.9f ? 1.0f : 0.0f;

        float ux = dy * rz - dz * ry;
        float uy = dz * rx - dx * rz;
        float uz = dx * ry - dy * rx;
        float uLen = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen < 1e-4f) {
            return;
        }
        ux /= uLen;
        uy /= uLen;
        uz /= uLen;

        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;

        float uwx = ux * halfW, uwy = uy * halfW, uwz = uz * halfW;
        float vdx = vx * halfD, vdy = vy * halfD, vdz = vz * halfD;

        float a0x = ax + uwx + vdx, a0y = ay + uwy + vdy, a0z = az + uwz + vdz;
        float a1x = ax - uwx + vdx, a1y = ay - uwy + vdy, a1z = az - uwz + vdz;
        float a2x = ax - uwx - vdx, a2y = ay - uwy - vdy, a2z = az - uwz - vdz;
        float a3x = ax + uwx - vdx, a3y = ay + uwy - vdy, a3z = az + uwz - vdz;

        float b0x = bx + uwx + vdx, b0y = by + uwy + vdy, b0z = bz + uwz + vdz;
        float b1x = bx - uwx + vdx, b1y = by - uwy + vdy, b1z = bz - uwz + vdz;
        float b2x = bx - uwx - vdx, b2y = by - uwy - vdy, b2z = bz - uwz - vdz;
        float b3x = bx + uwx - vdx, b3y = by + uwy - vdy, b3z = bz + uwz - vdz;

        int darkR = Math.max(8, Math.round(r * 0.68f)), darkG = Math.max(8, Math.round(g * 0.68f)), darkB = Math.max(8, Math.round(b * 0.68f));
        int midR = Math.max(8, Math.round(r * 0.86f)), midG = Math.max(8, Math.round(g * 0.86f)), midB = Math.max(8, Math.round(b * 0.86f));

        emitQuadWhiteDoubleSided(pose, vc, light, a0x, a0y, a0z, a1x, a1y, a1z, b1x, b1y, b1z, b0x, b0y, b0z, r, g, b, a);
        emitQuadWhiteDoubleSided(pose, vc, light, a1x, a1y, a1z, a2x, a2y, a2z, b2x, b2y, b2z, b1x, b1y, b1z, midR, midG, midB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, a2x, a2y, a2z, a3x, a3y, a3z, b3x, b3y, b3z, b2x, b2y, b2z, darkR, darkG, darkB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, a3x, a3y, a3z, a0x, a0y, a0z, b0x, b0y, b0z, b3x, b3y, b3z, midR, midG, midB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, a0x, a0y, a0z, a3x, a3y, a3z, a2x, a2y, a2z, a1x, a1y, a1z, darkR, darkG, darkB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, b0x, b0y, b0z, b1x, b1y, b1z, b2x, b2y, b2z, b3x, b3y, b3z, r, g, b, a);
    }

    static void emitVoxelCube(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float cx, float cy, float cz,
            float hx, float hy, float hz,
            int r, int g, int b, int a
    ) {
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;
        int topR = Math.min(255, Math.round(r * 1.06f)), topG = Math.min(255, Math.round(g * 1.06f)), topB = Math.min(255, Math.round(b * 1.06f));
        int sideR = Math.max(8, Math.round(r * 0.85f)), sideG = Math.max(8, Math.round(g * 0.85f)), sideB = Math.max(8, Math.round(b * 0.85f));
        int botR = Math.max(8, Math.round(r * 0.68f)), botG = Math.max(8, Math.round(g * 0.68f)), botB = Math.max(8, Math.round(b * 0.68f));

        emitQuadWhiteDoubleSided(pose, vc, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
        emitQuadWhiteDoubleSided(pose, vc, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, sideR, sideG, sideB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, sideR, sideG, sideB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, sideR, sideG, sideB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, topR, topG, topB, a);
        emitQuadWhiteDoubleSided(pose, vc, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, botR, botG, botB, a);
    }

    static void emitCameraBillboardQuad(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int light,
            float cx, float cy, float cz,
            float halfSize,
            float rollRad,
            float[] uv,
            int r, int g, int b, int a
    ) {
        if (a <= 4) {
            return;
        }
        Matrix4f m = pose.pose();
        Vector3f center = TEMP_POS.get();
        m.transformPosition(cx, cy, cz, center);

        float colScale = Mth.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
        float s = halfSize * (colScale > 1e-4f ? colScale : 1.0f);

        Vector3f camRight = TEMP_RIGHT.get();
        Vector3f camUp = TEMP_UP.get();
        resolveBillboardAxes(camRight, camUp);
        Vector3f modelUp = TEMP_MODEL_UP.get();
        m.transformDirection(0.0f, -1.0f, 0.0f, modelUp);
        if (modelUp.dot(camUp) < 0.0f) {
            camUp.negate();
        }

        float cosR = Mth.cos(rollRad);
        float sinR = Mth.sin(rollRad);
        float rx = (camRight.x * cosR + camUp.x * sinR) * s;
        float ry = (camRight.y * cosR + camUp.y * sinR) * s;
        float rz = (camRight.z * cosR + camUp.z * sinR) * s;

        float ux = (camUp.x * cosR - camRight.x * sinR) * s;
        float uy = (camUp.y * cosR - camRight.y * sinR) * s;
        float uz = (camUp.z * cosR - camRight.z * sinR) * s;

        float x0 = center.x - rx - ux, y0 = center.y - ry - uy, z0 = center.z - rz - uz;
        float x1 = center.x + rx - ux, y1 = center.y + ry - uy, z1 = center.z + rz - uz;
        float x2 = center.x + rx + ux, y2 = center.y + ry + uy, z2 = center.z + rz + uz;
        float x3 = center.x - rx + ux, y3 = center.y - ry + uy, z3 = center.z - rz + uz;

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        vc.addVertex(x0, y0, z0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(x2, y2, z2).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, 1f);
        vc.addVertex(x3, y3, z3).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, 1f);
    }

    private static void resolveBillboardAxes(Vector3f camRight, Vector3f camUp) {
        camRight.set(1.0f, 0.0f, 0.0f);
        camUp.set(0.0f, 1.0f, 0.0f);
        try {
            if (RenderSystem.outputColorTextureOverride != null) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Camera cam = (mc != null && mc.gameRenderer != null) ? mc.gameRenderer.mainCamera() : null;
            Quaternionf q = cam != null ? cam.rotation() : null;
            if (q != null) {
                camRight.rotate(q);
                camUp.rotate(q);
            }
        } catch (Throwable ignored) {
        }
    }
}
