package com.wish.client.features.visual;

public final class CatPawTrailMath {

    public static final String DEFAULT_COLOR_HEX = "#FFB0DE";
    public static final int DEFAULT_COLOR_RGB = 0xFFB0DE;

    public static final float FIXED_GLOW_MULT = 1.0f;
    public static final float FIXED_SCALE = 1.0f;

    public static final int ATLAS_SIZE = 256;
    public static final int TRAIL_ATLAS_WIDTH = 512;
    public static final int TRAIL_ATLAS_HEIGHT = 512;
    public static final int QUADRANT_SIZE = 128;
    public static final int SUB_SPRITE_SIZE = 64;

    public static final float STEP_LATERAL_OFFSET = 0.18f;
    public static final float STEP_TOE_OUT_RAD = (float) Math.toRadians(7.0);
    public static final float GROUND_Y_OFFSET = 0.022f;

    private static final float COS_28_DEG = (float) Math.cos(Math.toRadians(28.0));
    private static final float SIN_28_DEG = (float) Math.sin(Math.toRadians(28.0));
    private static final float COS_8_DEG = (float) Math.cos(Math.toRadians(8.0));
    private static final float SIN_8_DEG = (float) Math.sin(Math.toRadians(8.0));
    private static final float COS_GLOSS = (float) Math.cos(-0.38);
    private static final float SIN_GLOSS = (float) Math.sin(-0.38);
    private static final float HEART_LOBE_RADIUS = (float) (Math.sqrt(2.0) * 0.25);

    private CatPawTrailMath() {
    }

    public static int resolveGlowRgb(int customRgb) {
        int rgb = customRgb & 0x00FFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int maxC = Math.max(r, Math.max(g, b));
        int minC = Math.min(r, Math.min(g, b));
        int delta = maxC - minC;
        if (delta < 18) {
            return mixRgb(rgb, 0xFF4DA6, 0.20f);
        }
        float targetSat = Math.max((float) delta / maxC, 0.70f);
        int nr = Math.min(255, Math.round(255.0f * (1.0f - targetSat * ((float) (maxC - r) / delta))));
        int ng = Math.min(255, Math.round(255.0f * (1.0f - targetSat * ((float) (maxC - g) / delta))));
        int nb = Math.min(255, Math.round(255.0f * (1.0f - targetSat * ((float) (maxC - b) / delta))));
        return (nr << 16) | (ng << 8) | nb;
    }

    public static int resolveBodyRgb(int customRgb) {
        return brightenRgb(customRgb & 0x00FFFFFF, 0.12f);
    }

    public static int resolveCoreRgb(int customRgb) {
        return brightenRgb(customRgb & 0x00FFFFFF, 0.84f);
    }

    public static float computeStepLateralOffset(boolean isLeft) {
        return isLeft ? -STEP_LATERAL_OFFSET : STEP_LATERAL_OFFSET;
    }

    public static float computeStepYawRad(float moveYawRad, boolean isLeft) {
        return moveYawRad + (isLeft ? -STEP_TOE_OUT_RAD : STEP_TOE_OUT_RAD);
    }

    public static float computeBounceScale(float ageSec, float durationSec) {
        if (ageSec <= 0.0f) {
            return 0.60f;
        }
        if (ageSec < 0.12f) {
            float t = ageSec / 0.12f;
            float eased = 1.0f - (1.0f - t) * (1.0f - t);
            return 0.60f + 0.53f * eased;
        }
        if (ageSec < 0.20f) {
            float t = (ageSec - 0.12f) / 0.08f;
            float smooth = t * t * (3.0f - 2.0f * t);
            return 1.13f - 0.13f * smooth;
        }
        float tailDuration = Math.max(0.1f, durationSec - 0.20f);
        float tailT = clamp01((ageSec - 0.20f) / tailDuration);
        return 1.00f - 0.10f * tailT;
    }

