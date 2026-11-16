package com.wish.client.color;

import com.wish.client.config.ModConfig;
import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameColorManagerTest {

    private final int originalCosmeticVisibility = ModConfig.INSTANCE.cosmeticVisibility;
    private final boolean originalEnableNameColor = ModConfig.INSTANCE.enableNameColor;
    private final int originalPseudoAnimation = ModConfig.INSTANCE.pseudoAnimation;
    private final float originalPseudoAnimationSpeed = ModConfig.INSTANCE.pseudoAnimationSpeed;
    private final String originalCustomHexColor = ModConfig.INSTANCE.customHexColor;
    private final String originalCustomHexColor2 = ModConfig.INSTANCE.customHexColor2;
    private final String originalSpoofedName = ModConfig.INSTANCE.spoofedName;
    private final int originalPseudoFont = ModConfig.INSTANCE.pseudoFont;
    private final boolean originalEnableWordReplacer = ModConfig.INSTANCE.enableWordReplacer;
    private final boolean originalWordReplacerWholeWord = ModConfig.INSTANCE.wordReplacerWholeWord;
    private final boolean originalWordReplacerCaseSensitive = ModConfig.INSTANCE.wordReplacerCaseSensitive;
    private final List<ModConfig.WordReplacementRule> originalWordReplacements =
            ModConfig.INSTANCE.wordReplacements != null ? List.copyOf(ModConfig.INSTANCE.wordReplacements) : List.of();

    @AfterEach
    void cleanup() {
        ModConfig.INSTANCE.cosmeticVisibility = originalCosmeticVisibility;
        ModConfig.INSTANCE.enableNameColor = originalEnableNameColor;
        ModConfig.INSTANCE.pseudoAnimation = originalPseudoAnimation;
        ModConfig.INSTANCE.pseudoAnimationSpeed = originalPseudoAnimationSpeed;
        ModConfig.INSTANCE.customHexColor = originalCustomHexColor;
        ModConfig.INSTANCE.customHexColor2 = originalCustomHexColor2;
        ModConfig.INSTANCE.spoofedName = originalSpoofedName;
        ModConfig.INSTANCE.pseudoFont = originalPseudoFont;
        ModConfig.INSTANCE.enableWordReplacer = originalEnableWordReplacer;
        ModConfig.INSTANCE.wordReplacerWholeWord = originalWordReplacerWholeWord;
        ModConfig.INSTANCE.wordReplacerCaseSensitive = originalWordReplacerCaseSensitive;
        ModConfig.INSTANCE.wordReplacements = new CopyOnWriteArrayList<>(originalWordReplacements);
        WordReplacerManager.invalidateCache();
        NameColorManager.isRenderingTab = false;
        NameColorManager.setTestLocalPlayer(null, null);
        CustomPlayerManager.setTestState(Collections.emptyMap(), Collections.emptyMap());
    }

    @Test
    void computeAnimationRgbChromaProducesSmooth24BitRainbowMatchingMenuPreview() {
        int c0 = NameColorManager.computeAnimationRgb("CHROMA", 0, 8, 1.0, 0L, "#FFFFFF", "#FFFFFF");
        int c50 = NameColorManager.computeAnimationRgb("CHROMA", 0, 8, 1.0, 50L, "#FFFFFF", "#FFFFFF");
        int c100 = NameColorManager.computeAnimationRgb("CHROMA", 0, 8, 1.0, 100L, "#FFFFFF", "#FFFFFF");
        int char3At0 = NameColorManager.computeAnimationRgb("CHROMA", 3, 8, 1.0, 0L, "#FFFFFF", "#FFFFFF");

        assertNotEquals(c0, c50, "Smooth rainbow must update every frame rather than freezing for 150ms");
        assertNotEquals(c50, c100);
        assertNotEquals(c0, char3At0, "Characters across a username must form a rainbow gradient");

        float expectedHue = ((50L % 3000L) / 3000.0f) % 1.0f;
        int expectedRgb = ColorUtils.hsbToRgb(expectedHue, 1.0f, 1.0f) & 0x00FFFFFF;
        assertEquals(expectedRgb, c50);

        assertEquals(c50, NameColorManager.computeAnimationRgb("CHROMA", 0, 8, 0.0, 50L, "#FFFFFF", "#FFFFFF"));
        assertEquals(c50, NameColorManager.computeAnimationRgb("CHROMA", 0, 8, Double.NaN, 50L, "#FFFFFF", "#FFFFFF"));
    }

    @Test
    void computeAnimationRgbWaveBreatheBlinkAndGradientWorkCleanly() {
        int gradStart = NameColorManager.computeAnimationRgb("GRADIENT:#FF0000:#0000FF", 0, 5, 1.0, 0L, "#FFFFFF", "#FFFFFF");
        int gradMid = NameColorManager.computeAnimationRgb("GRADIENT:#FF0000:#0000FF", 2, 5, 1.0, 0L, "#FFFFFF", "#FFFFFF");
        int gradEnd = NameColorManager.computeAnimationRgb("GRADIENT:#FF0000:#0000FF", 4, 5, 1.0, 0L, "#FFFFFF", "#FFFFFF");

        assertEquals(0xFF0000, gradStart);
        assertEquals(0x800080, gradMid);
        assertEquals(0x0000FF, gradEnd);

        int blink0 = NameColorManager.computeAnimationRgb("BLINK:#112233:#AABBCC", 0, 5, 1.0, 100L, "#FFFFFF", "#FFFFFF");
        int blink1 = NameColorManager.computeAnimationRgb("BLINK:#112233:#AABBCC", 0, 5, 1.0, 600L, "#FFFFFF", "#FFFFFF");
        assertEquals(0x112233, blink0);
        assertEquals(0xAABBCC, blink1);

        int wave0 = NameColorManager.computeAnimationRgb("WAVE:#000000:#FFFFFF", 0, 6, 1.0, 0L, "#FFFFFF", "#FFFFFF");
        int wave500 = NameColorManager.computeAnimationRgb("WAVE:#000000:#FFFFFF", 0, 6, 1.0, 500L, "#FFFFFF", "#FFFFFF");
        assertNotEquals(wave0, wave500);

        int breathe0 = NameColorManager.computeAnimationRgb("BREATHE:#000000:#FFFFFF", 0, 6, 1.0, 0L, "#FFFFFF", "#FFFFFF");
        int breathe750 = NameColorManager.computeAnimationRgb("BREATHE:#000000:#FFFFFF", 0, 6, 1.0, 750L, "#FFFFFF", "#FFFFFF");
        assertNotEquals(breathe0, breathe750);
    }

    @Test
    void colorizeTextAppliesTabMarkerAndSmoothRgbForUuidBoundRenamedPlayer() {
        ModConfig.INSTANCE.cosmeticVisibility = 0;
        UUID uuid = UUID.fromString("3bf9f985-e9f5-451e-bd59-7c290677dc34");
        CustomPlayerManager.CustomPlayerData data = new CustomPlayerManager.CustomPlayerData(
                uuid, "CHROMA", 1, 1.0, false, "\uE001", 1.0f, 1.0f, 1.0f, 0.0f
        );
        CustomPlayerManager.setTestState(Map.of(uuid, data), Map.of(uuid, "OldAlias"));
        CustomPlayerManager.observePlayer(uuid, "RenamedHero");

        Component oldEntry = Component.literal("[VIP] OldAlias");
        assertSame(oldEntry, NameColorManager.colorizeText(oldEntry, false, true));

        Component tabEntry = Component.literal("[VIP] RenamedHero");
        Component colored = NameColorManager.colorizeText(tabEntry, false, true);
        assertEquals("[VIP] RenamedHero", colored.getString());
        assertEquals("wish:tab", colored.getStyle().getInsertion());
        assertFalse(colored.getSiblings().isEmpty());

        boolean foundBoldRgbChar = colored.getSiblings().stream().anyMatch(child -> {
            TextColor color = child.getStyle().getColor();
            return color != null && child.getStyle().isBold() && "R".equals(child.getString());
        });
        assertTrue(foundBoldRgbChar, "Renamed player must be rendered with smooth per-character RGB and custom font");
    }

    @Test
    void colorizeTextColorsAlreadySpoofedLocalPlayerInTabWithNameColorAndFont() {
        UUID localUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        NameColorManager.setTestLocalPlayer(localUuid, "Sorakkaa");
        ModConfig.INSTANCE.cosmeticVisibility = 0;
        ModConfig.INSTANCE.enableNameColor = true;
        ModConfig.INSTANCE.pseudoAnimation = 0;
        ModConfig.INSTANCE.customHexColor = "#FF55AA";
        ModConfig.INSTANCE.pseudoFont = 1;
        ModConfig.INSTANCE.spoofedName = "\u273F";

        Component alreadySpoofedTab = Component.literal("[487] \u273F \u2744");
        Component colored = NameColorManager.colorizeText(alreadySpoofedTab, false, true);

        assertEquals("[487] \u273F \u2744", colored.getString());
        assertEquals("wish:tab", colored.getStyle().getInsertion());
        Component flowerNode = findLeafContaining(colored, "\u273F");
        assertNotNull(flowerNode);
        assertNotNull(flowerNode.getStyle().getColor());
        assertEquals(0xFF55AA, flowerNode.getStyle().getColor().getValue());
        assertTrue(flowerNode.getStyle().isBold());
    }

    @Test
    void colorizeTextUsesNameColorFallbackWhenLocalCustomPlayerHasEmptyColorAndFontOnly() {
        UUID localUuid = UUID.fromString("22222222-3333-4444-5555-666666666666");
        NameColorManager.setTestLocalPlayer(localUuid, "Sorakkaa");
        CustomPlayerManager.CustomPlayerData emptyColorLocal = new CustomPlayerManager.CustomPlayerData(
                localUuid, "", 1, 1.0, false, "", 1.0f, 1.0f, 1.0f, 0.0f
        );
        CustomPlayerManager.setTestState(Map.of(localUuid, emptyColorLocal), Map.of(localUuid, "Sorakkaa"));

        ModConfig.INSTANCE.cosmeticVisibility = 0;
        ModConfig.INSTANCE.enableNameColor = true;
        ModConfig.INSTANCE.pseudoAnimation = 0;
        ModConfig.INSTANCE.customHexColor = "#33CCFF";
        ModConfig.INSTANCE.pseudoFont = 0;
        ModConfig.INSTANCE.spoofedName = "\u273F";

        Component colored = NameColorManager.colorizeText(Component.literal("[487] Sorakkaa \u2744"), false, true);
        assertEquals("[487] \u273F \u2744", colored.getString());

        Component flowerNode = findLeafContaining(colored, "\u273F");
        assertNotNull(flowerNode);
        assertNotNull(flowerNode.getStyle().getColor());
        assertEquals(0x33CCFF, flowerNode.getStyle().getColor().getValue());
        assertTrue(flowerNode.getStyle().isBold());
    }

    @Test
    void colorizeTextPreservesRankLegacyColorWhenSpoofedLocalPlayerHasNameColorDisabled() {
        UUID localUuid = UUID.fromString("33333333-4444-5555-6666-777777777777");
        NameColorManager.setTestLocalPlayer(localUuid, "Sorakkaa");
        ModConfig.INSTANCE.cosmeticVisibility = 0;
        ModConfig.INSTANCE.enableNameColor = false;
        ModConfig.INSTANCE.pseudoAnimation = 0;
        ModConfig.INSTANCE.customHexColor = "#FFFFFF";
        ModConfig.INSTANCE.pseudoFont = 0;
        ModConfig.INSTANCE.spoofedName = "\u273F";

        MutableComponent entry = Component.literal("[487] ").withStyle(ChatFormatting.RED)
                .append(Component.literal("Sorakkaa").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" \u2744").withStyle(ChatFormatting.AQUA));

        Component colored = NameColorManager.colorizeText(entry, false, true);
        assertEquals("[487] \u273F \u2744", colored.getString());

        Style flowerStyle = findEffectiveStyleContaining(colored, "\u273F");
        assertNotNull(flowerStyle);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), flowerStyle.getColor());

        Component legacyEntry = Component.literal("\u00A78[\u00A74487\u00A78] \u00A7bSorakkaa \u00A7b\u2744");
        Component coloredLegacy = NameColorManager.colorizeText(legacyEntry, false, true);
        assertTrue(coloredLegacy.getString().contains("\u273F"));
        Style legacyFlowerStyle = findEffectiveStyleContaining(coloredLegacy, "\u273F");
        assertNotNull(legacyFlowerStyle);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), legacyFlowerStyle.getColor());
    }

    @Test
    void colorizeTextColorsUnstyledWordReplacerAliasWhilePreservingExplicitWordReplacerStyle() {
        UUID localUuid = UUID.fromString("44444444-5555-6666-7777-888888888888");
        NameColorManager.setTestLocalPlayer(localUuid, "Sorakkaa");
        ModConfig.INSTANCE.cosmeticVisibility = 0;
        ModConfig.INSTANCE.enableNameColor = true;
        ModConfig.INSTANCE.pseudoAnimation = 0;
        ModConfig.INSTANCE.customHexColor = "#FF55AA";
        ModConfig.INSTANCE.pseudoFont = 2;
        ModConfig.INSTANCE.spoofedName = "";
        ModConfig.INSTANCE.enableWordReplacer = true;
        ModConfig.INSTANCE.wordReplacerWholeWord = true;
        ModConfig.INSTANCE.wordReplacerCaseSensitive = false;
        ModConfig.INSTANCE.wordReplacements = new CopyOnWriteArrayList<>(List.of(
                new ModConfig.WordReplacementRule("Sorakkaa", "\u273F", true)
        ));
        WordReplacerManager.invalidateCache();

        Component coloredUnstyled = NameColorManager.colorizeText(Component.literal("[487] Sorakkaa \u2744"), false, true);
        assertEquals("[487] \u273F \u2744", coloredUnstyled.getString());
        Component unstyledFlower = findLeafContaining(coloredUnstyled, "\u273F");
        assertNotNull(unstyledFlower);
        assertNotNull(unstyledFlower.getStyle().getColor());
        assertEquals(0xFF55AA, unstyledFlower.getStyle().getColor().getValue());
        assertTrue(unstyledFlower.getStyle().isItalic());

        ModConfig.INSTANCE.spoofedName = "\u273F";
        ModConfig.INSTANCE.pseudoFont = 0;
        ModConfig.INSTANCE.wordReplacements = new CopyOnWriteArrayList<>(List.of(
                new ModConfig.WordReplacementRule("Sorakkaa", "&a&l\u273F", true)
        ));
        WordReplacerManager.invalidateCache();

        Component preservedStyled = NameColorManager.colorizeText(Component.literal("[487] Sorakkaa \u2744"), false, true);
        assertEquals("[487] \u273F \u2744", preservedStyled.getString());
        Component styledFlower = findLeafContaining(preservedStyled, "\u273F");
        assertNotNull(styledFlower);
        assertNotNull(styledFlower.getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), styledFlower.getStyle().getColor());
        assertTrue(styledFlower.getStyle().isBold());
    }

    @Test
    void colorizeGuiAndTabSuppressBadgesWhileEntityNametagPreservesSingleBadge() {
        ModConfig.INSTANCE.cosmeticVisibility = 0;
        UUID uuid = UUID.fromString("55555555-6666-7777-8888-999999999999");
        CustomPlayerManager.CustomPlayerData badgePlayer = new CustomPlayerManager.CustomPlayerData(
                uuid, "#FF55AA", 0, 1.0, true, "\uE001", 1.0f, 1.0f, 1.0f, 0.0f
        );
        CustomPlayerManager.setTestState(Map.of(uuid, badgePlayer), Map.of(uuid, "TabHero"));

        Component tabColored = NameColorManager.colorizeText(Component.literal("[487] TabHero"), false, true);
        assertFalse(tabColored.getString().contains("\uE001"));
        assertFalse(tabColored.getString().contains("\uE002"));
        assertTrue(tabColored.getString().contains("TabHero"));

        Component guiColored = NameColorManager.colorizeGuiText(Component.literal("[487] TabHero"));
        assertFalse(guiColored.getString().contains("\uE001"), "Custom/2D Tab HUD must not receive Wish badge");
        assertFalse(guiColored.getString().contains("\uE002"));
        Component heroLeaf = findLeafContaining(guiColored, "TabHero");
        assertNotNull(heroLeaf);
        assertNotNull(heroLeaf.getStyle().getColor());
        assertEquals(0xFF55AA, heroLeaf.getStyle().getColor().getValue());

        Component entityTag = NameColorManager.colorizeText(Component.literal("TabHero"));
        String tagText = entityTag.getString();
        assertTrue(tagText.contains("\uE001"), "3D world nametag should still receive Wish badge");
        assertEquals(1, countOccurrences(tagText, "\uE001"));

        Component measuredByFont = NameColorManager.colorizeGuiText(entityTag);
        assertEquals(1, countOccurrences(measuredByFont.getString(), "\uE001"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Style findEffectiveStyleContaining(Component root, String needle) {
        return root.visit(
                (style, text) -> text.contains(needle) ? Optional.of(style) : Optional.empty(),
                Style.EMPTY
        ).orElse(null);
    }

    private static Component findLeafContaining(Component root, String needle) {
        if (root.getSiblings().isEmpty()) {
            return root.getString().contains(needle) ? root : null;
        }
        for (Component sibling : root.getSiblings()) {
            Component found = findLeafContaining(sibling, needle);
            if (found != null) {
                return found;
            }
        }
        return root.getString().contains(needle) ? root : null;
    }
}
