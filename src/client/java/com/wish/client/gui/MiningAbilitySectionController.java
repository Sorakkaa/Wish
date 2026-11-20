package com.wish.client.gui;

import com.wish.client.config.ModConfig;
import com.wish.client.features.MiningAbilityManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

final class MiningAbilitySectionController {

    private static final int COL_TEXT = 0xFFE6E6F0;
    private static final int COL_OFF = 0xFF555566;

    private static boolean sectionOpen = false;

    int getContentHeight(int hH, int rowH) {
        int h = hH + 4;
        if (sectionOpen) {
            h += (rowH + 4) * 2;
        }
        return h;
    }

    int render(
            GuiGraphicsExtractor g,
            Font font,
            int cx,
            int cy,
            int cw,
            int hH,
            int rowH,
            int clipTop,
            int clipBottom,
            int mx,
            int my,
            int colAccent,
            int colHover
    ) {
        int hY = cy;
        boolean hHover = mx >= cx && mx < cx + cw && my >= Math.max(hY, clipTop) && my < Math.min(hY + hH, clipBottom);
        RenderUtils.fillRoundedRect(g, cx, hY, cw, hH, 4, sectionOpen ? colHover : (hHover ? 0x44444455 : 0x2A2A2E55));
        RenderUtils.drawGradientOutline(g, cx, hY, cw, hH, 4, sectionOpen ? colAccent : 0x55888888, 0x22444444);
        g.text(font, (sectionOpen ? "\u25BC " : "\u25B6 ") + "Pickaxe Ability", cx + 8, hY + 7, COL_TEXT);

        boolean enabled = ModConfig.INSTANCE.enableMiningAbilityHud;
        int dotSize = 8;
        int dotX = cx + cw - dotSize - 10;
        int dotY = hY + 7;
        boolean dotHover = isDotHovered(mx, my, cx, hY, cw);
        int dotCol = enabled ? (dotHover ? 0xFFBB00FF : colAccent) : (dotHover ? 0xFFFF5566 : COL_OFF);
        RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, dotCol);
        RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, dotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
        cy += hH + 4;

        if (!sectionOpen) {
            return cy;
        }

        int sx = cx + 10;
        int sw = cw - 10;
        cy = renderSimpleToggle(g, font, sx, cy, sw, rowH, "Only With Pickaxe / Drill", ModConfig.INSTANCE.miningAbilityOnlyWithTool, mx, my, colAccent);
        cy = renderSimpleToggle(g, font, sx, cy, sw, rowH, "Ready Sound Alert", ModConfig.INSTANCE.enableMiningAbilitySound, mx, my, colAccent);
        return cy;
    }

    boolean handleClick(
            double mx,
            double my,
            int cx,
            int cy,
            int cw,
            int hH,
            int rowH,
            Screen parentScreen
    ) {
        if (isDotHovered(mx, my, cx, cy, cw)) {
            ModConfig.INSTANCE.enableMiningAbilityHud = !ModConfig.INSTANCE.enableMiningAbilityHud;
            ModConfig.INSTANCE.save();
            MiningAbilityManager.refreshHudState();
            return true;
        }
        if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
            sectionOpen = !sectionOpen;
            return true;
        }
        cy += hH + 4;
        if (!sectionOpen) {
            return false;
        }

        int sx = cx + 10;
        int sw = cw - 10;
        if (isToggleClicked(mx, my, sx, cy, sw, rowH)) {
            ModConfig.INSTANCE.miningAbilityOnlyWithTool = !ModConfig.INSTANCE.miningAbilityOnlyWithTool;
            ModConfig.INSTANCE.save();
            MiningAbilityManager.refreshHudState();
            return true;
        }
        cy += rowH + 4;

        if (isToggleClicked(mx, my, sx, cy, sw, rowH)) {
            ModConfig.INSTANCE.enableMiningAbilitySound = !ModConfig.INSTANCE.enableMiningAbilitySound;
            ModConfig.INSTANCE.save();
            return true;
        }
        return false;
    }

    private int renderSimpleToggle(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int y,
            int w,
            int rowH,
            String label,
            boolean state,
            int mx,
            int my,
            int colAccent
    ) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
        RenderUtils.fillRoundedRect(g, x, y, w, rowH, 4, hover ? 0x333A3A50 : 0x22222230);
        RenderUtils.drawGradientOutline(g, x, y, w, rowH, 4, 0x33FFFFFF, 0x11FFFFFF);
        g.text(font, label, x + 8, y + (rowH - font.lineHeight) / 2, COL_TEXT);

        int btnW = 34;
        int btnH = 14;
        int btnX = x + w - btnW - 8;
        int btnY = y + (rowH - btnH) / 2;
        int bg = state ? colAccent : COL_OFF;
        RenderUtils.fillRoundedRect(g, btnX, btnY, btnW, btnH, 4, bg);
        String txt = state ? "ON" : "OFF";
        g.text(font, txt, btnX + (btnW - font.width(txt)) / 2, btnY + 3, 0xFFFFFFFF);
        return y + rowH + 4;
    }

    private static boolean isDotHovered(double mx, double my, int cx, int hY, int cw) {
        int dotX = cx + cw - 18;
        int dotY = hY + 7;
        return mx >= dotX - 3 && mx <= dotX + 11 && my >= dotY - 3 && my <= dotY + 11;
    }

    private static boolean isToggleClicked(double mx, double my, int x, int y, int w, int rowH) {
        int btnW = 34;
        int btnH = 14;
        int btnX = x + w - btnW - 8;
        int btnY = y + (rowH - btnH) / 2;
        return mx >= btnX - 4 && mx <= btnX + btnW + 4 && my >= btnY - 4 && my <= btnY + btnH + 4;
    }
}
