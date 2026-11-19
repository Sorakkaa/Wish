package com.wish.client.features.cosmetic;

public final class CosmeticAtlasMath {
    public static final int CELL_SIZE = 128;
    public static final int COLUMNS = 4;
    public static final int ROWS = 3;
    public static final int ATLAS_WIDTH = COLUMNS * CELL_SIZE;
    public static final int ATLAS_HEIGHT = ROWS * CELL_SIZE;
    public static final int CELL_COUNT = COLUMNS * ROWS;

    public static final int CELL_FEATHER_WING = 0;
    public static final int CELL_RIB_WING = 1;
    public static final int CELL_ENERGY_WING = 2;
    public static final int CELL_HALO_BAND = 3;
    public static final int CELL_ORB = 4;
    public static final int CELL_PETAL = 5;
    public static final int CELL_SPARK = 6;
    public static final int CELL_NOTE = 7;
    public static final int CELL_HEART = 8;
    public static final int CELL_SNOWFLAKE = 9;
    public static final int CELL_LIGHTNING = 10;
    public static final int CELL_DIAMOND = 11;

    public static final float SOLID_WHITE_U = 0.5f / COLUMNS;
    public static final float SOLID_WHITE_V = 1.5f / ROWS;

    private static final float UV_PAD_U = 2.0f / ATLAS_WIDTH;
    private static final float UV_PAD_V = 2.0f / ATLAS_HEIGHT;

    private CosmeticAtlasMath() {
    }

    public static void getCellUv(int cellIndex, float[] outU0V0U1V1) {
        int clamped = Math.max(0, Math.min(CELL_COUNT - 1, cellIndex));
        int col = clamped % COLUMNS;
        int row = clamped / COLUMNS;
        outU0V0U1V1[0] = (float) col / COLUMNS + UV_PAD_U;
        outU0V0U1V1[1] = (float) row / ROWS + UV_PAD_V;
        outU0V0U1V1[2] = (float) (col + 1) / COLUMNS - UV_PAD_U;
        outU0V0U1V1[3] = (float) (row + 1) / ROWS - UV_PAD_V;
    }

    public static int sampleAtlasPixelAbgr(int x, int y) {
        if (x < 0 || x >= ATLAS_WIDTH || y < 0 || y >= ATLAS_HEIGHT) {
            return 0;
        }
        int col = x / CELL_SIZE;
        int row = y / CELL_SIZE;
        int cell = row * COLUMNS + col;
        float u = (x % CELL_SIZE + 0.5f) / CELL_SIZE;
        float v = (y % CELL_SIZE + 0.5f) / CELL_SIZE;
        int argb = switch (cell) {
            case CELL_FEATHER_WING -> sampleFeatherBladeArgb(u, v);
            case CELL_RIB_WING -> sampleBatMembraneArgb(u, v);
            case CELL_ENERGY_WING -> sampleButterflyLobeArgb(u, v);
            case CELL_HALO_BAND -> sampleEnergyBladeArgb(u, v);
            case CELL_ORB -> sampleOrbArgb(u, v);
            case CELL_PETAL -> samplePetalArgb(u, v);
            case CELL_SPARK -> sampleSparkArgb(u, v);
            case CELL_NOTE -> sampleNoteArgb(u, v);
            case CELL_HEART -> sampleHeartArgb(u, v);
            case CELL_SNOWFLAKE -> sampleSnowflakeArgb(u, v);
            case CELL_LIGHTNING -> sampleLightningArgb(u, v);
            case CELL_DIAMOND -> sampleDiamondArgb(u, v);
            default -> 0;
        };
        return argbToAbgr(argb);
    }

