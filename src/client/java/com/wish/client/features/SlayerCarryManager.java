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

    private static final java.util.Map<String, CarrySession> trackedPlayers = new java.util.HashMap<>();
    
    private static final java.util.Map<Integer, String> knownBossIdToOwner = new java.util.HashMap<>();
    private static final java.util.Map<Integer, double[]> knownBossLastPos = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> lastSpawnNotifyByOwner = new java.util.HashMap<>();
    private static final java.util.Set<Integer> knownOwnBossIds = new java.util.HashSet<>();
    private static long lastOwnCoordsNotifyTime = 0L;

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
        String playerLower = playerRaw.toLowerCase();
        CarrySession session = trackedPlayers.get(playerLower);
        if (session != null) {
            session.bossesDone++;
            
            pendingChatMessage = "pc Boss " + session.bossesDone + "/" + session.totalBosses + " done :3";
            pendingChatDelay = 20; 

            if (session.bossesDone >= session.totalBosses) {
                pendingCompletionMessage = "pc Carry completed for " + session.originalName + " (" + session.totalBosses + "/" + session.totalBosses + ")";
                pendingCompletionDelay = 60; 
                trackedPlayers.remove(playerLower);
            }
        }
    }

    public static void stopTracking() {
        trackedPlayers.clear();
        knownBossIdToOwner.clear();
        knownBossLastPos.clear();
        lastSpawnNotifyByOwner.clear();
        pendingChatMessage = null;
        pendingChatDelay = 0;
        pendingCompletionMessage = null;
        pendingCompletionDelay = 0;
        showSpawnHudTicks = 0;
        lastSpawnedBossOwner = "";
        HudStateContainer.updateBossState(false, null, null);
        WishClient.sendLocalChatMessage("§d§l[Wish] §cSlayer carry tracking stopped for everyone.");
    }

    public static void stopTrackingPlayer(String player) {
        if (trackedPlayers.remove(player.toLowerCase()) != null) {
            WishClient.sendLocalChatMessage("§d§l[Wish] §cSlayer carry tracking stopped for " + player + ".");
        } else {
            WishClient.sendLocalChatMessage("§c[Wish] No active tracker found for " + player + ".");
        }
    }

    public static int getGlowColorRgb() {
        String glow = ModConfig.INSTANCE.slayerGlowColor;
        if (glow != null && glow.length() == 7 && glow.charAt(0) == '#') {
            try {
                return Integer.parseInt(glow, 1, 7, 16);
            } catch (Exception ignored) {}
        }
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

    public static void onClientTick(Minecraft mc) {
        if (showSpawnHudTicks > 0) {
            showSpawnHudTicks--;
        }

        
        boolean showHud = ModConfig.INSTANCE.enableBossSpawnHud && showSpawnHudTicks > 0 && lastSpawnedBossOwner != null && !lastSpawnedBossOwner.isEmpty();
        HudStateContainer.updateBossState(showHud, lastSpawnedBossOwner, mc.font);

        if (pendingChatDelay > 0) {
            pendingChatDelay--;
            if (pendingChatDelay <= 0 && pendingChatMessage != null) {
                if (mc.player != null && mc.player.connection != null) {
                    mc.player.connection.sendCommand(pendingChatMessage);
                }
                pendingChatMessage = null;
            }
        }

        if (pendingCompletionDelay > 0) {
            pendingCompletionDelay--;
            if (pendingCompletionDelay <= 0 && pendingCompletionMessage != null) {
                if (mc.player != null && mc.player.connection != null) {
                    mc.player.connection.sendCommand(pendingCompletionMessage);
                }
                pendingCompletionMessage = null;
            }
        }

        if (mc.level != null && !trackedPlayers.isEmpty()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                String trackedName = getTrackedBossOwner(entity);
                
                if (trackedName != null) {
                    int id = entity.getId();
                    knownBossLastPos.put(id, new double[]{entity.getX(), entity.getY(), entity.getZ()});
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
            }
            
            
            knownBossIdToOwner.entrySet().removeIf(entry -> {
                int id = entry.getKey();
                Entity boss = mc.level.getEntity(id);
                if (boss != null && boss.isAlive()) {
                    knownBossLastPos.put(id, new double[]{boss.getX(), boss.getY(), boss.getZ()});
                    return false;
                }
                double[] pos = knownBossLastPos.remove(id);
                if (mc.player != null && pos != null && mc.player.distanceToSqr(pos[0], pos[1], pos[2]) <= 36.0 * 36.0) {
                    incrementBosses(entry.getValue());
                }
                return true;
            });
        }

        if (mc.level != null && mc.player != null && ModConfig.INSTANCE.enableSlayerAutoCoords) {
            String myName = mc.player.getScoreboardName();
            if (myName != null && !myName.isEmpty()) {
                String myNameLower = myName.toLowerCase(java.util.Locale.ROOT);
                if (!trackedPlayers.containsKey(myNameLower)) {
                    for (Entity entity : mc.level.entitiesForRendering()) {
                        if (entity == null || !entity.hasCustomName() || entity.getCustomName() == null) continue;
                        String nameLower = SkyblockDetector.stripColorCodes(entity.getCustomName().getString()).toLowerCase(java.util.Locale.ROOT);
                        if (nameLower.contains(myNameLower + "'s") || (nameLower.contains("spawned by:") && nameLower.contains(myNameLower))) {
                            int id = entity.getId();
                            if (knownOwnBossIds.add(id)) {
                                long now = System.currentTimeMillis();
                                if (now - lastOwnCoordsNotifyTime >= 3000L && mc.player.connection != null) {
                                    lastOwnCoordsNotifyTime = now;
                                    int bx = (int) Math.floor(entity.getX());
                                    int by = (int) Math.floor(entity.getY());
                                    int bz = (int) Math.floor(entity.getZ());
                                    mc.player.connection.sendCommand("pc x: " + bx + ", y: " + by + ", z: " + bz);
                                }
                            }
                        }
                    }
                    knownOwnBossIds.removeIf(id -> {
                        Entity boss = mc.level.getEntity(id);
                        return boss == null || !boss.isAlive();
                    });
                }
            }
        }
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
