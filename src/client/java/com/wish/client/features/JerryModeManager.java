package com.wish.client.features;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;

public class JerryModeManager {
    private static long endTimestamp = 0;
    private static final java.util.Map<net.minecraft.world.entity.Entity, Villager> dummyMap = new java.util.WeakHashMap<>();

    public static void activate() {
        SorakaModeManager.deactivate();
        endTimestamp = System.currentTimeMillis() + 10000;
    }

    public static void deactivate() {
        endTimestamp = 0;
        dummyMap.clear();
    }

    public static boolean isActive() {
        if (System.currentTimeMillis() >= endTimestamp) {
            dummyMap.clear();
            return false;
        }
        return true;
    }

    public static Villager getDummyVillager(net.minecraft.world.entity.Entity original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        
        Villager dummy = dummyMap.get(original);
        if (dummy == null || dummy.level() != mc.level) {
            dummy = new Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, mc.level);
            dummyMap.put(original, dummy);
        }
        return dummy;
    }
}
