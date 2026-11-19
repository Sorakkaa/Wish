package com.wish.client.features.visual;

import com.wish.client.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordReplacerManagerTest {

    @Test
    void compileSnapshotOrdersLongestSourceFirstAndSkipsDisabledOrBlankRules() {
        WordReplacerManager.Snapshot snapshot = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(
                        new ModConfig.WordReplacementRule("cat", "dog", true),
                        new ModConfig.WordReplacementRule("", "empty", true),
                        new ModConfig.WordReplacementRule("off", "on", false),
                        new ModConfig.WordReplacementRule("catfish", "shark", true)
                )
        );

        assertTrue(snapshot.enabled());
        assertEquals(2, snapshot.rules().size());
        assertEquals("catfish", snapshot.rules().get(0).from());
        assertEquals("cat", snapshot.rules().get(1).from());
        assertEquals(3, snapshot.minFromLength());
    }

    @Test
    void replacePlainStringHandlesWholeWordCaseUrlAndFormattingCodesSafely() {
        WordReplacerManager.Snapshot wholeInsensitive = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(
                        new ModConfig.WordReplacementRule("cat", "dog", true),
                        new ModConfig.WordReplacementRule("catfish", "shark", true),
                        new ModConfig.WordReplacementRule("jerry", "&dBarry", true),
                        new ModConfig.WordReplacementRule("caf\u00E9", "th\u00E9", true)
                )
        );

        assertEquals("The shark and dog played, while catch stays.",
                WordReplacerManager.replacePlainString("The Catfish and CAT played, while catch stays.", wholeInsensitive));
        assertEquals("Bonjour au th\u00E9 mais pas d\u00E9caf\u00E9!",
                WordReplacerManager.replacePlainString("Bonjour au caf\u00E9 mais pas d\u00E9caf\u00E9!", wholeInsensitive));
        assertEquals("\u00A7c\u00A7dBarry\u00A7r\u00A7c \u00A7eattack!",
                WordReplacerManager.replacePlainString("\u00A7cJerry \u00A7eattack!", wholeInsensitive));
        assertEquals("Visit https://cat.example.com/cat now dog!",
                WordReplacerManager.replacePlainString("Visit https://cat.example.com/cat now cat!", wholeInsensitive));

        String untouched = "Nothing matches here & literal";
        assertSame(untouched, WordReplacerManager.replacePlainString(untouched, wholeInsensitive));

        WordReplacerManager.Snapshot subSensitive = WordReplacerManager.compileSnapshot(
                true,
                false,
                true,
                List.of(new ModConfig.WordReplacementRule("cat", "big cat", true))
        );
        assertEquals("Cat big catch", WordReplacerManager.replacePlainString("Cat catch", subSensitive));
    }

    @Test
    void replacePlainStringTranslatesValidColorCodesOnly() {
        WordReplacerManager.Snapshot snap = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(
                        new ModConfig.WordReplacementRule("duo", "Tom & Jerry", true),
                        new ModConfig.WordReplacementRule("wish", "&#FF55AAWish", true)
                )
        );

        assertEquals("Tom & Jerry and \u00A7x\u00A7f\u00A7f\u00A75\u00A75\u00A7a\u00A7aWish",
                WordReplacerManager.replacePlainString("duo and wish", snap));
    }

    @Test
    void replaceComponentPreservesStylesEventsAndPreventsSelfContainingLoopsAcrossPasses() {
        WordReplacerManager.Snapshot snap = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(
                        new ModConfig.WordReplacementRule("bad", "&aGOOD&r!", true),
                        new ModConfig.WordReplacementRule("cat", "big cat", true)
                )
        );

        Style interactive = Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/wish"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("hover")));

        MutableComponent original = Component.empty()
                .append(Component.literal("Hello bad world").withStyle(interactive))
                .append(Component.literal(" (cat)").withStyle(ChatFormatting.GRAY));

        Component replaced = WordReplacerManager.replaceComponent(original, snap);
        assertEquals("Hello GOOD! world (big cat)", replaced.getString());

        Component firstChild = replaced.getSiblings().get(0);
        assertNotNull(firstChild.getStyle().getClickEvent());
        assertNotNull(firstChild.getStyle().getHoverEvent());
        assertTrue(firstChild.getSiblings().stream().anyMatch(
                s -> "GOOD".equals(s.getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(s.getStyle().getColor())
                        && s.getStyle().getClickEvent() != null
        ));
        assertTrue(firstChild.getSiblings().stream().anyMatch(
                s -> "! world".equals(s.getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(s.getStyle().applyTo(firstChild.getStyle()).getColor())
                        && s.getStyle().applyTo(firstChild.getStyle()).isBold()
        ));

        Component secondPass = WordReplacerManager.replaceComponent(replaced.copy(), snap);
        assertEquals("Hello GOOD! world (big cat)", secondPass.getString());

        Component unaffected = Component.literal("No changes here").withStyle(ChatFormatting.AQUA);
        assertSame(unaffected, WordReplacerManager.replaceComponent(unaffected, snap));
    }

    @Test
    void replaceComponentHandlesTranslatableArgsAndTemplatesSafely() {
        WordReplacerManager.Snapshot snap = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(
                        new ModConfig.WordReplacementRule("zombie", "Bob", true),
                        new ModConfig.WordReplacementRule("Steve", "Alex", true)
                )
        );

        Component translatable = Component.translatableWithFallback(
                "wish.test.death",
                "%1$s was slain by zombie!",
                Component.literal("Steve").withStyle(ChatFormatting.YELLOW)
        );
        Component replaced = WordReplacerManager.replaceComponent(translatable, snap);
        assertEquals("Alex was slain by Bob!", replaced.getString());

        Component malformed = Component.translatableWithFallback(
                "wish.test.malformed",
                "%99999999999$s zombie",
                "Steve"
        );
        assertEquals(" Bob", WordReplacerManager.replaceComponent(malformed, snap).getString());
    }

    @Test
    void sanitizeRulesStripsInvalidEntriesControlCharsAndCapsSize() {
        ModConfig.WordReplacementRule newlineRule = new ModConfig.WordReplacementRule("\u00A7ahe\nllo\r\0", "wo\nrld\0", true);
        ModConfig.WordReplacementRule blankFrom = new ModConfig.WordReplacementRule("   ", "ignored", true);

        List<ModConfig.WordReplacementRule> sanitized = WordReplacerManager.sanitizeRules(
                java.util.Arrays.asList(null, blankFrom, newlineRule)
        );
        assertEquals(1, sanitized.size());
        assertEquals("ahello", sanitized.get(0).from);
        assertEquals("world", sanitized.get(0).to);
    }

    @Test
    void replaceComponentSupportsAmpersandColorsFormattingAndPreservesEvents() {
        WordReplacerManager.Snapshot snapshot = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(new ModConfig.WordReplacementRule("cat", "&a&lBIG &oITALIC &nUNDER &mSTRIKE &kOBF &rPLAIN", true))
        );

        Style baseStyle = Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://example.com")))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Cat tooltip")));

        Component input = Component.literal("hello cat world").withStyle(baseStyle);
        Component replaced = WordReplacerManager.replaceComponent(input, snapshot);

        assertEquals("hello BIG ITALIC UNDER STRIKE OBF PLAIN world", replaced.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), replaced.getStyle().getColor());

        List<Component> siblings = replaced.getSiblings();
        assertTrue(siblings.size() >= 6);

        Component bigSeg = siblings.get(0);
        assertEquals("BIG ", bigSeg.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), bigSeg.getStyle().getColor());
        assertTrue(bigSeg.getStyle().isBold());
        assertNotNull(bigSeg.getStyle().getClickEvent());
        assertNotNull(bigSeg.getStyle().getHoverEvent());

        Component italicSeg = siblings.get(1);
        assertEquals("ITALIC ", italicSeg.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), italicSeg.getStyle().getColor());
        assertTrue(italicSeg.getStyle().isBold());
        assertTrue(italicSeg.getStyle().isItalic());

        Component underSeg = siblings.get(2);
        assertEquals("UNDER ", underSeg.getString());
        assertTrue(underSeg.getStyle().isUnderlined());

        Component strikeSeg = siblings.get(3);
        assertEquals("STRIKE ", strikeSeg.getString());
        assertTrue(strikeSeg.getStyle().isStrikethrough());

        Component obfSeg = siblings.get(4);
        assertEquals("OBF ", obfSeg.getString());
        assertTrue(obfSeg.getStyle().isObfuscated());

        Component plainAndTrailing = siblings.get(5);
        assertEquals("PLAIN world", plainAndTrailing.getString());
        assertNull(plainAndTrailing.getStyle().getColor(), "Trailing segment must inherit parent color without bleeding");
        assertFalse(plainAndTrailing.getStyle().isBold());
        assertFalse(plainAndTrailing.getStyle().isItalic());
        assertFalse(plainAndTrailing.getStyle().isUnderlined());
        assertFalse(plainAndTrailing.getStyle().isStrikethrough());
        assertFalse(plainAndTrailing.getStyle().isObfuscated());

        Style trailingEffectiveStyle = replaced.visit(
                (style, text) -> "PLAIN world".equals(text) ? Optional.of(style) : Optional.empty(),
                Style.EMPTY
        ).orElse(null);
        assertNotNull(trailingEffectiveStyle);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), trailingEffectiveStyle.getColor());
    }

    @Test
    void replaceComponentSupportsInlineHexWithoutBleeding() {
        WordReplacerManager.Snapshot snapshot = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(new ModConfig.WordReplacementRule("flower", "&#123456\u273F Hero", true))
        );

        Component input = Component.literal("hi flower & bye").withStyle(ChatFormatting.AQUA);
        Component replaced = WordReplacerManager.replaceComponent(input, snapshot);
        assertEquals("hi \u273F Hero & bye", replaced.getString());

        Component replacedSeg = replaced.getSiblings().stream()
                .filter(c -> c.getString().contains("\u273F"))
                .findFirst()
                .orElse(null);
        assertNotNull(replacedSeg);
        assertEquals(0x123456, replacedSeg.getStyle().getColor().getValue());

        Style tailStyle = replaced.visit(
                (style, text) -> text.contains(" & bye") ? Optional.of(style) : Optional.empty(),
                Style.EMPTY
        ).orElse(null);
        assertNotNull(tailStyle);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), tailStyle.getColor());
    }

    @Test
    void replacePlainStringAppliesSectionCodesAndRestoresEnclosingFormatting() {
        WordReplacerManager.Snapshot snapshot = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(new ModConfig.WordReplacementRule("cat", "&c&lDOG", true))
        );

        assertEquals(
                "\u00A7ahello \u00A7c\u00A7lDOG\u00A7r\u00A7a world",
                WordReplacerManager.replacePlainString("\u00A7ahello cat world", snapshot)
        );
        assertEquals(
                "\u00A7c\u00A7lDOG\u00A7r world",
                WordReplacerManager.replacePlainString("cat world", snapshot)
        );
    }

    @Test
    void buildPreviewComponentRendersAmpersandColorsAndFormatting() {
        Component preview = WordReplacerManager.buildPreviewComponent("&d&l\u273F &eStar&r!");
        assertEquals("\u273F Star!", preview.getString());
        List<Component> siblings = preview.getSiblings();
        assertFalse(siblings.isEmpty());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE), siblings.get(0).getStyle().getColor());
        assertTrue(siblings.get(0).getStyle().isBold());
    }

    @Test
    void buildEditorPreviewComponentKeepsCodeTokensVisibleAndColorsTypedTextAndCaret() {
        Component goldPreview = WordReplacerManager.buildEditorPreviewComponent("&6 caca", true);
        assertEquals("&6 caca_", goldPreview.getString());
        List<Component> goldSiblings = goldPreview.getSiblings();
        assertEquals(3, goldSiblings.size());
        assertEquals("&6", goldSiblings.get(0).getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), goldSiblings.get(0).getStyle().getColor());
        assertEquals(" caca", goldSiblings.get(1).getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), goldSiblings.get(1).getStyle().getColor());
        assertEquals("_", goldSiblings.get(2).getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), goldSiblings.get(2).getStyle().getColor());

        Component hexAndMagic = WordReplacerManager.buildEditorPreviewComponent("&#123456&kHi", true);
        assertEquals("&#123456&kHi_", hexAndMagic.getString());
        Component caretSeg = hexAndMagic.getSiblings().getLast();
        assertEquals("_", caretSeg.getString());
        assertEquals(0x123456, caretSeg.getStyle().getColor().getValue());
        assertFalse(caretSeg.getStyle().isObfuscated());
    }

    @Test
    void updateRuleEditsExistingRulePreservesEnabledAndRejectsDuplicatesOrBlank() {
        List<ModConfig.WordReplacementRule> backup = ModConfig.INSTANCE.wordReplacements != null
                ? List.copyOf(ModConfig.INSTANCE.wordReplacements)
                : List.of();
        boolean origCaseSensitive = ModConfig.INSTANCE.wordReplacerCaseSensitive;
        try {
            ModConfig.INSTANCE.wordReplacerCaseSensitive = false;
            ModConfig.WordReplacementRule first = new ModConfig.WordReplacementRule("alpha", "&aAlpha", false);
            ModConfig.WordReplacementRule second = new ModConfig.WordReplacementRule("beta", "&bBeta", true);
            ModConfig.INSTANCE.wordReplacements = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(first, second));
            WordReplacerManager.invalidateCache();

            assertFalse(WordReplacerManager.updateRule(0, "BETA", "&cNope"));
            assertFalse(WordReplacerManager.updateRule(0, "   ", "&cNope"));
            assertFalse(WordReplacerManager.updateRule(5, "gamma", "&cNope"));
            assertEquals("alpha", ModConfig.INSTANCE.wordReplacements.get(0).from);

            assertTrue(WordReplacerManager.updateRule(0, "alpha2", "&e\u273F Updated"));
            ModConfig.WordReplacementRule updated = ModConfig.INSTANCE.wordReplacements.get(0);
            assertEquals("alpha2", updated.from);
            assertEquals("&e\u273F Updated", updated.to);
            assertFalse(updated.enabled);
        } finally {
            ModConfig.INSTANCE.wordReplacerCaseSensitive = origCaseSensitive;
            ModConfig.INSTANCE.wordReplacements = new java.util.concurrent.CopyOnWriteArrayList<>(backup);
            WordReplacerManager.invalidateCache();
        }
    }

    @Test
    void scoreboardSplitSiblingsAndNestedTeamComponentsAreReplaced() {
        WordReplacerManager.Snapshot snapshot = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(
                        new ModConfig.WordReplacementRule("Purse: 100", "&eWallet: &a100", true),
                        new ModConfig.WordReplacementRule("SKYBLOCK", "&d&lWISHBLOCK", true)
                )
        );

        MutableComponent scoreboardRow = Component.empty()
                .append(Component.empty()
                        .append(Component.literal("Pur").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("se:").withStyle(ChatFormatting.WHITE)))
                .append(Component.literal("\u00A77"))
                .append(Component.literal(" 100").withStyle(ChatFormatting.GOLD));

        Component replacedRow = WordReplacerManager.replaceComponent(scoreboardRow, snapshot);
        assertEquals("Wallet: 100", replacedRow.getString());
        assertEquals(replacedRow, WordReplacerManager.replaceComponent(replacedRow, snapshot));

        MutableComponent splitTitle = Component.empty()
                .append(Component.literal("S").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("K").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("YBLOCK").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        Component replacedTitle = WordReplacerManager.replaceComponent(splitTitle, snapshot);
        assertEquals("WISHBLOCK", replacedTitle.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE), replacedTitle.getSiblings().get(0).getStyle().getColor());
    }

    @Test
    void bossBarInlineSectionCodesAndNonBreakingSpacesMatchWordReplacerRules() {
        WordReplacerManager.Snapshot snapshot = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(
                        new ModConfig.WordReplacementRule("SKYBLOCK", "Wish", true),
                        new ModConfig.WordReplacementRule("Boss Health", "Mob HP", true)
                )
        );

        String rainbowTitle = "\u00A7e\u00A7lS\u00A76\u00A7lK\u00A7e\u00A7lYBLOCK\u00A77\u00A0Mode";
        assertEquals("\u00A7e\u00A7lWish\u00A77\u00A0Mode", WordReplacerManager.replacePlainString(rainbowTitle, snapshot));

        Component bossBarComponent = Component.literal("\u00A7e\u00A7lS\u00A76\u00A7lK\u00A7e\u00A7lYBLOCK")
                .append(Component.literal(" Boss\u00A0Health"));
        Component replacedBoss = WordReplacerManager.replaceComponent(bossBarComponent, snapshot);
        assertEquals("\u00A7e\u00A7lWish Mob HP", replacedBoss.getString());
    }

    @Test
    void replaceCharSequenceFormatsHexLegacyAndRespectsVersionAndBypassMarkers() {
        WordReplacerManager.Snapshot snapshot = WordReplacerManager.compileSnapshot(
                true,
                true,
                false,
                List.of(new ModConfig.WordReplacementRule("cat", "&#112233&lbig cat", true))
        );

        FormattedCharSequence input = FormattedCharSequence.forward("my cat here", Style.EMPTY.withColor(ChatFormatting.GRAY));
        FormattedCharSequence replaced = WordReplacerManager.replaceCharSequence(input, snapshot);
        StringBuilder textOut = new StringBuilder();
        TextColor[] matchedColor = new TextColor[1];
        replaced.accept((pos, style, cp) -> {
            textOut.appendCodePoint(cp);
            if (textOut.toString().endsWith("big cat")) {
                matchedColor[0] = style.getColor();
            }
            return true;
        });
        assertEquals("my big cat here", textOut.toString());
        assertEquals(0x112233, matchedColor[0].getValue());

        FormattedCharSequence secondPass = WordReplacerManager.replaceCharSequence(replaced, snapshot);
        assertSame(replaced, secondPass);

        FormattedCharSequence bypassed = WordReplacerManager.markBypassed(
                FormattedCharSequence.forward("my cat", Style.EMPTY)
        );
        FormattedCharSequence afterBypass = WordReplacerManager.replaceCharSequence(bypassed, snapshot);
        assertSame(bypassed, afterBypass);
    }
}
