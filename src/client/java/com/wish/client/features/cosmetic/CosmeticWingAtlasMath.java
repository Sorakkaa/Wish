package com.wish.client.features.cosmetic;

public final class CosmeticWingAtlasMath {
    public static final int CELL_SIZE = 48;
    public static final int COLUMNS = 5;
    public static final int ROWS = 2;
    public static final int ATLAS_WIDTH = COLUMNS * CELL_SIZE;
    public static final int ATLAS_HEIGHT = ROWS * CELL_SIZE;

    private static final float UV_PAD_U = 0.25f / ATLAS_WIDTH;
    private static final float UV_PAD_V = 0.25f / ATLAS_HEIGHT;

    private CosmeticWingAtlasMath() {
    }

    public static void getWingUv(int wingId, float[] outU0V0U1V1) {
        int idx = Math.max(0, Math.min(9, wingId - 1));
        int col = idx % COLUMNS;
        int row = idx / COLUMNS;
        outU0V0U1V1[0] = (float) col / COLUMNS + UV_PAD_U;
        outU0V0U1V1[1] = (float) row / ROWS + UV_PAD_V;
        outU0V0U1V1[2] = (float) (col + 1) / COLUMNS - UV_PAD_U;
        outU0V0U1V1[3] = (float) (row + 1) / ROWS - UV_PAD_V;
    }

    public static int sampleWingAtlasAbgr(int x, int y) {
        if (x < 0 || x >= ATLAS_WIDTH || y < 0 || y >= ATLAS_HEIGHT) {
            return 0;
        }
        int col = x / CELL_SIZE;
        int row = y / CELL_SIZE;
        int wingId = row * COLUMNS + col + 1;
        int px = x % CELL_SIZE;
        int py = y % CELL_SIZE;
        if (px == 0 || px == CELL_SIZE - 1 || py == 0 || py == CELL_SIZE - 1) {
            return 0;
        }
        int argb = switch (wingId) {
            case 1 -> sampleCrimsonDemon(px, py);
            case 2 -> samplePhoenixFlame(px, py);
            case 3 -> sampleGoldenValkyrie(px, py);
            case 4 -> sampleEmeraldRunic(px, py);
            case 5 -> sampleFrostCrystal(px, py);
            case 6 -> sampleSapphireCyber(px, py);
            case 7 -> sampleVoidRift(px, py);
            case 8 -> sampleSakuraSpirit(px, py);
            case 9 -> sampleCelestialPearl(px, py);
            case 10 -> sampleShadowObsidian(px, py);
            default -> 0;
        };
        return CosmeticAtlasMath.argbToAbgr(argb);
    }

    private static int sampleCrimsonDemon(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        float topEdge = 0.42f - 0.30f * (float) Math.sin(Math.pow(u, 0.72) * Math.PI * 0.92);
        float botEdge = 0.62f + 0.16f * u - 0.14f * (float) Math.abs(Math.sin(u * Math.PI * 3.0));
        if (u < 0.04f || u > 0.94f || v < topEdge || v > botEdge) {
            return 0;
        }
        boolean outline = v - topEdge < 0.035f || botEdge - v < 0.035f || u > 0.91f;
        float rib1 = distToSeg(u, v, 0.05f, 0.42f, 0.48f, 0.14f);
        float rib2 = distToSeg(u, v, 0.48f, 0.14f, 0.92f, 0.24f);
        float rib3 = distToSeg(u, v, 0.48f, 0.14f, 0.68f, 0.70f);
        float rib4 = distToSeg(u, v, 0.28f, 0.28f, 0.36f, 0.66f);
        boolean bone = rib1 < 0.028f || rib2 < 0.024f || rib3 < 0.020f || rib4 < 0.018f;
        if (outline || bone) {
            if (distToSeg(u, v, 0.46f, 0.10f, 0.50f, 0.18f) < 0.035f) {
                return 0xFFFF3864;
            }
            return (v - topEdge < 0.025f && u < 0.50f) ? 0xFF3A0E1B : 0xFF220810;
        }
        float shade = (v - topEdge) / Math.max(0.05f, botEdge - topEdge);
        if ((px + py) % 7 == 0 && shade < 0.65f) {
            return 0xFFFF4D78;
        }
        return shade < 0.35f ? 0xFFE6204B : (shade < 0.70f ? 0xFFB01238 : 0xFF700B24);
    }

