package com.wish.client.gui;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class EditLagHudScreen extends Screen {

    private final Screen parent;
    private int draggingId = -1; // 0 = Lag HUD, 1 = Boss HUD
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private final String mockText = "1000ms";
    private String mockBossLine1 = "PlayerName";
    private String mockBossLine2 = "§lBoss spawn";

    public EditLagHudScreen(Screen parent) {
        super(Component.literal("Edit HUDs"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        
        g.fill(0, 0, this.width, this.height, 0x44000000);

        int colLine = 0x22FFFFFF;
        int colCenterLine = 0x44FFFFFF;

        int midX = this.width / 2;
        int midY = this.height / 2;
        g.fill(midX, 0, midX + 1, this.height, colCenterLine);
        g.fill(0, midY, this.width, midY + 1, colCenterLine);

        g.fill(midX / 2, 0, midX / 2 + 1, this.height, colLine);
        g.fill(midX + midX / 2, 0, midX + midX / 2 + 1, this.height, colLine);
        g.fill(0, midY / 2, this.width, midY / 2 + 1, colLine);
        g.fill(0, midY + midY / 2, this.width, midY + midY / 2 + 1, colLine);
        
        var font = Minecraft.getInstance().font;
        String hint = "Drag to move. Scroll wheel to resize (scale). ESC to save.";
        int hintW = font.width(hint);
        g.text(font, hint, (this.width - hintW) / 2, 20, 0xFFFFFF);

        renderHudBox(g, font, mx, my, ModConfig.INSTANCE.lagHudX, ModConfig.INSTANCE.lagHudY, ModConfig.INSTANCE.lagHudScale, mockText, null, 0);
        renderHudBox(g, font, mx, my, ModConfig.INSTANCE.bossHudX, ModConfig.INSTANCE.bossHudY, ModConfig.INSTANCE.bossHudScale, mockBossLine1, mockBossLine2, 1);
    }
    
    private void renderHudBox(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my, int x, int y, float scale, String text1, String text2, int id) {
        if (scale <= 0.1f) scale = 1.0f;
        
        int w1 = font.width(text1);
        int w2 = text2 == null ? 0 : font.width(text2);
        int cardW = Math.max(w1, w2);
        int cardH = text2 == null ? font.lineHeight : (font.lineHeight * 2 + 2);
        
        int textW = (int) (cardW * scale);
        int textH = (int) (cardH * scale);
        
        boolean centered = (id == 1);
        int leftX = centered ? x - textW / 2 : x;
        
        boolean hovering = mx >= leftX - 2 && mx <= leftX + textW + 2 && my >= y - 2 && my <= y + textH + 2;

        if (hovering || draggingId == id) {
            int colHudGuide = 0x33AAAAAA;
            g.fill(leftX, 0, leftX + 1, g.guiHeight(), colHudGuide);
            g.fill(leftX + textW, 0, leftX + textW + 1, g.guiHeight(), colHudGuide);
            g.fill(0, y, g.guiWidth(), y + 1, colHudGuide);
            g.fill(0, y + textH, g.guiWidth(), y + textH + 1, colHudGuide);

            g.fill(leftX - 2, y - 2, leftX + textW + 2, y + textH + 2, 0x44FFFFFF);
        }

        g.pose().pushMatrix();
        g.pose().translate(leftX, y);
        g.pose().scale(scale, scale);

        if (id == 1) {
            g.text(font, text1, (cardW - w1) / 2, 0, 0xFFFFAA00);
            g.text(font, text2, (cardW - w2) / 2, font.lineHeight + 2, 0xFFFF5555);
        } else {
            g.text(font, text1, 0, 0, 0xFFFF5555);
        }
        
        g.pose().popMatrix();

        if (hovering || draggingId == id) {
            String scaleHint = String.format(java.util.Locale.US, "Scale: %.1fx", scale);
            g.text(font, scaleHint, leftX, y + textH + 4, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            double mx = Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getScreenWidth();
            double my = Minecraft.getInstance().mouseHandler.ypos() * (double)this.height / (double)Minecraft.getInstance().getWindow().getScreenHeight();
            
            var font = Minecraft.getInstance().font;
            int hoveredId = getHoveredId(mx, my, font);
            
            if (hoveredId != -1) {
                float scale = 1.0f;
                if (hoveredId == 0) scale = ModConfig.INSTANCE.lagHudScale;
                else if (hoveredId == 1) scale = ModConfig.INSTANCE.bossHudScale;
                
                if (scale <= 0.1f) scale = 1.0f;
                scale += (scrollY > 0) ? 0.1f : -0.1f;
                if (scale < 0.2f) scale = 0.2f;
                if (scale > 4.0f) scale = 4.0f;
                scale = Math.round(scale * 10.0f) / 10.0f;
                
                if (hoveredId == 0) ModConfig.INSTANCE.lagHudScale = scale;
                else if (hoveredId == 1) ModConfig.INSTANCE.bossHudScale = scale;
                
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    private int getHoveredId(double mx, double my, net.minecraft.client.gui.Font font) {
        float s1 = ModConfig.INSTANCE.bossHudScale; if (s1 <= 0.1f) s1 = 1.0f;
        int x1 = ModConfig.INSTANCE.bossHudX; int y1 = ModConfig.INSTANCE.bossHudY;
        int w1 = font.width(mockBossLine1);
        int w2 = font.width(mockBossLine2);
        int bossW = (int)(Math.max(w1, w2) * s1);
        int bossH = (int)((font.lineHeight * 2 + 2) * s1);
        int leftX1 = x1 - bossW / 2;
        if (mx >= leftX1 - 2 && mx <= leftX1 + bossW + 2 && my >= y1 - 2 && my <= y1 + bossH + 2) return 1;
        
        float s0 = ModConfig.INSTANCE.lagHudScale; if (s0 <= 0.1f) s0 = 1.0f;
        int x0 = ModConfig.INSTANCE.lagHudX; int y0 = ModConfig.INSTANCE.lagHudY;
        int lagW = (int)(font.width(mockText) * s0);
        int lagH = (int)(font.lineHeight * s0);
        if (mx >= x0 - 2 && mx <= x0 + lagW + 2 && my >= y0 - 2 && my <= y0 + lagH + 2) return 0;

        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mx = Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getScreenWidth();
        double my = Minecraft.getInstance().mouseHandler.ypos() * (double)this.height / (double)Minecraft.getInstance().getWindow().getScreenHeight();
        
        var font = Minecraft.getInstance().font;
        int hoveredId = getHoveredId(mx, my, font);
        
        if (hoveredId != -1) {
            draggingId = hoveredId;
            int x = 0; int y = 0;
            if (hoveredId == 0) { x = ModConfig.INSTANCE.lagHudX; y = ModConfig.INSTANCE.lagHudY; }
            else if (hoveredId == 1) { x = ModConfig.INSTANCE.bossHudX; y = ModConfig.INSTANCE.bossHudY; }
            dragOffsetX = (int) (mx - x);
            dragOffsetY = (int) (my - y);
            return true;
        }
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingId != -1) {
            double mx = Minecraft.getInstance().mouseHandler.xpos() * (double)this.width / (double)Minecraft.getInstance().getWindow().getScreenWidth();
            double my = Minecraft.getInstance().mouseHandler.ypos() * (double)this.height / (double)Minecraft.getInstance().getWindow().getScreenHeight();
            
            if (draggingId == 0) {
                ModConfig.INSTANCE.lagHudX = (int) (mx - dragOffsetX);
                ModConfig.INSTANCE.lagHudY = (int) (my - dragOffsetY);
            } else if (draggingId == 1) {
                ModConfig.INSTANCE.bossHudX = (int) (mx - dragOffsetX);
                ModConfig.INSTANCE.bossHudY = (int) (my - dragOffsetY);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingId != -1) {
            draggingId = -1;
            ModConfig.INSTANCE.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}