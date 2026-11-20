package com.wish.client.features;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MiningAbilityManager {

    private static final MiningAbilityTracker TRACKER = new MiningAbilityTracker();
    private static final List<String> LORE_SCRATCH = new ArrayList<>(16);

    private static int tickCounter = 0;
    private static ItemStack lastScannedStack = ItemStack.EMPTY;
    private static ItemLore lastScannedLore = null;
    private static boolean holdingMiningTool = false;

    private MiningAbilityManager() {
    }

    public static void reset() {
        TRACKER.reset();
        tickCounter = 0;
        lastScannedStack = ItemStack.EMPTY;
        lastScannedLore = null;
        holdingMiningTool = false;
        HudStateContainer.updateMiningAbilityState(false, null, null, 0, null);
    }

    public static void onChatMessage(String rawMessage) {
        if (!ModConfig.INSTANCE.enableMiningAbilityHud || rawMessage == null || rawMessage.isEmpty()) {
            return;
        }
        String clean = SkyblockDetector.stripColorCodes(rawMessage);
        if (clean.isEmpty()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        boolean becameReady = TRACKER.handleChatLine(clean, nowMs);
        if (becameReady && ModConfig.INSTANCE.enableMiningAbilitySound) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.25f);
                    }
                });
            }
        }
        publishSnapshot(Minecraft.getInstance(), nowMs);
    }

    public static void onClientTick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        if (!ModConfig.INSTANCE.enableMiningAbilityHud) {
            HudStateContainer.updateMiningAbilityState(false, null, null, 0, mc.font);
            return;
        }
        tickCounter++;
        if (tickCounter < 2) {
            return;
        }
        tickCounter = 0;

        long nowMs = System.currentTimeMillis();
        scanHeldTool(mc.player.getMainHandItem());
        scanTabListWidget(mc, nowMs);
        TRACKER.tick(nowMs);
        publishSnapshot(mc, nowMs);
    }

    public static void refreshHudState() {
        Minecraft mc = Minecraft.getInstance();
        publishSnapshot(mc, System.currentTimeMillis());
    }

    private static void publishSnapshot(Minecraft mc, long nowMs) {
        if (!ModConfig.INSTANCE.enableMiningAbilityHud || mc == null || mc.player == null || mc.level == null) {
            HudStateContainer.updateMiningAbilityState(false, null, null, 0, null);
            return;
        }
        MiningAbilityTracker.AbilityState state = TRACKER.getState();
        boolean activeOrCooldown = state != MiningAbilityTracker.AbilityState.READY;
        boolean contextAllowed = SkyblockDetector.isInSkyblock || holdingMiningTool || activeOrCooldown;
        boolean visible = contextAllowed && (!ModConfig.INSTANCE.miningAbilityOnlyWithTool || holdingMiningTool || activeOrCooldown);
        if (!visible) {
            HudStateContainer.updateMiningAbilityState(false, null, null, 0, mc.font);
            return;
        }

        String prefix = TRACKER.formatLabelPrefix(null);
        String status = TRACKER.formatStatusText(nowMs);
        int statusColor = switch (state) {
            case READY -> 0xFF55FF55;
            case ACTIVE -> 0xFFFFAA00;
            case COOLDOWN -> 0xFFFF5555;
        };
        HudStateContainer.updateMiningAbilityState(true, prefix, status, statusColor, mc.font);
    }

    private static void scanHeldTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            lastScannedStack = ItemStack.EMPTY;
            lastScannedLore = null;
            holdingMiningTool = false;
            return;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (stack == lastScannedStack && lore == lastScannedLore) {
            return;
        }
        lastScannedStack = stack;
        lastScannedLore = lore;

        String name = SkyblockDetector.stripColorCodes(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
        boolean nameLooksMining = name.contains("pickaxe") || name.contains("drill") || name.contains("gauntlet")
                || name.contains("pickonimbus") || name.contains("divan") || name.contains("stonk");

        LORE_SCRATCH.clear();
        boolean hasPickaxeAbilityLore = false;
        if (lore != null) {
            for (Component line : lore.lines()) {
                String clean = SkyblockDetector.stripColorCodes(line.getString());
                if (!clean.isEmpty()) {
                    LORE_SCRATCH.add(clean);
                    if (MiningAbilityTracker.isPickaxeAbilityLoreHeader(clean)) {
                        hasPickaxeAbilityLore = true;
                    }
                }
            }
        }
        boolean hasBlueCheese = hasBlueCheeseUpgrade(stack);
        holdingMiningTool = nameLooksMining || hasPickaxeAbilityLore;
        if (holdingMiningTool && !LORE_SCRATCH.isEmpty()) {
            TRACKER.handleToolLore(LORE_SCRATCH, hasBlueCheese);
        }
    }

    private static boolean hasBlueCheeseUpgrade(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return false;
        }
        try {
            CompoundTag tag = customData.copyTag();
            if (tagContainsBlueCheese(tag)) {
                return true;
            }
            if (tag.contains("ExtraAttributes")) {
                return tagContainsBlueCheese(tag.getCompoundOrEmpty("ExtraAttributes"));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean tagContainsBlueCheese(CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        String flatModule = tag.getStringOr("drill_part_upgrade_module", "");
        if ("goblin_omelette_blue_cheese".equalsIgnoreCase(flatModule)) {
            return true;
        }
        if (tag.contains("upgrade_module")) {
            CompoundTag moduleTag = tag.getCompoundOrEmpty("upgrade_module");
            if ("GOBLIN_OMELETTE_BLUE_CHEESE".equalsIgnoreCase(moduleTag.getStringOr("id", ""))) {
                return true;
            }
        }
        return false;
    }

    private static void scanTabListWidget(Minecraft mc, long nowMs) {
        if (mc.player == null || mc.player.connection == null) {
            return;
        }
        try {
            boolean inAbilitySection = false;
            for (PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
                if (info == null || info.getTabListDisplayName() == null) {
                    continue;
                }
                String clean = SkyblockDetector.stripColorCodes(info.getTabListDisplayName().getString()).trim();
                if (clean.isEmpty()) {
                    continue;
                }
                if (clean.startsWith("Pickaxe Ability")) {
                    inAbilitySection = true;
                    continue;
                }
                if (inAbilitySection) {
                    if (clean.endsWith(":") && !clean.contains("Available") && !clean.contains("Ready")) {
                        break;
                    }
                    TRACKER.handleTabPickaxeLine(clean, nowMs);
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