    private static int samplePhoenixFlame(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        if (u < 0.04f) {
            return 0;
        }
        float[][] plumes = {
                { 0.06f, 0.46f, 0.76f, 0.08f, 0.075f },
                { 0.08f, 0.49f, 0.92f, 0.22f, 0.072f },
                { 0.10f, 0.52f, 0.88f, 0.40f, 0.068f },
                { 0.10f, 0.56f, 0.74f, 0.58f, 0.062f },
                { 0.08f, 0.60f, 0.54f, 0.74f, 0.054f }
        };
        float bestD = 9.0f;
        float bestW = 0.0f;
        for (float[] p : plumes) {
            float d = distToSeg(u, v, p[0], p[1], p[2], p[3]);
            float w = p[4] * (1.0f - u * 0.35f);
            if (d < w && (bestW == 0.0f || d / w < bestD / bestW)) {
                bestD = d;
                bestW = w;
            }
        }
        if (bestW == 0.0f) {
            return 0;
        }
        if (bestD > bestW * 0.74f) {
            return 0xFF4A1205;
        }
        if (u > 0.66f) {
            return ((px + py) % 2 == 0) ? 0xFFFFEA58 : 0xFFFFB824;
        }
        return u > 0.34f ? 0xFFFF6B1A : 0xFFD92B0C;
    }

    private static int sampleGoldenValkyrie(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        if (u < 0.04f) {
            return 0;
        }
        boolean shoulder = (float) Math.hypot((u - 0.18f) / 0.16f, (v - 0.50f) / 0.18f) < 1.0f;
        float p1 = distToSeg(u, v, 0.18f, 0.42f, 0.90f, 0.10f);
        float p2 = distToSeg(u, v, 0.20f, 0.48f, 0.84f, 0.28f);
        float p3 = distToSeg(u, v, 0.20f, 0.54f, 0.72f, 0.46f);
        float p4 = distToSeg(u, v, 0.18f, 0.60f, 0.56f, 0.64f);
        float minP = Math.min(Math.min(p1, p2), Math.min(p3, p4));
        float maxW = 0.068f * (1.0f - u * 0.32f);
        if (!shoulder && minP > maxW) {
            return 0;
        }
        if (shoulder) {
            float d = (float) Math.hypot((u - 0.18f) / 0.16f, (v - 0.50f) / 0.18f);
            if (d > 0.84f) {
                return 0xFF5C3E08;
            }
            return d < 0.42f ? 0xFFFFF6C2 : 0xFFD99B1C;
        }
        if (minP > maxW * 0.72f) {
            return 0xFF5C3E08;
        }
        return u > 0.66f ? 0xFFFFF4AD : (minP < maxW * 0.35f ? 0xFFFFD84D : 0xFFE3A824);
    }

    private static int sampleEmeraldRunic(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        float ringD = Math.abs((float) Math.hypot(u - 0.24f, v - 0.48f) - 0.16f);
        boolean ring = ringD < 0.032f;
        float c1 = Math.abs(u - 0.58f) * 1.4f + Math.abs(v - 0.22f);
        float c2 = Math.abs(u - 0.74f) * 1.3f + Math.abs(v - 0.46f);
        float c3 = Math.abs(u - 0.54f) * 1.4f + Math.abs(v - 0.72f);
        float minC = Math.min(c1, Math.min(c2, c3));
        if (!ring && minC > 0.18f) {
            return 0;
        }
        if (ring) {
            return ringD < 0.015f ? 0xFFC2FFE2 : 0xFF147A4C;
        }
        if (minC > 0.145f) {
            return 0xFF093824;
        }
        return minC < 0.065f ? 0xFFB8FFDD : 0xFF2EF29D;
    }

    private static int sampleFrostCrystal(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        if (u < 0.04f) {
            return 0;
        }
        float s1 = distToSeg(u, v, 0.05f, 0.44f, 0.90f, 0.10f);
        float s2 = distToSeg(u, v, 0.05f, 0.48f, 0.94f, 0.38f);
        float s3 = distToSeg(u, v, 0.05f, 0.52f, 0.82f, 0.68f);
        float s4 = distToSeg(u, v, 0.05f, 0.56f, 0.60f, 0.86f);
        float b1 = distToSeg(u, v, 0.56f, 0.24f, 0.64f, 0.12f);
        float b2 = distToSeg(u, v, 0.62f, 0.42f, 0.72f, 0.52f);
        float minS = Math.min(Math.min(Math.min(s1, s2), Math.min(s3, s4)), Math.min(b1, b2));
        float maxW = 0.062f * (1.0f - u * 0.55f);
        if (minS > maxW) {
            return 0;
        }
        if (minS > maxW * 0.70f) {
            return 0xFF0E3A4D;
        }
        return (u > 0.70f || minS < maxW * 0.28f) ? 0xFFE8FDFF : 0xFF66EEFF;
    }

