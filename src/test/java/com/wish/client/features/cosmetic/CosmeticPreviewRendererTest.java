package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticPreviewRendererTest {
    private static final int CARD_W = 240;
    private static final int CARD_H = 290;
    private static final int COLS = 5;
    private static final int FULL_LIGHT = 15728880;

    private record VertexData(float x, float y, float z, float u, float v, int r, int g, int b, int a, float nx, float ny, float nz) {
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        final List<VertexData> vertices = new ArrayList<>();
        private float x, y, z, u, v, nx, ny = 1.0f, nz;
        private int r = 255, g = 255, b = 255, a = 255;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = 255;
            this.g = 255;
            this.b = 255;
            this.a = 255;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            this.a = (argb >>> 24) & 0xFF;
            this.r = (argb >>> 16) & 0xFF;
            this.g = (argb >>> 8) & 0xFF;
            this.b = argb & 0xFF;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            vertices.add(new VertexData(x, y, z, u, v, r, g, b, a, nx, ny, nz));
            return this;
        }
    }

    @Test
    void allThirtyCosmeticsRenderVisibleNonEmptyGeometryAndGeneratePreviewSheets() throws IOException {
        int[] atlasAbgr = new int[CosmeticAtlasMath.ATLAS_WIDTH * CosmeticAtlasMath.ATLAS_HEIGHT];
        for (int y = 0; y < CosmeticAtlasMath.ATLAS_HEIGHT; y++) {
            for (int x = 0; x < CosmeticAtlasMath.ATLAS_WIDTH; x++) {
                atlasAbgr[y * CosmeticAtlasMath.ATLAS_WIDTH + x] = CosmeticAtlasMath.sampleAtlasPixelAbgr(x, y);
            }
        }
        int[] wingAtlasAbgr = new int[CosmeticWingAtlasMath.ATLAS_WIDTH * CosmeticWingAtlasMath.ATLAS_HEIGHT];
        for (int y = 0; y < CosmeticWingAtlasMath.ATLAS_HEIGHT; y++) {
            for (int x = 0; x < CosmeticWingAtlasMath.ATLAS_WIDTH; x++) {
                wingAtlasAbgr[y * CosmeticWingAtlasMath.ATLAS_WIDTH + x] = CosmeticWingAtlasMath.sampleWingAtlasAbgr(x, y);
            }
        }

        Path outDir = Path.of("build", "cosmetic-previews");
        Files.createDirectories(outDir);

        BufferedImage wingsSheet = renderCategoryGrid("WINGS (10 Models - Back 3/4 View)", PlayerCosmeticsManager.WING_NAMES, 0, atlasAbgr, wingAtlasAbgr);
        BufferedImage halosSheet = renderCategoryGrid("HALOS (10 Models - Front 3/4 View)", PlayerCosmeticsManager.HALO_NAMES, 1, atlasAbgr, wingAtlasAbgr);
        BufferedImage particlesSheet = renderCategoryGrid("PARTICLES (10 Models - Front 3/4 View)", PlayerCosmeticsManager.PARTICLE_NAMES, 2, atlasAbgr, wingAtlasAbgr);

        ImageIO.write(wingsSheet, "png", outDir.resolve("wings_preview.png").toFile());
        ImageIO.write(halosSheet, "png", outDir.resolve("halos_preview.png").toFile());
        ImageIO.write(particlesSheet, "png", outDir.resolve("particles_preview.png").toFile());

        BufferedImage combined = new BufferedImage(
                wingsSheet.getWidth(),
                wingsSheet.getHeight() + halosSheet.getHeight() + particlesSheet.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D cg = combined.createGraphics();
        cg.drawImage(wingsSheet, 0, 0, null);
        cg.drawImage(halosSheet, 0, wingsSheet.getHeight(), null);
        cg.drawImage(particlesSheet, 0, wingsSheet.getHeight() + halosSheet.getHeight(), null);
        cg.dispose();
        ImageIO.write(combined, "png", outDir.resolve("cosmetics_preview.png").toFile());
    }

    private static BufferedImage renderCategoryGrid(String rowHeader, List<String> names, int kind, int[] atlasAbgr, int[] wingAtlasAbgr) {
        int count = PlayerCosmeticsManager.COUNT_PER_CATEGORY;
        int rows = (count + COLS - 1) / COLS;
        int pad = 12;
        int headerH = 28;
        int totalW = COLS * CARD_W + (COLS + 1) * pad;
        int totalH = headerH + rows * (CARD_H + pad) + pad;
        BufferedImage sheet = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x0D0F16));
        g.fillRect(0, 0, totalW, totalH);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(new Color(0xD8DDF0));
        g.drawString(rowHeader, pad, 22);

        for (int id = 1; id <= count; id++) {
            int col = (id - 1) % COLS;
            int row = (id - 1) / COLS;
            int cx = pad + col * (CARD_W + pad);
            int cy = headerH + pad + row * (CARD_H + pad);
            BufferedImage card = renderSingleCard(names.get(id), kind, id, atlasAbgr, wingAtlasAbgr);
            g.drawImage(card, cx, cy, null);
        }
        g.dispose();
        return sheet;
    }

    private static BufferedImage renderSingleCard(String title, int kind, int id, int[] atlasAbgr, int[] wingAtlasAbgr) {
        BufferedImage img = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
        float[] zBuf = new float[CARD_W * CARD_H];
        Arrays.fill(zBuf, Float.NEGATIVE_INFINITY);

        for (int y = 0; y < CARD_H; y++) {
            int shade = 0x16 + (y * 8 / CARD_H);
            int bg = (0xFF << 24) | (shade << 16) | ((shade + 2) << 8) | (shade + 8);
            for (int x = 0; x < CARD_W; x++) {
                img.setRGB(x, y, bg);
            }
        }

        CapturingVertexConsumer bodyVc = new CapturingVertexConsumer();
        float yaw = kind == 0 ? 168.0f : 24.0f;
        float cy = kind == 1 ? 156.0f : 134.0f;
        float sc = kind == 1 ? 110.0f : 72.0f;
        PoseStack bodyPose = setupPreviewPose(yaw, cy, sc);
        emitPlayerSilhouette(bodyPose.last(), bodyVc);
        rasterizeQuads(img, zBuf, bodyVc.vertices, atlasAbgr, false, true);

        CapturingVertexConsumer solidVc = new CapturingVertexConsumer();
        CapturingVertexConsumer glowVc = new CapturingVertexConsumer();
        PoseStack cosPose = setupPreviewPose(yaw, cy, sc);
        float ageTicks = 24.0f;
        if (kind == 0) {
            float flap = PlayerCosmeticsManager.computeWingFlapAngleRad(id, ageTicks, 0.2f, false, false);
            float fold = PlayerCosmeticsManager.computeWingTipFoldRad(id, flap, ageTicks);
            CosmeticWingRenderer.renderWingsSolid(cosPose.last(), solidVc, FULL_LIGHT, id, flap, fold, ageTicks);
            CosmeticWingRenderer.renderWingsGlow(cosPose.last(), glowVc, id, flap, fold, ageTicks);
        } else if (kind == 1) {
            float fy = PlayerCosmeticsManager.computeHaloFloatOffset(id, ageTicks);
            CosmeticHaloParticleRenderer.renderHaloSolid(cosPose.last(), solidVc, id, fy, ageTicks);
            CosmeticHaloParticleRenderer.renderHaloGlow(cosPose.last(), glowVc, id, fy, ageTicks);
        } else {
            CosmeticHaloParticleRenderer.renderParticles(cosPose.last(), glowVc, id, ageTicks);
        }

        int[] solidTex = (kind == 0) ? wingAtlasAbgr : atlasAbgr;
        int solidW = (kind == 0) ? CosmeticWingAtlasMath.ATLAS_WIDTH : CosmeticAtlasMath.ATLAS_WIDTH;
        int solidH = (kind == 0) ? CosmeticWingAtlasMath.ATLAS_HEIGHT : CosmeticAtlasMath.ATLAS_HEIGHT;
        int solidPixels = rasterizeQuads(img, zBuf, solidVc.vertices, solidTex, solidW, solidH, true, true);
        int glowPixels = rasterizeQuads(img, zBuf, glowVc.vertices, atlasAbgr, CosmeticAtlasMath.ATLAS_WIDTH, CosmeticAtlasMath.ATLAS_HEIGHT, true, false);
        int renderedPixels = solidPixels + glowPixels;
        assertTrue(renderedPixels > 80,
                "Cosmetic kind=" + kind + " id=" + id + " (" + title + ") must render visible pixels, got " + renderedPixels);

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x2B3042));
        g.drawRect(0, 0, CARD_W - 1, CARD_H - 1);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.WHITE);
        String label = id + ". " + title;
        int textW = g.getFontMetrics().stringWidth(label);
        g.drawString(label, (CARD_W - textW) / 2, CARD_H - 14);
        g.dispose();
        return img;
    }

    private static PoseStack setupPreviewPose(float yawDeg, float screenCenterY, float scale) {
        PoseStack pose = new PoseStack();
        pose.translate(CARD_W * 0.5f, screenCenterY, 0.0f);
        pose.scale(scale, -scale, scale);
        pose.mulPose(new org.joml.Quaternionf().rotationX((float) Math.toRadians(-14.0)));
        pose.mulPose(new org.joml.Quaternionf().rotationY((float) Math.toRadians(yawDeg)));
        pose.scale(-1.0f, -1.0f, 1.0f);
        return pose;
    }

    private static void emitPlayerSilhouette(PoseStack.Pose pose, CapturingVertexConsumer vc) {
        emitBox(pose, vc, -0.25f, -0.50f, -0.25f, 0.25f, 0.00f, 0.25f, 64, 72, 92);
        emitBox(pose, vc, -0.25f, 0.00f, -0.125f, 0.25f, 0.75f, 0.125f, 52, 58, 76);
        emitBox(pose, vc, -0.50f, 0.00f, -0.125f, -0.25f, 0.75f, 0.125f, 46, 52, 68);
        emitBox(pose, vc, 0.25f, 0.00f, -0.125f, 0.50f, 0.75f, 0.125f, 46, 52, 68);
        emitBox(pose, vc, -0.25f, 0.75f, -0.125f, 0.00f, 1.50f, 0.125f, 40, 45, 60);
        emitBox(pose, vc, 0.00f, 0.75f, -0.125f, 0.25f, 1.50f, 0.125f, 40, 45, 60);
    }

    private static void emitBox(PoseStack.Pose pose, CapturingVertexConsumer vc,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                int r, int g, int b) {
        CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_LIGHT, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, 255);
        CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_LIGHT, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, r - 6, g - 6, b - 6, 255);
        CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_LIGHT, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r - 4, g - 4, b - 4, 255);
        CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_LIGHT, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r - 4, g - 4, b - 4, 255);
        CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_LIGHT, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r + 6, g + 6, b + 6, 255);
        CosmeticGeometryHelper.emitQuadWhiteDoubleSided(pose, vc, FULL_LIGHT, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r - 8, g - 8, b - 8, 255);
    }

    private static int rasterizeQuads(
            BufferedImage img,
            float[] zBuf,
            List<VertexData> verts,
            int[] atlasAbgr,
            boolean alphaBlend,
            boolean writeDepth
    ) {
        return rasterizeQuads(img, zBuf, verts, atlasAbgr, CosmeticAtlasMath.ATLAS_WIDTH, CosmeticAtlasMath.ATLAS_HEIGHT, alphaBlend, writeDepth);
    }

    private static int rasterizeQuads(
            BufferedImage img,
            float[] zBuf,
            List<VertexData> verts,
            int[] atlasAbgr,
            int atlasW,
            int atlasH,
            boolean alphaBlend,
            boolean writeDepth
    ) {
        int drawn = 0;
        for (int i = 0; i + 3 < verts.size(); i += 4) {
            VertexData v0 = verts.get(i);
            VertexData v1 = verts.get(i + 1);
            VertexData v2 = verts.get(i + 2);
            VertexData v3 = verts.get(i + 3);
            drawn += rasterizeTri(img, zBuf, v0, v1, v2, atlasAbgr, atlasW, atlasH, alphaBlend, writeDepth);
            drawn += rasterizeTri(img, zBuf, v0, v2, v3, atlasAbgr, atlasW, atlasH, alphaBlend, writeDepth);
        }
        return drawn;
    }

    private static int rasterizeTri(
            BufferedImage img,
            float[] zBuf,
            VertexData a,
            VertexData b,
            VertexData c,
            int[] atlasAbgr,
            int atlasW,
            int atlasH,
            boolean alphaBlend,
            boolean writeDepth
    ) {
        int minX = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int maxX = Math.min(CARD_W - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int maxY = Math.min(CARD_H - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        float area = edge(a.x, a.y, b.x, b.y, c.x, c.y);
        if (Math.abs(area) < 1e-4f) {
            return 0;
        }
        int drawn = 0;
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                float sx = px + 0.5f;
                float sy = py + 0.5f;
                float w0 = edge(b.x, b.y, c.x, c.y, sx, sy) / area;
                float w1 = edge(c.x, c.y, a.x, a.y, sx, sy) / area;
                float w2 = 1.0f - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                float z = w0 * a.z + w1 * b.z + w2 * c.z;
                int idx = py * CARD_W + px;
                if (z < zBuf[idx]) {
                    continue;
                }
                float u = Math.max(0.0f, Math.min(0.9999f, w0 * a.u + w1 * b.u + w2 * c.u));
                float v = Math.max(0.0f, Math.min(0.9999f, w0 * a.v + w1 * b.v + w2 * c.v));
                int tx = (int) (u * atlasW);
                int ty = (int) (v * atlasH);
                int texAbgr = atlasAbgr[ty * atlasW + tx];
                int texA = (texAbgr >>> 24) & 0xFF;
                int texR = texAbgr & 0xFF;
                int texG = (texAbgr >>> 8) & 0xFF;
                int texB = (texAbgr >>> 16) & 0xFF;

                int vr = Math.round(w0 * a.r + w1 * b.r + w2 * c.r);
                int vg = Math.round(w0 * a.g + w1 * b.g + w2 * c.g);
                int vb = Math.round(w0 * a.b + w1 * b.b + w2 * c.b);
                int va = Math.round(w0 * a.a + w1 * b.a + w2 * c.a);

                int outA = (texA * va) / 255;
                int cutoff = writeDepth ? 26 : 6;
                if (outA < cutoff) {
                    continue;
                }
                int outR = (texR * vr) / 255;
                int outG = (texG * vg) / 255;
                int outB = (texB * vb) / 255;

                if (writeDepth) {
                    zBuf[idx] = z;
                }
                if (alphaBlend && outA < 255) {
                    int dst = img.getRGB(px, py);
                    int dr = (dst >>> 16) & 0xFF;
                    int dg = (dst >>> 8) & 0xFF;
                    int db = dst & 0xFF;
                    float fa = outA / 255.0f;
                    int mr = Math.min(255, Math.round(outR * fa + dr * (1.0f - fa)));
                    int mg = Math.min(255, Math.round(outG * fa + dg * (1.0f - fa)));
                    int mb = Math.min(255, Math.round(outB * fa + db * (1.0f - fa)));
                    img.setRGB(px, py, (0xFF << 24) | (mr << 16) | (mg << 8) | mb);
                } else {
                    img.setRGB(px, py, (0xFF << 24) | (outR << 16) | (outG << 8) | outB);
                }
                drawn++;
            }
        }
        return drawn;
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }
}
