package com.wish.client.gui;

import com.wish.client.color.ColorUtils;
import com.wish.client.config.ModConfig;
import com.wish.client.features.CustomModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.regex.Pattern;

public class WishModelScreen extends Screen {

    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\\n\\r]");
    private static final Pattern LINE_SPLIT_PATTERN = Pattern.compile("\n");

    private final Screen lastScreen;
    private final ItemStack heldStack;
    private final String targetKey;
    private final String targetDisplayName;

    private String searchQuery = "";
    private boolean searchFocused = false;

    private int currentPage = 0;
    private static final int COLS = 8;
    private static final int ROWS = 4;
    private static final int ITEMS_PER_PAGE = COLS * ROWS;
    private static final int PREVIEW_W = 152;
    private static final int PAGE_JUMP_STEP = 5;
    private static final int PAGE_BTN_W = 22;
    private static final int PAGE_JUMP_BTN_W = 28;
    private static final int PAGE_BTN_H = 16;
    private static final int PAGE_BTN_GAP = 4;
    private static final int PAGE_CENTER_HALF_GAP = 28;

    private int activeSectionTab = 0;
    private final WishModelNameTab nameTab;

    private String cachedPreviewModelId = null;
    private ItemStack cachedPreviewStack = null;
    private Boolean cachedPreviewGlow = null;
    private boolean cachedPreviewHasOverride = false;
    private ItemStack cachedPreviewDisplayStack = null;
    private String cachedPreviewName = null;
    private int cachedPreviewNameWidth = 0;
    private String cachedPreviewCustomRaw = null;
    private Component cachedPreviewCustomComp = null;
    private int cachedPreviewCustomWidth = 0;

    private String hoveredTooltipTitle = null;
    private String hoveredTooltipDesc = null;

    private final List<WishModelCatalog.ModelOption> filteredOptions = new ArrayList<>();

    public WishModelScreen(Screen lastScreen) {
        super(Component.literal("Item Model Customizer"));
        this.lastScreen = lastScreen;

        WishModelCatalog.ensurePackModelsLoaded();

        var player = Minecraft.getInstance().player;
        if (player != null && !player.getMainHandItem().isEmpty()) {
            this.heldStack = player.getMainHandItem().copy();
        } else {
            this.heldStack = new ItemStack(Items.DIAMOND_PICKAXE);
        }

        this.targetKey = CustomModelManager.getItemTargetKey(this.heldStack);
        this.targetDisplayName = CustomModelManager.getItemDisplayName(this.heldStack);
        this.nameTab = new WishModelNameTab(this.targetKey, this.targetDisplayName);

        updateFilteredOptions();
    }

    private void updateFilteredOptions() {
        WishModelCatalog.populateFilteredOptions(searchQuery, heldStack, filteredOptions);
        currentPage = 0;
    }

    private int getAccentColor() {
        String accent = ModConfig.INSTANCE.menuAccentColor;
        if (accent != null && accent.startsWith("#")) {
            int rgb = ColorUtils.parseHexRgb(accent, -1);
            if (rgb >= 0) {
                return 0xFF000000 | rgb;
            }
        }
        return 0xFF9D00FF;
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor g) {
        g.fillGradient(0, 0, this.width, this.height, 0x340A0A12, 0x460A0A14);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        CustomModelManager.bypassReplacement = true;
        try {
            renderScreenContent(g, mx, my);
        } finally {
            CustomModelManager.bypassReplacement = false;
        }
    }

    private int getWinW() {
        return Math.min(this.width - 24, 600);
    }

    private int getWinH() {
        return Math.min(this.height - 24, 372);
    }

    private boolean isItemGlowActive() {
        Boolean customGlow = CustomModelManager.getGlowOverride(targetKey);
        if (customGlow != null) {
            return customGlow;
        }
        return heldStack.isEnchanted()
                || (heldStack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
                        && Boolean.TRUE.equals(heldStack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
    }

    private Component resolveCachedCustomName(String customNameRaw) {
        if (customNameRaw == null || customNameRaw.isEmpty()) {
            cachedPreviewCustomRaw = null;
            cachedPreviewCustomComp = null;
            cachedPreviewCustomWidth = 0;
            return null;
        }
        if (!customNameRaw.equals(cachedPreviewCustomRaw)) {
            cachedPreviewCustomRaw = customNameRaw;
            cachedPreviewCustomComp = CustomModelManager.getCompiledNameComponent(customNameRaw);
            cachedPreviewCustomWidth = cachedPreviewCustomComp != null ? font.width(cachedPreviewCustomComp) : 0;
        }
        return cachedPreviewCustomComp;
    }

    private void renderScreenContent(GuiGraphicsExtractor g, int mx, int my) {
        hoveredTooltipTitle = null;
        hoveredTooltipDesc = null;

        int colAccent = getAccentColor();
        int colAccentDim = (colAccent & 0x00FFFFFF) | 0x44000000;

        int winW = getWinW();
        int winH = getWinH();
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        RenderUtils.fillRoundedRect(g, winX, winY, winW, winH, 6, 0x220C0C14);
        RenderUtils.drawGradientOutline(g, winX, winY, winW, winH, 6, colAccent, colAccent);

        g.text(font, "§lITEM MODEL CUSTOMIZER", winX + 16, winY + 13, colAccent);

        int closeBtnSize = 18;
        int closeX = winX + winW - closeBtnSize - 12;
        int closeY = winY + 10;

        int tabBtnH = 16;
        int tabBtnY = winY + 11;
        int tabNameW = 86;
        int tabModelsW = 54;
        int tabNameX = closeX - tabNameW - 8;
        int tabModelsX = tabNameX - tabModelsW - 6;
        renderTopTabButton(g, tabModelsX, tabBtnY, tabModelsW, tabBtnH, "Models", activeSectionTab == 0, mx, my, colAccent, colAccentDim);
        renderTopTabButton(g, tabNameX, tabBtnY, tabNameW, tabBtnH, "Name & Colors", activeSectionTab == 1, mx, my, colAccent, colAccentDim);

        boolean closeHover = mx >= closeX && mx <= closeX + closeBtnSize && my >= closeY && my <= closeY + closeBtnSize;
        RenderUtils.fillRoundedRect(g, closeX, closeY, closeBtnSize, closeBtnSize, 4, closeHover ? 0xAAFF3333 : 0x00000000);
        RenderUtils.drawGradientOutline(g, closeX, closeY, closeBtnSize, closeBtnSize, 4, closeHover ? 0xFFFF4444 : 0x44FFFFFF, 0x11000000);
        g.text(font, "✕", closeX + (closeBtnSize - font.width("✕")) / 2 + 1, closeY + 4, 0xFFFFFFFF);

        int bannerY = winY + 32;
        int bannerH = 38;
        int bannerW = winW - 28;
        int bannerX = winX + 14;

        RenderUtils.fillRoundedRect(g, bannerX, bannerY, bannerW, bannerH, 4, 0x00000000);
        RenderUtils.drawGradientOutline(g, bannerX, bannerY, bannerW, bannerH, 4, 0x55FFFFFF, 0x55FFFFFF);

        g.pose().pushMatrix();
        g.pose().translate(bannerX + 6, bannerY + 3);
        g.pose().scale(2.0f, 2.0f);
        g.fakeItem(heldStack, 0, 0);
        g.pose().popMatrix();

        String assignedModelId = CustomModelManager.getOverride(targetKey);
        boolean hasOverride = (assignedModelId != null && !assignedModelId.isEmpty() && !"default".equalsIgnoreCase(assignedModelId));
        Component customNameComp = resolveCachedCustomName(CustomModelManager.getNameOverride(targetKey));
        boolean hasAnyOverride = CustomModelManager.hasAnyOverride(targetKey);

        int resetBtnW = 54;
        int resetBtnH = 22;
        int resetBtnX = bannerX + bannerW - resetBtnW - 8;
        int resetBtnY = bannerY + (bannerH - resetBtnH) / 2;

        int infoTextX = bannerX + 44;
        int targetLabelEnd = resetBtnX - 6;
        g.enableScissor(infoTextX, bannerY + 3, targetLabelEnd, bannerY + 20);
        if (customNameComp != null) {
            int prefixW = font.width("Target: ");
            g.text(font, "Target: ", infoTextX, bannerY + 7, 0xFFFFFFFF);
            g.text(font, customNameComp, infoTextX + prefixW, bannerY + 7, 0xFFFFFFFF);
        } else {
            g.text(font, "Target: §f" + targetDisplayName, infoTextX, bannerY + 7, 0xFFFFFFFF);
        }
        g.disableScissor();

        String modelStatus;
        if (hasOverride) {
            String mName = assignedModelId;
            Identifier mid = Identifier.tryParse(assignedModelId.contains(":") ? assignedModelId : "minecraft:" + assignedModelId);
            if (mid != null) {
                Item mItem = BuiltInRegistries.ITEM.getValue(mid);
                if (mItem != null && mItem != Items.AIR) {
                    mName = CustomModelManager.getOriginalHoverName(new ItemStack(mItem)).getString();
                }
            }
            modelStatus = "Model: §a" + mName;
        } else {
            modelStatus = "Model: §7Original / None";
        }
        if (customNameComp != null) {
            modelStatus += "  §7|  Name: §dCustom";
        }
        g.enableScissor(infoTextX, bannerY + 19, targetLabelEnd, bannerY + 35);
        g.text(font, modelStatus, infoTextX, bannerY + 21, 0xFFDDDDDD);
        g.disableScissor();

        boolean resetHov = mx >= resetBtnX && mx < resetBtnX + resetBtnW && my >= resetBtnY && my < resetBtnY + resetBtnH;
        RenderUtils.fillRoundedRect(g, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 3, resetHov ? 0x44FF3333 : 0x00000000);
        RenderUtils.drawGradientOutline(g, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 3, hasAnyOverride ? (resetHov ? 0xFFFF5555 : 0x88FF5555) : 0x33666666, 0x11000000);
        g.text(font, "Reset", resetBtnX + (resetBtnW - font.width("Reset")) / 2, resetBtnY + 6, hasAnyOverride ? 0xFFFF8888 : 0xFF777777);
        if (resetHov && hasAnyOverride) {
            hoveredTooltipTitle = "Reset Item Overrides";
            hoveredTooltipDesc = "Resets custom model, glow & custom name for this item.";
        }

        int ctrlY = bannerY + bannerH + 8;
        int gridW = bannerW - PREVIEW_W - 8;
        int gridX = bannerX;
        int prevX = gridX + gridW + 8;
        boolean isGlowActive = isItemGlowActive();

        int previewPanelY;
        int previewPanelH;

        if (activeSectionTab == 0) {
            int searchH = 20;
            int glowBtnW = 54;
            int glowBtnH = 20;
            int searchX = bannerX;
            int searchW = bannerW - glowBtnW - 6;
            int glowBtnX = searchX + searchW + 6;
            int glowBtnY = ctrlY;

            RenderUtils.fillRoundedRect(g, searchX, ctrlY, searchW, searchH, 3, searchFocused ? 0x22FFFFFF : 0x00000000);
            RenderUtils.drawGradientOutline(g, searchX, ctrlY, searchW, searchH, 3, searchFocused ? colAccent : 0x44FFFFFF, 0x11000000);
            String searchPrompt = searchQuery.isEmpty() && !searchFocused
                    ? "§7Search items or pack models..."
                    : searchQuery + (searchFocused && (System.currentTimeMillis() % 1000 < 500) ? "§f|" : "");
            g.text(font, searchPrompt, searchX + 8, ctrlY + 6, 0xFFFFFFFF);

            boolean glowHov = mx >= glowBtnX && mx < glowBtnX + glowBtnW && my >= glowBtnY && my < glowBtnY + glowBtnH;
            int glowBg = isGlowActive ? (glowHov ? 0x66FF55FF : 0x33FF55FF) : (glowHov ? 0x22FFFFFF : 0x00000000);
            int glowBorder = isGlowActive ? 0xFFFF77FF : (glowHov ? 0x88FFFFFF : 0x44FFFFFF);
            RenderUtils.fillRoundedRect(g, glowBtnX, glowBtnY, glowBtnW, glowBtnH, 3, glowBg);
            RenderUtils.drawGradientOutline(g, glowBtnX, glowBtnY, glowBtnW, glowBtnH, 3, glowBorder, glowBorder);
            String glowLabel = isGlowActive ? "Glow: §dON" : "Glow: §7OFF";
            g.text(font, glowLabel, glowBtnX + (glowBtnW - font.width(glowLabel)) / 2, glowBtnY + 6, 0xFFFFFFFF);

            if (glowHov) {
                hoveredTooltipTitle = "Enchantment Glow (Glint)";
                hoveredTooltipDesc = isGlowActive
                        ? "§aGlow is currently ENABLED\n§7Click to remove enchantment glint shimmer."
                        : "§7Glow is currently DISABLED\n§7Click to add purple enchantment shimmer.";
            }

            int contentY = ctrlY + searchH + 6;
            int contentH = winY + winH - contentY - 8;
            previewPanelY = contentY;
            previewPanelH = contentH;

            RenderUtils.fillRoundedRect(g, gridX, contentY, gridW, contentH, 4, 0x00000000);
            RenderUtils.drawGradientOutline(g, gridX, contentY, gridW, contentH, 4, 0x44FFFFFF, 0x44FFFFFF);

            int totalPages = getTotalPages();
            if (currentPage >= totalPages) currentPage = totalPages - 1;
            if (currentPage < 0) currentPage = 0;

            int tileSize = 30;
            int tileGap = 4;
            int gridInnerX = gridX + (gridW - (COLS * tileSize + (COLS - 1) * tileGap)) / 2;
            int gridInnerY = contentY + 6 + (contentH - 36 - (ROWS * tileSize + (ROWS - 1) * tileGap)) / 2;

            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(filteredOptions.size(), startIndex + ITEMS_PER_PAGE);

            for (int i = startIndex; i < endIndex; i++) {
                int localIdx = i - startIndex;
                int col = localIdx % COLS;
                int row = localIdx / COLS;
                int tx = gridInnerX + col * (tileSize + tileGap);
                int ty = gridInnerY + row * (tileSize + tileGap);

                WishModelCatalog.ModelOption opt = filteredOptions.get(i);
                boolean isCurrentModel = opt.id.equalsIgnoreCase(assignedModelId);
                boolean tileHov = mx >= tx && mx < tx + tileSize && my >= ty && my < ty + tileSize;

                if (isCurrentModel) {
                    g.fill(tx, ty, tx + tileSize, ty + tileSize, 0x40FFFFFF);
                    RenderUtils.drawGradientOutline(g, tx, ty, tileSize, tileSize, 2, colAccent, 0x22222222);
                } else if (tileHov) {
                    g.fill(tx, ty, tx + tileSize, ty + tileSize, 0x25FFFFFF);
                    RenderUtils.drawGradientOutline(g, tx, ty, tileSize, tileSize, 2, colAccentDim, 0x22222222);
                }

                g.fakeItem(opt.stack, tx + 7, ty + 7);

                if (tileHov) {
                    hoveredTooltipTitle = opt.name;
                    String desc = "§bID: §f" + opt.id;
                    if (opt.isTexturePack) {
                        desc = "§d✦ Texture Pack Model\n" + desc;
                    }
                    hoveredTooltipDesc = desc + "\n§aClick to apply as model!";
                }
            }

            int sep2Y = contentY + contentH - 24;
            g.fill(gridX, sep2Y, gridX + gridW, sep2Y + 1, 0x22FFFFFF);

            int pageBarY = sep2Y + 4;
            int centerX = gridX + gridW / 2;
            boolean canPrev = currentPage > 0;
            boolean canNext = currentPage < totalPages - 1;

            renderPageNavButton(g, getPageJumpPrevX(centerX), pageBarY, PAGE_JUMP_BTN_W, PAGE_BTN_H,
                    "\u25C0\u25C0", canPrev, mx, my, colAccentDim, "-5 Pages", "Jump 5 pages backward (or Shift+Scroll)");
            renderPageNavButton(g, getPagePrevX(centerX), pageBarY, PAGE_BTN_W, PAGE_BTN_H,
                    "\u25C0", canPrev, mx, my, colAccentDim, null, null);

            String pageText = (currentPage + 1) + " / " + totalPages;
            g.text(font, pageText, gridX + (gridW - font.width(pageText)) / 2, pageBarY + 3, 0xFFAAAAAA);

            renderPageNavButton(g, getPageNextX(centerX), pageBarY, PAGE_BTN_W, PAGE_BTN_H,
                    "\u25B6", canNext, mx, my, colAccentDim, null, null);
            renderPageNavButton(g, getPageJumpNextX(centerX), pageBarY, PAGE_JUMP_BTN_W, PAGE_BTN_H,
                    "\u25B6\u25B6", canNext, mx, my, colAccentDim, "+5 Pages", "Jump 5 pages forward (or Shift+Scroll)");
        } else {
            previewPanelY = ctrlY;
            previewPanelH = winY + winH - ctrlY - 8;
            nameTab.render(g, font, gridX, ctrlY, gridW, previewPanelH, mx, my, colAccent, colAccentDim);
        }

        renderPreviewPanel(g, prevX, previewPanelY, PREVIEW_W, previewPanelH,
                assignedModelId, hasOverride, isGlowActive, customNameComp, mx, my, colAccent);

        if (activeSectionTab == 1) {
            if (hoveredTooltipTitle == null && nameTab.getHoveredTitle() != null) {
                hoveredTooltipTitle = nameTab.getHoveredTitle();
                hoveredTooltipDesc = nameTab.getHoveredDesc();
            }
        }

        if (hoveredTooltipTitle != null) {
            List<Component> tooltipLines = new ArrayList<>();
            tooltipLines.add(Component.literal("§6" + hoveredTooltipTitle));
            if (hoveredTooltipDesc != null) {
                for (String line : LINE_SPLIT_PATTERN.split(hoveredTooltipDesc)) {
                    tooltipLines.add(Component.literal("§7" + line));
                }
            }
            int tw = 0;
            for (Component c : tooltipLines) tw = Math.max(tw, font.width(c.getString()));
            tw += 12;
            int th = tooltipLines.size() * 11 + 8;
            int tpx = Math.min(this.width - tw - 4, mx + 10);
            int tpy = Math.min(this.height - th - 4, my + 10);

            RenderUtils.fillRoundedRect(g, tpx, tpy, tw, th, 4, 0x9814141E);
            RenderUtils.drawGradientOutline(g, tpx, tpy, tw, th, 4, colAccent, 0x33FFFFFF);
            for (int i = 0; i < tooltipLines.size(); i++) {
                g.text(font, tooltipLines.get(i).getString(), tpx + 6, tpy + 5 + i * 11, 0xFFFFFFFF);
            }
        }
    }

    private void renderTopTabButton(
            GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
            boolean active, int mx, int my, int colAccent, int colAccentDim) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        RenderUtils.fillRoundedRect(g, x, y, w, h, 3, active ? colAccentDim : (hov ? 0x22FFFFFF : 0x00000000));
        RenderUtils.drawGradientOutline(g, x, y, w, h, 3, active ? colAccent : (hov ? 0x88FFFFFF : 0x44FFFFFF), 0x22000000);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 4, active ? 0xFFFFFFFF : 0xFFBBBBCC);
    }

    private void renderPageNavButton(
            GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
            boolean enabled, int mx, int my, int colAccentDim, String tipTitle, String tipDesc) {
        boolean hov = isInRect(mx, my, x, y, w, h);
        int bg = (enabled && hov) ? colAccentDim : (enabled ? 0x16FFFFFF : 0x08FFFFFF);
        RenderUtils.fillRoundedRect(g, x, y, w, h, 2, bg);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 3, enabled ? 0xFFFFFFFF : 0xFF555555);
        if (hov && tipTitle != null) {
            hoveredTooltipTitle = tipTitle;
            hoveredTooltipDesc = tipDesc;
        }
    }

    private int getTotalPages() { return Math.max(1, (filteredOptions.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE); }
    private void stepPage(int delta) { currentPage = Math.max(0, Math.min(getTotalPages() - 1, currentPage + delta)); }
    private static int getPagePrevX(int centerX) { return centerX - PAGE_CENTER_HALF_GAP - PAGE_BTN_W; }
    private static int getPageJumpPrevX(int centerX) { return getPagePrevX(centerX) - PAGE_BTN_GAP - PAGE_JUMP_BTN_W; }
    private static int getPageNextX(int centerX) { return centerX + PAGE_CENTER_HALF_GAP; }
    private static int getPageJumpNextX(int centerX) { return getPageNextX(centerX) + PAGE_BTN_W + PAGE_BTN_GAP; }
    private static boolean isInRect(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private void renderPreviewPanel(
            GuiGraphicsExtractor g, int prevX, int contentY, int previewW, int contentH,
            String assignedModelId, boolean hasOverride, boolean isGlowActive,
            Component customNameComp, int mx, int my, int colAccent) {
        RenderUtils.fillRoundedRect(g, prevX, contentY, previewW, contentH, 4, 0x00000000);
        RenderUtils.drawGradientOutline(g, prevX, contentY, previewW, contentH, 4, colAccent, colAccent);

        g.text(font, "MODEL PREVIEW", prevX + (previewW - font.width("MODEL PREVIEW")) / 2, contentY + 8, colAccent);

        boolean previewDirty = cachedPreviewDisplayStack == null
                || cachedPreviewHasOverride != hasOverride
                || cachedPreviewGlow == null
                || cachedPreviewGlow.booleanValue() != isGlowActive
                || (hasOverride && !assignedModelId.equals(cachedPreviewModelId));
        if (previewDirty) {
            cachedPreviewHasOverride = hasOverride;
            cachedPreviewGlow = isGlowActive;
            ItemStack baseStack;
            if (hasOverride) {
                cachedPreviewModelId = assignedModelId;
                cachedPreviewStack = CustomModelManager.createItemStackForModel(assignedModelId, heldStack);
                baseStack = (cachedPreviewStack != null) ? cachedPreviewStack.copy() : heldStack.copy();
            } else {
                cachedPreviewModelId = null;
                cachedPreviewStack = null;
                baseStack = heldStack.copy();
            }
            baseStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, isGlowActive);
            cachedPreviewDisplayStack = baseStack;
            String rawName = CustomModelManager.getOriginalHoverName(baseStack).getString();
            cachedPreviewName = font.width(rawName) > previewW - 14
                    ? font.plainSubstrByWidth(rawName, previewW - 20) + ".."
                    : rawName;
            cachedPreviewNameWidth = font.width(cachedPreviewName);
        }

        int centerPrevX = prevX + previewW / 2;
        int centerPrevY = contentY + 68;
        float bounce = (float) Math.sin((System.currentTimeMillis() % 2400L) / 2400.0f * Math.PI * 2) * 2.5f;

        g.pose().pushMatrix();
        g.pose().translate(centerPrevX, centerPrevY + bounce);
        g.pose().scale(3.2f, 3.2f);
        g.fakeItem(cachedPreviewDisplayStack, -8, -8);
        g.pose().popMatrix();

        int nameY = contentY + 110;
        if (customNameComp != null) {
            g.enableScissor(prevX + 5, nameY - 2, prevX + previewW - 5, nameY + 12);
            int nameDrawX = cachedPreviewCustomWidth <= previewW - 12
                    ? prevX + (previewW - cachedPreviewCustomWidth) / 2
                    : prevX + 6;
            g.text(font, customNameComp, nameDrawX, nameY, 0xFFFFFFFF);
            g.disableScissor();
        } else {
            g.text(font, cachedPreviewName, prevX + (previewW - cachedPreviewNameWidth) / 2, nameY, 0xFFFFFFFF);
        }

        String statusTxt = hasOverride ? "§a✓ Custom Model Active" : "§7Original Texture";
        g.text(font, statusTxt, prevX + (previewW - font.width(statusTxt)) / 2, contentY + 125, 0xFFFFFFFF);
        if (customNameComp != null) {
            String nameBadge = "§d✦ Custom Name Active";
            g.text(font, nameBadge, prevX + (previewW - font.width(nameBadge)) / 2, contentY + 137, 0xFFFFFFFF);
        }

        int togW = previewW - 16;
        int togH = 20;
        int togX = prevX + 8;
        int togY = contentY + contentH - 26;
        boolean togHov = mx >= togX && mx < togX + togW && my >= togY && my < togY + togH;
        boolean isModEnabled = ModConfig.INSTANCE.enableCustomItemModels;

        RenderUtils.fillRoundedRect(g, togX, togY, togW, togH, 3, togHov ? 0x33FFFFFF : 0x00000000);
        RenderUtils.drawGradientOutline(g, togX, togY, togW, togH, 3, isModEnabled ? 0xFF55FF55 : 0xFFFF5555, 0x22000000);
        String togLabel = isModEnabled ? "Mod: §aENABLED" : "Mod: §cDISABLED";
        g.text(font, togLabel, togX + (togW - font.width(togLabel)) / 2, togY + 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (event == null || event.button() != 0) return super.mouseClicked(event, focused);
        double mx = event.x();
        double my = event.y();

        int winW = getWinW();
        int winH = getWinH();
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int closeBtnSize = 18;
        int closeX = winX + winW - closeBtnSize - 12;
        int closeY = winY + 10;
        if (mx >= closeX && mx <= closeX + closeBtnSize && my >= closeY && my <= closeY + closeBtnSize) {
            this.onClose();
            return true;
        }

        int bannerY = winY + 32;
        int bannerH = 38;
        int bannerW = winW - 28;
        int bannerX = winX + 14;
        int ctrlY = bannerY + bannerH + 8;
        int gridW = bannerW - PREVIEW_W - 8;
        int gridX = bannerX;

        if (activeSectionTab == 1 && nameTab.mouseClicked(gridX, ctrlY, gridW, mx, my)) {
            searchFocused = false;
            return true;
        }

        int tabBtnH = 16;
        int tabBtnY = winY + 11;
        int tabNameW = 86;
        int tabModelsW = 54;
        int tabNameX = closeX - tabNameW - 8;
        int tabModelsX = tabNameX - tabModelsW - 6;

        if (mx >= tabModelsX && mx < tabModelsX + tabModelsW && my >= tabBtnY && my < tabBtnY + tabBtnH) {
            activeSectionTab = 0;
            return true;
        }
        if (mx >= tabNameX && mx < tabNameX + tabNameW && my >= tabBtnY && my < tabBtnY + tabBtnH) {
            activeSectionTab = 1;
            searchFocused = false;
            nameTab.syncFromConfig();
            return true;
        }

        int resetBtnW = 54;
        int resetBtnH = 22;
        int resetBtnX = bannerX + bannerW - resetBtnW - 8;
        int resetBtnY = bannerY + (bannerH - resetBtnH) / 2;
        if (mx >= resetBtnX && mx < resetBtnX + resetBtnW && my >= resetBtnY && my < resetBtnY + resetBtnH) {
            CustomModelManager.removeOverride(targetKey);
            nameTab.syncFromConfig();
            return true;
        }

        int searchH = 20;
        int previewPanelY = activeSectionTab == 0 ? (ctrlY + searchH + 6) : ctrlY;
        int previewPanelH = winY + winH - previewPanelY - 8;
        int prevX = gridX + gridW + 8;
        int togW = PREVIEW_W - 16;
        int togH = 20;
        int togX = prevX + 8;
        int togY = previewPanelY + previewPanelH - 26;
        if (mx >= togX && mx < togX + togW && my >= togY && my < togY + togH) {
            ModConfig.INSTANCE.enableCustomItemModels = !ModConfig.INSTANCE.enableCustomItemModels;
            ModConfig.INSTANCE.save();
            return true;
        }

        if (activeSectionTab == 1) {
            searchFocused = false;
            return super.mouseClicked(event, focused);
        }

        int glowBtnW = 54;
        int glowBtnH = 20;
        int searchX = bannerX;
        int searchW = bannerW - glowBtnW - 6;
        int glowBtnX = searchX + searchW + 6;
        int glowBtnY = ctrlY;

        if (mx >= searchX && mx < searchX + searchW && my >= ctrlY && my < ctrlY + searchH) {
            searchFocused = true;
            return true;
        }

        if (mx >= glowBtnX && mx < glowBtnX + glowBtnW && my >= glowBtnY && my < glowBtnY + glowBtnH) {
            CustomModelManager.setGlowOverride(targetKey, !isItemGlowActive());
            return true;
        }

        int contentY = ctrlY + searchH + 6;
        int contentH = winY + winH - contentY - 8;

        int sep2Y = contentY + contentH - 24;
        int pageBarY = sep2Y + 4;
        int centerX = gridX + gridW / 2;
        int stepClick = event.hasShiftDown() ? PAGE_JUMP_STEP : 1;

        if (isInRect(mx, my, getPageJumpPrevX(centerX), pageBarY, PAGE_JUMP_BTN_W, PAGE_BTN_H)) {
            stepPage(-PAGE_JUMP_STEP);
            return true;
        }
        if (isInRect(mx, my, getPagePrevX(centerX), pageBarY, PAGE_BTN_W, PAGE_BTN_H)) {
            stepPage(-stepClick);
            return true;
        }
        if (isInRect(mx, my, getPageNextX(centerX), pageBarY, PAGE_BTN_W, PAGE_BTN_H)) {
            stepPage(stepClick);
            return true;
        }
        if (isInRect(mx, my, getPageJumpNextX(centerX), pageBarY, PAGE_JUMP_BTN_W, PAGE_BTN_H)) {
            stepPage(PAGE_JUMP_STEP);
            return true;
        }

        int tileSize = 30;
        int tileGap = 4;
        int gridInnerX = gridX + (gridW - (COLS * tileSize + (COLS - 1) * tileGap)) / 2;
        int gridInnerY = contentY + 6 + (contentH - 36 - (ROWS * tileSize + (ROWS - 1) * tileGap)) / 2;

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(filteredOptions.size(), startIndex + ITEMS_PER_PAGE);

        for (int i = startIndex; i < endIndex; i++) {
            int localIdx = i - startIndex;
            int col = localIdx % COLS;
            int row = localIdx / COLS;
            int tx = gridInnerX + col * (tileSize + tileGap);
            int ty = gridInnerY + row * (tileSize + tileGap);

            if (mx >= tx && mx < tx + tileSize && my >= ty && my < ty + tileSize) {
                WishModelCatalog.ModelOption opt = filteredOptions.get(i);
                if (opt.id.equals("wish:custom_pack_input")) {
                    searchFocused = true;
                    searchQuery = "";
                    updateFilteredOptions();
                    return true;
                }
                CustomModelManager.setOverride(targetKey, opt.id);
                return true;
            }
        }

        searchFocused = false;
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeSectionTab == 0) {
            int step = (this.minecraft != null && this.minecraft.hasShiftDown()) ? PAGE_JUMP_STEP : 1;
            if (scrollY < 0) {
                stepPage(step);
            } else if (scrollY > 0) {
                stepPage(-step);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (activeSectionTab == 1 && nameTab.charTyped(event)) {
            return true;
        }
        char chr = (char) event.codepoint();
        if (activeSectionTab == 0 && searchFocused && chr >= 32 && chr <= 126) {
            searchQuery += chr;
            updateFilteredOptions();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (activeSectionTab == 1 && nameTab.keyPressed(event)) {
            return true;
        }
        int key = event.key();

        if (activeSectionTab == 0 && event.isPaste()) {
            String clip = this.minecraft.keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                clip = NEWLINE_PATTERN.matcher(clip).replaceAll("");
                if (searchFocused) {
                    searchQuery += clip;
                    updateFilteredOptions();
                    return true;
                }
            }
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (activeSectionTab == 0 && searchFocused && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                updateFilteredOptions();
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (activeSectionTab == 0 && searchFocused) {
                searchFocused = false;
                return true;
            }
            this.onClose();
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
            return true;
        }

        if (activeSectionTab == 0) {
            int step = event.hasShiftDown() ? PAGE_JUMP_STEP : 1;
            if (key == GLFW.GLFW_KEY_PAGE_UP || (!searchFocused && key == GLFW.GLFW_KEY_LEFT)) {
                stepPage(key == GLFW.GLFW_KEY_PAGE_UP ? -PAGE_JUMP_STEP : -step);
                return true;
            }
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || (!searchFocused && key == GLFW.GLFW_KEY_RIGHT)) {
                stepPage(key == GLFW.GLFW_KEY_PAGE_DOWN ? PAGE_JUMP_STEP : step);
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return activeSectionTab != 1 || !nameTab.isCapturingKeyboard();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.lastScreen != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        } else {
            super.onClose();
        }
    }
}