    private static int sampleFeatherBladeArgb(float u, float v) {
        float center = 0.50f + 0.035f * (float) Math.sin(v * Math.PI);
        float tipMask = smoothstep(0.98f, 0.88f, v);
        float rootMask = smoothstep(0.01f, 0.08f, v);
        float arch = (float) Math.sin(Math.pow(clamp01(v), 0.72) * Math.PI);
        float halfW = 0.08f + 0.37f * arch;
        float dist = Math.abs(u - center);
        float mask = smoothstep(halfW, halfW - 0.04f, dist) * tipMask * rootMask;
        if (mask <= 0.01f) {
            return 0;
        }

        float shaft = smoothstep(0.045f * (1.0f - v * 0.55f), 0.010f, dist);
        float barbCoord = v * 26.0f - dist * 18.0f;
        float barbShading = 0.86f + 0.14f * (float) Math.sin(barbCoord * Math.PI * 2.0f);

        float lum = Math.min(1.0f, barbShading * (0.90f + shaft * 0.14f));
        float alpha = clamp01(mask * (0.97f - v * 0.04f));
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleBatMembraneArgb(float u, float v) {
        float archBottom = 0.96f - 0.28f * (float) Math.sin(Math.PI * u);
        float bottomMask = smoothstep(archBottom, archBottom - 0.045f, v);
        if (bottomMask <= 0.01f) {
            return 0;
        }

        float archRim = smoothstep(0.065f, 0.0f, Math.abs(archBottom - 0.02f - v));
        float veinA = smoothstep(0.032f, 0.006f, Math.abs(v - (0.16f + u * 0.44f)));
        float veinB = smoothstep(0.032f, 0.006f, Math.abs(v - (0.58f - u * 0.40f)));
        float grain = 0.78f + 0.10f * (float) Math.sin(u * Math.PI * 3.0f) * (1.0f - v);
        float lum = Math.min(1.0f, grain + archRim * 0.24f + Math.max(veinA, veinB) * 0.16f);
        float alpha = clamp01(bottomMask * Math.min(1.0f, 0.94f + archRim * 0.06f));

        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleButterflyLobeArgb(float u, float v) {
        float dx = Math.max(0.0f, u);
        float dy = v - 0.50f;
        float spanV = 0.28f + 0.20f * (float) Math.sin(clamp01(dx / 0.94f) * Math.PI * 0.72);
        float angle = (float) Math.atan2(dy, dx + 0.16f);
        float rNorm = (float) Math.sqrt((dx / 0.94f) * (dx / 0.94f) * 0.65f + (dy / spanV) * (dy / spanV) * 0.92f);
        float border = 1.0f + 0.045f * (float) Math.cos(angle * 10.0f);
        float mask = smoothstep(border, border - 0.055f, rNorm);
        if (mask <= 0.01f) {
            return 0;
        }

        float veins = Math.abs((float) Math.sin(angle * 7.5f));
        float veinShade = smoothstep(0.95f, 0.84f, veins) * 0.30f + 0.70f;
        float rimBand = smoothstep(border * 0.74f, border * 0.86f, rNorm) * smoothstep(border, border * 0.90f, rNorm);
        float pearls = (rimBand > 0.25f && Math.cos(angle * 10.0f) > 0.35f) ? 0.30f : 0.0f;
        float innerGlow = (1.0f - clamp01(rNorm / border)) * 0.24f;

        float lum = Math.min(1.0f, (0.80f + innerGlow + pearls) * veinShade);
        float alpha = clamp01(mask * (0.90f + innerGlow * 0.15f + pearls * 0.10f));
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleEnergyBladeArgb(float u, float v) {
        float rootTaper = smoothstep(0.0f, 0.10f, u);
        float tipTaper = smoothstep(1.0f, 0.62f, u);
        float halfW = 0.08f + 0.35f * Math.min(rootTaper, tipTaper);
        float distV = Math.abs(v - 0.5f);
        float shape = smoothstep(halfW, halfW - 0.05f, distV) * smoothstep(0.0f, 0.04f, u) * smoothstep(1.0f, 0.94f, u);
        if (shape <= 0.01f) {
            return 0;
        }

        float core = smoothstep(halfW * 0.42f, 0.0f, distV);
        float rim = smoothstep(halfW * 0.70f, halfW * 0.94f, distV);
        float circuit = (Math.abs((u * 10.0f) % 1.0f - 0.5f) < 0.08f) ? 0.12f : 0.0f;
        float lum = Math.min(1.0f, 0.72f + core * 0.28f + rim * 0.18f + circuit);
        float alpha = clamp01(shape * (0.86f + core * 0.14f));
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleOrbArgb(float u, float v) {
        float dx = (u - 0.5f) * 2.0f;
        float dy = (v - 0.5f) * 2.0f;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        if (r < 0.18f) {
            return 0xFFFFFFFF;
        }
        if (r >= 0.98f) {
            return 0;
        }
        float core = smoothstep(0.45f, 0.18f, r);
        float halo = (float) Math.pow(Math.max(0.0f, 1.0f - r), 1.65);
        float alpha = clamp01(halo * 0.88f + core * 0.35f);
        int c = clampByte((0.82f + 0.18f * core) * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int samplePetalArgb(float u, float v) {
        float du = (u - 0.5f) * 2.20f;
        float dv = (v - 0.54f) * 2.00f;
        float widen = 0.66f + (1.0f - v) * 0.55f;
        float d = (du * du) / (widen * widen) + (dv * dv) / 0.78f;
        float cleft = smoothstep(0.14f, 0.0f, Math.abs(u - 0.5f)) * smoothstep(0.28f, 0.06f, v);
        float mask = smoothstep(1.0f, 0.84f, d + cleft * 0.65f);
        if (mask <= 0.01f) {
            return 0;
        }
        float blush = 0.84f + 0.16f * v;
        int c = clampByte(blush * 255.0f);
        int a = clampByte(mask * 250.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleSparkArgb(float u, float v) {
        float dx = Math.abs(u - 0.5f) * 2.02f;
        float dy = Math.abs(v - 0.5f) * 2.02f;
        float astroid = (float) (Math.pow(dx, 0.55) + Math.pow(dy, 0.55));
        float star = smoothstep(1.04f, 0.65f, astroid);
        float core = smoothstep(0.35f, 0.0f, (float) Math.sqrt(dx * dx + dy * dy));
        float alpha = clamp01(Math.max(star * 0.92f, core));
        if (alpha <= 0.01f) {
            return 0;
        }
        int c = clampByte((0.84f + 0.16f * core) * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleNoteArgb(float u, float v) {
        float h1d = tiltedNoteHeadDist(u - 0.27f, v - 0.74f);
        float h2d = tiltedNoteHeadDist(u - 0.69f, v - 0.64f);
        float head1 = smoothstep(1.12f, 0.80f, h1d);
        float head2 = smoothstep(1.12f, 0.80f, h2d);
        float headGlow = Math.max(smoothstep(1.65f, 0.90f, h1d), smoothstep(1.65f, 0.90f, h2d));

        float stem1 = smoothstep(0.048f, 0.022f, Math.abs(u - 0.365f))
                * smoothstep(0.22f, 0.27f, v) * smoothstep(0.76f, 0.71f, v);
        float stem2 = smoothstep(0.048f, 0.022f, Math.abs(u - 0.785f))
                * smoothstep(0.12f, 0.17f, v) * smoothstep(0.66f, 0.61f, v);

        float beamT = clamp01((u - 0.36f) / 0.43f);
        float beamY = 0.27f - beamT * 0.10f;
        float beam = smoothstep(0.072f, 0.036f, Math.abs(v - beamY))
                * smoothstep(0.32f, 0.37f, u) * smoothstep(0.83f, 0.78f, u);
        float beamGlow = smoothstep(0.135f, 0.055f, Math.abs(v - beamY))
                * smoothstep(0.28f, 0.37f, u) * smoothstep(0.86f, 0.77f, u);

        float core = Math.max(Math.max(head1, head2), Math.max(Math.max(stem1, stem2), beam));
        float glow = Math.max(headGlow, beamGlow) * 0.36f;
        float alpha = clamp01(Math.max(core, glow));
        if (alpha <= 0.01f) {
            return 0;
        }
        float lum = 0.88f + 0.12f * core;
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleHeartArgb(float u, float v) {
        float x = Math.abs(u - 0.5f) * 2.55f;
        float y = (0.58f - v) * 2.55f;
        float y2 = y - (float) Math.sqrt(x) * 0.72f;
        float d = x * x + y2 * y2;
        float mask = smoothstep(1.06f, 0.86f, d);
        if (mask <= 0.01f) {
            return 0;
        }
        float inner = smoothstep(0.85f, 0.15f, d);
        float rim = smoothstep(0.95f, 0.72f, d) * smoothstep(0.45f, 0.78f, d);
        float lum = Math.min(1.0f, 0.80f + inner * 0.22f + rim * 0.18f);
        float alpha = clamp01(mask * (0.90f + inner * 0.10f));
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleSnowflakeArgb(float u, float v) {
        float dx = u - 0.5f;
        float dy = v - 0.5f;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        if (r > 0.48f) {
            return 0;
        }
        double angle = Math.atan2(dy, dx);
        double sector = Math.PI / 3.0;
        double localAngle = Math.abs(((angle % sector) + sector) % sector - sector * 0.5);
        float ax = (float) (r * Math.cos(localAngle));
        float ay = (float) (r * Math.sin(localAngle));

        float mainArm = smoothstep(0.032f, 0.010f, ay) * smoothstep(0.45f, 0.36f, ax);
        float branchDist = Math.abs(ax - 0.22f - ay * 0.85f);
        float branch = smoothstep(0.026f, 0.008f, branchDist) * smoothstep(0.18f, 0.12f, ay) * smoothstep(0.01f, 0.04f, ay);
        float innerStar = smoothstep(0.14f, 0.04f, r);
        float alpha = clamp01(Math.max(Math.max(mainArm, branch), innerStar));
        if (alpha <= 0.01f) {
            return 0;
        }
        float lum = 0.84f + 0.16f * Math.max(innerStar, Math.max(mainArm, branch));
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleLightningArgb(float u, float v) {
        float upperDist = pointToSegmentDist(u, v, 0.58f, 0.10f, 0.34f, 0.52f);
        float bridgeDist = pointToSegmentDist(u, v, 0.34f, 0.52f, 0.62f, 0.50f);
        float lowerDist = pointToSegmentDist(u, v, 0.62f, 0.50f, 0.40f, 0.91f);
        float minDist = Math.min(upperDist, Math.min(bridgeDist, lowerDist));
        float core = smoothstep(0.068f, 0.030f, minDist);
        float glow = smoothstep(0.155f, 0.048f, minDist) * 0.48f;
        float alpha = clamp01(Math.max(core, glow));
        if (alpha <= 0.01f) {
            return 0;
        }
        float lum = 0.82f + 0.18f * core;
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleDiamondArgb(float u, float v) {
        float dx = Math.abs(u - 0.5f) / 0.36f;
        float dy = Math.abs(v - 0.5f) / 0.46f;
        float d = dx + dy;
        float mask = smoothstep(1.0f, 0.90f, d);
        if (mask <= 0.01f) {
            return 0;
        }
        float innerFacet = smoothstep(0.55f, 0.42f, d);
        float crossSpine = smoothstep(0.06f, 0.015f, Math.min(dx, dy));
        float rim = smoothstep(0.78f, 0.92f, d) * smoothstep(1.0f, 0.94f, d);
        float lum = Math.min(1.0f, 0.72f + innerFacet * 0.18f + crossSpine * 0.16f + rim * 0.22f);
        float alpha = clamp01(mask * (0.92f + innerFacet * 0.08f));
        int c = clampByte(lum * 255.0f);
        int a = clampByte(alpha * 255.0f);
        return (a << 24) | (c << 16) | (c << 8) | c;
    }

    private static float pointToSegmentDist(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax;
        float vy = by - ay;
        float wx = px - ax;
        float wy = py - ay;
        float c2 = vx * vx + vy * vy;
        float t = c2 > 1e-6f ? clamp01((wx * vx + wy * vy) / c2) : 0.0f;
        float dx = px - (ax + t * vx);
        float dy = py - (ay + t * vy);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float tiltedNoteHeadDist(float hx, float hy) {
        float rx = hx * 0.92f + hy * 0.39f;
        float ry = -hx * 0.39f + hy * 0.92f;
        return (float) Math.sqrt((rx / 0.145f) * (rx / 0.145f) + (ry / 0.102f) * (ry / 0.102f));
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    public static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int clampByte(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
