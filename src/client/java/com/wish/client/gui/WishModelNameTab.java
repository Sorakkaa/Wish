package com.wish.client.gui;

import com.wish.client.features.CustomModelManager;
import com.wish.client.features.ItemNameFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class WishModelNameTab {

    private static final int ROW_H = 20;
    private static final int COLOR_COLS = 8;
    private static final int COLOR_CELL_H = 18;
    private static final int COLOR_GAP = 2;
    private static final int FORMAT_CELL_H = 18;
    private static final int FORMAT_GAP = 3;
    private static final int SYMBOL_COLS = 11;
    private static final int SYMBOL_CELL_H = 18;
    private static final int SYMBOL_GAP = 2;

    private static final String[] FORMAT_LABELS = {
            "\u00A7l&l Bold",
            "\u00A7o&o Ital",
            "\u00A7n&n Und",
            "\u00A7m&m Str",
            "\u00A7f&k Magic",
            "\u00A7f&r Reset"
    };

    private final String targetKey;
    private final String basePlainName;

    private String nameDraft;
    private int cursorPos;
    private boolean inputFocused = false;
    private boolean allSelected = false;

    private String hoveredTitle = null;
    private String hoveredDesc = null;

    public WishModelNameTab(String targetKey, String rawTargetDisplayName) {
        this.targetKey = targetKey != null ? targetKey : "";
        String stripped = ItemNameFormatter.stripFormatting(rawTargetDisplayName).trim();
        this.basePlainName = stripped.isEmpty() ? "Item" : stripped;
        syncFromConfig();
    }

    public void syncFromConfig() {
        String saved = CustomModelManager.getNameOverride(targetKey);
        this.nameDraft = saved != null ? ItemNameFormatter.sanitizeInput(saved) : "";
        this.cursorPos = this.nameDraft.length();
        this.allSelected = false;
    }

    public boolean isCapturingKeyboard() {
        return inputFocused;
    }

    public String getHoveredTitle() {
        return hoveredTitle;
    }

    public String getHoveredDesc() {
        return hoveredDesc;
    }

    public void render(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int y,
            int w,
            int h,
            int mx,
            int my,
            int colAccent,
            int colAccentDim
    ) {
        hoveredTitle = null;
        hoveredDesc = null;

        RenderUtils.fillRoundedRect(g, x, y, w, h, 4, 0x00000000);
        RenderUtils.drawGradientOutline(g, x, y, w, h, 4, 0x44FFFFFF, 0x44FFFFFF);

        int inX = x + 10;
        int inW = w - 20;
        int curY = y + 8;

        renderNameInput(g, font, inX, curY, inW, ROW_H, mx, my, colAccent);
        curY += ROW_H + 5;

        renderLivePreviewBar(g, font, inX, curY, inW);
        curY += 23;

        g.text(font, "Colors & Formatting", inX, curY, 0xFFBBBBCC);
        curY += 11;

        int colorCellW = (inW - (COLOR_COLS - 1) * COLOR_GAP) / COLOR_COLS;
        for (int i = 0; i < ItemNameFormatter.COLOR_CODES.size(); i++) {
            ItemNameFormatter.ColorCodeEntry entry = ItemNameFormatter.COLOR_CODES.get(i);
            int col = i % COLOR_COLS;
            int row = i / COLOR_COLS;
            int cx = inX + col * (colorCellW + COLOR_GAP);
            int cy = curY + row * (COLOR_CELL_H + COLOR_GAP);
            boolean hover = isInside(mx, my, cx, cy, colorCellW, COLOR_CELL_H);

            RenderUtils.fillRoundedRect(g, cx, cy, colorCellW, COLOR_CELL_H, 3, hover ? 0x44484858 : 0x26181822);
            RenderUtils.drawGradientOutline(g, cx, cy, colorCellW, COLOR_CELL_H, 3,
                    hover ? colAccent : 0x44666677, 0x22222228);

            int dotY = cy + (COLOR_CELL_H - 8) / 2;
            RenderUtils.fillRoundedRect(g, cx + 4, dotY, 8, 8, 2, 0xFF000000 | entry.rgb());
            if (entry.code() == '0') {
                RenderUtils.drawGradientOutline(g, cx + 4, dotY, 8, 8, 2, 0xAA888888, 0xAA888888);
            }
            int codeCol = (entry.code() == '0') ? 0xFFCCCCCC : (0xFF000000 | entry.rgb());
            g.text(font, entry.token(), cx + 15, cy + 5, codeCol);

            if (hover) {
                hoveredTitle = entry.token() + " - " + entry.label();
                hoveredDesc = "Click to insert '" + entry.token() + "' at cursor.";
            }
        }
        curY += 2 * (COLOR_CELL_H + COLOR_GAP) + 2;

        int formatCount = ItemNameFormatter.FORMAT_CODES.size();
        int formatCellW = (inW - (formatCount - 1) * FORMAT_GAP) / formatCount;
        for (int i = 0; i < formatCount; i++) {
            ItemNameFormatter.FormatCodeEntry entry = ItemNameFormatter.FORMAT_CODES.get(i);
            int fx = inX + i * (formatCellW + FORMAT_GAP);
            boolean hover = isInside(mx, my, fx, curY, formatCellW, FORMAT_CELL_H);
            RenderUtils.fillRoundedRect(g, fx, curY, formatCellW, FORMAT_CELL_H, 3, hover ? 0x44484858 : 0x26181822);
            RenderUtils.drawGradientOutline(g, fx, curY, formatCellW, FORMAT_CELL_H, 3,
                    hover ? colAccent : 0x44666677, 0x22222228);

            String label = FORMAT_LABELS[i];
            g.text(font, label, fx + (formatCellW - font.width(label)) / 2, curY + 5, 0xFFFFFFFF);
            if (hover) {
                hoveredTitle = entry.token() + " - " + entry.label();
                hoveredDesc = "Click to insert '" + entry.token() + "' formatting code.";
            }
        }
        curY += FORMAT_CELL_H + 7;

        g.text(font, "Symbols & Stars (Dungeon / Master / SkyBlock)", inX, curY, 0xFFBBBBCC);
        curY += 11;

        int symCellW = (inW - (SYMBOL_COLS - 1) * SYMBOL_GAP) / SYMBOL_COLS;
        for (int i = 0; i < ItemNameFormatter.SYMBOLS.size(); i++) {
            ItemNameFormatter.SymbolEntry entry = ItemNameFormatter.SYMBOLS.get(i);
            int col = i % SYMBOL_COLS;
            int row = i / SYMBOL_COLS;
            int sx = inX + col * (symCellW + SYMBOL_GAP);
            int sy = curY + row * (SYMBOL_CELL_H + SYMBOL_GAP);
            boolean hover = isInside(mx, my, sx, sy, symCellW, SYMBOL_CELL_H);

            RenderUtils.fillRoundedRect(g, sx, sy, symCellW, SYMBOL_CELL_H, 3, hover ? colAccentDim : 0x26181822);
            RenderUtils.drawGradientOutline(g, sx, sy, symCellW, SYMBOL_CELL_H, 3,
                    hover ? colAccent : 0x44666677, 0x22222228);

            int textX = sx + (symCellW - font.width(entry.display())) / 2;
            g.text(font, entry.display(), textX, sy + 5, 0xFF000000 | entry.previewRgb());

            if (hover) {
                hoveredTitle = entry.display() + "  " + entry.label();
                hoveredDesc = "Click to insert '" + entry.token() + "' into item name.";
            }
        }
    }

    public boolean mouseClicked(int x, int y, int w, double mx, double my) {
        int inX = x + 10;
        int inW = w - 20;
        int curY = y + 8;

        if (isInside(mx, my, inX, curY, inW, ROW_H)) {
            inputFocused = true;
            allSelected = false;
            Font font = Minecraft.getInstance().font;
            int scrollX = computeInputScrollX(font, inW);
            int relX = Math.max(0, (int) mx - (inX + 6) + scrollX);
            String prefix = font.plainSubstrByWidth(nameDraft, relX);
            cursorPos = Math.max(0, Math.min(nameDraft.length(), prefix.length()));
            return true;
        }

        curY += ROW_H + 5 + 23 + 11;

        int colorCellW = (inW - (COLOR_COLS - 1) * COLOR_GAP) / COLOR_COLS;
        for (int i = 0; i < ItemNameFormatter.COLOR_CODES.size(); i++) {
            int col = i % COLOR_COLS;
            int row = i / COLOR_COLS;
            int cx = inX + col * (colorCellW + COLOR_GAP);
            int cy = curY + row * (COLOR_CELL_H + COLOR_GAP);
            if (isInside(mx, my, cx, cy, colorCellW, COLOR_CELL_H)) {
                insertCodeToken(ItemNameFormatter.COLOR_CODES.get(i).token());
                return true;
            }
        }
        curY += 2 * (COLOR_CELL_H + COLOR_GAP) + 2;

        int formatCount = ItemNameFormatter.FORMAT_CODES.size();
        int formatCellW = (inW - (formatCount - 1) * FORMAT_GAP) / formatCount;
        for (int i = 0; i < formatCount; i++) {
            int fx = inX + i * (formatCellW + FORMAT_GAP);
            if (isInside(mx, my, fx, curY, formatCellW, FORMAT_CELL_H)) {
                insertCodeToken(ItemNameFormatter.FORMAT_CODES.get(i).token());
                return true;
            }
        }
        curY += FORMAT_CELL_H + 7 + 11;

        int symCellW = (inW - (SYMBOL_COLS - 1) * SYMBOL_GAP) / SYMBOL_COLS;
        for (int i = 0; i < ItemNameFormatter.SYMBOLS.size(); i++) {
            int col = i % SYMBOL_COLS;
            int row = i / SYMBOL_COLS;
            int sx = inX + col * (symCellW + SYMBOL_GAP);
            int sy = curY + row * (SYMBOL_CELL_H + SYMBOL_GAP);
            if (isInside(mx, my, sx, sy, symCellW, SYMBOL_CELL_H)) {
                insertCodeToken(ItemNameFormatter.SYMBOLS.get(i).token());
                return true;
            }
        }

        inputFocused = false;
        allSelected = false;
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        char chr = (char) event.codepoint();
        if (inputFocused && chr >= 32 && chr != 127) {
            String base = allSelected ? "" : nameDraft;
            int pos = allSelected ? 0 : cursorPos;
            ItemNameFormatter.EditResult res = ItemNameFormatter.insertTokenAt(base, pos, String.valueOf(chr));
            updateDraft(res.text(), res.cursorPos());
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!inputFocused) {
            return false;
        }
        int key = event.key();

        if (event.isSelectAll()) {
            allSelected = !nameDraft.isEmpty();
            cursorPos = nameDraft.length();
            return true;
        }
        if (event.isCopy()) {
            if (!nameDraft.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(nameDraft);
            }
            return true;
        }
        if (event.isCut()) {
            if (!nameDraft.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(nameDraft);
                updateDraft("", 0);
            }
            return true;
        }
        if (event.isPaste()) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String base = allSelected ? "" : nameDraft;
                int pos = allSelected ? 0 : cursorPos;
                ItemNameFormatter.EditResult res = ItemNameFormatter.insertTokenAt(base, pos, clip);
                updateDraft(res.text(), res.cursorPos());
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (allSelected) {
                updateDraft("", 0);
            } else if (cursorPos > 0 && !nameDraft.isEmpty()) {
                String next = nameDraft.substring(0, cursorPos - 1) + nameDraft.substring(cursorPos);
                updateDraft(next, cursorPos - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (allSelected) {
                updateDraft("", 0);
            } else if (cursorPos < nameDraft.length()) {
                String next = nameDraft.substring(0, cursorPos) + nameDraft.substring(cursorPos + 1);
                updateDraft(next, cursorPos);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            allSelected = false;
            cursorPos = Math.max(0, cursorPos - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            allSelected = false;
            cursorPos = Math.min(nameDraft.length(), cursorPos + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            allSelected = false;
            cursorPos = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            allSelected = false;
            cursorPos = nameDraft.length();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            inputFocused = false;
            allSelected = false;
            return true;
        }
        return false;
    }

    private void insertCodeToken(String token) {
        allSelected = false;
        ItemNameFormatter.EditResult res = ItemNameFormatter.insertTokenAt(nameDraft, cursorPos, token);
        updateDraft(res.text(), res.cursorPos());
        inputFocused = true;
    }

    private void updateDraft(String nextText, int nextCursor) {
        nameDraft = ItemNameFormatter.sanitizeInput(nextText);
        cursorPos = Math.max(0, Math.min(nameDraft.length(), nextCursor));
        allSelected = false;
        CustomModelManager.setNameOverride(targetKey, nameDraft);
    }

    private int computeInputScrollX(Font font, int inputW) {
        if (nameDraft.isEmpty()) {
            return 0;
        }
        int safeCursor = Math.min(cursorPos, nameDraft.length());
        int cursorOffset = font.width(nameDraft.substring(0, safeCursor));
        int maxVisibleW = inputW - 14;
        return Math.max(0, cursorOffset - maxVisibleW + 12);
    }

    private void renderNameInput(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int y,
            int w,
            int h,
            int mx,
            int my,
            int colAccent
    ) {
        boolean hover = isInside(mx, my, x, y, w, h);
        RenderUtils.fillRoundedRect(g, x, y, w, h, 3, inputFocused ? 0x33181822 : 0x22101016);
        RenderUtils.drawGradientOutline(g, x, y, w, h, 3,
                inputFocused ? colAccent : (hover ? 0x88FFFFFF : 0x44FFFFFF), 0x11000000);

        g.enableScissor(x + 4, y + 1, x + w - 4, y + h - 1);
        if (nameDraft.isEmpty() && !inputFocused) {
            g.text(font, "Custom name (ex: &d&lHyperion &6\u272A\u272A\u272A\u272A\u272A)...", x + 6, y + 6, 0xFF777788);
        } else {
            int safeCursor = Math.min(cursorPos, nameDraft.length());
            int cursorOffset = font.width(nameDraft.substring(0, safeCursor));
            int scrollX = computeInputScrollX(font, w);
            int drawX = x + 6 - scrollX;
            if (allSelected && !nameDraft.isEmpty()) {
                int selW = Math.min(w - 10, font.width(nameDraft));
                g.fill(x + 5, y + 4, x + 5 + selW, y + h - 4, 0x553388FF);
            }
            g.text(font, nameDraft, drawX, y + 6, 0xFFFFFFFF);
            if (inputFocused && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
                g.fill(drawX + cursorOffset, y + 4, drawX + cursorOffset + 1, y + h - 4, 0xFFFFFFFF);
            }
        }
        g.disableScissor();
    }

    private void renderLivePreviewBar(GuiGraphicsExtractor g, Font font, int x, int y, int w) {
        RenderUtils.fillRoundedRect(g, x, y, w, 18, 3, 0x3314141E);
        RenderUtils.drawGradientOutline(g, x, y, w, 18, 3, 0x33FFFFFF, 0x11FFFFFF);

        String prefix = "Preview: ";
        int prefixW = font.width(prefix);
        g.text(font, prefix, x + 7, y + 5, 0xFF9999AA);

        Component liveComp = CustomModelManager.getCompiledNameComponent(nameDraft);
        int areaX = x + 7 + prefixW;
        int areaEnd = x + w - 7;
        g.enableScissor(areaX, y + 1, areaEnd, y + 17);
        if (liveComp != null) {
            g.text(font, liveComp, areaX, y + 5, 0xFFFFFFFF);
        } else {
            g.text(font, "(default: " + basePlainName + ")", areaX, y + 5, 0xFF666677);
        }
        g.disableScissor();
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
