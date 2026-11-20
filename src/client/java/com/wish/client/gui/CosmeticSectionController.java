package com.wish.client.gui;

import com.wish.client.config.ModConfig;
import com.wish.client.features.cosmetic.PlayerCosmeticsManager;
import com.wish.client.features.visual.CatPawTrailManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

final class CosmeticSectionController {
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 8;
    private static final int OPT_H = 18;

    private int scrollY = 0;
    private int openDropdown = 0;

    void clearTransientState() {
        openDropdown = 0;
    }

    int getContentHeight() {
        int h = (ROW_H + ROW_GAP) * 4 + 26;
        int openCount = getOpenItemCount();
        if (openCount > 0) {
            h += openCount * OPT_H + 6;
        }
        return h;
    }

    private int getOpenItemCount() {
        return switch (openDropdown) {
            case 1 -> PlayerCosmeticsManager.WING_NAMES.size();
            case 2 -> PlayerCosmeticsManager.HALO_NAMES.size();
            case 3 -> PlayerCosmeticsManager.PARTICLE_NAMES.size();
            case 4 -> PlayerCosmeticsManager.TRAIL_NAMES.size();
            default -> 0;
        };
    }

    void scroll(double scrollDeltaY, int clipHeight) {
        int maxScroll = Math.max(0, getContentHeight() - clipHeight);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (scrollDeltaY * 18)));
    }

    void render(
            GuiGraphicsExtractor g,
            Font font,
            int cx,
            int clipTop,
            int cw,
            int clipBottom,
            int mx,
            int my,
            int colAccent,
            int colAccentDim,
            int colHover
    ) {
        int clipHeight = Math.max(1, clipBottom - clipTop);
        int totalH = getContentHeight();
        int maxScroll = Math.max(0, totalH - clipHeight);
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;

        g.enableScissor(cx - 2, clipTop, cx + cw + 4, clipBottom);
        int y = clipTop - scrollY;
        ModConfig cfg = ModConfig.INSTANCE;

        y = renderDropdownBlock(g, font, cx, y, cw, clipTop, clipBottom,
                1, "Wings", PlayerCosmeticsManager.WING_NAMES, cfg.selectedWing, 0,
                mx, my, colAccent, colHover);
        y = renderDropdownBlock(g, font, cx, y, cw, clipTop, clipBottom,
                2, "Halos", PlayerCosmeticsManager.HALO_NAMES, cfg.selectedHalo, 1,
                mx, my, colAccent, colHover);
        y = renderDropdownBlock(g, font, cx, y, cw, clipTop, clipBottom,
                3, "Particles", PlayerCosmeticsManager.PARTICLE_NAMES, cfg.selectedParticle, 2,
                mx, my, colAccent, colHover);

        int activeTrailId = cfg.selectedTrail > 0 ? cfg.selectedTrail : (cfg.enableCatPawTrails ? 1 : 0);
        y = renderDropdownBlock(g, font, cx, y, cw, clipTop, clipBottom,
                4, "Trails", PlayerCosmeticsManager.TRAIL_NAMES, activeTrailId, 3,
                mx, my, colAccent, colHover);

        boolean resetHov = mx >= cx && mx < cx + cw && my >= Math.max(y, clipTop) && my < Math.min(y + 22, clipBottom);
        RenderUtils.fillRoundedRect(g, cx, y, cw, 22, 4, resetHov ? colHover : 0x332E2E33);
        RenderUtils.drawGradientOutline(g, cx, y, cw, 22, 4, resetHov ? 0xFFFF5566 : 0x44666666, 0x22222222);
        String resetLbl = "Reset Cosmetics";
        g.text(font, resetLbl, cx + (cw - font.width(resetLbl)) / 2, y + 7, 0xFFFFFFFF);

        g.disableScissor();

        if (maxScroll > 0) {
            int scrollTrackX = cx + cw + 4;
            int barH = Math.max(16, (int) ((float) clipHeight / (float) totalH * clipHeight));
            int barY = clipTop + (int) ((float) scrollY / (float) maxScroll * (clipHeight - barH));
            RenderUtils.fillRoundedRect(g, scrollTrackX, clipTop, 3, clipHeight, 1, 0x22FFFFFF);
            RenderUtils.fillRoundedRect(g, scrollTrackX, barY, 3, barH, 1, colAccentDim);
        }
    }

    private int renderDropdownBlock(
            GuiGraphicsExtractor g,
            Font font,
            int cx,
            int y,
            int cw,
            int clipTop,
            int clipBottom,
            int dropdownId,
            String categoryLabel,
            List<String> names,
            int selectedId,
            int categoryKind,
            int mx,
            int my,
            int colAccent,
            int colHover
    ) {
        int clamped = clampForKind(categoryKind, selectedId);
        boolean isOpen = (openDropdown == dropdownId);
        boolean rowHov = mx >= cx && mx < cx + cw && my >= Math.max(y, clipTop) && my < Math.min(y + ROW_H, clipBottom);

        int bg = isOpen ? colHover : (rowHov ? 0x443A3A46 : 0x2A2A3233);
        int outline = isOpen ? colAccent : (rowHov ? 0x88AAAAAA : 0x33555555);
        RenderUtils.fillRoundedRect(g, cx, y, cw, ROW_H, 4, bg);
        RenderUtils.drawGradientOutline(g, cx, y, cw, ROW_H, 4, outline, isOpen ? colAccent : 0x22222222);

        g.text(font, categoryLabel, cx + 10, y + 7, 0xFFFFFFFF);

        String activeName = names.get(clamped);
        String arrow = isOpen ? "▲" : "▼";
        int rightPad = 10;
        int arrowW = font.width(arrow);
        int nameW = font.width(activeName);
        int dotSpace = clamped > 0 ? 10 : 0;
        int valueX = cx + cw - rightPad - arrowW - 6 - nameW - dotSpace;
        if (clamped > 0) {
            int dotRgb = getCategoryRgb(categoryKind, clamped);
            RenderUtils.fillRoundedRect(g, valueX, y + 8, 6, 6, 3, 0xFF000000 | dotRgb);
        }
        g.text(font, activeName, valueX + dotSpace, y + 7, clamped > 0 ? colAccent : 0xFFAAAAAA);
        g.text(font, arrow, cx + cw - rightPad - arrowW, y + 7, isOpen ? colAccent : 0xFF888899);

        y += ROW_H;

        if (isOpen) {
            int listH = names.size() * OPT_H;
            RenderUtils.fillRoundedRect(g, cx + 4, y + 1, cw - 8, listH, 4, 0xCC121218);
            RenderUtils.drawGradientOutline(g, cx + 4, y + 1, cw - 8, listH, 4, colAccent, 0x55444455);

            for (int idx = 0; idx < names.size(); idx++) {
                int oy = y + 1 + idx * OPT_H;
                boolean optActive = (clamped == idx);
                boolean optHov = mx >= cx + 4 && mx < cx + cw - 4
                        && my >= Math.max(oy, clipTop) && my < Math.min(oy + OPT_H, clipBottom);
                if (optActive || optHov) {
                    RenderUtils.fillRoundedRect(g, cx + 6, oy + 1, cw - 12, OPT_H - 2, 3, optActive ? colHover : 0x33FFFFFF);
                }

                int textX = cx + 14;
                if (idx > 0) {
                    int dotRgb = getCategoryRgb(categoryKind, idx);
                    RenderUtils.fillRoundedRect(g, cx + 12, oy + 6, 6, 6, 3, 0xFF000000 | dotRgb);
                    textX = cx + 22;
                }
                g.text(font, names.get(idx), textX, oy + 5, optActive ? colAccent : (optHov ? 0xFFFFFFFF : 0xFFCCCCCC));
            }
            y += listH + 4;
        }
        return y + ROW_GAP;
    }

    boolean handleClick(
            int cx,
            int clipTop,
            int cw,
            int clipBottom,
            double mx,
            double my
    ) {
        if (mx < cx || mx >= cx + cw || my < clipTop || my >= clipBottom) {
            openDropdown = 0;
            return false;
        }
        int y = clipTop - scrollY;
        ModConfig cfg = ModConfig.INSTANCE;

        int optChoice = hitTestDropdown(cx, y, cw, 1, PlayerCosmeticsManager.WING_NAMES.size(), mx, my);
        if (optChoice == -2) {
            openDropdown = (openDropdown == 1) ? 0 : 1;
            return true;
        } else if (optChoice >= 0) {
            cfg.selectedWing = optChoice;
            cfg.save();
            openDropdown = 0;
            return true;
        }
        y += ROW_H + (openDropdown == 1 ? PlayerCosmeticsManager.WING_NAMES.size() * OPT_H + 4 : 0) + ROW_GAP;

        optChoice = hitTestDropdown(cx, y, cw, 2, PlayerCosmeticsManager.HALO_NAMES.size(), mx, my);
        if (optChoice == -2) {
            openDropdown = (openDropdown == 2) ? 0 : 2;
            return true;
        } else if (optChoice >= 0) {
            cfg.selectedHalo = optChoice;
            cfg.save();
            openDropdown = 0;
            return true;
        }
        y += ROW_H + (openDropdown == 2 ? PlayerCosmeticsManager.HALO_NAMES.size() * OPT_H + 4 : 0) + ROW_GAP;

        optChoice = hitTestDropdown(cx, y, cw, 3, PlayerCosmeticsManager.PARTICLE_NAMES.size(), mx, my);
        if (optChoice == -2) {
            openDropdown = (openDropdown == 3) ? 0 : 3;
            return true;
        } else if (optChoice >= 0) {
            cfg.selectedParticle = optChoice;
            cfg.save();
            openDropdown = 0;
            return true;
        }
        y += ROW_H + (openDropdown == 3 ? PlayerCosmeticsManager.PARTICLE_NAMES.size() * OPT_H + 4 : 0) + ROW_GAP;

        optChoice = hitTestDropdown(cx, y, cw, 4, PlayerCosmeticsManager.TRAIL_NAMES.size(), mx, my);
        if (optChoice == -2) {
            openDropdown = (openDropdown == 4) ? 0 : 4;
            return true;
        } else if (optChoice >= 0) {
            cfg.selectedTrail = optChoice;
            cfg.enableCatPawTrails = optChoice > 0;
            if (optChoice == 0) {
                CatPawTrailManager.reset();
            }
            cfg.save();
            openDropdown = 0;
            return true;
        }
        y += ROW_H + (openDropdown == 4 ? PlayerCosmeticsManager.TRAIL_NAMES.size() * OPT_H + 4 : 0) + ROW_GAP;

        if (my >= y && my < y + 22 && mx >= cx && mx < cx + cw) {
            cfg.selectedWing = 0;
            cfg.selectedHalo = 0;
            cfg.selectedParticle = 0;
            cfg.selectedTrail = 0;
            cfg.enableCatPawTrails = false;
            cfg.cosmeticCustomColorEnabled = false;
            CatPawTrailManager.reset();
            cfg.save();
            openDropdown = 0;
            return true;
        }

        openDropdown = 0;
        return false;
    }

    private int hitTestDropdown(int cx, int y, int cw, int dropdownId, int itemCount, double mx, double my) {
        if (mx >= cx && mx < cx + cw && my >= y && my < y + ROW_H) {
            return -2;
        }
        if (openDropdown != dropdownId) {
            return -1;
        }
        int listTop = y + ROW_H + 1;
        int listH = itemCount * OPT_H;
        if (mx >= cx + 4 && mx < cx + cw - 4 && my >= listTop && my < listTop + listH) {
            int idx = (int) ((my - listTop) / OPT_H);
            return (idx >= 0 && idx < itemCount) ? idx : -1;
        }
        return -1;
    }

    private static int clampForKind(int categoryKind, int id) {
        return categoryKind == 3 ? PlayerCosmeticsManager.clampTrailId(id) : PlayerCosmeticsManager.clampId(id);
    }

    private static int getCategoryRgb(int categoryKind, int idx) {
        return switch (categoryKind) {
            case 0 -> PlayerCosmeticsManager.getWingDefaultRgb(idx);
            case 1 -> PlayerCosmeticsManager.getHaloDefaultRgb(idx);
            case 2 -> PlayerCosmeticsManager.getParticleDefaultRgb(idx);
            default -> PlayerCosmeticsManager.getTrailDefaultRgb(idx);
        };
    }
}
