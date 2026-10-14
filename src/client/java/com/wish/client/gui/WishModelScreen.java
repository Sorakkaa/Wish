package com.wish.client.gui;

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
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import com.wish.client.mixin.ModelManagerAccessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class WishModelScreen extends Screen {

    private final Screen lastScreen;
    private final ItemStack heldStack;
    private final String targetKey;
    private final String targetDisplayName;

    // Search
    private String searchQuery = "";
    private boolean searchFocused = false;

    // Pagination: 8 cols x 4 rows = 32 items (optimal FPS and buttery smooth rendering!)
    private int currentPage = 0;
    private static final int COLS = 8;
    private static final int ROWS = 4;
    private static final int ITEMS_PER_PAGE = COLS * ROWS; // 32 items

    // Cached preview stack to avoid object creation during render
    private String cachedPreviewModelId = null;
    private ItemStack cachedPreviewStack = null;

    // Tooltip
    private String hoveredTooltipTitle = null;
    private String hoveredTooltipDesc = null;

    public static class ModelOption {
        public final String id;
        public final String name;
        public final ItemStack stack;
        public final boolean isTexturePack;

        public ModelOption(String id, String name, ItemStack stack, boolean isTexturePack) {
            this.id = id;
            this.name = name;
            this.stack = stack;
            this.isTexturePack = isTexturePack;
        }

        public ModelOption(String id, String name, ItemStack stack) {
            this(id, name, stack, false);
        }
    }

    private static final List<ModelOption> ALL_OPTIONS = new ArrayList<>();
    private static final List<ModelOption> PACK_OPTIONS = new ArrayList<>();
    private static boolean packModelsLoaded = false;
    private static final ModelOption CUSTOM_INPUT_OPTION = new ModelOption(
        "wish:custom_pack_input", 
        "§d+ Enter Pack Model ID", 
        new ItemStack(Items.NAME_TAG), 
        true
    );

    private final List<ModelOption> filteredOptions = new ArrayList<>();

    static {
        // Vanilla Minecraft items
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;

            String path = id.getPath().toLowerCase(Locale.ROOT);
            String name = new ItemStack(item).getHoverName().getString();
            if (name == null || name.isEmpty()) {
                name = path;
            }

            ALL_OPTIONS.add(new ModelOption(id.toString(), name, new ItemStack(item)));
        }
    }

    public static synchronized void loadTexturePackModels() {
        PACK_OPTIONS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getModelManager() == null) return;

        Set<String> seenIds = new HashSet<>();
        Set<String> vanillaKeys = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier vid = BuiltInRegistries.ITEM.getKey(item);
            if (vid != null) {
                vanillaKeys.add(vid.toString());
                vanillaKeys.add(vid.getPath());
            }
        }

        try {
            var bakedMap = ((ModelManagerAccessor) mc.getModelManager()).getBakedItemStackModels();
            if (bakedMap != null) {
                for (Map.Entry<Identifier, ItemModel> entry : bakedMap.entrySet()) {
                    Identifier resId = entry.getKey();
                    ItemModel model = entry.getValue();

                    // Discard any missing or unbaked placeholder models
                    if (model == null || model instanceof MissingItemModel) {
                        continue;
                    }

                    String ns = resId.getNamespace();
                    String path = resId.getPath();

                    // Filter out animation frames, charge states, and pull frames
                    if (path.contains("_pulling") || path.endsWith("_cast") || path.contains("_firework") 
                            || path.contains("_arrow") || path.contains("_charged") || path.contains("_standby")) {
                        continue;
                    }

                    // Skip standard vanilla items (they are already in ALL_OPTIONS)
                    if (ns.equals("minecraft") && vanillaKeys.contains(path)) {
                        continue;
                    }

                    String fullModelId = resId.toString();
                    if (!seenIds.add(fullModelId)) continue;

                    String rawName = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
                    String displayName = rawName.isEmpty() ? fullModelId : Character.toUpperCase(rawName.charAt(0)) + rawName.substring(1);

                    ItemStack iconStack = new ItemStack(Items.DIAMOND_SWORD);
                    iconStack.set(DataComponents.ITEM_MODEL, resId);

                    PACK_OPTIONS.add(new ModelOption(fullModelId, displayName + " §7(" + ns + ")", iconStack, true));
                }
            }
        } catch (Exception ignored) {}

        // Sort pack options alphabetically by display name
        PACK_OPTIONS.sort(Comparator.comparing(a -> a.name));
        packModelsLoaded = true;
    }

    public WishModelScreen(Screen lastScreen) {
        super(Component.literal("Item Model Customizer"));
        this.lastScreen = lastScreen;

        if (!packModelsLoaded) {
            loadTexturePackModels();
        }

        var player = Minecraft.getInstance().player;
        if (player != null && !player.getMainHandItem().isEmpty()) {
            this.heldStack = player.getMainHandItem().copy();
        } else {
            this.heldStack = new ItemStack(Items.DIAMOND_PICKAXE);
        }

        this.targetKey = CustomModelManager.getItemTargetKey(this.heldStack);
        this.targetDisplayName = CustomModelManager.getItemDisplayName(this.heldStack);

        updateFilteredOptions();
    }

    private void updateFilteredOptions() {
        filteredOptions.clear();
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);

        if (q.isEmpty()) {
            filteredOptions.addAll(ALL_OPTIONS);
            filteredOptions.addAll(PACK_OPTIONS);
            filteredOptions.add(CUSTOM_INPUT_OPTION);
        } else {
            // If user typed or pasted a direct pack model ID or namespace (e.g. contains ":" or "/")
            if (q.contains(":") || q.contains("/")) {
                Identifier typedId = Identifier.tryParse(q.contains(":") ? q : "minecraft:" + q);
                if (typedId != null) {
                    var mm = Minecraft.getInstance().getModelManager();
                    if (mm != null) {
                        var model = mm.getItemModel(typedId);
                        if (model != null && !(model instanceof MissingItemModel)) {
                            ItemStack previewStack = new ItemStack(heldStack.getItem());
                            previewStack.set(DataComponents.ITEM_MODEL, typedId);
                            filteredOptions.add(new ModelOption(typedId.toString(), "§d[Custom Pack ID] §f" + typedId, previewStack, true));
                        }
                    }
                }
            }

            for (ModelOption opt : ALL_OPTIONS) {
                if (opt.name.toLowerCase(Locale.ROOT).contains(q) || opt.id.toLowerCase(Locale.ROOT).contains(q)) {
                    filteredOptions.add(opt);
                }
            }
            for (ModelOption opt : PACK_OPTIONS) {
                if (opt.name.toLowerCase(Locale.ROOT).contains(q) || opt.id.toLowerCase(Locale.ROOT).contains(q)) {
                    filteredOptions.add(opt);
                }
            }
            if ("texture pack model enter id".contains(q)) {
                filteredOptions.add(CUSTOM_INPUT_OPTION);
            }
        }
        currentPage = 0;
    }

    private int getAccentColor() {
        if (ModConfig.INSTANCE.menuAccentColor != null && ModConfig.INSTANCE.menuAccentColor.startsWith("#") && ModConfig.INSTANCE.menuAccentColor.length() == 7) {
            try {
                return 0xFF000000 | Integer.parseInt(ModConfig.INSTANCE.menuAccentColor.substring(1), 16);
            } catch (Exception ignored) {}
        }
        return 0xFF9D00FF;
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

    private void renderScreenContent(GuiGraphicsExtractor g, int mx, int my) {
        hoveredTooltipTitle = null;
        hoveredTooltipDesc = null;

        int colAccent = getAccentColor();
        int colAccentDim = (colAccent & 0x00FFFFFF) | 0x44000000;

        int winW = Math.min(this.width - 24, 560);
        int winH = Math.min(this.height - 24, 350);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Transparent container with sleek neon accent outline
        RenderUtils.fillRoundedRect(g, winX, winY, winW, winH, 6, 0x00000000);
        RenderUtils.drawGradientOutline(g, winX, winY, winW, winH, 6, colAccent, colAccent);

        // Header Title
        g.text(font, "§lITEM MODEL CUSTOMIZER", winX + 16, winY + 12, colAccent);

        // Close 'X' Button
        int closeBtnSize = 18;
        int closeX = winX + winW - closeBtnSize - 12;
        int closeY = winY + 10;
        boolean closeHover = mx >= closeX && mx <= closeX + closeBtnSize && my >= closeY && my <= closeY + closeBtnSize;
        RenderUtils.fillRoundedRect(g, closeX, closeY, closeBtnSize, closeBtnSize, 4, closeHover ? 0xAAFF3333 : 0x00000000);
        RenderUtils.drawGradientOutline(g, closeX, closeY, closeBtnSize, closeBtnSize, 4, closeHover ? 0xFFFF4444 : 0x44FFFFFF, 0x11000000);
        g.text(font, "✕", closeX + (closeBtnSize - font.width("✕")) / 2 + 1, closeY + 4, 0xFFFFFFFF);

        // =========================================================================
        // TARGET ITEM BANNER (Held item)
        // =========================================================================
        int bannerY = winY + 32;
        int bannerH = 38;
        int bannerW = winW - 28;
        int bannerX = winX + 14;

        RenderUtils.fillRoundedRect(g, bannerX, bannerY, bannerW, bannerH, 4, 0x00000000);
        RenderUtils.drawGradientOutline(g, bannerX, bannerY, bannerW, bannerH, 4, 0x55FFFFFF, 0x55FFFFFF);

        // Render target held item icon (2x scale)
        g.pose().pushMatrix();
        g.pose().translate(bannerX + 6, bannerY + 3);
        g.pose().scale(2.0f, 2.0f);
        g.fakeItem(heldStack, 0, 0);
        g.pose().popMatrix();

        // Check active model override
        String assignedModelId = CustomModelManager.getOverride(targetKey);
        boolean hasOverride = (assignedModelId != null && !assignedModelId.isEmpty() && !"default".equalsIgnoreCase(assignedModelId));

        int infoTextX = bannerX + 44;
        g.text(font, "Target: §f" + targetDisplayName, infoTextX, bannerY + 7, 0xFFFFFFFF);

        String modelStatus;
        if (hasOverride) {
            String mName = assignedModelId;
            Identifier mid = Identifier.tryParse(assignedModelId.contains(":") ? assignedModelId : "minecraft:" + assignedModelId);
            if (mid != null) {
                Item mItem = BuiltInRegistries.ITEM.getValue(mid);
                if (mItem != null) mName = new ItemStack(mItem).getHoverName().getString();
            }
            modelStatus = "Model: §a" + mName;
        } else {
            modelStatus = "Model: §7Original / None";
        }
        g.text(font, modelStatus, infoTextX, bannerY + 21, 0xFFDDDDDD);

        // Reset Button
        int resetBtnW = 54;
        int resetBtnH = 22;
        int resetBtnX = bannerX + bannerW - resetBtnW - 8;
        int resetBtnY = bannerY + (bannerH - resetBtnH) / 2;
        boolean resetHov = mx >= resetBtnX && mx < resetBtnX + resetBtnW && my >= resetBtnY && my < resetBtnY + resetBtnH;
        RenderUtils.fillRoundedRect(g, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 3, resetHov ? 0x44FF3333 : 0x00000000);
        RenderUtils.drawGradientOutline(g, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 3, hasOverride ? (resetHov ? 0xFFFF5555 : 0x88FF5555) : 0x33666666, 0x11000000);
        g.text(font, "Reset", resetBtnX + (resetBtnW - font.width("Reset")) / 2, resetBtnY + 6, hasOverride ? 0xFFFF8888 : 0xFF777777);

        // =========================================================================
        // CONTROL BAR: Search Bar + Glow Toggle Button
        // =========================================================================
        int ctrlY = bannerY + bannerH + 8;
        int searchH = 20;
        int glowBtnW = 54;
        int glowBtnH = 20;
        int searchX = bannerX;
        int searchW = bannerW - glowBtnW - 6;
        int glowBtnX = searchX + searchW + 6;
        int glowBtnY = ctrlY;

        // Search Bar
        RenderUtils.fillRoundedRect(g, searchX, ctrlY, searchW, searchH, 3, searchFocused ? 0x22FFFFFF : 0x00000000);
        RenderUtils.drawGradientOutline(g, searchX, ctrlY, searchW, searchH, 3, searchFocused ? colAccent : 0x44FFFFFF, 0x11000000);
        String searchPrompt = searchQuery.isEmpty() && !searchFocused ? "§7Search items or pack models..." : searchQuery + (searchFocused && (System.currentTimeMillis() % 1000 < 500) ? "§f|" : "");
        g.text(font, searchPrompt, searchX + 8, ctrlY + 6, 0xFFFFFFFF);

        // Glow Toggle Button (to the right of search bar)
        Boolean customGlow = CustomModelManager.getGlowOverride(targetKey);
        boolean isGlowActive;
        if (customGlow != null) {
            isGlowActive = customGlow;
        } else {
            isGlowActive = heldStack.isEnchanted() || (heldStack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) && Boolean.TRUE.equals(heldStack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
        }

        boolean glowHov = mx >= glowBtnX && mx < glowBtnX + glowBtnW && my >= glowBtnY && my < glowBtnY + glowBtnH;
        int glowBg = isGlowActive ? (glowHov ? 0x66FF55FF : 0x33FF55FF) : (glowHov ? 0x22FFFFFF : 0x00000000);
        int glowBorder = isGlowActive ? 0xFFFF77FF : (glowHov ? 0x88FFFFFF : 0x44FFFFFF);
        RenderUtils.fillRoundedRect(g, glowBtnX, glowBtnY, glowBtnW, glowBtnH, 3, glowBg);
        RenderUtils.drawGradientOutline(g, glowBtnX, glowBtnY, glowBtnW, glowBtnH, 3, glowBorder, glowBorder);
        String glowLabel = isGlowActive ? "Glow: §dON" : "Glow: §7OFF";
        g.text(font, glowLabel, glowBtnX + (glowBtnW - font.width(glowLabel)) / 2, glowBtnY + 6, 0xFFFFFFFF);

        if (glowHov) {
            hoveredTooltipTitle = "Enchantment Glow (Glint)";
            hoveredTooltipDesc = isGlowActive ? "§aGlow is currently ENABLED\n§7Click to remove enchantment glint shimmer." : "§7Glow is currently DISABLED\n§7Click to add purple enchantment shimmer.";
        }

        // NO TOP DIVIDER LINE (image 1 bar removed!)
        int contentY = ctrlY + searchH + 6;
        int contentH = winY + winH - contentY - 8;

        int previewW = 140;
        int gridW = bannerW - previewW - 8;
        int gridX = bannerX;

        // Transparent Grid Container
        RenderUtils.fillRoundedRect(g, gridX, contentY, gridW, contentH, 4, 0x00000000);
        RenderUtils.drawGradientOutline(g, gridX, contentY, gridW, contentH, 4, 0x44FFFFFF, 0x44FFFFFF);

        // Paginated Grid: 8 columns x 4 rows = 32 items
        int totalPages = Math.max(1, (filteredOptions.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
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

            ModelOption opt = filteredOptions.get(i);
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

        // Bottom Divider Line above pagination
        int sep2Y = contentY + contentH - 24;
        g.fill(gridX, sep2Y, gridX + gridW, sep2Y + 1, 0x22FFFFFF);

        // Pagination Controls
        int pageBarY = sep2Y + 4;
        int pageBtnW = 22;
        int pageBtnH = 16;
        int pagePrevX = gridX + gridW / 2 - 45;
        int pageNextX = gridX + gridW / 2 + 25;

        boolean prevHov = mx >= pagePrevX && mx < pagePrevX + pageBtnW && my >= pageBarY && my < pageBarY + pageBtnH;
        RenderUtils.fillRoundedRect(g, pagePrevX, pageBarY, pageBtnW, pageBtnH, 2, prevHov ? colAccentDim : 0x00000000);
        g.text(font, "◀", pagePrevX + 7, pageBarY + 3, currentPage > 0 ? 0xFFFFFFFF : 0xFF555555);

        String pageText = (currentPage + 1) + " / " + totalPages;
        g.text(font, pageText, gridX + (gridW - font.width(pageText)) / 2, pageBarY + 3, 0xFFAAAAAA);

        boolean nextHov = mx >= pageNextX && mx < pageNextX + pageBtnW && my >= pageBarY && my < pageBarY + pageBtnH;
        RenderUtils.fillRoundedRect(g, pageNextX, pageBarY, pageBtnW, pageBtnH, 2, nextHov ? colAccentDim : 0x00000000);
        g.text(font, "▶", pageNextX + 7, pageBarY + 3, currentPage < totalPages - 1 ? 0xFFFFFFFF : 0xFF555555);

        // =========================================================================
        // RIGHT: Live Model Preview Box (Transparent)
        // =========================================================================
        int prevX = gridX + gridW + 8;
        RenderUtils.fillRoundedRect(g, prevX, contentY, previewW, contentH, 4, 0x00000000);
        RenderUtils.drawGradientOutline(g, prevX, contentY, previewW, contentH, 4, colAccent, colAccent);

        g.text(font, "MODEL PREVIEW", prevX + (previewW - font.width("MODEL PREVIEW")) / 2, contentY + 8, colAccent);

        ItemStack displayStack;
        if (hasOverride) {
            if (!assignedModelId.equals(cachedPreviewModelId) || cachedPreviewStack == null) {
                cachedPreviewModelId = assignedModelId;
                cachedPreviewStack = CustomModelManager.createItemStackForModel(assignedModelId, heldStack);
            }
            displayStack = (cachedPreviewStack != null) ? cachedPreviewStack.copy() : heldStack.copy();
        } else {
            cachedPreviewModelId = null;
            cachedPreviewStack = null;
            displayStack = heldStack.copy();
        }

        // Apply glow effect to preview display stack
        displayStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, isGlowActive);

        int centerPrevX = prevX + previewW / 2;
        int centerPrevY = contentY + 68;
        float bounce = (float) Math.sin((System.currentTimeMillis() % 2400L) / 2400.0f * Math.PI * 2) * 2.5f;

        g.pose().pushMatrix();
        g.pose().translate(centerPrevX, centerPrevY + bounce);
        g.pose().scale(3.2f, 3.2f);
        g.fakeItem(displayStack, -8, -8);
        g.pose().popMatrix();

        String prevName = displayStack.getHoverName().getString();
        if (font.width(prevName) > previewW - 14) {
            prevName = font.plainSubstrByWidth(prevName, previewW - 20) + "..";
        }
        g.text(font, prevName, prevX + (previewW - font.width(prevName)) / 2, contentY + 110, 0xFFFFFFFF);

        String statusTxt = hasOverride ? "§a✓ Custom Model Active" : "§7Original Texture";
        g.text(font, statusTxt, prevX + (previewW - font.width(statusTxt)) / 2, contentY + 124, 0xFFFFFFFF);

        // Toggle custom models button
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

        // Tooltip rendering
        if (hoveredTooltipTitle != null) {
            List<Component> tooltipLines = new ArrayList<>();
            tooltipLines.add(Component.literal("§6" + hoveredTooltipTitle));
            if (hoveredTooltipDesc != null) {
                for (String line : hoveredTooltipDesc.split("\n")) {
                    tooltipLines.add(Component.literal("§7" + line));
                }
            }
            int tw = 0;
            for (Component c : tooltipLines) tw = Math.max(tw, font.width(c.getString()));
            tw += 12;
            int th = tooltipLines.size() * 11 + 8;
            int tpx = Math.min(this.width - tw - 4, mx + 10);
            int tpy = Math.min(this.height - th - 4, my + 10);

            RenderUtils.fillRoundedRect(g, tpx, tpy, tw, th, 4, 0xF0101015);
            RenderUtils.drawGradientOutline(g, tpx, tpy, tw, th, 4, colAccent, 0x33FFFFFF);
            for (int i = 0; i < tooltipLines.size(); i++) {
                g.text(font, tooltipLines.get(i).getString(), tpx + 6, tpy + 5 + i * 11, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mx = event.x();
        double my = event.y();

        int winW = Math.min(this.width - 24, 560);
        int winH = Math.min(this.height - 24, 350);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Close button
        int closeBtnSize = 18;
        int closeX = winX + winW - closeBtnSize - 12;
        int closeY = winY + 10;
        if (mx >= closeX && mx <= closeX + closeBtnSize && my >= closeY && my <= closeY + closeBtnSize) {
            super.onClose();
            return true;
        }

        // Reset button in banner
        int bannerY = winY + 32;
        int bannerH = 38;
        int bannerW = winW - 28;
        int bannerX = winX + 14;

        int resetBtnW = 54;
        int resetBtnH = 22;
        int resetBtnX = bannerX + bannerW - resetBtnW - 8;
        int resetBtnY = bannerY + (bannerH - resetBtnH) / 2;
        if (mx >= resetBtnX && mx < resetBtnX + resetBtnW && my >= resetBtnY && my < resetBtnY + resetBtnH) {
            CustomModelManager.removeOverride(targetKey);
            return true;
        }

        // Search Bar click
        int ctrlY = bannerY + bannerH + 8;
        int searchH = 20;
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

        // Glow Button click
        if (mx >= glowBtnX && mx < glowBtnX + glowBtnW && my >= glowBtnY && my < glowBtnY + glowBtnH) {
            Boolean customGlow = CustomModelManager.getGlowOverride(targetKey);
            boolean currentGlow;
            if (customGlow != null) {
                currentGlow = customGlow;
            } else {
                currentGlow = heldStack.isEnchanted() || (heldStack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) && Boolean.TRUE.equals(heldStack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
            }
            CustomModelManager.setGlowOverride(targetKey, !currentGlow);
            return true;
        }

        int contentY = ctrlY + searchH + 6;
        int contentH = winY + winH - contentY - 8;

        int previewW = 140;
        int gridW = bannerW - previewW - 8;
        int gridX = bannerX;

        // Pagination buttons
        int sep2Y = contentY + contentH - 24;
        int pageBarY = sep2Y + 4;
        int pageBtnW = 22;
        int pageBtnH = 16;
        int pagePrevX = gridX + gridW / 2 - 45;
        int pageNextX = gridX + gridW / 2 + 25;

        int totalPages = Math.max(1, (filteredOptions.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);

        if (mx >= pagePrevX && mx < pagePrevX + pageBtnW && my >= pageBarY && my < pageBarY + pageBtnH) {
            if (currentPage > 0) currentPage--;
            return true;
        }
        if (mx >= pageNextX && mx < pageNextX + pageBtnW && my >= pageBarY && my < pageBarY + pageBtnH) {
            if (currentPage < totalPages - 1) currentPage++;
            return true;
        }

        // Grid item clicking
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
                ModelOption opt = filteredOptions.get(i);
                if (opt.id.equals("wish:custom_pack_input")) {
                    searchFocused = true;
                    searchQuery = "pack:";
                    updateFilteredOptions();
                    return true;
                }
                CustomModelManager.setOverride(targetKey, opt.id);
                return true;
            }
        }

        // Toggle Mod enabled button
        int prevX = gridX + gridW + 8;
        int togW = previewW - 16;
        int togH = 20;
        int togX = prevX + 8;
        int togY = contentY + contentH - 26;
        if (mx >= togX && mx < togX + togW && my >= togY && my < togY + togH) {
            ModConfig.INSTANCE.enableCustomItemModels = !ModConfig.INSTANCE.enableCustomItemModels;
            ModConfig.INSTANCE.save();
            return true;
        }

        searchFocused = false;
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalPages = Math.max(1, (filteredOptions.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        if (scrollY < 0) {
            if (currentPage < totalPages - 1) currentPage++;
        } else if (scrollY > 0) {
            if (currentPage > 0) currentPage--;
        }
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char chr = (char) event.codepoint();
        if (searchFocused && chr >= 32 && chr <= 126) {
            searchQuery += chr;
            updateFilteredOptions();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();

        if (event.isPaste()) {
            String clip = this.minecraft.keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                clip = clip.replaceAll("[\\n\\r]", "");
                if (searchFocused) {
                    searchQuery += clip;
                    updateFilteredOptions();
                    return true;
                }
            }
        }

        if (key == 259) { // Backspace
            if (searchFocused && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                updateFilteredOptions();
                return true;
            }
        }

        if (key == 256) { // ESC -> Close immediately
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
            super.onClose();
            return true;
        }

        if (key == 257) { // Enter
            searchFocused = false;
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
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
