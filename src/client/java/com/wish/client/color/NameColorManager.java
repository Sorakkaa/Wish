package com.wish.client.color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wish.client.config.ModConfig;
import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameColorManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish");

    public static final String[] CHROMA_CODES = new String[]{"\u00A7c", "\u00A76", "\u00A7e", "\u00A7a", "\u00A7b", "\u00A79", "\u00A7d"};

    private static boolean isHex6(String s) {
        if (s == null || s.length() != 6) return false;
        for (int i = 0; i < 6; i++) {
            if (Character.digit(s.charAt(i), 16) == -1) return false;
        }
        return true;
    }

    public static String normalizeHex(String code) {
        if (code == null) return null;
        String s = code.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (isHex6(s)) {
            return "#" + s.toUpperCase(java.util.Locale.ROOT);
        }
        return null;
    }

    public static String getCurrentColorCode() {
        int anim = ModConfig.INSTANCE.pseudoAnimation;
        if (ModConfig.INSTANCE.pseudoAnimationSpeed == 0.0f) ModConfig.INSTANCE.pseudoAnimationSpeed = 1.0f;
        String norm1 = normalizeHex(ModConfig.INSTANCE.customHexColor);
        if (norm1 == null) norm1 = "#FFC6F9";
        String norm2 = normalizeHex(ModConfig.INSTANCE.customHexColor2);
        if (norm2 == null) norm2 = "#FFFFFF";

        if (anim == 1) return "CHROMA";
        if (anim == 2) return "GRADIENT:" + norm1 + ":" + norm2;
        if (anim == 3) return "WAVE:" + norm1 + ":" + norm2;
        if (anim == 4) return "BREATHE:" + norm1 + ":" + norm2;
        if (anim == 5) return "BLINK:" + norm1 + ":" + norm2;

        return norm1;
    }

    private static volatile UUID testLocalUuid = null;
    private static volatile String testLocalName = null;

    static void setTestLocalPlayer(UUID uuid, String name) {
        testLocalUuid = uuid;
        testLocalName = name;
        invalidateTargetCache();
    }

    private static Minecraft getMinecraftOrNull() {
        try {
            return Minecraft.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID resolveLocalUuid(Minecraft mc) {
        if (mc != null && mc.player != null) {
            return mc.player.getUUID();
        }
        return testLocalUuid;
    }

    private static String resolveLocalName(Minecraft mc) {
        if (mc != null && mc.player != null) {
            return mc.player.getScoreboardName();
        }
        return testLocalName;
    }

    private static boolean isLocalPlayerName(Minecraft mc, String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String localName = resolveLocalName(mc);
        return localName != null && localName.equalsIgnoreCase(name);
    }

    public static String getColorForPlayer(UUID uuid, String name) {
        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
        if (cosMode == 2) return null;

        var mc = getMinecraftOrNull();
        UUID localUuid = resolveLocalUuid(mc);
        String localName = resolveLocalName(mc);
        boolean isLocal = (localUuid != null && localUuid.equals(uuid))
                || (localName != null && localName.equalsIgnoreCase(name));
        if (cosMode == 1 && !isLocal) return null;

        CustomPlayerManager.CustomPlayerData customData = CustomPlayerManager.getPlayer(uuid);
        if (customData == null && isLocal && localUuid != null) {
            customData = CustomPlayerManager.getPlayer(localUuid);
        }
        if (customData != null && customData.color() != null && !customData.color().isEmpty()) {
            return customData.color();
        }
        if (isLocal) {
            return ModConfig.INSTANCE.enableNameColor ? getCurrentColorCode() : "";
        }
        return null;
    }

    public static volatile boolean isRenderingTab = false;
    public static volatile boolean isRenderingScoreboard = false;

    private static final AtomicLong TARGET_CACHE_VERSION = new AtomicLong(0L);
    private static volatile ComponentTargetSnapshot cachedComponentTargets = null;
    private static volatile AnimatedTargetSnapshot cachedAnimatedTargets = null;
    private static final ThreadLocal<StringBuilder> ANIMATED_LINE_SB = ThreadLocal.withInitial(() -> new StringBuilder(128));

    public static void invalidateTargetCache() {
        TARGET_CACHE_VERSION.incrementAndGet();
        cachedComponentTargets = null;
        cachedAnimatedTargets = null;
    }

    private record TargetCacheKey(
            long version,
            long wordReplacerVersion,
            int cosMode,
            boolean enableNameColor,
            int pseudoAnimation,
            float pseudoAnimationSpeed,
            String customHex1,
            String customHex2,
            String spoofedName,
            int pseudoFont,
            UUID localUuid,
            String localName
    ) {
        static TargetCacheKey capture(long version, long wordReplacerVersion, int cosMode, Minecraft mc) {
            ModConfig cfg = ModConfig.INSTANCE;
            return new TargetCacheKey(
                    version,
                    wordReplacerVersion,
                    cosMode,
                    cfg.enableNameColor,
                    cfg.pseudoAnimation,
                    cfg.pseudoAnimationSpeed,
                    cfg.customHexColor,
                    cfg.customHexColor2,
                    cfg.spoofedName,
                    cfg.pseudoFont,
                    resolveLocalUuid(mc),
                    resolveLocalName(mc)
            );
        }

        boolean matches(long version, long wordReplacerVersion, int cosMode, ModConfig cfg, UUID uuid, String name) {
            return this.version == version
                    && this.wordReplacerVersion == wordReplacerVersion
                    && this.cosMode == cosMode
                    && this.enableNameColor == cfg.enableNameColor
                    && this.pseudoAnimation == cfg.pseudoAnimation
                    && Float.compare(this.pseudoAnimationSpeed, cfg.pseudoAnimationSpeed) == 0
                    && Objects.equals(this.customHex1, cfg.customHexColor)
                    && Objects.equals(this.customHex2, cfg.customHexColor2)
                    && Objects.equals(this.spoofedName, cfg.spoofedName)
                    && this.pseudoFont == cfg.pseudoFont
                    && Objects.equals(this.localUuid, uuid)
                    && Objects.equals(this.localName, name);
        }
    }

    private record AnimTarget(String name, String colorCode, String nameLower) {}

    private record ComponentTargetSnapshot(
            TargetCacheKey key,
            List<String> targetNames,
            Map<String, String> nameToColor,
            Map<String, String> aliasToCanonicalLower
    ) {}

    private record AnimatedTargetSnapshot(TargetCacheKey key, List<AnimTarget> targets) {}

    public static boolean isTabOverlayActive() {
        return isRenderingTab;
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component) {
        return colorizeText(component, false);
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component, boolean isChat) {
        return colorizeText(component, isChat, isTabOverlayActive());
    }

    public static net.minecraft.network.chat.Component colorizeGuiText(net.minecraft.network.chat.Component component) {
        return colorizeText(component, false, isTabOverlayActive(), isRenderingScoreboard, false);
    }

    private static boolean containsTabMarker(net.minecraft.network.chat.Component component) {
        if (component == null) return false;
        if ("wish:tab".equals(component.getStyle().getInsertion())) return true;
        for (net.minecraft.network.chat.Component child : component.getSiblings()) {
            if (containsTabMarker(child)) return true;
        }
        return false;
    }

    private static boolean containsBadgeComponent(net.minecraft.network.chat.Component component) {
        if (component == null) return false;
        if (FONT_BADGES.equals(component.getStyle().getFont())) return true;
        if (component.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            String t = plain.text();
            if (t.indexOf('\uE001') != -1 || t.indexOf('\uE002') != -1) {
                return true;
            }
        }
        for (net.minecraft.network.chat.Component child : component.getSiblings()) {
            if (containsBadgeComponent(child)) return true;
        }
        return false;
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component, boolean isChat, boolean isTab) {
        return colorizeText(component, isChat, isTab, isRenderingScoreboard);
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component, boolean isChat, boolean isTab, boolean isScoreboard) {
        return colorizeText(component, isChat, isTab, isScoreboard, !(isChat || isTab || isScoreboard));
    }

    public static net.minecraft.network.chat.Component colorizeText(
            net.minecraft.network.chat.Component component,
            boolean isChat,
            boolean isTab,
            boolean isScoreboard,
            boolean allowBadge
    ) {
        if (component == null) return null;
        if ("wish:tab".equals(component.getStyle().getInsertion())) {
            return component;
        }
        if (!isTab && containsTabMarker(component)) {
            return component;
        }
        try {
            net.minecraft.network.chat.Component wrReplaced = WordReplacerManager.replaceComponent(component);
            boolean effectiveAllowBadge = allowBadge && !isChat && !isTab && !isScoreboard && !containsBadgeComponent(wrReplaced);
            net.minecraft.network.chat.Component result = modifyComponent(wrReplaced, isChat, isTab, isScoreboard, effectiveAllowBadge);
            if (isTab && result != null && result != component) {
                return result.copy().withStyle(result.getStyle().withInsertion("wish:tab"));
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("[Wish] Error in colorizeText", e);
            return component;
        }
    }

    public static String stripMiniMessageTags(String text) {
        return ComponentTextMatcher.stripMiniMessageTags(text);
    }

    private static boolean isUsernameChar(char c) {
        return ComponentTextMatcher.isUsernameChar(c);
    }

    public static final net.minecraft.network.chat.FontDescription FONT_BADGES = new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("wish", "badges"));

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00A7[0-9a-fA-Fk-rK-R]");
    private static final Pattern LEADING_COLOR_CODE_PATTERN = Pattern.compile("^(?:\u00A7[0-9a-fA-Fk-rK-R])+");
    private static final Pattern TRAILING_COLOR_CODE_PATTERN = Pattern.compile("(\u00A7[0-9a-fA-Fk-rK-R])+$");
    private static final Pattern BOLD_CODE_PATTERN = Pattern.compile("\u00A7[lL]");
    private static final Pattern ITALIC_CODE_PATTERN = Pattern.compile("\u00A7[oO]");
    private static final Pattern BOLD_OR_ITALIC_CODE_PATTERN = Pattern.compile("\u00A7[lLoO]");
    private static final Pattern RANK_OR_LEVEL_PATTERN = Pattern.compile(
        "^(?:(\\[?\\d+\\]?\\s*)+|\\[(VIP\\+?|MVP\\+{0,2}|YOUTUBE|ADMIN|MOD|HELPER|BUILD TEAM|OWNER|PIG\\+{0,3}|GM)\\]|(\\[?\\d+\\]?\\s*)+\\s*\\[(VIP\\+?|MVP\\+{0,2}|YOUTUBE|ADMIN|MOD|HELPER|BUILD TEAM|OWNER|PIG\\+{0,3}|GM)\\])$"
    );

    private static final Pattern EMBLEM_PREFIX_PATTERN = Pattern.compile(
        "^((?:\\u00A7[0-9a-fA-Fk-rK-R])*(?:\\uD83D\\uDD37|\\u25C6|\\u25C7|\\u25C8|\\u2756|\\u2728|\\u2727|\\u2605|\\u2B50|\\u2606|\\u2694))(.*)$"
    );

    private static boolean isEmblemOnly(String s) {
        if (s == null) return false;
        String clean = COLOR_CODE_PATTERN.matcher(s).replaceAll("").trim();
        return clean.equals("\uD83D\uDD37") || clean.equals("\u25C6") || clean.equals("\u25C7") || clean.equals("\u25C8")
                || clean.equals("\u2756") || clean.equals("\u2728") || clean.equals("\u2727") || clean.equals("\u2605")
                || clean.equals("\u2B50") || clean.equals("\u2606") || clean.equals("\u2694");
    }

    private static boolean isRankOrLevelString(String s) {
        if (s == null) return false;
        String clean = COLOR_CODE_PATTERN.matcher(s).replaceAll("").trim();
        return !clean.isEmpty() && RANK_OR_LEVEL_PATTERN.matcher(clean).matches();
    }

    public static net.minecraft.network.chat.MutableComponent createBadgeComponent() {
        return createBadgeComponent("\uE001");
    }

    public static net.minecraft.network.chat.MutableComponent createBadgeComponent(String charCode) {
        net.minecraft.network.chat.Style badgeStyle = net.minecraft.network.chat.Style.EMPTY
            .withColor(0xFFFFFFFF)
            .withBold(false)
            .withItalic(false)
            .withUnderlined(false)
            .withStrikethrough(false)
            .withObfuscated(false)
            .withFont(FONT_BADGES)
            .withoutShadow();
        String c = (charCode != null && !charCode.isEmpty()) ? charCode : "\uE001";
        return net.minecraft.network.chat.Component.literal(c).withStyle(badgeStyle);
    }
    private static String getLastColors(String text) {
        StringBuilder colors = new StringBuilder();
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\u00A7' || text.charAt(i) == '&') {
                char c = text.charAt(i + 1);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == 'r' || c == 'R') {
                    colors.setLength(0);
                    colors.append('\u00A7').append(c);
                } else if ((c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O')) {
                    colors.append('\u00A7').append(c);
                }
            }
        }
        return colors.toString();
    }

    private static void appendPrefixAndBadge(net.minecraft.network.chat.MutableComponent builder, String finalBefore, boolean addBadge, String badgeCode) {
        if (!addBadge) {
            if (!finalBefore.isEmpty()) {
                builder.append(net.minecraft.network.chat.Component.literal(finalBefore));
            }
            return;
        }

        if (finalBefore.isEmpty()) {
            builder.append(createBadgeComponent(badgeCode));
            builder.append(net.minecraft.network.chat.Component.literal(" "));
            return;
        }

        Matcher m = EMBLEM_PREFIX_PATTERN.matcher(finalBefore);
        if (m.matches()) {
            String emblemPart = m.group(1).stripTrailing();
            String restPart = m.group(2).stripLeading();
            String carry = getLastColors(emblemPart);
            builder.append(net.minecraft.network.chat.Component.literal(emblemPart));
            builder.append(createBadgeComponent(badgeCode));
            builder.append(net.minecraft.network.chat.Component.literal(" "));
            if (!restPart.isEmpty()) {
                builder.append(net.minecraft.network.chat.Component.literal(carry + restPart));
                if (!restPart.endsWith(" ")) {
                    builder.append(net.minecraft.network.chat.Component.literal(" "));
                }
            }
        } else {
            int rankIdx = finalBefore.indexOf('[');
            if (rankIdx != -1) {
                String p1 = finalBefore.substring(0, rankIdx);
                String p2 = finalBefore.substring(rankIdx);
                String carry = getLastColors(p1);
                builder.append(net.minecraft.network.chat.Component.literal(p1));
                builder.append(createBadgeComponent(badgeCode));
                builder.append(net.minecraft.network.chat.Component.literal(" "));
                builder.append(net.minecraft.network.chat.Component.literal(carry + p2));
            } else {
                builder.append(createBadgeComponent(badgeCode));
                builder.append(net.minecraft.network.chat.Component.literal(" "));
                builder.append(net.minecraft.network.chat.Component.literal(finalBefore));
            }
            if (!finalBefore.endsWith(" ")) {
                builder.append(net.minecraft.network.chat.Component.literal(" "));
            }
        }
    }

    private static void reorderBadgeInSiblings(java.util.List<net.minecraft.network.chat.Component> siblings) {
        int badgeOwnerIdx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            net.minecraft.network.chat.Component comp = siblings.get(i);
            String str = comp.getString();
            if ((str.startsWith("\uE001") || str.startsWith("\uE002")) && comp instanceof net.minecraft.network.chat.MutableComponent) {
                badgeOwnerIdx = i;
                break;
            }
        }
        if (badgeOwnerIdx <= 0) return;

        net.minecraft.network.chat.MutableComponent badgeOwner = (net.minecraft.network.chat.MutableComponent) siblings.get(badgeOwnerIdx);
        net.minecraft.network.chat.Component badgeComp = null;
        if (!badgeOwner.getSiblings().isEmpty()) {
            String firstStr = badgeOwner.getSiblings().get(0).getString();
            if (firstStr.startsWith("\uE001") || firstStr.startsWith("\uE002")) {
                badgeComp = badgeOwner.getSiblings().remove(0);
                if (!badgeOwner.getSiblings().isEmpty() && " ".equals(badgeOwner.getSiblings().get(0).getString())) {
                    badgeOwner.getSiblings().remove(0);
                }
            } else {
                return;
            }
        } else {
            return;
        }

        net.minecraft.network.chat.MutableComponent badgeWithSpace = net.minecraft.network.chat.Component.literal("");
        badgeWithSpace.append(badgeComp);
        badgeWithSpace.append(net.minecraft.network.chat.Component.literal(" "));

        for (int k = 0; k < badgeOwnerIdx; k++) {
            net.minecraft.network.chat.Component prev = siblings.get(k);
            String prevStr = prev.getString();
            Matcher m = EMBLEM_PREFIX_PATTERN.matcher(prevStr);
            if (m.matches()) {
                String emb = m.group(1).stripTrailing();
                String rest = m.group(2).stripLeading();
                if (!rest.isEmpty()) {
                    siblings.remove(k);
                    net.minecraft.network.chat.Component embComp = net.minecraft.network.chat.Component.literal(emb).withStyle(prev.getStyle());
                    net.minecraft.network.chat.Component restComp = net.minecraft.network.chat.Component.literal(rest.endsWith(" ") ? rest : rest + " ").withStyle(prev.getStyle());
                    siblings.add(k, restComp);
                    siblings.add(k, badgeWithSpace);
                    siblings.add(k, embComp);
                    return;
                }
            }
        }

        int emblemPos = -1;
        for (int k = 0; k < badgeOwnerIdx; k++) {
            if (isEmblemOnly(siblings.get(k).getString())) {
                emblemPos = k;
                break;
            }
        }

        if (emblemPos != -1) {
            net.minecraft.network.chat.Component embComp = siblings.get(emblemPos);
            String embStr = embComp.getString();
            if (embStr.endsWith(" ")) {
                siblings.set(emblemPos, net.minecraft.network.chat.Component.literal(embStr.stripTrailing()).withStyle(embComp.getStyle()));
            }
            siblings.add(emblemPos + 1, badgeWithSpace);
            return;
        }

        int firstRankLevelPos = -1;
        for (int k = 0; k < badgeOwnerIdx; k++) {
            if (isRankOrLevelString(siblings.get(k).getString())) {
                firstRankLevelPos = k;
                break;
            }
        }

        if (firstRankLevelPos != -1) {
            siblings.add(firstRankLevelPos, badgeWithSpace);
            return;
        }

        siblings.add(badgeOwnerIdx, badgeWithSpace);
    }

    private static String resolveCanonicalLower(String nameLower) {
        if (nameLower == null || nameLower.isEmpty()) {
            return "";
        }
        ComponentTargetSnapshot snap = cachedComponentTargets;
        if (snap != null && !snap.aliasToCanonicalLower().isEmpty()) {
            String canonical = snap.aliasToCanonicalLower().get(nameLower);
            if (canonical != null) {
                return canonical;
            }
        }
        String spoof = ModConfig.INSTANCE.spoofedName;
        if (spoof != null) {
            String trimmedSpoof = spoof.trim();
            String localName = resolveLocalName(getMinecraftOrNull());
            if (isSafeTargetAlias(trimmedSpoof, localName) && trimmedSpoof.equalsIgnoreCase(nameLower)) {
                return localName.toLowerCase(Locale.ROOT);
            }
        }
        return nameLower;
    }

    private static boolean isSafeTargetAlias(String alias, String canonicalName) {
        if (alias == null || alias.isEmpty() || canonicalName == null || canonicalName.isEmpty()) {
            return false;
        }
        if (alias.equalsIgnoreCase(canonicalName)) {
            return false;
        }
        if (alias.length() >= 2) {
            return true;
        }
        char ch = alias.charAt(0);
        return !(Character.isLetterOrDigit(ch) && ch < 128);
    }

    private static boolean wordReplacerRuleMatchesName(WordReplacerManager.CompiledRule rule, String name, boolean caseSensitive) {
        return caseSensitive ? rule.from().equals(name) : rule.from().equalsIgnoreCase(name);
    }

    private static boolean hasExplicitWordReplacerRuleFor(
            WordReplacerManager.Snapshot wrSnap,
            String sourceName,
            String replacementTarget
    ) {
        if (wrSnap == null || !wrSnap.enabled() || sourceName == null || replacementTarget == null) {
            return false;
        }
        for (WordReplacerManager.CompiledRule rule : wrSnap.rules()) {
            if (rule.hasExplicitStyle()
                    && wordReplacerRuleMatchesName(rule, sourceName, wrSnap.caseSensitive())
                    && replacementTarget.equalsIgnoreCase(WordReplacerManager.stripFormattingCodes(rule.to()))) {
                return true;
            }
        }
        return false;
    }

    private static void registerUnstyledWordReplacerAliases(
            WordReplacerManager.Snapshot wrSnap,
            String canonicalName,
            String canonicalLower,
            String canonicalColor,
            List<String> targetNames,
            Map<String, String> nameToColor,
            Map<String, String> aliasToCanonicalLower
    ) {
        if (wrSnap == null || !wrSnap.enabled() || canonicalName == null || canonicalName.isEmpty()) {
            return;
        }
        for (WordReplacerManager.CompiledRule rule : wrSnap.rules()) {
            if (rule.hasExplicitStyle() || !wordReplacerRuleMatchesName(rule, canonicalName, wrSnap.caseSensitive())) {
                continue;
            }
            String alias = rule.to();
            if (isSafeTargetAlias(alias, canonicalName)) {
                String aliasLower = alias.toLowerCase(Locale.ROOT);
                if (!nameToColor.containsKey(aliasLower)) {
                    if (!targetNames.contains(alias)) targetNames.add(alias);
                    nameToColor.put(aliasLower, canonicalColor);
                    aliasToCanonicalLower.put(aliasLower, canonicalLower);
                }
            }
        }
    }

    private static net.minecraft.network.chat.Style applyTrailingLegacyColors(
            net.minecraft.network.chat.Style base,
            String trailingColors,
            boolean keepBold,
            boolean keepItalic
    ) {
        if (trailingColors == null || trailingColors.isEmpty()) {
            return base;
        }
        net.minecraft.network.chat.Style result = base;
        for (int i = 0; i < trailingColors.length() - 1; i++) {
            if (trailingColors.charAt(i) != '\u00A7') {
                continue;
            }
            char c = Character.toLowerCase(trailingColors.charAt(i + 1));
            if ((c == 'l' && !keepBold) || (c == 'o' && !keepItalic)) {
                i++;
                continue;
            }
            net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(c);
            if (fmt != null) {
                result = result.applyLegacyFormat(fmt);
            }
            i++;
        }
        return result;
    }

    private static net.minecraft.network.chat.Style applyCustomFont(net.minecraft.network.chat.Style st, String nameLower) {
        var mc = getMinecraftOrNull();
        String canonicalLower = resolveCanonicalLower(nameLower);
        CustomPlayerManager.CustomPlayerData customData = resolveCustomPlayer(mc, canonicalLower);
        if (customData != null) {
            return applyFontBitmask(st, customData.font());
        } else if (isLocalPlayerName(mc, canonicalLower)) {
            return applyFontBitmask(st, ModConfig.INSTANCE.pseudoFont);
        }
        return st;
    }

    private static CustomPlayerManager.CustomPlayerData resolveCustomPlayer(Minecraft mc, String nameLower) {
        String canonicalLower = resolveCanonicalLower(nameLower);
        CustomPlayerManager.CustomPlayerData customData = CustomPlayerManager.getPlayerByResolvedName(canonicalLower);
        if (customData == null && isLocalPlayerName(mc, canonicalLower)) {
            UUID localUuid = resolveLocalUuid(mc);
            if (localUuid != null) {
                customData = CustomPlayerManager.getPlayer(localUuid);
            }
        }
        return customData;
    }

    private static net.minecraft.network.chat.Style applyFontBitmask(net.minecraft.network.chat.Style st, int fontBits) {
        st = st.withBold((fontBits & 1) != 0);
        st = st.withItalic((fontBits & 2) != 0);
        st = st.withUnderlined((fontBits & 4) != 0);
        st = st.withStrikethrough((fontBits & 8) != 0);
        return st.withObfuscated((fontBits & 16) != 0);
    }

    private static ComponentTargetSnapshot getOrBuildComponentTargets(Minecraft mc, int cosMode) {
        CustomPlayerManager.syncLiveBindings(mc, false);
        long version = TARGET_CACHE_VERSION.get();
        long wrVersion = WordReplacerManager.getVersion();
        ModConfig cfg = ModConfig.INSTANCE;
        UUID localUuid = resolveLocalUuid(mc);
        String localName = resolveLocalName(mc);

        ComponentTargetSnapshot existing = cachedComponentTargets;
        if (existing != null && existing.key().matches(version, wrVersion, cosMode, cfg, localUuid, localName)) {
            return existing;
        }

        List<String> targetNames = new ArrayList<>();
        Map<String, String> nameToColor = new HashMap<>();
        Map<String, String> aliasToCanonicalLower = new HashMap<>();
        WordReplacerManager.Snapshot wrSnap = WordReplacerManager.getActiveSnapshot();

        if (cosMode == 0) {
            for (CustomPlayerManager.ResolvedCustomPlayer rp : CustomPlayerManager.getResolvedPlayers()) {
                CustomPlayerManager.CustomPlayerData cp = rp.data();
                String liveName = rp.liveName();
                String liveLower = liveName.toLowerCase(Locale.ROOT);
                String col = (cp.color() != null && !cp.color().isEmpty())
                        ? cp.color()
                        : ((cp.font() != 0 || cp.badge()) ? "PLAIN" : "");
                if (!targetNames.contains(liveName)) targetNames.add(liveName);
                nameToColor.put(liveLower, col);
                if (!col.isEmpty()) {
                    registerUnstyledWordReplacerAliases(wrSnap, liveName, liveLower, col, targetNames, nameToColor, aliasToCanonicalLower);
                }
            }
        }

        if (localName != null && !localName.isEmpty()) {
            String localNameLower = localName.toLowerCase(Locale.ROOT);
            CustomPlayerManager.CustomPlayerData localCustom = localUuid != null ? CustomPlayerManager.getPlayer(localUuid) : null;
            String localColor = "";
            boolean hasSpoofConfigured = cfg.spoofedName != null && !cfg.spoofedName.trim().isEmpty();

            if (localCustom != null) {
                if (localCustom.color() != null && !localCustom.color().isEmpty()) {
                    localColor = localCustom.color();
                } else if (cfg.enableNameColor) {
                    localColor = getCurrentColorCode();
                } else if (localCustom.font() != 0 || localCustom.badge() || hasSpoofConfigured || cfg.pseudoFont != 0) {
                    localColor = "PLAIN";
                }
            } else {
                if (cfg.enableNameColor) {
                    localColor = getCurrentColorCode();
                } else if (hasSpoofConfigured || cfg.pseudoFont != 0) {
                    localColor = "PLAIN";
                }
            }

            if (!targetNames.contains(localName)) targetNames.add(localName);
            nameToColor.put(localNameLower, localColor);

            if (!localColor.isEmpty()) {
                registerUnstyledWordReplacerAliases(wrSnap, localName, localNameLower, localColor, targetNames, nameToColor, aliasToCanonicalLower);
                if (hasSpoofConfigured) {
                    String trimmedSpoof = cfg.spoofedName.trim();
                    if (isSafeTargetAlias(trimmedSpoof, localName)
                            && !hasExplicitWordReplacerRuleFor(wrSnap, localName, trimmedSpoof)) {
                        String spoofLower = trimmedSpoof.toLowerCase(Locale.ROOT);
                        if (!nameToColor.containsKey(spoofLower)) {
                            if (!targetNames.contains(trimmedSpoof)) targetNames.add(trimmedSpoof);
                            nameToColor.put(spoofLower, localColor);
                            aliasToCanonicalLower.put(spoofLower, localNameLower);
                        }
                    }
                }
            }
        }

        targetNames.sort((a, b) -> Integer.compare(b.length(), a.length()));
        ComponentTargetSnapshot rebuilt = new ComponentTargetSnapshot(
                TargetCacheKey.capture(version, wrVersion, cosMode, mc),
                List.copyOf(targetNames),
                Map.copyOf(nameToColor),
                Map.copyOf(aliasToCanonicalLower)
        );
        cachedComponentTargets = rebuilt;
        return rebuilt;
    }

    private static net.minecraft.network.chat.Component modifyComponent(
            net.minecraft.network.chat.Component component,
            boolean isChat,
            boolean isTab,
            boolean isScoreboard,
            boolean allowBadge) {
        if (component == null) return null;

        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
        if (cosMode == 2) {
            return ComponentTextMatcher.stripMiniMessageFromComponent(component);
        }

        var mc = getMinecraftOrNull();
        ComponentTargetSnapshot snapshot = getOrBuildComponentTargets(mc, cosMode);
        if (snapshot.targetNames().isEmpty()) {
            return ComponentTextMatcher.stripMiniMessageFromComponent(component);
        }

        return recursivelyModify(
                component,
                snapshot.targetNames(),
                snapshot.nameToColor(),
                snapshot.aliasToCanonicalLower(),
                isChat,
                isTab,
                isScoreboard,
                allowBadge
        );
    }

    private static void mergePossessiveAcrossSiblings(java.util.List<net.minecraft.network.chat.Component> siblings, java.util.List<String> targetNames) {
        for (int i = 0; i < siblings.size() - 1; i++) {
            net.minecraft.network.chat.Component cur = siblings.get(i);
            net.minecraft.network.chat.Component next = siblings.get(i + 1);
            if (cur == null || next == null) continue;
            String curText = cur.getString();
            String nextText = next.getString();
            if (curText == null || nextText == null) continue;
            String cleanCur = COLOR_CODE_PATTERN.matcher(curText).replaceAll("").trim();
            String cleanNext = COLOR_CODE_PATTERN.matcher(nextText).replaceAll("").trim();
            boolean matchesName = false;
            for (String tName : targetNames) {
                if (cleanCur.equalsIgnoreCase(tName) || cleanCur.toLowerCase().endsWith(tName.toLowerCase())) {
                    matchesName = true;
                    break;
                }
            }
            if (matchesName && (cleanNext.startsWith("'s") || cleanNext.startsWith("'S"))) {
                int sIdx = nextText.indexOf("'s");
                if (sIdx == -1) sIdx = nextText.indexOf("'S");
                if (sIdx != -1) {
                    String nextRem = nextText.substring(sIdx + 2);
                    net.minecraft.network.chat.MutableComponent newCur = cur.copy();
                    newCur.append(net.minecraft.network.chat.Component.literal(nextText.substring(sIdx, sIdx + 2)).withStyle(cur.getStyle()));
                    siblings.set(i, newCur);

                    net.minecraft.network.chat.MutableComponent newNext = net.minecraft.network.chat.Component.literal(nextRem).withStyle(next.getStyle());
                    for (net.minecraft.network.chat.Component ch : next.getSiblings()) {
                        newNext.append(ch);
                    }
                    siblings.set(i + 1, newNext);
                }
            }
        }
    }

    private static net.minecraft.network.chat.Component recursivelyModify(
            net.minecraft.network.chat.Component component,
            java.util.List<String> targetNames,
            Map<String, String> nameToColor,
            Map<String, String> aliasToCanonicalLower,
            boolean isChat,
            boolean isTab,
            boolean isScoreboard,
            boolean allowBadge) {
        if (component == null) return null;
        if ("wish:tab".equals(component.getStyle().getInsertion())) return component;

        boolean selfChanged = false;
        net.minecraft.network.chat.Component newSelf = null;

        java.util.List<net.minecraft.network.chat.Component> rawSiblings = component.getSiblings();
        boolean hasSiblings = !rawSiblings.isEmpty();
        java.util.List<net.minecraft.network.chat.Component> originalSiblings = hasSiblings
                ? new java.util.ArrayList<>(rawSiblings)
                : java.util.List.of();

        net.minecraft.network.chat.ComponentContents coalesceContents = component.getContents();
        boolean coalescedChanged = false;
        String coalescedSelfText = null;
        if (hasSiblings) {
            mergePossessiveAcrossSiblings(originalSiblings, targetNames);
            String selfRawForCoalesce = (coalesceContents instanceof net.minecraft.network.chat.contents.PlainTextContents plainForCoalesce)
                    ? plainForCoalesce.text()
                    : null;
            ComponentTextMatcher.CoalesceResult coalesceResult = ComponentTextMatcher.coalesceSplitTargetNames(
                    selfRawForCoalesce,
                    component.getStyle(),
                    originalSiblings,
                    targetNames);
            if (coalesceResult.changed()) {
                coalescedChanged = true;
                coalescedSelfText = coalesceResult.selfText();
                originalSiblings = new java.util.ArrayList<>(coalesceResult.siblings());
            }
        }

        var contents = component.getContents();
        if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents trans) {
            Object[] args = trans.getArgs();
            Object[] newArgs = new Object[args.length];
            boolean argsChanged = false;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof net.minecraft.network.chat.Component argComp) {
                    net.minecraft.network.chat.Component modifiedArg = recursivelyModify(argComp, targetNames, nameToColor, aliasToCanonicalLower, isChat, isTab, isScoreboard, allowBadge);
                    newArgs[i] = modifiedArg;
                    if (modifiedArg != argComp) argsChanged = true;
                } else if (arg instanceof String argStr) {
                    net.minecraft.network.chat.Component argComp = net.minecraft.network.chat.Component.literal(argStr);
                    net.minecraft.network.chat.Component modifiedArg = recursivelyModify(argComp, targetNames, nameToColor, aliasToCanonicalLower, isChat, isTab, isScoreboard, allowBadge);
                    if (modifiedArg != argComp) {
                        newArgs[i] = modifiedArg;
                        argsChanged = true;
                    } else {
                        newArgs[i] = argStr;
                    }
                } else {
                    newArgs[i] = arg;
                }
            }
            if (argsChanged) {
                selfChanged = true;
                newSelf = net.minecraft.network.chat.Component.translatable(trans.getKey(), newArgs).withStyle(component.getStyle());
            }
        } else if (contents instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            String rawText = (coalescedChanged && coalescedSelfText != null) ? coalescedSelfText : plain.text();
            if (coalescedChanged && coalescedSelfText != null && !coalescedSelfText.equals(plain.text())) {
                selfChanged = true;
                newSelf = net.minecraft.network.chat.Component.literal(rawText).withStyle(component.getStyle());
            }
            if (rawText != null && !rawText.isEmpty()) {
                if (!originalSiblings.isEmpty()) {
                    net.minecraft.network.chat.Component firstChild = originalSiblings.get(0);
                    String firstStr = firstChild.getString();
                    String cleanFirst = COLOR_CODE_PATTERN.matcher(firstStr).replaceAll("").trim();
                    if (cleanFirst.startsWith("'s") || cleanFirst.startsWith("'S")) {
                        String cleanRaw = COLOR_CODE_PATTERN.matcher(rawText).replaceAll("").trim();
                        for (String tName : targetNames) {
                            if (cleanRaw.equalsIgnoreCase(tName) || cleanRaw.toLowerCase().endsWith(tName.toLowerCase())) {
                                int sIdx = firstStr.indexOf("'s");
                                if (sIdx == -1) sIdx = firstStr.indexOf("'S");
                                if (sIdx != -1) {
                                    rawText += firstStr.substring(sIdx, sIdx + 2);
                                    String rem = firstStr.substring(sIdx + 2);
                                    net.minecraft.network.chat.MutableComponent newFirst = net.minecraft.network.chat.Component.literal(rem).withStyle(firstChild.getStyle());
                                    for (net.minecraft.network.chat.Component ch : firstChild.getSiblings()) {
                                        newFirst.append(ch);
                                    }
                                    originalSiblings.set(0, newFirst);
                                }
                                break;
                            }
                        }
                    }
                }

                String text = stripMiniMessageTags(rawText);

                if (text.contains("http://") || text.contains("https://")) {
                    return component;
                }

                if (!text.equals(rawText)) {
                    selfChanged = true;
                    newSelf = net.minecraft.network.chat.Component.literal(text).withStyle(component.getStyle());
                }

                ComponentTextMatcher.TargetSpanMatch spanMatch = ComponentTextMatcher.findFirstTargetMatch(text, targetNames);
                if (spanMatch != null) {
                    String name = targetNames.get(spanMatch.targetIndex());
                    String nameLower = name.toLowerCase(Locale.ROOT);
                    String canonicalLower = aliasToCanonicalLower.getOrDefault(nameLower, nameLower);
                    String colorCode = nameToColor.get(nameLower);
                    var mc = getMinecraftOrNull();
                    boolean isLocal = isLocalPlayerName(mc, canonicalLower);
                    CustomPlayerManager.CustomPlayerData customData = resolveCustomPlayer(mc, canonicalLower);
                    boolean hasSpoof = isLocal && ModConfig.INSTANCE.spoofedName != null && !ModConfig.INSTANCE.spoofedName.trim().isEmpty();
                    if ((colorCode != null && !colorCode.isEmpty()) || hasSpoof) {
                        selfChanged = true;
                        String originalCaseName = hasSpoof ? ModConfig.INSTANCE.spoofedName.trim() : spanMatch.matchedCleanName();
                        String before = spanMatch.before();
                        String plainTrailingColors = getLastColors(before);

                        String cleanBefore = TRAILING_COLOR_CODE_PATTERN.matcher(before).replaceAll("");
                        String after = spanMatch.after();
                        String afterNoLeadColors = LEADING_COLOR_CODE_PATTERN.matcher(after).replaceFirst("");
                            if (afterNoLeadColors.startsWith("'s") || afterNoLeadColors.startsWith("'S")) {
                                originalCaseName += afterNoLeadColors.substring(0, 2);
                                after = afterNoLeadColors.substring(2);
                            }

                            boolean isCustomUser = customData != null;
                            boolean badgeEnabled = isCustomUser && customData.badge();
                            boolean addBadge = allowBadge && badgeEnabled && !isChat && !isTab && !isScoreboard;
                            String badgeCode = (customData != null && customData.badgeIcon() != null) ? customData.badgeIcon() : "\uE001";

                            if (addBadge || isTab || isChat || isScoreboard) {
                                cleanBefore = cleanBefore.replace("\uE001 ", "").replace("\uE001", "")
                                        .replace("\uE002 ", "").replace("\uE002", "");
                            }

                            if (cleanBefore.endsWith("˚˖❀ ")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 4);
                            } else if (cleanBefore.endsWith("˚˖❀")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 3);
                            } else if (cleanBefore.endsWith("Bismillah ")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 10);
                            } else if (cleanBefore.endsWith("Bismillah")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 9);
                            } else if (cleanBefore.endsWith("✪ ")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 2);
                            } else if (cleanBefore.endsWith("✪")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 1);
                            } else if (cleanBefore.endsWith("☠⚔⚔ Death ")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - "☠⚔⚔ Death ".length());
                            } else if (cleanBefore.endsWith("☠⚔⚔ Death")) {
                                cleanBefore = cleanBefore.substring(0, cleanBefore.length() - "☠⚔⚔ Death".length());
                            }

                            String afterLower = after.toLowerCase();
                            if (afterLower.startsWith(" the mistress")) {
                                after = after.substring(" the mistress".length());
                            } else if (afterLower.startsWith(" the mistres")) {
                                after = after.substring(" the mistres".length());
                            } else if (afterLower.startsWith(" the collapser")) {
                                after = after.substring(" the collapser".length());
                            } else if (afterLower.startsWith(" the collapse")) {
                                after = after.substring(" the collapse".length());
                            } else if (afterLower.startsWith(" les tenebres")) {
                                after = after.substring(" les tenebres".length());
                            } else if (afterLower.startsWith("les tenebres")) {
                                after = after.substring("les tenebres".length());
                            } else if (afterLower.startsWith(" surgeon ⚔⚔☠")) {
                                after = after.substring(" surgeon ⚔⚔☠".length());
                            } else if (afterLower.startsWith("surgeon ⚔⚔☠")) {
                                after = after.substring("surgeon ⚔⚔☠".length());
                            } else if (afterLower.startsWith(" fluk")) {
                                after = after.substring(" fluk".length());
                            } else if (afterLower.startsWith("fluk")) {
                                after = after.substring("fluk".length());
                            } else if (afterLower.startsWith(" ✪")) {
                                after = after.substring(" ✪".length());
                            } else if (afterLower.startsWith("✪")) {
                                after = after.substring("✪".length());
                            }

                            String normHex = normalizeHex(colorCode);
                            String finalBefore = cleanBefore;
                            int effectiveFont = isCustomUser ? customData.font() : (isLocal ? ModConfig.INSTANCE.pseudoFont : 0);
                            boolean keepBold = (effectiveFont & 1) != 0;
                            boolean keepItalic = (effectiveFont & 2) != 0;
                            if (!keepBold) finalBefore = BOLD_CODE_PATTERN.matcher(finalBefore).replaceAll("");
                            if (!keepItalic) finalBefore = ITALIC_CODE_PATTERN.matcher(finalBefore).replaceAll("");
                            String finalAfter = after;
                            if (!keepBold) finalAfter = BOLD_CODE_PATTERN.matcher(finalAfter).replaceAll("");
                            if (!keepItalic) finalAfter = ITALIC_CODE_PATTERN.matcher(finalAfter).replaceAll("");

                            if (colorCode == null || colorCode.isEmpty() || "PLAIN".equalsIgnoreCase(colorCode)) {
                                net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                net.minecraft.network.chat.Style st = applyTrailingLegacyColors(net.minecraft.network.chat.Style.EMPTY, plainTrailingColors, keepBold, keepItalic);
                                st = applyCustomFont(st, canonicalLower);
                                builder.append(net.minecraft.network.chat.Component.literal(originalCaseName).withStyle(st));
                                if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                newSelf = builder.withStyle(component.getStyle());
                            } else {

                            double playerAnimSpeed = normalizeAnimSpeed(
                                    (isCustomUser && customData.animSpeed() > 0)
                                            ? customData.animSpeed()
                                            : ModConfig.INSTANCE.pseudoAnimationSpeed);

                            if (isAnimatedColorCode(colorCode)) {
                                String cleanAnimatedName = BOLD_OR_ITALIC_CODE_PATTERN.matcher(originalCaseName).replaceAll("");
                                net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                appendAnimatedNameCharacters(
                                        builder,
                                        cleanAnimatedName,
                                        colorCode,
                                        playerAnimSpeed,
                                        System.currentTimeMillis(),
                                        canonicalLower
                                );
                                if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                newSelf = builder.withStyle(component.getStyle());
                            } else if (normHex != null) {
                                try {
                                    int hexInt = Integer.parseInt(normHex.substring(1), 16);
                                    net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromRgb(hexInt);
                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                    String justName = BOLD_OR_ITALIC_CODE_PATTERN.matcher(originalCaseName).replaceAll("");
                                    net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY.withColor(tc), canonicalLower);
                                    builder.append(net.minecraft.network.chat.Component.literal(justName).withStyle(st));
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                } catch (Exception ignored) {}
                            } else {
                                net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(colorCode.length() > 1 ? colorCode.charAt(1) : 'r');
                                if (fmt != null) {
                                    net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromLegacyFormat(fmt);
                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                    String justName = BOLD_OR_ITALIC_CODE_PATTERN.matcher(originalCaseName).replaceAll("");
                                    net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY.withColor(tc), canonicalLower);
                                    builder.append(net.minecraft.network.chat.Component.literal(justName).withStyle(st));
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                }
                            }
                            }
                        } else if (!text.equals(rawText)) {
                            selfChanged = true;
                            newSelf = net.minecraft.network.chat.Component.literal(text).withStyle(component.getStyle());
                        }
                    }
                }
            }
        if (originalSiblings.isEmpty() && !coalescedChanged) {
            return selfChanged ? newSelf : component;
        }

        java.util.List<net.minecraft.network.chat.Component> newSiblings = new java.util.ArrayList<>(originalSiblings.size());
        boolean siblingsChanged = coalescedChanged;

        for (int i = 0; i < originalSiblings.size() - 1; i++) {
            net.minecraft.network.chat.Component curSib = originalSiblings.get(i);
            net.minecraft.network.chat.Component nextSib = originalSiblings.get(i + 1);
            if (curSib.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents curPlain
                    && nextSib.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents nextPlain) {
                String curStr = curPlain.text();
                String nextStr = nextPlain.text();
                String cleanNext = COLOR_CODE_PATTERN.matcher(nextStr).replaceAll("");
                if (cleanNext.startsWith("'s") || cleanNext.startsWith("'S")) {
                    String cleanCur = COLOR_CODE_PATTERN.matcher(curStr).replaceAll("").trim();
                    boolean matchesTarget = false;
                    for (String tName : targetNames) {
                        if (cleanCur.equalsIgnoreCase(tName) || cleanCur.toLowerCase(java.util.Locale.ROOT).endsWith(tName.toLowerCase(java.util.Locale.ROOT))) {
                            matchesTarget = true;
                            break;
                        }
                    }
                    if (matchesTarget) {
                        int sIdx = nextStr.indexOf("'s");
                        if (sIdx == -1) sIdx = nextStr.indexOf("'S");
                        if (sIdx != -1) {
                            net.minecraft.network.chat.MutableComponent mergedCur = net.minecraft.network.chat.Component.literal(curStr + nextStr.substring(sIdx, sIdx + 2)).withStyle(curSib.getStyle());
                            for (net.minecraft.network.chat.Component ch : curSib.getSiblings()) mergedCur.append(ch);
                            net.minecraft.network.chat.MutableComponent trimmedNext = net.minecraft.network.chat.Component.literal(nextStr.substring(sIdx + 2)).withStyle(nextSib.getStyle());
                            for (net.minecraft.network.chat.Component ch : nextSib.getSiblings()) trimmedNext.append(ch);
                            originalSiblings.set(i, mergedCur);
                            originalSiblings.set(i + 1, trimmedNext);
                            siblingsChanged = true;
                        }
                    }
                }
            }
        }

        for (net.minecraft.network.chat.Component sibling : originalSiblings) {
            if (isTab && containsBadgeComponent(sibling) && FONT_BADGES.equals(sibling.getStyle().getFont())) {
                siblingsChanged = true;
                continue;
            }
            net.minecraft.network.chat.Component modifiedSibling = recursivelyModify(sibling, targetNames, nameToColor, aliasToCanonicalLower, isChat, isTab, isScoreboard, allowBadge);
            newSiblings.add(modifiedSibling);
            if (modifiedSibling != sibling) {
                siblingsChanged = true;
            }
        }

        if (allowBadge && siblingsChanged && newSiblings.size() > 1) {
            reorderBadgeInSiblings(newSiblings);
        }

        if (selfChanged) {
            net.minecraft.network.chat.MutableComponent result = (net.minecraft.network.chat.MutableComponent) newSelf;
            for (net.minecraft.network.chat.Component s : newSiblings) {
                result.append(s);
            }
            return result;
        } else if (siblingsChanged) {
            net.minecraft.network.chat.MutableComponent result = component.copy();
            result.getSiblings().clear();
            for (net.minecraft.network.chat.Component s : newSiblings) {
                result.append(s);
            }
            return result;
        }

        return component;
    }

    static double normalizeAnimSpeed(double rawSpeed) {
        return (rawSpeed > 0.0 && Double.isFinite(rawSpeed)) ? rawSpeed : 1.0;
    }

    private static boolean isAnimatedColorCode(String colorCode) {
        if (colorCode == null || colorCode.isEmpty()) {
            return false;
        }
        String upper = colorCode.toUpperCase(Locale.ROOT);
        return "CHROMA".equals(upper)
                || upper.startsWith("GRADIENT")
                || upper.startsWith("WAVE")
                || upper.startsWith("BREATHE")
                || upper.startsWith("BLINK");
    }

    private static void appendAnimatedNameCharacters(
            net.minecraft.network.chat.MutableComponent builder,
            String name,
            String colorCode,
            double animSpeed,
            long nowMs,
            String nameLower
    ) {
        int cpCount = name.codePointCount(0, name.length());
        int ci = 0;
        for (int offset = 0; offset < name.length(); ) {
            int cp = name.codePointAt(offset);
            int charCount = Character.charCount(cp);
            int rgb = computeAnimationRgb(colorCode, ci, cpCount, animSpeed, nowMs);
            net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY;
            if (rgb >= 0) {
                st = st.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
            }
            st = applyCustomFont(st, nameLower);
            builder.append(net.minecraft.network.chat.Component.literal(name.substring(offset, offset + charCount)).withStyle(st));
            offset += charCount;
            ci++;
        }
    }

    static int computeAnimationRgb(String colorCode, int charIndex, int charCount, double animSpeed, long nowMs) {
        ModConfig cfg = ModConfig.INSTANCE;
        return computeAnimationRgb(
                colorCode,
                charIndex,
                charCount,
                animSpeed,
                nowMs,
                cfg != null ? cfg.customHexColor : "#FFFFFF",
                cfg != null ? cfg.customHexColor2 : "#FFFFFF"
        );
    }

    public static int computeAnimationRgb(
            String colorCode,
            int charIndex,
            int charCount,
            double animSpeed,
            long nowMs,
            String defaultHex1,
            String defaultHex2
    ) {
        if (colorCode == null || colorCode.isEmpty()) {
            return -1;
        }
        String upper = colorCode.toUpperCase(Locale.ROOT);
        long scaledTime = (long) (nowMs * normalizeAnimSpeed(animSpeed));
        float ratio = charCount <= 1
                ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, (float) charIndex / (charCount - 1)));

        if ("CHROMA".equals(upper)) {
            float hue = (((scaledTime % 3000L) / 3000.0f) + ratio) % 1.0f;
            if (hue < 0.0f) {
                hue += 1.0f;
            }
            return ColorUtils.hsbToRgb(hue, 1.0f, 1.0f) & 0x00FFFFFF;
        }

        boolean isGrad = upper.startsWith("GRADIENT");
        boolean isWave = upper.startsWith("WAVE");
        boolean isBreathe = upper.startsWith("BREATHE");
        boolean isBlink = upper.startsWith("BLINK");
        if (!isGrad && !isWave && !isBreathe && !isBlink) {
            return -1;
        }

        String normHex1 = normalizeHex(defaultHex1);
        String normHex2 = normalizeHex(defaultHex2);
        if (colorCode.contains(":")) {
            String[] parts = colorCode.split(":");
            if (parts.length >= 3) {
                String p1 = normalizeHex(parts[1]);
                String p2 = normalizeHex(parts[2]);
                if (p1 != null) normHex1 = p1;
                if (p2 != null) normHex2 = p2;
            }
        }
        int c1 = ColorUtils.parseHexRgb(normHex1, 0xFFFFFF);
        int c2 = ColorUtils.parseHexRgb(normHex2, 0xFFFFFF);

        if (isBlink) {
            return ((scaledTime / 500L) % 2L == 0L) ? c1 : c2;
        }
        float blend = ratio;
        if (isWave) {
            float timeOffset = (scaledTime % 2000L) / 2000.0f;
            blend = (float) (Math.sin((ratio - timeOffset) * Math.PI * 2.0) * 0.5 + 0.5);
        } else if (isBreathe) {
            float timeRatio = (scaledTime % 3000L) / 3000.0f;
            blend = (float) (Math.sin(timeRatio * Math.PI * 2.0) * 0.5 + 0.5);
        }

        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int ri = Math.clamp(Math.round(r1 + (r2 - r1) * blend), 0, 255);
        int gi = Math.clamp(Math.round(g1 + (g2 - g1) * blend), 0, 255);
        int bi = Math.clamp(Math.round(b1 + (b2 - b1) * blend), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    public static net.minecraft.network.chat.TextColor computeAnimatedColor(String colorCode, int ci, int len, String nameLower) {
        return computeAnimatedColor(colorCode, ci, len, nameLower, System.currentTimeMillis());
    }

    private static net.minecraft.network.chat.TextColor computeAnimatedColor(String colorCode, int ci, int len, String nameLower, long nowMs) {
        if (colorCode == null || colorCode.isEmpty()) return null;
        CustomPlayerManager.CustomPlayerData customData = resolveCustomPlayer(getMinecraftOrNull(), nameLower);
        double animSpeed = normalizeAnimSpeed(
                (customData != null && customData.animSpeed() > 0)
                        ? customData.animSpeed()
                        : ModConfig.INSTANCE.pseudoAnimationSpeed);
        int charCount = Math.max(1, len + 1);
        int rgb = computeAnimationRgb(colorCode, ci, charCount, animSpeed, nowMs);
        return rgb >= 0 ? net.minecraft.network.chat.TextColor.fromRgb(rgb) : null;
    }

    private static class AnimMatch {
        final int start;
        final int end;
        final int totalLen;
        final String colorCode;
        final String nameLower;

        AnimMatch(int start, int end, int totalLen, String colorCode, String nameLower) {
            this.start = start;
            this.end = end;
            this.totalLen = totalLen;
            this.colorCode = colorCode;
            this.nameLower = nameLower;
        }
    }

    private static AnimatedTargetSnapshot getOrBuildAnimatedTargets(Minecraft mc, int cosMode) {
        CustomPlayerManager.syncLiveBindings(mc, false);
        long version = TARGET_CACHE_VERSION.get();
        long wrVersion = WordReplacerManager.getVersion();
        ModConfig cfg = ModConfig.INSTANCE;
        UUID localUuid = resolveLocalUuid(mc);
        String localName = resolveLocalName(mc);

        AnimatedTargetSnapshot existing = cachedAnimatedTargets;
        if (existing != null && existing.key().matches(version, wrVersion, cosMode, cfg, localUuid, localName)) {
            return existing;
        }

        List<AnimTarget> targets = new ArrayList<>();
        WordReplacerManager.Snapshot wrSnap = WordReplacerManager.getActiveSnapshot();

        if (cosMode == 0) {
            for (CustomPlayerManager.ResolvedCustomPlayer rp : CustomPlayerManager.getResolvedPlayers()) {
                CustomPlayerManager.CustomPlayerData cp = rp.data();
                String col = (cp.color() != null && !cp.color().isEmpty())
                        ? cp.color()
                        : ((cp.font() != 0 || cp.badge()) ? "PLAIN" : "");
                if (!col.isEmpty()) {
                    String baseLower = rp.liveName().toLowerCase(Locale.ROOT);
                    targets.add(new AnimTarget(baseLower, col, baseLower));
                    addUnstyledWordReplacerAnimAliases(wrSnap, rp.liveName(), baseLower, col, targets);
                }
            }
        }

        if (localName != null && !localName.isEmpty()) {
            String localLower = localName.toLowerCase(Locale.ROOT);
            CustomPlayerManager.CustomPlayerData localCustom = localUuid != null ? CustomPlayerManager.getPlayer(localUuid) : null;
            String trimmedSpoof = (cfg.spoofedName != null && !cfg.spoofedName.trim().isEmpty())
                    ? cfg.spoofedName.trim()
                    : null;

            String localColor = getColorForPlayer(localUuid, localName);
            if (localColor == null || localColor.isEmpty()) {
                if (cfg.enableNameColor && cfg.pseudoAnimation > 0) {
                    localColor = getCurrentColorCode();
                } else if (localCustom != null && (localCustom.font() != 0 || localCustom.badge())) {
                    localColor = "PLAIN";
                } else if (trimmedSpoof != null || cfg.pseudoFont != 0) {
                    localColor = "PLAIN";
                }
            }
            if (localColor != null && !localColor.isEmpty()) {
                addAnimTargetIfAbsent(targets, localLower, localColor, localLower);
                if (trimmedSpoof != null
                        && isSafeTargetAlias(trimmedSpoof, localName)
                        && !hasExplicitWordReplacerRuleFor(wrSnap, localName, trimmedSpoof)) {
                    addAnimTargetIfAbsent(targets, trimmedSpoof.toLowerCase(Locale.ROOT), localColor, localLower);
                }
                addUnstyledWordReplacerAnimAliases(wrSnap, localName, localLower, localColor, targets);
            }
        }

        targets.sort((a, b) -> Integer.compare(b.name().length(), a.name().length()));
        AnimatedTargetSnapshot rebuilt = new AnimatedTargetSnapshot(
                TargetCacheKey.capture(version, wrVersion, cosMode, mc),
                List.copyOf(targets)
        );
        cachedAnimatedTargets = rebuilt;
        return rebuilt;
    }

    private static void addAnimTargetIfAbsent(List<AnimTarget> targets, String nameMatchLower, String colorCode, String canonicalLower) {
        for (AnimTarget item : targets) {
            if (item.name().equalsIgnoreCase(nameMatchLower)) {
                return;
            }
        }
        targets.add(new AnimTarget(nameMatchLower, colorCode, canonicalLower));
    }

    private static void addUnstyledWordReplacerAnimAliases(
            WordReplacerManager.Snapshot wrSnap,
            String canonicalName,
            String canonicalLower,
            String colorCode,
            List<AnimTarget> targets
    ) {
        if (wrSnap == null || !wrSnap.enabled() || canonicalName == null || canonicalName.isEmpty()) {
            return;
        }
        for (WordReplacerManager.CompiledRule rule : wrSnap.rules()) {
            if (!rule.hasExplicitStyle()
                    && wordReplacerRuleMatchesName(rule, canonicalName, wrSnap.caseSensitive())
                    && isSafeTargetAlias(rule.to(), canonicalName)) {
                addAnimTargetIfAbsent(targets, rule.to().toLowerCase(Locale.ROOT), colorCode, canonicalLower);
            }
        }
    }

    public static net.minecraft.util.FormattedCharSequence wrapAnimatedLine(net.minecraft.util.FormattedCharSequence original) {
        if (original == null) return null;
        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
        if (cosMode == 2) return original;

        var mc = getMinecraftOrNull();
        List<AnimTarget> targets = getOrBuildAnimatedTargets(mc, cosMode).targets();
        if (targets.isEmpty()) return original;

        StringBuilder sb = ANIMATED_LINE_SB.get();
        sb.setLength(0);
        original.accept((idx, st, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        if (sb.length() == 0) return original;
        String lineText = sb.toString();
        String lineLower = lineText.toLowerCase(Locale.ROOT);

        List<AnimMatch> matches = null;

        for (AnimTarget t : targets) {
            int searchPos = 0;
            while (searchPos < lineLower.length()) {
                int candidate = lineLower.indexOf(t.name(), searchPos);
                int idx = -1;
                while (candidate != -1) {
                    char charBefore = candidate > 0 ? lineText.charAt(candidate - 1) : ' ';
                    boolean isColorCodeBefore = candidate >= 2 && lineText.charAt(candidate - 2) == '\u00A7';
                    boolean beforeOk = (candidate == 0) || isColorCodeBefore || !isUsernameChar(charBefore);
                    int afterPos = candidate + t.name().length();
                    boolean afterOk = (afterPos >= lineText.length()) || !isUsernameChar(lineText.charAt(afterPos));
                    if (beforeOk && afterOk) {
                        idx = candidate;
                        break;
                    }
                    candidate = lineLower.indexOf(t.name(), candidate + t.name().length());
                }
                if (idx != -1) {
                    int matchEnd = idx + t.name().length();
                    boolean overlaps = false;
                    if (matches != null) {
                        for (AnimMatch existing : matches) {
                            if (idx < existing.end && matchEnd > existing.start) {
                                overlaps = true;
                                break;
                            }
                        }
                    }
                    if (!overlaps) {
                        if (matches == null) matches = new ArrayList<>(2);
                        matches.add(new AnimMatch(idx, matchEnd, Math.max(1, t.name().length() - 1), t.colorCode(), t.nameLower()));
                    }
                    searchPos = matchEnd;
                } else {
                    break;
                }
            }
        }

        if (matches == null || matches.isEmpty()) return original;

        final List<AnimMatch> matchedRegions = matches;
        return sink -> {
            final long nowMs = System.currentTimeMillis();
            int[] pos = new int[]{0};
            return original.accept((idx, st, cp) -> {
                int p = pos[0];
                pos[0] += Character.charCount(cp);
                for (AnimMatch m : matchedRegions) {
                    if (p >= m.start && p < m.end) {
                        int ci = p - m.start;
                        net.minecraft.network.chat.Style newSt = applyCustomFont(st, m.nameLower);
                        net.minecraft.network.chat.TextColor animColor = computeAnimatedColor(m.colorCode, ci, m.totalLen, m.nameLower, nowMs);
                        if (animColor != null) {
                            newSt = newSt.withColor(animColor);
                        }
                        return sink.accept(idx, newSt, cp);
                    }
                }
                return sink.accept(idx, st, cp);
            });
        };
    }
}
