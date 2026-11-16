package com.wish.client.features;

import com.wish.client.color.ColorUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

public final class ItemNameFormatter {

    public static final int MAX_NAME_LENGTH = 256;
    public static final int DEFAULT_RGB = 0xFFFFFF;

    public record FormatSpan(
            String text,
            int rgb,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated
    ) {}

    public record ColorCodeEntry(char code, String token, String label, int rgb) {}

    public record FormatCodeEntry(char code, String token, String label) {}

    public record SymbolEntry(String display, String token, String label, int previewRgb) {}

    public record EditResult(String text, int cursorPos) {}

    public static final List<ColorCodeEntry> COLOR_CODES = List.of(
            new ColorCodeEntry('0', "&0", "Black", 0x000000),
            new ColorCodeEntry('1', "&1", "Dark Blue", 0x0000AA),
            new ColorCodeEntry('2', "&2", "Dark Green", 0x00AA00),
            new ColorCodeEntry('3', "&3", "Dark Aqua", 0x00AAAA),
            new ColorCodeEntry('4', "&4", "Dark Red", 0xAA0000),
            new ColorCodeEntry('5', "&5", "Dark Purple", 0xAA00AA),
            new ColorCodeEntry('6', "&6", "Gold", 0xFFAA00),
            new ColorCodeEntry('7', "&7", "Gray", 0xAAAAAA),
            new ColorCodeEntry('8', "&8", "Dark Gray", 0x555555),
            new ColorCodeEntry('9', "&9", "Blue", 0x5555FF),
            new ColorCodeEntry('a', "&a", "Green", 0x55FF55),
            new ColorCodeEntry('b', "&b", "Aqua", 0x55FFFF),
            new ColorCodeEntry('c', "&c", "Red", 0xFF5555),
            new ColorCodeEntry('d', "&d", "Light Purple", 0xFF55FF),
            new ColorCodeEntry('e', "&e", "Yellow", 0xFFFF55),
            new ColorCodeEntry('f', "&f", "White", 0xFFFFFF)
    );

    public static final List<FormatCodeEntry> FORMAT_CODES = List.of(
            new FormatCodeEntry('l', "&l", "Bold"),
            new FormatCodeEntry('o', "&o", "Italic"),
            new FormatCodeEntry('n', "&n", "Underline"),
            new FormatCodeEntry('m', "&m", "Strike"),
            new FormatCodeEntry('k', "&k", "Obfuscated"),
            new FormatCodeEntry('r', "&r", "Reset")
    );

    public static final List<SymbolEntry> SYMBOLS = List.of(
            new SymbolEntry("\u272A", "\u272A", "Dungeon Star", 0xFFAA00),
            new SymbolEntry("\u278A", "\u278A", "Master Star 1", 0xFF5555),
            new SymbolEntry("\u278B", "\u278B", "Master Star 2", 0xFF5555),
            new SymbolEntry("\u278C", "\u278C", "Master Star 3", 0xFF5555),
            new SymbolEntry("\u278D", "\u278D", "Master Star 4", 0xFF5555),
            new SymbolEntry("\u278E", "\u278E", "Master Star 5", 0xFF5555),
            new SymbolEntry("\u269A", "\u269A", "Fragged / Upgraded", 0xFFAA00),
            new SymbolEntry("\u273F", "\u273F", "Shiny / Flower", 0xFF55FF),
            new SymbolEntry("\u2726", "\u2726", "Sparkle / Special", 0xFF55FF),
            new SymbolEntry("\u2727", "\u2727", "White Star", 0xFFFF55),
            new SymbolEntry("\u2605", "\u2605", "Filled Star", 0xFFAA00),

            new SymbolEntry("\u2606", "\u2606", "Hollow Star", 0xAAAAAA),
            new SymbolEntry("\u2694", "\u2694", "Combat / Dungeon", 0xFF5555),
            new SymbolEntry("\u2764", "\u2764", "Health", 0xFF5555),
            new SymbolEntry("\u2748", "\u2748", "Defense", 0x55FF55),
            new SymbolEntry("\u2741", "\u2741", "Strength", 0xFF5555),
            new SymbolEntry("\u2623", "\u2623", "Crit Chance", 0x5555FF),
            new SymbolEntry("\u2620", "\u2620", "Crit Damage", 0x5555FF),
            new SymbolEntry("\u26A1", "\u26A1", "Speed / Charge", 0xFFFFFF),
            new SymbolEntry("\u270E", "\u270E", "Intelligence", 0x55FFFF),
            new SymbolEntry("\u2618", "\u2618", "Fortune", 0xFFAA00),
            new SymbolEntry("\u2744", "\u2744", "Ice / Cold", 0x55FFFF),

            new SymbolEntry("\u25C6", "\u25C6", "Rune Diamond", 0xAA00AA),
            new SymbolEntry("\u25C8", "\u25C8", "Gemstone / Essence", 0xFF55FF),
            new SymbolEntry("\u2742", "\u2742", "Ability / True Def", 0xFFFFFF),
            new SymbolEntry("\u2E15", "\u2E15", "Mining Speed", 0xFFAA00),
            new SymbolEntry("\u271E", "\u271E", "Cross / Revive", 0xAA0000),
            new SymbolEntry("\u03B1", "\u03B1", "Sea Creature", 0x55FFFF),
            new SymbolEntry("\u03A9", "\u03A9", "Omega", 0xFFAA00),
            new SymbolEntry("\u2714", "\u2714", "Checkmark", 0x55FF55),
            new SymbolEntry("\u2716", "\u2716", "Cross Mark", 0xFF5555),
            new SymbolEntry("\u221E", "\u221E", "Infinity", 0x55FFFF),
            new SymbolEntry("5\u2605", "&6\u272A\u272A\u272A\u272A\u272A", "Gold 5-Star Preset (&6 + 5x\u272A)", 0xFFAA00)
    );