    public static float computeFootprintAlpha(float ageSec, float durationSec) {
        if (ageSec < 0.0f || durationSec <= 0.0f || ageSec >= durationSec) {
            return 0.0f;
        }
        float fadeIn = clamp01(ageSec / 0.08f);
        float lifeT = clamp01(ageSec / durationSec);
        float fadeOut = (float) Math.pow(1.0f - lifeT, 1.35f);
        return fadeIn * fadeOut;
    }

    public static float pawSignedDistance(float nx, float ny) {
        float dMain = mainPadSignedDistance(nx, ny);
        float dToeOuterLeft = sdRotatedEllipse(nx, ny, -0.45f, -0.30f, 0.120f, 0.146f, COS_28_DEG, -SIN_28_DEG);
        float dToeInnerLeft = sdRotatedEllipse(nx, ny, -0.18f, -0.50f, 0.122f, 0.149f, COS_8_DEG, -SIN_8_DEG);
        float dToeInnerRight = sdRotatedEllipse(nx, ny, 0.18f, -0.50f, 0.122f, 0.149f, COS_8_DEG, SIN_8_DEG);
        float dToeOuterRight = sdRotatedEllipse(nx, ny, 0.45f, -0.30f, 0.120f, 0.146f, COS_28_DEG, SIN_28_DEG);

        float dToes = Math.min(Math.min(dToeOuterLeft, dToeInnerLeft), Math.min(dToeInnerRight, dToeOuterRight));
        return Math.min(dMain, dToes);
    }

    public static float mainPadSignedDistance(float nx, float ny) {
        float dLeftCheek = sdAxisEllipse(nx, ny, -0.25f, 0.12f, 0.26f, 0.19f);
        float dRightCheek = sdAxisEllipse(nx, ny, 0.25f, 0.12f, 0.26f, 0.19f);
        float dBottomPillow = sdAxisEllipse(nx, ny, 0.0f, 0.18f, 0.34f, 0.14f);
        float dCenterDome = sdAxisEllipse(nx, ny, 0.0f, -0.03f, 0.22f, 0.22f);

        float blendedCheeks = smoothMin(dLeftCheek, dRightCheek, 0.11f);
        float blendedBase = smoothMin(blendedCheeks, dBottomPillow, 0.11f);
        return smoothMin(blendedBase, dCenterDome, 0.11f);
    }

    public static float shineSignedDistance(float nx, float ny) {
        float dMainOval = sdRotatedEllipse(nx, ny, -0.12f, -0.05f, 0.072f, 0.036f, COS_GLOSS, SIN_GLOSS);
        float dMainDot = (float) Math.hypot(nx + 0.21f, ny - 0.02f) - 0.021f;
        float dMainShine = Math.min(dMainOval, dMainDot);

        float dToe0 = sdAxisEllipse(nx, ny, -0.45f - 0.028f, -0.30f - 0.050f, 0.038f, 0.022f);
        float dToe1 = sdAxisEllipse(nx, ny, -0.18f - 0.028f, -0.50f - 0.050f, 0.038f, 0.022f);
        float dToe2 = sdAxisEllipse(nx, ny, 0.18f - 0.028f, -0.50f - 0.050f, 0.038f, 0.022f);
        float dToe3 = sdAxisEllipse(nx, ny, 0.45f - 0.028f, -0.30f - 0.050f, 0.038f, 0.022f);
        float dToes = Math.min(Math.min(dToe0, dToe1), Math.min(dToe2, dToe3));
        return Math.min(dMainShine, dToes);
    }

