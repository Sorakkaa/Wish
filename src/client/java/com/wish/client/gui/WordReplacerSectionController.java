package com.wish.client.gui;

import com.wish.client.config.ModConfig;
import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class WordReplacerSectionController {
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\\r\\n]");

    private static final int COMPOSER_BOX_H = 28;
    private static final int NOTE_BOX_H = 143;
    private static final int RULE_ROW_HEIGHT = 22;
    private static final int RULE_ROW_STRIDE = 26;
    private static final int EMPTY_ROW_HEIGHT = 20;

    private static final Component[] COLOR_NOTES_LEFT = {
            colorNote("&0", "Black", ChatFormatting.BLACK),
            colorNote("&2", "Dark Green", ChatFormatting.DARK_GREEN),
            colorNote("&4", "Dark Red", ChatFormatting.DARK_RED),
            colorNote("&6", "Gold", ChatFormatting.GOLD),
            colorNote("&8", "Dark Gray", ChatFormatting.DARK_GRAY),
            colorNote("&a", "Green", ChatFormatting.GREEN),
            colorNote("&c", "Red", ChatFormatting.RED),
            colorNote("&e", "Yellow", ChatFormatting.YELLOW)
    };

    private static final Component[] COLOR_NOTES_RIGHT = {
            colorNote("&1", "Dark Blue", ChatFormatting.DARK_BLUE),
            colorNote("&3", "Dark Aqua", ChatFormatting.DARK_AQUA),
            colorNote("&5", "Dark Purple", ChatFormatting.DARK_PURPLE),
            colorNote("&7", "Gray", ChatFormatting.GRAY),
            colorNote("&9", "Blue", ChatFormatting.BLUE),
            colorNote("&b", "Aqua", ChatFormatting.AQUA),
            colorNote("&d", "Light Purple", ChatFormatting.LIGHT_PURPLE),
            colorNote("&f", "White", ChatFormatting.WHITE)
    };

    private static final Component[] FORMAT_NOTES_LEFT = {
            Component.literal("&k = ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Magic").withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withObfuscated(true)))
                    .append(Component.literal(" (Obf)").withStyle(ChatFormatting.GRAY)),
            formatNote("&m", "Strikethrough", Style.EMPTY.withColor(ChatFormatting.WHITE).withStrikethrough(true)),
            formatNote("&o", "Italic", Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(true))
    };

    private static final Component[] FORMAT_NOTES_RIGHT = {
            formatNote("&l", "Bold", Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(true)),
            formatNote("&n", "Underline", Style.EMPTY.withColor(ChatFormatting.WHITE).withUnderlined(true)),
            formatNote("&r", "Reset", Style.EMPTY.withColor(ChatFormatting.WHITE))
    };

    private static Component colorNote(String code, String label, ChatFormatting color) {
        return Component.literal(code + " = ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(label).withStyle(color));
    }

    private static Component formatNote(String code, String label, Style labelStyle) {
        return Component.literal(code + " = ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(label).withStyle(labelStyle));
    }

    private record FittedPreviewKey(String rawTo, int maxWidth) {
    }

    private record StyledLeaf(String text, Style style) {
    }

    private final Map<String, Component> previewCache = new HashMap<>();
    private final Map<FittedPreviewKey, Component> fittedPreviewCache = new HashMap<>();

    private String cachedToInputText = null;
    private boolean cachedToInputCaret = false;
    private int cachedToInputMaxW = -1;
    private Component cachedToInputComponent = Component.empty();

    private boolean sectionOpen = false;
    private String fromInput = "";
    private String toInput = "";
    private boolean fromFocused = false;
    private boolean toFocused = false;
    private int editingRuleIndex = -1;

    private String hoveredTooltipTitle = null;
    private String hoveredTooltipDesc = null;

    boolean isOpen() {
        return sectionOpen;
    }

    boolean isInputFocused() {
        return fromFocused || toFocused;
    }

    void clearFocus() {
        fromFocused = false;
        toFocused = false;
    }

    void resetTransientState() {
        clearFocus();
        cancelEditing();
    }

    String getHoveredTooltipTitle() {
        return hoveredTooltipTitle;
    }

    String getHoveredTooltipDesc() {
        return hoveredTooltipDesc;
    }

    int getContentHeight(int headerH, int rowH) {
        int h = headerH + 4;
        if (!sectionOpen) {
            return h;
        }
        h += (rowH + 4) * 2;
        h += COMPOSER_BOX_H + 4;
        h += NOTE_BOX_H + 4;
        int ruleCount = WordReplacerManager.getRules().size();
        h += ruleCount == 0 ? (EMPTY_ROW_HEIGHT + 4) : (ruleCount * RULE_ROW_STRIDE);
        return h;
    }

    int renderHeader(
            GuiGraphicsExtractor g,
            Font font,
            int cx,
            int cy,
            int cw,
            int hH,
            int mx,
            int my,
            int clipTop,
            int clipBottom,
            int colHover,
            int colAccent,
            int colText,
            int colOff
    ) {
        hoveredTooltipTitle = null;
        hoveredTooltipDesc = null;

        boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + hH && my >= clipTop && my <= clipBottom;
        int hBg = sectionOpen ? colHover : (hover ? 0x44444455 : 0x2A2A2E55);
        RenderUtils.fillRoundedRect(g, cx, cy, cw, hH, 4, hBg);
        RenderUtils.drawGradientOutline(g, cx, cy, cw, hH, 4, sectionOpen ? colAccent : 0x55888888, 0x22444444);
        g.text(font, (sectionOpen ? "▼ " : "▶ ") + "Word Replacer", cx + 8, cy + 7, colText);

        boolean state = WordReplacerManager.isEnabled();
        int dotSize = 8;
        int dotX = cx + cw - dotSize - 10;
        int dotY = cy + 7;
        boolean dotHover = mx >= dotX - 4 && mx < dotX + dotSize + 4 && my >= dotY - 4 && my < dotY + dotSize + 4 && hover;
        int dotCol = state ? (dotHover ? 0xFFBB00FF : colAccent) : (dotHover ? 0xFFFF5566 : colOff);
        RenderUtils.fillRoundedRect(g, dotX, dotY, dotSize, dotSize, 4, dotCol);
        RenderUtils.drawGradientOutline(g, dotX, dotY, dotSize, dotSize, 4, dotHover ? 0xAAFFFFFF : 0x44FFFFFF, 0x11000000);

        if (hover) {
            hoveredTooltipTitle = "Word Replacer";
            hoveredTooltipDesc = "Replaces words client-side in chat, tab, scoreboard, nametags, and tooltips using & colors/formats.";
        }
        return cy + hH + 4;
    }

    int renderBody(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int cy,
            int w,
            int mx,
            int my,
            int clipTop,
            int clipBottom,
            int colAccent,
            int colText,
            int colTextDim,
            int colOff
    ) {
        syncEditingBounds();
        cy = renderNoteCard(g, font, x, cy, w, mx, my, clipTop, clipBottom, colText);
        cy = renderComposerRow(g, font, x, cy, w, mx, my, clipTop, clipBottom, colAccent, colText, colTextDim);

        List<ModConfig.WordReplacementRule> rules = WordReplacerManager.getRules();
        if (rules.isEmpty()) {
            RenderUtils.fillRoundedRect(g, x, cy, w, EMPTY_ROW_HEIGHT, 4, 0x18FFFFFF);
            g.text(font, "No rules yet. Add one above.", x + 8, cy + 6, colTextDim);
            return cy + EMPTY_ROW_HEIGHT + 4;
        }

        for (int i = 0; i < rules.size(); i++) {
            ModConfig.WordReplacementRule rule = rules.get(i);
            if (rule != null) {
                cy = renderRuleRow(g, font, i, rule, x, cy, w, mx, my, clipTop, clipBottom, colAccent, colText, colTextDim, colOff);
            }
        }
        return cy;
    }

    private int renderComposerRow(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int cy,
            int w,
            int mx,
            int my,
            int clipTop,
            int clipBottom,
            int colAccent,
            int colText,
            int colTextDim
    ) {
        boolean isEditing = editingRuleIndex >= 0;
        RenderUtils.fillRoundedRect(g, x, cy, w, COMPOSER_BOX_H, 4, isEditing ? 0x2E241A33 : 0x1EFFFFFF);
        RenderUtils.drawGradientOutline(g, x, cy, w, COMPOSER_BOX_H, 4, isEditing ? colAccent : 0x44FFFFFF, 0x11000000);

        ComposerLayout layout = computeComposerLayout(x, cy, w, isEditing);
        boolean caretVisible = (System.currentTimeMillis() / 500) % 2 == 0;

        renderInputField(
                g, font, layout.fromX, layout.fieldY, layout.fromW, layout.fieldH,
                fromInput, "From...", fromFocused, caretVisible, false, colAccent, colText, colTextDim
        );
        g.text(font, "->", layout.fromX + layout.fromW + 3, cy + 10, colTextDim);
        renderInputField(
                g, font, layout.toX, layout.fieldY, layout.toW, layout.fieldH,
                toInput, "To (&b, &l...)", toFocused, caretVisible, true, colAccent, colText, colTextDim
        );

        boolean hoverAction = isInside(mx, my, layout.actionX, layout.fieldY, layout.actionW, layout.fieldH, clipTop, clipBottom);
        int actionFill = hoverAction ? colAccent : (isEditing ? 0x997744CC : 0x664488AA);
        RenderUtils.fillRoundedRect(g, layout.actionX, layout.fieldY, layout.actionW, layout.fieldH, 3, actionFill);
        RenderUtils.drawGradientOutline(g, layout.actionX, layout.fieldY, layout.actionW, layout.fieldH, 3, hoverAction ? 0xFFFFFFFF : 0x55FFFFFF, 0x11000000);
        String actionLabel = isEditing ? "Save" : "+ Add";
        g.text(font, actionLabel, layout.actionX + (layout.actionW - font.width(actionLabel)) / 2, layout.fieldY + 6, colText);

        if (isEditing) {
            boolean hoverCancel = isInside(mx, my, layout.cancelX, layout.fieldY, layout.cancelW, layout.fieldH, clipTop, clipBottom);
            RenderUtils.fillRoundedRect(g, layout.cancelX, layout.fieldY, layout.cancelW, layout.fieldH, 3, hoverCancel ? 0xCC993344 : 0x6644222A);
            RenderUtils.drawGradientOutline(g, layout.cancelX, layout.fieldY, layout.cancelW, layout.fieldH, 3, hoverCancel ? 0xFFFF8899 : 0x44FFFFFF, 0x11000000);
            String cancelLabel = "X";
            g.text(font, cancelLabel, layout.cancelX + (layout.cancelW - font.width(cancelLabel)) / 2, layout.fieldY + 6, colText);
            if (hoverCancel) {
                hoveredTooltipTitle = "Cancel Edit";
                hoveredTooltipDesc = "Discard changes and exit edit mode.";
            }
        }

        if (hoverAction) {
            hoveredTooltipTitle = isEditing ? "Save Rule" : "Add Rule";
            hoveredTooltipDesc = isEditing
                    ? "Apply the edited From and To values."
                    : "Adds a rule replacing 'From' with 'To' (supports & color and format codes).";
        }
        return cy + COMPOSER_BOX_H + 4;
    }

    private void renderInputField(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int y,
            int w,
            int h,
            String value,
            String placeholder,
            boolean focused,
            boolean caretVisible,
            boolean styledPreview,
            int colAccent,
            int colText,
            int colTextDim
    ) {
        RenderUtils.fillRoundedRect(g, x, y, w, h, 3, focused ? 0x481C1C2A : 0x2E14141E);
        RenderUtils.drawGradientOutline(g, x, y, w, h, 3, focused ? colAccent : 0x55777777, 0x22000000);
        if (value.isEmpty() && !focused) {
            String display = fitTextWithEllipsis(font, placeholder, w - 8);
            g.text(font, display, x + 4, y + 6, colTextDim);
            return;
        }
        boolean showCaret = focused && caretVisible;
        if (styledPreview) {
            Component display = getCachedToInputPreview(font, value, showCaret, w - 8);
            g.text(font, display, x + 4, y + 6, colText);
            return;
        }
        String display = tailFitText(font, showCaret ? value + "_" : value, w - 8);
        g.text(font, display, x + 4, y + 6, colText);
    }

    private int renderNoteCard(
            GuiGraphicsExtractor g,
            Font font,
            int x,
            int cy,
            int w,
            int mx,
            int my,
            int clipTop,
            int clipBottom,
            int colText
    ) {
        RenderUtils.fillRoundedRect(g, x, cy, w, NOTE_BOX_H, 5, 0x624A5468);
        RenderUtils.drawGradientOutline(g, x, cy, w, NOTE_BOX_H, 5, 0x55FFFFFF, 0x18FFFFFF);
        g.text(font, "Formatting Notes", x + 8, cy + 5, colText);
        g.fill(x + 8, cy + 16, x + w - 8, cy + 17, 0x88CC4444);

        int leftX = x + 10;
        int rightX = x + w / 2 + 4;
        for (int i = 0; i < COLOR_NOTES_LEFT.length; i++) {
            int rowY = cy + 20 + i * 10;
            g.text(font, COLOR_NOTES_LEFT[i], leftX, rowY, colText);
            g.text(font, COLOR_NOTES_RIGHT[i], rightX, rowY, colText);
        }

        g.fill(x + 8, cy + 102, x + w - 8, cy + 103, 0x88CC4444);
        for (int i = 0; i < FORMAT_NOTES_LEFT.length; i++) {
            int rowY = cy + 106 + i * 10;
            g.text(font, FORMAT_NOTES_LEFT[i], leftX, rowY, colText);
            g.text(font, FORMAT_NOTES_RIGHT[i], rightX, rowY, colText);
        }
        g.fill(x + 8, cy + 137, x + w - 8, cy + 138, 0x88CC4444);

        if (isInside(mx, my, x, cy, w, NOTE_BOX_H, clipTop, clipBottom)) {
            hoveredTooltipTitle = "Formatting Codes";
            hoveredTooltipDesc = "Type any & code in 'To' (e.g. &b&lStar -> Aqua Bold Star).";
        }
        return cy + NOTE_BOX_H + 4;
    }

    private int renderRuleRow(
            GuiGraphicsExtractor g,
            Font font,
            int index,
            ModConfig.WordReplacementRule rule,
            int x,
            int cy,
            int w,
            int mx,
            int my,
            int clipTop,
            int clipBottom,
            int colAccent,
            int colText,
            int colTextDim,
            int colOff
    ) {
        boolean isBeingEdited = (editingRuleIndex == index);
        boolean rowHover = isInside(mx, my, x, cy, w, RULE_ROW_HEIGHT, clipTop, clipBottom);

        int bg = isBeingEdited ? 0x44362244 : (rowHover ? 0x36FFFFFF : 0x22FFFFFF);
        int border = isBeingEdited ? colAccent : (rowHover ? 0x55FFFFFF : 0x22FFFFFF);
        RenderUtils.fillRoundedRect(g, x, cy, w, RULE_ROW_HEIGHT, 4, bg);
        RenderUtils.drawGradientOutline(g, x, cy, w, RULE_ROW_HEIGHT, 4, border, 0x11000000);

        int dotCol = rule.enabled ? colAccent : colOff;
        RenderUtils.fillRoundedRect(g, x + 7, cy + 7, 8, 8, 4, dotCol);

        int delSize = 14;
        int delX = x + w - delSize - 6;
        int delY = cy + 4;
        int editW = 28;
        int editH = 14;
        int editX = delX - editW - 4;
        int editY = cy + 4;

        renderRulePreviewText(g, font, rule, x + 20, cy + 7, Math.max(24, editX - (x + 26)), colText, colTextDim);

        boolean editHover = isInside(mx, my, editX - 1, editY - 1, editW + 2, editH + 2, clipTop, clipBottom);
        int editBg = isBeingEdited ? colAccent : (editHover ? 0x996655AA : 0x44444455);
        RenderUtils.fillRoundedRect(g, editX, editY, editW, editH, 3, editBg);
        RenderUtils.drawGradientOutline(g, editX, editY, editW, editH, 3, isBeingEdited || editHover ? 0xAAFFFFFF : 0x33FFFFFF, 0x11000000);
        g.text(font, "Edit", editX + (editW - font.width("Edit")) / 2, editY + 3, colText);

        boolean delHover = isInside(mx, my, delX - 2, delY - 2, delSize + 4, delSize + 4, clipTop, clipBottom);
        RenderUtils.fillRoundedRect(g, delX, delY, delSize, delSize, 3, delHover ? 0xCCFF4455 : 0x44FFFFFF);
        g.text(font, "x", delX + 4, delY + 3, colText);

        if (delHover) {
            hoveredTooltipTitle = "Delete Rule";
            hoveredTooltipDesc = "Remove this word replacement rule.";
        } else if (editHover) {
            hoveredTooltipTitle = isBeingEdited ? "Cancel Editing" : "Edit Rule";
            hoveredTooltipDesc = isBeingEdited
                    ? "Stop editing this rule."
                    : "Load this rule above to modify its word or replacement.";
        } else if (rowHover) {
            hoveredTooltipTitle = "Toggle Rule";
            hoveredTooltipDesc = rule.enabled ? "Click row to disable this rule." : "Click row to enable this rule.";
        }
        return cy + RULE_ROW_STRIDE;
    }

    private void renderRulePreviewText(
            GuiGraphicsExtractor g,
            Font font,
            ModConfig.WordReplacementRule rule,
            int textX,
            int textY,
            int availW,
            int colText,
            int colTextDim
    ) {
        String fromLabel = rule.from != null ? rule.from : "";
        String arrow = " -> ";
        int arrowW = font.width(arrow);
        int fromMaxW = Math.max(24, (availW - arrowW) / 2);
        String fittedFrom = fitTextWithEllipsis(font, fromLabel, fromMaxW);
        int fromW = font.width(fittedFrom);
        int fromColor = rule.enabled ? colText : colTextDim;
        g.text(font, fittedFrom, textX, textY, fromColor);
        g.text(font, arrow, textX + fromW, textY, colTextDim);

        int toX = textX + fromW + arrowW;
        int toMaxW = Math.max(0, availW - fromW - arrowW);
        if (toMaxW <= 0) {
            return;
        }
        Component preview = getCachedPreviewComponent(rule.to);
        if (preview.getString().isEmpty()) {
            g.text(font, "(empty)", toX, textY, colTextDim);
            return;
        }
        if (font.width(preview) <= toMaxW) {
            g.text(font, preview, toX, textY, fromColor);
            return;
        }
        Component fitted = getCachedFittedPreviewComponent(font, rule.to, preview, toMaxW);
        g.text(font, fitted, toX, textY, fromColor);
    }

    private Component getCachedPreviewComponent(String rawTo) {
        String key = rawTo != null ? rawTo : "";
        Component cached = previewCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (previewCache.size() > WordReplacerManager.MAX_RULES * 2) {
            previewCache.clear();
        }
        Component built = WordReplacerManager.buildPreviewComponent(key);
        previewCache.put(key, built);
        return built;
    }

    private Component getCachedFittedPreviewComponent(Font font, String rawTo, Component fullPreview, int maxWidth) {
        FittedPreviewKey key = new FittedPreviewKey(rawTo != null ? rawTo : "", maxWidth);
        Component cached = fittedPreviewCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (fittedPreviewCache.size() > WordReplacerManager.MAX_RULES * 2) {
            fittedPreviewCache.clear();
        }
        Component fitted = fitComponentWithEllipsis(font, fullPreview, maxWidth);
        fittedPreviewCache.put(key, fitted);
        return fitted;
    }

    private Component getCachedToInputPreview(Font font, String value, boolean showCaret, int maxWidth) {
        if (value.equals(cachedToInputText) && showCaret == cachedToInputCaret && maxWidth == cachedToInputMaxW) {
            return cachedToInputComponent;
        }
        Component full = WordReplacerManager.buildEditorPreviewComponent(value, showCaret);
        Component fitted = tailFitComponent(font, full, maxWidth);
        cachedToInputText = value;
        cachedToInputCaret = showCaret;
        cachedToInputMaxW = maxWidth;
        cachedToInputComponent = fitted;
        return fitted;
    }

    boolean handleHeaderClick(double mx, double my, int cx, int cy, int cw, int hH, int clipTop, int clipBottom) {
        if (my < clipTop || my > clipBottom) {
            return false;
        }
        int dotSize = 8;
        int dotX = cx + cw - dotSize - 10;
        int dotY = cy + 7;
        if (mx >= dotX - 4 && mx < dotX + dotSize + 4 && my >= dotY - 4 && my < dotY + dotSize + 4) {
            WordReplacerManager.setEnabled(!WordReplacerManager.isEnabled());
            return true;
        }
        if (mx >= cx && mx < cx + cw && my >= cy && my < cy + hH) {
            sectionOpen = !sectionOpen;
            if (!sectionOpen) {
                resetTransientState();
            }
            return true;
        }
        return false;
    }

    boolean handleBodyClick(
            double mx,
            double my,
            int button,
            int x,
            int cy,
            int w,
            int clipTop,
            int clipBottom,
            Runnable onInteract
    ) {
        if (my < clipTop || my > clipBottom) {
            return false;
        }
        syncEditingBounds();
        if (mx >= x && mx < x + w && my >= cy && my < cy + NOTE_BOX_H) {
            if (button != 0) {
                return false;
            }
            if (onInteract != null) {
                onInteract.run();
            }
            clearFocus();
            return true;
        }
        cy += NOTE_BOX_H + 4;

        boolean isEditing = editingRuleIndex >= 0;
        ComposerLayout layout = computeComposerLayout(x, cy, w, isEditing);

        if (mx >= x && mx < x + w && my >= cy && my < cy + COMPOSER_BOX_H) {
            if (button != 0) {
                return false;
            }
            if (onInteract != null) {
                onInteract.run();
            }
            if (mx >= layout.fromX && mx < layout.fromX + layout.fromW && my >= layout.fieldY && my < layout.fieldY + layout.fieldH) {
                fromFocused = true;
                toFocused = false;
                return true;
            }
            if (mx >= layout.toX && mx < layout.toX + layout.toW && my >= layout.fieldY && my < layout.fieldY + layout.fieldH) {
                fromFocused = false;
                toFocused = true;
                return true;
            }
            if (mx >= layout.actionX && mx < layout.actionX + layout.actionW && my >= layout.fieldY && my < layout.fieldY + layout.fieldH) {
                submitRule();
                return true;
            }
            if (isEditing && mx >= layout.cancelX && mx < layout.cancelX + layout.cancelW && my >= layout.fieldY && my < layout.fieldY + layout.fieldH) {
                cancelEditing();
                clearFocus();
                return true;
            }
            clearFocus();
            return true;
        }

        cy += COMPOSER_BOX_H + 4;
        return handleRuleListClick(mx, my, button, x, cy, w, onInteract);
    }

    private boolean handleRuleListClick(double mx, double my, int button, int x, int cy, int w, Runnable onInteract) {
        List<ModConfig.WordReplacementRule> rules = WordReplacerManager.getRules();
        if (rules.isEmpty() || button != 0) {
            return false;
        }
        int delSize = 14;
        int editW = 28;
        for (int i = 0; i < rules.size(); i++) {
            if (mx >= x && mx < x + w && my >= cy && my < cy + RULE_ROW_HEIGHT) {
                if (onInteract != null) {
                    onInteract.run();
                }
                int delX = x + w - delSize - 6;
                int delY = cy + 4;
                int editX = delX - editW - 4;
                int editY = cy + 4;

                if (mx >= delX - 2 && mx < delX + delSize + 2 && my >= delY - 2 && my < delY + delSize + 2) {
                    handleRuleRemoved(i);
                    WordReplacerManager.removeRuleAt(i);
                } else if (mx >= editX - 1 && mx < editX + editW + 1 && my >= editY - 1 && my < editY + 15) {
                    toggleEditRule(i, rules.get(i));
                } else {
                    WordReplacerManager.toggleRuleAt(i);
                }
                return true;
            }
            cy += RULE_ROW_STRIDE;
        }
        return false;
    }

    private void toggleEditRule(int index, ModConfig.WordReplacementRule rule) {
        if (editingRuleIndex == index) {
            cancelEditing();
            clearFocus();
            return;
        }
        if (rule == null) {
            return;
        }
        editingRuleIndex = index;
        fromInput = rule.from != null ? rule.from : "";
        toInput = rule.to != null ? rule.to : "";
        fromFocused = false;
        toFocused = true;
    }

    private void handleRuleRemoved(int removedIndex) {
        if (editingRuleIndex == removedIndex) {
            cancelEditing();
        } else if (editingRuleIndex > removedIndex) {
            editingRuleIndex--;
        }
    }

    private void cancelEditing() {
        editingRuleIndex = -1;
        fromInput = "";
        toInput = "";
    }

    private void syncEditingBounds() {
        if (editingRuleIndex >= WordReplacerManager.getRules().size()) {
            editingRuleIndex = -1;
        }
    }

    boolean handleCharTyped(char chr) {
        if (!isInputFocused()) {
            return false;
        }
        if (chr < 32 || chr == 127 || Character.isISOControl(chr)) {
            return false;
        }
        if (fromFocused) {
            if (fromInput.length() < WordReplacerManager.MAX_TERM_LENGTH) {
                fromInput += chr;
            }
            return true;
        }
        if (toFocused) {
            if (toInput.length() < WordReplacerManager.MAX_REPLACEMENT_LENGTH) {
                toInput += chr;
            }
            return true;
        }
        return false;
    }

    boolean handleKeyPressed(KeyEvent event, Minecraft mc) {
        if (!isInputFocused()) {
            if (editingRuleIndex >= 0 && event.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelEditing();
                return true;
            }
            return false;
        }
        int key = event.key();
        if (event.isPaste()) {
            String clipboard = mc.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                String clean = NEWLINE_PATTERN.matcher(clipboard).replaceAll("");
                appendToFocused(clean);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (fromFocused && !fromInput.isEmpty()) {
                fromInput = fromInput.substring(0, fromInput.length() - 1);
            } else if (toFocused && !toInput.isEmpty()) {
                toInput = toInput.substring(0, toInput.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean wasFrom = fromFocused;
            fromFocused = !wasFrom;
            toFocused = wasFrom;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (fromFocused) {
                fromFocused = false;
                toFocused = true;
            } else {
                submitRule();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (editingRuleIndex >= 0) {
                cancelEditing();
            }
            clearFocus();
            return true;
        }
        return false;
    }

    private void appendToFocused(String text) {
        if (text.isEmpty()) {
            return;
        }
        if (fromFocused) {
            int rem = WordReplacerManager.MAX_TERM_LENGTH - fromInput.length();
            if (rem > 0) {
                fromInput += text.substring(0, Math.min(rem, text.length()));
            }
        } else if (toFocused) {
            int rem = WordReplacerManager.MAX_REPLACEMENT_LENGTH - toInput.length();
            if (rem > 0) {
                toInput += text.substring(0, Math.min(rem, text.length()));
            }
        }
    }

    private void submitRule() {
        if (editingRuleIndex >= 0) {
            if (WordReplacerManager.updateRule(editingRuleIndex, fromInput, toInput)) {
                cancelEditing();
                clearFocus();
            }
            return;
        }
        if (WordReplacerManager.addRule(fromInput, toInput)) {
            fromInput = "";
            toInput = "";
            fromFocused = true;
            toFocused = false;
        }
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h, int clipTop, int clipBottom) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my <= clipBottom;
    }

    private static ComposerLayout computeComposerLayout(int x, int cy, int w, boolean isEditing) {
        int pad = 4;
        int arrowW = 14;
        int gap = 4;
        int fieldH = 20;
        int fieldY = cy + 4;
        int actionW = isEditing ? 34 : 42;
        int cancelW = isEditing ? 18 : 0;
        int cancelGap = isEditing ? gap : 0;
        int rightButtonsW = actionW + cancelGap + cancelW;
        int fieldsAvail = Math.max(60, w - pad * 2 - arrowW - gap * 2 - rightButtonsW);
        int fromW = Math.max(28, (int) (fieldsAvail * 0.36f));
        int toW = Math.max(28, fieldsAvail - fromW);
        int fromX = x + pad;
        int toX = fromX + fromW + arrowW;
        int actionX = toX + toW + gap;
        int cancelX = actionX + actionW + cancelGap;
        return new ComposerLayout(fromX, toX, actionX, cancelX, fieldY, fieldH, fromW, toW, actionW, cancelW);
    }

    private record ComposerLayout(
            int fromX,
            int toX,
            int actionX,
            int cancelX,
            int fieldY,
            int fieldH,
            int fromW,
            int toW,
            int actionW,
            int cancelW
    ) {
    }

    private static String fitTextWithEllipsis(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisW = font.width("...");
        if (maxWidth <= ellipsisW) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxWidth - ellipsisW) + "...";
    }

    private static String tailFitText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth, true);
    }

    private static List<StyledLeaf> collectStyledLeaves(Component comp) {
        List<StyledLeaf> leaves = new ArrayList<>(4);
        comp.visit((style, text) -> {
            if (!text.isEmpty()) {
                leaves.add(new StyledLeaf(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return leaves;
    }

    private static Component fitComponentWithEllipsis(Font font, Component comp, int maxWidth) {
        if (font.width(comp) <= maxWidth) {
            return comp;
        }
        int ellipsisW = font.width("...");
        if (maxWidth <= ellipsisW) {
            return Component.empty();
        }
        int remaining = maxWidth - ellipsisW;
        MutableComponent out = Component.empty();
        for (StyledLeaf leaf : collectStyledLeaves(comp)) {
            Component leafComp = Component.literal(leaf.text()).withStyle(leaf.style());
            int w = font.width(leafComp);
            if (w <= remaining) {
                out.append(leafComp);
                remaining -= w;
                continue;
            }
            int end = maxPrefixIndexWithinWidth(font, leaf.text(), leaf.style(), remaining);
            if (end > 0) {
                out.append(Component.literal(leaf.text().substring(0, end)).withStyle(leaf.style()));
            }
            break;
        }
        out.append(Component.literal("..."));
        return out;
    }

    private static Component tailFitComponent(Font font, Component comp, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (font.width(comp) <= maxWidth) {
            return comp;
        }
        List<StyledLeaf> leaves = collectStyledLeaves(comp);
        List<Component> keptReversed = new ArrayList<>(leaves.size());
        int remaining = maxWidth;
        for (int i = leaves.size() - 1; i >= 0; i--) {
            StyledLeaf leaf = leaves.get(i);
            Component leafComp = Component.literal(leaf.text()).withStyle(leaf.style());
            int w = font.width(leafComp);
            if (w <= remaining) {
                keptReversed.add(leafComp);
                remaining -= w;
                continue;
            }
            int start = minSuffixStartWithinWidth(font, leaf.text(), leaf.style(), remaining);
            if (start < leaf.text().length()) {
                keptReversed.add(Component.literal(leaf.text().substring(start)).withStyle(leaf.style()));
            }
            break;
        }
        MutableComponent out = Component.empty();
        for (int i = keptReversed.size() - 1; i >= 0; i--) {
            out.append(keptReversed.get(i));
        }
        return out;
    }

    private static int maxPrefixIndexWithinWidth(Font font, String text, Style style, int maxWidth) {
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (font.width(Component.literal(text.substring(0, mid)).withStyle(style)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (lo > 0 && lo < text.length() && Character.isHighSurrogate(text.charAt(lo - 1))) {
            lo--;
        }
        return lo;
    }

    private static int minSuffixStartWithinWidth(Font font, String text, Style style, int maxWidth) {
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (font.width(Component.literal(text.substring(mid)).withStyle(style)) <= maxWidth) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        if (lo > 0 && lo < text.length() && Character.isLowSurrogate(text.charAt(lo))) {
            lo++;
        }
        return lo;
    }
}
