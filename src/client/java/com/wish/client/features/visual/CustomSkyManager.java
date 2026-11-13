package com.wish.client.features.visual;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalDouble;

public final class CustomSkyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-CustomSky");
    private static final Identifier CUSTOM_SKY_TEX_ID = Identifier.fromNamespaceAndPath("wish", "custom_sky");
    private static final int MAX_MIP_LEVELS = 6;
    private static final int SKY_TEX_W = 1536;
    private static final int SKY_TEX_H = 768;

    public static final String[] MODE_NAMES = {
        "Black Sky",
        "Lofi Dusk",
        "Midnight Breeze",
        "Cloudy Pastel",
        "Sunset Clouds",
        "Ocean Mirage",
        "Sakura Twilight"
    };

    @FunctionalInterface
    private interface SkyPixelSampler {
        int sampleAbgr(int x, int y, double nx, double ny, double nz);
    }

    private static CustomSkyGpuTexture skyTexture = null;
    private static GpuSampler highQualitySampler = null;
    private static GpuBuffer skyDomeBuffer = null;
    private static int skyDomeQuadCount = 0;
    private static int loadedSkyMode = -1;

    private CustomSkyManager() {}

    public static String getModeName(int mode) {
        if (mode < 0 || mode >= MODE_NAMES.length) return MODE_NAMES[0];
        return MODE_NAMES[mode];
    }

    public static int getSkyColor() {
        if (!ModConfig.INSTANCE.enableCustomSky) {
            return 0xFF000000;
        }
        return switch (ModConfig.INSTANCE.customSkyMode) {
            case 1 -> 0xFF42294E; // Lofi Dusk
            case 2 -> 0xFF183250; // Midnight Breeze
            case 3 -> 0xFFAECAE0; // Cloudy Pastel
            case 4 -> 0xFF331B2B; // Sunset Clouds
            case 5 -> 0xFF071D2E; // Ocean Mirage
            case 6 -> 0xFF211229; // Sakura Twilight
            default -> 0xFF000000;
        };
    }

    private static void closeResources() {
        if (skyTexture != null) {
            skyTexture.close();
            skyTexture = null;
        }
        if (skyDomeBuffer != null) {
            skyDomeBuffer.close();
            skyDomeBuffer = null;
        }
        skyDomeQuadCount = 0;
    }

    private static synchronized boolean ensureProceduralSkyLoaded(int skyMode) {
        if (skyMode <= 0 || skyMode >= MODE_NAMES.length) {
            return false;
        }
        if (loadedSkyMode == skyMode && skyTexture != null && skyDomeBuffer != null && skyDomeQuadCount > 0) {
            return true;
        }
        NativeImage generated = null;
        try {
            GpuDevice device = RenderSystem.getDevice();
            if (highQualitySampler == null) {
                highQualitySampler = createHighQualitySampler(device);
            }

            SkyPixelSampler sampler = switch (skyMode) {
                case 1 -> CustomSkyManager::sampleLofiDusk;
                case 2 -> CustomSkyManager::sampleMidnightBreeze;
                case 3 -> CustomSkyManager::sampleCloudyPastel;
                case 4 -> CustomSkyManager::sampleSunsetClouds;
                case 5 -> CustomSkyManager::sampleOceanMirage;
                case 6 -> CustomSkyManager::sampleSakuraTwilight;
                default -> null;
            };
            if (sampler == null) {
                return false;
            }

            generated = generateEquirectangularPanorama(SKY_TEX_W, SKY_TEX_H, sampler);
            closeResources();

            skyTexture = new CustomSkyGpuTexture(device, generated);
            generated = null;
            Minecraft.getInstance().getTextureManager().register(CUSTOM_SKY_TEX_ID, skyTexture);

            skyDomeBuffer = buildSphereBuffer("Wish Procedural Sky Sphere", 64, 32, 100.0f);
            skyDomeQuadCount = (skyDomeBuffer != null) ? (64 * 32) : 0;

            loadedSkyMode = skyMode;
            return skyDomeBuffer != null && skyDomeQuadCount > 0;
        } catch (Exception e) {
            if (generated != null) {
                generated.close();
            }
            LOGGER.error("[Wish] Failed to generate procedural sky mode {}", skyMode, e);
            return false;
        }
    }

    private static NativeImage generateEquirectangularPanorama(int w, int h, SkyPixelSampler shader) {
        double[] sinTheta = new double[w];
        double[] cosTheta = new double[w];
        for (int x = 0; x < w; x++) {
            double theta = ((double) x / w) * (Math.PI * 2.0);
            sinTheta[x] = Math.sin(theta);
            cosTheta[x] = Math.cos(theta);
        }

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            double phi = Math.PI * 0.5 - ((y + 0.5) / h) * Math.PI;
            double cosPhi = Math.cos(phi);
            double ny = Math.sin(phi);
            for (int x = 0; x < w; x++) {
                double nx = cosPhi * sinTheta[x];
                double nz = -cosPhi * cosTheta[x];
                img.setPixelABGR(x, y, shader.sampleAbgr(x, y, nx, ny, nz));
            }
        }
        return img;
    }

    private static int sampleLofiDusk(int x, int y, double nx, double ny, double nz) {
        // C-infinity smooth Gaussian horizon belt avoids any equator pinch at ny = 0
        float horizBand = (float) Math.exp(-ny * ny * 5.5);
        float upperSky = smoothstep(-0.15f, 0.75f, (float) ny);
        float duskAlign = clamp01((float) ((nx * 0.65 + nz * 0.76) * 0.5 + 0.5));
        float warmGlow = (float) Math.pow(duskAlign, 2.2) * (float) Math.exp(-ny * ny * 7.0);

        float baseR = lerp(0.115f, 0.085f, upperSky);
        float baseG = lerp(0.095f, 0.088f, upperSky);
        float baseB = lerp(0.235f, 0.230f, upperSky);

        float r = lerp(baseR, 0.340f, horizBand) + 0.34f * warmGlow;
        float g = lerp(baseG, 0.210f, horizBand) + 0.19f * warmGlow;
        float b = lerp(baseB, 0.355f, horizBand) + 0.13f * warmGlow;

        // Soft dreamy layered lo-fi twilight clouds
        double cloudA = fbm3(nx * 1.85 + 2.3, ny * 2.9 - 0.8, nz * 1.85 + 1.4, 4);
        double cloudB = fbm3(nx * 3.9 - 1.5, ny * 4.8 + 2.1, nz * 3.9 - 3.2, 3);
        double density = smoothstep(0.41f, 0.68f, (float) (cloudA * 0.72 + cloudB * 0.28));
        float cloudBelt = 0.24f + 0.76f * (float) Math.exp(-ny * ny * 2.6);
        float cMask = clamp01((float) density * cloudBelt);

        if (cMask > 0.001f) {
            float litFactor = clamp01((float) (0.28 + 0.72 * duskAlign * horizBand - Math.max(0.0, ny) * 0.25));
            float cR = lerp(0.42f, 0.94f, litFactor);
            float cG = lerp(0.30f, 0.64f, litFactor);
            float cB = lerp(0.52f, 0.70f, litFactor);
            r = lerp(r, cR, cMask * 0.58f);
            g = lerp(g, cG, cMask * 0.58f);
            b = lerp(b, cB, cMask * 0.58f);
        }
        return packAbgr(r, g, b);
    }

    private static int sampleMidnightBreeze(int x, int y, double nx, double ny, double nz) {
        float horizBand = (float) Math.exp(-ny * ny * 5.0);
        float r = lerp(0.028f, 0.102f, horizBand);
        float g = lerp(0.060f, 0.205f, horizBand);
        float b = lerp(0.145f, 0.325f, horizBand);

        // Soft moonlit atmospheric halo
        double moonDot = clamp01((float) ((-nx * 0.55 + ny * 0.42 + nz * 0.72) * 0.5 + 0.5));
        float halo = (float) (Math.pow(moonDot, 3.6) * 0.17 + Math.pow(moonDot, 14.0) * 0.24);
        r += halo * 0.62f;
        g += halo * 0.80f;
        b += halo * 0.98f;

        // Calm wispy night cirrus drifting across the sky dome
        double n1 = fbm3(nx * 2.2 - 1.1, ny * 3.4 + 2.7, nz * 2.2 - 3.4, 4);
        double n2 = fbm3(nx * 4.5 + 3.2, ny * 5.5 - 1.4, nz * 4.5 + 0.9, 3);
        double mistRaw = smoothstep(0.43f, 0.72f, (float) (n1 * 0.70 + n2 * 0.30));
        float cMask = clamp01((float) mistRaw * (0.32f + 0.68f * (float) Math.exp(-ny * ny * 2.2)));

        if (cMask > 0.001f) {
            float lit = clamp01((float) (0.32 + 0.68 * Math.pow(moonDot, 2.0)));
            float cR = lerp(0.18f, 0.50f, lit);
            float cG = lerp(0.32f, 0.68f, lit);
            float cB = lerp(0.51f, 0.85f, lit);
            r = lerp(r, cR, cMask * 0.50f);
            g = lerp(g, cG, cMask * 0.50f);
            b = lerp(b, cB, cMask * 0.50f);
        }
        return packAbgr(r, g, b);
    }

    private static int sampleCloudyPastel(int x, int y, double nx, double ny, double nz) {
        float horizBand = (float) Math.exp(-ny * ny * 4.5);
        float upperT = clamp01((float) (ny * 0.5 + 0.5));
        float sunDir = clamp01((float) ((nx * 0.70 - nz * 0.71) * 0.5 + 0.5));
        float sunWarm = (float) Math.pow(sunDir, 2.4) * (float) Math.exp(-ny * ny * 5.0);

        // Airy pastel periwinkle sky fading seamlessly into warm cotton-candy horizon mist
        float zenR = lerp(0.62f, 0.42f, upperT);
        float zenG = lerp(0.71f, 0.61f, upperT);
        float zenB = lerp(0.86f, 0.84f, upperT);

        float r = lerp(zenR, 0.72f, horizBand) + 0.16f * sunWarm;
        float g = lerp(zenG, 0.80f, horizBand) + 0.08f * sunWarm;
        float b = lerp(zenB, 0.89f, horizBand) + 0.04f * sunWarm;

        // Fluffy Ghibli-inspired cream & pastel-blush cumulus clouds
        double cBase = fbm3(nx * 1.75 + 4.2, ny * 3.0 - 1.5, nz * 1.75 - 2.3, 4);
        double cPuff = fbm3(nx * 4.2 - 2.1, ny * 5.2 + 3.1, nz * 4.2 + 1.8, 3);
        double density = smoothstep(0.44f, 0.69f, (float) (cBase * 0.68 + cPuff * 0.32));
        float cMask = clamp01((float) density * (0.34f + 0.66f * (float) Math.exp(-ny * ny * 2.0)));

        if (cMask > 0.001f) {
            float shade = clamp01((float) (0.36 + (cPuff - 0.35) * 1.10 + sunDir * 0.28 + ny * 0.18));
            float cR = lerp(0.76f, 0.99f, shade);
            float cG = lerp(0.79f, 0.95f, shade);
            float cB = lerp(0.90f, 0.95f, shade);
            r = lerp(r, cR, cMask * 0.82f);
            g = lerp(g, cG, cMask * 0.82f);
            b = lerp(b, cB, cMask * 0.82f);
        }
        return packAbgr(r, g, b);
    }

    private static int sampleSunsetClouds(int x, int y, double nx, double ny, double nz) {
        // Vertical twilight sky gradient from glowing sunset horizon to deep indigo zenith
        float horiz = clamp01(1.0f - Math.abs((float) ny));
        float up = clamp01((float) (ny * 0.5 + 0.5));
        float lowGlow = (float) Math.pow(horiz, 2.6);
        float sunDir = clamp01((float) ((nx * 0.78 - nz * 0.62) * 0.5 + 0.5));
        float sunHalo = (float) Math.pow(sunDir, 3.0) * (float) Math.pow(horiz, 1.5);

        float r = lerp(0.08f, 0.58f, lowGlow) + 0.38f * sunHalo;
        float g = lerp(0.09f, 0.29f, lowGlow) + 0.27f * sunHalo;
        float b = lerp(0.26f, 0.42f, lowGlow) + 0.08f * sunHalo;

        // Backlit stylized cumulus clouds scattered softly across the twilight dome
        double cloudW = fbm3(nx * 2.2 + 5.1, ny * 3.4 - 1.2, nz * 2.2 + 2.7, 4);
        double cloudDetail = fbm3(nx * 5.2 - 1.7, ny * 6.0 + 4.3, nz * 5.2 - 3.4, 3);
        double density = clamp01((float) ((cloudW * 0.72 + cloudDetail * 0.28 - 0.42) * 3.0));
        double belt = 0.35 + 0.65 * Math.pow(horiz, 0.85);
        float cMask = clamp01((float) (density * belt));

        if (cMask > 0.001f) {
            // Cloud underside catches golden-coral sun; upper crests shift lavender-violet
            float litFactor = clamp01((float) (0.35 + 0.65 * sunDir * Math.pow(horiz, 0.6) - Math.max(0.0, ny) * 0.35));
            float cloudR = lerp(0.46f, 0.98f, litFactor);
            float cloudG = lerp(0.32f, 0.64f, litFactor);
            float cloudB = lerp(0.58f, 0.56f, litFactor);
            r = lerp(r, cloudR, cMask * 0.78f);
            g = lerp(g, cloudG, cMask * 0.78f);
            b = lerp(b, cloudB, cMask * 0.78f);
        }

        // Faint early stars near high zenith only where clouds thin out
        int starHash = hash3i(x, y, 2719);
        if ((starHash & 0xFFF) < 5 && up > 0.66f && cMask < 0.25f) {
            float sb = (0.35f + ((starHash >>> 12) & 0xF) / 40.0f) * smoothstep(0.66f, 0.90f, up);
            r += sb * 0.95f;
            g += sb * 0.92f;
            b += sb;
        }
        return packAbgr(r, g, b);
    }

    private static int sampleOceanMirage(int x, int y, double nx, double ny, double nz) {
        float horiz = clamp01(1.0f - Math.abs((float) ny));
        float horizonGlow = (float) Math.pow(horiz, 2.1);

        float r = lerp(0.014f, 0.090f, horizonGlow);
        float g = lerp(0.055f, 0.340f, horizonGlow);
        float b = lerp(0.165f, 0.490f, horizonGlow);

        // Swirling atmospheric bioluminescent aqua & pearl-cyan mirage streams
        double flow = fbm3(nx * 2.4 - 3.3, ny * 1.5 + 1.8, nz * 2.4 + 2.2, 4);
        double stream = Math.sin((ny * 5.2 + nx * 2.4 - nz * 2.1) + flow * 4.5);
        double ribbon = Math.pow(Math.max(0.0, 1.0 - Math.abs(stream) * 1.3), 2.1);
        double foam = fbm3(nx * 5.8 + 4.2, ny * 5.8 - 2.6, nz * 5.8 + 1.1, 3);
        double glow = ribbon * (0.45 + 0.55 * foam) * (0.35 + 0.65 * Math.pow(horiz, 0.7));

        r += (float) (glow * 0.16);
        g += (float) (glow * 0.72);
        b += (float) (glow * 0.85);

        int starHash = hash3i(x, y, 8191);
        if ((starHash & 0xFFF) < 6 && ny > -0.15) {
            float sb = 0.42f + ((starHash >>> 12) & 0xF) / 36.0f;
            r += sb * 0.78f;
            g += sb * 0.96f;
            b += sb;
        }
        return packAbgr(r, g, b);
    }

    private static int sampleSakuraTwilight(int x, int y, double nx, double ny, double nz) {
        float horiz = clamp01(1.0f - Math.abs((float) ny));
        float duskGlow = (float) Math.pow(horiz, 2.2);

        float r = lerp(0.065f, 0.390f, duskGlow);
        float g = lerp(0.045f, 0.215f, duskGlow);
        float b = lerp(0.165f, 0.365f, duskGlow);

        // Soft wispy cherry-blossom & champagne-gold twilight cirrus veils
        double wx = nx + 0.45 * fbm3(nx * 2.3 + 1.7, ny * 2.3 - 0.9, nz * 2.3 + 4.1, 3);
        double wy = ny + 0.45 * fbm3(nx * 2.3 - 2.6, ny * 2.3 + 3.2, nz * 2.3 - 1.5, 3);
        double wz = nz + 0.45 * fbm3(nx * 2.3 + 3.8, ny * 2.3 + 0.4, nz * 2.3 - 2.8, 3);

        double n1 = fbm3(wx * 2.6, wy * 3.1, wz * 2.6, 4);
        double n2 = fbm3(wx * 5.4 - 3.1, wy * 5.4 + 2.4, wz * 5.4 + 1.6, 3);
        double petalWisp = Math.pow(Math.max(0.0, 1.0 - Math.abs(n1 * 2.0 - 1.0) * 1.45), 1.9);
        float cloudMask = clamp01((float) ((petalWisp * 0.65 + n2 * 0.35 - 0.12) * 1.15));

        if (cloudMask > 0.001f) {
            float warmShift = clamp01((float) n2);
            float cR = lerp(0.95f, 0.99f, warmShift);
            float cG = lerp(0.56f, 0.78f, warmShift);
            float cB = lerp(0.79f, 0.84f, warmShift);
            r = lerp(r, cR, cloudMask * 0.56f);
            g = lerp(g, cG, cloudMask * 0.56f);
            b = lerp(b, cB, cloudMask * 0.56f);
        }

        // Delicate warm rose-gold & lavender star sparkles across the twilight sky
        int starHash = hash3i(x, y, 5237);
        if ((starHash & 0xFFF) < 8) {
            float sb = 0.45f + ((starHash >>> 12) & 0xF) / 30.0f;
            boolean roseTint = (starHash & 0x10000) != 0;
            r += sb * (roseTint ? 1.0f : 0.92f);
            g += sb * (roseTint ? 0.80f : 0.90f);
            b += sb * (roseTint ? 0.92f : 1.0f);
        }
        return packAbgr(r, g, b);
    }

    private static GpuBuffer buildSphereBuffer(String label, int slices, int stacks, float radius) {
        VertexFormat fmt = DefaultVertexFormat.POSITION_TEX_COLOR;
        int quadCount = slices * stacks;
        int colWhite = 0xFFFFFFFF;

        double[] phis = new double[stacks + 1];
        float[] vs = new float[stacks + 1];
        float vEps = 0.0005f;
        for (int i = 0; i <= stacks; i++) {
            double frac = (double) i / stacks;
            phis[i] = Math.PI * 0.5 - frac * Math.PI;
            vs[i] = vEps + (float) frac * (1.0f - 2.0f * vEps);
        }

        try (ByteBufferBuilder bbb = ByteBufferBuilder.exactlySized(quadCount * 4 * fmt.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bbb, PrimitiveTopology.QUADS, fmt);

            for (int i = 0; i < stacks; i++) {
                double phi0 = phis[i];
                double phi1 = phis[i + 1];
                float v0 = vs[i];
                float v1 = vs[i + 1];

                double r0 = radius * Math.cos(phi0);
                float y0 = (float) (radius * Math.sin(phi0));
                double r1 = radius * Math.cos(phi1);
                float y1 = (float) (radius * Math.sin(phi1));

                for (int j = 0; j < slices; j++) {
                    float u0 = (float) j / slices;
                    float u1 = (float) (j + 1) / slices;
                    double t0 = u0 * (Math.PI * 2.0);
                    double t1 = u1 * (Math.PI * 2.0);

                    float x00 = (float) (r0 * Math.sin(t0));
                    float z00 = (float) (-r0 * Math.cos(t0));
                    float x10 = (float) (r1 * Math.sin(t0));
                    float z10 = (float) (-r1 * Math.cos(t0));
                    float x11 = (float) (r1 * Math.sin(t1));
                    float z11 = (float) (-r1 * Math.cos(t1));
                    float x01 = (float) (r0 * Math.sin(t1));
                    float z01 = (float) (-r0 * Math.cos(t1));

                    builder.addVertex(x00, y0, z00).setUv(u0, v0).setColor(colWhite);
                    builder.addVertex(x10, y1, z10).setUv(u0, v1).setColor(colWhite);
                    builder.addVertex(x11, y1, z11).setUv(u1, v1).setColor(colWhite);
                    builder.addVertex(x01, y0, z01).setUv(u1, v0).setColor(colWhite);
                }
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> label, 40, mesh.vertexBuffer());
            }
        } catch (Exception e) {
            LOGGER.error("[Wish] Failed to build sky dome buffer", e);
            return null;
        }
    }

    public static boolean renderCustomSky(RenderTarget renderTarget) {
        int mode = ModConfig.INSTANCE.customSkyMode;
        if (!ensureProceduralSkyLoaded(mode)) {
            return false;
        }
        try {
            GpuTextureView colorView = renderTarget.getColorTextureView();
            GpuTextureView depthView = renderTarget.getDepthTextureView();

            Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
            if (ModConfig.INSTANCE.customSkyRotate) {
                double angle = ((System.currentTimeMillis() % 600000L) / 600000.0) * (Math.PI * 2.0);
                mv.rotateY((float) angle);
            }
            GpuBufferSlice transformSlice = RenderSystem.getDynamicUniforms().writeTransform(mv);
            var indexBufferObj = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuSampler sampler = (highQualitySampler != null) ? highQualitySampler : skyTexture.getSampler();

            int idxCount = skyDomeQuadCount * 6;
            GpuBuffer idxBuf = indexBufferObj.getBuffer(idxCount);

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Wish Custom Sky",
                    colorView,
                    Optional.empty(),
                    depthView,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(RenderPipelines.END_SKY);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transformSlice);
                pass.bindTexture("Sampler0", skyTexture.getTextureView(), sampler);
                pass.setVertexBuffer(0, skyDomeBuffer.slice());
                pass.setIndexBuffer(idxBuf, indexBufferObj.type());
                pass.drawIndexed(idxCount, 1, 0, 0, 0);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("[Wish] Error rendering procedural sky", e);
            return false;
        }
    }

    private static GpuSampler createHighQualitySampler(GpuDevice device) {
        try {
            int maxAniso = Math.min(8, Math.max(1, device.getDeviceInfo().limits().maxAnisotropy()));
            return device.createSampler(
                AddressMode.REPEAT,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                maxAniso,
                OptionalDouble.empty()
            );
        } catch (Exception e) {
            LOGGER.warn("[Wish] Falling back to cached trilinear sampler", e);
            return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
        }
    }

    private static NativeImage downsampleMip(NativeImage src, int dstW, int dstH) {
        NativeImage dst = new NativeImage(NativeImage.Format.RGBA, dstW, dstH, false);
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        for (int y = 0; y < dstH; y++) {
            int y0 = Math.min(srcH - 1, y * 2);
            int y1 = Math.min(srcH - 1, y0 + 1);
            for (int x = 0; x < dstW; x++) {
                int x0 = Math.min(srcW - 1, x * 2);
                int x1 = Math.min(srcW - 1, x0 + 1);
                int c00 = src.getPixel(x0, y0);
                int c10 = src.getPixel(x1, y0);
                int c01 = src.getPixel(x0, y1);
                int c11 = src.getPixel(x1, y1);

                int a = (((c00 >>> 24) & 0xFF) + ((c10 >>> 24) & 0xFF) + ((c01 >>> 24) & 0xFF) + ((c11 >>> 24) & 0xFF) + 2) >> 2;
                int r = averageGammaChannel((c00 >>> 16) & 0xFF, (c10 >>> 16) & 0xFF, (c01 >>> 16) & 0xFF, (c11 >>> 16) & 0xFF);
                int g = averageGammaChannel((c00 >>> 8) & 0xFF, (c10 >>> 8) & 0xFF, (c01 >>> 8) & 0xFF, (c11 >>> 8) & 0xFF);
                int b = averageGammaChannel(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF);
                dst.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return dst;
    }

    private static int averageGammaChannel(int v0, int v1, int v2, int v3) {
        double meanSq = (v0 * v0 + v1 * v1 + v2 * v2 + v3 * v3) * 0.25;
        return Math.min(255, (int) Math.round(Math.sqrt(meanSq)));
    }

    private static double fbm3(double x, double y, double z, int octaves) {
        double sum = 0.0;
        double amp = 0.5;
        double freq = 1.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * valueNoise3(x * freq, y * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    private static double valueNoise3(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fy = y - iy;
        double fz = z - iz;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uy = fy * fy * (3.0 - 2.0 * fy);
        double uz = fz * fz * (3.0 - 2.0 * fz);

        double n000 = hashNorm3(ix, iy, iz);
        double n100 = hashNorm3(ix + 1, iy, iz);
        double n010 = hashNorm3(ix, iy + 1, iz);
        double n110 = hashNorm3(ix + 1, iy + 1, iz);
        double n001 = hashNorm3(ix, iy, iz + 1);
        double n101 = hashNorm3(ix + 1, iy, iz + 1);
        double n011 = hashNorm3(ix, iy + 1, iz + 1);
        double n111 = hashNorm3(ix + 1, iy + 1, iz + 1);

        double x00 = n000 + ux * (n100 - n000);
        double x10 = n010 + ux * (n110 - n010);
        double x01 = n001 + ux * (n101 - n001);
        double x11 = n011 + ux * (n111 - n011);

        double y0 = x00 + uy * (x10 - x00);
        double y1 = x01 + uy * (x11 - x01);
        return y0 + uz * (y1 - y0);
    }

    private static double hashNorm3(int x, int y, int z) {
        return (hash3i(x, y, z) & 0xFFFF) / 65535.0;
    }

    private static int hash3i(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static int packAbgr(float r, float g, float b) {
        return (0xFF << 24)
            | (clamp255(b * 255.0f) << 16)
            | (clamp255(g * 255.0f) << 8)
            | clamp255(r * 255.0f);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static final class CustomSkyGpuTexture extends AbstractTexture {
        CustomSkyGpuTexture(GpuDevice device, NativeImage baseImage) {
            int w = baseImage.getWidth();
            int h = baseImage.getHeight();
            int minDim = Math.min(w, h);
            int mipLevels = 1;
            while (mipLevels < MAX_MIP_LEVELS && (minDim >> mipLevels) >= 2) {
                mipLevels++;
            }

            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
            this.texture = device.createTexture(() -> "Wish Custom Sky", usage, GpuFormat.RGBA8_UNORM, w, h, 1, mipLevels);
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);

            CommandEncoder encoder = device.createCommandEncoder();
            NativeImage current = baseImage;
            for (int mip = 0; mip < mipLevels; mip++) {
                encoder.writeToTexture(this.texture, current, mip, 0, 0, 0);
                if (mip + 1 < mipLevels) {
                    NativeImage next = downsampleMip(current, this.texture.getWidth(mip + 1), this.texture.getHeight(mip + 1));
                    current.close();
                    current = next;
                }
            }
            current.close();
        }
    }
}
