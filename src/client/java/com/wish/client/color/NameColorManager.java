package com.wish.client.color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
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

    public static String getColorForPlayer(UUID uuid, String name) {
        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
        if (cosMode == 2) return null;

        var mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && (mc.player.getUUID().equals(uuid) || mc.player.getScoreboardName().equalsIgnoreCase(name));
        if (cosMode == 1 && !isLocal) return null;

        CustomPlayerManager.CustomPlayerData customData = CustomPlayerManager.getPlayer(uuid, name);
        if (customData == null && isLocal && mc.player != null) {
            customData = CustomPlayerManager.getPlayer(mc.player.getUUID(), mc.player.getScoreboardName());
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

    public static boolean isTabOverlayActive() {
        if (isRenderingTab) return true;
        var mc = Minecraft.getInstance();
        return mc.options != null && mc.options.keyPlayerList.isDown();
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component) {
        return colorizeText(component, false);
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component, boolean isChat) {
        return colorizeText(component, isChat, isTabOverlayActive());
    }

    private static boolean containsTabMarker(net.minecraft.network.chat.Component component) {
        if (component == null) return false;
        if ("wish:tab".equals(component.getStyle().getInsertion())) return true;
        for (net.minecraft.network.chat.Component child : component.getSiblings()) {
            if (containsTabMarker(child)) return true;
        }
        return false;
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component, boolean isChat, boolean isTab) {
        return colorizeText(component, isChat, isTab, isRenderingScoreboard);
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component, boolean isChat, boolean isTab, boolean isScoreboard) {
        if (component == null) return null;
        if (!isTab && containsTabMarker(component)) {
            return component;
        }
        try {
            net.minecraft.network.chat.Component result = modifyComponent(component, isChat, isTab, isScoreboard);
            if (isTab && result != null) {
                return result.copy().withStyle(result.getStyle().withInsertion("wish:tab"));
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("[Wish] Error in colorizeText", e);
            return component;
        }
    }

    private static final Pattern MM_HEX_EXTENDED = Pattern.compile("<[^>]*#[0-9A-Fa-f\\u00A7rRk-oK-O]{6,12}[^>]*>");
    private static final Pattern MM_HEX_SIMPLE   = Pattern.compile("<#/?[0-9A-Fa-f]{6}>");
    private static final Pattern MM_COMPLEX_TAGS = Pattern.compile("<(?:color|c|font|gradient|rainbow|hover|click):[^>]+>");
    private static final Pattern MM_CLOSE_TAGS   = Pattern.compile("</(?:color|c|font|gradient|rainbow|hover|click|bold|italic|underlined|strikethrough|obfuscated|reset)>");
    private static final Pattern MM_FORMAT_TAGS  = Pattern.compile("<(?:bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf|reset|r)>");
    private static final Pattern MM_ANY_CLOSE    = Pattern.compile("</[^>]*>");

    public static String stripMiniMessageTags(String text) {
        if (text == null || text.isEmpty() || text.indexOf('<') == -1) return text;
        String s = MM_HEX_EXTENDED.matcher(text).replaceAll("");
        s = MM_HEX_SIMPLE.matcher(s).replaceAll("");
        s = MM_COMPLEX_TAGS.matcher(s).replaceAll("");
        s = MM_CLOSE_TAGS.matcher(s).replaceAll("");
        s = MM_FORMAT_TAGS.matcher(s).replaceAll("");
        return MM_ANY_CLOSE.matcher(s).replaceAll("");
    }

    private static boolean isUsernameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    public static final net.minecraft.network.chat.FontDescription FONT_BADGES = new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("wish", "badges"));

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00A7[0-9a-fA-Fk-rK-R]");
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

    private static net.minecraft.network.chat.Style applyCustomFont(net.minecraft.network.chat.Style st, String nameLower) {
        var mc = Minecraft.getInstance();
        CustomPlayerManager.CustomPlayerData customData = CustomPlayerManager.getPlayerByName(nameLower);
        if (customData == null && mc.player != null && mc.player.getScoreboardName().equalsIgnoreCase(nameLower)) {
            customData = CustomPlayerManager.getPlayer(mc.player.getUUID(), mc.player.getScoreboardName());
        }
        if (customData != null) {
            int f = customData.font();
            st = st.withBold((f & 1) != 0);
            st = st.withItalic((f & 2) != 0);
            st = st.withUnderlined((f & 4) != 0);
            st = st.withStrikethrough((f & 8) != 0);
            st = st.withObfuscated((f & 16) != 0);
        } else if (mc.player != null && nameLower.equalsIgnoreCase(mc.player.getScoreboardName())) {
            int f = ModConfig.INSTANCE.pseudoFont;
            st = st.withBold((f & 1) != 0);
            st = st.withItalic((f & 2) != 0);
            st = st.withUnderlined((f & 4) != 0);
            st = st.withStrikethrough((f & 8) != 0);
            st = st.withObfuscated((f & 16) != 0);
        }
        return st;
    }

    private static net.minecraft.network.chat.Component modifyComponent(net.minecraft.network.chat.Component component, boolean isChat, boolean isTab, boolean isScoreboard) {
        if (component == null) return null;

        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
        if (cosMode == 2) {
            return component;
        }

        var mc = Minecraft.getInstance();
        java.util.List<String> targetNames = new java.util.ArrayList<>();
        Map<String, String> nameToColor = new java.util.HashMap<>();

        if (cosMode == 0) {
            for (CustomPlayerManager.CustomPlayerData cp : CustomPlayerManager.getPlayers()) {
                String col = (cp.color() != null && !cp.color().isEmpty())
                        ? cp.color()
                        : ((cp.font() != 0 || cp.badge()) ? "PLAIN" : "");
                if (!targetNames.contains(cp.name())) targetNames.add(cp.name());
                nameToColor.put(cp.name().toLowerCase(), col);
            }
        }

        if (mc.player != null) {
            String localName = mc.player.getScoreboardName();
            if (localName != null && !localName.isEmpty()) {
                String localNameLower = localName.toLowerCase();
                CustomPlayerManager.CustomPlayerData localCustom = CustomPlayerManager.getPlayer(mc.player.getUUID(), localName);
                String localColor = "";

                if (localCustom != null) {
                    localColor = (localCustom.color() != null && !localCustom.color().isEmpty())
                            ? localCustom.color()
                            : ((localCustom.font() != 0 || localCustom.badge()) ? "PLAIN" : "");
                } else {
                    if (ModConfig.INSTANCE.enableNameColor) {
                        localColor = getCurrentColorCode();
                    } else if ((ModConfig.INSTANCE.spoofedName != null && !ModConfig.INSTANCE.spoofedName.trim().isEmpty()) || ModConfig.INSTANCE.pseudoFont != 0) {
                        localColor = "PLAIN";
                    } else {
                        localColor = "";
                    }
                }

                if (!targetNames.contains(localName)) targetNames.add(localName);
                nameToColor.put(localNameLower, localColor);
            }
        }

        if (targetNames.isEmpty()) {
            return component;
        }

        targetNames.sort((a, b) -> Integer.compare(b.length(), a.length()));

        return recursivelyModify(component, targetNames, nameToColor, isChat, isTab, isScoreboard);
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
            boolean isChat,
            boolean isTab,
            boolean isScoreboard) {
        if (component == null) return null;
        if ("wish:tab".equals(component.getStyle().getInsertion())) return component;

        boolean selfChanged = false;
        net.minecraft.network.chat.Component newSelf = null;

        java.util.List<net.minecraft.network.chat.Component> originalSiblings = new java.util.ArrayList<>(component.getSiblings());
        mergePossessiveAcrossSiblings(originalSiblings, targetNames);

        var contents = component.getContents();
        if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents trans) {
            Object[] args = trans.getArgs();
            Object[] newArgs = new Object[args.length];
            boolean argsChanged = false;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof net.minecraft.network.chat.Component argComp) {
                    net.minecraft.network.chat.Component modifiedArg = recursivelyModify(argComp, targetNames, nameToColor, isChat, isTab, isScoreboard);
                    newArgs[i] = modifiedArg;
                    if (modifiedArg != argComp) argsChanged = true;
                } else if (arg instanceof String argStr) {
                    net.minecraft.network.chat.Component argComp = net.minecraft.network.chat.Component.literal(argStr);
                    net.minecraft.network.chat.Component modifiedArg = recursivelyModify(argComp, targetNames, nameToColor, isChat, isTab, isScoreboard);
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
            String rawText = plain.text();
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

                String textLower = text.toLowerCase();
                for (String name : targetNames) {
                    if (name == null || name.isEmpty()) continue;
                    String nameLower = name.toLowerCase();

                    
                    int idx = -1;
                    int searchPos = 0;
                    while ((searchPos = textLower.indexOf(nameLower, searchPos)) != -1) {
                        String subAfter = textLower.substring(searchPos + nameLower.length());
                        if (subAfter.startsWith(" head]") || subAfter.startsWith(" head")) {
                            searchPos += nameLower.length();
                            continue;
                        }

                        char charBefore = searchPos > 0 ? text.charAt(searchPos - 1) : ' ';
                        boolean isColorCodeBefore = searchPos >= 2 && text.charAt(searchPos - 2) == '\u00A7';
                        boolean beforeOk = (searchPos == 0) || isColorCodeBefore || !isUsernameChar(charBefore);
                        boolean afterOk = (searchPos + nameLower.length() == text.length()) || !isUsernameChar(text.charAt(searchPos + nameLower.length()));

                        if (beforeOk && afterOk) {
                            idx = searchPos;
                            break;
                        }
                        searchPos += nameLower.length();
                    }

                    if (idx != -1) {
                        String colorCode = nameToColor.get(nameLower);
                        var mc = Minecraft.getInstance();
                        boolean isLocal = mc.player != null && mc.player.getScoreboardName().equalsIgnoreCase(nameLower);
                        CustomPlayerManager.CustomPlayerData customData = CustomPlayerManager.getPlayerByName(nameLower);
                        if (customData == null && isLocal && mc.player != null) {
                            customData = CustomPlayerManager.getPlayer(mc.player.getUUID(), mc.player.getScoreboardName());
                        }
                        boolean hasSpoof = isLocal && ModConfig.INSTANCE.spoofedName != null && !ModConfig.INSTANCE.spoofedName.trim().isEmpty();
                        if ((colorCode != null && !colorCode.isEmpty()) || hasSpoof) {
                            selfChanged = true;
                            String originalCaseName = hasSpoof ? ModConfig.INSTANCE.spoofedName.trim() : text.substring(idx, idx + name.length());
                            String before = text.substring(0, idx);
                            
                            String cleanBefore = TRAILING_COLOR_CODE_PATTERN.matcher(before).replaceAll("");
                            String after = text.substring(idx + name.length());
                            String afterNoLeadColors = after.replaceFirst("^(?:\u00A7[0-9a-fA-Fk-rK-R])+", "");
                            if (afterNoLeadColors.startsWith("'s") || afterNoLeadColors.startsWith("'S")) {
                                originalCaseName += afterNoLeadColors.substring(0, 2);
                                after = afterNoLeadColors.substring(2);
                            }

                            boolean isCustomUser = customData != null;
                            boolean badgeEnabled = isCustomUser && customData.badge();
                            boolean addBadge = badgeEnabled && !isChat && !isTab && !isScoreboard;
                            String badgeCode = (customData != null && customData.badgeIcon() != null) ? customData.badgeIcon() : "\uE001";

                            cleanBefore = cleanBefore.replace("\uE001 ", "").replace("\uE001", "")
                                    .replace("\uE002 ", "").replace("\uE002", "");

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
                            boolean keepBold = isCustomUser && (customData.font() & 1) != 0;
                            boolean keepItalic = isCustomUser && (customData.font() & 2) != 0;
                            if (!keepBold) finalBefore = BOLD_CODE_PATTERN.matcher(finalBefore).replaceAll("");
                            if (!keepItalic) finalBefore = ITALIC_CODE_PATTERN.matcher(finalBefore).replaceAll("");
                            String finalAfter = after;
                            if (!keepBold) finalAfter = BOLD_CODE_PATTERN.matcher(finalAfter).replaceAll("");
                            if (!keepItalic) finalAfter = ITALIC_CODE_PATTERN.matcher(finalAfter).replaceAll("");

                            if (colorCode == null || colorCode.isEmpty() || "PLAIN".equalsIgnoreCase(colorCode)) {
                                net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY, nameLower);
                                StringBuilder fCodes = new StringBuilder();
                                if (st.isBold()) fCodes.append("\u00A7l");
                                if (st.isItalic()) fCodes.append("\u00A7o");
                                if (st.isUnderlined()) fCodes.append("\u00A7n");
                                if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                if (st.isObfuscated()) fCodes.append("\u00A7k");
                                builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + originalCaseName).withStyle(st));
                                if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                newSelf = builder.withStyle(component.getStyle());
                                break;
                            }

                            double playerAnimSpeed = (isCustomUser && customData.animSpeed() > 0) ? customData.animSpeed() : ModConfig.INSTANCE.pseudoAnimationSpeed;

                            if ("CHROMA".equalsIgnoreCase(colorCode)) {
                                net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                long scaledTime = (long)(System.currentTimeMillis() * playerAnimSpeed);
                                long step = scaledTime / 150;
                                int ci = 0;
                                for (int offset = 0; offset < originalCaseName.length(); ) {
                                    int cp = originalCaseName.codePointAt(offset);
                                    int charCount = Character.charCount(cp);
                                    int colIdx = Math.abs((int) ((step + ci) % CHROMA_CODES.length));
                                    net.minecraft.network.chat.TextColor cc = net.minecraft.network.chat.TextColor.fromLegacyFormat(
                                        net.minecraft.ChatFormatting.getByCode(CHROMA_CODES[colIdx].charAt(1)));
                                    net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY;
                                    if (cc != null) st = st.withColor(cc);
                                    st = applyCustomFont(st, nameLower);
                                    String charStr = originalCaseName.substring(offset, offset + charCount);
                                    StringBuilder fCodes = new StringBuilder();
                                    if (st.isBold()) fCodes.append("\u00A7l");
                                    if (st.isItalic()) fCodes.append("\u00A7o");
                                    if (st.isUnderlined()) fCodes.append("\u00A7n");
                                    if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                    if (st.isObfuscated()) fCodes.append("\u00A7k");
                                    builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + charStr).withStyle(st));
                                    offset += charCount;
                                    ci++;
                                }
                                if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                newSelf = builder.withStyle(component.getStyle());
                            } else if (colorCode.startsWith("GRADIENT") || colorCode.startsWith("WAVE") || colorCode.startsWith("BREATHE") || colorCode.startsWith("BLINK")) {
                                String normHex1 = normalizeHex(ModConfig.INSTANCE.customHexColor);
                                String normHex2 = normalizeHex(ModConfig.INSTANCE.customHexColor2);
                                
                                if (colorCode.contains(":")) {
                                    String[] parts = colorCode.split(":");
                                    if (parts.length >= 3) {
                                        normHex1 = normalizeHex(parts[1]);
                                        normHex2 = normalizeHex(parts[2]);
                                    }
                                }

                                if (normHex1 == null) normHex1 = "#FFFFFF";
                                if (normHex2 == null) normHex2 = "#FFFFFF";
                                try {
                                    int c1 = Integer.parseInt(normHex1.substring(1), 16);
                                    int c2 = Integer.parseInt(normHex2.substring(1), 16);
                                    int r1 = (c1 >> 16) & 0xFF;
                                    int g1 = (c1 >> 8) & 0xFF;
                                    int b1 = c1 & 0xFF;
                                    int r2 = (c2 >> 16) & 0xFF;
                                    int g2 = (c2 >> 8) & 0xFF;
                                    int b2 = c2 & 0xFF;

                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                    int cpCount = originalCaseName.codePointCount(0, originalCaseName.length());
                                    int len = Math.max(1, cpCount - 1);
                                    int ci = 0;
                                    for (int offset = 0; offset < originalCaseName.length(); ) {
                                        int cp = originalCaseName.codePointAt(offset);
                                        int charCount = Character.charCount(cp);
                                        float ratio = (float) ci / len;
                                        
                                        long scaledTime = (long)(System.currentTimeMillis() * playerAnimSpeed);
                                        if (colorCode.startsWith("WAVE")) {
                                            float timeOffset = (scaledTime % 2000L) / 2000.0f;
                                            ratio = (float) (Math.sin((ratio - timeOffset) * Math.PI * 2) * 0.5f + 0.5f);
                                        } else if (colorCode.startsWith("BREATHE")) {
                                            float timeRatio = (scaledTime % 3000L) / 3000.0f;
                                            ratio = (float) (Math.sin(timeRatio * Math.PI * 2) * 0.5f + 0.5f);
                                        }
                                        
                                        int ri = (int) (r1 + (r2 - r1) * ratio);
                                        int gi = (int) (g1 + (g2 - g1) * ratio);
                                        int bi = (int) (b1 + (b2 - b1) * ratio);
                                        
                                        if (colorCode.startsWith("BLINK")) {
                                            if ((scaledTime / 500) % 2 == 0) {
                                                ri = r1; gi = g1; bi = b1;
                                            } else {
                                                ri = r2; gi = g2; bi = b2;
                                            }
                                        }
                                        
                                        int rgb = (ri << 16) | (gi << 8) | bi;
                                        net.minecraft.network.chat.TextColor cc = net.minecraft.network.chat.TextColor.fromRgb(rgb);
                                        net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY.withColor(cc), nameLower);
                                        String charStr = originalCaseName.substring(offset, offset + charCount);
                                        StringBuilder fCodes = new StringBuilder();
                                        if (st.isBold()) fCodes.append("\u00A7l");
                                        if (st.isItalic()) fCodes.append("\u00A7o");
                                        if (st.isUnderlined()) fCodes.append("\u00A7n");
                                        if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                        if (st.isObfuscated()) fCodes.append("\u00A7k");
                                        builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + charStr).withStyle(st));
                                        offset += charCount;
                                        ci++;
                                    }
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                } catch (Exception ignored) {}
                            } else if (normHex != null) {
                                try {
                                    int hexInt = Integer.parseInt(normHex.substring(1), 16);
                                    net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromRgb(hexInt);
                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    appendPrefixAndBadge(builder, finalBefore, addBadge, badgeCode);
                                    String justName = BOLD_OR_ITALIC_CODE_PATTERN.matcher(originalCaseName).replaceAll("");
                                    net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY.withColor(tc), nameLower);
                                    StringBuilder fCodes = new StringBuilder();
                                    if (st.isBold()) fCodes.append("\u00A7l");
                                    if (st.isItalic()) fCodes.append("\u00A7o");
                                    if (st.isUnderlined()) fCodes.append("\u00A7n");
                                    if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                    if (st.isObfuscated()) fCodes.append("\u00A7k");
                                    builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + justName).withStyle(st));
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
                                    net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY.withColor(tc), nameLower);
                                    StringBuilder fCodes = new StringBuilder();
                                    if (st.isBold()) fCodes.append("\u00A7l");
                                    if (st.isItalic()) fCodes.append("\u00A7o");
                                    if (st.isUnderlined()) fCodes.append("\u00A7n");
                                    if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                    if (st.isObfuscated()) fCodes.append("\u00A7k");
                                    builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + justName).withStyle(st));
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                }
                            }
                            break;
                        } else if (!text.equals(rawText)) {
                            selfChanged = true;
                            newSelf = net.minecraft.network.chat.Component.literal(text).withStyle(component.getStyle());
                            break;
                        }
                    }
                }
            }
        }
        java.util.List<net.minecraft.network.chat.Component> newSiblings = new java.util.ArrayList<>(originalSiblings.size());
        boolean siblingsChanged = false;

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
            net.minecraft.network.chat.Component modifiedSibling = recursivelyModify(sibling, targetNames, nameToColor, isChat, isTab, isScoreboard);
            newSiblings.add(modifiedSibling);
            if (modifiedSibling != sibling) {
                siblingsChanged = true;
            }
        }

        if (siblingsChanged && newSiblings.size() > 1) {
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

    public static net.minecraft.network.chat.TextColor computeAnimatedColor(String colorCode, int ci, int len, String nameLower) {
        if (colorCode == null || colorCode.isEmpty()) return null;
        CustomPlayerManager.CustomPlayerData customData = CustomPlayerManager.getPlayerByName(nameLower);
        double animSpeed = (customData != null && customData.animSpeed() > 0) ? customData.animSpeed() : ModConfig.INSTANCE.pseudoAnimationSpeed;
        if (animSpeed == 0.0) animSpeed = 1.0;
        long scaledTime = (long)(System.currentTimeMillis() * animSpeed);

        if ("CHROMA".equalsIgnoreCase(colorCode)) {
            long step = scaledTime / 150;
            int colIdx = Math.abs((int) ((step + ci) % CHROMA_CODES.length));
            return net.minecraft.network.chat.TextColor.fromLegacyFormat(
                net.minecraft.ChatFormatting.getByCode(CHROMA_CODES[colIdx].charAt(1)));
        }

        if (colorCode.startsWith("GRADIENT") || colorCode.startsWith("WAVE") || colorCode.startsWith("BREATHE") || colorCode.startsWith("BLINK")) {
            String normHex1 = normalizeHex(ModConfig.INSTANCE.customHexColor);
            String normHex2 = normalizeHex(ModConfig.INSTANCE.customHexColor2);
            if (colorCode.contains(":")) {
                String[] parts = colorCode.split(":");
                if (parts.length >= 3) {
                    normHex1 = normalizeHex(parts[1]);
                    normHex2 = normalizeHex(parts[2]);
                }
            }
            if (normHex1 == null) normHex1 = "#FFFFFF";
            if (normHex2 == null) normHex2 = "#FFFFFF";
            try {
                int c1 = Integer.parseInt(normHex1.substring(1), 16);
                int c2 = Integer.parseInt(normHex2.substring(1), 16);
                int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
                int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

                int safeLen = Math.max(1, len);
                float ratio = (float) ci / safeLen;

                if (colorCode.startsWith("WAVE")) {
                    float timeOffset = (scaledTime % 2000L) / 2000.0f;
                    ratio = (float) (Math.sin((ratio - timeOffset) * Math.PI * 2) * 0.5f + 0.5f);
                } else if (colorCode.startsWith("BREATHE")) {
                    float timeRatio = (scaledTime % 3000L) / 3000.0f;
                    ratio = (float) (Math.sin(timeRatio * Math.PI * 2) * 0.5f + 0.5f);
                }

                int ri = (int) (r1 + (r2 - r1) * ratio);
                int gi = (int) (g1 + (g2 - g1) * ratio);
                int bi = (int) (b1 + (b2 - b1) * ratio);

                if (colorCode.startsWith("BLINK")) {
                    if ((scaledTime / 500) % 2 == 0) {
                        ri = r1; gi = g1; bi = b1;
                    } else {
                        ri = r2; gi = g2; bi = b2;
                    }
                }

                int rgb = (ri << 16) | (gi << 8) | bi;
                return net.minecraft.network.chat.TextColor.fromRgb(rgb);
            } catch (Exception ignored) {}
        }

        return null;
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

    public static net.minecraft.util.FormattedCharSequence wrapAnimatedLine(net.minecraft.util.FormattedCharSequence original) {
        if (original == null) return null;
        if (ModConfig.INSTANCE.cosmeticVisibility == 2) return original;

        StringBuilder sb = new StringBuilder();
        original.accept((idx, st, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        String lineText = sb.toString();
        if (lineText.isEmpty()) return original;
        String lineLower = lineText.toLowerCase();

        var mc = Minecraft.getInstance();
        java.util.List<AnimMatch> matches = new java.util.ArrayList<>();

        record Target(String name, String colorCode, String nameLower) {}
        java.util.List<Target> targets = new java.util.ArrayList<>();

        if (ModConfig.INSTANCE.cosmeticVisibility == 0) {
            for (CustomPlayerManager.CustomPlayerData cp : CustomPlayerManager.getPlayers()) {
                String col = (cp.color() != null && !cp.color().isEmpty())
                        ? cp.color()
                        : ((cp.font() != 0 || cp.badge()) ? "PLAIN" : "");
                if (!col.isEmpty()) {
                    String baseLower = cp.name().toLowerCase();
                    targets.add(new Target(baseLower, col, baseLower));
                }
            }
        }

        if (mc.player != null) {
            String localName = mc.player.getScoreboardName();
            if (localName != null && !localName.isEmpty()) {
                String localLower = localName.toLowerCase();
                CustomPlayerManager.CustomPlayerData localCustom = CustomPlayerManager.getPlayer(mc.player.getUUID(), localName);
                String targetName;
                if (ModConfig.INSTANCE.spoofedName != null && !ModConfig.INSTANCE.spoofedName.trim().isEmpty()) {
                    targetName = ModConfig.INSTANCE.spoofedName.trim();
                } else {
                    targetName = localName;
                }
                String targetLower = targetName.toLowerCase();
                
                String localColor = getColorForPlayer(mc.player.getUUID(), localName);
                if (localColor == null || localColor.isEmpty()) {
                    if (localCustom != null && (localCustom.font() != 0 || localCustom.badge())) {
                        localColor = "PLAIN";
                    } else if (ModConfig.INSTANCE.enableNameColor && ModConfig.INSTANCE.pseudoAnimation > 0) {
                        localColor = getCurrentColorCode();
                    } else if ((ModConfig.INSTANCE.spoofedName != null && !ModConfig.INSTANCE.spoofedName.trim().isEmpty()) || ModConfig.INSTANCE.pseudoFont != 0) {
                        localColor = "PLAIN";
                    }
                }
                if (localColor != null && !localColor.isEmpty()) {
                    boolean alreadyExists = false;
                    for (Target existing : targets) {
                        if (existing.name().equalsIgnoreCase(targetLower)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        targets.add(new Target(targetLower, localColor, localLower));
                    }
                }
            }
        }

        targets.sort((a, b) -> Integer.compare(b.name().length(), a.name().length()));

        for (Target t : targets) {
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
                    for (AnimMatch existing : matches) {
                        if (idx < existing.end && matchEnd > existing.start) {
                            overlaps = true;
                            break;
                        }
                    }
                    if (!overlaps) {
                        matches.add(new AnimMatch(idx, matchEnd, Math.max(1, t.name().length() - 1), t.colorCode(), t.nameLower()));
                    }
                    searchPos = matchEnd;
                } else {
                    break;
                }
            }
        }

        if (matches.isEmpty()) return original;

        return sink -> {
            int[] pos = new int[]{0};
            return original.accept((idx, st, cp) -> {
                int p = pos[0];
                pos[0] += Character.charCount(cp);
                for (AnimMatch m : matches) {
                    if (p >= m.start && p < m.end) {
                        int ci = p - m.start;
                        net.minecraft.network.chat.Style newSt = applyCustomFont(st, m.nameLower);
                        net.minecraft.network.chat.TextColor animColor = computeAnimatedColor(m.colorCode, ci, m.totalLen, m.nameLower);
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