    public static int sampleAtlasPixelAbgr(int x, int y) {
        if (x < 0 || x >= ATLAS_SIZE || y < 0 || y >= ATLAS_SIZE) {
            return 0;
        }
        float alpha;
        if (y < QUADRANT_SIZE) {
            if (x < QUADRANT_SIZE) {
                float nx = toNormalizedCoord(x, QUADRANT_SIZE);
                float ny = toNormalizedCoord(y, QUADRANT_SIZE);
                alpha = sampleOuterGlowAlpha(nx, ny);
            } else {
                float nx = toNormalizedCoord(x - QUADRANT_SIZE, QUADRANT_SIZE);
                float ny = toNormalizedCoord(y, QUADRANT_SIZE);
                alpha = sampleBodyAlpha(nx, ny);
            }
        } else if (x < QUADRANT_SIZE) {
            float nx = toNormalizedCoord(x, QUADRANT_SIZE);
            float ny = toNormalizedCoord(y - QUADRANT_SIZE, QUADRANT_SIZE);
            alpha = sampleCoreAndGlossAlpha(nx, ny);
        } else {
            alpha = sampleParticleQuadrantAlpha(x - QUADRANT_SIZE, y - QUADRANT_SIZE);
        }
        int alphaByte = Math.max(0, Math.min(255, Math.round(clamp01(alpha) * 255.0f)));
        return (alphaByte << 24) | 0x00FFFFFF;
    }

    public static int sampleTrailAtlas512x256Abgr(int x, int y) {
        if (x < 0 || x >= TRAIL_ATLAS_WIDTH || y < 0 || y >= TRAIL_ATLAS_HEIGHT) {
            return 0;
        }
        if (y >= ATLAS_SIZE) {
            int cellX = x / QUADRANT_SIZE;
            int cellY = y / QUADRANT_SIZE;
            float nx = toNormalizedCoord(x % QUADRANT_SIZE, QUADRANT_SIZE);
            float ny = toNormalizedCoord(y % QUADRANT_SIZE, QUADRANT_SIZE);
            float borderFade = borderFalloff(nx, ny, 0.80f, 0.95f);
            if (borderFade <= 0.0f) {
                return 0;
            }
            float alpha = switch (cellY * 4 + cellX) {
                case 8 -> sampleClawSlashAlpha(nx, ny);
                case 9 -> sampleFlamePrintAlpha(nx, ny);
                case 10 -> sampleRuneSigilAlpha(nx, ny);
                case 11 -> sampleLightningPrintAlpha(nx, ny);
                case 12 -> sampleVoidSpiralAlpha(nx, ny);
                case 13 -> sampleWingPrintAlpha(nx, ny);
                case 14 -> sampleCrescentPrintAlpha(nx, ny);
                default -> 0.0f;
            };
            int alphaByte = Math.max(0, Math.min(255, Math.round(clamp01(alpha * borderFade) * 255.0f)));
            return (alphaByte << 24) | 0x00FFFFFF;
        }
        if (x < ATLAS_SIZE) {
            if (x >= QUADRANT_SIZE + SUB_SPRITE_SIZE && y >= QUADRANT_SIZE + SUB_SPRITE_SIZE) {
                float nx = toNormalizedCoord(x - (QUADRANT_SIZE + SUB_SPRITE_SIZE), SUB_SPRITE_SIZE);
                float ny = toNormalizedCoord(y - (QUADRANT_SIZE + SUB_SPRITE_SIZE), SUB_SPRITE_SIZE);
                float alpha = sampleSnowflakeAlpha(nx, ny) * borderFalloff(nx, ny, 0.76f, 0.90f);
                int alphaByte = Math.max(0, Math.min(255, Math.round(clamp01(alpha) * 255.0f)));
                return (alphaByte << 24) | 0x00FFFFFF;
            }
            return sampleAtlasPixelAbgr(x, y);
        }
        int rx = x - ATLAS_SIZE;
        int cellX = rx / QUADRANT_SIZE;
        int cellY = y / QUADRANT_SIZE;
        float nx = toNormalizedCoord(rx % QUADRANT_SIZE, QUADRANT_SIZE);
        float ny = toNormalizedCoord(y % QUADRANT_SIZE, QUADRANT_SIZE);
        float borderFade = borderFalloff(nx, ny, 0.80f, 0.95f);
        if (borderFade <= 0.0f) {
            return 0;
        }
        float alpha;
        if (cellX == 0 && cellY == 0) {
            alpha = sampleSakuraBlossomAlpha(nx, ny);
        } else if (cellX == 1 && cellY == 0) {
            alpha = sampleStarlightPrintAlpha(nx, ny);
        } else if (cellX == 0 && cellY == 1) {
            alpha = sampleHeartPrintAlpha(nx, ny);
        } else {
            alpha = sampleSnowflakeAlpha(nx, ny);
        }
        int alphaByte = Math.max(0, Math.min(255, Math.round(clamp01(alpha * borderFade) * 255.0f)));
        return (alphaByte << 24) | 0x00FFFFFF;
    }

