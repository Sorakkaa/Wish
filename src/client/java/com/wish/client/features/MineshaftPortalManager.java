package com.wish.client.features;

import com.wish.client.color.ColorUtils;
import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MineshaftPortalManager {

    public static final String DEFAULT_GLOW_HEX = "#AA55FF";
    public static final int DEFAULT_GLOW_RGB = 0xAA55FF;

    private static final double PORTAL_HALF_WIDTH = 0.55;
    private static final double COMPANION_RADIUS_XZ_SQ = 0.81;
    private static final double COMPANION_MAX_BELOW_Y = 3.0;
    private static final double COMPANION_MAX_ABOVE_Y = 0.6;
    private static final double PORTAL_DEDUP_DIST_SQ = 2.25;
    private static final int SCAN_INTERVAL_TICKS = 2;

    private static final List<ArmorStand> SCAN_STANDS = new ArrayList<>();
    private static final List<AABB> ACTIVE_PORTAL_BOXES = new ArrayList<>();
    private static final List<AABB> ACTIVE_INNER_BOXES = new ArrayList<>();

    private static volatile String cachedGlowHex = null;
    private static volatile int cachedGlowRgb = DEFAULT_GLOW_RGB;

    private static ClientLevel lastLevel = null;
    private static int scanTickCounter = SCAN_INTERVAL_TICKS;

    private MineshaftPortalManager() {}

    public static void reset() {
        SCAN_STANDS.clear();
        ACTIVE_PORTAL_BOXES.clear();
        ACTIVE_INNER_BOXES.clear();
        lastLevel = null;
        scanTickCounter = SCAN_INTERVAL_TICKS;
    }

    public static boolean isMineshaftPortalName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return false;
        }
        String clean = SkyblockDetector.stripColorCodes(rawName).trim().toLowerCase(Locale.ROOT);
        return clean.contains("mineshaft portal");
    }

    public static boolean isClickToEnterLabel(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return false;
        }
        String clean = SkyblockDetector.stripColorCodes(rawName).trim().toLowerCase(Locale.ROOT);
        return clean.startsWith("click to enter");
    }

    public static int getGlowColorRgb() {
        return parseGlowColorRgb(ModConfig.INSTANCE.mineshaftPortalGlowColor);
    }

    public static int parseGlowColorRgb(String rawHex) {
        String cached = cachedGlowHex;
        if (rawHex != null && rawHex.equals(cached)) {
            return cachedGlowRgb;
        }
        int parsed = ColorUtils.parseHexRgb(rawHex, DEFAULT_GLOW_RGB);
        cachedGlowHex = rawHex;
        cachedGlowRgb = parsed;
        return parsed;
    }

    public static int brightenRgb(int rgb, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int nr = Math.min(255, Math.round(r + (255 - r) * t));
        int ng = Math.min(255, Math.round(g + (255 - g) * t));
        int nb = Math.min(255, Math.round(b + (255 - b) * t));
        return (nr << 16) | (ng << 8) | nb;
    }

    public static AABB resolvePortalBounds(
            double x,
            double nameStandY,
            double z,
            double bbHeight,
            boolean marker,
            Double clickStandY,
            Double companionMinY,
            Double companionMaxY
    ) {
        double minY;
        double maxY;
        if (companionMinY != null && companionMaxY != null && (companionMaxY - companionMinY) >= 1.2) {
            minY = companionMinY - 0.05;
            maxY = companionMaxY + 0.05;
        } else if (!marker && bbHeight >= 1.2) {
            minY = nameStandY - 0.05;
            maxY = nameStandY + bbHeight + 0.05;
        } else if (!marker && bbHeight > 0.1) {
            double refY = clickStandY != null ? Math.min(nameStandY, clickStandY) : nameStandY;
            minY = refY - 0.70;
            maxY = minY + 1.95;
        } else {
            double topY = (clickStandY != null ? Math.min(nameStandY, clickStandY) : nameStandY) - 0.08;
            maxY = topY;
            minY = topY - 1.85;
        }
        return new AABB(
                x - PORTAL_HALF_WIDTH,
                minY,
                z - PORTAL_HALF_WIDTH,
                x + PORTAL_HALF_WIDTH,
                maxY,
                z + PORTAL_HALF_WIDTH
        );
    }

    public static void onClientTick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null || !ModConfig.INSTANCE.enableMineshaftPortalGlow) {
            if (!ACTIVE_PORTAL_BOXES.isEmpty() || lastLevel != null) {
                reset();
            }
            return;
        }

        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }

        scanTickCounter++;
        if (scanTickCounter >= SCAN_INTERVAL_TICKS) {
            scanTickCounter = 0;
            refreshPortals(mc.level);
        }

        if (!ACTIVE_PORTAL_BOXES.isEmpty()) {
            emitPortalGizmos();
        }
    }

    private static void refreshPortals(ClientLevel level) {
        SCAN_STANDS.clear();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName() || stand.getCustomName() == null) {
                continue;
            }
            if (isMineshaftPortalName(stand.getCustomName().getString())) {
                addDeduplicatedAnchor(stand);
            }
        }

        ACTIVE_PORTAL_BOXES.clear();
        ACTIVE_INNER_BOXES.clear();
        if (SCAN_STANDS.isEmpty()) {
            return;
        }

        for (int i = 0; i < SCAN_STANDS.size(); i++) {
            processPortalAnchor(level, SCAN_STANDS.get(i));
        }
    }

    private static void addDeduplicatedAnchor(ArmorStand candidate) {
        for (int i = 0; i < SCAN_STANDS.size(); i++) {
            ArmorStand existing = SCAN_STANDS.get(i);
            if (existing.distanceToSqr(candidate) <= PORTAL_DEDUP_DIST_SQ) {
                return;
            }
        }
        SCAN_STANDS.add(candidate);
    }

    private static void processPortalAnchor(ClientLevel level, ArmorStand anchor) {
        Double clickStandY = null;
        Double companionMinY = null;
        Double companionMaxY = null;

        double ax = anchor.getX();
        double ay = anchor.getY();
        double az = anchor.getZ();

        for (Entity entity : level.entitiesForRendering()) {
            double dx = entity.getX() - ax;
            double dz = entity.getZ() - az;
            if (dx * dx + dz * dz > COMPANION_RADIUS_XZ_SQ) {
                continue;
            }
            double dy = entity.getY() - ay;
            if (dy < -COMPANION_MAX_BELOW_Y || dy > COMPANION_MAX_ABOVE_Y) {
                continue;
            }

            if (entity instanceof ArmorStand stand) {
                if (stand.hasCustomName() && stand.getCustomName() != null
                        && isClickToEnterLabel(stand.getCustomName().getString())) {
                    clickStandY = stand.getY();
                }
                if (!stand.isMarker() && stand.getBbHeight() >= 1.2) {
                    double sMinY = stand.getY();
                    double sMaxY = sMinY + stand.getBbHeight();
                    companionMinY = companionMinY == null ? sMinY : Math.min(companionMinY, sMinY);
                    companionMaxY = companionMaxY == null ? sMaxY : Math.max(companionMaxY, sMaxY);
                }
            }
        }

        AABB outer = resolvePortalBounds(
                ax, ay, az, anchor.getBbHeight(), anchor.isMarker(), clickStandY, companionMinY, companionMaxY
        );
        ACTIVE_PORTAL_BOXES.add(outer);
        ACTIVE_INNER_BOXES.add(outer.deflate(0.06));
    }

    private static void emitPortalGizmos() {
        int rgb = getGlowColorRgb() & 0x00FFFFFF;
        int innerRgb = brightenRgb(rgb, 0.45f);
        int outerStroke = 0xFF000000 | rgb;
        int outerFill = 0x3A000000 | rgb;
        int innerStroke = 0xEE000000 | innerRgb;

        GizmoStyle outerStyle = GizmoStyle.strokeAndFill(outerStroke, 4.0f, outerFill);
        GizmoStyle innerStyle = GizmoStyle.stroke(innerStroke, 2.0f);

        for (int i = 0; i < ACTIVE_PORTAL_BOXES.size(); i++) {
            Gizmos.cuboid(ACTIVE_PORTAL_BOXES.get(i), outerStyle);
            Gizmos.cuboid(ACTIVE_INNER_BOXES.get(i), innerStyle);
        }
    }
}
