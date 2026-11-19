package com.wish.client.features.cosmetic;

import com.wish.client.color.ColorUtils;
import com.wish.client.color.CustomPlayerManager;
import com.wish.client.config.ModConfig;
import net.minecraft.util.Mth;

import java.util.List;

public final class PlayerCosmeticsManager {
    public static final int COUNT_PER_CATEGORY = 10;
    public static final int DEFAULT_ACCENT_RGB = 0xFF55FF;

    public static final List<String> WING_NAMES = List.of(
            "None",
            "Demon Wings",
            "Eclipse Seraph",
            "Dragon Wings",
            "Butterfly Wings",
            "Cyber Neon",
            "Frost Crystal",
            "Phoenix Flame",
            "Void Rift",
            "Sakura Spirit",
            "Mecha Valkyrie"
    );

    public static final List<String> HALO_NAMES = List.of(
            "None",
            "Angel Ring",
            "Void Crown",
            "Starlight",
            "Sakura Wreath",
            "Runic Sigil",
            "Solar Flare",
            "Frost Diadem",
            "Demon Horns",
            "Cyber Visor",
            "Eclipse Halo"
    );

    public static final List<String> PARTICLE_NAMES = List.of(
            "None",
            "Arcane Orbs",
            "Sakura Petals",
            "Void Flames",
            "Starlight Sparks",
            "Musical Notes",
            "Frost Crystals",
            "Heart Aura",
            "Storm Sparks",
            "Solar Embers",
            "Emerald Shards"
    );

    private static final int[] WING_DEFAULT_RGB = {
            DEFAULT_ACCENT_RGB,
            0xE0245E, 0x8A5CFF, 0xFF6B1A, 0x55E6FF, 0xB845FF,
            0x7DF3FF, 0xFF4E1A, 0xA64DFF, 0xFF7EB6, 0xFFD147
    };
    private static final int[] HALO_DEFAULT_RGB = {
            DEFAULT_ACCENT_RGB,
            0xFFE66D, 0x9D4EDD, 0x64DFDF, 0xFF85B3, 0x00F5D4,
            0xFF9F1C, 0x80EDFF, 0xFF2A55, 0x00F0FF, 0xB980FF
    };
    private static final int[] PARTICLE_DEFAULT_RGB = {
            DEFAULT_ACCENT_RGB,
            0xB86BFF, 0xFF99C8, 0xC04DFF, 0x72F2EB, 0xFF65A3,
            0x8CF4FF, 0xFF5C93, 0x5CE1FF, 0xFF8C24, 0x2EF29D
    };

    private static final ThreadLocal<int[]> PREVIEW_IGNORE_SIZE_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private PlayerCosmeticsManager() {
    }

    public static void beginPreviewIgnoreSize() {
        PREVIEW_IGNORE_SIZE_DEPTH.get()[0]++;
    }

    public static void endPreviewIgnoreSize() {
        int[] depth = PREVIEW_IGNORE_SIZE_DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }

    public static boolean isPreviewIgnoringSize() {
        return PREVIEW_IGNORE_SIZE_DEPTH.get()[0] > 0;
    }

    public record CosmeticLoadout(
            int wingId,
            int haloId,
            int particleId,
            int accentRgb,
            boolean customColor
    ) {
        public static final CosmeticLoadout NONE = new CosmeticLoadout(0, 0, 0, DEFAULT_ACCENT_RGB, false);

        public boolean isEmpty() {
            return wingId == 0 && haloId == 0 && particleId == 0;
        }
    }

    public static int clampId(int rawId) {
        return Math.max(0, Math.min(COUNT_PER_CATEGORY, rawId));
    }

    public static String getWingName(int id) {
        return WING_NAMES.get(clampId(id));
    }

    public static String getHaloName(int id) {
        return HALO_NAMES.get(clampId(id));
    }

    public static String getParticleName(int id) {
        return PARTICLE_NAMES.get(clampId(id));
    }

