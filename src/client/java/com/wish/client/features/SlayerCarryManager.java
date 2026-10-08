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
    // Store primitive entity IDs mapped to owner name to avoid strong Entity references and memory leaks
    private static final java.util.Map<Integer, String> knownBossIdToOwner = new java.util.HashMap<>();

    private static int pendingChatDelay = 0;
    private static String pendingChatMessage = null;
    private static String pendingCompletionMessage = null;
    private static int pendingCompletionDelay = 0;
    public static int showSpawnHudTicks = 0;
    public static String lastSpawnedBossOwner = "";

    public static void setTrackedPlayer(String player, int amount) {
        trackedPlayers.put(player.toLowerCase(), new CarrySession(player, amount));
        WishClient.sendLocalChatMessage("§d§l[CarryTracker] §aSlayer carry tracker added for " + player + " (0/" + amount + " bosses).");
    }

    public static void incrementBosses(String playerRaw) {
        String playerLower = playerRaw.toLowerCase();
        CarrySession session = trackedPlayers.get(playerLower);
        if (session != null) {
            session.bossesDone++;
            
            pendingChatMessage = "pc Boss " + session.bossesDone + "/" + session.totalBosses + " done :3";
            pendingChatDelay = 20; // 1 second delay for kill message

            if (session.bossesDone >= session.totalBosses) {
                pendingCompletionMessage = "pc Carry completed for " + session.originalName + " (" + session.totalBosses + "/" + session.totalBosses + ")";
                pendingCompletionDelay = 60; // 3 seconds delay for completion message
                trackedPlayers.remove(playerLower);
            }
        }
    }

    public static void stopTracking() {
        trackedPlayers.clear();
        knownBossIdToOwner.clear();
        pendingChatMessage = null;
        pendingChatDelay = 0;
        pendingCompletionMessage = null;
        pendingCompletionDelay = 0;
        showSpawnHudTicks = 0;
        lastSpawnedBossOwner = "";
        HudStateContainer.updateBossState(false, null, null);
        WishClient.sendLocalChatMessage("§d§l[CarryTracker] §cSlayer carry tracking stopped for everyone.");
    }

    public static void stopTrackingPlayer(String player) {
        if (trackedPlayers.remove(player.toLowerCase()) != null) {
            WishClient.sendLocalChatMessage("§d§l[CarryTracker] §cSlayer carry tracking stopped for " + player + ".");
        } else {
            WishClient.sendLocalChatMessage("§c[CarryTracker] No active tracker found for " + player + ".");
        }
    }

    public static boolean isActualBossEntity(Entity entity) {
        if (trackedPlayers.isEmpty() || knownBossIdToOwner.isEmpty() || entity.level() == null) return false;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        // Fast O(1) entity lookup by ID instead of holding stale Entity references
        for (Integer bossId : knownBossIdToOwner.keySet()) {
            Entity armorStand = mc.level.getEntity(bossId);
            if (armorStand != null && armorStand.level() == entity.level()) {
                if (armorStand.distanceTo(entity) < 4.0 && entity instanceof net.minecraft.world.entity.LivingEntity && !(entity instanceof net.minecraft.world.entity.player.Player) && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
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

        // Push state to HudStateContainer (State-Driven architecture)
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
                    if (!knownBossIdToOwner.containsKey(id)) {
                        knownBossIdToOwner.put(id, trackedName);
                        
                        lastSpawnedBossOwner = trackedName;
                        showSpawnHudTicks = 100; // 5 seconds at 20 ticks/sec
                    }
                }
            }
            
            // Clean up removed entities (boss died) - O(1) lookup via mc.level.getEntity(id)
            knownBossIdToOwner.entrySet().removeIf(entry -> {
                int id = entry.getKey();
                Entity boss = mc.level.getEntity(id);
                if (boss == null || !boss.isAlive()) {
                    incrementBosses(entry.getValue());
                    return true;
                }
                return false;
            });
        }
    }

    // Returns the player name if this entity is a tracked boss, null otherwise
    public static String getTrackedBossOwner(Entity entity) {
        if (trackedPlayers.isEmpty() || entity == null || !entity.hasCustomName()) return null;
        var customNameComp = entity.getCustomName();
        if (customNameComp == null) return null;
        String name = customNameComp.getString();
        String nameLower = name.toLowerCase();
        
        for (java.util.Map.Entry<String, CarrySession> entry : trackedPlayers.entrySet()) {
            if (nameLower.contains(entry.getKey() + "'s") || (nameLower.contains("spawned by:") && nameLower.contains(entry.getKey()))) {
                return entry.getValue().originalName;
            }
        }
        return null;
    }
}