    private static float sampleSakuraBlossomAlpha(float nx, float ny) {
        float r = (float) Math.hypot(nx, ny);
        if (r > 0.88f) {
            return 0.0f;
        }
        double theta = Math.atan2(ny, nx);
        float petalShape = 0.44f + 0.24f * (float) Math.cos(theta * 5.0);
        float notch = 0.07f * (float) Math.pow(Math.max(0.0, Math.cos(theta * 5.0)), 6.0);
        float d = r - (petalShape - notch);
        float body = smoothstep(0.045f, -0.045f, d);
        float glow = (float) Math.exp(-(Math.max(0.0f, d) * Math.max(0.0f, d)) / (2.0f * 0.09f * 0.09f)) * 0.55f;
        float centerHole = smoothstep(0.11f, 0.17f, r);
        float centerDot = smoothstep(0.08f, 0.03f, r) * 0.90f;
        return clamp01(Math.max(Math.max(body * centerHole, centerDot), glow));
    }

    private static float sampleStarlightPrintAlpha(float nx, float ny) {
        float ax = Math.abs(nx);
        float ay = Math.abs(ny);
        float astroid = (float) (Math.pow(ax, 0.58) + Math.pow(ay, 0.58));
        float star = smoothstep(0.92f, 0.62f, astroid);
        float r = (float) Math.hypot(nx, ny);
        float core = smoothstep(0.32f, 0.0f, r);
        float glow = (float) Math.exp(-r * r * 5.2f) * 0.52f;
        return clamp01(Math.max(star, Math.max(core, glow)));
    }

    private static float sampleHeartPrintAlpha(float nx, float ny) {
        float dHeart = sdHeart(nx * 1.12f, ny * 1.12f);
        float core = smoothstep(0.04f, -0.04f, dHeart);
        float outDist = Math.max(0.0f, dHeart);
        float glow = (float) Math.exp(-(outDist * outDist) / (2.0f * 0.11f * 0.11f)) * 0.60f;
        return clamp01(Math.max(core, glow));
    }

    private static float sampleSnowflakeAlpha(float nx, float ny) {
        float r = (float) Math.hypot(nx, ny);
        if (r > 0.92f) {
            return 0.0f;
        }
        double angle = Math.atan2(ny, nx);
        double sector = Math.PI / 3.0;
        double localAngle = Math.abs(((angle % sector) + sector) % sector - sector * 0.5);
        float ax = (float) (r * Math.cos(localAngle));
        float ay = (float) (r * Math.sin(localAngle));
        float arm = smoothstep(0.070f, 0.025f, ay) * smoothstep(0.86f, 0.68f, ax);
        float branch = smoothstep(0.055f, 0.018f, Math.abs(ax - 0.42f - ay * 0.85f))
                * smoothstep(0.36f, 0.24f, ay) * smoothstep(0.02f, 0.08f, ay);
        float core = smoothstep(0.24f, 0.06f, r);
        float glow = (float) Math.exp(-r * r * 4.2f) * 0.48f;
        return clamp01(Math.max(Math.max(arm, branch), Math.max(core, glow)));
    }