    public static int getWingDefaultRgb(int wingId) {
        return WING_DEFAULT_RGB[clampId(wingId)];
    }

    public static int getHaloDefaultRgb(int haloId) {
        return HALO_DEFAULT_RGB[clampId(haloId)];
    }

    public static int getParticleDefaultRgb(int particleId) {
        return PARTICLE_DEFAULT_RGB[clampId(particleId)];
    }

    public static int getDefaultAccentRgb(int wingId, int haloId, int particleId) {
        int w = clampId(wingId);
        if (w > 0) return WING_DEFAULT_RGB[w];
        int h = clampId(haloId);
        if (h > 0) return HALO_DEFAULT_RGB[h];
        int p = clampId(particleId);
        if (p > 0) return PARTICLE_DEFAULT_RGB[p];
        return DEFAULT_ACCENT_RGB;
    }

    public static CosmeticLoadout resolveLoadout(
            boolean isLocalPlayer,
            CustomPlayerManager.CustomPlayerData customData,
            ModConfig cfg
    ) {
        if (cfg == null) {
            return CosmeticLoadout.NONE;
        }
        boolean previewBypass = isPreviewIgnoringSize() && isLocalPlayer;
        int visibility = cfg.cosmeticVisibility;
        if (!previewBypass && (visibility == 2 || (visibility == 1 && !isLocalPlayer))) {
            return CosmeticLoadout.NONE;
        }

        if (isLocalPlayer) {
            int w = clampId(cfg.selectedWing);
            int h = clampId(cfg.selectedHalo);
            int p = clampId(cfg.selectedParticle);
            if (w == 0 && h == 0 && p == 0) {
                return CosmeticLoadout.NONE;
            }
            return new CosmeticLoadout(w, h, p, getDefaultAccentRgb(w, h, p), false);
        }

        if (customData == null) {
            return CosmeticLoadout.NONE;
        }
        int w = clampId(customData.wingId());
        int h = clampId(customData.haloId());
        int p = clampId(customData.particleId());
        if (w == 0 && h == 0 && p == 0) {
            return CosmeticLoadout.NONE;
        }
        String rawHex = customData.cosmeticColor();
        int parsed = ColorUtils.parseHexRgb(rawHex, -1);
        boolean hasCustom = parsed >= 0;
        int accent = hasCustom ? parsed : getDefaultAccentRgb(w, h, p);
        return new CosmeticLoadout(w, h, p, accent, hasCustom);
    }

    public static float computeWingFlapAngleRad(
            int wingId,
            float ageInTicks,
            float walkSpeed,
            boolean crouching,
            boolean fallFlying
    ) {
        int id = clampId(wingId);
        if (id == 0) {
            return 0.0f;
        }
        float speedFactor = Math.min(1.5f, Math.max(0.0f, walkSpeed));
        float freq = switch (id) {
            case 4, 9 -> 0.34f + speedFactor * 0.20f;
            case 5, 10 -> 0.14f + speedFactor * 0.08f;
            case 2, 8 -> 0.16f + speedFactor * 0.10f;
            case 6 -> 0.17f + speedFactor * 0.10f;
            case 3, 7 -> 0.19f + speedFactor * 0.15f;
            default -> 0.21f + speedFactor * 0.16f;
        };
        float amp = switch (id) {
            case 4 -> 0.42f + speedFactor * 0.20f;
            case 9 -> 0.34f + speedFactor * 0.18f;
            case 5, 10 -> 0.10f + speedFactor * 0.06f;
            case 2, 6, 8 -> 0.16f + speedFactor * 0.10f;
            case 3, 7 -> 0.30f + speedFactor * 0.22f;
            default -> 0.24f + speedFactor * 0.18f;
        };
        if (fallFlying) {
            freq *= 0.65f;
            amp *= 0.45f;
        } else if (crouching) {
            amp *= 0.72f;
        }
        float sweepBase = switch (id) {
            case 5, 10 -> 0.32f;
            case 2, 6, 8 -> 0.38f;
            case 3, 7 -> 0.48f;
            default -> 0.42f;
        };
        if (fallFlying) {
            sweepBase += 0.35f;
        }
        return sweepBase + Mth.sin(ageInTicks * freq) * amp;
    }

