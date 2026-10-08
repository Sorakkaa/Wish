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

    public static class ColorOption {
        public final String code;
        public final String name;
        public final int hexColor;

        public ColorOption(String code, String name, int hexColor) {
            this.code = code;
            this.name = name;
            this.hexColor = hexColor;
        }
    }

    public static final ColorOption[] COLORS = new ColorOption[]{
        new ColorOption("NONE",   "\u00A7o None",        0xFFAAAAAA),
        new ColorOption("CHROMA", "\uD83C\uDF08 Rainbow",    0xFF55FFFF),
        new ColorOption("HEX",    "\uD83C\uDFA8 Custom Hex", 0xFF55FFAA)
    };

    public static final String[] CHROMA_CODES = new String[]{"\u00A7c", "\u00A76", "\u00A7e", "\u00A7a", "\u00A7b", "\u00A79", "\u00A7d"};
    private static boolean initialized = false;

    public static ColorOption getCurrentOption() {
        return COLORS[2]; // Always HEX now
    }

    public static String normalizeHex(String code) {
        if (code == null) return null;
        String s = code.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6 && s.matches("[0-9A-Fa-f]{6}")) {
            return "#" + s.toUpperCase();
        }
        return null;
    }

    public static String getCurrentColorCode() {
        int anim = ModConfig.INSTANCE.pseudoAnimation;
        if (ModConfig.INSTANCE.pseudoAnimationSpeed == 0.0f) ModConfig.INSTANCE.pseudoAnimationSpeed = 1.0f;
        if (anim == 1) return "CHROMA";
        if (anim == 2) return "GRADIENT";
        if (anim == 3) return "WAVE";
        if (anim == 4) return "BREATHE";
        if (anim == 5) return "BLINK";
        
        String norm = normalizeHex(ModConfig.INSTANCE.customHexColor);
        return norm != null ? norm : "#FFC6F9";
    }

    public static void cycleColor() {
        // Disabled
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
    }



    public static void syncLocalPlayerColor() {
        // Obsolete: Plus de synchronisation backend
    }

    public static String getColorForPlayer(UUID uuid, String name) {
        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
        if (cosMode == 2) return null;

        var mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && (mc.player.getUUID().equals(uuid) || mc.player.getScoreboardName().equalsIgnoreCase(name));
        if (cosMode == 1 && !isLocal) return null;

        if ((uuid != null && "14b458e1-1374-4981-ab21-8bd942449ef7".equals(uuid.toString())) || "sorakkaa".equalsIgnoreCase(name)) {
            return "WAVE:#FFC6F9:#7B0000";
        }
        if ((uuid != null && "43562a4a-93e2-437c-934c-64e17383ac00".equals(uuid.toString())) || "mairuy".equalsIgnoreCase(name)) {
            return "WAVE:#FFF4FE:#FF5AE6";
        }
        if ((uuid != null && "3bf9f985-e9f5-451e-bd59-7c290677dc34".equals(uuid.toString())) || "fi4sk0".equalsIgnoreCase(name)) {
            return "WAVE:#C6FFDE:#4B537B";
        }
        if ((uuid != null && "102dc411-1e25-4570-ba52-3e0c774906a2".equals(uuid.toString())) || "smiss78".equalsIgnoreCase(name)) {
            return "WAVE:#FFFFFF:#000000";
        }
        if ((uuid != null && "f0051f86-eee9-4290-a7a7-2c8dcaade6a1".equals(uuid.toString())) || "notsley".equalsIgnoreCase(name)) {
            return "WAVE:#00F9FF:#FFFFFF";
        }
        if (isLocal) {
            return ModConfig.INSTANCE.enableNameColor ? getCurrentColorCode() : "";
        }
        return null;
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component) {
        return colorizeText(component, false);
    }

    public static net.minecraft.network.chat.Component colorizeText(net.minecraft.network.chat.Component component, boolean isChat) {
        if (component == null) return null;
        try {
            return modifyComponent(component, isChat);
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

    public static final net.minecraft.network.chat.FontDescription FONT_DEFAULT = net.minecraft.network.chat.FontDescription.DEFAULT;
    public static final net.minecraft.network.chat.FontDescription FONT_BADGES = new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("wish", "badges"));

    private static final Pattern EMBLEM_PREFIX_PATTERN = Pattern.compile(
        "^((?:\\u00A7[0-9a-fA-Fk-rK-R])*(?:\\uD83D\\uDD37|🔷|\\u25C6|\\u25C7|\\u25C8|\\u2756|\\u2728|\\u2727|\\u2605|\\u2B50|\\u2606|\\u2694|◆|◇|◈|❖|✦|✧|★|⭐|⚔))(.*)$"
    );

    private static boolean isEmblemOnly(String s) {
        if (s == null) return false;
        String clean = s.replaceAll("\u00A7[0-9a-fA-Fk-rK-R]", "").trim();
        return clean.equals("🔷") || clean.equals("◆") || clean.equals("◇") || clean.equals("◈") || clean.equals("❖")
                || clean.equals("✦") || clean.equals("✧") || clean.equals("★") || clean.equals("⭐") || clean.equals("⚔")
                || clean.equals("\uD83D\uDD37") || clean.equals("\u25C6") || clean.equals("\u25C7") || clean.equals("\u25C8")
                || clean.equals("\u2756") || clean.equals("\u2728") || clean.equals("\u2727") || clean.equals("\u2605")
                || clean.equals("\u2B50") || clean.equals("\u2606") || clean.equals("\u2694");
    }

    private static boolean isRankOrLevelString(String s) {
        if (s == null) return false;
        String clean = s.replaceAll("\u00A7[0-9a-fA-Fk-rK-R]", "").trim();
        if (clean.isEmpty()) return false;
        if (clean.matches("^(\\[?\\d+\\]?\\s*)+$")) return true;
        if (clean.matches("^\\[(VIP\\+?|MVP\\+{0,2}|YOUTUBE|ADMIN|MOD|HELPER|BUILD TEAM|OWNER|PIG\\+{0,3}|GM)\\]$")) return true;
        if (clean.matches("^(\\[?\\d+\\]?\\s*)+\\s*\\[(VIP\\+?|MVP\\+{0,2}|YOUTUBE|ADMIN|MOD|HELPER|BUILD TEAM|OWNER|PIG\\+{0,3}|GM)\\]$")) return true;
        return false;
    }

    private static net.minecraft.network.chat.MutableComponent createBadgeComponent() {
        net.minecraft.network.chat.Style badgeStyle = net.minecraft.network.chat.Style.EMPTY
            .withColor(0xFFFFFFFF)
            .withBold(false)
            .withItalic(false)
            .withUnderlined(false)
            .withStrikethrough(false)
            .withObfuscated(false)
            .withFont(FONT_BADGES)
            .withoutShadow();
        return net.minecraft.network.chat.Component.literal("\uE001").withStyle(badgeStyle);
    }
    private static String getLastColors(String text) {
        StringBuilder colors = new StringBuilder();
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\u00A7' || text.charAt(i) == '&') {
                char c = text.charAt(i + 1);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == 'r' || c == 'R') {
                    colors.setLength(0); // color resets formats
                    colors.append('\u00A7').append(c);
                } else if ((c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O')) {
                    colors.append('\u00A7').append(c);
                }
            }
        }
        return colors.toString();
    }

    private static void appendPrefixAndBadge(net.minecraft.network.chat.MutableComponent builder, String finalBefore, boolean addBadge) {
        if (!addBadge) {
            if (!finalBefore.isEmpty()) {
                builder.append(net.minecraft.network.chat.Component.literal(finalBefore));
            }
            return;
        }

        if (finalBefore.isEmpty()) {
            builder.append(createBadgeComponent());
            builder.append(net.minecraft.network.chat.Component.literal(" "));
            return;
        }

        Matcher m = EMBLEM_PREFIX_PATTERN.matcher(finalBefore);
        if (m.matches()) {
            String emblemPart = m.group(1).stripTrailing();
            String restPart = m.group(2).stripLeading();
            String carry = getLastColors(emblemPart);
            builder.append(net.minecraft.network.chat.Component.literal(emblemPart));
            builder.append(createBadgeComponent());
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
                builder.append(createBadgeComponent());
                builder.append(net.minecraft.network.chat.Component.literal(" "));
                builder.append(net.minecraft.network.chat.Component.literal(carry + p2));
            } else {
                builder.append(createBadgeComponent());
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
            if (comp.getString().startsWith("\uE001") && comp instanceof net.minecraft.network.chat.MutableComponent) {
                badgeOwnerIdx = i;
                break;
            }
        }
        if (badgeOwnerIdx <= 0) return;

        net.minecraft.network.chat.MutableComponent badgeOwner = (net.minecraft.network.chat.MutableComponent) siblings.get(badgeOwnerIdx);
        net.minecraft.network.chat.Component badgeComp = null;
        if (!badgeOwner.getSiblings().isEmpty() && badgeOwner.getSiblings().get(0).getString().startsWith("\uE001")) {
            badgeComp = badgeOwner.getSiblings().remove(0);
            if (!badgeOwner.getSiblings().isEmpty() && " ".equals(badgeOwner.getSiblings().get(0).getString())) {
                badgeOwner.getSiblings().remove(0);
            }
        } else {
            return;
        }

        net.minecraft.network.chat.MutableComponent badgeWithSpace = net.minecraft.network.chat.Component.literal("");
        badgeWithSpace.append(badgeComp);
        badgeWithSpace.append(net.minecraft.network.chat.Component.literal(" "));

        // Case A: A preceding sibling contains BOTH emblem and level/rank (e.g. "🔷[471] ")
        for (int k = 0; k < badgeOwnerIdx; k++) {
            net.minecraft.network.chat.Component prev = siblings.get(k);
            String prevStr = prev.getString();
            Matcher m = EMBLEM_PREFIX_PATTERN.matcher(prevStr);
            if (m.matches()) {
                String emb = m.group(1).stripTrailing();
                String rest = m.group(2).stripLeading();
                if (isRankOrLevelString(rest) || !rest.isEmpty()) {
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

        // Case B: Emblems and level/ranks are in separate siblings (e.g. [0] is 🔷, [1] is [471] )
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

        // Case C: No emblem found, but preceding siblings are rank/level (e.g. [MVP+])
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

        // Fallback: put back at badgeOwner position
        siblings.add(badgeOwnerIdx, badgeWithSpace);
    }

    private static net.minecraft.network.chat.Style applyCustomFont(net.minecraft.network.chat.Style st, String nameLower) {
        var mc = Minecraft.getInstance();
        boolean isSorakkaa = "sorakkaa".equals(nameLower) || (mc.player != null && "14b458e1-1374-4981-ab21-8bd942449ef7".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
        boolean isMairuy = "mairuy".equals(nameLower) || (mc.player != null && "43562a4a-93e2-437c-934c-64e17383ac00".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
        boolean isFi4sk0 = "fi4sk0".equals(nameLower) || (mc.player != null && "3bf9f985-e9f5-451e-bd59-7c290677dc34".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
        boolean isSmiss78 = "smiss78".equals(nameLower) || (mc.player != null && "102dc411-1e25-4570-ba52-3e0c774906a2".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
        boolean isNotsley = "notsley".equals(nameLower) || (mc.player != null && "f0051f86-eee9-4290-a7a7-2c8dcaade6a1".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
        if (isSorakkaa) {
            int f = ModConfig.INSTANCE.pseudoFont;
            st = st.withBold((f & 1) != 0);
            st = st.withItalic((f & 2) != 0);
            st = st.withUnderlined((f & 4) != 0);
            st = st.withStrikethrough((f & 8) != 0);
            st = st.withObfuscated((f & 16) != 0);
        } else if (isMairuy || isFi4sk0 || isNotsley) {
            int f = 3; // Font: 3 (Bold + Italic)
            st = st.withBold((f & 1) != 0);
            st = st.withItalic((f & 2) != 0);
            st = st.withUnderlined((f & 4) != 0);
            st = st.withStrikethrough((f & 8) != 0);
            st = st.withObfuscated((f & 16) != 0);
        } else if (isSmiss78) {
            int f = 11; // Font: 11 (Bold + Italic + Strikethrough)
            st = st.withBold((f & 1) != 0);
            st = st.withItalic((f & 2) != 0);
            st = st.withUnderlined((f & 4) != 0);
            st = st.withStrikethrough((f & 8) != 0);
            st = st.withObfuscated((f & 16) != 0);
        } else if (mc.player != null && nameLower.equals(mc.player.getScoreboardName().toLowerCase())) {
            int f = ModConfig.INSTANCE.pseudoFont;
            st = st.withBold((f & 1) != 0);
            st = st.withItalic((f & 2) != 0);
            st = st.withUnderlined((f & 4) != 0);
            st = st.withStrikethrough((f & 8) != 0);
            st = st.withObfuscated((f & 16) != 0);
        }
        return st;
    }

    private static net.minecraft.network.chat.Component modifyComponent(net.minecraft.network.chat.Component component, boolean isChat) {
        if (component == null) return null;

        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
        if (cosMode == 2) {
            // Mode 2: Tout masquer (Hide All cosmetics)
            return component;
        }

        var mc = Minecraft.getInstance();
        java.util.List<String> targetNames = new java.util.ArrayList<>();
        Map<String, String> nameToColor = new java.util.HashMap<>();

        // If cosMode == 0 (Tout voir): Include other players (Sorakkaa, MaiRuy, Fi4sk0)
        if (cosMode == 0) {
            // Hardcode Sorakkaa
            if (!targetNames.contains("Sorakkaa")) targetNames.add("Sorakkaa");
            nameToColor.put("sorakkaa", "WAVE:#FFC6F9:#7B0000");
            
            // Hardcode MaiRuy
            if (!targetNames.contains("MaiRuy")) targetNames.add("MaiRuy");
            nameToColor.put("mairuy", "WAVE:#FFF4FE:#FF5AE6");

            // Hardcode Fi4sk0
            if (!targetNames.contains("Fi4sk0")) targetNames.add("Fi4sk0");
            nameToColor.put("fi4sk0", "WAVE:#C6FFDE:#4B537B");
            
            // Hardcode Smiss78
            if (!targetNames.contains("Smiss78")) targetNames.add("Smiss78");
            nameToColor.put("smiss78", "WAVE:#FFFFFF:#000000");

            // Hardcode notsley
            if (!targetNames.contains("notsley")) targetNames.add("notsley");
            nameToColor.put("notsley", "WAVE:#00F9FF:#FFFFFF");
        }

        // Local player always overrides remote ("" = None = strip tags, no color)
        if (mc.player != null) {
            String localName = mc.player.getScoreboardName();
            if (localName != null && !localName.isEmpty()) {
                String localNameLower = localName.toLowerCase();
                boolean isDev = "sorakkaa".equals(localNameLower) || "mairuy".equals(localNameLower) || "fi4sk0".equals(localNameLower) || "smiss78".equals(localNameLower) || "notsley".equals(localNameLower)
                        || mc.player.getUUID().toString().equals("14b458e1-1374-4981-ab21-8bd942449ef7")
                        || mc.player.getUUID().toString().equals("43562a4a-93e2-437c-934c-64e17383ac00")
                        || mc.player.getUUID().toString().equals("3bf9f985-e9f5-451e-bd59-7c290677dc34")
                        || mc.player.getUUID().toString().equals("102dc411-1e25-4570-ba52-3e0c774906a2")
                        || mc.player.getUUID().toString().equals("f0051f86-eee9-4290-a7a7-2c8dcaade6a1");
                if (mc.player.getUUID().toString().equals("14b458e1-1374-4981-ab21-8bd942449ef7") && !nameToColor.containsKey(localNameLower)) {
                    nameToColor.put(localNameLower, "WAVE:#FFC6F9:#7B0000");
                }
                if (mc.player.getUUID().toString().equals("43562a4a-93e2-437c-934c-64e17383ac00") && !nameToColor.containsKey(localNameLower)) {
                    nameToColor.put(localNameLower, "WAVE:#FFF4FE:#FF5AE6");
                }
                if (mc.player.getUUID().toString().equals("3bf9f985-e9f5-451e-bd59-7c290677dc34") && !nameToColor.containsKey(localNameLower)) {
                    nameToColor.put(localNameLower, "WAVE:#C6FFDE:#4B537B");
                }
                if (mc.player.getUUID().toString().equals("102dc411-1e25-4570-ba52-3e0c774906a2") && !nameToColor.containsKey(localNameLower)) {
                    nameToColor.put(localNameLower, "WAVE:#FFFFFF:#000000");
                }
                if (mc.player.getUUID().toString().equals("f0051f86-eee9-4290-a7a7-2c8dcaade6a1") && !nameToColor.containsKey(localNameLower)) {
                    nameToColor.put(localNameLower, "WAVE:#00F9FF:#FFFFFF");
                }
                String localColor = "";
                
                if (isDev) {
                    // For dev, always use the hardcoded one!
                    localColor = nameToColor.get(localNameLower);
                } else {
                    localColor = ModConfig.INSTANCE.enableNameColor ? getCurrentColorCode() : "";
                }
                
                if (!targetNames.contains(localName)) targetNames.add(localName);
                nameToColor.put(localNameLower, localColor);
            }
        }

        if (targetNames.isEmpty()) {
            return component;
        }

        // Sort targetNames descending by length so longer names match before substrings (e.g. SorakkaaLover before Sorakkaa)
        targetNames.sort((a, b) -> Integer.compare(b.length(), a.length()));

        return recursivelyModify(component, targetNames, nameToColor, isChat, new boolean[]{false});
    }

    private static net.minecraft.network.chat.Component recursivelyModify(
            net.minecraft.network.chat.Component component,
            java.util.List<String> targetNames,
            Map<String, String> nameToColor,
            boolean isChat,
            boolean[] passedSeparator) {
        if (component == null) return null;

        boolean selfChanged = false;
        net.minecraft.network.chat.Component newSelf = null;

        var contents = component.getContents();
        if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents trans) {
            Object[] args = trans.getArgs();
            Object[] newArgs = new Object[args.length];
            boolean argsChanged = false;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof net.minecraft.network.chat.Component argComp) {
                    net.minecraft.network.chat.Component modifiedArg = recursivelyModify(argComp, targetNames, nameToColor, isChat, passedSeparator);
                    newArgs[i] = modifiedArg;
                    if (modifiedArg != argComp) argsChanged = true;
                } else if (arg instanceof String argStr) {
                    net.minecraft.network.chat.Component argComp = net.minecraft.network.chat.Component.literal(argStr);
                    net.minecraft.network.chat.Component modifiedArg = recursivelyModify(argComp, targetNames, nameToColor, isChat, passedSeparator);
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

                    // Find index of name with word boundary check
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
                        boolean isSorakkaa = "sorakkaa".equalsIgnoreCase(nameLower) || (mc.player != null && "14b458e1-1374-4981-ab21-8bd942449ef7".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
                        boolean isMairuy = "mairuy".equalsIgnoreCase(nameLower) || (mc.player != null && "43562a4a-93e2-437c-934c-64e17383ac00".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
                        boolean isFi4sk0 = "fi4sk0".equalsIgnoreCase(nameLower) || (mc.player != null && "3bf9f985-e9f5-451e-bd59-7c290677dc34".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
                        boolean isSmiss78 = "smiss78".equalsIgnoreCase(nameLower) || (mc.player != null && "102dc411-1e25-4570-ba52-3e0c774906a2".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
                        boolean isNotsley = "notsley".equalsIgnoreCase(nameLower) || (mc.player != null && "f0051f86-eee9-4290-a7a7-2c8dcaade6a1".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
                        if (colorCode != null && !colorCode.isEmpty()) {
                            selfChanged = true;
                            String originalCaseName = text.substring(idx, idx + name.length());
                            String before = text.substring(0, idx);
                            // Strip only trailing \u00A7X color/format codes right before the name
                            String cleanBefore = before.replaceAll("(\u00A7[0-9a-fA-Fk-rK-R])+$", "");
                            String after = text.substring(idx + name.length());

                            boolean addBadge = (isSorakkaa || isMairuy || isFi4sk0 || isSmiss78 || isNotsley) && !isChat;

                            cleanBefore = cleanBefore.replace("\uE001 ", "").replace("\uE001", "");

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
                            }

                            String pfx = "";
                            String sfx = "";
                            if (isSorakkaa) {
                                pfx = "˚˖❀ ";
                                if (after.toLowerCase().startsWith(" the mistress")) {
                                    sfx = " The Mistress";
                                    after = after.substring(" the mistress".length());
                                } else if (after.toLowerCase().startsWith(" the mistres")) {
                                    sfx = " The Mistress";
                                    after = after.substring(" the mistres".length());
                                } else {
                                    sfx = " The Mistress";
                                }
                            } else if (isMairuy) {
                                pfx = "˚˖❀ ";
                                if (after.toLowerCase().startsWith(" fluk")) {
                                    sfx = " Fluk";
                                    after = after.substring(" fluk".length());
                                } else if (after.toLowerCase().startsWith("fluk")) {
                                    sfx = " Fluk";
                                    after = after.substring("fluk".length());
                                } else {
                                    sfx = " Fluk";
                                }
                            } else if (isFi4sk0) {
                                pfx = "Bismillah ";
                                if (after.toLowerCase().startsWith(" les tenebres")) {
                                    sfx = " Les Tenebres";
                                    after = after.substring(" les tenebres".length());
                                } else if (after.toLowerCase().startsWith("les tenebres")) {
                                    sfx = " Les Tenebres";
                                    after = after.substring("les tenebres".length());
                                } else {
                                    sfx = " Les Tenebres";
                                }
                            } else if (isSmiss78) {
                                pfx = "˚˖❀ ";
                                if (after.toLowerCase().startsWith(" the collapse")) {
                                    sfx = " The Collapse";
                                    after = after.substring(" the collapse".length());
                                } else if (after.toLowerCase().startsWith("the collapse")) {
                                    sfx = " The Collapse";
                                    after = after.substring("the collapse".length());
                                } else {
                                    sfx = " The Collapse";
                                }
                            } else if (isNotsley) {
                                pfx = "✪ ";
                                if (after.toLowerCase().startsWith(" ✪")) {
                                    sfx = " ✪";
                                    after = after.substring(" ✪".length());
                                } else if (after.toLowerCase().startsWith("✪")) {
                                    sfx = " ✪";
                                    after = after.substring("✪".length());
                                } else {
                                    sfx = " ✪";
                                }
                            } else {
                                pfx = ModConfig.INSTANCE.customPrefix.replace('&', '\u00A7');
                                sfx = ModConfig.INSTANCE.customSuffix.replace('&', '\u00A7');
                            }

                            String normHex = normalizeHex(colorCode);
                            String finalBefore = cleanBefore;
                            if (!isSorakkaa && !isMairuy && !isFi4sk0 && !isSmiss78 && !isNotsley) finalBefore = finalBefore.replaceAll("\u00A7[lL]", "");
                            if (!isMairuy && !isFi4sk0 && !isSmiss78 && !isNotsley) finalBefore = finalBefore.replaceAll("\u00A7[oO]", "");
                            String finalAfter = after;
                            if (!isSorakkaa && !isMairuy && !isFi4sk0 && !isSmiss78 && !isNotsley) finalAfter = finalAfter.replaceAll("\u00A7[lL]", "");
                            if (!isMairuy && !isFi4sk0 && !isSmiss78 && !isNotsley) finalAfter = finalAfter.replaceAll("\u00A7[oO]", "");

                            if (!pfx.isEmpty() && !pfx.endsWith(" ")) {
                                pfx = pfx + " ";
                            }
                            if (!sfx.isEmpty() && !sfx.startsWith(" ")) {
                                sfx = " " + sfx;
                            }
                            originalCaseName = pfx + originalCaseName + sfx;

                            int pfxLen = pfx.length();
                            int sfxLen = sfx.length();
                            int totalLen = originalCaseName.length();
                            int nameStart = pfxLen;
                            int nameEnd = totalLen; // Apply font to suffix

                            if ("CHROMA".equalsIgnoreCase(colorCode)) {
                                net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                appendPrefixAndBadge(builder, finalBefore, addBadge);
                                double animSpeed = isSorakkaa ? 0.50 : (isMairuy ? 0.67 : (isFi4sk0 ? 0.15 : (isSmiss78 ? 0.46 : (isNotsley ? 0.50 : ModConfig.INSTANCE.pseudoAnimationSpeed))));
                                long scaledTime = (long)(System.currentTimeMillis() * animSpeed);
                                long step = scaledTime / 150;
                                for (int ci = 0; ci < originalCaseName.length(); ci++) {
                                    int colIdx = Math.abs((int) ((step + ci) % CHROMA_CODES.length));
                                    net.minecraft.network.chat.TextColor cc = net.minecraft.network.chat.TextColor.fromLegacyFormat(
                                        net.minecraft.ChatFormatting.getByCode(CHROMA_CODES[colIdx].charAt(1)));
                                    net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY;
                                    if (cc != null) st = st.withColor(cc);
                                    if (ci >= nameStart && ci < nameEnd) {
                                        st = applyCustomFont(st, nameLower);
                                    }
                                    String charStr = String.valueOf(originalCaseName.charAt(ci));
                                    StringBuilder fCodes = new StringBuilder();
                                    if (st.isBold()) fCodes.append("\u00A7l");
                                    if (st.isItalic()) fCodes.append("\u00A7o");
                                    if (st.isUnderlined()) fCodes.append("\u00A7n");
                                    if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                    if (st.isObfuscated()) fCodes.append("\u00A7k");
                                    builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + charStr).withStyle(st));
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
                                    appendPrefixAndBadge(builder, finalBefore, addBadge);
                                    int len = Math.max(1, originalCaseName.length() - 1);
                                    for (int ci = 0; ci < originalCaseName.length(); ci++) {
                                        float ratio = (float) ci / len;
                                        
                                        double animSpeed = isSorakkaa ? 0.50 : (isMairuy ? 0.67 : (isFi4sk0 ? 0.15 : (isSmiss78 ? 0.46 : (isNotsley ? 0.50 : ModConfig.INSTANCE.pseudoAnimationSpeed))));
                                        long scaledTime = (long)(System.currentTimeMillis() * animSpeed);
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
                                        net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY.withColor(cc);
                                        if (ci >= nameStart && ci < nameEnd) {
                                            st = applyCustomFont(st, nameLower);
                                        }
                                        String charStr = String.valueOf(originalCaseName.charAt(ci));
                                        StringBuilder fCodes = new StringBuilder();
                                        if (st.isBold()) fCodes.append("\u00A7l");
                                        if (st.isItalic()) fCodes.append("\u00A7o");
                                        if (st.isUnderlined()) fCodes.append("\u00A7n");
                                        if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                        if (st.isObfuscated()) fCodes.append("\u00A7k");
                                        builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + charStr).withStyle(st));
                                    }
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                } catch (Exception ignored) {}
                            } else if (normHex != null) {
                                try {
                                    int hexInt = Integer.parseInt(normHex.substring(1), 16);
                                    net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromRgb(hexInt);
                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    appendPrefixAndBadge(builder, finalBefore, addBadge);
                                    if (!pfx.isEmpty()) {
                                        builder.append(net.minecraft.network.chat.Component.literal(pfx).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(tc)));
                                    }
                                    String justName = originalCaseName.substring(nameStart, nameEnd).replaceAll("\u00A7[lLoO]", "");
                                    net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY.withColor(tc), nameLower);
                                    StringBuilder fCodes = new StringBuilder();
                                    if (st.isBold()) fCodes.append("\u00A7l");
                                    if (st.isItalic()) fCodes.append("\u00A7o");
                                    if (st.isUnderlined()) fCodes.append("\u00A7n");
                                    if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                    if (st.isObfuscated()) fCodes.append("\u00A7k");
                                    builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + justName).withStyle(st));
                                    if (!sfx.isEmpty()) {
                                        builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + sfx).withStyle(st));
                                    }
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                } catch (Exception ignored) {}
                            } else {
                                net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(colorCode.length() > 1 ? colorCode.charAt(1) : 'r');
                                if (fmt != null) {
                                    net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromLegacyFormat(fmt);
                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    appendPrefixAndBadge(builder, finalBefore, addBadge);
                                    if (!pfx.isEmpty()) {
                                        builder.append(net.minecraft.network.chat.Component.literal(pfx).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(tc)));
                                    }
                                    String justName = originalCaseName.substring(nameStart, nameEnd).replaceAll("\u00A7[lLoO]", "");
                                    net.minecraft.network.chat.Style st = applyCustomFont(net.minecraft.network.chat.Style.EMPTY.withColor(tc), nameLower);
                                    StringBuilder fCodes = new StringBuilder();
                                    if (st.isBold()) fCodes.append("\u00A7l");
                                    if (st.isItalic()) fCodes.append("\u00A7o");
                                    if (st.isUnderlined()) fCodes.append("\u00A7n");
                                    if (st.isStrikethrough()) fCodes.append("\u00A7m");
                                    if (st.isObfuscated()) fCodes.append("\u00A7k");
                                    builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + justName).withStyle(st));
                                    if (!sfx.isEmpty()) {
                                        builder.append(net.minecraft.network.chat.Component.literal(fCodes.toString() + sfx).withStyle(st));
                                    }
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
                if (isChat && passedSeparator != null && !passedSeparator[0]) {
                    if (rawText.contains(":") || rawText.contains("»") || rawText.contains(">") || rawText.contains("\u00bb")) {
                        passedSeparator[0] = true;
                    }
                }
            }
        }
        java.util.List<net.minecraft.network.chat.Component> originalSiblings = component.getSiblings();
        java.util.List<net.minecraft.network.chat.Component> newSiblings = new java.util.ArrayList<>(originalSiblings.size());
        boolean siblingsChanged = false;

        for (net.minecraft.network.chat.Component sibling : originalSiblings) {
            net.minecraft.network.chat.Component modifiedSibling = recursivelyModify(sibling, targetNames, nameToColor, isChat, passedSeparator);
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

    // Legacy \u00A7-code chroma for plain chat text
    private static String getChromaName(String name) {
        StringBuilder sb = new StringBuilder();
        long step = (System.currentTimeMillis() / 150);
        for (int i = 0; i < name.length(); i++) {
            int colIdx = Math.abs((int) ((step + i) % CHROMA_CODES.length));
            sb.append(CHROMA_CODES[colIdx]).append(name.charAt(i));
        }
        sb.append("\u00A7r");
        return sb.toString();
    }
}