    private static float sampleOuterGlowAlpha(float nx, float ny) {
        float borderFade = borderFalloff(nx, ny, 0.78f, 0.92f);
        if (borderFade <= 0.0f) {
            return 0.0f;
        }
        float dPaw = pawSignedDistance(nx, ny);
        float outsideDist = Math.max(0.0f, dPaw);
        float halo = (float) Math.exp(-(outsideDist * outsideDist) / (2.0f * 0.105f * 0.105f));
        float softFill = dPaw <= 0.0f ? 0.95f : 0.92f * halo;
        float rimBoost = (float) Math.exp(-(dPaw * dPaw) / (2.0f * 0.032f * 0.032f)) * 0.60f;
        return clamp01((softFill + rimBoost) * borderFade);
    }

    private static float sampleBodyAlpha(float nx, float ny) {
        float borderFade = borderFalloff(nx, ny, 0.88f, 0.96f);
        if (borderFade <= 0.0f) {
            return 0.0f;
        }
        float dPaw = pawSignedDistance(nx, ny);
        float mask = smoothstep(0.018f, -0.018f, dPaw);
        float pillowShade = 0.88f + 0.12f * clamp01(-dPaw / 0.14f);
        return clamp01(mask * pillowShade * borderFade);
    }

    private static float sampleCoreAndGlossAlpha(float nx, float ny) {
        float borderFade = borderFalloff(nx, ny, 0.88f, 0.96f);
        if (borderFade <= 0.0f) {
            return 0.0f;
        }
        float dPaw = pawSignedDistance(nx, ny);
        float innerCore = smoothstep(-0.02f, -0.15f, dPaw) * 0.65f;
        float dShine = shineSignedDistance(nx, ny);
        float glossSpecks = smoothstep(0.014f, -0.014f, dShine) * 0.92f;
        return clamp01(Math.max(innerCore, glossSpecks) * borderFade);
    }

    private static float sampleParticleQuadrantAlpha(int qx, int qy) {
        int subX = qx / SUB_SPRITE_SIZE;
        int subY = qy / SUB_SPRITE_SIZE;
        float nx = toNormalizedCoord(qx % SUB_SPRITE_SIZE, SUB_SPRITE_SIZE);
        float ny = toNormalizedCoord(qy % SUB_SPRITE_SIZE, SUB_SPRITE_SIZE);
        float borderFade = borderFalloff(nx, ny, 0.76f, 0.90f);
        if (borderFade <= 0.0f) {
            return 0.0f;
        }

        if (subX == 0 && subY == 0) {
            float r = (float) Math.hypot(nx, ny);
            float delta = r - 0.68f;
            float ring = (float) Math.exp(-(delta * delta) / (2.0f * 0.068f * 0.068f));
            return clamp01(ring * borderFade);
        }
        if (subX == 1 && subY == 0) {
            float ax = Math.abs(nx);
            float ay = Math.abs(ny);
            float tipX = ax + 2.8f * ay;
            float tipY = ay + 2.8f * ax;
            float dStar = Math.min(tipX, tipY) - 0.62f;
            float starCore = smoothstep(0.06f, -0.06f, dStar);
            float r = (float) Math.hypot(nx, ny);
            float starGlow = (float) Math.exp(-r * r * 6.5f) * 0.45f;
            return clamp01(Math.max(starCore, starGlow) * borderFade);
        }
        if (subX == 0 && subY == 1) {
            float dHeart = sdHeart(nx, ny);
            float heartCore = smoothstep(0.04f, -0.04f, dHeart);
            float outDist = Math.max(0.0f, dHeart);
            float heartGlow = (float) Math.exp(-(outDist * outDist) / (2.0f * 0.10f * 0.10f)) * 0.55f;
            return clamp01(Math.max(heartCore, heartGlow) * borderFade);
        }
        return 0.0f;
    }

