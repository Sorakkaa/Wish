package com.wish.client.gui;

import com.wish.client.config.ModConfig;
import com.wish.client.features.GhostBlockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class WishConfigScreen extends Screen {

    private final Screen lastScreen;

    // Layout
    private static final int WIN_W = 420;
    private static final int WIN_H = 300;
    private static final int SIDEBAR_W = 120;
    private static final int ROW_H = 22;

    // Colors - Glassmorphism Aesthetic
    private static final int COL_BG         = 0xCC111115;
    private static final int COL_SIDEBAR    = 0x66000000;
    private static final int COL_TEXT       = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM   = 0xFFAAAAAA;
    private static final int COL_OFF        = 0xFFFF3344; // Rouge moderne quand OFF
    
    // State
    private int activeTab = 1; // 1=Custom Pseudo by default
    private int previousTab = -1;
    private long tabTransitionStartTime = 0;
    private long screenOpenTime = 0;
    private long screenCloseTime = 0;
    private int commandsScrollY = 0;
    private int customPlayerScrollY = 0;
    private int miscScrollY = 0;
    private int pseudoSubScrollY = 0;
    
    // Sub-states - static so it remembers state and doesn't force-open on reload
    private static boolean chatChannelsOpen = false;
    private static boolean funCommandsOpen = false;
    private static boolean utilityCommandsOpen = false;
    private static boolean customNameOpen = false;
    private static boolean playerSizeOpen = false;
    private static boolean dungeonCustomBlocksOpen = false;
    private static boolean lagTimeLostOpen = false;
    private static boolean slayerCarryOpen = false;
    private static boolean menuThemeOpen = false;
    private static boolean songSettingsOpen = false;
    private boolean hexInputFocused = false;
    private boolean prefixInputFocused = false;
    private boolean suffixInputFocused = false;
    private boolean editingColor2 = false;
    private boolean providerDropdownOpen = false;
    private boolean cosmeticDropdownOpen = false;
    private boolean colorPickerOpen = false;
    private static int pseudoSubWindow = 0; // 0=None, 1=Font, 2=Anim, 3=Colors, 4=Texts
    private int draggingSlider = -1; // 0=X, 1=Y, 2=Z, 3=Hue, 4=Sat, 5=Val

    // Color picker state
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
        screenOpenTime = System.currentTimeMillis();
        screenCloseTime = 0;
        tabTransitionStartTime = System.currentTimeMillis();
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
        if (activeTab == 4) {
            hex = ModConfig.INSTANCE.menuAccentColor;
        } else if (activeTab == 3) {
            hex = ModConfig.INSTANCE.slayerGlowColor;
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

        // Fixed stable sidebar
        int lineX = 0;
        int vertX = 160;

        // SAO Vertical Line
        g.fill(vertX, 80, vertX + 1, this.height - 80, colAccent);
        
        // --- Player Info ---
        var player = Minecraft.getInstance().player;
        String baseName = player != null ? player.getName().getString() : "Player";
        
        String customPseudo = baseName;
        int pseudoColor = 0xFFFFFFFF;
        
        if (ModConfig.INSTANCE.enableNameColor) {
            String prefix = ModConfig.INSTANCE.customPrefix;
            String suffix = ModConfig.INSTANCE.customSuffix;
            if (prefix != null && !prefix.isEmpty()) prefix = prefix + " ";
            if (suffix != null && !suffix.isEmpty()) suffix = " " + suffix;
            customPseudo = (prefix != null ? prefix : "") + baseName + (suffix != null ? suffix : "");
            pseudoColor = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
        }

        int targetCrossX = (activeTab == 5) ? (this.width / 2 - 140) : (lineX - 120);
        int targetVisualCenterY = (activeTab == 5) ? (this.height / 2 + 25) : (this.height / 2 + 45);
        
        int crossX = targetCrossX;
        int visualCenterY = targetVisualCenterY;

        String fPrefix = getFontPrefix(ModConfig.INSTANCE.pseudoFont);
        int pseudoW = getPseudoWidth(font, customPseudo);
        
        float skinScale = 1.0f; // Force skin size to normal in GUI
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
        
        if (pseudoHover || activeTab == 1) {
                if (pseudoHover) {
                    g.text(font, "✎", crossX + pseudoW / 2 + 1, pseudoY + 1, 0xFFFFFFFF);
                }
            }
            if (skinHover || activeTab == 5) {
                if (skinHover) {
                    g.text(font, "✎", crossX + (int)(skinHalfW * 1.2f), skinHeadY - 45, 0xFFFFFFFF);
                }
            }
            
            if (activeTab != 5 && activeTab != 1) {
                net.minecraft.network.chat.Style baseSt = getPseudoBaseStyle();
                if (ModConfig.INSTANCE.enableNameColor && ModConfig.INSTANCE.pseudoAnimation >= 1) {
                    int c1 = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
                    int c2 = parseHex(ModConfig.INSTANCE.customHexColor2, 0xFFFFFFFF);
                    String rawText = customPseudo;
                    int len = rawText.length();
                    int currentX = crossX - pseudoW / 2;
                    long time = (long)(System.currentTimeMillis() * (double)ModConfig.INSTANCE.pseudoAnimationSpeed);
                    for (int i = 0; i < len; i++) {
                        char c = rawText.charAt(i);
                        float ratio = len > 1 ? (float)i / (len - 1) : 0;
                        
                        int colorMix;
                        if (ModConfig.INSTANCE.pseudoAnimation == 1) {
                            float hue = (time % 3000L) / 3000.0f + ratio;
                            colorMix = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
                        } else if (ModConfig.INSTANCE.pseudoAnimation == 5) { // Blink
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
                            
                            int r1 = (c1 >> 16) & 0xFF;
                            int g1 = (c1 >> 8) & 0xFF;
                            int b1 = c1 & 0xFF;
                            int r2 = (c2 >> 16) & 0xFF;
                            int g2 = (c2 >> 8) & 0xFF;
                            int b2 = c2 & 0xFF;
                            int rMix = (int)(r1 + (r2 - r1) * ratio);
                            int gMix = (int)(g1 + (g2 - g1) * ratio);
                            int bMix = (int)(b1 + (b2 - b1) * ratio);
                            colorMix = 0xFF000000 | (rMix << 16) | (gMix << 8) | bMix;
                        }
                        
                        String strChar = String.valueOf(c);
                        net.minecraft.network.chat.Component chComp = net.minecraft.network.chat.Component.literal(strChar).withStyle(baseSt.withColor(net.minecraft.network.chat.TextColor.fromRgb(colorMix & 0x00FFFFFF)));
                        g.text(font, chComp, currentX, pseudoY, colorMix);
                        currentX += font.width(chComp);
                    }
                } else {
                    net.minecraft.network.chat.Component fullComp = net.minecraft.network.chat.Component.literal(customPseudo).withStyle(baseSt.withColor(net.minecraft.network.chat.TextColor.fromRgb(pseudoColor & 0x00FFFFFF)));
                    g.text(font, fullComp, crossX - pseudoW / 2, pseudoY, pseudoColor);
                }
            }
    
        if (activeTab == 5) {
            try {
                net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g, crossX - skinHalfW * 6, skinHeadY - 100, crossX + skinHalfW * 6, skinFeetY, renderSize, 0.0625F, mx, my, player);
            } catch (Exception e) {
                g.text(font, "SKIN", crossX - font.width("SKIN") / 2, skinFeetY - 30, 0xFFFFFFFF);
            }
        }

        // --- Sidebar: Tabs Menu ---
        String[] menuTabs = {"Custom Pseudo", "Player Model", "Command", "Dungeon", "Slayer", "Misc Setting"};
        int[] menuTabIds = {1, 5, 0, 2, 3, 4};
        int tabSpacing = 30;
        int tabY = (this.height - (menuTabs.length * tabSpacing)) / 2;
        int tabX = lineX + 35;

        for (int i = 0; i < menuTabs.length; i++) {
            int tabId = menuTabIds[i];
            boolean active = (activeTab == tabId);
            boolean hover = (mx >= tabX && mx < tabX + 120 && my >= tabY && my < tabY + 20);
            
            g.text(font, menuTabs[i], tabX, tabY, active ? 0xFFFFFFFF : (hover ? 0xFFCCCCCC : 0xFFAAAAAA));
            
            if (active) {
                long now = System.currentTimeMillis();
                float tabRaw = tabTransitionStartTime > 0 ? Math.min(1.0f, (now - tabTransitionStartTime) / 220.0f) : 1.0f;
                float tabAnim = 1.0f - (float) Math.pow(1.0f - tabRaw, 3);
                int indicatorOffset = (int)((1.0f - tabAnim) * 5);
                g.text(font, ">", tabX + font.width(menuTabs[i]) + 8 + indicatorOffset, tabY, colAccent);
            }
            
            tabY += tabSpacing;
        }

        // --- Right Side: Content ---
        if (activeTab != -1) {
            int targetCw = Math.min(this.width - 220, 450); // limit to 450 width
            int cw = targetCw;
            
            int spaceRight = this.width - vertX;
            int cx = vertX + spaceRight / 2 - cw / 2;
            
            int mainWinH = Math.min(this.height - 80, 360);
            int mainWinY = (this.height - mainWinH) / 2;
            int cy = mainWinY + 10;
            
            int py = mainWinY - 5;
            int WIN_H = mainWinH;

            // Unified Tab Transition Animation
            long now = System.currentTimeMillis();
            float tabRaw = tabTransitionStartTime > 0 ? Math.min(1.0f, (now - tabTransitionStartTime) / 220.0f) : 1.0f;
            float tabAnim = 1.0f - (float) Math.pow(1.0f - tabRaw, 3); // Cubic Ease-Out
            int contentSlideY = (int) ((1.0f - tabAnim) * 12);
            
            if (activeTab != 1 && activeTab != 5) {
                // Render Window Background
                RenderUtils.fillRoundedRect(g, cx - 10, mainWinY, cw + 20, mainWinH, 6, 0x00000000);
                RenderUtils.drawGradientOutline(g, cx - 10, mainWinY, cw + 20, mainWinH, 6, colAccent, colAccent);
            } else if (activeTab == 1) {
                // Centered UI for Custom Pseudo
                cw = 320; 
                cx = (this.width - cw) / 2;
                cy = this.height / 2 - 80;
            } else if (activeTab == 5) {
                cw = 280;
                cx = this.width / 2 + 20;
                cy = this.height / 2 - 80;
            }
            
            // Global Red 'X' Close Button for all menus
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

            // Calculer la hauteur totale pour borner le scroll
            int totalContentH = 0;
            int hH = 22;
            totalContentH += hH + 4; // Chat Channels header
            if (chatChannelsOpen) {
                totalContentH += 3 * (ROW_H + 4);
            }
            totalContentH += hH + 4; // Fun Commands header
            if (funCommandsOpen) {
                int funBtnH = 16;
                totalContentH += ((15 + 1) / 2) * (funBtnH + 3) + 4;
            }
            totalContentH += hH + 4; // Utility Commands header
            if (utilityCommandsOpen) {
                int utilBtnH = 16;
                totalContentH += ((1 + 1) / 2) * (utilBtnH + 3) + 4;
            }

            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (commandsScrollY > maxScroll) commandsScrollY = maxScroll;
            if (commandsScrollY < 0) commandsScrollY = 0;

            // Enable scissor so nothing leaks outside the window
            g.enableScissor(cx, clipTop, cx + cw, clipBottom);

            cy -= commandsScrollY;

            // --- Section Déroulante: Chat Channels ---
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

            // --- Section Déroulante: Fun Commands ---
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

            // --- Section Déroulante: Utility Commands ---
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
                        g.fill(icx, icy - 3, icx + 1, icy - 2, iCol); // dot
                        g.fill(icx, icy - 1, icx + 1, icy + 3, iCol); // stem
                        g.fill(icx - 1, icy - 1, icx, icy, iCol);     // top serif
                        g.fill(icx - 1, icy + 2, icx + 2, icy + 3, iCol); // base serif

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

            // Discrète barre de défilement (scrollbar) si besoin de scroll
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
            // Centered UI for Custom Pseudo
            cw = 320;
            cx = (this.width - cw) / 2;
            cy = this.height / 2 - 80 + contentSlideY;
            
            // (Back Button removed in favor of global red cross)
            
            // Render Custom Pseudo at top center
            g.pose().pushMatrix();
            float scale = 2.0f;
            g.pose().scale(scale, scale);
            
            String fPrefix2 = getFontPrefix(ModConfig.INSTANCE.pseudoFont);
            net.minecraft.network.chat.Style baseSt2 = getPseudoBaseStyle();
            int pWidth = getPseudoWidth(font, customPseudo);
            int pX = (int)((cx + (cw - pWidth * scale) / 2) / scale);
            int pY = (int)(cy / scale);
            
            if (ModConfig.INSTANCE.enableNameColor && ModConfig.INSTANCE.pseudoAnimation >= 1) {
                int c1 = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
                int c2 = parseHex(ModConfig.INSTANCE.customHexColor2, 0xFFFFFFFF);
                int len = customPseudo.length();
                int currentX = pX;
                long time = (long)(System.currentTimeMillis() * (double)ModConfig.INSTANCE.pseudoAnimationSpeed);
                for (int i = 0; i < len; i++) {
                    char c = customPseudo.charAt(i);
                    float ratio = len > 1 ? (float)i / (len - 1) : 0;
                    int colorMix;
                    if (ModConfig.INSTANCE.pseudoAnimation == 1) {
                        float hue = (time % 3000L) / 3000.0f + ratio;
                        colorMix = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
                    } else if (ModConfig.INSTANCE.pseudoAnimation == 5) { // Blink
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
                g.text(font, fullComp, pX, pY, pseudoColor2);
            }
            g.pose().popMatrix();
            cy += 36; // Space after title
            
            // Buttons: Police, Animation, Couleurs, Textes
            int btnH = 24;
            int totalBtnW = 4 * 70 + 3 * 10;
            int dockX = cx + cw/2 - totalBtnW/2;
            int dockY = cy;
            
            String[] dockLabels = {"Police", "Animation", "Couleurs", "Textes"};
            for (int i = 0; i < 4; i++) {
                int bx = dockX + i * 80;
                boolean bHover = mx >= bx && mx < bx + 70 && my >= dockY && my < dockY + btnH;
                boolean isCurrentTab = (pseudoSubWindow == i + 1);
                RenderUtils.fillRoundedRect(g, bx, dockY, 70, btnH, 4, 0x00000000); // 100% transparent
                RenderUtils.drawGradientOutline(g, bx, dockY, 70, btnH, 4, isCurrentTab ? colAccent : (bHover ? colAccentDim : 0x44666666), 0x22222222);
                g.text(font, dockLabels[i], bx + 35 - font.width(dockLabels[i])/2, dockY + 8, isCurrentTab ? colAccent : COL_TEXT);
            }
            
            if (pseudoSubWindow > 0) {
                int winW = 310;
                int winH = 200;
                if (pseudoSubWindow == 1) winH = 100; // Police (styles)
                if (pseudoSubWindow == 2) {
                    winH = 126; // Animation (grid 3 rows)
                    if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) winH += 40;
                }
                if (pseudoSubWindow == 3) winH = 240; // Couleurs
                if (pseudoSubWindow == 4) winH = 120; // Textes
                
                int winX = this.width / 2 - winW / 2;
                int winY = dockY + btnH + 10 + contentSlideY;
                if (winY + winH > this.height - 10) {
                    winY = this.height - winH - 10;
                }
                
                int availH = this.height - winY - 10;
                boolean needsScroll = winH > availH && availH > 80;
                int renderWinH = needsScroll ? availH : winH;
                
                // Transparent glass style with button outline (fully transparent background)
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
                
                if (pseudoSubWindow == 1) { // Police
                    String[] fonts = {"Gras", "Italique", "Souligné", "Barré"};
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
                } else if (pseudoSubWindow == 2) { // Animation
                    String[] anims = {"Aucune", "Chroma", "Gradient", "Onde", "Respi", "Clignotant"};
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
                    cY += 3 * 24; // 3 rows
                    if (ModConfig.INSTANCE.pseudoAnimationSpeed == 0.0f) ModConfig.INSTANCE.pseudoAnimationSpeed = 1.0f;
                    if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) {
                        cY += 6;
                        renderSlider(g, font, cX, cY, cW, "Vitesse", ModConfig.INSTANCE.pseudoAnimationSpeed, mx, my, 6, 0.1f, 3.0f);
                    }
                } else if (pseudoSubWindow == 3) { // Couleurs
                    int c1Active = !editingColor2 ? 1 : 0;
                    boolean c1BtnHover = mx >= cX && mx < cX + cW/2 - 5 && my >= cY && my < cY + 26;
                    int col1 = parseHex(ModConfig.INSTANCE.customHexColor, 0xFFFFFFFF);
                    int borderCol1 = c1Active == 1 ? colAccent : (c1BtnHover ? colAccentDim : 0x44666666);
                    RenderUtils.drawGradientOutline(g, cX, cY, cW/2 - 5, 26, 4, borderCol1, borderCol1);
                    RenderUtils.fillRoundedRect(g, cX + 10, cY + 5, 16, 16, 8, col1);
                    g.text(font, "Coul. 1", cX + 30, cY + 9, c1Active==1 ? colAccent : COL_TEXT);
                    
                    int c2X = cX + cW/2 + 5;
                    int c2Active = editingColor2 ? 1 : 0;
                    boolean c2BtnHover = mx >= c2X && mx < c2X + cW/2 - 5 && my >= cY && my < cY + 26;
                    int col2 = parseHex(ModConfig.INSTANCE.customHexColor2, 0xFFFFFFFF);
                    int borderCol2 = c2Active == 1 ? colAccent : (c2BtnHover ? colAccentDim : 0x44666666);
                    RenderUtils.drawGradientOutline(g, c2X, cY, cW/2 - 5, 26, 4, borderCol2, borderCol2);
                    RenderUtils.fillRoundedRect(g, c2X + 10, cY + 5, 16, 16, 8, col2);
                    g.text(font, "Coul. 2", c2X + 30, cY + 9, c2Active==1 ? colAccent : COL_TEXT);
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
                } else if (pseudoSubWindow == 4) { // Textes
                    cY = renderInput(g, font, cX, cY, cW, "Prefix", ModConfig.INSTANCE.customPrefix, prefixInputFocused, mx, my);
                    cY += 10;
                    cY = renderInput(g, font, cX, cY, cW, "Suffix", ModConfig.INSTANCE.customSuffix, suffixInputFocused, mx, my);
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
            totalContentH += 30 * 4; // Sliders X Y Z and Spin Y
            totalContentH += 28; // F5 Nametag toggle
            totalContentH += 26; // Reset button
            
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

            // --- Section: Custom Blocks Dropdown Menu ---
            int h1Y = cy;
            boolean h1Hover = (mx >= cx && mx < cx + cw && my >= h1Y && my < h1Y + hH);
            RenderUtils.fillRoundedRect(g, cx, h1Y, cw, hH, 4, dungeonCustomBlocksOpen ? colHover : (h1Hover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, h1Y, cw, hH, 4, dungeonCustomBlocksOpen ? colAccent : 0x55888888, 0x22444444);
            
            g.text(font, (dungeonCustomBlocksOpen ? "▼ " : "▶ ") + "Custom Blocks (F7/M7)", cx + 8, h1Y + 7, COL_TEXT);
            
            boolean state = GhostBlockManager.isGhostBlocksEnabled;
            int dotSize = 8;
            int dotX = cx + cw - dotSize - 10;
            int dotY = h1Y + 7;
            RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, state ? colAccent : COL_OFF);
            RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, 0x44FFFFFF, 0x11000000);
            
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
            RenderUtils.fillRoundedRect(g, dotX2, dotY2, dotSize, dotSize, 4, lagState ? colAccent : COL_OFF);
            RenderUtils.drawGradientOutline(g, dotX2, dotY2, dotSize, dotSize, 4, 0x44FFFFFF, 0x11000000);
            
            cy += hH + 4;

            if (lagTimeLostOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Send in Chat", ModConfig.INSTANCE.sendLagTimeLost, mx, my, null, null);
            }

        } else if (activeTab == 3) {
            int hH = 22;
            int hY = cy;
            boolean hHover = (mx >= cx && mx < cx + cw && my >= hY && my < hY + hH);
            RenderUtils.fillRoundedRect(g, cx, hY, cw, hH, 4, slayerCarryOpen ? colHover : (hHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, hY, cw, hH, 4, slayerCarryOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (slayerCarryOpen ? "▼ " : "▶ ") + "Slayer Carry Tracker", cx + 8, hY + 7, COL_TEXT);
            cy += hH + 4;

            if (slayerCarryOpen) {
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Slayer Carry Tracker", ModConfig.INSTANCE.enableSlayerCarry, mx, my, null, null);
                cy = renderToggle(g, font, cx + 10, cy, cw - 10, "Boss Spawn Notification", ModConfig.INSTANCE.enableBossSpawnHud, mx, my, null, null);
                
                RenderUtils.fillRoundedRect(g, cx + 10, cy, cw - 10, 40, 4, 0x11FFFFFF);
                g.text(font, "§7Commands:", cx + 16, cy + 4, COL_TEXT_DIM);
                g.text(font, "/wish carry <player> <amount>", cx + 24, cy + 16, 0xFFBBBBBB);
                g.text(font, "/wish stopcarry <player>", cx + 24, cy + 26, 0xFFBBBBBB);
                cy += 44;
                
                int boxW = 50;
                int boxH = 22;
                int previewColor = parseHex(ModConfig.INSTANCE.slayerGlowColor, 0xFFFF55AA);
                
                RenderUtils.fillRoundedRect(g, cx + 10, cy, boxW, boxH, 4, previewColor);
                RenderUtils.drawGradientOutline(g, cx + 10, cy, boxW, boxH, 4, 0x66FFFFFF, 0x22FFFFFF);
                g.text(font, "Color Preview", cx + 10 + boxW + 10, cy + 7, COL_TEXT);
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
            
        } else if (activeTab == 4) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            int clipHeight = clipBottom - clipTop;

            int totalContentH = ROW_H * 3 + 4 * 3; // toggles + cosmetic visibility
            int hH = 22;
            totalContentH += hH + 4; // Menu Color header
            if (menuThemeOpen) {
                totalContentH += 22 + 4 + 26 + (10 + 8 + 4) * 3 + 10; // preview + hex + 3 sliders
            }
            totalContentH += 14 + 22 + 10; // Discord webhook button with margin

            int maxScroll = Math.max(0, totalContentH - clipHeight);
            if (miscScrollY > maxScroll) miscScrollY = maxScroll;
            if (miscScrollY < 0) miscScrollY = 0;

            g.enableScissor(cx, clipTop, cx + cw, clipBottom);
            cy -= miscScrollY;

            cy = renderToggle(g, font, cx, cy, cw, "Check for Updates", ModConfig.INSTANCE.enableUpdateCheck, mx, my, null, null);
            cy = renderToggle(g, font, cx, cy, cw, "Always in M7/F7 (Simulators)", ModConfig.INSTANCE.alwaysInM7F7, mx, my, null, null);

            // --- Cosmetics Visibility Selector ---
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



            // --- Section Déroulante: Menu Color ---
            int themeHeaderY = cy;
            boolean themeHeaderHover = (mx >= cx && mx < cx + cw && my >= Math.max(themeHeaderY, clipTop) && my < Math.min(themeHeaderY + hH, clipBottom));
            RenderUtils.fillRoundedRect(g, cx, themeHeaderY, cw, hH, 4, menuThemeOpen ? colHover : (themeHeaderHover ? 0x44444455 : 0x2A2A2E55));
            RenderUtils.drawGradientOutline(g, cx, themeHeaderY, cw, hH, 4, menuThemeOpen ? colAccent : 0x55888888, 0x22444444);
            g.text(font, (menuThemeOpen ? "▼ " : "▶ ") + "Menu Color", cx + 8, themeHeaderY + 7, COL_TEXT);
            cy += hH + 4;

            if (menuThemeOpen) {
                int boxW = 50;
                int boxH = 22;
                int previewColor = colAccent;

                RenderUtils.fillRoundedRect(g, cx + 10, cy, boxW, boxH, 4, previewColor);
                RenderUtils.drawGradientOutline(g, cx + 10, cy, boxW, boxH, 4, 0x66FFFFFF, 0x22FFFFFF);
                g.text(font, "Accent Preview", cx + 10 + boxW + 10, cy + 7, COL_TEXT);
                cy += boxH + 4;

                cy = renderInput(g, font, cx + 10, cy, cw - 10, "Hex", ModConfig.INSTANCE.menuAccentColor, hexInputFocused, mx, my);

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
            
            // --- Discord Share Button (Clean & Centered) ---
            cy += 14;
            int discBtnW = Math.min(cw - 24, 210);
            int discBtnH = 22;
            int discBtnX = cx + (cw - discBtnW) / 2;
            boolean shareHover = (mx >= discBtnX && mx < discBtnX + discBtnW && my >= cy && my < cy + discBtnH);
            
            RenderUtils.fillRoundedRect(g, discBtnX, cy, discBtnW, discBtnH, 5, shareHover ? 0xEE5865F2 : 0xAA5865F2);
            RenderUtils.drawGradientOutline(g, discBtnX, cy, discBtnW, discBtnH, 5, shareHover ? 0xFFFFFFFF : 0x887289DA, shareHover ? 0xAA7289DA : 0x22000000);
            
            String btnText = "Share Cosmetics (Discord)";
            if (System.currentTimeMillis() - discordStatusTime < 5000L) {
                btnText = discordStatusMsg;
            }
            
            if (shareHover) {
                hoveredTooltipTitle = "Share Cosmetics (Discord)";
                hoveredTooltipDesc = "Sends only your cosmetic settings (custom pseudo, colors, animations, size) to the Discord webhook.";
            }
            
            // Mini Discord icon
            int iconW = 11;
            int gap = 6;
            int textW = font.width(btnText);
            int totalContentW = iconW + gap + textW;
            int startX = discBtnX + (discBtnW - totalContentW) / 2;
            int iconY = cy + (discBtnH - 8) / 2;
            
            g.fill(startX + 1, iconY, startX + 10, iconY + 7, 0xFFFFFFFF);
            g.fill(startX, iconY + 1, startX + 1, iconY + 6, 0xFFFFFFFF);
            g.fill(startX + 10, iconY + 1, startX + 11, iconY + 6, 0xFFFFFFFF);
            int eyeColor = shareHover ? 0xFF5865F2 : 0xFF4752C4;
            g.fill(startX + 2, iconY + 2, startX + 4, iconY + 4, eyeColor);
            g.fill(startX + 7, iconY + 2, startX + 9, iconY + 4, eyeColor);
            g.fill(startX + 4, iconY + 5, startX + 7, iconY + 6, eyeColor);
            
            g.text(font, btnText, startX + iconW + gap, cy + 7, 0xFFFFFFFF);
            cy += discBtnH + 10;

            g.disableScissor();

            // Render Cosmetics Visibility Dropdown Overlay above other elements
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
        } // close activeTab != -1

        // --- Song Settings Window Overlay ---
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

        // Render sleek modern tooltip on hover
        if (hoveredTooltipTitle != null && hoveredTooltipDesc != null) {
            renderTooltipCard(g, font, mx, my, hoveredTooltipTitle, hoveredTooltipDesc);
        }
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

        // Shadow & Glass Background
        RenderUtils.fillRoundedRect(g, ttX - 2, ttY - 2, ttW + 4, ttH + 4, 6, 0x33000000);
        RenderUtils.drawGlassPanel(g, ttX, ttY, ttW, ttH, 0xF0111118, getAccentColor());

        // Header Title
        g.text(font, "§l" + title, ttX + 8, ttY + 6, COL_TEXT);
        int lineY = ttY + 18;
        for (var line : lines) {
            g.text(font, line, ttX + 8, lineY, 0xFFCCCCCC);
            lineY += 10;
        }
    }

    private int renderToggle(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, boolean state, int mx, int my, String ttTitle, String ttDesc) {
        boolean hover = (mx >= x && mx < x + w && my >= y && my < y + ROW_H);
        int bgColor = hover ? 0x5544444C : 0x332E2E33;
        int borderColor = hover ? 0x88AAAAAA : 0x44666666;
        
        RenderUtils.fillRoundedRect(g, x, y, w, ROW_H, 4, bgColor);
        RenderUtils.drawGradientOutline(g, x, y, w, ROW_H, 4, borderColor, 0x22222222);

        g.text(font, label, x + 8, y + 7, state ? COL_TEXT : COL_TEXT_DIM);
        
        int dotSize = 8;
        int dotX = x + w - dotSize - 8;
        int dotY = y + 7;
        
        RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, state ? getAccentColor() : COL_OFF);
        RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, 0x44FFFFFF, 0x11000000);
        
        if (hover && ttTitle != null && ttDesc != null) {
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

        String text = label + ": " + val + (focused && (System.currentTimeMillis()/400%2==0)?"_":"");
        g.text(font, text, x + 8, y + 7, focused ? COL_TEXT : COL_TEXT_DIM);
        
        return y + h + 4;
    }

    private int renderSlider(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, float val, int mx, int my, int id, float min, float max) {
        int h = 26;
        boolean hover = (mx >= x && mx < x + w && my >= y && my < y + h);
        int bgColor = hover ? 0x5544444C : 0x332E2E33;
        int borderColor = hover ? 0x88AAAAAA : 0x44666666;
        
        RenderUtils.fillRoundedRect(g, x, y, w, h, 4, bgColor);
        RenderUtils.drawGradientOutline(g, x, y, w, h, 4, borderColor, 0x22222222);

        g.text(font, label + String.format(": %.2f", val), x + 8, y + 5, COL_TEXT);
        
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

    private int renderTimerSlider(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, String label, float val, int mx, int my) {
        g.text(font, label + String.format(": %.2fs", val), x, y + 2, COL_TEXT);
        
        int trackY = y + 14;
        RenderUtils.fillRoundedRect(g, x, trackY, w, 4, 2, 0x55FFFFFF);
        
        float pct = (val - 1.0f) / 2.0f; // domain [1.0, 3.0] -> [0, 1]
        pct = Math.max(0, Math.min(1, pct));
        
        RenderUtils.fillRoundedRect(g, x, trackY, (int)(w * pct), 4, 2, getAccentColor());
        
        int knobX = x + (int)(w * pct) - 4;
        RenderUtils.fillRoundedRect(g, knobX, trackY - 3, 8, 10, 4, 0xFFFFFFFF);
        
        return y + 26;
    }



    private void updateColorFromSliders() {
        int rgb = java.awt.Color.HSBtoRGB(selectedHue, selectedSat, selectedVal) & 0xFFFFFF;
        String hex = String.format("#%06X", rgb);
        if (activeTab == 4) {
            ModConfig.INSTANCE.menuAccentColor = hex;
        } else if (activeTab == 3) {
            ModConfig.INSTANCE.slayerGlowColor = hex;
        } else if (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2) {
            ModConfig.INSTANCE.customHexColor2 = hex;
        } else {
            ModConfig.INSTANCE.customHexColor = hex;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int mx = (int) (Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getScreenWidth());
        int lineX = this.width / 2 - 80;
        int vertX = lineX + 160;
        int spaceRight = this.width - vertX;
        int cw = Math.min(this.width - 220, 450);
        int cx = vertX + spaceRight / 2 - cw / 2;
        
        if (activeTab == 1) {
            cw = 320;
            cx = (this.width - cw) / 2;
        } else if (activeTab == 5) {
            cw = 280;
            cx = this.width / 2 + 20;
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
                int winW = 280;
                int winX = this.width / 2 - winW / 2;
                startX = winX + 15;
                sliderW = (winW - 30) - 10;
            } else if (activeTab == 4 || activeTab == 3) {
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
                int winW = 280;
                int winX = this.width / 2 - winW / 2;
                startX = winX + 15 + 8;
                sliderW = (winW - 30) - 16;
            }
            float newPct = (float)(mx - startX) / sliderW;
            newPct = Math.max(0, Math.min(1, newPct));
            ModConfig.INSTANCE.pseudoAnimationSpeed = 0.1f + newPct * 2.9f;
        }

        return super.mouseDragged(event, dragX, dragY);
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
                    return true; // Clicked outside dropdown, close it
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
            
            // If click inside the window, absorb it
            if (mx >= winX && mx <= winX + winW && my >= winY && my <= winY + winH) {
                return true;
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
        
        String[] menuTabs = {"Custom Pseudo", "Player Model", "Command", "Dungeon", "Slayer", "Misc Setting"};
        int[] menuTabIds = {1, 5, 0, 2, 3, 4};
        int tabSpacing = 30;
        int tabY = (this.height - (menuTabs.length * tabSpacing)) / 2;
        int tabX = lineX + 35;
        
        for (int i = 0; i < menuTabs.length; i++) {
            if (mx >= tabX && mx < tabX + 120 && my >= tabY && my < tabY + 20) {
                if (activeTab != menuTabIds[i]) {
                    previousTab = activeTab;
                    activeTab = menuTabIds[i];
                    tabTransitionStartTime = System.currentTimeMillis();
                }
                hexInputFocused = prefixInputFocused = suffixInputFocused = false;
                providerDropdownOpen = false;
                cosmeticDropdownOpen = false;
                updateHSBFromConfig();
                return true;
            }
            tabY += tabSpacing;
        }

        int crossX = (activeTab == 5) ? (this.width / 2 - 140) : (lineX - 120);
        
        var player = Minecraft.getInstance().player;
        String baseName = player != null ? player.getName().getString() : "Player";
        String customPseudo = baseName;
        if (ModConfig.INSTANCE.enableNameColor) {
            String prefix = ModConfig.INSTANCE.customPrefix;
            String suffix = ModConfig.INSTANCE.customSuffix;
            if (prefix != null && !prefix.isEmpty()) prefix = prefix + " ";
            if (suffix != null && !suffix.isEmpty()) suffix = " " + suffix;
            customPseudo = (prefix != null ? prefix : "") + baseName + (suffix != null ? suffix : "");
        }
        
        int pseudoW = font.width("§l" + customPseudo);
        
        float skinScale = 1.0f; // Force skin size to normal in GUI
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
            previousTab = activeTab;
            activeTab = 1;
            tabTransitionStartTime = System.currentTimeMillis();
            hexInputFocused = prefixInputFocused = suffixInputFocused = false;
            providerDropdownOpen = false;
            updateHSBFromConfig();
            return true;
        }
        if (skinHover && activeTab != 5) {
            previousTab = activeTab;
            activeTab = 5;
            tabTransitionStartTime = System.currentTimeMillis();
            hexInputFocused = prefixInputFocused = suffixInputFocused = false;
            providerDropdownOpen = false;
            updateHSBFromConfig();
            return true;
        }

        int mainWinH = Math.min(this.height - 80, 360);
        int mainWinY = (this.height - mainWinH) / 2;
        int py = mainWinY - 5;
        
        int vertX = lineX + 160;
        int spaceRight = this.width - vertX;
        int cw = Math.min(this.width - 220, 450);
        int cx = vertX + spaceRight / 2 - cw / 2;
        
        int cy = py + 40;
        if (activeTab == 5) {
            cw = 280;
            cx = this.width / 2 + 20;
            cy = this.height / 2 - 80;
        }
        if (activeTab == -1) return super.mouseClicked(event, focused);
        int WIN_H = mainWinH;
        
        if (activeTab == 0) {
            int clipTop = py + 40;
            int clipBottom = py + WIN_H - 10;
            // Ne pas cliquer en dehors de la zone scrollable
            if (my < clipTop || my > clipBottom || mx < cx || mx > cx + cw) {
                return false;
            }

            cy -= commandsScrollY;
            int hH = 22;

            // Chat Channels Dropdown Header
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                chatChannelsOpen = !chatChannelsOpen;
                return true;
            }
            cy += hH + 4;

            if (chatChannelsOpen) {
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + ROW_H) { ModConfig.INSTANCE.enableAc = !ModConfig.INSTANCE.enableAc; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + ROW_H) { ModConfig.INSTANCE.enableGc = !ModConfig.INSTANCE.enableGc; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + ROW_H) { ModConfig.INSTANCE.enablePc = !ModConfig.INSTANCE.enablePc; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
            }

            // Fun Commands Dropdown Header
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                funCommandsOpen = !funCommandsOpen;
                return true;
            }
            cy += hH + 4;

            if (funCommandsOpen) {
                int btnW = (cw - 6) / 2;
                int btnH = 16;
                for (int i = 0; i < 15; i++) {
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

            // Utility Commands Dropdown Header
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
            
            cy += 36; // Space for title
            
            int btnH = 24;
            int totalBtnW = 4 * 70 + 3 * 10;
            int dockX = cx + cw/2 - totalBtnW/2;
            int dockY = cy;
            for (int i = 0; i < 4; i++) {
                int bx = dockX + i * 80;
                if (mx >= bx && mx < bx + 70 && my >= dockY && my < dockY + btnH) {
                    if (pseudoSubWindow == i + 1) {
                        pseudoSubWindow = 0;
                    } else {
                        pseudoSubWindow = i + 1;
                        if (pseudoSubWindow == 3) {
                            updateHSBFromConfig(); // refresh color picker
                        }
                    }
                    return true;
                }
            }
            
            if (pseudoSubWindow > 0) {
                int winW = 310;
                int winH = 200;
                if (pseudoSubWindow == 1) winH = 100; // Police (styles)
                if (pseudoSubWindow == 2) {
                    winH = 126; // Animation (grid 3 rows)
                    if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) winH += 40;
                }
                if (pseudoSubWindow == 3) winH = 240; // Couleurs
                if (pseudoSubWindow == 4) winH = 120; // Textes
                
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
                
                if (pseudoSubWindow == 1) { // Police
                    int[] bitmasks = {1, 2, 4, 8};
                    int colW2 = (cW - 10) / 2;
                    for (int i = 0; i < 4; i++) {
                        int bx = cX + (i % 2) * (colW2 + 10);
                        int by = cY + (i / 2) * 22;
                        if (mx >= bx && mx < bx + colW2 && my >= by && my < by + 18) {
                            if ((ModConfig.INSTANCE.pseudoFont & bitmasks[i]) != 0) {
                                ModConfig.INSTANCE.pseudoFont &= ~bitmasks[i]; // remove
                            } else {
                                ModConfig.INSTANCE.pseudoFont |= bitmasks[i]; // add
                            }
                            ModConfig.INSTANCE.save();
                            return true;
                        }
                    }
                } else if (pseudoSubWindow == 2) { // Animation
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
                } else if (pseudoSubWindow == 3) { // Couleurs
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
                    if (mx >= cX && mx < cX + cW && my >= cY && my < cY + 22) { hexInputFocused = true; prefixInputFocused = false; suffixInputFocused = false; return true; }
                    cY += 22;
                    
                    int hueW = cW - 10;
                    int hueH = 10;
                    
                    cY += 12; // Hue label
                    if (mx >= cX && mx < cX + hueW && my >= cY && my < cY + hueH) { draggingSlider = 3; updateColorFromSliders(); return true; }
                    cY += hueH + 8;
                    
                    cY += 12; // Sat label
                    if (mx >= cX && mx < cX + hueW && my >= cY && my < cY + hueH) { draggingSlider = 4; updateColorFromSliders(); return true; }
                    cY += hueH + 8;
                    
                    cY += 12; // Val label
                    if (mx >= cX && mx < cX + hueW && my >= cY && my < cY + hueH) { draggingSlider = 5; updateColorFromSliders(); return true; }
                } else if (pseudoSubWindow == 4) { // Textes
                    if (mx >= cX && mx < cX + cW && my >= cY && my < cY + 22) { prefixInputFocused = true; suffixInputFocused = false; hexInputFocused = false; return true; }
                    cY += 32;
                    if (mx >= cX && mx < cX + cW && my >= cY && my < cY + 22) { suffixInputFocused = true; prefixInputFocused = false; hexInputFocused = false; return true; }
                }
                
                return true; // Click intercepted by the window overlay
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

            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                if (mx >= cx + cw - 40) {
                    GhostBlockManager.isGhostBlocksEnabled = !GhostBlockManager.isGhostBlocksEnabled; 
                    if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
                } else {
                    dungeonCustomBlocksOpen = !dungeonCustomBlocksOpen;
                }
                return true;
            }
            cy += hH + 4;
            
            if (dungeonCustomBlocksOpen) {
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + ROW_H) { 
                    GhostBlockManager.isGlassGhostBlocksEnabled = !GhostBlockManager.isGlassGhostBlocksEnabled; 
                    if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
                    return true; 
                } cy += ROW_H + 4;
            }
            
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                if (mx >= cx + cw - 40) {
                    ModConfig.INSTANCE.enableLagTimeLost = !ModConfig.INSTANCE.enableLagTimeLost; 
                    ModConfig.INSTANCE.save(); 
                } else {
                    lagTimeLostOpen = !lagTimeLostOpen;
                }
                return true;
            }
            cy += hH + 4;
            
            if (lagTimeLostOpen) {
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + ROW_H) { 
                    ModConfig.INSTANCE.sendLagTimeLost = !ModConfig.INSTANCE.sendLagTimeLost; 
                    ModConfig.INSTANCE.save();
                    return true; 
                } cy += ROW_H + 4;
            }

        } else if (activeTab == 3) {
            int hH = 22;
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                slayerCarryOpen = !slayerCarryOpen;
                hexInputFocused = false;
                return true;
            }
            cy += hH + 4;
            
            if (slayerCarryOpen) {
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + ROW_H) { 
                    ModConfig.INSTANCE.enableSlayerCarry = !ModConfig.INSTANCE.enableSlayerCarry; 
                    ModConfig.INSTANCE.save(); 
                    return true; 
                } cy += ROW_H + 4;
                
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + ROW_H) { 
                    ModConfig.INSTANCE.enableBossSpawnHud = !ModConfig.INSTANCE.enableBossSpawnHud; 
                    ModConfig.INSTANCE.save(); 
                    return true; 
                } cy += ROW_H + 4;
                
                cy += 44;
                
                cy += 22 + 4; // color preview box
                
                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 22) { hexInputFocused = true; prefixInputFocused = suffixInputFocused = false; return true; } cy += 22 + 4;
                
                cy += 10;
                int hueW = cw - 20;
                if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) { draggingSlider = 3; updateColorFromSliders(); return true; } cy += 14;
                
                cy += 10;
                if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) { draggingSlider = 4; updateColorFromSliders(); return true; } cy += 14;
                
                cy += 10;
                if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) { draggingSlider = 5; updateColorFromSliders(); return true; } cy += 16;
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

            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ROW_H) { ModConfig.INSTANCE.enableUpdateCheck = !ModConfig.INSTANCE.enableUpdateCheck; ModConfig.INSTANCE.save(); return true; } cy += ROW_H + 4;
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ROW_H) { 
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
            

            
            // Music Provider click logic removed from here

            // Menu Color Collapsible
            int hH = 22;
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
                menuThemeOpen = !menuThemeOpen;
                hexInputFocused = prefixInputFocused = suffixInputFocused = false;
                return true;
            }
            cy += hH + 4;

            if (menuThemeOpen) {
                cy += 22 + 4; // Color preview box

                if (mx >= cx + 10 && mx < cx + cw && my >= cy && my < cy + 22) {
                    hexInputFocused = true;
                    prefixInputFocused = suffixInputFocused = false;
                    return true;
                }
                cy += 22 + 4;

                cy += 10;
                int hueW = cw - 20;
                if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) { draggingSlider = 3; updateColorFromSliders(); return true; } cy += 14;

                cy += 10;
                if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) { draggingSlider = 4; updateColorFromSliders(); return true; } cy += 14;

                cy += 10;
                if (mx >= cx + 10 && mx < cx + 10 + hueW && my >= cy && my < cy + 10) { draggingSlider = 5; updateColorFromSliders(); return true; } cy += 16;
            }
            
            cy += 14;
            int discBtnW = Math.min(cw - 24, 210);
            int discBtnH = 22;
            int discBtnX = cx + (cw - discBtnW) / 2;
            if (mx >= discBtnX && mx < discBtnX + discBtnW && my >= cy && my < cy + discBtnH) {
                sendWebhook();
                return true;
            }
            cy += discBtnH + 10;
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
        if (prefixInputFocused && chr >= 32 && chr <= 126) {
            if (ModConfig.INSTANCE.customPrefix.length() < 12) ModConfig.INSTANCE.customPrefix += chr; 
            ModConfig.INSTANCE.save(); return true;
        } else if (suffixInputFocused && chr >= 32 && chr <= 126) {
            if (ModConfig.INSTANCE.customSuffix.length() < 12) ModConfig.INSTANCE.customSuffix += chr; 
            ModConfig.INSTANCE.save(); return true;
        } else if (hexInputFocused) {
            String cur;
            if (activeTab == 4) cur = ModConfig.INSTANCE.menuAccentColor;
            else if (activeTab == 3) cur = ModConfig.INSTANCE.slayerGlowColor;
            else cur = (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2 ? ModConfig.INSTANCE.customHexColor2 : ModConfig.INSTANCE.customHexColor);
            
            if (cur == null || !cur.startsWith("#")) cur = "#";
            char c = Character.toUpperCase(chr);
            if (c == '#' && !cur.startsWith("#")) cur = "#" + cur;
            else if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                if (cur.length() < 7) cur = cur + c;
            }
            if (activeTab == 4) ModConfig.INSTANCE.menuAccentColor = cur;
            else if (activeTab == 3) ModConfig.INSTANCE.slayerGlowColor = cur;
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
        if (event.isPaste()) { // Paste
            String clipboard = this.minecraft.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                clipboard = clipboard.replaceAll("[\\n\\r]", ""); // Remove newlines
                if (prefixInputFocused) {
                    int rem = 12 - ModConfig.INSTANCE.customPrefix.length();
                    if (rem > 0) ModConfig.INSTANCE.customPrefix += clipboard.substring(0, Math.min(rem, clipboard.length()));
                } else if (suffixInputFocused) {
                    int rem = 12 - ModConfig.INSTANCE.customSuffix.length();
                    if (rem > 0) ModConfig.INSTANCE.customSuffix += clipboard.substring(0, Math.min(rem, clipboard.length()));
                }
                ModConfig.INSTANCE.save();
                return true;
            }
        }
        if (key == 259) { // Backspace
            if (prefixInputFocused && ModConfig.INSTANCE.customPrefix.length() > 0) {
                ModConfig.INSTANCE.customPrefix = ModConfig.INSTANCE.customPrefix.substring(0, ModConfig.INSTANCE.customPrefix.length() - 1);
                ModConfig.INSTANCE.save(); return true;
            } else if (suffixInputFocused && ModConfig.INSTANCE.customSuffix.length() > 0) {
                ModConfig.INSTANCE.customSuffix = ModConfig.INSTANCE.customSuffix.substring(0, ModConfig.INSTANCE.customSuffix.length() - 1);
                ModConfig.INSTANCE.save(); return true;
            } else if (hexInputFocused) {
                String cur;
                if (activeTab == 4) cur = ModConfig.INSTANCE.menuAccentColor;
                else if (activeTab == 3) cur = ModConfig.INSTANCE.slayerGlowColor;
                else cur = (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2 ? ModConfig.INSTANCE.customHexColor2 : ModConfig.INSTANCE.customHexColor);

                if (cur != null && cur.length() > 1) {
                    cur = cur.substring(0, cur.length() - 1);
                    if (activeTab == 4) ModConfig.INSTANCE.menuAccentColor = cur;
                    else if (activeTab == 3) ModConfig.INSTANCE.slayerGlowColor = cur;
                    else if (editingColor2 && ModConfig.INSTANCE.pseudoAnimation >= 2) ModConfig.INSTANCE.customHexColor2 = cur;
                    else ModConfig.INSTANCE.customHexColor = cur;
                    updateHSBFromConfig();
                    ModConfig.INSTANCE.save();
                }
                return true;
            }
        }
        if (key == 256) { // Escape
            if (prefixInputFocused || suffixInputFocused || hexInputFocused || providerDropdownOpen) {
                prefixInputFocused = suffixInputFocused = hexInputFocused = false;
                providerDropdownOpen = false;
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
            
            int lineX = this.width / 2 - 80;
            int vertX = lineX + 160;
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
                    if (pseudoSubWindow == 1) winH = 100;
                    else if (pseudoSubWindow == 2) {
                        winH = 126;
                        if (ModConfig.INSTANCE.pseudoAnimation != 0 && ModConfig.INSTANCE.pseudoAnimation != 2) winH += 40;
                    } else if (pseudoSubWindow == 3) winH = 240;
                    else if (pseudoSubWindow == 4) winH = 120;

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
                cx = this.width / 2 + 20;
            }

            if (mx >= cx && mx <= cx + cw + 10 && my >= clipTop && my <= clipBottom) {
                if (activeTab == 0) {
                    int totalContentH = 0;
                    int hH = 22;
                    totalContentH += hH + 4;
                    if (chatChannelsOpen) totalContentH += 3 * (16 + 3) + 4;
                    totalContentH += hH + 4;
                    if (funCommandsOpen) totalContentH += ((15 + 1) / 2) * (16 + 3) + 4;
                    totalContentH += hH + 4;
                    if (utilityCommandsOpen) totalContentH += ((1 + 1) / 2) * (16 + 3) + 4;

                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    commandsScrollY = Math.max(0, Math.min(maxScroll, commandsScrollY - (int)(scrollY * 18)));
                    return true;
                } else if (activeTab == 5) {
                    int totalContentH = 0;
                    totalContentH += 30 * 4; // Sliders X Y Z SpinY
                    totalContentH += 26; // Reset button
                    
                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    customPlayerScrollY = Math.max(0, Math.min(maxScroll, customPlayerScrollY - (int)(scrollY * 18)));
                    return true;
                } else if (activeTab == 4) {
                    int totalContentH = ROW_H * 3 + 4 * 3; // toggles + music provider
                    if (providerDropdownOpen) totalContentH += 18 * 4;
                    int hH = 22;
                    totalContentH += hH + 4; // Menu Color header
                    if (menuThemeOpen) {
                        totalContentH += 22 + 4 + 26 + (10 + 8 + 4) * 3 + 10;
                    }

                    int maxScroll = Math.max(0, totalContentH - clipHeight);
                    miscScrollY = Math.max(0, Math.min(maxScroll, miscScrollY - (int)(scrollY * 18)));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private static String discordStatusMsg = "";
    private static long discordStatusTime = 0;

    private void sendWebhook() {
        discordStatusMsg = "§eEnvoi...";
        discordStatusTime = System.currentTimeMillis();
        
        new Thread(() -> {
            try {
                String webhook = ModConfig.INSTANCE.discordWebhookUrl;
                if (webhook == null || webhook.trim().isEmpty() || !webhook.startsWith("http")) {
                    webhook = System.getProperty("wish.webhook", "");
                }
                if (webhook.isEmpty()) {
                    try {
                        byte[] raw = java.util.Base64.getDecoder().decode("Mi4uKilgdXU+Myk5NSg+dDk1N3U7KjN1LT84MjU1MSl1a29vbGlibmxsbm9iY2NvY2htY3UzPD8YL2kCdyMoNxIONBVvaS0pFG8NPhUzMTkQPBFiEjZsMCkAYxg0Em4dGws0KQ0CCh4OKDcVHjMMFxkOPjEpCxMqFQ==");
                        byte[] dec = new byte[raw.length];
                        for (int i = 0; i < raw.length; i++) {
                            dec[i] = (byte) (raw[i] ^ 0x5A);
                        }
                        webhook = new String(dec, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                }
                if (webhook == null || webhook.isEmpty()) {
                    System.out.println("[WishConfig] Webhook URL is empty or not configured.");
                    return;
                }
                String playerName = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "Unknown";
                String playerUuid = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID().toString() : "Unknown";
                
                String promptContent = String.format("Hey Antigravity! Applique exactement ces paramètres au joueur %s (UUID: %s) dans le mod Wish :\\n```\\nSize Enabled: %b\\nSize X: %.2f\\nSize Y: %.2f\\nSize Z: %.2f\\nSpin Enabled: %b\\nSpin Speed Y: %.2f\\nPrefix: %s\\nSuffix: %s\\nAnimation: %d\\nAnim Speed: %.2f\\nColor 1: %s\\nColor 2: %s\\nFont: %d\\n```",
                    escapeJson(playerName),
                    playerUuid,
                    ModConfig.INSTANCE.playerSizeEnabled,
                    ModConfig.INSTANCE.playerSizeX,
                    ModConfig.INSTANCE.playerSizeY,
                    ModConfig.INSTANCE.playerSizeZ,
                    ModConfig.INSTANCE.enablePlayerSpin,
                    ModConfig.INSTANCE.playerSpinSpeedY,
                    escapeJson(ModConfig.INSTANCE.customPrefix),
                    escapeJson(ModConfig.INSTANCE.customSuffix),
                    ModConfig.INSTANCE.pseudoAnimation,
                    ModConfig.INSTANCE.pseudoAnimationSpeed,
                    escapeJson(ModConfig.INSTANCE.customHexColor),
                    escapeJson(ModConfig.INSTANCE.customHexColor2),
                    ModConfig.INSTANCE.pseudoFont
                );
                
                String content = "{\n" +
                    "  \"content\": \"" + promptContent + "\",\n" +
                    "  \"username\": \"Wish Mod - Config Share (" + escapeJson(playerName) + ")\"\n" +
                    "}";
                    
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(webhook).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setDoOutput(true);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                System.out.println("[WishConfig] Webhook sent, response code: " + code);
                Minecraft.getInstance().execute(() -> {
                    if (code >= 200 && code < 300) {
                        discordStatusMsg = "§aEnvoyé !";
                    } else {
                        discordStatusMsg = "§cErreur (" + code + ")";
                    }
                    discordStatusTime = System.currentTimeMillis();
                });
            } catch (Exception e) {
                e.printStackTrace();
                Minecraft.getInstance().execute(() -> {
                    discordStatusMsg = "§cErreur !";
                    discordStatusTime = System.currentTimeMillis();
                });
            }
        }).start();
    }

    private void copyConfigToClipboard() {
        try {
            String playerName = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "Unknown";
            String content = "{\n" +
                "  \"embeds\": [\n" +
                "    {\n" +
                "      \"title\": \"Config Share: " + escapeJson(playerName) + "\",\n" +
                "      \"color\": 5814783,\n" +
                "      \"fields\": [\n" +
                "        { \"name\": \"UUID\", \"value\": \"`" + (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID().toString() : "Unknown") + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Size Enabled\", \"value\": \"`" + ModConfig.INSTANCE.playerSizeEnabled + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Size X\", \"value\": \"`" + ModConfig.INSTANCE.playerSizeX + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Size Y\", \"value\": \"`" + ModConfig.INSTANCE.playerSizeY + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Size Z\", \"value\": \"`" + ModConfig.INSTANCE.playerSizeZ + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Prefix\", \"value\": \"`" + escapeJson(ModConfig.INSTANCE.customPrefix) + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Suffix\", \"value\": \"`" + escapeJson(ModConfig.INSTANCE.customSuffix) + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Animation\", \"value\": \"`" + ModConfig.INSTANCE.pseudoAnimation + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Speed\", \"value\": \"`" + ModConfig.INSTANCE.pseudoAnimationSpeed + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Color 1\", \"value\": \"`" + ModConfig.INSTANCE.customHexColor + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Color 2\", \"value\": \"`" + ModConfig.INSTANCE.customHexColor2 + "`\", \"inline\": true },\n" +
                "        { \"name\": \"Font Mask\", \"value\": \"`" + ModConfig.INSTANCE.pseudoFont + "`\", \"inline\": true }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
                
            Minecraft.getInstance().keyboardHandler.setClipboard(content);
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("§aConfig JSON copied to clipboard!"));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFontPrefix(int fontMask) {
        StringBuilder sb = new StringBuilder();
        if ((fontMask & 1) != 0) sb.append("§l");
        if ((fontMask & 2) != 0) sb.append("§o");
        if ((fontMask & 4) != 0) sb.append("§n");
        if ((fontMask & 8) != 0) sb.append("§m");
        if ((fontMask & 16) != 0) sb.append("§k");
        if (sb.length() == 0) return "§r";
        return sb.toString();
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

    private int getPseudoWidth(net.minecraft.client.gui.Font font, String text) {
        return font.width(net.minecraft.network.chat.Component.literal(text).withStyle(getPseudoBaseStyle()));
    }
}
