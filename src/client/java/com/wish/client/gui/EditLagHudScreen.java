package com.wish.client.gui;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class EditLagHudScreen extends Screen {

    private final Screen parent;
    private int draggingId = -1; 
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private final String mockText = "1000ms";
    private final String[] mockAutoLines = {
        "Automaton Tracker",
        "Kills: 45 (180/h)",
        "Profit: 1.25M (5.00M/h)",
        "Timer: 15m 00s"
    };

    public EditLagHudScreen(Screen parent) {
        super(Component.literal("Edit HUDs"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);

        // Process active dragging directly in render loop for instant responsiveness
        if (draggingId != -1) {
            long window = Minecraft.getInstance().getWindow().handle();
            if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                if (draggingId == 0) {
                    ModConfig.INSTANCE.lagHudX = (int) (mx - dragOffsetX);
                    ModConfig.INSTANCE.lagHudY = (int) (my - dragOffsetY);
                } else if (draggingId == 2) {
                    ModConfig.INSTANCE.automatonHudX = (int) (mx - dragOffsetX);
                    ModConfig.INSTANCE.automatonHudY = (int) (my - dragOffsetY);
                }
            } else {
                draggingId = -1;
                setDragging(false);
                ModConfig.INSTANCE.save();
            }
        }
        
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

        renderHudBox(g, font, mx, my, ModConfig.INSTANCE.lagHudX, ModConfig.INSTANCE.lagHudY, ModConfig.INSTANCE.lagHudScale, mockText, 0);
        renderAutoHudBox(g, font, mx, my, ModConfig.INSTANCE.automatonHudX, ModConfig.INSTANCE.automatonHudY, ModConfig.INSTANCE.automatonHudScale);
    }
    
    private void renderHudBox(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my, int x, int y, float scale, String text, int id) {
        if (scale <= 0.1f) scale = 1.0f;
        
        int cardW = font.width(text);
        int cardH = font.lineHeight;
        
        int textW = (int) (cardW * scale);
        int textH = (int) (cardH * scale);
        int leftX = x;
        
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

        g.text(font, text, 0, 0, 0xFFFF5555);
        
        g.pose().popMatrix();

        if (hovering || draggingId == id) {
            String scaleHint = String.format(java.util.Locale.US, "Scale: %.1fx", scale);
            g.text(font, scaleHint, leftX, y + textH + 4, 0xFFAAAAAA);
        }
    }

    private void renderAutoHudBox(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my, int x, int y, float scale) {
        if (scale <= 0.1f) scale = 1.0f;
        int maxW = 0;
        for (String s : mockAutoLines) {
            maxW = Math.max(maxW, font.width(s));
        }
        int cardW = maxW + 4;
        int cardH = mockAutoLines.length * font.lineHeight + (mockAutoLines.length - 1);
        int textW = (int) (cardW * scale);
        int textH = (int) (cardH * scale);
        int leftX = x;

        boolean hovering = mx >= leftX - 2 && mx <= leftX + textW + 2 && my >= y - 2 && my <= y + textH + 2;
        if (hovering || draggingId == 2) {
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

        int curY = 0;
        g.text(font, mockAutoLines[0], 0, curY, 0xFFFFAA00);
        curY += font.lineHeight + 1;
        g.text(font, mockAutoLines[1], 0, curY, 0xFF55FF55);
        curY += font.lineHeight + 1;
        g.text(font, mockAutoLines[2], 0, curY, 0xFFFFAA00);
        curY += font.lineHeight + 1;
        g.text(font, mockAutoLines[3], 0, curY, 0xFF55FFFF);

        g.pose().popMatrix();

        if (hovering || draggingId == 2) {
            String scaleHint = String.format(java.util.Locale.US, "Scale: %.1fx", scale);
            g.text(font, scaleHint, leftX, y + textH + 4, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            var font = Minecraft.getInstance().font;
            int hoveredId = getHoveredId(mouseX, mouseY, font);
            
            if (hoveredId != -1) {
                float scale = 1.0f;
                if (hoveredId == 0) scale = ModConfig.INSTANCE.lagHudScale;
                else if (hoveredId == 2) scale = ModConfig.INSTANCE.automatonHudScale;
                
                if (scale <= 0.1f) scale = 1.0f;
                scale += (scrollY > 0) ? 0.1f : -0.1f;
                if (scale < 0.2f) scale = 0.2f;
                if (scale > 4.0f) scale = 4.0f;
                scale = Math.round(scale * 10.0f) / 10.0f;
                
                if (hoveredId == 0) ModConfig.INSTANCE.lagHudScale = scale;
                else if (hoveredId == 2) ModConfig.INSTANCE.automatonHudScale = scale;
                
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    private int getHoveredId(double mx, double my, net.minecraft.client.gui.Font font) {
        float s2 = ModConfig.INSTANCE.automatonHudScale; if (s2 <= 0.1f) s2 = 1.0f;
        int x2 = ModConfig.INSTANCE.automatonHudX; int y2 = ModConfig.INSTANCE.automatonHudY;
        int maxW2 = 0;
        for (String s : mockAutoLines) maxW2 = Math.max(maxW2, font.width(s));
        int autoW = (int)((maxW2 + 4) * s2);
        int autoH = (int)((mockAutoLines.length * font.lineHeight + (mockAutoLines.length - 1)) * s2);
        if (mx >= x2 - 2 && mx <= x2 + autoW + 2 && my >= y2 - 2 && my <= y2 + autoH + 2) return 2;

        float s0 = ModConfig.INSTANCE.lagHudScale; if (s0 <= 0.1f) s0 = 1.0f;
        int x0 = ModConfig.INSTANCE.lagHudX; int y0 = ModConfig.INSTANCE.lagHudY;
        int lagW = (int)(font.width(mockText) * s0);
        int lagH = (int)(font.lineHeight * s0);
        if (mx >= x0 - 2 && mx <= x0 + lagW + 2 && my >= y0 - 2 && my <= y0 + lagH + 2) return 0;

        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (event == null || event.button() != 0) return super.mouseClicked(event, focused);
        double mx = event.x();
        double my = event.y();
        
        var font = Minecraft.getInstance().font;
        int hoveredId = getHoveredId(mx, my, font);
        
        if (hoveredId != -1) {
            draggingId = hoveredId;
            setFocused(this);
            setDragging(true);
            int x = 0; int y = 0;
            if (hoveredId == 0) { x = ModConfig.INSTANCE.lagHudX; y = ModConfig.INSTANCE.lagHudY; }
            else if (hoveredId == 2) { x = ModConfig.INSTANCE.automatonHudX; y = ModConfig.INSTANCE.automatonHudY; }
            dragOffsetX = (int) (mx - x);
            dragOffsetY = (int) (my - y);
            return true;
        }
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingId != -1 && event != null) {
            double mx = event.x();
            double my = event.y();
            
            if (draggingId == 0) {
                ModConfig.INSTANCE.lagHudX = (int) (mx - dragOffsetX);
                ModConfig.INSTANCE.lagHudY = (int) (my - dragOffsetY);
            } else if (draggingId == 2) {
                ModConfig.INSTANCE.automatonHudX = (int) (mx - dragOffsetX);
                ModConfig.INSTANCE.automatonHudY = (int) (my - dragOffsetY);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingId != -1) {
            draggingId = -1;
            setDragging(false);
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