    private static float sdHeart(float nx, float ny) {
        float px = Math.abs(nx) * 1.55f;
        float py = (-ny + 0.38f) * 1.55f;
        if (py + px > 1.0f) {
            float dx = px - 0.25f;
            float dy = py - 0.75f;
            return (float) Math.hypot(dx, dy) - HEART_LOBE_RADIUS;
        }
        float d1 = (float) Math.hypot(px, py - 1.0f);
        float m = 0.5f * Math.max(px + py, 0.0f);
        float d2 = (float) Math.hypot(px - m, py - m);
        return Math.min(d1, d2) * Math.signum(px - py);
    }

    private static float sdAxisEllipse(float px, float py, float cx, float cy, float rx, float ry) {
        return sdRotatedEllipse(px, py, cx, cy, rx, ry, 1.0f, 0.0f);
    }

    private static float sdRotatedEllipse(
            float px,
            float py,
            float cx,
            float cy,
            float rx,
            float ry,
            float cosA,
            float sinA
    ) {
        float dx = px - cx;
        float dy = py - cy;
        float lx = dx * cosA + dy * sinA;
        float ly = -dx * sinA + dy * cosA;
        float qx = lx / rx;
        float qy = ly / ry;
        float k0 = (float) Math.hypot(qx, qy);
        if (k0 < 1e-5f) {
            return -Math.min(rx, ry);
        }
        float gx = qx / rx;
        float gy = qy / ry;
        float k1 = (float) Math.hypot(gx, gy);
        return k0 * (k0 - 1.0f) / k1;
    }

    private static float smoothMin(float a, float b, float k) {
        float h = clamp01(0.5f + 0.5f * (b - a) / k);
        return lerp(b, a, h) - k * h * (1.0f - h);
    }

    private static float toNormalizedCoord(int pixelIndex, int span) {
        return ((pixelIndex + 0.5f) / span) * 2.0f - 1.0f;
    }

    private static float borderFalloff(float nx, float ny, float fadeStart, float fadeEnd) {
        float maxCoord = Math.max(Math.abs(nx), Math.abs(ny));
        return 1.0f - smoothstep(fadeStart, fadeEnd, maxCoord);
    }

    private static float sampleClawSlashAlpha(float nx, float ny) {
        float u = (nx + ny) * 0.7071f;
        float v = (-nx + ny) * 0.7071f;
        float best = 0.0f;
        for (int i = -1; i <= 1; i++) {
            float off = i * 0.28f;
            float lenLim = (i == 0) ? 0.68f : 0.56f;
            float dv = Math.abs(v - off);
            float taper = smoothstep(lenLim, 0.0f, Math.abs(u));
            float w = 0.075f * taper;
            best = Math.max(best, smoothstep(w + 0.03f, w * 0.35f, dv) * taper);
        }
        return clamp01(best);
    }

    private static float sampleFlamePrintAlpha(float nx, float ny) {
        float w = 0.44f * clamp01(1.0f - (0.35f - ny) * 0.65f);
        float dBody = (float) Math.hypot(nx / Math.max(0.08f, w), (ny - 0.14f) / 0.58f);
        float wave = 0.09f * (float) Math.sin(ny * 8.0f + nx * 3.0f);
        float outer = smoothstep(0.95f, 0.30f, dBody + wave);
        float core = smoothstep(0.50f, 0.0f, (float) Math.hypot(nx, ny - 0.22f));
        return clamp01(Math.max(outer, core));
    }

    private static float sampleRuneSigilAlpha(float nx, float ny) {
        float r = (float) Math.hypot(nx, ny);
        float ring = smoothstep(0.08f, 0.02f, Math.abs(r - 0.62f));
        float hex = Math.max(Math.abs(nx) * 0.866f + Math.abs(ny) * 0.5f, Math.abs(ny));
        float hexRing = smoothstep(0.07f, 0.02f, Math.abs(hex - 0.46f));
        float diag1 = smoothstep(0.06f, 0.015f, Math.abs(nx - ny)) * smoothstep(0.62f, 0.52f, r);
        float diag2 = smoothstep(0.06f, 0.015f, Math.abs(nx + ny)) * smoothstep(0.62f, 0.52f, r);
        float center = smoothstep(0.18f, 0.04f, r);
        return clamp01(Math.max(ring, Math.max(hexRing, Math.max(diag1, Math.max(diag2, center)))));
    }

