package com.wish.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.wish.client.color.ColorUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColorWheelPicker {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-ColorWheel");

    public static final int DRAG_NONE = 0;
    public static final int DRAG_WHEEL = 1;
    public static final int DRAG_BRIGHTNESS = 2;
    public static final int DRAG_ALPHA = 3;

    public static final int CLICK_NONE = 0;
    public static final int CLICK_HEX = 1;
    public static final int CLICK_WHEEL = 2;
    public static final int CLICK_BRIGHTNESS = 3;
    public static final int CLICK_ALPHA = 4;

    private static final int PAD = 8;
    private static final int WHEEL_SIZE = 86;
    private static final int HEX_GAP = 5;
    private static final int HEX_H = 16;
    private static final int WHEEL_BAR_GAP = 10;
    private static final int BAR_W = 14;
    private static final int BAR_GAP = 8;
    private static final int BAR_H = WHEEL_SIZE;
    private static final int TEX_SIZE = 172;
    private static final float KNOB_TRACK_RADIUS = (WHEEL_SIZE * 0.5f) - 3.0f;

    public static final int WIDGET_W = PAD + WHEEL_SIZE + WHEEL_BAR_GAP + BAR_W + BAR_GAP + BAR_W + PAD;
    public static final int WIDGET_H = PAD + WHEEL_SIZE + HEX_GAP + HEX_H + PAD;

    private static final Identifier WHEEL_TEX_ID = Identifier.fromNamespaceAndPath("wish", "color_wheel");
    private static final String[] PCT_LABELS = buildPercentLabels();

    private static DynamicTexture wheelTexture = null;
    private static boolean textureInitFailed = false;

    private ColorWheelPicker() {
    }

    private static String[] buildPercentLabels() {
        String[] labels = new String[101];
        for (int i = 0; i <= 100; i++) {
            labels[i] = Integer.toString(i);
        }
        return labels;
    }

    public static int computeCardX(int containerX, int containerW) {
        return containerX + Math.max(0, (containerW - WIDGET_W) / 2);
    }

    public static String render(
            GuiGraphicsExtractor g,
            Font font,
            int containerX,
            int containerY,
            int containerW,
            float hue,
            float sat,
            float val,
            float alpha,
            boolean supportsAlpha,
            String hexValue,
            boolean hexFocused,
            int accentColor,
            int mx,
            int my
    ) {
        int cardX = computeCardX(containerX, containerW);
        int cardY = containerY;

        int wheelX = cardX + PAD;
        int wheelY = cardY + PAD;
        drawWheel(g, wheelX, wheelY, hue, sat, val);

        int hexY = wheelY + WHEEL_SIZE + HEX_GAP;
        drawHexBox(g, font, wheelX, hexY, WHEEL_SIZE, HEX_H, hexValue, hexFocused, accentColor, mx, my);

        int briX = wheelX + WHEEL_SIZE + WHEEL_BAR_GAP;
        int briTopRgb = ColorUtils.hsbToRgb(hue, sat, 1.0f);
        drawVerticalBar(g, font, briX, wheelY, briTopRgb, 0xFF000000, val, false, true);

        int alphaX = briX + BAR_W + BAR_GAP;
        int selectedOpaqueArgb = ColorUtils.hsbToRgb(hue, sat, val);
        float shownAlpha = supportsAlpha ? clamp01(alpha) : 1.0f;
        drawVerticalBar(g, font, alphaX, wheelY, selectedOpaqueArgb, selectedOpaqueArgb & 0x00FFFFFF, shownAlpha, true, supportsAlpha);

        if (!supportsAlpha && mx >= alphaX && mx < alphaX + BAR_W && my >= wheelY && my < wheelY + BAR_H) {
            return "Only 3D Lyrics supports opacity; name colors and entity glow use solid RGB.";
        }
        return null;
    }

    public static int handleClick(
            int containerX,
            int containerY,
            int containerW,
            int mx,
            int my,
            boolean supportsAlpha,
            float[] outHsbAlpha
    ) {
        int cardX = computeCardX(containerX, containerW);
        int wheelX = cardX + PAD;
        int wheelY = containerY + PAD;

        int hexY = wheelY + WHEEL_SIZE + HEX_GAP;
        if (mx >= wheelX && mx < wheelX + WHEEL_SIZE && my >= hexY && my < hexY + HEX_H) {
            return CLICK_HEX;
        }

        float wheelCenterX = wheelX + (WHEEL_SIZE * 0.5f);
        float wheelCenterY = wheelY + (WHEEL_SIZE * 0.5f);
        float wheelRadius = WHEEL_SIZE * 0.5f;
        float dx = mx - wheelCenterX;
        float dy = my - wheelCenterY;
        if (Math.hypot(dx, dy) <= wheelRadius + 2.0f) {
            outHsbAlpha[0] = ColorUtils.wheelOffsetToHue(dx, dy);
            outHsbAlpha[1] = ColorUtils.wheelOffsetToSaturation(dx, dy, KNOB_TRACK_RADIUS);
            return CLICK_WHEEL;
        }

        int briX = wheelX + WHEEL_SIZE + WHEEL_BAR_GAP;
        if (mx >= briX - 1 && mx < briX + BAR_W + 1 && my >= wheelY - 1 && my < wheelY + BAR_H + 1) {
            outHsbAlpha[2] = computeBarValueFromY(my, wheelY);
            return CLICK_BRIGHTNESS;
        }

        if (supportsAlpha) {
            int alphaX = briX + BAR_W + BAR_GAP;
            if (mx >= alphaX - 1 && mx < alphaX + BAR_W + 1 && my >= wheelY - 1 && my < wheelY + BAR_H + 1) {
                outHsbAlpha[3] = computeBarValueFromY(my, wheelY);
                return CLICK_ALPHA;
            }
        }
        return CLICK_NONE;
    }

    public static boolean handleDrag(
            int dragMode,
            int containerX,
            int containerY,
            int containerW,
            int mx,
            int my,
            boolean supportsAlpha,
            float[] outHsbAlpha
    ) {
        if (dragMode == DRAG_NONE) {
            return false;
        }
        int cardX = computeCardX(containerX, containerW);
        int wheelX = cardX + PAD;
        int wheelY = containerY + PAD;

        if (dragMode == DRAG_WHEEL) {
            float dx = mx - (wheelX + (WHEEL_SIZE * 0.5f));
            float dy = my - (wheelY + (WHEEL_SIZE * 0.5f));
            outHsbAlpha[0] = ColorUtils.wheelOffsetToHue(dx, dy);
            outHsbAlpha[1] = ColorUtils.wheelOffsetToSaturation(dx, dy, KNOB_TRACK_RADIUS);
            return true;
        }
        if (dragMode == DRAG_BRIGHTNESS) {
            outHsbAlpha[2] = computeBarValueFromY(my, wheelY);
            return true;
        }
        if (dragMode == DRAG_ALPHA && supportsAlpha) {
            outHsbAlpha[3] = computeBarValueFromY(my, wheelY);
            return true;
        }
        return false;
    }

    private static float computeBarValueFromY(int my, int barY) {
        float t = 1.0f - ((my - (barY + 1)) / (float) Math.max(1, BAR_H - 2));
        return clamp01(t);
    }

    private static void drawWheel(GuiGraphicsExtractor g, int wheelX, int wheelY, float hue, float sat, float val) {
        if (ensureWheelTexture()) {
            g.blit(RenderPipelines.GUI_TEXTURED, WHEEL_TEX_ID, wheelX, wheelY, 0.0f, 0.0f,
                    WHEEL_SIZE, WHEEL_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        } else {
            RenderUtils.fillRoundedRect(g, wheelX, wheelY, WHEEL_SIZE, WHEEL_SIZE, WHEEL_SIZE / 2, 0xFF33333A);
        }

        float centerX = wheelX + (WHEEL_SIZE * 0.5f);
        float centerY = wheelY + (WHEEL_SIZE * 0.5f);
        int handleX = Math.round(centerX + ColorUtils.hsbToWheelX(hue, sat, KNOB_TRACK_RADIUS));
        int handleY = Math.round(centerY + ColorUtils.hsbToWheelY(hue, sat, KNOB_TRACK_RADIUS));
        int handleFill = ColorUtils.hsbToRgb(hue, sat, val);

        RenderUtils.fillRoundedRect(g, handleX - 5, handleY - 5, 10, 10, 5, 0xFF000000);
        RenderUtils.fillRoundedRect(g, handleX - 4, handleY - 4, 8, 8, 4, 0xFFFFFFFF);
        RenderUtils.fillRoundedRect(g, handleX - 3, handleY - 3, 6, 6, 3, handleFill);
    }

    private static void drawHexBox(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int y,
            int w,
            int h,
            String hexValue,
            boolean focused,
            int accentColor,
            int mx,
            int my
    ) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int border = focused ? accentColor : (hover ? 0xFFFFFFFF : 0xFFCCCCCC);
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xAA08080E);

        String displayHex = (hexValue != null && !hexValue.isEmpty()) ? hexValue : "#";
        String cursor = (focused && ((System.currentTimeMillis() / 400L) & 1L) == 0L) ? "_" : "";
        String shown = displayHex + cursor;
        int maxTextW = w - 10;
        if (font.width(shown) > maxTextW) {
            shown = font.plainSubstrByWidth(shown, maxTextW);
        }
        g.text(font, shown, x + 5, y + 4, 0xFFFFFFFF);
    }

    private static void drawVerticalBar(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int y,
            int topColor,
            int bottomColor,
            float value,
            boolean checkerboard,
            boolean active
    ) {
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF000000);
        int innerX = x + 1;
        int innerY = y + 1;
        int innerX2 = x + BAR_W - 1;
        int innerY2 = y + BAR_H - 1;

        if (checkerboard) {
            drawCheckerboard(g, innerX, innerY, innerX2, innerY2);
        }
        g.fillGradient(innerX, innerY, innerX2, innerY2, topColor, bottomColor);

        if (!active) {
            g.fill(innerX, innerY, innerX2, innerY2, 0x44000000);
        }

        float clamped = clamp01(value);
        int indicatorY = innerY + Math.round((1.0f - clamped) * ((innerY2 - innerY) - 1));
        g.fill(innerX, indicatorY, innerX2, indicatorY + 1, 0xFF000000);
        if (!checkerboard && clamped < 0.25f && indicatorY > innerY) {
            g.fill(innerX, indicatorY - 1, innerX2, indicatorY, 0x88FFFFFF);
        }

        int pct = Math.max(0, Math.min(100, Math.round(clamped * 100.0f)));
        String label = PCT_LABELS[pct];
        int labelCol = active ? 0xFFCCCCCC : 0xFF777780;
        g.text(font, label, x + (BAR_W / 2) - (font.width(label) / 2), y + BAR_H + 7, labelCol);
    }

    private static void drawCheckerboard(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        int tile = 4;
        for (int py = y1; py < y2; py += tile) {
            int row = (py - y1) / tile;
            int ty2 = Math.min(y2, py + tile);
            for (int px = x1; px < x2; px += tile) {
                int col = (px - x1) / tile;
                int tx2 = Math.min(x2, px + tile);
                int c = (((row + col) & 1) == 0) ? 0xFFC8C8CE : 0xFF888890;
                g.fill(px, py, tx2, ty2, c);
            }
        }
    }

    private static boolean ensureWheelTexture() {
        if (wheelTexture != null) {
            return true;
        }
        if (textureInitFailed) {
            return false;
        }
        NativeImage image = null;
        try {
            image = generateWheelImage();
            wheelTexture = new DynamicTexture(() -> "Wish Color Wheel", image);
            image = null;
            Minecraft.getInstance().getTextureManager().register(WHEEL_TEX_ID, wheelTexture);
            return true;
        } catch (Exception e) {
            if (image != null) {
                image.close();
            }
            textureInitFailed = true;
            LOGGER.error("[Wish] Failed to build color wheel texture", e);
            return false;
        }
    }

    private static NativeImage generateWheelImage() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        float half = TEX_SIZE * 0.5f;
        float outerRadius = half - 1.5f;
        float colorRadius = half - 4.0f;

        for (int py = 0; py < TEX_SIZE; py++) {
            float dy = (py + 0.5f) - half;
            for (int px = 0; px < TEX_SIZE; px++) {
                float dx = (px + 0.5f) - half;
                float dist = (float) Math.hypot(dx, dy);

                if (dist > outerRadius + 0.8f) {
                    image.setPixelABGR(px, py, 0);
                    continue;
                }

                float hue = ColorUtils.wheelOffsetToHue(dx, dy);
                float sat = Math.min(1.0f, dist / colorRadius);
                int rgb = ColorUtils.hsbToRgb(hue, sat, 1.0f);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (dist > colorRadius - 1.0f) {
                    float borderBlend = clamp01((dist - (colorRadius - 1.0f)) / 1.4f);
                    r = Math.round(r * (1.0f - borderBlend) + 10.0f * borderBlend);
                    g = Math.round(g * (1.0f - borderBlend) + 10.0f * borderBlend);
                    b = Math.round(b * (1.0f - borderBlend) + 14.0f * borderBlend);
                }

                float edgeAlpha = dist <= outerRadius ? 1.0f : clamp01(1.0f - (dist - outerRadius) / 0.8f);
                int a = Math.round(edgeAlpha * 255.0f);
                int abgr = ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
                image.setPixelABGR(px, py, abgr);
            }
        }
        return image;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