    public static float computeWingTipFoldRad(int wingId, float baseFlapRad, float ageInTicks) {
        int id = clampId(wingId);
        float lagFreq = (id == 4 || id == 9) ? 0.34f : 0.21f;
        float lagPhase = Mth.sin(ageInTicks * lagFreq - 0.75f);
        return switch (id) {
            case 5, 10 -> 0.12f + lagPhase * 0.06f;
            case 2, 6, 8 -> 0.16f + lagPhase * 0.10f;
            case 4, 9 -> 0.26f + lagPhase * 0.24f;
            default -> 0.24f + Math.max(-0.20f, lagPhase * 0.22f) + (baseFlapRad - 0.4f) * 0.25f;
        };
    }

    public static float computeHaloFloatOffset(int haloId, float ageInTicks) {
        int id = clampId(haloId);
        if (id == 0) {
            return 0.0f;
        }
        if (id == 8) {
            return Mth.sin(ageInTicks * 0.09f) * 0.008f;
        }
        float speed = (id == 5 || id == 9 || id == 10) ? 0.16f : 0.11f;
        float amp = (id == 2 || id == 7) ? 0.022f : 0.032f;
        return Mth.sin(ageInTicks * speed) * amp;
    }

    public static void computeParticleOrbitPoint(
            int particleId,
            int index,
            int totalCount,
            float ageInTicks,
            float[] outXyzAlpha
    ) {
        int id = clampId(particleId);
        if (id == 0 || totalCount <= 0 || outXyzAlpha == null || outXyzAlpha.length < 4) {
            if (outXyzAlpha != null && outXyzAlpha.length >= 4) {
                outXyzAlpha[0] = 0.0f;
                outXyzAlpha[1] = 0.0f;
                outXyzAlpha[2] = 0.0f;
                outXyzAlpha[3] = 0.0f;
            }
            return;
        }
        float phase = (float) index / (float) totalCount;
        float tau = (float) (Math.PI * 2.0);
        switch (id) {
            case 1 -> {
                float dir = (index % 2 == 0) ? 1.0f : -1.0f;
                float angle = phase * tau * 2.0f + ageInTicks * 0.10f * dir;
                float radius = 0.62f + 0.14f * Mth.sin(ageInTicks * 0.12f + index * 1.1f);
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = 0.24f + Mth.sin(ageInTicks * 0.09f + phase * tau * 2.0f) * 0.56f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = 0.88f;
            }
            case 2 -> {
                float fallCycle = (phase + ageInTicks * 0.016f) % 1.0f;
                float angle = phase * tau * 2.0f + ageInTicks * 0.055f + Mth.sin(ageInTicks * 0.08f + index) * 0.40f;
                float radius = 0.58f + 0.24f * Mth.sin(fallCycle * (float) Math.PI + index * 0.4f);
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = -0.45f + fallCycle * 1.38f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = Math.max(0.0f, Math.min(1.0f, Mth.sin(fallCycle * (float) Math.PI) * 0.94f));
            }
            case 3 -> {
                float rise = (phase + ageInTicks * 0.024f) % 1.0f;
                float angle = phase * tau * 3.0f + ageInTicks * 0.12f;
                float radius = 0.56f + (1.0f - rise) * 0.24f;
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = 0.88f - rise * 1.32f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = Math.max(0.0f, Math.min(1.0f, Mth.sin(rise * (float) Math.PI) * 0.96f));
            }
            case 4 -> {
                float angle = phase * tau * 2.0f + ageInTicks * 0.075f;
                float pulse = 0.5f + 0.5f * Mth.sin(ageInTicks * 0.24f + index * 1.7f);
                float radius = 0.64f + 0.18f * Mth.cos(phase * tau * 3.0f + ageInTicks * 0.05f);
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = 0.20f + 0.58f * Mth.sin(phase * tau * 4.0f + ageInTicks * 0.065f);
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = 0.30f + 0.70f * pulse;
            }
            case 5 -> {
                float beat = (phase + ageInTicks * 0.015f) % 1.0f;
                float angle = phase * tau * 2.0f + ageInTicks * 0.085f;
                float radius = 0.64f + 0.12f * Mth.sin(phase * tau * 3.0f + ageInTicks * 0.1f);
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = 0.80f - beat * 1.22f + Mth.abs(Mth.sin(ageInTicks * 0.20f + index)) * 0.07f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = Math.max(0.0f, Math.min(1.0f, Mth.sin(beat * (float) Math.PI) * 0.95f));
            }
            case 6 -> {
                float fallCycle = (phase + ageInTicks * 0.014f) % 1.0f;
                float angle = phase * tau * 2.0f - ageInTicks * 0.048f + Mth.cos(ageInTicks * 0.07f + index) * 0.32f;
                float radius = 0.60f + 0.20f * Mth.cos(fallCycle * (float) Math.PI + index * 0.6f);
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = -0.48f + fallCycle * 1.42f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = Math.max(0.0f, Math.min(1.0f, Mth.sin(fallCycle * (float) Math.PI) * 0.95f));
            }
            case 7 -> {
                float rise = (phase + ageInTicks * 0.015f) % 1.0f;
                float angle = phase * tau * 2.0f + ageInTicks * 0.055f;
                float sway = Mth.sin(ageInTicks * 0.14f + index * 1.3f) * 0.10f;
                float radius = 0.60f + 0.14f * Mth.sin(rise * (float) Math.PI) + sway;
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = 0.82f - rise * 1.32f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = Math.max(0.0f, Math.min(1.0f, Mth.sin(rise * (float) Math.PI) * 0.96f));
            }
            case 8 -> {
                float dir = (index % 2 == 0) ? 1.0f : -1.0f;
                float jitter = Mth.sin(ageInTicks * 0.45f + index * 2.3f) * 0.24f;
                float angle = phase * tau * 3.0f + ageInTicks * 0.15f * dir + jitter;
                float radius = 0.62f + 0.18f * Mth.cos(ageInTicks * 0.32f + index * 1.7f);
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = 0.22f + Mth.sin(phase * tau * 3.0f + ageInTicks * 0.18f) * 0.56f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = 0.45f + 0.55f * (0.5f + 0.5f * Mth.sin(ageInTicks * 0.40f + index * 2.1f));
            }
            case 9 -> {
                float rise = (phase + ageInTicks * 0.022f) % 1.0f;
                float angle = phase * tau * 2.0f + ageInTicks * 0.10f;
                float radius = 0.52f + rise * 0.28f;
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = 0.86f - rise * 1.36f;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = Math.max(0.0f, Math.min(1.0f, Mth.sin(rise * (float) Math.PI) * 0.96f));
            }
            case 10 -> {
                boolean upperRing = (index % 2 == 0);
                float angle = phase * tau * 2.0f + ageInTicks * (upperRing ? 0.09f : -0.075f);
                float radius = upperRing ? 0.66f : 0.58f;
                float tiltY = Mth.sin(angle + (upperRing ? 0.4f : -0.6f)) * 0.38f;
                outXyzAlpha[0] = Mth.cos(angle) * radius;
                outXyzAlpha[1] = (upperRing ? 0.06f : 0.46f) + tiltY;
                outXyzAlpha[2] = Mth.sin(angle) * radius;
                outXyzAlpha[3] = 0.84f + 0.14f * Mth.sin(ageInTicks * 0.18f + index);
            }
            default -> {
                outXyzAlpha[0] = 0.0f;
                outXyzAlpha[1] = 0.0f;
                outXyzAlpha[2] = 0.0f;
                outXyzAlpha[3] = 0.0f;
            }
        }
    }
}
