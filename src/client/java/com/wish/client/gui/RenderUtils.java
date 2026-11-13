package com.wish.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RenderUtils {

    private RenderUtils() {}
    
    public static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return; 
        }
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        r = Math.min(r, Math.min(w / 2, h / 2));
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        
        if (h > 2 * r) {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
        
        g.fill(x + r, y, x + w - r, y + r, color);
        g.fill(x + r, y + h - r, x + w - r, y + h, color);

        
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            
            g.fill(x + dx, y + i, x + r, y + i + 1, color);
            g.fill(x + w - r, y + i, x + w - dx, y + i + 1, color);

            
            int bi = h - 1 - i;
            g.fill(x + dx, y + bi, x + r, y + bi + 1, color);
            g.fill(x + w - r, y + bi, x + w - dx, y + bi + 1, color);
        }
    }

    
    public static void drawGradientOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int colorTop, int colorBottom) {
        if (w <= 0 || h <= 0 || ((colorTop >>> 24) == 0 && (colorBottom >>> 24) == 0)) return;

        if (r <= 0) {
            g.fill(x, y, x + w, y + 1, colorTop);
            g.fill(x, y + h - 1, x + w, y + h, colorBottom);
            if (h > 2) {
                if (colorTop == colorBottom) {
                    g.fill(x, y + 1, x + 1, y + h - 1, colorTop);
                    g.fill(x + w - 1, y + 1, x + w, y + h - 1, colorTop);
                } else {
                    g.fillGradient(x, y + 1, x + 1, y + h - 1, colorTop, colorBottom);
                    g.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, colorTop, colorBottom);
                }
            }
            return;
        }

        r = Math.min(r, Math.min(w / 2, h / 2));

        
        g.fill(x + r, y, x + w - r, y + 1, colorTop);
        g.fill(x + r, y + h - 1, x + w - r, y + h, colorBottom);

        
        if (h > 2 * r) {
            if (colorTop == colorBottom) {
                g.fill(x, y + r, x + 1, y + h - r, colorTop);
                g.fill(x + w - 1, y + r, x + w, y + h - r, colorTop);
            } else {
                int topMid = mixColors(colorTop, colorBottom, (float) r / h);
                int botMid = mixColors(colorTop, colorBottom, (float) (h - r) / h);
                g.fillGradient(x, y + r, x + 1, y + h - r, topMid, botMid);
                g.fillGradient(x + w - 1, y + r, x + w, y + h - r, topMid, botMid);
            }
        }

        
        boolean sameColor = (colorTop == colorBottom);
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            int colTop = sameColor ? colorTop : mixColors(colorTop, colorBottom, (float) i / h);
            int colBot = sameColor ? colorBottom : mixColors(colorTop, colorBottom, (float) (h - 1 - i) / h);

            
            g.fill(x + dx, y + i, x + dx + 1, y + i + 1, colTop);
            g.fill(x + w - 1 - dx, y + i, x + w - dx, y + i + 1, colTop);

            
            int bi = h - 1 - i;
            g.fill(x + dx, y + bi, x + dx + 1, y + bi + 1, colBot);
            g.fill(x + w - 1 - dx, y + bi, x + w - dx, y + bi + 1, colBot);
        }
    }

    
    public static void drawGlassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int bgColor, int borderColor) {
        fillRoundedRect(g, x, y, w, h, 4, bgColor);
        drawGradientOutline(g, x, y, w, h, 4, borderColor, borderColor);
    }

    public static int mixColors(int color1, int color2, float ratio) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        int a1 = (color1 >>> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >>> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int)(a1 * (1 - ratio) + a2 * ratio);
        int r = (int)(r1 * (1 - ratio) + r2 * ratio);
        int g = (int)(g1 * (1 - ratio) + g2 * ratio);
        int b = (int)(b1 * (1 - ratio) + b2 * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
