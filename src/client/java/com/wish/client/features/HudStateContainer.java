package com.wish.client.features;

import net.minecraft.network.chat.Component;

public final class HudStateContainer {

    public record LagSnapshot(boolean isLagging, Component formattedText, long ms, int textWidth) {}
    public record BossSnapshot(boolean isVisible, Component line1, Component line2, int maxWidth) {}

    private static volatile LagSnapshot currentLag = new LagSnapshot(false, Component.empty(), 0, 0);
    private static volatile BossSnapshot currentBoss = new BossSnapshot(false, Component.empty(), Component.empty(), 0);

    private static final Component BOSS_TITLE = Component.literal("Boss spawn")
            .withStyle(style -> style.withBold(true).withColor(0xFFFF5555));

    public static LagSnapshot getLagSnapshot() {
        return currentLag;
    }

    public static BossSnapshot getBossSnapshot() {
        return currentBoss;
    }

    public static void updateLagState(boolean lagging, long lagMs, net.minecraft.client.gui.Font font) {
        if (!lagging) {
            if (currentLag.isLagging()) {
                currentLag = new LagSnapshot(false, Component.empty(), 0, 0);
            }
            return;
        }

        // Cache Component and metrics so render thread allocates nothing
        Component comp = Component.literal(lagMs + "ms")
                .withStyle(style -> style.withColor(0xFFFF5555));
        int width = font != null ? font.width(comp) : 40;
        currentLag = new LagSnapshot(true, comp, lagMs, width);
    }

    public static void updateBossState(boolean visible, String owner, net.minecraft.client.gui.Font font) {
        if (!visible || owner == null || owner.isEmpty()) {
            if (currentBoss.isVisible()) {
                currentBoss = new BossSnapshot(false, Component.empty(), Component.empty(), 0);
            }
            return;
        }

        Component line1 = Component.literal(owner)
                .withStyle(style -> style.withColor(0xFFFFAA00).withBold(true));
        int w1 = font != null ? font.width(line1) : 50;
        int w2 = font != null ? font.width(BOSS_TITLE) : 60;
        currentBoss = new BossSnapshot(true, line1, BOSS_TITLE, Math.max(w1, w2));
    }
}