    private static float sampleLightningPrintAlpha(float nx, float ny) {
        float r = (float) Math.hypot(nx, ny);
        float ring = smoothstep(0.06f, 0.015f, Math.abs(r - 0.66f)) * 0.65f;
        float d1 = segmentDist(nx, ny, 0.16f, -0.60f, -0.18f, 0.02f);
        float d2 = segmentDist(nx, ny, -0.18f, 0.02f, 0.14f, 0.02f);
        float d3 = segmentDist(nx, ny, 0.14f, 0.02f, -0.18f, 0.62f);
        float bolt = smoothstep(0.10f, 0.03f, Math.min(d1, Math.min(d2, d3)));
        return clamp01(Math.max(ring, bolt));
    }

    private static float sampleVoidSpiralAlpha(float nx, float ny) {
        float r = (float) Math.hypot(nx, ny);
        if (r > 0.78f) {
            return 0.0f;
        }
        double angle = Math.atan2(ny, nx) + r * 6.5;
        float arm = (float) Math.abs(Math.sin(angle));
        float spiral = smoothstep(0.45f, 0.08f, arm) * smoothstep(0.76f, 0.15f, r);
        float core = smoothstep(0.24f, 0.05f, r);
        return clamp01(Math.max(spiral, core));
    }

    private static float sampleWingPrintAlpha(float nx, float ny) {
        float ax = Math.abs(nx);
        float d1 = segmentDist(ax, ny, 0.04f, 0.25f, 0.58f, -0.32f);
        float d2 = segmentDist(ax, ny, 0.04f, 0.02f, 0.48f, -0.10f);
        float d3 = segmentDist(ax, ny, 0.04f, -0.18f, 0.34f, 0.22f);
        float wing = smoothstep(0.11f, 0.03f, Math.min(d1, Math.min(d2, d3)));
        return clamp01(wing);
    }

    private static float sampleCrescentPrintAlpha(float nx, float ny) {
        float dOuter = (float) Math.hypot(nx, ny);
        float dInner = (float) Math.hypot(nx - 0.24f, ny + 0.08f);
        float crescent = smoothstep(0.70f, 0.60f, dOuter) * smoothstep(0.40f, 0.50f, dInner);
        float star = smoothstep(0.18f, 0.03f, (float) Math.hypot(nx - 0.28f, ny + 0.10f));
        return clamp01(Math.max(crescent, star));
    }

    private static float segmentDist(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax;
        float vy = by - ay;
        float wx = px - ax;
        float wy = py - ay;
        float c2 = vx * vx + vy * vy;
        float t = c2 > 1e-6f ? clamp01((wx * vx + wy * vy) / c2) : 0.0f;
        return (float) Math.hypot(px - (ax + t * vx), py - (ay + t * vy));
    }

    private static int brightenRgb(int rgb, float amount) {
        return mixRgb(rgb, 0xFFFFFF, clamp01(amount));
    }

    private static int mixRgb(int rgbA, int rgbB, float t) {
        float w = clamp01(t);
        int rA = (rgbA >> 16) & 0xFF;
        int gA = (rgbA >> 8) & 0xFF;
        int bA = rgbA & 0xFF;
        int rB = (rgbB >> 16) & 0xFF;
        int gB = (rgbB >> 8) & 0xFF;
        int bB = rgbB & 0xFF;
        int nr = Math.min(255, Math.round(lerp(rA, rB, w)));
        int ng = Math.min(255, Math.round(lerp(gA, gB, w)));
        int nb = Math.min(255, Math.round(lerp(bA, bB, w)));
        return (nr << 16) | (ng << 8) | nb;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