    private static int sampleSapphireCyber(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        if (u < 0.04f || u > 0.95f) {
            return 0;
        }
        boolean pod = u < 0.30f && v > 0.32f && v < 0.64f;
        float b1 = distToSeg(u, v, 0.24f, 0.38f, 0.90f, 0.16f);
        float b2 = distToSeg(u, v, 0.26f, 0.46f, 0.92f, 0.36f);
        float b3 = distToSeg(u, v, 0.26f, 0.54f, 0.86f, 0.56f);
        float b4 = distToSeg(u, v, 0.24f, 0.60f, 0.74f, 0.74f);
        float minB = Math.min(Math.min(b1, b2), Math.min(b3, b4));
        if (!pod && minB > 0.048f) {
            return 0;
        }
        if (pod) {
            boolean podEdge = u < 0.07f || v < 0.35f || v > 0.61f || u > 0.27f;
            if (podEdge) {
                return 0xFF141B30;
            }
            return (Math.abs(v - 0.48f) < 0.04f) ? 0xFF70B2FF : 0xFF2A3B66;
        }
        if (minB > 0.032f) {
            return 0xFF102454;
        }
        return minB < 0.015f ? 0xFFC2E2FF : 0xFF4D88FF;
    }

    private static int sampleVoidRift(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        if (u < 0.04f) {
            return 0;
        }
        float c1 = distToSeg(u, v, 0.10f, 0.46f, 0.42f, 0.10f);
        float c2 = distToSeg(u, v, 0.24f, 0.52f, 0.68f, 0.18f);
        float c3 = distToSeg(u, v, 0.18f, 0.60f, 0.62f, 0.76f);
        float minC = Math.min(c1, Math.min(c2, c3));
        float maxW = 0.078f * (1.0f - Math.abs(v - 0.46f) * 0.45f);
        if (minC > maxW) {
            return 0;
        }
        if (minC > maxW * 0.75f) {
            return 0xFF1E0838;
        }
        if ((px * 7 + py * 11) % 23 == 0) {
            return 0xFFF5DBFF;
        }
        return minC < maxW * 0.30f ? 0xFFD084FF : 0xFF7324C9;
    }

    private static int sampleSakuraSpirit(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        float dUpper = (float) Math.hypot((u - 0.46f) / 0.42f, (v - 0.32f) / 0.24f);
        float dLower = (float) Math.hypot((u - 0.36f) / 0.28f, (v - 0.66f) / 0.22f);
        float dMask = Math.min(dUpper, dLower);
        if (u < 0.04f || dMask > 1.0f) {
            return 0;
        }
        if (dMask > 0.86f) {
            return 0xFF5E1B3A;
        }
        float vein1 = distToSeg(u, v, 0.06f, 0.44f, 0.72f, 0.24f);
        float vein2 = distToSeg(u, v, 0.06f, 0.48f, 0.54f, 0.70f);
        if (vein1 < 0.018f || vein2 < 0.018f) {
            return 0xFF8C2A58;
        }
        if (Math.hypot(u - 0.64f, v - 0.28f) < 0.06f || Math.hypot(u - 0.48f, v - 0.68f) < 0.05f) {
            return 0xFFFFEBF4;
        }
        return dMask < 0.48f ? 0xFFFFB0D4 : 0xFFFF7EB6;
    }

    private static int sampleCelestialPearl(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        if (u < 0.04f) {
            return 0;
        }
        float upper = distToSeg(u, v, 0.06f, 0.42f, 0.92f, 0.14f);
        float lower = distToSeg(u, v, 0.06f, 0.56f, 0.74f, 0.78f);
        float minD = Math.min(upper, lower);
        float maxW = 0.145f * (1.0f - u * 0.42f);
        if (minD > maxW) {
            return 0;
        }
        if (minD > maxW * 0.80f) {
            return 0xFF7A8CA8;
        }
        int feather = (int) (v * 24.0f - u * 4.0f);
        if (feather % 4 == 0 && u > 0.26f) {
            return 0xFFB8CAE6;
        }
        return u > 0.68f ? 0xFFFFFFFF : (minD < maxW * 0.35f ? 0xFFF2F7FF : 0xFFD9E6FA);
    }

    private static int sampleShadowObsidian(int px, int py) {
        float u = px / 47.0f;
        float v = py / 47.0f;
        if (u < 0.04f) {
            return 0;
        }
        float t1 = distToSeg(u, v, 0.06f, 0.40f, 0.86f, 0.28f);
        float t2 = distToSeg(u, v, 0.12f, 0.48f, 0.76f, 0.62f);
        float t3 = distToSeg(u, v, 0.10f, 0.56f, 0.56f, 0.84f);
        float minT = Math.min(t1, Math.min(t2, t3));
        float maxW = 0.088f * (1.0f - u * 0.52f);
        if (minT > maxW) {
            return 0;
        }
        if (minT > maxW * 0.74f) {
            return 0xFF0A0810;
        }
        return u > 0.68f ? 0xFF6B628A : (minT < maxW * 0.35f ? 0xFF342C48 : 0xFF1A1526);
    }

    private static float distToSeg(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax;
        float vy = by - ay;
        float wx = px - ax;
        float wy = py - ay;
        float c2 = vx * vx + vy * vy;
        float t = c2 > 1e-6f ? Math.max(0.0f, Math.min(1.0f, (wx * vx + wy * vy) / c2)) : 0.0f;
        return (float) Math.hypot(px - (ax + t * vx), py - (ay + t * vy));
    }
}
