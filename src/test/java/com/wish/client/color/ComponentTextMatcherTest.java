package com.wish.client.color;

import java.net.URI;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTextMatcherTest {

    private static final String MINI_MAYLINE =
            "[4] <#529BEC>I<#995BDC>L<#D822CE>o<#FB02C6>v<#F706C7>e<#CC2DD1>M<#8969E0>a<#43A9EF>y<#10D7FB>l<#00E5FE>i<#18CFF9>n<#529BEC>e";

    @Test
    void stripMiniMessageFromComponentStripsTagsInSingleAndSplitSiblingsWhilePreservingStyles() {
        Component single = Component.literal(MINI_MAYLINE).withStyle(Style.EMPTY.withBold(true).withUnderlined(true));
        Component strippedSingle = ComponentTextMatcher.stripMiniMessageFromComponent(single);
        assertEquals("[4] ILoveMayline", strippedSingle.getString());
        assertTrue(strippedSingle.getStyle().isBold());
        assertTrue(strippedSingle.getStyle().isUnderlined());

        Style letterStyle = Style.EMPTY.withBold(true).withUnderlined(true).withColor(0xFFFFFF);
        MutableComponent split = Component.empty()
                .append(Component.literal("[4] ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("<#529BEC>I").withStyle(letterStyle))
                .append(Component.literal("<#995BDC>L").withStyle(letterStyle))
                .append(Component.literal("<#D822CE>o").withStyle(letterStyle))
                .append(Component.literal("<#FB02C6>v").withStyle(letterStyle))
                .append(Component.literal("<#F706C7>e").withStyle(letterStyle))
                .append(Component.literal("<#CC2DD1>M").withStyle(letterStyle))
                .append(Component.literal("<#8969E0>a").withStyle(letterStyle))
                .append(Component.literal("<#43A9EF>y").withStyle(letterStyle))
                .append(Component.literal("<#10D7FB>l").withStyle(letterStyle))
                .append(Component.literal("<#00E5FE>i").withStyle(letterStyle))
                .append(Component.literal("<#18CFF9>n").withStyle(letterStyle))
                .append(Component.literal("<#529BEC>e").withStyle(letterStyle));

        Component strippedSplit = ComponentTextMatcher.stripMiniMessageFromComponent(split);
        assertEquals("[4] ILoveMayline", strippedSplit.getString());
        assertTrue(strippedSplit.getSiblings().get(1).getStyle().isUnderlined());

        Component tagOnlySibs = Component.empty()
                .append(Component.literal("[4] "))
                .append(Component.literal("<#529BEC>"))
                .append(Component.literal("I").withStyle(letterStyle))
                .append(Component.literal("<#995BDC>"))
                .append(Component.literal("LoveMayline").withStyle(letterStyle));
        assertEquals("[4] ILoveMayline", ComponentTextMatcher.stripMiniMessageFromComponent(tagOnlySibs).getString());
    }

    @Test
    void stripMiniMessageFromComponentHandlesTranslatableArgsUrlsAndNoOpIdentity() {
        Component translatable = Component.translatable("chat.type.text", Component.literal(MINI_MAYLINE), "hi");
        Component strippedTrans = ComponentTextMatcher.stripMiniMessageFromComponent(translatable);
        assertTrue(strippedTrans.getString().contains("[4] ILoveMayline"));
        assertFalse(strippedTrans.getString().contains("<#529BEC>"));

        Component url = Component.literal("https://example.com/<#529BEC>path");
        assertSame(url, ComponentTextMatcher.stripMiniMessageFromComponent(url));

        Component plain = Component.literal("[4] Notch");
        assertSame(plain, ComponentTextMatcher.stripMiniMessageFromComponent(plain));

        Component heart = Component.literal("<3 Notch");
        assertSame(heart, ComponentTextMatcher.stripMiniMessageFromComponent(heart));

        Component markedTab = Component.literal(MINI_MAYLINE).withStyle(Style.EMPTY.withInsertion("wish:tab"));
        assertSame(markedTab, ComponentTextMatcher.stripMiniMessageFromComponent(markedTab));
    }

    @Test
    void findFirstTargetMatchMatchesInlineSectionFormattedNamesAndRespectsBoundaries() {
        String inlineFormatted = "[4] \u00A7l\u00A7nI\u00A7l\u00A7nL\u00A7l\u00A7no\u00A7l\u00A7nv\u00A7l\u00A7ne"
                + "\u00A7l\u00A7nM\u00A7l\u00A7na\u00A7l\u00A7ny\u00A7l\u00A7nl\u00A7l\u00A7ni\u00A7l\u00A7nn\u00A7l\u00A7ne";
        ComponentTextMatcher.TargetSpanMatch match =
                ComponentTextMatcher.findFirstTargetMatch(inlineFormatted, List.of("ILoveMayline"));
        assertNotNull(match);
        assertEquals(0, match.targetIndex());
        assertEquals("ILoveMayline", match.matchedCleanName());
        assertEquals("[4] \u00A7l\u00A7n", match.before());
        assertEquals("", match.after());

        ComponentTextMatcher.TargetSpanMatch possessive =
                ComponentTextMatcher.findFirstTargetMatch("[4] ILoveMayline's sword", List.of("ilovemayline"));
        assertNotNull(possessive);
        assertEquals("ILoveMayline", possessive.matchedCleanName());
        assertEquals("'s sword", possessive.after());

        assertNull(ComponentTextMatcher.findFirstTargetMatch("fooILoveMayline", List.of("ILoveMayline")));
        assertNull(ComponentTextMatcher.findFirstTargetMatch("ILoveMayline2", List.of("ILoveMayline")));
        assertNull(ComponentTextMatcher.findFirstTargetMatch("[ILoveMayline \u00A7fhead]", List.of("ILoveMayline")));
    }

    @Test
    void coalesceSplitTargetNamesMergesPerCharacterMiniMessageAndRgbSiblingsWithoutTouchPrefix() {
        Style boldUnderline = Style.EMPTY.withBold(true).withUnderlined(true);
        List<Component> miniMessageSiblings = List.of(
                Component.literal("[4] ").withStyle(ChatFormatting.GRAY),
                Component.literal("<#529BEC>").withStyle(boldUnderline),
                Component.literal("I").withStyle(boldUnderline),
                Component.literal("<#995BDC>").withStyle(boldUnderline),
                Component.literal("L").withStyle(boldUnderline),
                Component.literal("<#D822CE>o").withStyle(boldUnderline),
                Component.literal("<#FB02C6>ve").withStyle(boldUnderline),
                Component.literal("<#8969E0>May").withStyle(boldUnderline),
                Component.literal("<#10D7FB>line").withStyle(boldUnderline)
        );

        ComponentTextMatcher.CoalesceResult res = ComponentTextMatcher.coalesceSplitTargetNames(
                "", Style.EMPTY, miniMessageSiblings, List.of("ILoveMayline"));
        assertTrue(res.changed());
        assertEquals(2, res.siblings().size());
        assertEquals("[4] ", res.siblings().get(0).getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), res.siblings().get(0).getStyle().getColor());
        assertEquals("ILoveMayline", res.siblings().get(1).getString());
        assertTrue(res.siblings().get(1).getStyle().isBold());
        assertTrue(res.siblings().get(1).getStyle().isUnderlined());

        List<Component> rgbSiblings = List.of(
                Component.literal("[4] "),
                Component.literal("I").withStyle(boldUnderline.withColor(0x529BEC)),
                Component.literal("Love").withStyle(boldUnderline.withColor(0x995BDC)),
                Component.literal("Mayline").withStyle(boldUnderline.withColor(0xFB02C6))
        );
        ComponentTextMatcher.CoalesceResult rgbRes = ComponentTextMatcher.coalesceSplitTargetNames(
                "", Style.EMPTY, rgbSiblings, List.of("ILoveMayline"));
        assertTrue(rgbRes.changed());
        assertEquals(2, rgbRes.siblings().size());
        assertEquals("ILoveMayline", rgbRes.siblings().get(1).getString());
    }

    @Test
    void coalesceSplitTargetNamesMergesParentPlusSiblingAndEmptyWrappersAndRespectsBoundaries() {
        ComponentTextMatcher.CoalesceResult parentAndSib = ComponentTextMatcher.coalesceSplitTargetNames(
                "[4] ILove",
                Style.EMPTY,
                List.of(Component.literal("Mayline [MVP+]")),
                List.of("ILoveMayline"));
        assertTrue(parentAndSib.changed());
        assertEquals("[4] ", parentAndSib.selfText());
        assertEquals(2, parentAndSib.siblings().size());
        assertEquals("ILoveMayline", parentAndSib.siblings().get(0).getString());
        assertEquals(" [MVP+]", parentAndSib.siblings().get(1).getString());

        ComponentTextMatcher.CoalesceResult pureParentCleared = ComponentTextMatcher.coalesceSplitTargetNames(
                "ILove",
                Style.EMPTY,
                List.of(Component.literal("Mayline"), Component.literal("'s")),
                List.of("ILoveMayline"));
        assertTrue(pureParentCleared.changed());
        assertEquals("", pureParentCleared.selfText());
        assertEquals(1, pureParentCleared.siblings().size());
        assertEquals("ILoveMayline's", pureParentCleared.siblings().get(0).getString());

        ComponentTextMatcher.CoalesceResult wrapped = ComponentTextMatcher.coalesceSplitTargetNames(
                "",
                Style.EMPTY,
                List.of(
                        Component.empty().append(Component.literal("ILove")),
                        Component.empty().append(Component.literal("Mayline"))
                ),
                List.of("ILoveMayline"));
        assertTrue(wrapped.changed());
        assertEquals(1, wrapped.siblings().size());
        assertEquals("ILoveMayline", wrapped.siblings().get(0).getString());

        Style hoverStyle = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("Rank")));
        Style clickStyle = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://example.com")));
        ComponentTextMatcher.CoalesceResult blockedByInteractive = ComponentTextMatcher.coalesceSplitTargetNames(
                "",
                Style.EMPTY,
                List.of(Component.literal("ILove").withStyle(hoverStyle), Component.literal("Mayline").withStyle(clickStyle)),
                List.of("ILoveMayline"));
        assertFalse(blockedByInteractive.changed());

        ComponentTextMatcher.CoalesceResult alreadySingle = ComponentTextMatcher.coalesceSplitTargetNames(
                "",
                Style.EMPTY,
                List.of(Component.literal("[4] "), Component.literal("ILoveMayline")),
                List.of("ILoveMayline"));
        assertFalse(alreadySingle.changed());

        ComponentTextMatcher.CoalesceResult partialExtension = ComponentTextMatcher.coalesceSplitTargetNames(
                "",
                Style.EMPTY,
                List.of(Component.literal("ILove"), Component.literal("Mayline"), Component.literal("X")),
                List.of("ILoveMayline"));
        assertFalse(partialExtension.changed());
    }

    @Test
    void stripMiniMessageThenCoalesceHandlesEmptyTagPlaceholderSiblings() {
        Component stripped = ComponentTextMatcher.stripMiniMessageFromComponent(
                Component.empty()
                        .append(Component.literal("[4] "))
                        .append(Component.literal("<#529BEC>"))
                        .append(Component.literal("I"))
                        .append(Component.literal("<#995BDC>"))
                        .append(Component.literal("L"))
                        .append(Component.literal("<#D822CE>"))
                        .append(Component.literal("oveMayline")));
        assertEquals("[4] ILoveMayline", stripped.getString());

        ComponentTextMatcher.CoalesceResult fromStripped = ComponentTextMatcher.coalesceSplitTargetNames(
                "",
                Style.EMPTY,
                stripped.getSiblings(),
                List.of("ILoveMayline"));
        assertTrue(fromStripped.changed());
        assertEquals(2, fromStripped.siblings().size());
        assertEquals("[4] ", fromStripped.siblings().get(0).getString());
        assertEquals("ILoveMayline", fromStripped.siblings().get(1).getString());

        ComponentTextMatcher.CoalesceResult withExplicitEmptySiblings = ComponentTextMatcher.coalesceSplitTargetNames(
                "",
                Style.EMPTY,
                List.of(
                        Component.literal("[4] "),
                        Component.empty(),
                        Component.literal("I"),
                        Component.empty(),
                        Component.literal("L"),
                        Component.empty(),
                        Component.literal("o"),
                        Component.empty(),
                        Component.literal("v"),
                        Component.literal("e"),
                        Component.literal("M"),
                        Component.literal("a"),
                        Component.literal("y"),
                        Component.literal("l"),
                        Component.literal("i"),
                        Component.literal("n"),
                        Component.literal("e")),
                List.of("ILoveMayline"));
        assertTrue(withExplicitEmptySiblings.changed());
        assertEquals(2, withExplicitEmptySiblings.siblings().size());
        assertEquals("[4] ", withExplicitEmptySiblings.siblings().get(0).getString());
        assertEquals("ILoveMayline", withExplicitEmptySiblings.siblings().get(1).getString());
    }
}
