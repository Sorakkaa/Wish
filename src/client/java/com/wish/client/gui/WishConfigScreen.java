package com.wish.client.gui;

import com.wish.client.config.ModConfig;
import com.wish.client.features.GhostBlockManager;
import com.wish.client.features.visual.LyricsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WishConfigScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-ConfigScreen");
    private final Screen lastScreen;

    
    private static final int ROW_H = 22;

    
    private static final int COL_TEXT       = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM   = 0xFFAAAAAA;
    private static final int COL_OFF        = 0xFFFF3344; 
    
    
    private int activeTab = -1; 
    private long tabTransitionStartTime = 0;
    private int commandsScrollY = 0;
    private int customPlayerScrollY = 0;
    private int miscScrollY = 0;
    private int pseudoSubScrollY = 0;
    private int miningScrollY = 0;
    private int visualScrollY = 0;
    private int slayerScrollY = 0;
    private static boolean lyricsSectionOpen = false;
    private static boolean timeChangerSectionOpen = false;
    private static boolean weatherChangerSectionOpen = false;
    private static boolean customSkySectionOpen = false;
    private static boolean otherPlayersSizeSectionOpen = false;
    
    
    private static boolean chatChannelsOpen = false;
    private static boolean funCommandsOpen = false;
    private static boolean utilityCommandsOpen = false;
    private static boolean dungeonCustomBlocksOpen = false;
    private static boolean slayerCarryOpen = false;
    private static boolean lagTimeLostOpen = false;
    private static boolean miningAutomatonOpen = false;
    private static boolean automatonGlowOpen = false;
    private static boolean slayerGlowOpen = false;
    private static boolean lyricsColorOpen = false;
    private static boolean songSettingsOpen = false;
    private boolean hexInputFocused = false;
    private boolean spoofInputFocused = false;
    private boolean editingColor2 = false;
    private boolean providerDropdownOpen = false;
    private boolean cosmeticDropdownOpen = false;
    private static final int VISUAL_DROP_W = 110;
    private static final String[] MUSIC_PROVIDER_OPTIONS = {"None", "YTM", "Spotify", "Deezer"};
    private static final String[] LYRICS_SHOW_OPTIONS = {"Word by word", "Line by line"};
    private int activeVisualDropdown = 0; // 0 = none, 1 = Music Provider, 2 = Show, 3 = Weather Type, 4 = Sky Background
    private int activeDropX = 0;
    private int activeDropY = 0;
    private int activeDropW = VISUAL_DROP_W;
    private String[] activeDropOptions = new String[0];
    private int activeDropSelected = 0;
    private static int pseudoSubWindow = 0; 
    private int draggingSlider = -1; 
    
    private int openCategoryPopup = 0; 
    private int activeSubMenuCategory = 0; 
    private float barAnimProgress = 0.0f; 
    private float barAnimStartProgress = 0.0f;
    private float barAnimTargetProgress = 0.0f;
    private long barAnimStartTime = 0;

    private void setCategoryPopup(int target) {
        if (this.openCategoryPopup == target) {
            target = 0;
        }
        this.openCategoryPopup = target;
        if (target > 0) {
            this.activeSubMenuCategory = target;
        }
        this.barAnimStartProgress = this.barAnimProgress;
        this.barAnimTargetProgress = (target > 0) ? 1.0f : 0.0f;
        this.barAnimStartTime = System.currentTimeMillis();
    }

    private float getBarAnimProgress() {
        if (barAnimStartTime <= 0) {
            return barAnimTargetProgress;
        }
        long now = System.currentTimeMillis();
        float elapsed = (now - barAnimStartTime) / 200.0f;
        if (elapsed >= 1.0f) {
            barAnimProgress = barAnimTargetProgress;
            if (barAnimProgress <= 0.001f && openCategoryPopup == 0) {
                activeSubMenuCategory = 0;
            }
            return barAnimProgress;
        }
        float ease = 1.0f - (float) Math.pow(1.0f - elapsed, 3);
        barAnimProgress = barAnimStartProgress + (barAnimTargetProgress - barAnimStartProgress) * ease;
        return barAnimProgress;
    }

    
    private float selectedHue = 0.0f;
    private float selectedSat = 1.0f;
    private float selectedVal = 1.0f;
    
    private String hoveredTooltipTitle = null;
    private String hoveredTooltipDesc = null;

    public WishConfigScreen(Screen lastScreen) {
        super(Component.literal("Astra Client Settings"));
        this.lastScreen = lastScreen;
        updateHSBFromConfig();
    }

    @Override
    protected void init() {
        super.init();
        activeTab = -1;
        tabTransitionStartTime = System.currentTimeMillis();
        openCategoryPopup = 0;
        activeSubMenuCategory = 0;
        barAnimProgress = 0.0f;
        barAnimStartProgress = 0.0f;
        barAnimTargetProgress = 0.0f;
        barAnimStartTime = 0;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private int getAccentColor() {
        return parseHex(ModConfig.INSTANCE.menuAccentColor, 0xFF9D00FF);
    }

    private int getAccentDimColor() {
        int acc = getAccentColor() & 0x00FFFFFF;
        return 0xAA000000 | acc;
    }

    private int getHoverColor() {
        int acc = getAccentColor() & 0x00FFFFFF;
        return 0x33000000 | acc;
    }

    private void updateHSBFromConfig() {
        String hex;
        if (activeTab == 3) {
            hex = ModConfig.INSTANCE.slayerGlowColor;
        } else if (activeTab == 6) {
            hex = ModConfig.INSTANCE.automatonGlowColor;
        } else if (activeTab == 7) {
            hex = ModConfig.INSTANCE.lyricsColor;
        } else {
            hex = editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2 ? ModConfig.INSTANCE.customHexColor2 : ModConfig.INSTANCE.customHexColor;
        }
        if (hex != null && hex.length() == 7 && hex.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(hex.substring(1), 16);
                float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
                selectedHue = hsb[0];
                selectedSat = hsb[1];
                selectedVal = hsb[2];
            } catch (Exception ignored) {}
        }
    }

    private int parseHex(String hex, int defaultColor) {
        if (hex != null && hex.length() == 7 && hex.startsWith("#")) {
            try {
                return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
            } catch (Exception ignored) {}
        }
        return defaultColor;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        
        int colAccent = getAccentColor();
        int colAccentDim = getAccentDimColor();
        int colHover = getHoverColor();
        var font = Minecraft.getInstance().font;

        
        int lineX = 0;
        float barAnim = getBarAnimProgress();
        int baseVertX = 160;
        int vertX = baseVertX + (int)(65 * barAnim);

        
        String[] menuCategories = {"Custom Player", "Feature", "Misc Setting"};
        int categorySpacing = 38;
        int sidebarStartY = (this.height - (menuCategories.length * categorySpacing)) / 2;

        
        renderSubMenuChoices(g, font, mx, my, sidebarStartY, categorySpacing, colAccent, colAccentDim, colHover, barAnim);

        
        g.fill(vertX, 80, vertX + 1, this.height - 80, colAccent);
        
        
        var player = Minecraft.getInstance().player;
        String baseName = player != null ? player.getName().getString() : "Player";
        boolean hasSpoof = ModConfig.INSTANCE.spoofedName != null && !ModConfig.INSTANCE.spoofedName.trim().isEmpty();
        if (hasSpoof) {
            baseName = ModConfig.INSTANCE.spoofedName.trim();
        }
        
        String customPseudo = baseName;
        int pseudoColor = 0xFFFFFFFF;
        
        if (ModConfig.INSTANCE.enableNameColor) {
            pseudoColor = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
        }

        if (openCategoryPopup == 0 && barAnim <= 0.05f && (activeTab == 1 || activeTab == 5)) {
            int sliderBoxX = this.width / 2 + 55;
            int targetCrossX = (activeTab == 5) ? ((vertX + sliderBoxX) / 2 - 20) : (lineX - 120);
            int targetVisualCenterY = (activeTab == 5) ? (this.height / 2 + 25) : (this.height / 2 + 45);
            
            int crossX = targetCrossX;
            int visualCenterY = targetVisualCenterY;

            int pseudoW = getPseudoWidth(font, customPseudo);
            
            float skinScale = 1.0f; 
            int baseSize = (activeTab == 5) ? 75 : 45;
            int renderSize = (int)(baseSize * skinScale);
            int absRenderSize = Math.max(1, Math.abs(renderSize));
            int totalHeight = 15 + (int)(absRenderSize * 2.2f);
            int skinFeetY = visualCenterY + totalHeight / 2;
            int skinHeadY = skinFeetY - (int)(absRenderSize * 2.2f);
            int skinHalfW = Math.max(20, (int)(absRenderSize * 0.7f));
            
            int hitWRight = (int)(skinHalfW * 0.8f);
            int hitWLeft = (int)(skinHalfW * 1.2f);
            boolean skinHover = mx >= crossX - hitWLeft && mx <= crossX + hitWRight && my >= skinHeadY - 60 && my <= skinFeetY - 50;
            
            int pseudoY = skinHeadY - 75;
            boolean pseudoHover = mx >= crossX - pseudoW / 2 - 10 && mx <= crossX + pseudoW / 2 + 10 && my >= pseudoY - 5 && my <= pseudoY + 15;
            if (activeTab == 5) {
                pseudoHover = false;
                skinHover = false;
            }
            
            if (pseudoHover) {
                g.text(font, "✎", crossX + pseudoW / 2 + 1, pseudoY + 1, 0xFFFFFFFF);
            }
            if (skinHover) {
                g.text(font, "✎", crossX + (int)(skinHalfW * 1.2f), skinHeadY - 45, 0xFFFFFFFF);
            }
    
            if (activeTab == 5) {
                if (player != null) {
                    try {
                        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g, crossX - skinHalfW * 6, skinHeadY - 100, crossX + skinHalfW * 6, skinFeetY, renderSize, 0.0625F, mx, my, player);
                    } catch (Exception e) {
                        g.text(font, "SKIN", crossX - font.width("SKIN") / 2, skinFeetY - 30, 0xFFFFFFFF);
                    }
                } else {
                    g.text(font, "SKIN", crossX - font.width("SKIN") / 2, skinFeetY - 30, 0xFFFFFFFF);
                }
            }
        }

        
        int tabX = lineX + 35;

        for (int i = 0; i < menuCategories.length; i++) {
            int catY = sidebarStartY + i * categorySpacing;
            boolean isCategoryActive = false;
            boolean isPopupOpen = false;
            if (i == 0) {
                isCategoryActive = (activeTab == 1 || activeTab == 5);
                isPopupOpen = (openCategoryPopup == 1);
            } else if (i == 1) {
                isCategoryActive = (activeTab == 0 || activeTab == 2 || activeTab == 3 || activeTab == 6 || activeTab == 7);
                isPopupOpen = (openCategoryPopup == 2);
            } else if (i == 2) {
                isCategoryActive = (activeTab == 4);
            }

            boolean hover = (mx >= lineX + 15 && mx < lineX + 145 && my >= catY - 6 && my < catY + 26);

            g.text(font, menuCategories[i], tabX, catY, (isCategoryActive || isPopupOpen) ? 0xFFFFFFFF : (hover ? 0xFFCCCCCC : 0xFFAAAAAA));

            if (isCategoryActive || isPopupOpen) {
                long now = System.currentTimeMillis();
                float tabRaw = tabTransitionStartTime > 0 ? Math.min(1.0f, (now - tabTransitionStartTime) / 220.0f) : 1.0f;
                float tabAnim = 1.0f - (float) Math.pow(1.0f - tabRaw, 3);
                int indicatorOffset = (int)((1.0f - tabAnim) * 5);
                g.text(font, ">", tabX + font.width(menuCategories[i]) + 8 + indicatorOffset, catY, colAccent);
            }
        }

        
        if (activeTab != -1 && openCategoryPopup == 0) {
            int targetCw = Math.min(this.width - 220, 450); 
            int cw = targetCw;
            
            int spaceRight = this.width - vertX;
            int cx = vertX + spaceRight / 2 - cw / 2;
            
            int mainWinH = Math.min(this.height - 80, 360);
            int mainWinY = (this.height - mainWinH) / 2;
            int cy = mainWinY + 10;
            
            int py = mainWinY - 5;
            int WIN_H = mainWinH;

            
            long now = System.currentTimeMillis();
            float tabRaw = tabTransitionStartTime > 0 ? Math.min(1.0f, (now - tabTransitionStartTime) / 220.0f) : 1.0f;
            float tabAnim = 1.0f - (float) Math.pow(1.0f - tabRaw, 3); 
            int contentSlideY = (int) ((1.0f - tabAnim) * 12);
            
            if (activeTab != 1 && activeTab != 5) {
                
                RenderUtils.fillRoundedRect(g, cx - 10, mainWinY, cw + 20, mainWinH, 6, 0x00000000);
                RenderUtils.drawGradientOutline(g, cx - 10, mainWinY, cw + 20, mainWinH, 6, colAccent, colAccent);
            } else if (activeTab == 1) {
                
                cw = 320; 
                cx = (this.width - cw) / 2;
                cy = this.height / 2 - 80;
            } else if (activeTab == 5) {
                cw = 280;
                cx = this.width / 2 + 55;
                cy = this.height / 2 - 80;
            }
            
            
            int closeBtnSize = 20;
            int closeX = this.width - closeBtnSize - 20;
            int closeY = 20;
            boolean closeHover = mx >= closeX && mx <= closeX + closeBtnSize && my >= closeY && my <= closeY + closeBtnSize;
            RenderUtils.fillRoundedRect(g, closeX, closeY, closeBtnSize, closeBtnSize, 4, closeHover ? 0xAAFF3333 : 0x44FF3333);
            RenderUtils.drawGradientOutline(g, closeX, closeY, closeBtnSize, closeBtnSize, 4, 0xFFFF3333, 0x88FF0000);
            g.text(font, "X", closeX + (closeBtnSize - font.width("X"))/2 + 1, closeY + (closeBtnSize - 8)/2, 0xFFFFFFFF);

            if (activeTab != 1 && activeTab != 5) {
                String menuTitle = "";
                if (activeTab == 0) menuTitle = "COMMAND";
                else if (activeTab == 2) menuTitle = "DUNGEON";
                else if (activeTab == 3) menuTitle = "SLAYER";
                else if (activeTab == 4) menuTitle = "MISC SETTING";
                else if (activeTab == 6) menuTitle = "MINING";
                else if (activeTab == 7) menuTitle = "VISUAL";
                
                g.text(font, "§l" + menuTitle, cx, cy, colAccent);
                cy += 25;
            }
            
            cy += contentSlideY;

        hoveredTooltipTitle = null;
        hoveredTooltipDesc = null;

        if (activeTab == 0) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int clipHeight = clipBottom - clipTop;

            
            int totalContentH = 0;
            int hH = 22;
            totalContentH += hH + 4; 
            if (chatChannelsOpen) {
                totalContentH += 3 * (ROW_H + 4);
            }
            totalContentH += hH + 4; 
            if (funCommandsOpen) {
                int funBtnH = 16;
                totalContentH += ((14 + 1) / 2) * (funBtnH + 3) + 4;
            }
            totalContentH += hH + 4; 
            if (utilityCommandsOpen) {
                int utilBtnH = 16;
                totalContentH += ((1 + 1) / 2) * (utilBtnH + 3) + 4;
            }

            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (commandsScrollY > maxScroll) commandsScrollY = maxScroll;
            if (commandsScrollY < 0) commandsScrollY = 0;

            
            g.enableScissor(cx, clipTop, cx + cw, clipBottom);

            cy -= commandsScrollY;

            
            int chanHeaderY = cy;
            boolean chanHeaderHover = (mx >= cx && mx < cx + cw && my >= Math.max(chanHeaderY, clipTop) && my < Math.min(chanHeaderY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, chanHeaderY, cw, hH, 4, chanHeaderHover ? 0x44444455 : 0x2A2A2E55);
            RenderUtils.drawGradientOutline(g, cx, chanHeaderY, cw, hH, 4, 0x55888888, 0x22444444);
            g.text(font, (chatChannelsOpen ? "▼ " : "▶ ") + "Chat Channels", cx + 8, chanHeaderY + 7, COL_TEXT);
            cy += hH + 4;

            if (chatChannelsOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "All Chat /ac", ModConfig.INSTANCE.enableAc, mx, my, "All Chat /ac", "Allows commands to be triggered in public/all chat.");
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Guild Chat /gc", ModConfig.INSTANCE.enableGc, mx, my, "Guild Chat /gc", "Allows commands to be triggered in guild chat.");
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Party Chat /pc", ModConfig.INSTANCE.enablePc, mx, my, "Party Chat /pc", "Allows commands to be triggered in party chat.");
            }

            
            int funHeaderY = cy;
            boolean funHeaderHover = (mx >= cx && mx < cx + cw && my >= Math.max(funHeaderY, clipTop) && my < Math.min(funHeaderY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, funHeaderY, cw, hH, 4, funHeaderHover ? 0x44444455 : 0x2A2A2E55);
            RenderUtils.drawGradientOutline(g, cx, funHeaderY, cw, hH, 4, 0x55888888, 0x22444444);
            g.text(font, (funCommandsOpen ? "▼ " : "▶ ") + "Fun Commands", cx + 8, funHeaderY + 7, COL_TEXT);
            cy += hH + 4;

            if (funCommandsOpen) {
                String[] funLabels = {
                    "!meow", "!wanted", "!kiss", "!feed", "!poke",
                    "!pat", "!hug", "!sus", "!rizz", "!jerry", "!iq",
                    "!sleep", "!yuri", "!soraka"
                };
                boolean[] funStates = {
                    ModConfig.INSTANCE.enableMeow,
                    ModConfig.INSTANCE.enableWanted, ModConfig.INSTANCE.enableKiss,
                    ModConfig.INSTANCE.enableFeed, ModConfig.INSTANCE.enablePoke,
                    ModConfig.INSTANCE.enablePat, ModConfig.INSTANCE.enableHug,
                    ModConfig.INSTANCE.enableSus, ModConfig.INSTANCE.enableRizz,
                    ModConfig.INSTANCE.enableJerry, ModConfig.INSTANCE.enableIq,
                    ModConfig.INSTANCE.enableSleep, ModConfig.INSTANCE.enableYuri,
                    ModConfig.INSTANCE.enableSoraka
                };
                String[] funDescs = {
                    "say: <player> is X% cat... nyaa :3",
                    "say: WANTED: <player> for ninjaing Necron's Handle (Bounty: 1 coin and a stick).",
                    "say: <you> kissed <player> <3",
                    "say: <you> fed <player> a cookie. Eat and be quiet.",
                    "say: <you> poked <player>! Hey, wake up!",
                    "say: <you> gently patted <player>'s head. Good boy/girl.",
                    "say: <you> gave <player> a big warm hug!",
                    "say: <player> is X% sus (vote them out right now)",
                    "say: <player> has X% rizz (W rizz)",
                    "say: [Jerry] Jerry has taken full control.",
                    "say: <player>'s IQ: X (can't find the W key / galaxy brain)",
                    "say: <you>: 'Wake up, my bed exploded.'",
                    "say: <p1> & <p2>: X% yuri!",
                    "say: [Soraka] Yes, that was a banana. Nobody expects the banana."
                };

                int btnW = (cw - 6) / 2;
                int btnH = 16;
                for (int i = 0; i < funLabels.length; i++) {
                    int col = i % 2;
                    int row = i / 2;
                    int tx = cx + col * (btnW + 6);
                    int ty = cy + row * (btnH + 3);

                    boolean hover = (mx >= tx && mx < tx + btnW && my >= Math.max(ty, clipTop) && my < Math.min(ty + btnH, clipBottom));
                    int bgColor = hover ? 0x5544444C : 0x332E2E33;
                    int borderColor = hover ? 0x88AAAAAA : 0x44666666;

                    RenderUtils.fillRoundedRect(g, tx, ty, btnW, btnH, 3, bgColor);
                    RenderUtils.drawGradientOutline(g, tx, ty, btnW, btnH, 3, borderColor, 0x22222222);

                    int dotX = tx + btnW - 10;
                    int dotY = ty + 4;
                    RenderUtils.fillRoundedRect(g, dotX, dotY, 7, 7, 3, funStates[i] ? colAccent : COL_OFF);

                    g.text(font, funLabels[i], tx + 5, ty + 4, funStates[i] ? COL_TEXT : COL_TEXT_DIM);

                    if (hover) {
                        hoveredTooltipTitle = funLabels[i];
                        hoveredTooltipDesc = funDescs[i];
                    }
                }
                cy += ((funLabels.length + 1) / 2) * (btnH + 3) + 4;
            }

            
            int utilHeaderY = cy;
            boolean utilHeaderHover = (mx >= cx && mx < cx + cw && my >= Math.max(utilHeaderY, clipTop) && my < Math.min(utilHeaderY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, utilHeaderY, cw, hH, 4, utilHeaderHover ? 0x44444455 : 0x2A2A2E55);
            RenderUtils.drawGradientOutline(g, cx, utilHeaderY, cw, hH, 4, 0x55888888, 0x22444444);
            g.text(font, (utilityCommandsOpen ? "▼ " : "▶ ") + "Utility Commands", cx + 8, utilHeaderY + 7, COL_TEXT);
            cy += hH + 4;

            if (utilityCommandsOpen) {
                String[] utilLabels = {"!song"};
                boolean[] utilStates = {ModConfig.INSTANCE.enableSong};
                String[] utilDescs = {
                    "say: <title> - <artist>"
                };

                int btnW = (cw - 6) / 2;
                int btnH = 16;
                for (int i = 0; i < utilLabels.length; i++) {
                    int col = i % 2;
                    int row = i / 2;
                    int tx = cx + col * (btnW + 6);
                    int ty = cy + row * (btnH + 3);

                    boolean hover = (mx >= tx && mx < tx + btnW && my >= Math.max(ty, clipTop) && my < Math.min(ty + btnH, clipBottom));
                    int bgColor = hover ? 0x5544444C : 0x332E2E33;
                    int borderColor = hover ? 0x88AAAAAA : 0x44666666;

                    RenderUtils.fillRoundedRect(g, tx, ty, btnW, btnH, 3, bgColor);
                    RenderUtils.drawGradientOutline(g, tx, ty, btnW, btnH, 3, borderColor, 0x22222222);

                    int dotX = tx + btnW - 10;
                    int dotY = ty + 4;
                    RenderUtils.fillRoundedRect(g, dotX, dotY, 7, 7, 3, utilStates[i] ? colAccent : COL_OFF);

                    if (utilLabels[i].equals("!song")) {
                        int iSize = 12;
                        int iBtnX = dotX - 18;
                        int iBtnY = ty + (btnH - iSize) / 2;
                        boolean iHover = (mx >= iBtnX && mx < iBtnX + iSize && my >= Math.max(ty, clipTop) && my < Math.min(ty + btnH, clipBottom));
                        
                        RenderUtils.fillRoundedRect(g, iBtnX, iBtnY, iSize, iSize, 6, iHover ? 0x33FFFFFF : 0x14FFFFFF);
                        RenderUtils.drawGradientOutline(g, iBtnX, iBtnY, iSize, iSize, 6, iHover ? colAccent : 0x55AAAAAA, iHover ? colAccentDim : 0x22666666);
                        
                        int icx = iBtnX + 6;
                        int icy = iBtnY + 6;
                        int iCol = iHover ? colAccent : 0xFFEEEEEE;
                        g.fill(icx, icy - 3, icx + 1, icy - 2, iCol); 
                        g.fill(icx, icy - 1, icx + 1, icy + 3, iCol); 
                        g.fill(icx - 1, icy - 1, icx, icy, iCol);     
                        g.fill(icx - 1, icy + 2, icx + 2, icy + 3, iCol); 

                        if (iHover) {
                            hoveredTooltipTitle = "Song Settings";
                            hoveredTooltipDesc = "Choose music provider";
                        }
                    }

                    g.text(font, utilLabels[i], tx + 5, ty + 4, utilStates[i] ? COL_TEXT : COL_TEXT_DIM);

                    if (hover && (!utilLabels[i].equals("!song") || mx > tx + btnW - 18 || mx < tx + btnW - 30)) {
                        hoveredTooltipTitle = utilLabels[i];
                        hoveredTooltipDesc = utilDescs[i];
                    }
                }
                cy += ((utilLabels.length + 1) / 2) * (btnH + 3) + 4;
            }

            g.disableScissor();

            
            if (maxScroll > 0) {
                int scrollTrackX = cx + cw + 3;
                int scrollTrackY = clipTop;
                int scrollTrackH = clipHeight;
                int barH = Math.max(16, (int)((float)clipHeight / (float)totalContentH * clipHeight));
                int barY = scrollTrackY + (int)((float)commandsScrollY / (float)maxScroll * (scrollTrackH - barH));

                RenderUtils.fillRoundedRect(g, scrollTrackX, scrollTrackY, 3, scrollTrackH, 1, 0x22FFFFFF);
                RenderUtils.fillRoundedRect(g, scrollTrackX, barY, 3, barH, 1, colAccentDim);
            }
        } else if (activeTab == 1) {
            
            cw = 320;
            cx = (this.width - cw) / 2;
            cy = this.height / 2 - 80 + contentSlideY;
            
            
            g.pose().pushMatrix();
            float scale = 2.0f;
            g.pose().scale(scale, scale);
            
            net.minecraft.network.chat.Style baseSt2 = getPseudoBaseStyle();
            int pWidth = getPseudoWidth(font, customPseudo);
            int pX = (int)((cx + (cw - pWidth * scale) / 2) / scale);
            int pY = (int)(cy / scale);
            int currentX = pX;
            if (isPreviewBadgeActive()) {
                net.minecraft.network.chat.Component bComp = com.wish.client.color.NameColorManager.createBadgeComponent();
                g.text(font, bComp, currentX, pY, 0xFFFFFFFF);
                currentX += font.width(bComp) + font.width(" ");
            }
            
            if (ModConfig.INSTANCE.enableNameColor && ModConfig.INSTANCE.pseudoAnimation >= 1) {
                int c1 = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
                int c2 = parseHex(ModConfig.INSTANCE.customHexColor2, 0xFFFFFFFF);
                int len = customPseudo.length();
                long time = (long)(System.currentTimeMillis() * (double)ModConfig.INSTANCE.pseudoAnimationSpeed);
                for (int i = 0; i < len; i++) {
                    char c = customPseudo.charAt(i);
                    float ratio = len > 1 ? (float)i / (len - 1) : 0;
                    int colorMix;
                    if (ModConfig.INSTANCE.pseudoAnimation == 1) {
                        float hue = (time % 3000L) / 3000.0f + ratio;
                        colorMix = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
                    } else if (ModConfig.INSTANCE.pseudoAnimation == 5) { 
                        if ((time / 500) % 2 == 0) colorMix = c1;
                        else colorMix = c2;
                    } else {
                        if (ModConfig.INSTANCE.pseudoAnimation == 3) {
                            float timeOffset = (time % 2000L) / 2000.0f;
                            ratio = (float) (Math.sin((ratio - timeOffset) * Math.PI * 2) * 0.5f + 0.5f);
                        } else if (ModConfig.INSTANCE.pseudoAnimation == 4) {
                            float timeRatio = (time % 3000L) / 3000.0f;
                            ratio = (float) (Math.sin(timeRatio * Math.PI * 2) * 0.5f + 0.5f);
                        }
                        
                        int r1 = (c1 >> 16) & 0xFF; int g1 = (c1 >> 8) & 0xFF; int b1 = c1 & 0xFF;
                        int r2 = (c2 >> 16) & 0xFF; int g2 = (c2 >> 8) & 0xFF; int b2 = c2 & 0xFF;
                        int rMix = (int)(r1 + (r2 - r1) * ratio);
                        int gMix = (int)(g1 + (g2 - g1) * ratio);
                        int bMix = (int)(b1 + (b2 - b1) * ratio);
                        colorMix = 0xFF000000 | (rMix << 16) | (gMix << 8) | bMix;
                    }
                    String strChar = String.valueOf(c);
                    net.minecraft.network.chat.Component chComp = net.minecraft.network.chat.Component.literal(strChar).withStyle(baseSt2.withColor(net.minecraft.network.chat.TextColor.fromRgb(colorMix & 0x00FFFFFF)));
                    g.text(font, chComp, currentX, pY, colorMix);
                    currentX += font.width(chComp);
                }
            } else {
                int pseudoColor2 = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
                net.minecraft.network.chat.Component fullComp = net.minecraft.network.chat.Component.literal(customPseudo).withStyle(baseSt2.withColor(net.minecraft.network.chat.TextColor.fromRgb(pseudoColor2 & 0x00FFFFFF)));
                g.text(font, fullComp, currentX, pY, pseudoColor2);
            }
            g.pose().popMatrix();
            cy += 36; 
            
            
            int btnH = 24;
            int totalBtnW = 3 * 70 + 2 * 10;
            int dockX = cx + cw/2 - totalBtnW/2;
            int dockY = cy;
            
            String[] dockLabels = {"Font", "Animation", "Colors"};
            for (int i = 0; i < 3; i++) {
                int bx = dockX + i * 80;
                boolean bHover = mx >= bx && mx < bx + 70 && my >= dockY && my < dockY + btnH;
                boolean isCurrentTab = (pseudoSubWindow == i + 1);
                RenderUtils.fillRoundedRect(g, bx, dockY, 70, btnH, 4, 0x00000000); 
                RenderUtils.drawGradientOutline(g, bx, dockY, 70, btnH, 4, isCurrentTab ? colAccent : (bHover ? colAccentDim : 0x44666666), 0x22222222);
                g.text(font, dockLabels[i], bx + 35 - font.width(dockLabels[i])/2, dockY + 8, isCurrentTab ? colAccent : COL_TEXT);
            }
            
            if (pseudoSubWindow > 0) {
                int winW = 310;
                int winH = 200;
                if (pseudoSubWindow == 1) winH = 120; 
                if (pseudoSubWindow == 2) {
                    winH = 126; 
                    if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) winH += 40;
                }
                if (pseudoSubWindow == 3) winH = 195; 
                
                int winX = this.width / 2 - winW / 2;
                int winY = dockY + btnH + 10 + contentSlideY;
                if (winY + winH > this.height - 10) {
                    winY = this.height - winH - 10;
                }
                
                int availH = this.height - winY - 10;
                boolean needsScroll = winH > availH && availH > 80;
                int renderWinH = needsScroll ? availH : winH;
                
                
                RenderUtils.fillRoundedRect(g, winX, winY, winW, renderWinH, 4, 0x00000000);
                RenderUtils.drawGradientOutline(g, winX, winY, winW, renderWinH, 4, colAccent, colAccent);
                
                g.text(font, dockLabels[pseudoSubWindow - 1], winX + 15, winY + 12, 0xFFFFFFFF);
                
                int swCloseX = winX + winW - 22;
                int swCloseY = winY + 9;
                boolean swCloseHover = mx >= swCloseX && mx < swCloseX + 14 && my >= swCloseY && my < swCloseY + 14;
                RenderUtils.fillRoundedRect(g, swCloseX, swCloseY, 14, 14, 3, swCloseHover ? 0xAAFF3333 : 0x00000000);
                RenderUtils.drawGradientOutline(g, swCloseX, swCloseY, 14, 14, 3, 0x44FFFFFF, 0x11000000);
                for (int i=0; i<6; i++) {
                    g.fill(swCloseX + 4 + i, swCloseY + 4 + i, swCloseX + 5 + i, swCloseY + 5 + i, 0xFFFFFFFF);
                    g.fill(swCloseX + 9 - i, swCloseY + 4 + i, swCloseX + 10 - i, swCloseY + 5 + i, 0xFFFFFFFF);
                }

                int cX = winX + 15;
                int cY = winY + 32;
                int cW = winW - 30;

                int contentClipTop = winY + 28;
                int contentClipBottom = winY + renderWinH - 6;
                int contentClipHeight = contentClipBottom - contentClipTop;
                int subMaxScroll = Math.max(0, winH - 32 - contentClipHeight);
                if (pseudoSubScrollY > subMaxScroll) pseudoSubScrollY = subMaxScroll;
                if (pseudoSubScrollY < 0) pseudoSubScrollY = 0;

                if (needsScroll) {
                    g.enableScissor(winX + 4, contentClipTop, winX + winW - 4, contentClipBottom);
                    cY -= pseudoSubScrollY;
                }
                
                if (pseudoSubWindow == 1) { 
                    String[] fonts = {"Bold", "Italic", "Underline", "Strikethrough"};
                    int[] bitmasks = {1, 2, 4, 8};
                    int colW2 = (cW - 10) / 2;
                    for (int i = 0; i < 4; i++) {
                        int bx = cX + (i % 2) * (colW2 + 10);
                        int by = cY + (i / 2) * 22;
                        boolean hov = mx >= bx && mx < bx + colW2 && my >= by && my < by + 18;
                        boolean isActive = (ModConfig.INSTANCE.pseudoFont & bitmasks[i]) != 0;
                        int borderCol = isActive ? colAccent : (hov ? colAccentDim : 0x44666666);
                        RenderUtils.drawGradientOutline(g, bx, by, colW2, 18, 4, borderCol, borderCol);
                        g.text(font, fonts[i], bx + 8, by + 5, isActive ? colAccent : COL_TEXT);
                    }
                    cY += 2 * 22 + 6;
                    cY = renderInput(g, font, cX, cY, cW, "Name Spoofer", ModConfig.INSTANCE.spoofedName != null ? ModConfig.INSTANCE.spoofedName : "", spoofInputFocused, mx, my);
                } else if (pseudoSubWindow == 2) { 
                    String[] anims = {"None", "Chroma", "Gradient", "Wave", "Breathe", "Blink"};
                    int colW = (cW - 10) / 2;
                    for (int i=0; i<6; i++) {
                        int bx = cX + (i % 2) * (colW + 10);
                        int by = cY + (i / 2) * 24;
                        boolean hov = mx >= bx && mx < bx + colW && my >= by && my < by + 20;
                        boolean isAnimActive = (ModConfig.INSTANCE.pseudoAnimation == i);
                        int borderCol = isAnimActive ? colAccent : (hov ? colAccentDim : 0x44666666);
                        RenderUtils.drawGradientOutline(g, bx, by, colW, 20, 4, borderCol, borderCol);
                        g.text(font, anims[i], bx + 10, by + 6, isAnimActive ? colAccent : COL_TEXT);
                    }
                    cY += 3 * 24; 
                    if (ModConfig.INSTANCE.pseudoAnimationSpeed == 0.0f) ModConfig.INSTANCE.pseudoAnimationSpeed = 1.0f;
                    if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) {
                        cY += 6;
                        renderSlider(g, font, cX, cY, cW, "Speed", ModConfig.INSTANCE.pseudoAnimationSpeed, mx, my, 6, 0.1f, 3.0f);
                    }
                } else if (pseudoSubWindow == 3) { 
                    int c1Active = !editingColor2 ? 1 : 0;
                    boolean c1BtnHover = mx >= cX && mx < cX + cW/2 - 5 && my >= cY && my < cY + 26;
                    int col1 = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
                    int borderCol1 = c1Active == 1 ? colAccent : (c1BtnHover ? colAccentDim : 0x44666666);
                    RenderUtils.drawGradientOutline(g, cX, cY, cW/2 - 5, 26, 4, borderCol1, borderCol1);
                    RenderUtils.fillRoundedRect(g, cX + 10, cY + 5, 16, 16, 8, col1);
                    g.text(font, "Color 1", cX + 30, cY + 9, c1Active==1 ? colAccent : COL_TEXT);
                    
                    int c2X = cX + cW/2 + 5;
                    int c2Active = editingColor2 ? 1 : 0;
                    boolean c2BtnHover = mx >= c2X && mx < c2X + cW/2 - 5 && my >= cY && my < cY + 26;
                    int col2 = parseHex(ModConfig.INSTANCE.customHexColor2, 0xFFFFFFFF);
                    int borderCol2 = c2Active == 1 ? colAccent : (c2BtnHover ? colAccentDim : 0x44666666);
                    RenderUtils.drawGradientOutline(g, c2X, cY, cW/2 - 5, 26, 4, borderCol2, borderCol2);
                    RenderUtils.fillRoundedRect(g, c2X + 10, cY + 5, 16, 16, 8, col2);
                    g.text(font, "Color 2", c2X + 30, cY + 9, c2Active==1 ? colAccent : COL_TEXT);
                    if (ModConfig.INSTANCE.pseudoAnimation < 2) {
                        RenderUtils.fillRoundedRect(g, c2X, cY, cW/2 - 5, 26, 4, 0xAA000000);
                    }
                    
                    cY += 35;
                    String curHex = editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2 ? ModConfig.INSTANCE.customHexColor2 : ModConfig.INSTANCE.customHexColor;
                    cY = renderInput(g, font, cX, cY, cW, "Hex", curHex, hexInputFocused, mx, my);
                    
                    int hueW = cW - 10;
                    int hueH = 10;
                    
                    g.text(font, "Hue", cX, cY, COL_TEXT_DIM); cY += 12;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB((float)i / hueW, 1.0f, 1.0f) | 0xFF000000;
                        g.fill(cX + i, cY, cX + i + 1, cY + hueH, c);
                    }
                    int pickerX = cX + (int)(selectedHue * hueW);
                    pickerX = Math.max(cX, Math.min(cX + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cY - 2, pickerX + 2, cY + hueH + 2, 0xFFFFFFFF);
                    cY += hueH + 8;
                    
                    g.text(font, "Saturation", cX, cY, COL_TEXT_DIM); cY += 12;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, (float)i / hueW, selectedVal) | 0xFF000000;
                        g.fill(cX + i, cY, cX + i + 1, cY + hueH, c);
                    }
                    pickerX = cX + (int)(selectedSat * hueW);
                    pickerX = Math.max(cX, Math.min(cX + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cY - 2, pickerX + 2, cY + hueH + 2, 0xFFFFFFFF);
                    cY += hueH + 8;
                    
                    g.text(font, "Brightness", cX, cY, COL_TEXT_DIM); cY += 12;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, selectedSat, (float)i / hueW) | 0xFF000000;
                        g.fill(cX + i, cY, cX + i + 1, cY + hueH, c);
                    }
                    pickerX = cX + (int)(selectedVal * hueW);
                    pickerX = Math.max(cX, Math.min(cX + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cY - 2, pickerX + 2, cY + hueH + 2, 0xFFFFFFFF);
                }

                if (needsScroll) {
                    g.disableScissor();
                    if (subMaxScroll > 0) {
                        int barH = Math.max(14, (int)((float)contentClipHeight / (float)(winH - 32) * contentClipHeight));
                        int barY = contentClipTop + (int)((float)pseudoSubScrollY / (float)subMaxScroll * (contentClipHeight - barH));
                        RenderUtils.fillRoundedRect(g, winX + winW - 5, contentClipTop, 2, contentClipHeight, 1, 0x22FFFFFF);
                        RenderUtils.fillRoundedRect(g, winX + winW - 5, barY, 2, barH, 1, colAccentDim);
                    }
                }
            }
        } else if (activeTab == 5) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int clipHeight = clipBottom - clipTop;
            
            int totalContentH = 0;
            totalContentH += 30 * 4; 
            totalContentH += 28; 
            totalContentH += 26; 
            
            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (customPlayerScrollY > maxScroll) customPlayerScrollY = maxScroll;
            if (customPlayerScrollY < 0) customPlayerScrollY = 0;

            g.enableScissor(cx, clipTop, cx + cw, clipBottom);
            cy -= customPlayerScrollY;
            
            cy = renderSlider(g, font, cx, cy, cw, "Size X", ModConfig.INSTANCE.playerSizeX, mx, my, 0, -1.0f, 3.0f);
            cy = renderSlider(g, font, cx, cy, cw, "Size Y", ModConfig.INSTANCE.playerSizeY, mx, my, 1, -1.0f, 3.0f);
            cy = renderSlider(g, font, cx, cy, cw, "Size Z", ModConfig.INSTANCE.playerSizeZ, mx, my, 2, -1.0f, 3.0f);
            cy = renderSlider(g, font, cx, cy, cw, "Spin Speed", ModConfig.INSTANCE.playerSpinSpeedY, mx, my, 7, -3.0f, 3.0f);

            
            int bx = cx;
            boolean bHover = (mx >= bx && mx < bx + 100 && my >= cy && my < cy + 22);
            RenderUtils.fillRoundedRect(g, bx, cy, 100, 22, 4, bHover ? colHover : 0x332E2E33);
            RenderUtils.drawGradientOutline(g, bx, cy, 100, 22, 4, bHover ? 0x88AAAAAA : 0x44666666, 0x22222222);
            g.text(font, "Reset", bx + 30, cy + 7, COL_TEXT);
            cy += 26;

            g.disableScissor();

            if (maxScroll > 0) {
                int scrollTrackX = cx + cw + 3;
                int scrollTrackY = clipTop;
                int scrollTrackH = clipHeight;
                int barH = Math.max(16, (int)((float)clipHeight / (float)totalContentH * clipHeight));
                int barY = scrollTrackY + (int)((float)customPlayerScrollY / (float)maxScroll * (scrollTrackH - barH));

                RenderUtils.fillRoundedRect(g, scrollTrackX, scrollTrackY, 3, scrollTrackH, 1, 0x22FFFFFF);
                RenderUtils.fillRoundedRect(g, scrollTrackX, barY, 3, barH, 1, colAccentDim);
            }
        } else if (activeTab == 2) {
            int hH = 22;

            
            int h1Y = cy;
            boolean h1Hover = (mx >= cx && mx < cx + cw && my >= h1Y && my < h1Y + hH);
            RenderUtils.fillRoundedRect(g, cx, h1Y, cw, hH, 4, dungeonCustomBlocksOpen ? colHover : (h1Hover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, h1Y, cw, hH, 4, dungeonCustomBlocksOpen ? colAccent : 0x55888888, 0x22444444);
            
            g.text(font, (dungeonCustomBlocksOpen ? "▼ " : "▶ ") + "Custom Blocks (F7/M7)", cx + 8, h1Y + 7, COL_TEXT);
            
            boolean state = GhostBlockManager.isGhostBlocksEnabled;
            int dotSize = 8;
            int dotX = cx + cw - dotSize - 10;
            int dotY = h1Y + 7;
            boolean dotHover = isHeaderDotClicked(mx, my, cx, h1Y, cw);
            int dotCol = state ? (dotHover ? 0xFFBB00FF : colAccent) : (dotHover ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, dotCol);
            RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, dotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            
            cy += hH + 4;

            if (dungeonCustomBlocksOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Glass Pad", GhostBlockManager.isGlassGhostBlocksEnabled, mx, my, null, null);
            }
            
            int h2Y = cy;
            boolean h2Hover = (mx >= cx && mx < cx + cw && my >= h2Y && my < h2Y + hH);
            RenderUtils.fillRoundedRect(g, cx, h2Y, cw, hH, 4, lagTimeLostOpen ? colHover : (h2Hover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, h2Y, cw, hH, 4, lagTimeLostOpen ? colAccent : 0x55888888, 0x22444444);
            
            g.text(font, (lagTimeLostOpen ? "▼ " : "▶ ") + "Time Lost to Lag", cx + 8, h2Y + 7, COL_TEXT);
            
            boolean lagState = ModConfig.INSTANCE.enableLagTimeLost;
            int dotX2 = cx + cw - dotSize - 10;
            int dotY2 = h2Y + 7;
            boolean dotHover2 = isHeaderDotClicked(mx, my, cx, h2Y, cw);
            int dotCol2 = lagState ? (dotHover2 ? 0xFFBB00FF : colAccent) : (dotHover2 ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, dotX2, dotY2, dotSize, dotSize, 4, dotCol2);
            RenderUtils.drawGradientOutline(g, dotX2, dotY2, dotSize, dotSize, 4, dotHover2 ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            
            cy += hH + 4;

            if (lagTimeLostOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Send in Chat", ModConfig.INSTANCE.sendLagTimeLost, mx, my, null, null);
            }

        } else if (activeTab == 3) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int clipHeight = clipBottom - clipTop;

            int totalContentH = 0;
            int hH = 22;
            totalContentH += hH + 4;
            if (slayerCarryOpen) {
                totalContentH += (ROW_H + 4) * 2 + 44 + (hH + 4);
                if (slayerGlowOpen) {
                    totalContentH += 22 + 4 + 26 + (10 + 8 + 4) * 3 + 6;
                }
            }

            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (slayerScrollY > maxScroll) slayerScrollY = maxScroll;
            if (slayerScrollY < 0) slayerScrollY = 0;

            g.enableScissor(cx - 2, clipTop, cx + cw + 4, clipBottom);
            cy -= slayerScrollY;

            int hY = cy;
            boolean hHover = (mx >= cx && mx < cx + cw && my >= Math.max(hY, clipTop) && my < Math.min(hY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, hY, cw, hH, 4, slayerCarryOpen ? colHover : (hHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, hY, cw, hH, 4, slayerCarryOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (slayerCarryOpen ? "▼ " : "▶ ") + "Slayer Carry Tracker", cx + 8, hY + 7, COL_TEXT);

            boolean slayerState = ModConfig.INSTANCE.enableSlayerCarry;
            int dotSize = 8;
            int dotX = cx + cw - dotSize - 10;
            int dotY = hY + 7;
            RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, slayerState ? colAccent : COL_OFF);
            RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, 0x44FFFFFF, 0x11000000);
            cy += hH + 4;

            if (slayerCarryOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Boss Spawn Notification", ModConfig.INSTANCE.enableBossSpawnHud, mx, my, null, null);
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Auto Send Coords", ModConfig.INSTANCE.enableSlayerAutoCoords, mx, my, null, null);
                
                RenderUtils.fillRoundedRect(g, cx + 10, cy, cw - 10, 40, 4, 0x11FFFFFF);
                g.text(font, "§7Commands:", cx + 16, cy + 4, COL_TEXT_DIM);
                g.text(font, "/wish slayer carry <player> <amount>", cx + 24, cy + 16, 0xFFBBBBBB);
                g.text(font, "/wish slayer stopcarry <player>", cx + 24, cy + 26, 0xFFBBBBBB);
                cy += 44;

                int glowHY = cy;
                boolean glowHover = (mx >= cx + 10 && mx < cx + cw && my >= Math.max(glowHY, clipTop) && my < Math.min(glowHY + hH, clipBottom));
                RenderUtils.fillRoundedRect(g, cx + 10, glowHY, cw - 10, hH, 4, slayerGlowOpen ? colHover : (glowHover ? 0x44444455 : 0x2A2A2E55));
                RenderUtils.drawGradientOutline(g, cx + 10, glowHY, cw - 10, hH, 4, slayerGlowOpen ? colAccent : 0x55888888, 0x22444444);
                g.text(font, (slayerGlowOpen ? "▼ " : "▶ ") + "Slayer Glow", cx + 18, glowHY + 7, COL_TEXT);

                boolean glowState = ModConfig.INSTANCE.enableSlayerGlow;
                int dotX2 = cx + cw - dotSize - 10;
                int dotY2 = glowHY + 7;
                boolean dotHover2 = isHeaderDotClicked(mx, my, cx + 10, glowHY, cw - 10);
                int dotCol2 = glowState ? (dotHover2 ? 0xFFBB00FF : colAccent) : (dotHover2 ? 0xFFFF5566 : COL_OFF);
                RenderUtils.fillRoundedRect(g, dotX2, dotY2, dotSize, dotSize, 4, dotCol2);
                RenderUtils.drawGradientOutline(g, dotX2, dotY2, dotSize, dotSize, 4, dotHover2 ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
                cy += hH + 4;

                if (slayerGlowOpen) {
                    int boxW = 50;
                    int boxH = 22;
                    int previewColor = parseHex(ModConfig.INSTANCE.slayerGlowColor, 0xFFFF55AA);
                    
                    RenderUtils.fillRoundedRect(g, cx + 10, cy, boxW, boxH, 4, previewColor);
                    RenderUtils.drawGradientOutline(g, cx + 10, cy, boxW, boxH, 4, 0x66FFFFFF, 0x22FFFFFF);
                    g.text(font, "Glow Preview", cx + 10 + boxW + 10, cy + 7, COL_TEXT);
                    cy += boxH + 4;
                    
                    cy = renderInput(g, font, cx + 10, cy, cw - 10, "Glow Hex", ModConfig.INSTANCE.slayerGlowColor, hexInputFocused, mx, my);

                    int hueW = cw - 20;
                    int hueH = 8;
                    
                    g.text(font, "Hue", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB((float)i / hueW, 1.0f, 1.0f) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    int pickerX = cx + 10 + (int)(selectedHue * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 4;
                    
                    g.text(font, "Saturation", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, (float)i / hueW, selectedVal) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    pickerX = cx + 10 + (int)(selectedSat * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 4;
                    
                    g.text(font, "Brightness", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, selectedSat, (float)i / hueW) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    pickerX = cx + 10 + (int)(selectedVal * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 6;
                }
            }

            g.disableScissor();

            if (maxScroll > 0) {
                int scrollTrackX = cx + cw + 4;
                int scrollTrackY = clipTop;
                int scrollTrackW = 3;
                int scrollTrackH = clipHeight;

                RenderUtils.fillRoundedRect(g, scrollTrackX, scrollTrackY, scrollTrackW, scrollTrackH, 2, 0x22FFFFFF);
                int barH = Math.max(16, (int)((float)clipHeight / (float)totalContentH * scrollTrackH));
                int barY = scrollTrackY + (int)((float)slayerScrollY / (float)maxScroll * (scrollTrackH - barH));
                RenderUtils.fillRoundedRect(g, scrollTrackX, barY, scrollTrackW, barH, 2, colAccent);
            }

        } else if (activeTab == 6) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int clipHeight = clipBottom - clipTop;

            int totalContentH = 0;
            int hH = 22;
            int noteH = 80;
            totalContentH += hH + 4; 
            if (miningAutomatonOpen) {
                totalContentH += (ROW_H + 4); 
                totalContentH += (ROW_H + 4); 
                totalContentH += (ROW_H + 4); 
                totalContentH += 26 + 4; // Pause Timeout slider
                totalContentH += hH + 4; 
                if (automatonGlowOpen) {
                    totalContentH += ROW_H + 4; 
                    totalContentH += 22 + 4 + 26 + (10 + 8 + 4) * 3 + 6; 
                }
                totalContentH += noteH + 6; 
            }

            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (miningScrollY > maxScroll) miningScrollY = maxScroll;
            if (miningScrollY < 0) miningScrollY = 0;

            g.enableScissor(cx, clipTop, cx + cw, clipBottom);
            cy -= miningScrollY;

            
            int hY = cy;
            boolean hHover = (mx >= cx && mx < cx + cw && my >= Math.max(hY, clipTop) && my < Math.min(hY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, hY, cw, hH, 4, miningAutomatonOpen ? colHover : (hHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, hY, cw, hH, 4, miningAutomatonOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (miningAutomatonOpen ? "▼ " : "▶ ") + "Automaton", cx + 8, hY + 7, COL_TEXT);

            boolean autoState = ModConfig.INSTANCE.enableMiningAutomaton;
            int dotSize = 8;
            int dotX = cx + cw - dotSize - 10;
            int dotY = hY + 7;
            boolean dotHover = isHeaderDotClicked(mx, my, cx, hY, cw);
            int dotCol = autoState ? (dotHover ? 0xFFBB00FF : colAccent) : (dotHover ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, dotCol);
            RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, dotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            cy += hH + 4;

            if (miningAutomatonOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Tracker HUD", ModConfig.INSTANCE.enableAutomatonTracker, mx, my, "Tracker HUD", "Toggle on-screen Automaton Tracker overlay");
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "City Tracker", ModConfig.INSTANCE.enablePrecursorCityScanner, mx, my, "City Tracker", "Locate and show waypoint to Precursor City");
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Enable if you have Crystal Hollows Sack", ModConfig.INSTANCE.automatonSackMode, mx, my, "Crystal Hollows Sack", "ON: Drops are tracked via Sacks only, prevents Bazaar sell offers from counting as fake drops. Turn OFF only if you don't have a Crystal Hollows Sack.");
                cy = renderSlider(g, font, cx + 10, cy, cw - 10, "Pause Timeout", (float) ModConfig.INSTANCE.automatonPauseTimeout, mx, my, 10, 1.0f, 20.0f);
                
                int glowHY = cy;
                boolean glowHover = (mx >= cx + 10 && mx < cx + cw && my >= Math.max(glowHY, clipTop) && my < Math.min(glowHY + hH, clipBottom));
                RenderUtils.fillRoundedRect(g, cx + 10, glowHY, cw - 10, hH, 4, automatonGlowOpen ? colHover : (glowHover ? 0x44444455 : 0x2A2A2E55));
                RenderUtils.drawGradientOutline(g, cx + 10, glowHY, cw - 10, hH, 4, automatonGlowOpen ? colAccent : 0x55888888, 0x22444444);
                g.text(font, (automatonGlowOpen ? "▼ " : "▶ ") + "Automaton Glow", cx + 18, glowHY + 7, COL_TEXT);

                boolean glowState = ModConfig.INSTANCE.enableAutomatonGlow;
                int dotX2 = cx + cw - dotSize - 10;
                int dotY2 = glowHY + 7;
                boolean dotHover2 = isHeaderDotClicked(mx, my, cx + 10, glowHY, cw - 10);
                int dotCol2 = glowState ? (dotHover2 ? 0xFFBB00FF : colAccent) : (dotHover2 ? 0xFFFF5566 : COL_OFF);
                RenderUtils.fillRoundedRect(g, dotX2, dotY2, dotSize, dotSize, 4, dotCol2);
                RenderUtils.drawGradientOutline(g, dotX2, dotY2, dotSize, dotSize, 4, dotHover2 ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
                cy += hH + 4;

                if (automatonGlowOpen) {
                    cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Enable Glow", ModConfig.INSTANCE.enableAutomatonGlow, mx, my, "Enable Glow", "Glow Automaton mobs when visible in line-of-sight");

                    int boxW = 50;
                    int boxH = 22;
                    int previewColor = parseHex(ModConfig.INSTANCE.automatonGlowColor, 0xFFFF3355);

                    RenderUtils.fillRoundedRect(g, cx + 10, cy, boxW, boxH, 4, previewColor);
                    RenderUtils.drawGradientOutline(g, cx + 10, cy, boxW, boxH, 4, 0x66FFFFFF, 0x22FFFFFF);
                    g.text(font, "Glow Preview", cx + 10 + boxW + 10, cy + 7, COL_TEXT);
                    cy += boxH + 4;

                    cy = renderInput(g, font, cx + 10, cy, cw - 10, "Glow Hex", ModConfig.INSTANCE.automatonGlowColor, hexInputFocused, mx, my);

                    int hueW = cw - 20;
                    int hueH = 8;

                    g.text(font, "Hue", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB((float)i / hueW, 1.0f, 1.0f) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    int pickerX = cx + 10 + (int)(selectedHue * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 4;

                    g.text(font, "Saturation", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, (float)i / hueW, selectedVal) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    pickerX = cx + 10 + (int)(selectedSat * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 4;

                    g.text(font, "Brightness", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, selectedSat, (float)i / hueW) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    pickerX = cx + 10 + (int)(selectedVal * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 6;
                }

                
                int noteX = cx + 10;
                int noteW = cw - 10;
                RenderUtils.fillRoundedRect(g, noteX, cy, noteW, noteH, 5, 0x3314141E);
                RenderUtils.drawGradientOutline(g, noteX, cy, noteW, noteH, 5, 0x33FFFFFF, 0x11FFFFFF);

                g.text(font, "💡 Important Notes", noteX + 8, cy + 6, COL_TEXT);
                g.text(font, "• Click [Session]/[Total] or [Sell]/[Instasell] on GUI", noteX + 8, cy + 18, COL_TEXT);
                g.text(font, "• /wish automatonresetsession to reset session stats", noteX + 8, cy + 30, COL_TEXT);
                g.text(font, "• /wish automatonresettotal to reset total lifetime stats", noteX + 8, cy + 42, COL_TEXT);
                g.text(font, "• Sacks: ON = Sacks only | OFF = Inventory drops", noteX + 8, cy + 54, COL_TEXT);
                g.text(font, "• Precursor City only - detection range 150m", noteX + 8, cy + 66, COL_TEXT);
                cy += noteH + 6;
            }

            g.disableScissor();

            if (maxScroll > 0) {
                int scrollTrackX = cx + cw + 3;
                int scrollTrackY = clipTop;
                int scrollTrackH = clipHeight;
                int barH = Math.max(16, (int)((float)clipHeight / (float)totalContentH * clipHeight));
                int barY = scrollTrackY + (int)((float)miningScrollY / (float)maxScroll * (scrollTrackH - barH));

                RenderUtils.fillRoundedRect(g, scrollTrackX, scrollTrackY, 3, scrollTrackH, 1, 0x22FFFFFF);
                RenderUtils.fillRoundedRect(g, scrollTrackX, barY, 3, barH, 1, colAccentDim);
            }
            
        } else if (activeTab == 7) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int clipHeight = clipBottom - clipTop;

            activeDropOptions = new String[0];
            int hH = 22;
            int totalContentH = getVisualContentHeight(hH);

            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (visualScrollY > maxScroll) visualScrollY = maxScroll;
            if (visualScrollY < 0) visualScrollY = 0;

            g.enableScissor(cx, clipTop, cx + cw, clipBottom);
            cy -= visualScrollY;

            int hY = cy;
            boolean hHover = (mx >= cx && mx < cx + cw && my >= Math.max(hY, clipTop) && my < Math.min(hY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, hY, cw, hH, 4, lyricsSectionOpen ? colHover : (hHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, hY, cw, hH, 4, lyricsSectionOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (lyricsSectionOpen ? "▼ " : "▶ ") + "3D Lyrics", cx + 8, hY + 7, COL_TEXT);

            if (LyricsManager.hasSyncedLyrics()) {
                String badge = "• Synced";
                int badgeW = font.width(badge);
                g.text(font, badge, cx + cw - 18 - badgeW - 8, hY + 7, 0xFF44EE88);
            }

            boolean lyricsState = ModConfig.INSTANCE.enableLyrics;
            int dotSize = 8;
            int dotX = cx + cw - dotSize - 10;
            int dotY = hY + 7;
            boolean dotHover = isHeaderDotClicked(mx, my, cx, hY, cw);
            int dotCol = lyricsState ? (dotHover ? 0xFFBB00FF : colAccent) : (dotHover ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, dotCol);
            RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, dotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            cy += hH + 4;

            if (lyricsSectionOpen) {
                // Now Playing Mini Card (Valence style)
                int cardH = 46;
                int cardY = cy;
                RenderUtils.fillRoundedRect(g, cx + 10, cardY, cw - 10, cardH, 5, 0x33181822);
                RenderUtils.drawGradientOutline(g, cx + 10, cardY, cw - 10, cardH, 5, 0x44665588, 0x11000000);

                String nowTitle = LyricsManager.getCurrentTitle();
                if (nowTitle.isEmpty()) nowTitle = "No song playing";
                String nowArtist = LyricsManager.getCurrentArtist();
                if (nowArtist.isEmpty()) nowArtist = "Open Spotify, YouTube Music or Deezer";

                long pos = LyricsManager.getProgressMs();
                long dur = LyricsManager.getDurationMs();
                long posSec = (pos / 1000) % 60;
                long posMin = (pos / 1000) / 60;
                long durSec = (dur / 1000) % 60;
                long durMin = (dur / 1000) / 60;
                String timeStr = String.format("%d:%02d / %d:%02d", posMin, posSec, durMin, durSec);

                boolean synced = LyricsManager.hasSyncedLyrics();
                boolean fetching = LyricsManager.isFetchingLyrics();
                boolean hasTrack = !nowTitle.equals("No song playing") && !nowTitle.isEmpty();

                String syncText;
                int syncCol;
                if (synced) {
                    int lines = LyricsManager.getLyricsLineCount();
                    syncText = "• Lyrics synced" + (lines > 0 ? " (" + lines + " lines)" : "");
                    syncCol = 0xFF44EE88;
                } else if (fetching) {
                    syncText = "○ Searching lyrics...";
                    syncCol = 0xFFFFAA00;
                } else if (hasTrack) {
                    syncText = "○ No synced lyrics found";
                    syncCol = 0xFFFF6666;
                } else {
                    syncText = "○ Waiting for music player";
                    syncCol = 0xFFAAAAAA;
                }

                int maxTextW = cw - 30;
                String dispTitle = font.plainSubstrByWidth("♪  " + nowTitle, maxTextW);
                String dispArtist = font.plainSubstrByWidth(nowArtist, maxTextW);

                g.text(font, dispTitle, cx + 18, cardY + 5, 0xFFFFFFFF);
                g.text(font, "§7" + dispArtist, cx + 18, cardY + 16, COL_TEXT_DIM);
                g.text(font, syncText + (dur > 0 ? "  §8" + timeStr : ""), cx + 18, cardY + 28, syncCol);

                if (dur > 0) {
                    float prog = Math.max(0f, Math.min(1f, (float) pos / dur));
                    int pBarW = cw - 14;
                    int fillW = (int) (prog * (pBarW - 4));
                    g.fill(cx + 12, cardY + cardH - 2, cx + 12 + (pBarW - 4), cardY + cardH - 1, 0x33FFFFFF);
                    g.fill(cx + 12, cardY + cardH - 2, cx + 12 + fillW, cardY + cardH - 1, colAccent);
                }
                cy += cardH + 6;

                int provY = cy;
                cy = renderVisualDropdownRow(g, font, cx, cy, cw, clipTop, clipBottom, "Music Provider", MUSIC_PROVIDER_OPTIONS, getMusicProviderIndex(), 1, mx, my, colHover, colAccent);
                if ("YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)) {
                    int pBtnX = cx + cw - VISUAL_DROP_W - 10;
                    int pairBtnW = 60;
                    int pairBtnX = pBtnX - pairBtnW - 6;
                    boolean pairBtnHover = (mx >= pairBtnX && mx < pairBtnX + pairBtnW && my >= provY + 2 && my < provY + ROW_H - 2 && my >= clipTop && my <= clipBottom);
                    int pairBg = pairBtnHover ? 0xDD1DB954 : 0xAA1DB954;
                    RenderUtils.fillRoundedRect(g, pairBtnX, provY + 2, pairBtnW, ROW_H - 4, 3, pairBg);
                    RenderUtils.drawGradientOutline(g, pairBtnX, provY + 2, pairBtnW, ROW_H - 4, 3, pairBtnHover ? 0xFFFFFFFF : 0x88FFFFFF, 0x22000000);
                    String pairLabel = "Pair";
                    g.text(font, pairLabel, pairBtnX + (pairBtnW - font.width(pairLabel)) / 2, provY + 6, 0xFFFFFFFF);
                    if (pairBtnHover) {
                        hoveredTooltipTitle = "Pair YouTube Music API";
                        hoveredTooltipDesc = "Connects to YouTube Music Desktop App via companion API (Port 9863).";
                    }
                }

                int modeIdx = (ModConfig.INSTANCE.lyricsMode == 1) ? 1 : 0;
                cy = renderVisualDropdownRow(g, font, cx, cy, cw, clipTop, clipBottom, "Show", LYRICS_SHOW_OPTIONS, modeIdx, 2, mx, my, colHover, colAccent);

                int colorHY = cy;
                boolean colorHover = (mx >= cx + 10 && mx < cx + cw && my >= Math.max(colorHY, clipTop) && my < Math.min(colorHY + hH, clipBottom));
                RenderUtils.fillRoundedRect(g, cx + 10, colorHY, cw - 10, hH, 4, lyricsColorOpen ? colHover : (colorHover ? 0x44444455 : 0x2A2A2E55));
                RenderUtils.drawGradientOutline(g, cx + 10, colorHY, cw - 10, hH, 4, lyricsColorOpen ? colAccent : 0x55888888, 0x22444444);
                g.text(font, (lyricsColorOpen ? "▼ " : "▶ ") + "Color", cx + 18, colorHY + 7, COL_TEXT);

                int cDotSize = 8;
                int dotX2 = cx + cw - cDotSize - 10;
                int dotY2 = colorHY + 7;
                int curColorInt = parseHex(ModConfig.INSTANCE.lyricsColor, 0xFFFFFFFF);
                RenderUtils.fillRoundedRect(g, dotX2, dotY2, cDotSize, cDotSize, 4, curColorInt);
                RenderUtils.drawGradientOutline(g, dotX2, dotY2, cDotSize, cDotSize, 4, 0x66FFFFFF, 0x11000000);
                cy += hH + 4;

                if (lyricsColorOpen) {
                    int boxW = 50;
                    int boxH = 22;
                    int previewColor = curColorInt;

                    RenderUtils.fillRoundedRect(g, cx + 10, cy, boxW, boxH, 4, previewColor);
                    RenderUtils.drawGradientOutline(g, cx + 10, cy, boxW, boxH, 4, 0x66FFFFFF, 0x22FFFFFF);
                    g.text(font, "Lyrics Preview", cx + 10 + boxW + 10, cy + 7, COL_TEXT);
                    cy += boxH + 4;

                    String curHex = ModConfig.INSTANCE.lyricsColor != null ? ModConfig.INSTANCE.lyricsColor : "#FFFFFF";
                    cy = renderInput(g, font, cx + 10, cy, cw - 10, "Lyrics Hex", curHex, hexInputFocused, mx, my);

                    // Roue de couleur (Hue, Saturation, Brightness sliders)
                    int hueW = cw - 20;
                    int hueH = 8;

                    g.text(font, "Hue", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB((float)i / hueW, 1.0f, 1.0f) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    int pickerX = cx + 10 + (int)(selectedHue * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 4;

                    g.text(font, "Saturation", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, (float)i / hueW, selectedVal) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    pickerX = cx + 10 + (int)(selectedSat * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 4;

                    g.text(font, "Brightness", cx + 10, cy, COL_TEXT_DIM); cy += 10;
                    for (int i = 0; i < hueW; i++) {
                        int c = java.awt.Color.HSBtoRGB(selectedHue, selectedSat, (float)i / hueW) | 0xFF000000;
                        g.fill(cx + 10 + i, cy, cx + 10 + i + 1, cy + hueH, c);
                    }
                    pickerX = cx + 10 + (int)(selectedVal * hueW);
                    pickerX = Math.max(cx + 10, Math.min(cx + 10 + hueW - 3, pickerX));
                    g.fill(pickerX - 1, cy - 2, pickerX + 2, cy + hueH + 2, 0xFFFFFFFF);
                    cy += hueH + 6;
                }

                int noteY = cy;
                RenderUtils.fillRoundedRect(g, cx + 10, noteY, cw - 10, 24, 4, 0x22111116);
                RenderUtils.drawGradientOutline(g, cx + 10, noteY, cw - 10, 24, 4, 0x44666666, 0x22222222);
                g.text(font, "§7Supports §fSpotify, YouTube Music §7& §fDeezer", cx + 18, noteY + 8, COL_TEXT_DIM);
                cy += 24 + 6;
            }

            // Time Changer Section Header
            int timeHY = cy;
            boolean timeHHover = (mx >= cx && mx < cx + cw && my >= Math.max(timeHY, clipTop) && my < Math.min(timeHY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, timeHY, cw, hH, 4, timeChangerSectionOpen ? colHover : (timeHHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, timeHY, cw, hH, 4, timeChangerSectionOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (timeChangerSectionOpen ? "▼ " : "▶ ") + "Time Changer", cx + 8, timeHY + 7, COL_TEXT);

            boolean timeState = ModConfig.INSTANCE.enableCustomTime;
            int timeDotX = cx + cw - dotSize - 10;
            int timeDotY = timeHY + 7;
            boolean timeDotHover = isHeaderDotClicked(mx, my, cx, timeHY, cw);
            int timeDotCol = timeState ? (timeDotHover ? 0xFFBB00FF : colAccent) : (timeDotHover ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, timeDotX, timeDotY, dotSize, dotSize, 4, timeDotCol);
            RenderUtils.drawGradientOutline(g, timeDotX, timeDotY, dotSize, dotSize, 4, timeDotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            cy += hH + 4;

            if (timeChangerSectionOpen) {
                // Custom Time Slider
                String timeFormatted = com.wish.client.features.visual.TimeManager.formatTime(ModConfig.INSTANCE.customTimeTicks);
                String sliderTitle = "Custom Time: §e" + timeFormatted + " §7(" + ModConfig.INSTANCE.customTimeTicks + " ticks)";
                cy = renderSlider(g, font, cx + 10, cy, cw - 10, sliderTitle, ModConfig.INSTANCE.customTimeTicks / 24000.0f, mx, my, 14, 0.0f, 1.0f);

                // Freeze Time Toggle
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Freeze Time", ModConfig.INSTANCE.customTimeFrozen, mx, my, "Freeze Time", "Locks client time at this exact moment vs letting time advance naturally");
            }

            // Weather Changer Section Header
            int weatherHY = cy;
            boolean weatherHHover = (mx >= cx && mx < cx + cw && my >= Math.max(weatherHY, clipTop) && my < Math.min(weatherHY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, weatherHY, cw, hH, 4, weatherChangerSectionOpen ? colHover : (weatherHHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, weatherHY, cw, hH, 4, weatherChangerSectionOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (weatherChangerSectionOpen ? "▼ " : "▶ ") + "Weather Changer", cx + 8, weatherHY + 7, COL_TEXT);

            boolean weatherState = ModConfig.INSTANCE.enableCustomWeather;
            int weatherDotX = cx + cw - dotSize - 10;
            int weatherDotY = weatherHY + 7;
            boolean weatherDotHover = isHeaderDotClicked(mx, my, cx, weatherHY, cw);
            int weatherDotCol = weatherState ? (weatherDotHover ? 0xFFBB00FF : colAccent) : (weatherDotHover ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, weatherDotX, weatherDotY, dotSize, dotSize, 4, weatherDotCol);
            RenderUtils.drawGradientOutline(g, weatherDotX, weatherDotY, dotSize, dotSize, 4, weatherDotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            cy += hH + 4;

            if (weatherChangerSectionOpen) {
                int wMode = com.wish.client.features.visual.WeatherManager.getMode();
                cy = renderVisualDropdownRow(g, font, cx, cy, cw, clipTop, clipBottom, "Weather Type", com.wish.client.features.visual.WeatherManager.MODE_NAMES, wMode, 3, mx, my, colHover, colAccent);

                float wCount = com.wish.client.features.visual.WeatherManager.getCount();
                cy = renderPercentSlider(g, font, cx + 10, cy, cw - 10, "Count", wCount, mx, my, 0.1f, 3.0f);

                float wSize = com.wish.client.features.visual.WeatherManager.getSize();
                cy = renderPercentSlider(g, font, cx + 10, cy, cw - 10, "Size", wSize, mx, my, 0.25f, 2.5f);

                boolean windOn = com.wish.client.features.visual.WeatherManager.isWindEnabled();
                String windLabel = windOn ? ("Wind (§e" + com.wish.client.features.visual.WeatherManager.getWindDirectionLabel() + "§r)") : "Wind";
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, windLabel, windOn, mx, my, "Dynamic Wind", "Slants rain & swirls snow with wind that shifts direction smoothly over time.");

                if (windOn) {
                    float windStr = com.wish.client.features.visual.WeatherManager.getWindStrength();
                    cy = renderPercentSlider(g, font, cx + 10, cy, cw - 10, "Wind Force", windStr, mx, my, 0.25f, 2.0f);
                }
            }

            // Custom Sky Section Header
            int skyHY = cy;
            boolean skyHHover = (mx >= cx && mx < cx + cw && my >= Math.max(skyHY, clipTop) && my < Math.min(skyHY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, skyHY, cw, hH, 4, customSkySectionOpen ? colHover : (skyHHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, skyHY, cw, hH, 4, customSkySectionOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (customSkySectionOpen ? "▼ " : "▶ ") + "Custom Sky", cx + 8, skyHY + 7, COL_TEXT);

            boolean skyState = ModConfig.INSTANCE.enableCustomSky;
            int skyDotX = cx + cw - dotSize - 10;
            int skyDotY = skyHY + 7;
            boolean skyDotHover = isHeaderDotClicked(mx, my, cx, skyHY, cw);
            int skyDotCol = skyState ? (skyDotHover ? 0xFFBB00FF : colAccent) : (skyDotHover ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, skyDotX, skyDotY, dotSize, dotSize, 4, skyDotCol);
            RenderUtils.drawGradientOutline(g, skyDotX, skyDotY, dotSize, dotSize, 4, skyDotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            cy += hH + 4;

            if (customSkySectionOpen) {
                int skyModeIdx = Math.max(0, Math.min(com.wish.client.features.visual.CustomSkyManager.MODE_NAMES.length - 1, ModConfig.INSTANCE.customSkyMode));
                cy = renderVisualDropdownRow(g, font, cx, cy, cw, clipTop, clipBottom, "Sky Background", com.wish.client.features.visual.CustomSkyManager.MODE_NAMES, skyModeIdx, 4, mx, my, colHover, colAccent);

                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Show Stars", ModConfig.INSTANCE.customSkyShowStars, mx, my, "Show Stars", "Renders bright stars in the custom sky.");
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Show Sun & Moon", ModConfig.INSTANCE.customSkyShowSunMoon, mx, my, "Show Sun & Moon", "Keeps the sun and moon visible in the custom sky.");
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Slow Rotation", ModConfig.INSTANCE.customSkyRotate, mx, my, "Slow Rotation", "Gently rotates the custom sky dome over time.");
            }

            // Smol People Section Header
            int opsHY = cy;
            boolean opsHHover = (mx >= cx && mx < cx + cw && my >= Math.max(opsHY, clipTop) && my < Math.min(opsHY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, opsHY, cw, hH, 4, otherPlayersSizeSectionOpen ? colHover : (opsHHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, opsHY, cw, hH, 4, otherPlayersSizeSectionOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (otherPlayersSizeSectionOpen ? "▼ " : "▶ ") + "Smol People", cx + 8, opsHY + 7, COL_TEXT);

            boolean opsState = ModConfig.INSTANCE.enableOtherPlayersSize;
            int opsDotX = cx + cw - dotSize - 10;
            int opsDotY = opsHY + 7;
            boolean opsDotHover = isHeaderDotClicked(mx, my, cx, opsHY, cw);
            int opsDotCol = opsState ? (opsDotHover ? 0xFFBB00FF : colAccent) : (opsDotHover ? 0xFFFF5566 : COL_OFF);
            RenderUtils.fillRoundedRect(g, opsDotX, opsDotY, dotSize, dotSize, 4, opsDotCol);
            RenderUtils.drawGradientOutline(g, opsDotX, opsDotY, dotSize, dotSize, 4, opsDotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
            cy += hH + 4;

            if (otherPlayersSizeSectionOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Include Yourself", ModConfig.INSTANCE.smolPeopleIncludeSelf, mx, my, "Include Yourself", "Also renders your own player with SmolPeople sizing.");
            }

            g.disableScissor();
            renderVisualDropdownPopup(g, font, mx, my, colHover, colAccent);

            if (maxScroll > 0) {
                int scrollTrackX = cx + cw + 3;
                int scrollTrackY = clipTop;
                int scrollTrackH = clipHeight;
                int barH = Math.max(16, (int)((float)clipHeight / (float)totalContentH * clipHeight));
                int barY = scrollTrackY + (int)((float)visualScrollY / (float)maxScroll * (scrollTrackH - barH));

                RenderUtils.fillRoundedRect(g, scrollTrackX, scrollTrackY, 3, scrollTrackH, 1, 0x22FFFFFF);
                RenderUtils.fillRoundedRect(g, scrollTrackX, barY, 3, barH, 1, colAccentDim);
            }
            
        } else if (activeTab == 4) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int clipHeight = clipBottom - clipTop;

            int totalContentH = ROW_H * 3 + 4 * 3 + 14 + 22 + 10; 

            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (miscScrollY > maxScroll) miscScrollY = maxScroll;
            if (miscScrollY < 0) miscScrollY = 0;

            g.enableScissor(cx, clipTop, cx + cw, clipBottom);
            cy -= miscScrollY;

            cy = renderToggle(g, font, cx, cy, cw, "Check for Updates", ModConfig.INSTANCE.enableUpdateCheck, mx, my, null, null);
            cy = renderToggle(g, font, cx, cy, cw, "Always in M7/F7 (Simulators)", ModConfig.INSTANCE.alwaysInM7F7, mx, my, null, null);

            
            boolean hoverCosmetic = (mx >= cx && mx < cx + cw && my >= cy && my < cy + ROW_H);
            int cosmeticBg = hoverCosmetic ? 0x4444444C : 0x332E2E33;
            int cosmeticBorder = hoverCosmetic ? 0x88AAAAAA : 0x44666666;
            
            RenderUtils.fillRoundedRect(g, cx, cy, cw, ROW_H, 4, cosmeticBg);
            RenderUtils.drawGradientOutline(g, cx, cy, cw, ROW_H, 4, cosmeticBorder, 0x22222222);
            g.text(font, "Cosmetics Visibility", cx + 8, cy + 7, COL_TEXT);

            int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
            String cosText = switch (cosMode) {
                case 1 -> "Mine Only";
                case 2 -> "Hide All";
                default -> "Show All";
            };
            
            int cosW = 94;
            int cosBx = cx + cw - cosW - 8;
            int cosBy = cy + 2;
            int cosBh = ROW_H - 4;
            boolean cosHover = (mx >= cosBx && mx < cosBx + cosW && my >= cosBy && my < cosBy + cosBh);
            RenderUtils.fillRoundedRect(g, cosBx, cosBy, cosW, cosBh, 3, cosHover ? colHover : 0x44000000);
            RenderUtils.drawGradientOutline(g, cosBx, cosBy, cosW, cosBh, 3, (cosHover || cosmeticDropdownOpen) ? colAccent : 0x33FFFFFF, 0x11000000);
            
            g.text(font, cosText, cosBx + 8, cosBy + 4, COL_TEXT);
            String chevron = cosmeticDropdownOpen ? "▲" : "▼";
            g.text(font, chevron, cosBx + cosW - 14, cosBy + 4, 0xFFAAAAAA);

            int savedCosDropX = cosBx;
            int savedCosDropY = cosBy + cosBh + 2;
            cy += ROW_H + 4;

            cy += 14;
            int copyBtnW = Math.min(cw - 24, 210);
            int copyBtnH = 22;
            int copyBtnX = cx + (cw - copyBtnW) / 2;
            boolean copyHover = (mx >= copyBtnX && mx < copyBtnX + copyBtnW && my >= cy && my < cy + copyBtnH);
            
            RenderUtils.fillRoundedRect(g, copyBtnX, cy, copyBtnW, copyBtnH, 5, copyHover ? 0xEE445566 : 0xAA223344);
            RenderUtils.drawGradientOutline(g, copyBtnX, cy, copyBtnW, copyBtnH, 5, copyHover ? colAccent : 0x88446688, copyHover ? colAccentDim : 0x22000000);
            
            String btnText = "Copy Cosmetics Config";
            if (copyHover) {
                hoveredTooltipTitle = "Copy Cosmetics Config";
                hoveredTooltipDesc = "Copies your custom name, colors, animations, and model scaling settings to clipboard.";
            }
            
            int textW = font.width(btnText);
            g.text(font, btnText, copyBtnX + (copyBtnW - textW) / 2, cy + 7, copyHover ? 0xFFFFFFFF : 0xFFDDDDDD);
            cy += copyBtnH + 10;

            g.disableScissor();

            
            if (cosmeticDropdownOpen) {
                int dropW = 94;
                int dropH = 3 * 18 + 4;
                RenderUtils.fillRoundedRect(g, savedCosDropX, savedCosDropY, dropW, dropH, 4, 0xF518181E);
                RenderUtils.drawGradientOutline(g, savedCosDropX, savedCosDropY, dropW, dropH, 4, colAccent, 0x44444444);
                String[] cosLabels = {"Show All", "Mine Only", "Hide All"};
                for (int i = 0; i < 3; i++) {
                    int iy = savedCosDropY + 2 + i * 18;
                    boolean iHover = (mx >= savedCosDropX && mx < savedCosDropX + dropW && my >= iy && my < iy + 18);
                    if (iHover) {
                        RenderUtils.fillRoundedRect(g, savedCosDropX + 2, iy, dropW - 4, 18, (i == 2 ? 3 : 2), 0x33FFFFFF);
                    }
                    boolean isSelected = (cosMode == i);
                    g.text(font, cosLabels[i], savedCosDropX + 12, iy + 5, isSelected ? colAccent : 0xFFFFFFFF);
                    if (isSelected) {
                        g.text(font, "✓", savedCosDropX + dropW - 14, iy + 5, colAccent);
                    }
                }
            }

            if (maxScroll > 0) {
                int scrollTrackX = cx + cw + 3;
                int scrollTrackY = clipTop;
                int scrollTrackH = clipHeight;
                int barH = Math.max(16, (int)((float)clipHeight / (float)totalContentH * clipHeight));
                int barY = scrollTrackY + (int)((float)miscScrollY / (float)maxScroll * (scrollTrackH - barH));

                RenderUtils.fillRoundedRect(g, scrollTrackX, scrollTrackY, 3, scrollTrackH, 1, 0x22FFFFFF);
                RenderUtils.fillRoundedRect(g, scrollTrackX, barY, 3, barH, 1, colAccentDim);
            }
        }
        }  

        
        if (songSettingsOpen) {
            int winW = 220;
            int winH = 120;
            int winX = this.width / 2 - winW / 2;
            int winY = this.height / 2 - winH / 2;
            
            RenderUtils.fillRoundedRect(g, winX, winY, winW, winH, 4, 0xDD111115);
            RenderUtils.drawGradientOutline(g, winX, winY, winW, winH, 4, colAccent, 0x22222222);
            
            g.text(font, "Song Settings", winX + 15, winY + 15, 0xFFFFFFFF);
            
            int swCloseX = winX + winW - 25;
            int swCloseY = winY + 10;
            boolean swCloseHover = mx >= swCloseX && mx < swCloseX + 15 && my >= swCloseY && my < swCloseY + 15;
            RenderUtils.fillRoundedRect(g, swCloseX, swCloseY, 15, 15, 3, swCloseHover ? 0xAAFF3333 : 0x00000000);
            RenderUtils.drawGradientOutline(g, swCloseX, swCloseY, 15, 15, 3, 0x44FFFFFF, 0x11000000);
            for (int i=0; i<7; i++) {
                g.fill(swCloseX + 4 + i, swCloseY + 4 + i, swCloseX + 5 + i, swCloseY + 5 + i, 0xFFFFFFFF);
                g.fill(swCloseX + 10 - i, swCloseY + 4 + i, swCloseX + 11 - i, swCloseY + 5 + i, 0xFFFFFFFF);
            }
            
            int cX = winX + 15;
            int cY = winY + 40;
            int cW = winW - 30;
            
            g.text(font, "Music Provider", cX + 8, cY + 7, COL_TEXT);
            
            int bx = cX + cW - 90;
            int bw = 80;
            boolean bHover = (mx >= bx && mx < bx + bw && my >= cY + 2 && my < cY + ROW_H - 2);
            RenderUtils.fillRoundedRect(g, bx, cY + 2, bw, ROW_H - 4, 3, bHover ? colHover : 0x44000000);
            RenderUtils.drawGradientOutline(g, bx, cY + 2, bw, ROW_H - 4, 3, bHover ? 0x66FFFFFF : 0x33FFFFFF, 0x11000000);
            g.text(font, ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider : "None", bx + 6, cY + 6, COL_TEXT);
            
            if ("YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)) {
                cY += ROW_H + 4;
                int pairX = cX + 8;
                int pairW = bw;
                boolean pairHover = (mx >= pairX && mx < pairX + pairW && my >= cY + 2 && my < cY + ROW_H - 2);
                RenderUtils.fillRoundedRect(g, pairX, cY + 2, pairW, ROW_H - 4, 3, pairHover ? 0xDD1DB954 : 0xAA1DB954); 
                RenderUtils.drawGradientOutline(g, pairX, cY + 2, pairW, ROW_H - 4, 3, pairHover ? 0xFFFFFFFF : 0x88FFFFFF, 0x22000000);
                g.text(font, "Pair API", pairX + 15, cY + 6, 0xFFFFFFFF);
                cY -= ROW_H + 4;
            }
            
            if (providerDropdownOpen) {
                String[] plats = {"None", "YTM", "Spotify", "Deezer"};
                int dropH = plats.length * 18 + 2;
                RenderUtils.fillRoundedRect(g, bx, cY + ROW_H, bw, dropH, 4, 0xF018181E);
                RenderUtils.drawGradientOutline(g, bx, cY + ROW_H, bw, dropH, 4, colAccent, 0x44444444);
                for (int i = 0; i < plats.length; i++) {
                    int dy = cY + ROW_H + 1 + i * 18;
                    boolean dHover = (mx >= bx && mx < bx + bw && my >= dy && my < dy + 18);
                    if (dHover) {
                        RenderUtils.fillRoundedRect(g, bx + 2, dy, bw - 4, 18, (i == plats.length - 1 ? 3 : 2), colHover);
                    }
                    g.text(font, plats[i], bx + 8, dy + 5, COL_TEXT);
                }
            }
        }

        
        if (hoveredTooltipTitle != null && hoveredTooltipDesc != null) {
            renderTooltipCard(g, font, mx, my, hoveredTooltipTitle, hoveredTooltipDesc);
        }
    }

    private void renderSubMenuChoices(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my, int sidebarStartY, int categorySpacing, int colAccent, int colAccentDim, int colHover, float barAnim) {
        if (barAnim <= 0.01f || activeSubMenuCategory == 0) return;

        
        float textAlpha;
        int curOptX;
        int optX = 126;
        if (openCategoryPopup == 0) {
            long elapsed = System.currentTimeMillis() - barAnimStartTime;
            float closeProgress = Math.min(1.0f, elapsed / 70.0f);
            textAlpha = 1.0f - closeProgress;
            if (textAlpha <= 0.02f) {
                return; 
            }
            curOptX = optX - (int)(closeProgress * 12);
        } else {
            textAlpha = Math.min(1.0f, barAnim * 1.5f);
            curOptX = optX + (int)((1.0f - textAlpha) * 8);
        }

        int a = Math.max(0, Math.min(255, (int)(255 * textAlpha)));
        if (a < 5) return;
        int colNormal = (a << 24) | 0x00AAAAAA;
        int colHovered = (a << 24) | 0x00FFFFFF;
        int colArrow = (a << 24) | (colAccent & 0x00FFFFFF);

        int baseVertX = 160;
        int maxShift = 65;
        int vertX = baseVertX + (int)(maxShift * barAnim);

        
        g.enableScissor(35, 0, vertX, this.height);

        if (activeSubMenuCategory == 1) { 
            int catY = sidebarStartY;
            int opt1Y = catY - 11;
            int opt2Y = catY + 11;

            
            boolean hov1 = mx >= curOptX - 8 && mx < curOptX + 110 && my >= opt1Y - 6 && my < opt1Y + 16;
            g.text(font, "Custom Name", curOptX, opt1Y, hov1 ? colHovered : colNormal);
            if (hov1) {
                g.text(font, ">", curOptX + font.width("Custom Name") + 5, opt1Y, colArrow);
            }

            
            boolean hov2 = mx >= curOptX - 8 && mx < curOptX + 110 && my >= opt2Y - 6 && my < opt2Y + 16;
            g.text(font, "Player Size", curOptX, opt2Y, hov2 ? colHovered : colNormal);
            if (hov2) {
                g.text(font, ">", curOptX + font.width("Player Size") + 5, opt2Y, colArrow);
            }

        } else if (activeSubMenuCategory == 2) { 
            int catY = sidebarStartY + categorySpacing;
            String[] featLabels = {"Command", "Dungeon", "Slayer", "Mining", "Visual"};
            int[] optYs = {catY - 36, catY - 18, catY, catY + 18, catY + 36};

            for (int k = 0; k < featLabels.length; k++) {
                int y = optYs[k];
                boolean hov = mx >= curOptX - 8 && mx < curOptX + 110 && my >= y - 6 && my < y + 16;
                g.text(font, featLabels[k], curOptX, y, hov ? colHovered : colNormal);
                if (hov) {
                    g.text(font, ">", curOptX + font.width(featLabels[k]) + 5, y, colArrow);
                }
            }
        }

        g.disableScissor();
    }

    private void renderTooltipCard(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my, String title, String desc) {
        int ttW = Math.max(160, font.width(title) + 16);
        var lines = font.split(Component.literal(desc), 180);
        int maxLineW = 0;
        for (var line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        ttW = Math.max(ttW, maxLineW + 16);
        int ttH = 22 + lines.size() * 10;

        int ttX = mx + 12;
        int ttY = my - 10;
        if (ttX + ttW > this.width - 8) ttX = mx - ttW - 8;
        if (ttY + ttH > this.height - 8) ttY = this.height - ttH - 8;
        if (ttY < 8) ttY = 8;

        
        RenderUtils.fillRoundedRect(g, ttX - 2, ttY - 2, ttW + 4, ttH + 4, 6, 0x33000000);
        RenderUtils.drawGlassPanel(g, ttX, ttY, ttW, ttH, 0xF0111118, getAccentColor());

        
        g.text(font, "§l" + title, ttX + 8, ttY + 6, COL_TEXT);
        int lineY = ttY + 18;
        for (var line : lines) {
            g.text(font, line, ttX + 8, lineY, 0xFFCCCCCC);
            lineY += 10;
        }
    }

    public static boolean isHeaderDotClicked(double mx, double my, int x, int y, int w) {
        int dotSize = 8;
        int dotX = x + w - dotSize - 10;
        int dotY = y + 7;
        return (mx >= dotX - 6 && mx <= dotX + dotSize + 6 && my >= dotY - 6 && my <= dotY + dotSize + 6);
    }

    public static boolean isToggleBtnClicked(double mx, double my, int x, int y, int w) {
        int dotSize = 8;
        int dotX = x + w - dotSize - 10;
        int dotY = y + 7;
        return (mx >= dotX - 6 && mx <= dotX + dotSize + 6 && my >= dotY - 6 && my <= dotY + dotSize + 6);
    }

    private int renderToggle(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, boolean state, int mx, int my, String ttTitle, String ttDesc) {
        boolean barHover = (mx >= x && mx < x + w && my >= y && my < y + ROW_H);
        boolean dotHover = isToggleBtnClicked(mx, my, x, y, w);
        int bgColor = barHover ? 0x443A3A42 : 0x2A2A2E33;
        int borderColor = barHover ? 0x66888888 : 0x33555555;
        
        RenderUtils.fillRoundedRect(g, x, y, w, ROW_H, 4, bgColor);
        RenderUtils.drawGradientOutline(g, x, y, w, ROW_H, 4, borderColor, 0x22222222);

        g.text(font, label, x + 8, y + 7, state ? COL_TEXT : COL_TEXT_DIM);
        
        int dotSize = 8;
        int dotX = x + w - dotSize - 10;
        int dotY = y + 7;

        int dotCol = state ? (dotHover ? 0xFFBB00FF : getAccentColor()) : (dotHover ? 0xFFFF5566 : COL_OFF);
        RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, dotCol);
        RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, dotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);
        
        if (barHover && ttTitle != null && ttDesc != null) {
            hoveredTooltipTitle = ttTitle;
            hoveredTooltipDesc = ttDesc;
        }
        
        return y + ROW_H + 4;
    }

    private int renderInput(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, String val, boolean focused, int mx, int my) {
        int h = 22;
        boolean hover = (mx >= x && mx < x + w && my >= y && my < y + h);
        int bgColor = focused ? 0x33FFFFFF : (hover ? 0x22FFFFFF : 0x00000000);
        int borderColor = focused ? getAccentColor() : (hover ? 0x88AAAAAA : 0x44666666);
        
        RenderUtils.fillRoundedRect(g, x, y, w, h, 4, bgColor);
        RenderUtils.drawGradientOutline(g, x, y, w, h, 4, borderColor, 0x22222222);

        String cursor = (focused && (System.currentTimeMillis() / 400 % 2 == 0)) ? "_" : "";
        String text = label + ": " + val + cursor;
        int textCol = (label.toLowerCase().contains("spoof") || !val.isEmpty()) ? 0xFFFFFFFF : (focused ? COL_TEXT : COL_TEXT_DIM);
        g.text(font, text, x + 8, y + 7, textCol);
        
        return y + h + 4;
    }

    private int renderSlider(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, float val, int mx, int my, int id, float min, float max) {
        return renderSliderInternal(g, font, x, y, w, label + String.format(": %.2f", val), val, mx, my, min, max);
    }

    private int renderPercentSlider(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, float val, int mx, int my, float min, float max) {
        return renderSliderInternal(g, font, x, y, w, label + ": §e" + Math.round(val * 100.0f) + "%", val, mx, my, min, max);
    }

    private int renderSliderInternal(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String text, float val, int mx, int my, float min, float max) {
        int h = 26;
        boolean hover = (mx >= x && mx < x + w && my >= y && my < y + h);
        int bgColor = hover ? 0x5544444C : 0x332E2E33;
        int borderColor = hover ? 0x88AAAAAA : 0x44666666;
        
        RenderUtils.fillRoundedRect(g, x, y, w, h, 4, bgColor);
        RenderUtils.drawGradientOutline(g, x, y, w, h, 4, borderColor, 0x22222222);

        g.text(font, text, x + 8, y + 5, COL_TEXT);
        
        int trackY = y + 16;
        int trackW = w - 16;
        int trackX = x + 8;
        RenderUtils.fillRoundedRect(g, trackX, trackY, trackW, 4, 2, 0x55FFFFFF);
        
        float pct = (val - min) / (max - min);
        pct = Math.max(0, Math.min(1, pct));
        
        RenderUtils.fillRoundedRect(g, trackX, trackY, (int)(trackW * pct), 4, 2, getAccentColor());
        
        int knobX = trackX + (int)(trackW * pct) - 4;
        RenderUtils.fillRoundedRect(g, knobX, trackY - 3, 8, 10, 4, 0xFFFFFFFF);
        
        return y + h + 4;
    }

    private void updateColorFromSliders() {
        int rgb = java.awt.Color.HSBtoRGB(selectedHue, selectedSat, selectedVal) & 0xFFFFFF;
        String hex = String.format("#%06X", rgb);
        if (activeTab == 3) {
            ModConfig.INSTANCE.slayerGlowColor = hex;
        } else if (activeTab == 6) {
            ModConfig.INSTANCE.automatonGlowColor = hex;
        } else if (activeTab == 7) {
            ModConfig.INSTANCE.lyricsColor = hex;
        } else if (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2) {
            ModConfig.INSTANCE.customHexColor2 = hex;
        } else {
            ModConfig.INSTANCE.customHexColor = hex;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int mx = (int) (Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getScreenWidth());
        float barAnim = getBarAnimProgress();
        int baseVertX = 160;
        int vertX = baseVertX + (int)(65 * barAnim);
        int spaceRight = this.width - vertX;
        int cw = Math.min(this.width - 220, 450);
        int cx = vertX + spaceRight / 2 - cw / 2;
        
        if (activeTab == 1) {
            cw = 320;
            cx = (this.width - cw) / 2;
        } else if (activeTab == 5) {
            cw = 280;
            cx = this.width / 2 + 55;
        }

        if (draggingSlider == 0 || draggingSlider == 1 || draggingSlider == 2) {
            float newPct = (float)(mx - (cx + 8)) / (cw - 16);
            newPct = Math.max(0, Math.min(1, newPct));
            float newVal = -1.0f + newPct * 4.0f;
            if (Math.abs(newVal) < 0.01f) newVal = (newVal < 0) ? -0.01f : 0.01f;
            if (draggingSlider == 0) ModConfig.INSTANCE.playerSizeX = newVal;
            if (draggingSlider == 1) ModConfig.INSTANCE.playerSizeY = newVal;
            if (draggingSlider == 2) ModConfig.INSTANCE.playerSizeZ = newVal;
            ModConfig.INSTANCE.playerSizeEnabled = true;
        } else if (draggingSlider == 7) {
            float newPct = (float)(mx - (cx + 8)) / (cw - 16);
            newPct = Math.max(0, Math.min(1, newPct));
            float newVal = -3.0f + newPct * 6.0f;
            if (Math.abs(newVal) < 0.15f) newVal = 0.0f;
            ModConfig.INSTANCE.playerSpinSpeedY = newVal;
            ModConfig.INSTANCE.enablePlayerSpin = (newVal != 0.0f);
        } else if (draggingSlider == 3 || draggingSlider == 4 || draggingSlider == 5) {
            int sliderW = cw;
            int startX = cx;
            
            if (activeTab == 1) {
                int winW = 310;
                int winX = this.width / 2 - winW / 2;
                startX = winX + 15;
                sliderW = (winW - 30) - 10;
            } else if (activeTab == 3 || activeTab == 6 || activeTab == 7) {
                sliderW = cw - 20;
                startX = cx + 10;
            }
            
            float newPct = (float)(mx - startX) / sliderW;
            newPct = Math.max(0, Math.min(1, newPct));
            
            if (draggingSlider == 3) selectedHue = newPct;
            if (draggingSlider == 4) selectedSat = newPct;
            if (draggingSlider == 5) selectedVal = newPct;
            updateColorFromSliders();
        } else if (draggingSlider == 6) {
            int startX = cx;
            int sliderW = cw;
            if (activeTab == 1) {
                int winW = 310;
                int winX = this.width / 2 - winW / 2;
                startX = winX + 15 + 8;
                sliderW = (winW - 30) - 16;
            }
            float newPct = (float)(mx - startX) / sliderW;
            newPct = Math.max(0, Math.min(1, newPct));
            ModConfig.INSTANCE.pseudoAnimationSpeed = 0.1f + newPct * 2.9f;
        } else if (draggingSlider == 10) {
            float newPct = (float)(mx - (cx + 10 + 8)) / (cw - 10 - 16);
            newPct = Math.max(0, Math.min(1, newPct));
            ModConfig.INSTANCE.automatonPauseTimeout = Math.max(1, Math.min(20, Math.round(1.0f + newPct * 19.0f)));
        } else if (draggingSlider == 14) {
            float newPct = (float)(mx - (cx + 10 + 8)) / (cw - 10 - 16);
            newPct = Math.max(0.0f, Math.min(1.0f, newPct));
            int ticks = Math.round(newPct * 24000.0f);
            com.wish.client.features.visual.TimeManager.setCustomTicks(ticks);
        } else if (draggingSlider >= 15 && draggingSlider <= 18) {
            applyVisualSliderValue(draggingSlider, mx, cx, cw);
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    private void applyVisualSliderValue(int sliderId, int mx, int cx, int cw) {
        float pct = (float) (mx - (cx + 10 + 8)) / (cw - 10 - 16);
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        if (sliderId == 15) {
            com.wish.client.features.visual.WeatherManager.setCount(0.1f + pct * 2.9f);
        } else if (sliderId == 16) {
            com.wish.client.features.visual.WeatherManager.setSize(0.25f + pct * 2.25f);
        } else if (sliderId == 18) {
            com.wish.client.features.visual.WeatherManager.setWindStrength(0.25f + pct * 1.75f);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        int mx = (int) (Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getScreenWidth());
        int my = (int) (Minecraft.getInstance().mouseHandler.ypos() * (double)this.height / (double)Minecraft.getInstance().getWindow().getScreenHeight());
        
        if (songSettingsOpen) {
            int winW = 220;
            int winH = 120;
            int winX = this.width / 2 - winW / 2;
            int winY = this.height / 2 - winH / 2;
            
            int swCloseX = winX + winW - 25;
            int swCloseY = winY + 10;
            if (mx >= swCloseX && mx < swCloseX + 15 && my >= swCloseY && my < swCloseY + 15) {
                songSettingsOpen = false;
                providerDropdownOpen = false;
                return true;
            }
            
            int cX = winX + 15;
            int cY = winY + 40;
            int cW = winW - 30;
            int bx = cX + cW - 90;
            int bw = 80;
            
            if (providerDropdownOpen) {
                if (mx >= bx && mx < bx + bw && my >= cY + ROW_H && my < cY + ROW_H + 72) {
                    String[] plats = {"None", "YTM", "Spotify", "Deezer"};
                    ModConfig.INSTANCE.musicProvider = plats[(int)((my - (cY + ROW_H)) / 18)];
                    ModConfig.INSTANCE.save();
                    providerDropdownOpen = false;
                    return true;
                } else {
                    providerDropdownOpen = false;
                    return true; 
                }
            } else if (mx >= bx && mx < bx + bw && my >= cY + 2 && my < cY + ROW_H - 2) {
                providerDropdownOpen = true;
                return true;
            } else if ("YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)) {
                int pairX = cX + 8;
                int pairW = bw;
                if (mx >= pairX && mx < pairX + pairW && my >= cY + ROW_H + 6 && my < cY + ROW_H + 6 + ROW_H - 4) {
                    com.wish.client.WishClient.requestPairing();
                    return true;
                }
            }
            
            
            if (mx >= winX && mx <= winX + winW && my >= winY && my <= winY + winH) {
                return true;
            } else {
                songSettingsOpen = false;
                providerDropdownOpen = false;
            }
        }

        int lineX = 0;
        
        int closeBtnSize = 20;
        int closeX = this.width - closeBtnSize - 20;
        int closeY = 20;
        if (mx >= closeX && mx <= closeX + closeBtnSize && my >= closeY && my <= closeY + closeBtnSize) {
            this.onClose();
            return true;
        }
        
        
        float currentBarAnim = getBarAnimProgress();
        if (currentBarAnim > 0.3f && activeSubMenuCategory > 0) {
            String[] menuCategories = {"Custom Player", "Feature", "Misc Setting"};
            int categorySpacing = 38;
            int sidebarStartY = (this.height - (menuCategories.length * categorySpacing)) / 2;
            int optX = 126;

            if (activeSubMenuCategory == 1) { 
                int catY = sidebarStartY;
                int opt1Y = catY - 11; 
                int opt2Y = catY + 11; 

                if (mx >= optX - 8 && mx < optX + 110 && my >= opt1Y - 6 && my < opt1Y + 16) {
                    activeTab = 1;
                    tabTransitionStartTime = System.currentTimeMillis();
                    setCategoryPopup(0); 
                    hexInputFocused = spoofInputFocused = false;
                    providerDropdownOpen = false;
                    cosmeticDropdownOpen = false;
                    activeVisualDropdown = 0;
                    pseudoSubWindow = 0;
                    updateHSBFromConfig();
                    return true;
                }

                if (mx >= optX - 8 && mx < optX + 110 && my >= opt2Y - 6 && my < opt2Y + 16) {
                    activeTab = 5;
                    tabTransitionStartTime = System.currentTimeMillis();
                    setCategoryPopup(0); 
                    hexInputFocused = spoofInputFocused = false;
                    providerDropdownOpen = false;
                    cosmeticDropdownOpen = false;
                    activeVisualDropdown = 0;
                    pseudoSubWindow = 0;
                    updateHSBFromConfig();
                    return true;
                }
            } else if (activeSubMenuCategory == 2) { 
                int catY = sidebarStartY + categorySpacing;
                String[] featLabels = {"Command", "Dungeon", "Slayer", "Mining", "Visual"};
                int[] featTabs = {0, 2, 3, 6, 7};
                int[] optYs = {catY - 36, catY - 18, catY, catY + 18, catY + 36};
                for (int k = 0; k < featLabels.length; k++) {
                    int fTab = featTabs[k];
                    int y = optYs[k];
                    if (mx >= optX - 8 && mx < optX + 110 && my >= y - 6 && my < y + 16) {
                        activeTab = fTab;
                        tabTransitionStartTime = System.currentTimeMillis();
                        setCategoryPopup(0); 
                        hexInputFocused = spoofInputFocused = false;
                        providerDropdownOpen = false;
                        cosmeticDropdownOpen = false;
                        activeVisualDropdown = 0;
                        pseudoSubWindow = 0;
                        updateHSBFromConfig();
                        return true;
                    }
                }
            }

            int curVertX = 160 + (int)(65 * currentBarAnim);
            if (mx >= 160 && mx < curVertX && my >= 80 && my < this.height - 80) {
                return true;
            }
        }

        
        String[] menuCategories = {"Custom Player", "Feature", "Misc Setting"};
        int categorySpacing = 38;
        int sidebarStartY = (this.height - (menuCategories.length * categorySpacing)) / 2;

        for (int i = 0; i < menuCategories.length; i++) {
            int catY = sidebarStartY + i * categorySpacing;
            if (mx >= lineX + 15 && mx < lineX + 145 && my >= catY - 6 && my < catY + 26) {
                if (i == 0) { 
                    setCategoryPopup(1);
                } else if (i == 1) { 
                    setCategoryPopup(2);
                } else if (i == 2) { 
                    setCategoryPopup(0);
                    if (activeTab != 4) {
                        activeTab = 4;
                        tabTransitionStartTime = System.currentTimeMillis();
                    }
                }
                hexInputFocused = spoofInputFocused = false;
                providerDropdownOpen = false;
                cosmeticDropdownOpen = false;
                activeVisualDropdown = 0;
                updateHSBFromConfig();
                return true;
            }
        }

        if (openCategoryPopup > 0) {
            
            setCategoryPopup(0);
            return true;
        }

        float barAnim = getBarAnimProgress();
        int baseVertX = 160;
        int vertX = baseVertX + (int)(65 * barAnim);
        int sliderBoxX = this.width / 2 + 55;
        int crossX = (activeTab == 5) ? ((vertX + sliderBoxX) / 2 - 20) : (lineX - 120);
        
        var player = Minecraft.getInstance().player;
        String baseName = player != null ? player.getName().getString() : "Player";
        if (ModConfig.INSTANCE.spoofedName != null && !ModConfig.INSTANCE.spoofedName.trim().isEmpty()) {
            baseName = ModConfig.INSTANCE.spoofedName.trim();
        }
        String customPseudo = baseName;
        
        int pseudoW = font.width("§l" + customPseudo);
        
        float skinScale = 1.0f; 
        int baseSize = (activeTab == 5) ? 75 : 45;
        int renderSize = (int)(baseSize * skinScale);
        int absRenderSize = Math.max(1, Math.abs(renderSize));
        
        int totalHeight = 15 + (int)(absRenderSize * 2.2f);
        int visualCenterY = (activeTab == 5) ? (this.height / 2 + 25) : (this.height / 2 + 45);
        int skinFeetY = visualCenterY + totalHeight / 2;
        int skinHeadY = skinFeetY - (int)(absRenderSize * 2.2f);
        int skinHalfW = Math.max(20, (int)(absRenderSize * 0.7f));
        
        int hitWRight = (int)(skinHalfW * 0.8f);
        int hitWLeft = (int)(skinHalfW * 1.2f);
        boolean skinHover = mx >= crossX - hitWLeft && mx <= crossX + hitWRight && my >= skinHeadY - 60 && my <= skinFeetY - 50;
        
        int pseudoY = skinHeadY - 75;
        boolean pseudoHover = mx >= crossX - pseudoW / 2 - 10 && mx <= crossX + pseudoW / 2 + 10 && my >= pseudoY - 5 && my <= pseudoY + 15;
        
        if (activeTab == 5) {
            pseudoHover = false;
            skinHover = false;
        }
        
        if (pseudoHover && activeTab != 1) {
            activeTab = 1;
            tabTransitionStartTime = System.currentTimeMillis();
            hexInputFocused = spoofInputFocused = false;
            providerDropdownOpen = false;
            updateHSBFromConfig();
            return true;
        }
        if (skinHover && activeTab != 5) {
            activeTab = 5;
            tabTransitionStartTime = System.currentTimeMillis();
            hexInputFocused = spoofInputFocused = false;
            providerDropdownOpen = false;
            updateHSBFromConfig();
            return true;
        }

        int mainWinH = Math.min(this.height - 80, 360);
        int mainWinY = (this.height - mainWinH) / 2;
        int py = mainWinY - 5;
        
        int spaceRight = this.width - vertX;
        int cw = Math.min(this.width - 220, 450);
        int cx = vertX + spaceRight / 2 - cw / 2;
        
        int cy = py + 40;
        if (activeTab == 5) {
            cw = 280;
            cx = this.width / 2 + 55;
            cy = this.height / 2 - 80;
        }
        if (activeTab == -1) return super.mouseClicked(event, focused);
        int WIN_H = mainWinH;
        
        if (activeTab == 0) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            
            if (my < clipTop || my > clipBottom || mx < cx || mx > cx + cw) {
                return false;
            }

            cy -= commandsScrollY;
            int hH = 22;

            
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                chatChannelsOpen = !chatChannelsOpen;
                return true;
            }
            cy += hH + 4;

            if (chatChannelsOpen) {
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) { ModConfig.INSTANCE.enableAc = !ModConfig.INSTANCE.enableAc; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) { ModConfig.INSTANCE.enableGc = !ModConfig.INSTANCE.enableGc; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) { ModConfig.INSTANCE.enablePc = !ModConfig.INSTANCE.enablePc; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
            }

            
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                funCommandsOpen = !funCommandsOpen;
                return true;
            }
            cy += hH + 4;

            if (funCommandsOpen) {
                int btnW = (cw - 6) / 2;
                int btnH = 16;
                for (int i = 0; i < 14; i++) {
                    int col = i % 2;
                    int row = i / 2;
                    int tx = cx + col * (btnW + 6);
                    int ty = cy + row * (btnH + 3);
                    if (mx >= tx && mx < tx + btnW && my >= ty && my < ty + btnH) {
                        switch (i) {
                            case 0 -> ModConfig.INSTANCE.enableMeow = !ModConfig.INSTANCE.enableMeow;
                            case 1 -> ModConfig.INSTANCE.enableWanted = !ModConfig.INSTANCE.enableWanted;
                            case 2 -> ModConfig.INSTANCE.enableKiss = !ModConfig.INSTANCE.enableKiss;
                            case 3 -> ModConfig.INSTANCE.enableFeed = !ModConfig.INSTANCE.enableFeed;
                            case 4 -> ModConfig.INSTANCE.enablePoke = !ModConfig.INSTANCE.enablePoke;
                            case 5 -> ModConfig.INSTANCE.enablePat = !ModConfig.INSTANCE.enablePat;
                            case 6 -> ModConfig.INSTANCE.enableHug = !ModConfig.INSTANCE.enableHug;
                            case 7 -> ModConfig.INSTANCE.enableSus = !ModConfig.INSTANCE.enableSus;
                            case 8 -> ModConfig.INSTANCE.enableRizz = !ModConfig.INSTANCE.enableRizz;
                            case 9 -> ModConfig.INSTANCE.enableJerry = !ModConfig.INSTANCE.enableJerry;
                            case 10 -> ModConfig.INSTANCE.enableIq = !ModConfig.INSTANCE.enableIq;
                            case 11 -> ModConfig.INSTANCE.enableSleep = !ModConfig.INSTANCE.enableSleep;
                            case 12 -> ModConfig.INSTANCE.enableYuri = !ModConfig.INSTANCE.enableYuri;
                            case 13 -> ModConfig.INSTANCE.enableSoraka = !ModConfig.INSTANCE.enableSoraka;
                        }
                        ModConfig.INSTANCE.save();
                        return true;
                    }
                }
                cy += ((14 + 1) / 2) * (btnH + 3) + 4;
            }

            
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                utilityCommandsOpen = !utilityCommandsOpen;
                return true;
            }
            cy += hH + 4;

            if (utilityCommandsOpen) {
                int btnW = (cw - 6) / 2;
                int btnH = 16;
                int tx = cx;
                int ty = cy;
                int dotX = tx + btnW - 10;
                int iBtnX = dotX - 18;
                
                if (mx >= iBtnX && mx < iBtnX + 12 && my >= ty && my < ty + btnH) {
                    songSettingsOpen = !songSettingsOpen;
                    return true;
                } else if (mx >= tx && mx < tx + btnW && my >= ty && my < ty + btnH) {
                    ModConfig.INSTANCE.enableSong = !ModConfig.INSTANCE.enableSong;
                    ModConfig.INSTANCE.save();
                    return true;
                }
                cy += (btnH + 3) + 4;
            }
            return false;
        } else if (activeTab == 1) {
            cw = 320;
            cx = (this.width - cw) / 2;
            cy = this.height / 2 - 80;
            
            cy += 36; 
            
            int btnH = 24;
            int totalBtnW = 3 * 70 + 2 * 10;
            int dockX = cx + cw/2 - totalBtnW/2;
            int dockY = cy;
            for (int i = 0; i < 3; i++) {
                int bx = dockX + i * 80;
                if (mx >= bx && mx < bx + 70 && my >= dockY && my < dockY + btnH) {
                    if (pseudoSubWindow == i + 1) {
                        pseudoSubWindow = 0;
                    } else {
                        pseudoSubWindow = i + 1;
                        if (pseudoSubWindow == 3) {
                            updateHSBFromConfig(); 
                        }
                    }
                    return true;
                }
            }
            
            if (pseudoSubWindow > 0) {
                int winW = 310;
                int winH = 200;
                if (pseudoSubWindow == 1) winH = 120; 
                if (pseudoSubWindow == 2) {
                    winH = 126; 
                    if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) winH += 40;
                }
                if (pseudoSubWindow == 3) winH = 195; 
                
                int winX = this.width / 2 - winW / 2;
                int winY = dockY + btnH + 10;
                if (winY + winH > this.height - 10) {
                    winY = this.height - winH - 10;
                }
                
                int availH = this.height - winY - 10;
                boolean needsScroll = winH > availH && availH > 80;
                
                int swCloseX = winX + winW - 22;
                int swCloseY = winY + 9;
                if (mx >= swCloseX && mx < swCloseX + 14 && my >= swCloseY && my < swCloseY + 14) {
                    pseudoSubWindow = 0;
                    return true;
                }
                
                int cX = winX + 15;
                int cY = winY + 32;
                if (needsScroll) {
                    cY -= pseudoSubScrollY;
                }
                int cW = winW - 30;
                
                if (pseudoSubWindow == 1) { 
                    int[] bitmasks = {1, 2, 4, 8};
                    int colW2 = (cW - 10) / 2;
                    for (int i = 0; i < 4; i++) {
                        int bx = cX + (i % 2) * (colW2 + 10);
                        int by = cY + (i / 2) * 22;
                        if (mx >= bx && mx < bx + colW2 && my >= by && my < by + 18) {
                            if ((ModConfig.INSTANCE.pseudoFont & bitmasks[i]) != 0) {
                                ModConfig.INSTANCE.pseudoFont &= ~bitmasks[i]; 
                            } else {
                                ModConfig.INSTANCE.pseudoFont |= bitmasks[i]; 
                            }
                            ModConfig.INSTANCE.save();
                            return true;
                        }
                    }
                    cY += 2 * 22 + 6;
                    if (mx >= cX && mx < cX + cW && my >= cY && my < cY + 22) {
                        spoofInputFocused = true;
                        hexInputFocused = false;
                        return true;
                    }
                } else if (pseudoSubWindow == 2) { 
                    int colW = (cW - 10) / 2;
                    for (int i=0; i<6; i++) {
                        int bx = cX + (i % 2) * (colW + 10);
                        int by = cY + (i / 2) * 24;
                        if (mx >= bx && mx < bx + colW && my >= by && my < by + 20) {
                            ModConfig.INSTANCE.pseudoAnimation = i;
                            ModConfig.INSTANCE.save();
                            return true;
                        }
                    }
                    cY += 3 * 24;
                    if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) {
                        cY += 6;
                        if (mx >= cX && mx < cX + cW && my >= cY && my < cY + 26) {
                            draggingSlider = 6;
                            return true;
                        }
                    }
                } else if (pseudoSubWindow == 3) { 
                    if (mx >= cX && mx < cX + cW/2 - 5 && my >= cY && my < cY + 26) {
                        editingColor2 = false;
                        updateHSBFromConfig();
                        return true;
                    }
                    int c2X = cX + cW/2 + 5;
                    if (mx >= c2X && mx < c2X + cW/2 - 5 && my >= cY && my < cY + 26) {
                        editingColor2 = true;
                        if (ModConfig.INSTANCE.pseudoAnimation < 2) {
                            ModConfig.INSTANCE.pseudoAnimation = 2;
                            ModConfig.INSTANCE.save();
                        }
                        updateHSBFromConfig();
                        return true;
                    }
                    
                    cY += 35;
                    if (mx >= cX && mx < cX + cW && my >= cY && my < cY + 22) { hexInputFocused = true; spoofInputFocused = false; return true; }
                    cY += 26;
                    
                    int hueW = cW - 10;
                    int hueH = 10;
                    
                    cY += 12; 
                    if (mx >= cX && mx < cX + hueW && my >= cY && my < cY + hueH) {
                        draggingSlider = 3;
                        selectedHue = Math.max(0f, Math.min(1f, (float)(mx - cX) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cY += hueH + 8;
                    
                    cY += 12; 
                    if (mx >= cX && mx < cX + hueW && my >= cY && my < cY + hueH) {
                        draggingSlider = 4;
                        selectedSat = Math.max(0f, Math.min(1f, (float)(mx - cX) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cY += hueH + 8;
                    
                    cY += 12; 
                    if (mx >= cX && mx < cX + hueW && my >= cY && my < cY + hueH) {
                        draggingSlider = 5;
                        selectedVal = Math.max(0f, Math.min(1f, (float)(mx - cX) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                }
                
                return true; 
            }
        } else if (activeTab == 5) {
            
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 26) { draggingSlider = 0; return true; } cy += 26 + 4;
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 26) { draggingSlider = 1; return true; } cy += 26 + 4;
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 26) { draggingSlider = 2; return true; } cy += 26 + 4;
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 26) { draggingSlider = 7; return true; } cy += 26 + 4;
            

            if (mx >= cx && mx < cx + 100 && my >= cy && my < cy + 22) {
                ModConfig.INSTANCE.playerSizeX = 1.0f;
                ModConfig.INSTANCE.playerSizeY = 1.0f;
                ModConfig.INSTANCE.playerSizeZ = 1.0f;
                ModConfig.INSTANCE.playerSizeEnabled = false;
                ModConfig.INSTANCE.playerSpinSpeedY = 0.0f;
                ModConfig.INSTANCE.enablePlayerSpin = false;
                ModConfig.INSTANCE.save();
                return true;
            } cy += 26;

        } else if (activeTab == 2) {
            int hH = 22;

            if (isHeaderDotClicked(mx, my, cx, cy, cw)) {
                GhostBlockManager.isGhostBlocksEnabled = !GhostBlockManager.isGhostBlocksEnabled; 
                if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                dungeonCustomBlocksOpen = !dungeonCustomBlocksOpen;
                return true;
            }
            cy += hH + 4;
            
            if (dungeonCustomBlocksOpen) {
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) { 
                    GhostBlockManager.isGlassGhostBlocksEnabled = !GhostBlockManager.isGlassGhostBlocksEnabled; 
                    if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
                    return true; 
                } cy += ROW_H + 4;
            }
            
            if (isHeaderDotClicked(mx, my, cx, cy, cw)) {
                ModConfig.INSTANCE.enableLagTimeLost = !ModConfig.INSTANCE.enableLagTimeLost; 
                ModConfig.INSTANCE.save(); 
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                lagTimeLostOpen = !lagTimeLostOpen;
                return true;
            }
            cy += hH + 4;
            
            if (lagTimeLostOpen) {
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) { 
                    ModConfig.INSTANCE.sendLagTimeLost = !ModConfig.INSTANCE.sendLagTimeLost; 
                    ModConfig.INSTANCE.save();
                    return true; 
                } cy += ROW_H + 4;
            }

        } else if (activeTab == 3) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            if (my < clipTop || my > clipBottom || mx < cx || mx > cx + cw) {
                return false;
            }
            cy -= slayerScrollY;

            int hH = 22;
            if (isHeaderDotClicked(mx, my, cx, cy, cw)) {
                ModConfig.INSTANCE.enableSlayerCarry = !ModConfig.INSTANCE.enableSlayerCarry;
                ModConfig.INSTANCE.save();
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                slayerCarryOpen = !slayerCarryOpen;
                hexInputFocused = false;
                return true;
            }
            cy += hH + 4;
            
            if (slayerCarryOpen) {
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) { 
                    ModConfig.INSTANCE.enableBossSpawnHud = !ModConfig.INSTANCE.enableBossSpawnHud; 
                    ModConfig.INSTANCE.save(); 
                    return true; 
                }
                cy += ROW_H + 4;
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) { 
                    ModConfig.INSTANCE.enableSlayerAutoCoords = !ModConfig.INSTANCE.enableSlayerAutoCoords; 
                    ModConfig.INSTANCE.save(); 
                    return true; 
                }
                cy += ROW_H + 4;
                
                cy += 44; // Commands box

                if (isHeaderDotClicked(mx, my, cx + 10, cy, cw - 10)) {
                    ModConfig.INSTANCE.enableSlayerGlow = !ModConfig.INSTANCE.enableSlayerGlow;
                    ModConfig.INSTANCE.save();
                    return true;
                } else if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + hH) {
                    slayerGlowOpen = !slayerGlowOpen;
                    hexInputFocused = false;
                    return true;
                }
                cy += hH + 4;

                if (slayerGlowOpen) {
                    cy += 22 + 4; // Preview box
                    
                    if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 22) {
                        hexInputFocused = true;
                        spoofInputFocused = false;
                        return true;
                    }
                    cy += 22 + 4;
                    
                    int hueW = cw - 20;
                    cy += 10;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) {
                        draggingSlider = 3;
                        selectedHue = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 14;
                    
                    cy += 10;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) {
                        draggingSlider = 4;
                        selectedSat = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 14;
                    
                    cy += 10;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) {
                        draggingSlider = 5;
                        selectedVal = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 16;
                }
            }

        } else if (activeTab == 6) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            if (my < clipTop || my > clipBottom || mx < cx || mx > cx + cw) {
                return false;
            }
            cy -= miningScrollY;

            int hH = 22;
            if (isHeaderDotClicked(mx, my, cx, cy, cw)) {
                ModConfig.INSTANCE.enableMiningAutomaton = !ModConfig.INSTANCE.enableMiningAutomaton;
                ModConfig.INSTANCE.save();
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                miningAutomatonOpen = !miningAutomatonOpen;
                hexInputFocused = false;
                return true;
            }
            cy += hH + 4;

            if (miningAutomatonOpen) {
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) {
                    ModConfig.INSTANCE.enableAutomatonTracker = !ModConfig.INSTANCE.enableAutomatonTracker;
                    ModConfig.INSTANCE.save();
                    com.wish.client.features.AutomatonManager.refreshHudState();
                    return true;
                }
                cy += ROW_H + 4;

                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) {
                    ModConfig.INSTANCE.enablePrecursorCityScanner = !ModConfig.INSTANCE.enablePrecursorCityScanner;
                    ModConfig.INSTANCE.save();
                    if (!ModConfig.INSTANCE.enablePrecursorCityScanner) {
                        com.wish.client.features.PrecursorCityLocator.reset();
                    }
                    return true;
                }
                cy += ROW_H + 4;

                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) {
                    ModConfig.INSTANCE.automatonSackMode = !ModConfig.INSTANCE.automatonSackMode;
                    ModConfig.INSTANCE.save();
                    return true;
                }
                cy += ROW_H + 4;

                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 26) {
                    draggingSlider = 10;
                    float newPct = (float)(mx - (cx + 10 + 8)) / (cw - 10 - 16);
                    newPct = Math.max(0, Math.min(1, newPct));
                    ModConfig.INSTANCE.automatonPauseTimeout = Math.max(1, Math.min(20, Math.round(1.0f + newPct * 19.0f)));
                    ModConfig.INSTANCE.save();
                    return true;
                }
                cy += 26 + 4;

                
                if (isHeaderDotClicked(mx, my, cx + 10, cy, cw - 10)) {
                    ModConfig.INSTANCE.enableAutomatonGlow = !ModConfig.INSTANCE.enableAutomatonGlow;
                    ModConfig.INSTANCE.save();
                    return true;
                } else if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + hH) {
                    automatonGlowOpen = !automatonGlowOpen;
                    hexInputFocused = false;
                    return true;
                }
                cy += hH + 4;

                if (automatonGlowOpen) {
                    if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10)) {
                        ModConfig.INSTANCE.enableAutomatonGlow = !ModConfig.INSTANCE.enableAutomatonGlow;
                        ModConfig.INSTANCE.save();
                        return true;
                    }
                    cy += ROW_H + 4;

                    cy += 22 + 4; 
                    if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 22) {
                        hexInputFocused = true;
                        spoofInputFocused = false;
                        return true;
                    }
                    cy += 22 + 4;

                    cy += 10;
                    int hueW = cw - 20;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) {
                        draggingSlider = 3;
                        selectedHue = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 14;

                    cy += 10;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) {
                        draggingSlider = 4;
                        selectedSat = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 14;

                    cy += 10;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) {
                        draggingSlider = 5;
                        selectedVal = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 16;
                }
            }
        } else if (activeTab == 7) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int hH = 22;

            if (activeVisualDropdown != 0 && activeDropOptions.length > 0) {
                int itemH = 18;
                int dropH = activeDropOptions.length * itemH + 4;
                if (mx >= activeDropX && mx < activeDropX + activeDropW && my >= activeDropY && my < activeDropY + dropH) {
                    int clickedIdx = (my - (activeDropY + 2)) / itemH;
                    if (clickedIdx >= 0 && clickedIdx < activeDropOptions.length) {
                        applyVisualDropdownSelection(activeVisualDropdown, clickedIdx);
                    }
                    activeVisualDropdown = 0;
                    return true;
                }
            }

            if (my < clipTop || my > clipBottom || mx < cx || mx > cx + cw) {
                activeVisualDropdown = 0;
                return false;
            }

            int prevDrop = activeVisualDropdown;
            activeVisualDropdown = 0;

            cy -= visualScrollY;

            if (isHeaderDotClicked(mx, my, cx, cy, cw) && my >= clipTop && my <= clipBottom) {
                ModConfig.INSTANCE.enableLyrics = !ModConfig.INSTANCE.enableLyrics;
                ModConfig.INSTANCE.save();
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH && my >= clipTop && my <= clipBottom) {
                lyricsSectionOpen = !lyricsSectionOpen;
                hexInputFocused = false;
                return true;
            }
            cy += hH + 4;

            if (lyricsSectionOpen) {
                cy += 46 + 6; // Now playing mini card offset

                int pBtnW = VISUAL_DROP_W;
                int pBtnX = cx + cw - pBtnW - 10;

                if ("YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)) {
                    int pairBtnW = 60;
                    int pairBtnX = pBtnX - pairBtnW - 6;
                    if (mx >= pairBtnX && mx < pairBtnX + pairBtnW && my >= cy + 2 && my < cy + ROW_H - 2 && my >= clipTop && my <= clipBottom) {
                        com.wish.client.WishClient.requestPairing();
                        return true;
                    }
                }

                if (mx >= pBtnX && mx < pBtnX + pBtnW && my >= cy + 2 && my < cy + ROW_H - 2 && my >= clipTop && my <= clipBottom) {
                    activeVisualDropdown = (prevDrop == 1) ? 0 : 1;
                    return true;
                }
                cy += ROW_H + 4;

                int mBtnW = VISUAL_DROP_W;
                int mBtnX = cx + cw - mBtnW - 10;
                if (mx >= mBtnX && mx < mBtnX + mBtnW && my >= cy + 2 && my < cy + ROW_H - 2 && my >= clipTop && my <= clipBottom) {
                    activeVisualDropdown = (prevDrop == 2) ? 0 : 2;
                    return true;
                }
                cy += ROW_H + 4;

                // Color collapsible header
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + hH && my >= clipTop && my <= clipBottom) {
                    lyricsColorOpen = !lyricsColorOpen;
                    hexInputFocused = false;
                    return true;
                }
                cy += hH + 4;

                if (lyricsColorOpen) {
                    cy += 22 + 4; // Lyrics Preview box

                    // Lyrics Hex input
                    if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 22 && my >= clipTop && my <= clipBottom) {
                        hexInputFocused = true;
                        spoofInputFocused = false;
                        return true;
                    }
                    cy += 22 + 4;

                    cy += 10;
                    int hueW = cw - 20;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10 && my >= clipTop && my <= clipBottom) {
                        draggingSlider = 3;
                        selectedHue = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 14;

                    cy += 10;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10 && my >= clipTop && my <= clipBottom) {
                        draggingSlider = 4;
                        selectedSat = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 14;

                    cy += 10;
                    if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10 && my >= clipTop && my <= clipBottom) {
                        draggingSlider = 5;
                        selectedVal = Math.max(0f, Math.min(1f, (float)(mx - (cx + 10)) / hueW));
                        updateColorFromSliders();
                        return true;
                    }
                    cy += 16;
                }
                cy += 24 + 6;
            }

            // Time Changer Section Click Handling
            if (isHeaderDotClicked(mx, my, cx, cy, cw) && my >= clipTop && my <= clipBottom) {
                ModConfig.INSTANCE.enableCustomTime = !ModConfig.INSTANCE.enableCustomTime;
                com.wish.client.features.visual.TimeManager.invalidateCache();
                ModConfig.INSTANCE.save();
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH && my >= clipTop && my <= clipBottom) {
                timeChangerSectionOpen = !timeChangerSectionOpen;
                return true;
            }
            cy += hH + 4;

            if (timeChangerSectionOpen) {
                // Custom Time slider (14)
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 26 && my >= clipTop && my <= clipBottom) {
                    draggingSlider = 14;
                    float newPct = (float)(mx - (cx + 10 + 8)) / (cw - 10 - 16);
                    newPct = Math.max(0, Math.min(1, newPct));
                    int ticks = Math.round(newPct * 24000.0f);
                    com.wish.client.features.visual.TimeManager.setCustomTicks(ticks);
                    return true;
                }
                cy += 26 + 4;

                // Freeze Time toggle
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10) && my >= clipTop && my <= clipBottom) {
                    com.wish.client.features.visual.TimeManager.setFrozen(!ModConfig.INSTANCE.customTimeFrozen);
                    return true;
                }
                cy += ROW_H + 4;
            }

            // Weather Changer Section Click Handling
            if (isHeaderDotClicked(mx, my, cx, cy, cw) && my >= clipTop && my <= clipBottom) {
                com.wish.client.features.visual.WeatherManager.setEnabled(!ModConfig.INSTANCE.enableCustomWeather);
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH && my >= clipTop && my <= clipBottom) {
                weatherChangerSectionOpen = !weatherChangerSectionOpen;
                return true;
            }
            cy += hH + 4;

            if (weatherChangerSectionOpen) {
                int wmBtnW = VISUAL_DROP_W;
                int wmBtnX = cx + cw - wmBtnW - 10;
                if (mx >= wmBtnX && mx < wmBtnX + wmBtnW && my >= cy + 2 && my < cy + ROW_H - 2 && my >= clipTop && my <= clipBottom) {
                    activeVisualDropdown = (prevDrop == 3) ? 0 : 3;
                    return true;
                }
                cy += ROW_H + 4;

                for (int sliderId = 15; sliderId <= 16; sliderId++) {
                    if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 26 && my >= clipTop && my <= clipBottom) {
                        draggingSlider = sliderId;
                        applyVisualSliderValue(sliderId, mx, cx, cw);
                        return true;
                    }
                    cy += 26 + 4;
                }

                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10) && my >= clipTop && my <= clipBottom) {
                    com.wish.client.features.visual.WeatherManager.setWindEnabled(!com.wish.client.features.visual.WeatherManager.isWindEnabled());
                    return true;
                }
                cy += ROW_H + 4;

                if (com.wish.client.features.visual.WeatherManager.isWindEnabled()) {
                    if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 26 && my >= clipTop && my <= clipBottom) {
                        draggingSlider = 18;
                        applyVisualSliderValue(18, mx, cx, cw);
                        return true;
                    }
                    cy += 26 + 4;
                }
            }

            // Custom Sky Section Click Handling
            if (isHeaderDotClicked(mx, my, cx, cy, cw) && my >= clipTop && my <= clipBottom) {
                ModConfig.INSTANCE.enableCustomSky = !ModConfig.INSTANCE.enableCustomSky;
                ModConfig.INSTANCE.save();
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH && my >= clipTop && my <= clipBottom) {
                customSkySectionOpen = !customSkySectionOpen;
                return true;
            }
            cy += hH + 4;

            if (customSkySectionOpen) {
                int smBtnW = VISUAL_DROP_W;
                int smBtnX = cx + cw - smBtnW - 10;
                if (mx >= smBtnX && mx < smBtnX + smBtnW && my >= cy + 2 && my < cy + ROW_H - 2 && my >= clipTop && my <= clipBottom) {
                    activeVisualDropdown = (prevDrop == 4) ? 0 : 4;
                    return true;
                }
                cy += ROW_H + 4;

                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10) && my >= clipTop && my <= clipBottom) {
                    ModConfig.INSTANCE.customSkyShowStars = !ModConfig.INSTANCE.customSkyShowStars;
                    ModConfig.INSTANCE.save();
                    return true;
                }
                cy += ROW_H + 4;

                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10) && my >= clipTop && my <= clipBottom) {
                    ModConfig.INSTANCE.customSkyShowSunMoon = !ModConfig.INSTANCE.customSkyShowSunMoon;
                    ModConfig.INSTANCE.save();
                    return true;
                }
                cy += ROW_H + 4;

                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10) && my >= clipTop && my <= clipBottom) {
                    ModConfig.INSTANCE.customSkyRotate = !ModConfig.INSTANCE.customSkyRotate;
                    ModConfig.INSTANCE.save();
                    return true;
                }
                cy += ROW_H + 4;
            }

            // Smol People (Skytils) Section Click Handling
            if (isHeaderDotClicked(mx, my, cx, cy, cw) && my >= clipTop && my <= clipBottom) {
                ModConfig.INSTANCE.enableOtherPlayersSize = !ModConfig.INSTANCE.enableOtherPlayersSize;
                ModConfig.INSTANCE.save();
                return true;
            } else if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH && my >= clipTop && my <= clipBottom) {
                otherPlayersSizeSectionOpen = !otherPlayersSizeSectionOpen;
                return true;
            }
            cy += hH + 4;

            if (otherPlayersSizeSectionOpen) {
                if (isToggleBtnClicked(mx, my, cx + 10, cy, cw - 10) && my >= clipTop && my <= clipBottom) {
                    ModConfig.INSTANCE.smolPeopleIncludeSelf = !ModConfig.INSTANCE.smolPeopleIncludeSelf;
                    ModConfig.INSTANCE.save();
                    return true;
                }
                cy += ROW_H + 4;
            }
        } else if (activeTab == 4) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;

            int cosW = 94;
            int cosBx = cx + cw - cosW - 8;
            int cosRowY = cy - miscScrollY + 2 * (ROW_H + 4);
            int cosBy = cosRowY + 2;
            int cosBh = ROW_H - 4;
            int cosDropX = cosBx;
            int cosDropY = cosBy + cosBh + 2;
            int cosDropH = 3 * 18 + 4;

            if (cosmeticDropdownOpen) {
                if (mx >= cosDropX && mx < cosDropX + cosW && my >= cosDropY && my < cosDropY + cosDropH) {
                    int clickedIdx = (my - (cosDropY + 2)) / 18;
                    if (clickedIdx >= 0 && clickedIdx < 3) {
                        ModConfig.INSTANCE.cosmeticVisibility = clickedIdx;
                        ModConfig.INSTANCE.save();
                    }
                    cosmeticDropdownOpen = false;
                    return true;
                } else if (mx >= cosBx && mx < cosBx + cosW && my >= cosBy && my < cosBy + cosBh) {
                    cosmeticDropdownOpen = false;
                    return true;
                } else {
                    cosmeticDropdownOpen = false;
                }
            }

            if (my < clipTop || my > clipBottom || mx < cx || mx > cx + cw) {
                return false;
            }

            cy -= miscScrollY;

            if (isToggleBtnClicked(mx, my, cx, cy, cw)) { ModConfig.INSTANCE.enableUpdateCheck = !ModConfig.INSTANCE.enableUpdateCheck; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
            if (isToggleBtnClicked(mx, my, cx, cy, cw)) { 
                ModConfig.INSTANCE.alwaysInM7F7 = !ModConfig.INSTANCE.alwaysInM7F7; 
                ModConfig.INSTANCE.save(); 
                if (Minecraft.getInstance().levelExtractor != null) {
                    Minecraft.getInstance().levelExtractor.allChanged();
                }
                return true; 
            }
            cy += ROW_H + 4;
            
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ROW_H) {
                cosmeticDropdownOpen = !cosmeticDropdownOpen;
                return true;
            }
            cy += ROW_H + 4;

            cy += 14;
            int copyBtnW = Math.min(cw - 24, 210);
            int copyBtnH = 22;
            int copyBtnX = cx + (cw - copyBtnW) / 2;
            if (mx >= copyBtnX && mx < copyBtnX + copyBtnW && my >= cy && my < cy + copyBtnH) {
                copyConfigToClipboard();
                return true;
            }
            cy += copyBtnH + 10;
        }
        
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSlider != -1) {
            draggingSlider = -1;
            ModConfig.INSTANCE.save();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char chr = (char) event.codepoint();
        if (spoofInputFocused && chr >= 32 && chr <= 126) {
            if (ModConfig.INSTANCE.spoofedName == null) ModConfig.INSTANCE.spoofedName = "";
            if (ModConfig.INSTANCE.spoofedName.length() < 32) ModConfig.INSTANCE.spoofedName += chr; 
            ModConfig.INSTANCE.save(); return true;
        } else if (hexInputFocused) {
            String cur;
            if (activeTab == 3) cur = ModConfig.INSTANCE.slayerGlowColor;
            else if (activeTab == 6) cur = ModConfig.INSTANCE.automatonGlowColor;
            else if (activeTab == 7) cur = ModConfig.INSTANCE.lyricsColor;
            else cur = (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2 ? ModConfig.INSTANCE.customHexColor2 : ModConfig.INSTANCE.customHexColor);
            
            if (cur == null || !cur.startsWith("#")) cur = "#";
            char c = Character.toUpperCase(chr);
            if (c == '#' && !cur.startsWith("#")) cur = "#" + cur;
            else if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                if (cur.length() < 7) cur = cur + c;
            }
            if (activeTab == 3) ModConfig.INSTANCE.slayerGlowColor = cur;
            else if (activeTab == 6) ModConfig.INSTANCE.automatonGlowColor = cur;
            else if (activeTab == 7) ModConfig.INSTANCE.lyricsColor = cur;
            else if (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2) ModConfig.INSTANCE.customHexColor2 = cur;
            else ModConfig.INSTANCE.customHexColor = cur;
            updateHSBFromConfig();
            ModConfig.INSTANCE.save();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (event.isPaste()) { 
            String clipboard = this.minecraft.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                clipboard = clipboard.replaceAll("[\\n\\r]", ""); 
                if (spoofInputFocused) {
                    if (ModConfig.INSTANCE.spoofedName == null) ModConfig.INSTANCE.spoofedName = "";
                    int rem = 32 - ModConfig.INSTANCE.spoofedName.length();
                    if (rem > 0) ModConfig.INSTANCE.spoofedName += clipboard.substring(0, Math.min(rem, clipboard.length()));
                } else if (hexInputFocused) {
                    String clean = clipboard.trim().toUpperCase();
                    if (!clean.startsWith("#")) clean = "#" + clean;
                    clean = clean.replaceAll("[^#0-9A-F]", "");
                    if (clean.length() > 7) clean = clean.substring(0, 7);
                    if (activeTab == 3) ModConfig.INSTANCE.slayerGlowColor = clean;
                    else if (activeTab == 6) ModConfig.INSTANCE.automatonGlowColor = clean;
                    else if (activeTab == 7) ModConfig.INSTANCE.lyricsColor = clean;
                    else if (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2) ModConfig.INSTANCE.customHexColor2 = clean;
                    else ModConfig.INSTANCE.customHexColor = clean;
                    updateHSBFromConfig();
                }
                ModConfig.INSTANCE.save();
                return true;
            }
        }
        if (key == 259) { 
            if (spoofInputFocused && ModConfig.INSTANCE.spoofedName != null && ModConfig.INSTANCE.spoofedName.length() > 0) {
                ModConfig.INSTANCE.spoofedName = ModConfig.INSTANCE.spoofedName.substring(0, ModConfig.INSTANCE.spoofedName.length() - 1);
                ModConfig.INSTANCE.save(); return true;
            } else if (hexInputFocused) {
                String cur;
                if (activeTab == 3) cur = ModConfig.INSTANCE.slayerGlowColor;
                else if (activeTab == 6) cur = ModConfig.INSTANCE.automatonGlowColor;
                else if (activeTab == 7) cur = ModConfig.INSTANCE.lyricsColor;
                else cur = (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2 ? ModConfig.INSTANCE.customHexColor2 : ModConfig.INSTANCE.customHexColor);

                if (cur != null && cur.length() > 1) {
                    cur = cur.substring(0, cur.length() - 1);
                    if (activeTab == 3) ModConfig.INSTANCE.slayerGlowColor = cur;
                    else if (activeTab == 6) ModConfig.INSTANCE.automatonGlowColor = cur;
                    else if (activeTab == 7) ModConfig.INSTANCE.lyricsColor = cur;
                    else if (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2) ModConfig.INSTANCE.customHexColor2 = cur;
                    else ModConfig.INSTANCE.customHexColor = cur;
                    updateHSBFromConfig();
                    ModConfig.INSTANCE.save();
                }
                return true;
            }
        }
        if (key == 256) { 
            if (openCategoryPopup > 0) {
                setCategoryPopup(0);
                return true;
            }
            if (spoofInputFocused || hexInputFocused || providerDropdownOpen || cosmeticDropdownOpen || activeVisualDropdown != 0) {
                spoofInputFocused = hexInputFocused = false;
                providerDropdownOpen = false;
                cosmeticDropdownOpen = false;
                activeVisualDropdown = 0;
                return true;
            }
            super.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int mainWinH = Math.min(this.height - 80, 360);
            int mainWinY = (this.height - mainWinH) / 2;
            int py = mainWinY - 5;
            
            float barAnim = getBarAnimProgress();
            int baseVertX = 160;
            int vertX = baseVertX + (int)(65 * barAnim);
            int spaceRight = this.width - vertX;
            int cw = Math.min(this.width - 220, 450);
            int cx = vertX + spaceRight / 2 - cw / 2;
            
            int clipTop = py + 40;
            int clipBottom = mainWinY + mainWinH - 10;
            int clipHeight = clipBottom - clipTop;

            double mx = Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getScreenWidth();
            double my = Minecraft.getInstance().mouseHandler.ypos() * (double)this.height / (double)Minecraft.getInstance().getWindow().getScreenHeight();

            if (activeTab == 1) {
                if (pseudoSubWindow > 0) {
                    int winW = 310;
                    int winH = 200;
                    if (pseudoSubWindow == 1) winH = 142;
                    else if (pseudoSubWindow == 2) {
                        winH = 126;
                        if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) winH += 40;
                    } else if (pseudoSubWindow == 3) winH = 240;

                    int winX = this.width / 2 - winW / 2;
                    int cyTab1 = this.height / 2 - 80;
                    int dockY = cyTab1 + 36;
                    int btnH = 24;
                    int winY = dockY + btnH + 10;
                    if (winY + winH > this.height - 10) {
                        winY = this.height - winH - 10;
                    }

                    int availH = this.height - winY - 10;
                    int renderWinH = winH > availH && availH > 80 ? availH : winH;
                    int contentClipHeight = renderWinH - 34;
                    int subMaxScroll = Math.max(0, winH - 32 - contentClipHeight);

                    if (mx >= winX && mx <= winX + winW && my >= winY && my <= winY + renderWinH) {
                        if (subMaxScroll > 0) {
                            pseudoSubScrollY = Math.max(0, Math.min(subMaxScroll, pseudoSubScrollY - (int)(scrollY * 18)));
                            return true;
                        }
                    }
                }
            } else if (activeTab == 5) {
                cw = 280;
                cx = this.width / 2 + 55;
            }

            if (mx >= cx && mx <= cx + cw + 10 && my >= clipTop && my <= clipBottom) {
                if (activeTab == 0) {
                    int totalContentH = 0;
                    int hH = 22;
                    totalContentH += hH + 4;
                    if (chatChannelsOpen) totalContentH += 3 * (16 + 3) + 4;
                    totalContentH += hH + 4;
                    if (funCommandsOpen) totalContentH += ((14 + 1) / 2) * (16 + 3) + 4;
                    totalContentH += hH + 4;
                    if (utilityCommandsOpen) totalContentH += ((1 + 1) / 2) * (16 + 3) + 4;

                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    commandsScrollY = Math.max(0, Math.min(maxScroll, commandsScrollY - (int)(scrollY * 18)));
                    return true;
                } else if (activeTab == 5) {
                    int totalContentH = 0;
                    totalContentH += 30 * 4; 
                    totalContentH += 26; 
                    
                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    customPlayerScrollY = Math.max(0, Math.min(maxScroll, customPlayerScrollY - (int)(scrollY * 18)));
                    return true;
                } else if (activeTab == 4) {
                    int totalContentH = ROW_H * 3 + 4 * 3 + 14 + 22 + 10; 
                    if (cosmeticDropdownOpen) totalContentH += 18 * 3;

                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    miscScrollY = Math.max(0, Math.min(maxScroll, miscScrollY - (int)(scrollY * 18)));
                    return true;
                } else if (activeTab == 3) {
                    int totalContentH = 22 + 4;
                    if (slayerCarryOpen) {
                        totalContentH += (ROW_H + 4) * 2 + 44 + (22 + 4);
                        if (slayerGlowOpen) {
                            totalContentH += 22 + 4 + 26 + (10 + 8 + 4) * 3 + 6;
                        }
                    }
                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    slayerScrollY = Math.max(0, Math.min(maxScroll, slayerScrollY - (int)(scrollY * 18)));
                    return true;
                } else if (activeTab == 6) {
                    int totalContentH = 0;
                    int hH = 22;
                    int noteH = 80;
                    totalContentH += hH + 4;
                    if (miningAutomatonOpen) {
                        totalContentH += (ROW_H + 4); 
                        totalContentH += (ROW_H + 4); 
                        totalContentH += (ROW_H + 4); 
                        totalContentH += 26 + 4;
                        totalContentH += hH + 4;
                        if (automatonGlowOpen) {
                            totalContentH += ROW_H + 4;
                            totalContentH += 22 + 4 + 26 + (10 + 8 + 4) * 3 + 6;
                        }
                        totalContentH += noteH + 6;
                    }
                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    miningScrollY = Math.max(0, Math.min(maxScroll, miningScrollY - (int)(scrollY * 18)));
                    return true;
                } else if (activeTab == 7) {
                    int hH = 22;
                    int totalContentH = getVisualContentHeight(hH);
                    activeVisualDropdown = 0;
                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    visualScrollY = Math.max(0, Math.min(maxScroll, visualScrollY - (int)(scrollY * 18)));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    private void copyConfigToClipboard() {
        try {
            String playerName = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "Unknown";
            String playerUuid = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID().toString() : "Unknown";

            com.google.gson.JsonObject playerObj = new com.google.gson.JsonObject();
            playerObj.addProperty("name", playerName);
            playerObj.addProperty("color", com.wish.client.color.NameColorManager.getCurrentColorCode());
            playerObj.addProperty("font", ModConfig.INSTANCE.pseudoFont);
            playerObj.addProperty("animSpeed", ModConfig.INSTANCE.pseudoAnimationSpeed);
            playerObj.addProperty("scaleX", ModConfig.INSTANCE.playerSizeEnabled ? ModConfig.INSTANCE.playerSizeX : 1.0f);
            playerObj.addProperty("scaleY", ModConfig.INSTANCE.playerSizeEnabled ? ModConfig.INSTANCE.playerSizeY : 1.0f);
            playerObj.addProperty("scaleZ", ModConfig.INSTANCE.playerSizeEnabled ? ModConfig.INSTANCE.playerSizeZ : 1.0f);
            playerObj.addProperty("spinSpeedY", ModConfig.INSTANCE.enablePlayerSpin ? ModConfig.INSTANCE.playerSpinSpeedY : 0.0f);

            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.add(playerUuid, playerObj);

            String content = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
            Minecraft.getInstance().keyboardHandler.setClipboard(content);
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("§aConfiguration exported to clipboard!"));
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to copy cosmetics configuration to clipboard", e);
        }
    }

    private net.minecraft.network.chat.Style getPseudoBaseStyle() {
        net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY;
        int f = ModConfig.INSTANCE.pseudoFont;
        if ((f & 1) != 0) st = st.withBold(true);
        if ((f & 2) != 0) st = st.withItalic(true);
        if ((f & 4) != 0) st = st.withUnderlined(true);
        if ((f & 8) != 0) st = st.withStrikethrough(true);
        if ((f & 16) != 0) st = st.withObfuscated(true);
        return st;
    }

    private boolean isPreviewBadgeActive() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            com.wish.client.color.CustomPlayerManager.CustomPlayerData data = com.wish.client.color.CustomPlayerManager.getPlayer(player.getUUID(), player.getScoreboardName());
            if (data != null) {
                return data.badge();
            }
        }
        return false;
    }

    private int getBadgeWidth(net.minecraft.client.gui.Font font) {
        if (!isPreviewBadgeActive()) return 0;
        return font.width(com.wish.client.color.NameColorManager.createBadgeComponent()) + font.width(" ");
    }

    private int getPseudoWidth(net.minecraft.client.gui.Font font, String text) {
        return getBadgeWidth(font) + font.width(net.minecraft.network.chat.Component.literal(text).withStyle(getPseudoBaseStyle()));
    }

    private int getMusicProviderIndex() {
        String cur = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider : "None";
        for (int i = 0; i < MUSIC_PROVIDER_OPTIONS.length; i++) {
            if (MUSIC_PROVIDER_OPTIONS[i].equalsIgnoreCase(cur)) {
                return i;
            }
        }
        return 0;
    }

    private int renderVisualDropdownRow(
            GuiGraphicsExtractor g, net.minecraft.client.gui.Font font,
            int cx, int cy, int cw, int clipTop, int clipBottom,
            String label, String[] options, int selectedIdx, int dropdownId,
            int mx, int my, int colHover, int colAccent
    ) {
        boolean isOpen = (activeVisualDropdown == dropdownId);
        boolean rowHover = (mx >= cx + 10 && mx < cx + cw && my >= Math.max(cy, clipTop) && my < Math.min(cy + ROW_H, clipBottom));
        RenderUtils.fillRoundedRect(g, cx + 10, cy, cw - 10, ROW_H, 4, rowHover ? 0x33FFFFFF : 0x00000000);
        RenderUtils.drawGradientOutline(g, cx + 10, cy, cw - 10, ROW_H, 4, rowHover ? 0x88AAAAAA : 0x44666666, 0x22222222);
        g.text(font, label, cx + 18, cy + 7, COL_TEXT);

        int btnW = VISUAL_DROP_W;
        int btnX = cx + cw - btnW - 10;
        int btnY = cy + 2;
        int btnH = ROW_H - 4;
        boolean btnHover = (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH && my >= clipTop && my <= clipBottom);
        RenderUtils.fillRoundedRect(g, btnX, btnY, btnW, btnH, 3, (btnHover || isOpen) ? colHover : 0x44222228);
        RenderUtils.drawGradientOutline(g, btnX, btnY, btnW, btnH, 3, (btnHover || isOpen) ? colAccent : 0x44666666, 0x22000000);

        String currentVal = (selectedIdx >= 0 && selectedIdx < options.length) ? options[selectedIdx] : "";
        g.text(font, currentVal, btnX + 8, cy + 6, 0xFFFFFFFF);
        g.text(font, isOpen ? "▲" : "▼", btnX + btnW - 13, cy + 6, 0xFFAAAAAA);

        if (isOpen) {
            activeDropX = btnX;
            activeDropY = cy + ROW_H + 2;
            activeDropW = btnW;
            activeDropOptions = options;
            activeDropSelected = selectedIdx;
        }
        return cy + ROW_H + 4;
    }

    private void renderVisualDropdownPopup(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my, int colHover, int colAccent) {
        if (activeVisualDropdown == 0 || activeDropOptions.length == 0) return;
        int itemH = 18;
        int dropH = activeDropOptions.length * itemH + 4;
        if (activeDropY + dropH > this.height - 6) {
            activeDropY = activeDropY - (ROW_H + 4) - dropH;
        }
        int dropX = activeDropX;
        int dropY = activeDropY;
        RenderUtils.fillRoundedRect(g, dropX - 1, dropY - 1, activeDropW + 2, dropH + 2, 4, 0xEE111116);
        RenderUtils.drawGradientOutline(g, dropX - 1, dropY - 1, activeDropW + 2, dropH + 2, 4, colAccent, 0x66444444);
        for (int i = 0; i < activeDropOptions.length; i++) {
            int iy = dropY + 2 + i * itemH;
            boolean optHover = (mx >= dropX && mx < dropX + activeDropW && my >= iy && my < iy + itemH);
            boolean optSel = (i == activeDropSelected);
            if (optHover) {
                RenderUtils.fillRoundedRect(g, dropX + 2, iy + 1, activeDropW - 4, itemH - 2, 3, colHover);
            } else if (optSel) {
                RenderUtils.fillRoundedRect(g, dropX + 2, iy + 1, activeDropW - 4, itemH - 2, 3, 0x33FFFFFF);
            }
            String optText = activeDropOptions[i];
            int optCol = (optSel || optHover) ? 0xFFFFFFFF : 0xFFBBBBBB;
            g.text(font, optText, dropX + 8, iy + 5, optCol);
            if (optSel) {
                g.text(font, "✓", dropX + activeDropW - 14, iy + 5, colAccent);
            }
        }
        if (mx >= dropX && mx < dropX + activeDropW && my >= dropY && my < dropY + dropH) {
            hoveredTooltipTitle = null;
            hoveredTooltipDesc = null;
        }
    }

    private void applyVisualDropdownSelection(int dropdownId, int idx) {
        if (dropdownId == 1) {
            if (idx >= 0 && idx < MUSIC_PROVIDER_OPTIONS.length) {
                ModConfig.INSTANCE.musicProvider = MUSIC_PROVIDER_OPTIONS[idx];
                ModConfig.INSTANCE.save();
            }
        } else if (dropdownId == 2) {
            ModConfig.INSTANCE.lyricsMode = (idx == 1) ? 1 : 0;
            ModConfig.INSTANCE.save();
        } else if (dropdownId == 3) {
            com.wish.client.features.visual.WeatherManager.setMode(idx);
        } else if (dropdownId == 4) {
            if (idx >= 0 && idx < com.wish.client.features.visual.CustomSkyManager.MODE_NAMES.length) {
                ModConfig.INSTANCE.customSkyMode = idx;
                ModConfig.INSTANCE.save();
            }
        }
    }

    private int getVisualContentHeight(int hH) {
        int totalContentH = hH + 4; // Lyrics header
        if (lyricsSectionOpen) {
            totalContentH += (46 + 6);    // Now Playing mini card
            totalContentH += (ROW_H + 4); // Music Provider
            totalContentH += (ROW_H + 4); // Show (Word by word / Line by line)
            totalContentH += hH + 4;      // Color collapsible header
            if (lyricsColorOpen) {
                totalContentH += 22 + 4;      // Lyrics Preview
                totalContentH += 26;          // Lyrics Hex
                totalContentH += (10 + 8 + 4) * 2 + (10 + 8 + 6); // Hue + Saturation + Brightness
            }
            totalContentH += 24 + 6;      // Info Box
        }
        totalContentH += hH + 4; // Time Changer header
        if (timeChangerSectionOpen) {
            totalContentH += 26 + 4;      // Custom Time slider
            totalContentH += (ROW_H + 4); // Freeze Time toggle
        }
        totalContentH += hH + 4; // Weather Changer header
        if (weatherChangerSectionOpen) {
            totalContentH += (ROW_H + 4);     // Weather Type dropdown
            totalContentH += (26 + 4) * 2;    // Count + Size sliders
            totalContentH += (ROW_H + 4);     // Wind toggle
            if (com.wish.client.features.visual.WeatherManager.isWindEnabled()) {
                totalContentH += 26 + 4;      // Wind Force slider
            }
        }
        totalContentH += hH + 4; // Custom Sky header
        if (customSkySectionOpen) {
            totalContentH += (ROW_H + 4);     // Sky Background mode selector
            totalContentH += (ROW_H + 4) * 3; // Show Stars + Show Sun & Moon + Slow Rotation
        }
        totalContentH += hH + 4; // Smol People (Skytils) header
        if (otherPlayersSizeSectionOpen) {
            totalContentH += (ROW_H + 4);     // Include Yourself
        }
        return totalContentH;
    }
}
