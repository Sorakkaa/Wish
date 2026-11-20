package com.wish.client.features.cosmetic;

public final class CosmeticAtlasMath {
    public static final int CELL_SIZE = 128;
    public static final int COLUMNS = 4;
    public static final int ROWS = 4;
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
    public static final int CELL_EMBER = 12;
    public static final int CELL_CRESCENT = 13;
    public static final int CELL_RING_RUNE = 14;
    public static final int CELL_STAR_4PT = 15;

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
            case CELL_EMBER -> sampleEmberArgb(u, v);
            case CELL_CRESCENT -> sampleCrescentArgb(u, v);
            case CELL_RING_RUNE -> sampleRingRuneArgb(u, v);
            case CELL_STAR_4PT -> sampleStar4PtArgb(u, v);
            default -> 0;
        };
        return argbToAbgr(argb);
    }

    private static int sampleFeatherBladeArgb(float u, float v) {
        int gx = Math.min(31, Math.max(0, (int) (u * 32.0f)));
        int gy = Math.min(31, Math.max(0, (int) (v * 32.0f)));
        float qu = (gx + 0.5f) / 32.0f;
        float qv = (gy + 0.5f) / 32.0f;
        if (gy == 0 || gy == 31) {
            return 0;
        }
        float arch = (float) Math.sin(Math.pow(clamp01(qv), 0.72) * Math.PI);
        float halfW = 0.09f + 0.36f * arch;
        float dist = Math.abs(qu - 0.50f);
        if (dist > halfW) {
            return 0;
        }
        boolean border = (halfW - dist) < 0.045f || gy == 1 || gy == 30;
        boolean quill = dist < 0.038f;
        boolean barb = ((gx + gy * 2) % 5 == 0) && dist > 0.06f;
        float dither = (((gx + gy) & 1) == 0) ? 0.04f : -0.04f;
        float lum = border ? 0.50f : (quill ? 1.0f : (barb ? 0.78f : clamp01(0.88f + dither)));
        int c = clampByte(lum * 255.0f);
        return (0xFF << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleBatMembraneArgb(float u, float v) {
        int gx = Math.min(31, Math.max(0, (int) (u * 32.0f)));
        int gy = Math.min(31, Math.max(0, (int) (v * 32.0f)));
        float qu = (gx + 0.5f) / 32.0f;
        float qv = (gy + 0.5f) / 32.0f;

        float jag = (gx % 4 == 0) ? 0.032f : 0.0f;
        float archBottom = 0.96f - 0.30f * (float) Math.sin(Math.PI * qu) - jag;
        if (qv > archBottom) {
            return 0;
        }

        boolean bottomRim = (archBottom - qv) < 0.055f;
        boolean veinA = Math.abs(qv - (0.18f + qu * 0.44f)) < 0.030f;
        boolean veinB = Math.abs(qv - (0.56f - qu * 0.38f)) < 0.030f;
        float dither = (((gx + gy) & 1) == 0) ? 0.045f : -0.045f;
        float shade = 0.72f + 0.24f * clamp01(qv / Math.max(0.1f, archBottom)) + dither;
        float lum = bottomRim ? 0.46f : ((veinA || veinB) ? Math.min(1.0f, shade + 0.16f) : clamp01(shade));
        int c = clampByte(lum * 255.0f);
        return (0xFF << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleButterflyLobeArgb(float u, float v) {
        int gx = Math.min(31, Math.max(0, (int) (u * 32.0f)));
        int gy = Math.min(31, Math.max(0, (int) (v * 32.0f)));
        float qu = (gx + 0.5f) / 32.0f;
        float qv = (gy + 0.5f) / 32.0f;

        float dx = Math.max(0.0f, qu);
        float dy = qv - 0.50f;
        float spanV = 0.28f + 0.20f * (float) Math.sin(clamp01(dx / 0.94f) * Math.PI * 0.72);
        float angle = (float) Math.atan2(dy, dx + 0.16f);
        float rNorm = (float) Math.sqrt((dx / 0.94f) * (dx / 0.94f) * 0.65f + (dy / spanV) * (dy / spanV) * 0.92f);
        float border = 1.0f + 0.045f * (float) Math.cos(angle * 10.0f);
        if (rNorm > border) {
            return 0;
        }

        boolean outerRim = rNorm > border * 0.88f;
        boolean vein = Math.abs((float) Math.sin(angle * 7.5f)) > 0.90f;
        boolean pearlSpot = !outerRim && rNorm > border * 0.72f && Math.cos(angle * 10.0f) > 0.45f;
        float dither = (((gx + gy) & 1) == 0) ? 0.04f : -0.04f;
        float lum = outerRim ? 0.44f : (pearlSpot ? 1.0f : (vein ? 0.62f : clamp01(0.86f + (1.0f - rNorm) * 0.14f + dither)));
        int c = clampByte(lum * 255.0f);
        return (0xFF << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleEnergyBladeArgb(float u, float v) {
        int gx = Math.min(31, Math.max(0, (int) (u * 32.0f)));
        int gy = Math.min(31, Math.max(0, (int) (v * 32.0f)));
        float qu = (gx + 0.5f) / 32.0f;
        float qv = (gy + 0.5f) / 32.0f;
        if (gx == 0 || gx == 31) {
            return 0;
        }
        float rootTaper = clamp01(qu / 0.12f);
        float tipTaper = clamp01((1.0f - qu) / 0.36f);
        float halfW = 0.08f + 0.36f * Math.min(rootTaper, tipTaper);
        float distV = Math.abs(qv - 0.5f);
        if (distV > halfW) {
            return 0;
        }
        boolean rim = (halfW - distV) < 0.045f;
        boolean core = distV < halfW * 0.38f;
        boolean notch = (gx % 6 == 0) && distV > halfW * 0.55f;
        float lum = core ? 1.0f : (rim ? 0.58f : (notch ? 0.95f : 0.80f));
        int c = clampByte(lum * 255.0f);
        return (0xFF << 24) | (c << 16) | (c << 8) | c;
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

    private static int sampleEmberArgb(float u, float v) {
        float px = (u - 0.5f) * 2.0f;
        float py = (v - 0.5f) * 2.0f;
        float w = 0.42f * clamp01(1.0f - (0.35f - py) * 0.65f);
        float dBody = (float) Math.sqrt((px / Math.max(0.08f, w)) * (px / Math.max(0.08f, w)) + ((py - 0.14f) / 0.58f) * ((py - 0.14f) / 0.58f));
        float wave = 0.08f * (float) Math.sin(py * 8.0f + px * 3.0f);
        float tip = smoothstep(0.95f, 0.25f, dBody + wave);
        float core = smoothstep(0.55f, 0.0f, (float) Math.sqrt(px * px + (py - 0.22f) * (py - 0.22f)));
        float alpha = clamp01(tip * 0.92f + core * 0.35f);
        int c = clampByte((0.78f + 0.22f * core) * 255.0f);
        return (clampByte(alpha * 255.0f) << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleCrescentArgb(float u, float v) {
        float px = (u - 0.5f) * 2.0f;
        float py = (v - 0.5f) * 2.0f;
        float dOuter = (float) Math.sqrt(px * px + py * py);
        float dInner = (float) Math.sqrt((px - 0.26f) * (px - 0.26f) + (py + 0.10f) * (py + 0.10f));
        float mask = smoothstep(0.76f, 0.68f, dOuter) * smoothstep(0.46f, 0.56f, dInner);
        float lum = 0.85f + 0.15f * smoothstep(0.68f, 0.50f, dOuter);
        int c = clampByte(lum * 255.0f);
        return (clampByte(mask * 255.0f) << 24) | (c << 16) | (c << 8) | c;
    }

    private static int sampleRingRuneArgb(float u, float v) {
        float px = (u - 0.5f) * 2.0f;
        float py = (v - 0.5f) * 2.0f;
        float r = (float) Math.sqrt(px * px + py * py);
        float ring = smoothstep(0.11f, 0.03f, Math.abs(r - 0.56f));
        float diag1 = smoothstep(0.08f, 0.02f, Math.abs(px - py)) * smoothstep(0.62f, 0.50f, r);
        float diag2 = smoothstep(0.08f, 0.02f, Math.abs(px + py)) * smoothstep(0.62f, 0.50f, r);
        float center = smoothstep(0.22f, 0.04f, r);
        float alpha = clamp01(Math.max(ring, Math.max(diag1, Math.max(diag2, center))));
        return (clampByte(alpha * 255.0f) << 24) | 0x00FFFFFF;
    }

    private static int sampleStar4PtArgb(float u, float v) {
        float px = Math.abs((u - 0.5f) * 2.0f);
        float py = Math.abs((v - 0.5f) * 2.0f);
        float arm = (float) Math.pow(px, 0.55) + (float) Math.pow(py, 0.55);
        float mask = smoothstep(0.92f, 0.72f, arm);
        float core = smoothstep(0.32f, 0.0f, (float) Math.sqrt(px * px + py * py));
        float alpha = clamp01(Math.max(mask, core));
        return (clampByte(alpha * 255.0f) << 24) | 0x00FFFFFF;
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
