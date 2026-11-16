package com.wish.client.features;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemNameFormatterTest {

    @Test
    void parseSpansHandlesLegacyColorsAndFormattingResetRule() {
        List<ItemNameFormatter.FormatSpan> spans = ItemNameFormatter.parseSpans(
                "&6Withered &d&lHyperion &6\u272A\u272A\u272A\u272A\u272A&c\u278E"
        );
        assertEquals(4, spans.size());

        assertEquals("Withered ", spans.get(0).text());
        assertEquals(0xFFAA00, spans.get(0).rgb());
        assertFalse(spans.get(0).bold());

        assertEquals("Hyperion ", spans.get(1).text());
        assertEquals(0xFF55FF, spans.get(1).rgb());
        assertTrue(spans.get(1).bold());

        assertEquals("\u272A\u272A\u272A\u272A\u272A", spans.get(2).text());
        assertEquals(0xFFAA00, spans.get(2).rgb());
        assertFalse(spans.get(2).bold());

        assertEquals("\u278E", spans.get(3).text());
        assertEquals(0xFF5555, spans.get(3).rgb());
    }

    @Test
    void parseSpansSupportsHexColorsLiteralAmpersandsAndResetCode() {
        List<ItemNameFormatter.FormatSpan> spans = ItemNameFormatter.parseSpans(
                "&#FFC6F9&oPink & Blade&r Normal"
        );
        assertEquals(2, spans.size());
        assertEquals("Pink & Blade", spans.get(0).text());
        assertEquals(0xFFC6F9, spans.get(0).rgb());
        assertTrue(spans.get(0).italic());

        assertEquals(" Normal", spans.get(1).text());
        assertEquals(ItemNameFormatter.DEFAULT_RGB, spans.get(1).rgb());
        assertFalse(spans.get(1).italic());
    }

    @Test
    void paletteContainsAllSixteenColorsAllFormatsAndStars() {
        assertEquals(16, ItemNameFormatter.COLOR_CODES.size());
        assertEquals(6, ItemNameFormatter.FORMAT_CODES.size());
        assertTrue(ItemNameFormatter.SYMBOLS.stream().anyMatch(s -> "\u272A".equals(s.token())));
        assertTrue(ItemNameFormatter.SYMBOLS.stream().anyMatch(s -> "\u278A".equals(s.token())));
        assertTrue(ItemNameFormatter.SYMBOLS.stream().anyMatch(s -> "\u278E".equals(s.token())));
    }

    @Test
    void parseSpansPreservesModifiersAcrossConsecutiveColorCodes() {
        List<ItemNameFormatter.FormatSpan> boldBeforeColor = ItemNameFormatter.parseSpans("&l&cHyperion");
        assertEquals(1, boldBeforeColor.size());
        assertEquals("Hyperion", boldBeforeColor.get(0).text());
        assertEquals(0xFF5555, boldBeforeColor.get(0).rgb());
        assertTrue(boldBeforeColor.get(0).bold());

        List<ItemNameFormatter.FormatSpan> boldBeforeHex = ItemNameFormatter.parseSpans("&l&n&#FF55FFTerminator &6\u272A");
        assertEquals(2, boldBeforeHex.size());
        assertEquals("Terminator ", boldBeforeHex.get(0).text());
        assertEquals(0xFF55FF, boldBeforeHex.get(0).rgb());
        assertTrue(boldBeforeHex.get(0).bold());
        assertTrue(boldBeforeHex.get(0).underlined());

        assertEquals("\u272A", boldBeforeHex.get(1).text());
        assertEquals(0xFFAA00, boldBeforeHex.get(1).rgb());
        assertFalse(boldBeforeHex.get(1).bold());
        assertFalse(boldBeforeHex.get(1).underlined());

        var component = ItemNameFormatter.toComponent("&l&cHyperion");
        assertEquals("Hyperion", component.getString());
        assertTrue(component.getSiblings().get(0).getStyle().isBold());
    }

    @Test
    void insertTokenUpdatesTextSafely() {
        ItemNameFormatter.EditResult inserted = ItemNameFormatter.insertTokenAt("Hyperion", 0, "&d&l");
        assertEquals("&d&lHyperion", inserted.text());
        assertEquals(4, inserted.cursorPos());
    }

    @Test
    void hasVisibleTextAndToComponentWorkCleanly() {
        assertFalse(ItemNameFormatter.hasVisibleText("&a&l"));
        assertTrue(ItemNameFormatter.hasVisibleText("&aSword"));
        assertEquals("Heroic \u272A", ItemNameFormatter.toComponent("&dHeroic \u272A").getString());
    }
}
