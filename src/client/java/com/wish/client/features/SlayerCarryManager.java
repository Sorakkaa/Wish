package com.wish.client.features;

import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class SlayerCarryManager {

    public static class CarrySession {
        public String originalName;
        public int totalBosses;
        public int bossesDone;
        public CarrySession(String originalName, int total) {
            this.originalName = originalName;
            this.totalBosses = total;
            this.bossesDone = 0;
        }
    }

    private static final class BossPos {
        double x;
        double y;
        double z;

        void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final java.util.Map<String, CarrySession> trackedPlayers = new java.util.HashMap<>();

    private static final java.util.Map<Integer, String> knownBossIdToOwner = new java.util.HashMap<>();
    private static final java.util.Map<Integer, BossPos> knownBossLastPos = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> lastSpawnNotifyByOwner = new java.util.HashMap<>();
    private static final java.util.Set<Integer> knownOwnBossIds = new java.util.HashSet<>();
    private static long lastOwnCoordsNotifyTime = 0L;
    private static volatile String cachedGlowHex = null;
    private static volatile int cachedGlowRgb = 0xFF55AA;

    private static int pendingChatDelay = 0;
    private static String pendingChatMessage = null;
    private static String pendingCompletionMessage = null;
    private static int pendingCompletionDelay = 0;
    public static int showSpawnHudTicks = 0;
    public static String lastSpawnedBossOwner = "";

    public static void setTrackedPlayer(String player, int amount) {
        trackedPlayers.put(player.toLowerCase(), new CarrySession(player, amount));
        WishClient.sendLocalChatMessage("§d§l[Wish] §aSlayer carry tracker added for " + player + " (0/" + amount + " bosses).");
    }

    public static void incrementBosses(String playerRaw) {
        String playerLower = playerRaw.toLowerCase(java.util.Locale.ROOT);
        CarrySession session = trackedPlayers.get(playerLower);
        if (session != null) {
            session.bossesDone++;

            pendingChatMessage = "Boss " + session.bossesDone + "/" + session.totalBosses + " done :3";
            pendingChatDelay = 20;

            if (session.bossesDone >= session.totalBosses) {
                pendingCompletionMessage = "Carry completed for " + session.originalName + " (" + session.totalBosses + "/" + session.totalBosses + ")";
                pendingCompletionDelay = 60;
                trackedPlayers.remove(playerLower);
            }
        }
    }

    public static void reset() {
        clearTrackingState();
    }

    public static void stopTracking() {
        clearTrackingState();
        WishClient.sendLocalChatMessage("§d§l[Wish] §cSlayer carry tracking stopped for everyone.");
    }

    private static void clearTrackingState() {
        trackedPlayers.clear();
        knownBossIdToOwner.clear();
        knownBossLastPos.clear();
        lastSpawnNotifyByOwner.clear();
        knownOwnBossIds.clear();
        lastOwnCoordsNotifyTime = 0L;
        pendingChatMessage = null;
        pendingChatDelay = 0;
        pendingCompletionMessage = null;
        pendingCompletionDelay = 0;
        showSpawnHudTicks = 0;
        lastSpawnedBossOwner = "";
        HudStateContainer.updateBossState(false, null, null);
    }

    public static void stopTrackingPlayer(String player) {
        if (trackedPlayers.remove(player.toLowerCase(java.util.Locale.ROOT)) != null) {
            WishClient.sendLocalChatMessage("§d§l[Wish] §cSlayer carry tracking stopped for " + player + ".");
        } else {
            WishClient.sendLocalChatMessage("§c[Wish] No active tracker found for " + player + ".");
        }
    }

    public static int getGlowColorRgb() {
        String glow = ModConfig.INSTANCE.slayerGlowColor;
        if (glow != null && glow.equals(cachedGlowHex)) {
            return cachedGlowRgb;
        }
        cachedGlowHex = glow;
        if (glow != null && glow.length() == 7 && glow.charAt(0) == '#') {
            try {
                int parsed = Integer.parseInt(glow, 1, 7, 16);
                cachedGlowRgb = parsed;
                return parsed;
            } catch (Exception ignored) {}
        }
        cachedGlowRgb = 0xFF55AA;
        return 0xFF55AA;
    }

    public static boolean isActualBossEntity(Entity entity) {
        if (trackedPlayers.isEmpty() || knownBossIdToOwner.isEmpty() || entity.level() == null) return false;
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)
                || entity instanceof net.minecraft.world.entity.player.Player
                || entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        for (Integer bossId : knownBossIdToOwner.keySet()) {
            Entity armorStand = mc.level.getEntity(bossId);
            if (armorStand != null && armorStand.level() == entity.level()) {
                double dx = armorStand.getX() - entity.getX();
                double dz = armorStand.getZ() - entity.getZ();
                double dy = armorStand.getY() - entity.getY();
                if ((dx * dx + dz * dz) <= 2.25 && Math.abs(dy) <= 5.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final double OWN_BOSS_CARRIER_RANGE_SQ = 72.0 * 72.0;
    private static final double BLAZE_SPLIT_CANDIDATE_RADIUS_SQ = 5.0 * 5.0;
    private static final double BLAZE_SPLIT_ACTIVE_RADIUS_SQ = 24.0 * 24.0;
    private static final int SPLIT_COORD_CAP = 16;
    private static final double[] splitCoordX = new double[SPLIT_COORD_CAP];
    private static final double[] splitCoordY = new double[SPLIT_COORD_CAP];
    private static final double[] splitCoordZ = new double[SPLIT_COORD_CAP];
    private static double lastOwnBossX = 0.0;
    private static double lastOwnBossY = 0.0;
    private static double lastOwnBossZ = 0.0;

    public static void onChatMessage(String rawMessage) {
        Minecraft mc = Minecraft.getInstance();
        String selfName = mc != null && mc.player != null ? mc.player.getScoreboardName() : null;
        SlayerOwnBossTracker.INSTANCE.onChatMessage(rawMessage, selfName);
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModConfig.INSTANCE.enableSlayerCarry) {
            showSpawnHudTicks = 0;
            lastSpawnedBossOwner = null;
            pendingChatDelay = 0;
            pendingChatMessage = null;
            pendingCompletionDelay = 0;
            pendingCompletionMessage = null;
            knownOwnBossIds.clear();
            SlayerOwnBossTracker.INSTANCE.resetOwnBossState();
            HudStateContainer.updateBossState(false, "", mc != null ? mc.font : null);
            return;
        }

        if (showSpawnHudTicks > 0) {
            showSpawnHudTicks--;
        }

        boolean showHud = ModConfig.INSTANCE.enableBossSpawnHud && showSpawnHudTicks > 0 && lastSpawnedBossOwner != null && !lastSpawnedBossOwner.isEmpty();
        HudStateContainer.updateBossState(showHud, lastSpawnedBossOwner, mc.font);

        if (pendingChatDelay > 0) {
            pendingChatDelay--;
            if (pendingChatDelay <= 0 && pendingChatMessage != null) {
                WishClient.sendServerChatMessage("/pc ", pendingChatMessage);
                pendingChatMessage = null;
            }
        }

        if (pendingCompletionDelay > 0) {
            pendingCompletionDelay--;
            if (pendingCompletionDelay <= 0 && pendingCompletionMessage != null) {
                WishClient.sendServerChatMessage("/pc ", pendingCompletionMessage);
                pendingCompletionMessage = null;
            }
        }

        boolean checkTracked = mc.level != null && !trackedPlayers.isEmpty();
        String myNameLower = null;
        boolean nearbyPartyCarrier = false;
        if (mc.level != null && mc.player != null && ModConfig.INSTANCE.enableSlayerAutoCoords && trackedPlayers.isEmpty()) {
            String myName = mc.player.getScoreboardName();
            if (myName != null && !myName.isEmpty()) {
                String candidate = myName.toLowerCase(java.util.Locale.ROOT);
                if (!trackedPlayers.containsKey(candidate)) {
                    myNameLower = candidate;
                    nearbyPartyCarrier = hasNearbyPartyCarrier(mc);
                }
            }
        }

        boolean canSendOwnCoords = SlayerOwnBossTracker.INSTANCE.canAutoSendCoords(
                ModConfig.INSTANCE.enableSlayerCarry,
                ModConfig.INSTANCE.enableSlayerAutoCoords,
                !trackedPlayers.isEmpty(),
                nearbyPartyCarrier
        );
        boolean hasBossTrackingState = !knownOwnBossIds.isEmpty() || SlayerOwnBossTracker.INSTANCE.isBossActive();
        boolean scanOwnBoss = mc.level != null && myNameLower != null && (canSendOwnCoords || hasBossTrackingState);

        if (mc.level == null || myNameLower == null) {
            knownOwnBossIds.clear();
            SlayerOwnBossTracker.INSTANCE.resetOwnBossState();
        }

        if (checkTracked || scanOwnBoss) {
            int splitCount = 0;
            boolean foundOwnBossCandidate = false;
            double candBossX = 0.0;
            double candBossY = 0.0;
            double candBossZ = 0.0;

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity == null) continue;
                if (checkTracked) {
                    processTrackedCarryEntity(mc, entity);
                }
                if (scanOwnBoss && entity.hasCustomName() && entity.getCustomName() != null) {
                    String nameLower = SkyblockDetector.stripColorCodes(entity.getCustomName().getString()).toLowerCase(java.util.Locale.ROOT);
                    if (SlayerOwnBossTracker.isBlazeSplitDemonName(nameLower)) {
                        if (splitCount < SPLIT_COORD_CAP && entity.distanceToSqr(mc.player) <= BLAZE_SPLIT_ACTIVE_RADIUS_SQ * 2.0) {
                            splitCoordX[splitCount] = entity.getX();
                            splitCoordY[splitCount] = entity.getY();
                            splitCoordZ[splitCount] = entity.getZ();
                            splitCount++;
                        }
                    } else if (SlayerOwnBossTracker.isOwnSlayerBossHologram(nameLower, myNameLower)) {
                        knownOwnBossIds.add(entity.getId());
                        lastOwnBossX = entity.getX();
                        lastOwnBossY = entity.getY();
                        lastOwnBossZ = entity.getZ();
                        if (!foundOwnBossCandidate && canSendOwnCoords && !SlayerOwnBossTracker.INSTANCE.isBossActive()) {
                            foundOwnBossCandidate = true;
                            candBossX = entity.getX();
                            candBossY = entity.getY();
                            candBossZ = entity.getZ();
                        }
                    }
                }
            }

            if (checkTracked) {
                pruneDeadTrackedBosses(mc);
            }
            if (scanOwnBoss) {
                pruneDeadOwnBossIds(mc);
                boolean candidateNearSplit = foundOwnBossCandidate && isAnySplitNear(splitCount, candBossX, candBossY, candBossZ, BLAZE_SPLIT_CANDIDATE_RADIUS_SQ);
                boolean splitNearLastBoss = SlayerOwnBossTracker.INSTANCE.isBossActive()
                        && isAnySplitNear(splitCount, lastOwnBossX, lastOwnBossY, lastOwnBossZ, BLAZE_SPLIT_ACTIVE_RADIUS_SQ);

                if (foundOwnBossCandidate && canSendOwnCoords && mc.player.connection != null) {
                    long now = System.currentTimeMillis();
                    if (SlayerOwnBossTracker.INSTANCE.tryAnnounceOwnBoss(now, candidateNearSplit)) {
                        lastOwnCoordsNotifyTime = now;
                        int bx = (int) Math.floor(candBossX);
                        int by = (int) Math.floor(candBossY);
                        int bz = (int) Math.floor(candBossZ);
                        WishClient.sendServerChatMessage("/pc ", "x: " + bx + ", y: " + by + ", z: " + bz);
                    }
                }

                SlayerOwnBossTracker.INSTANCE.onOwnBossTickEnd(!knownOwnBossIds.isEmpty(), splitNearLastBoss);
            }
        }
    }

    private static boolean hasNearbyPartyCarrier(Minecraft mc) {
        if (mc.level == null || mc.player == null || !SlayerOwnBossTracker.INSTANCE.hasOtherPartyMembers()) {
            return false;
        }
        for (net.minecraft.world.entity.player.Player other : mc.level.players()) {
            if (other == null || other == mc.player || other.isRemoved()) {
                continue;
            }
            if (other.distanceToSqr(mc.player) <= OWN_BOSS_CARRIER_RANGE_SQ
                    && SlayerOwnBossTracker.INSTANCE.isPartyMember(other.getScoreboardName())) {
                return true;
            }
        }
        return false;
    }

    private static void pruneDeadOwnBossIds(Minecraft mc) {
        java.util.Iterator<Integer> it = knownOwnBossIds.iterator();
        while (it.hasNext()) {
            Entity boss = mc.level.getEntity(it.next());
            if (boss == null || !boss.isAlive()) {
                it.remove();
            }
        }
    }

    private static boolean isAnySplitNear(int splitCount, double x, double y, double z, double maxDistSq) {
        for (int i = 0; i < splitCount; i++) {
            double dx = splitCoordX[i] - x;
            double dy = splitCoordY[i] - y;
            double dz = splitCoordZ[i] - z;
            if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    private static void processTrackedCarryEntity(Minecraft mc, Entity entity) {
        String trackedName = getTrackedBossOwner(entity);
        if (trackedName == null) return;

        int id = entity.getId();
        knownBossLastPos.computeIfAbsent(id, k -> new BossPos()).set(entity.getX(), entity.getY(), entity.getZ());
        if (!knownBossIdToOwner.containsKey(id)) {
            knownBossIdToOwner.put(id, trackedName);
            long now = System.currentTimeMillis();
            String ownerKey = trackedName.toLowerCase(java.util.Locale.ROOT);
            long lastNotify = lastSpawnNotifyByOwner.getOrDefault(ownerKey, 0L);
            if (now - lastNotify >= 3000L) {
                lastSpawnNotifyByOwner.put(ownerKey, now);
                lastSpawnedBossOwner = trackedName;
                showSpawnHudTicks = 100;
                if (ModConfig.INSTANCE.enableBossSpawnHud) {
                    WishClient.sendLocalChatMessage("§d§l[Wish] §e" + trackedName + "§a's boss has spawned!");
                    if (mc.player != null) {
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private static void pruneDeadTrackedBosses(Minecraft mc) {
        knownBossIdToOwner.entrySet().removeIf(entry -> {
            int id = entry.getKey();
            Entity boss = mc.level.getEntity(id);
            if (boss != null && boss.isAlive()) {
                knownBossLastPos.computeIfAbsent(id, k -> new BossPos()).set(boss.getX(), boss.getY(), boss.getZ());
                return false;
            }
            BossPos pos = knownBossLastPos.remove(id);
            if (mc.player != null && pos != null && mc.player.distanceToSqr(pos.x, pos.y, pos.z) <= 36.0 * 36.0) {
                incrementBosses(entry.getValue());
            }
            return true;
        });
    }


    public static String getTrackedBossOwner(Entity entity) {
        if (trackedPlayers.isEmpty() || entity == null || !entity.hasCustomName()) return null;
        var customNameComp = entity.getCustomName();
        if (customNameComp == null) return null;
        String name = SkyblockDetector.stripColorCodes(customNameComp.getString());
        String nameLower = name.toLowerCase(java.util.Locale.ROOT);

        for (java.util.Map.Entry<String, CarrySession> entry : trackedPlayers.entrySet()) {
            if (nameLower.contains(entry.getKey() + "'s") || (nameLower.contains("spawned by:") && nameLower.contains(entry.getKey()))) {
                return entry.getValue().originalName;
            }
        }
        return null;
    }
}
