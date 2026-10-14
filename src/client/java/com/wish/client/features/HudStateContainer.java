package com.wish.client.features;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HudStateContainer {

    public record LagSnapshot(boolean isLagging, Component formattedText, long ms, int textWidth) {}
    public record BossSnapshot(boolean isVisible, Component line1, Component line2, int maxWidth) {}
    public record AutomatonSnapshot(boolean isVisible, List<Component> mainLines, List<Component> dropLines, int maxWidth, int totalHeight) {}

    private static volatile LagSnapshot currentLag = new LagSnapshot(false, Component.empty(), 0, 0);
    private static volatile BossSnapshot currentBoss = new BossSnapshot(false, Component.empty(), Component.empty(), 0);
    private static volatile AutomatonSnapshot currentAutomaton = new AutomatonSnapshot(false, Collections.emptyList(), Collections.emptyList(), 0, 0);

    private static final Component BOSS_TITLE = Component.literal("Boss spawn")
            .withStyle(style -> style.withBold(true).withColor(0xFFFF5555));
    private static final Component AUTOMATON_TITLE = Component.literal("Automaton Tracker")
            .withStyle(style -> style.withColor(0xFFFFAA00));

    public static LagSnapshot getLagSnapshot() {
        return currentLag;
    }

    public static BossSnapshot getBossSnapshot() {
        return currentBoss;
    }

    public static AutomatonSnapshot getAutomatonSnapshot() {
        return currentAutomaton;
    }

    public static void updateLagState(boolean lagging, long lagMs, net.minecraft.client.gui.Font font) {
        if (!lagging) {
            if (currentLag.isLagging()) {
                currentLag = new LagSnapshot(false, Component.empty(), 0, 0);
            }
            return;
        }

        
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

    public static void updateAutomatonState(
            boolean visible,
            int kills,
            double kph,
            double profit,
            double pph,
            long elapsedSec,
            net.minecraft.client.gui.Font font
    ) {
        if (!visible || font == null) {
            if (currentAutomaton.isVisible()) {
                currentAutomaton = new AutomatonSnapshot(false, Collections.emptyList(), Collections.emptyList(), 0, 0);
            }
            return;
        }

        boolean isTotal = com.wish.client.config.ModConfig.INSTANCE.automatonShowTotalStats;

        List<Component> mainLines = new java.util.ArrayList<>();

        String statModeStr = isTotal ? "Total" : "Session";
        int statModeColor = isTotal ? 0xFFFF55FF : 0xFF55FFFF;

        Component titleLine = Component.literal("Automaton Tracker")
                .withStyle(s -> s.withColor(0xFFFFAA00))
                .append(Component.literal(" [").withStyle(s -> s.withColor(0xFF888888)))
                .append(Component.literal(statModeStr).withStyle(s -> s.withColor(statModeColor)))
                .append(Component.literal("]").withStyle(s -> s.withColor(0xFF888888)));
        mainLines.add(titleLine);

        int displayKills = isTotal ? AutomatonManager.getTotalLifetimeKills() : kills;
        double displayKph = isTotal ? AutomatonManager.getTotalLifetimeKillsPerHour() : kph;
        String kphStr = String.format(Locale.US, "%.0f", displayKph);
        net.minecraft.network.chat.MutableComponent killLine = Component.literal("Kills: ")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(String.valueOf(displayKills)).withStyle(s -> s.withColor(0xFF55FF55)))
                .append(Component.literal(" (").withStyle(s -> s.withColor(0xFF888888)))
                .append(Component.literal(kphStr + "/h").withStyle(s -> s.withColor(0xFFFFFF55)))
                .append(Component.literal(")").withStyle(s -> s.withColor(0xFF888888)));
        mainLines.add(killLine);

        double displayProfit = isTotal ? AutomatonManager.getTotalLifetimeProfit() : profit;
        double displayPph = isTotal ? AutomatonManager.getTotalLifetimeProfitPerHour() : pph;

        Component profLine = Component.literal("Profit: ")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(AutomatonManager.formatCoins(displayProfit)).withStyle(s -> s.withColor(0xFFFFAA00)))
                .append(Component.literal(" (").withStyle(s -> s.withColor(0xFF888888)))
                .append(Component.literal(AutomatonManager.formatCoins(displayPph) + "/h").withStyle(s -> s.withColor(0xFFFFFF55)))
                .append(Component.literal(")").withStyle(s -> s.withColor(0xFF888888)));
        mainLines.add(profLine);

        long displaySec = isTotal ? AutomatonManager.getTotalLifetimeActiveSeconds() : elapsedSec;
        var timeLine = Component.literal("Timer: ")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(AutomatonManager.formatTime(displaySec)).withStyle(s -> s.withColor(0xFF55FFFF)));
        if (!isTotal && AutomatonManager.isPaused()) {
            timeLine.append(Component.literal(" (Paused)").withStyle(s -> s.withColor(0xFFFF5555).withBold(true)));
        }
        mainLines.add(timeLine);

        boolean instaMode = com.wish.client.config.ModConfig.INSTANCE.automatonUseInstaSell;
        String sellModeStr = instaMode ? "Instasell" : "Sell Offer";
        int modeColor = instaMode ? 0xFFFFAA00 : 0xFF55FFFF;

        Component dropHeader = Component.literal("Drops")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(" [").withStyle(s -> s.withColor(0xFF888888)))
                .append(Component.literal(sellModeStr).withStyle(s -> s.withColor(modeColor)))
                .append(Component.literal("]").withStyle(s -> s.withColor(0xFF888888)));
        mainLines.add(dropHeader);

        Map<String, Integer> drops = isTotal ? AutomatonManager.getTotalLifetimeDrops() : AutomatonManager.getAllDropCounts();
        List<Component> dropLines = new java.util.ArrayList<>();
        for (String part : AutomatonManager.ALL_TRACKED_ITEMS) {
            int count = drops.getOrDefault(part, 0);
            if (count > 0) {
                double unitPrice = AutomatonManager.getPrice(part);
                double total = unitPrice * count;
                String displayName = AutomatonManager.getDisplayName(part);
                int itemColor = AutomatonManager.getItemColor(part);
                String priceInfo = " (" + AutomatonManager.formatCoins(total) + ")";

                Component dropLine = Component.literal("• " + displayName + ": ")
                        .withStyle(s -> s.withColor(itemColor))
                        .append(Component.literal(String.valueOf(count)).withStyle(s -> s.withColor(0xFFFFFFFF).withBold(true)))
                        .append(Component.literal(priceInfo).withStyle(s -> s.withColor(0xFFFFAA00)));
                dropLines.add(dropLine);
            }
        }

        int maxMainW = 0;
        for (Component c : mainLines) {
            maxMainW = Math.max(maxMainW, font.width(c));
        }

        int maxDropW = 0;
        for (Component c : dropLines) {
            maxDropW = Math.max(maxDropW, font.width(c));
        }

        float dropScale = 0.75f;
        int scaledDropW = (int) (maxDropW * dropScale) + 3;
        int maxW = Math.max(maxMainW, scaledDropW);

        int totalH = mainLines.size() * (font.lineHeight + 1);
        if (!dropLines.isEmpty()) {
            totalH += (int) (dropLines.size() * (font.lineHeight + 1) * dropScale);
        }

        currentAutomaton = new AutomatonSnapshot(true, mainLines, dropLines, maxW, totalH);
    }
}