    private ItemNameFormatter() {}

    public static String sanitizeInput(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_NAME_LENGTH));
        for (int i = 0; i < raw.length() && sb.length() < MAX_NAME_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static int legacyCodeToRgb(char code) {
        char lower = Character.toLowerCase(code);
        for (ColorCodeEntry entry : COLOR_CODES) {
            if (entry.code == lower) {
                return entry.rgb;
            }
        }
        return -1;
    }

    public static boolean hasVisibleText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        List<FormatSpan> spans = parseSpans(raw);
        for (FormatSpan span : spans) {
            if (!span.text().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static String stripFormatting(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        List<FormatSpan> spans = parseSpans(raw);
        StringBuilder sb = new StringBuilder(raw.length());
        for (FormatSpan span : spans) {
            sb.append(span.text());
        }
        return sb.toString();
    }

    public static List<FormatSpan> parseSpans(String raw) {
        String clean = sanitizeInput(raw);
        if (clean.isEmpty()) {
            return List.of();
        }
        List<FormatSpan> spans = new ArrayList<>();
        StringBuilder buf = new StringBuilder();

        int rgb = DEFAULT_RGB;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        boolean hasTextSinceStyle = false;

        int len = clean.length();
        for (int i = 0; i < len; i++) {
            char c = clean.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < len) {
                char next = clean.charAt(i + 1);
                if (next == '#' && i + 7 < len) {
                    int hexRgb = ColorUtils.parseHexRgb(clean.substring(i + 2, i + 8), -1);
                    if (hexRgb >= 0) {
                        flushSpan(spans, buf, rgb, bold, italic, underlined, strikethrough, obfuscated);
                        rgb = hexRgb;
                        if (hasTextSinceStyle) {
                            bold = false;
                            italic = false;
                            underlined = false;
                            strikethrough = false;
                            obfuscated = false;
                            hasTextSinceStyle = false;
                        }
                        i += 7;
                        continue;
                    }
                }
                int codeRgb = legacyCodeToRgb(next);
                if (codeRgb >= 0) {
                    flushSpan(spans, buf, rgb, bold, italic, underlined, strikethrough, obfuscated);
                    rgb = codeRgb;
                    if (hasTextSinceStyle) {
                        bold = false;
                        italic = false;
                        underlined = false;
                        strikethrough = false;
                        obfuscated = false;
                        hasTextSinceStyle = false;
                    }
                    i++;
                    continue;
                }
                char lower = Character.toLowerCase(next);
                if (lower == 'k' || lower == 'l' || lower == 'm' || lower == 'n' || lower == 'o' || lower == 'r') {
                    flushSpan(spans, buf, rgb, bold, italic, underlined, strikethrough, obfuscated);
                    switch (lower) {
                        case 'k' -> obfuscated = true;
                        case 'l' -> bold = true;
                        case 'm' -> strikethrough = true;
                        case 'n' -> underlined = true;
                        case 'o' -> italic = true;
                        case 'r' -> {
                            rgb = DEFAULT_RGB;
                            bold = false;
                            italic = false;
                            underlined = false;
                            strikethrough = false;
                            obfuscated = false;
                            hasTextSinceStyle = false;
                        }
                    }
                    i++;
                    continue;
                }
            }
            buf.append(c);
            hasTextSinceStyle = true;
        }
        flushSpan(spans, buf, rgb, bold, italic, underlined, strikethrough, obfuscated);
        return spans;
    }

    private static void flushSpan(
            List<FormatSpan> out,
            StringBuilder buf,
            int rgb,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated
    ) {
        if (buf.isEmpty()) {
            return;
        }
        out.add(new FormatSpan(buf.toString(), rgb, bold, italic, underlined, strikethrough, obfuscated));
        buf.setLength(0);
    }

    public static Component toComponent(String raw) {
        List<FormatSpan> spans = parseSpans(raw);
        MutableComponent root = Component.empty().withStyle(Style.EMPTY.withItalic(false));
        for (FormatSpan span : spans) {
            Style spanStyle = Style.EMPTY
                    .withColor(TextColor.fromRgb(span.rgb() & 0xFFFFFF))
                    .withBold(span.bold())
                    .withItalic(span.italic())
                    .withUnderlined(span.underlined())
                    .withStrikethrough(span.strikethrough())
                    .withObfuscated(span.obfuscated());
            root.append(Component.literal(span.text()).withStyle(spanStyle));
        }
        return root;
    }

    public static EditResult insertTokenAt(String current, int cursorPos, String token) {
        String base = sanitizeInput(current);
        String cleanToken = sanitizeInput(token);
        int pos = Math.max(0, Math.min(base.length(), cursorPos));
        if (cleanToken.isEmpty() || base.length() >= MAX_NAME_LENGTH) {
            return new EditResult(base, pos);
        }
        int remaining = MAX_NAME_LENGTH - base.length();
        if (cleanToken.length() > remaining) {
            cleanToken = cleanToken.substring(0, remaining);
        }
        String updated = base.substring(0, pos) + cleanToken + base.substring(pos);
        return new EditResult(updated, pos + cleanToken.length());
    }
}
