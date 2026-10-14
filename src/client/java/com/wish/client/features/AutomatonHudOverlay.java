package com.wish.client.features;

import com.wish.client.config.ModConfig;
import com.wish.client.gui.GuiAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class AutomatonHudOverlay {

    // Mode button bounds ([Session] / [Total])
    private static float lastModeBtnX = 0;
    private static float lastModeBtnY = 0;
    private static float lastModeBtnW = 0;
    private static float lastModeBtnH = 0;
    private static boolean isModeHovered = false;

    // Sell button bounds ([Instasell] / [Sell Offer])
    private static float lastSellBtnX = 0;
    private static float lastSellBtnY = 0;
    private static float lastSellBtnW = 0;
    private static float lastSellBtnH = 0;
    private static boolean isSellHovered = false;

    private AutomatonHudOverlay() {}

    public static void render(GuiGraphicsExtractor g, Minecraft mc, int screenW, int screenH, int mx, int my, boolean inScreen) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || !ModConfig.INSTANCE.enableAutomatonTracker) {
            isModeHovered = false;
            isSellHovered = false;
            return;
        }

        var auto = HudStateContainer.getAutomatonSnapshot();
        if (!auto.isVisible()) {
            isModeHovered = false;
            isSellHovered = false;
            return;
        }

        int cardW = auto.maxWidth();
        int cardH = auto.totalHeight();

        int x = GuiAnchor.TOP_LEFT.resolveX(ModConfig.INSTANCE.automatonHudX, cardW, screenW);
        int y = GuiAnchor.TOP_LEFT.resolveY(ModConfig.INSTANCE.automatonHudY, cardH, screenH);
        float scale = Math.max(0.2f, ModConfig.INSTANCE.automatonHudScale);

        // 1. Compute bounds for [Session] / [Total] button (Line index 0: Title "Automaton Tracker ")
        int modeLineY = 0;
        String modePrefix = "Automaton Tracker ";
        int modePrefixW = mc.font.width(modePrefix);
        String modeBadge = "[" + (ModConfig.INSTANCE.automatonShowTotalStats ? "Total" : "Session") + "]";
        int modeBadgeW = mc.font.width(modeBadge);

        float modeBtnX = x + modePrefixW * scale;
        float modeBtnY = y + modeLineY * scale;
        float modeBtnW = modeBadgeW * scale;
        float modeBtnH = (mc.font.lineHeight + 1) * scale;

        lastModeBtnX = modeBtnX;
        lastModeBtnY = modeBtnY;
        lastModeBtnW = modeBtnW;
        lastModeBtnH = modeBtnH;

        // 2. Compute bounds for [Instasell] / [Sell Offer] button (Line index: dropHeader)
        int dropLineIndex = Math.max(0, auto.mainLines().size() - 1);
        int dropsLineY = dropLineIndex * (mc.font.lineHeight + 1);
        String sellPrefix = "Drops ";
        int sellPrefixW = mc.font.width(sellPrefix);
        String sellBadge = "[" + (ModConfig.INSTANCE.automatonUseInstaSell ? "Instasell" : "Sell Offer") + "]";
        int sellBadgeW = mc.font.width(sellBadge);

        float sellBtnX = x + sellPrefixW * scale;
        float sellBtnY = y + dropsLineY * scale;
        float sellBtnW = sellBadgeW * scale;
        float sellBtnH = (mc.font.lineHeight + 1) * scale;

        lastSellBtnX = sellBtnX;
        lastSellBtnY = sellBtnY;
        lastSellBtnW = sellBtnW;
        lastSellBtnH = sellBtnH;

        isModeHovered = inScreen && (mx >= modeBtnX - 4 && mx <= modeBtnX + modeBtnW + 4 && my >= modeBtnY - 3 && my <= modeBtnY + modeBtnH + 3);
        isSellHovered = inScreen && (mx >= sellBtnX - 4 && mx <= sellBtnX + sellBtnW + 4 && my >= sellBtnY - 3 && my <= sellBtnY + sellBtnH + 3);

        // Draw HUD lines
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        int lineY = 0;
        for (var line : auto.mainLines()) {
            g.text(mc.font, line, 0, lineY, 0xFFFFFFFF);
            lineY += mc.font.lineHeight + 1;
        }

        if (!auto.dropLines().isEmpty()) {
            float dropScale = 0.75f;
            g.pose().pushMatrix();
            g.pose().translate(3, lineY);
            g.pose().scale(dropScale, dropScale);

            int dropY = 0;
            for (var dropLine : auto.dropLines()) {
                g.text(mc.font, dropLine, 0, dropY, 0xFFFFFFFF);
                dropY += mc.font.lineHeight + 1;
            }

            g.pose().popMatrix();
        }

        g.pose().popMatrix();

        // Show tooltips when hovered in inventory screen (100% transparent background)
        if (inScreen) {
            if (isModeHovered) {
                String tooltip = ModConfig.INSTANCE.automatonShowTotalStats
                        ? "Click to switch to Session stats"
                        : "Click to switch to Total lifetime stats";
                int ttW = mc.font.width(tooltip);
                int ttX = Math.min(screenW - ttW - 4, mx + 10);
                int ttY = Math.min(screenH - mc.font.lineHeight - 4, my + 10);
                g.text(mc.font, tooltip, ttX, ttY, 0xFFFF55FF);
            } else if (isSellHovered) {
                String tooltip = ModConfig.INSTANCE.automatonUseInstaSell
                        ? "Click to switch to Sell Offer"
                        : "Click to switch to Instasell";
                int ttW = mc.font.width(tooltip);
                int ttX = Math.min(screenW - ttW - 4, mx + 10);
                int ttY = Math.min(screenH - mc.font.lineHeight - 4, my + 10);
                g.text(mc.font, tooltip, ttX, ttY, 0xFFFFAA00);
            }
        }
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || !ModConfig.INSTANCE.enableAutomatonTracker) {
            return false;
        }
        if (button != 0) return false; // Left-click only

        var auto = HudStateContainer.getAutomatonSnapshot();
        if (!auto.isVisible()) return false;

        // Click on [Session] / [Total] button
        if (mx >= lastModeBtnX - 4 && mx <= lastModeBtnX + lastModeBtnW + 4
                && my >= lastModeBtnY - 3 && my <= lastModeBtnY + lastModeBtnH + 3) {
            ModConfig.INSTANCE.automatonShowTotalStats = !ModConfig.INSTANCE.automatonShowTotalStats;
            ModConfig.INSTANCE.save();
            AutomatonManager.refreshHudState();

            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
            return true;
        }

        // Click on [Instasell] / [Sell Offer] button
        if (mx >= lastSellBtnX - 4 && mx <= lastSellBtnX + lastSellBtnW + 4
                && my >= lastSellBtnY - 3 && my <= lastSellBtnY + lastSellBtnH + 3) {
            ModConfig.INSTANCE.automatonUseInstaSell = !ModConfig.INSTANCE.automatonUseInstaSell;
            ModConfig.INSTANCE.save();
            AutomatonManager.refreshHudState();
            AutomatonManager.fetchBazaarPricesAsync(true);

            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
            return true;
        }
        return false;
    }
}
