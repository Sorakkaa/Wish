package com.wish.client.color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
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

    private static net.minecraft.network.chat.Style applyCustomFont(net.minecraft.network.chat.Style st, String nameLower) {
        var mc = Minecraft.getInstance();
        boolean isSorakkaa = "sorakkaa".equals(nameLower) || (mc.player != null && "14b458e1-1374-4981-ab21-8bd942449ef7".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
        boolean isMairuy = "mairuy".equals(nameLower) || (mc.player != null && "43562a4a-93e2-437c-934c-64e17383ac00".equals(mc.player.getUUID().toString()) && mc.player.getScoreboardName().equalsIgnoreCase(nameLower));
        if (isSorakkaa) {
            int f = 1; // Font: 1 (Bold)
            st = st.withBold((f & 1) != 0);
            st = st.withItalic((f & 2) != 0);
            st = st.withUnderlined((f & 4) != 0);
            st = st.withStrikethrough((f & 8) != 0);
            st = st.withObfuscated((f & 16) != 0);
        } else if (isMairuy) {
            int f = 3; // Font: 3 (Bold + Italic)
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

        // If cosMode == 0 (Tout voir): Include other players (Sorakkaa, MaiRuy)
        if (cosMode == 0) {
            // Hardcode Sorakkaa
            if (!targetNames.contains("Sorakkaa")) targetNames.add("Sorakkaa");
            nameToColor.put("sorakkaa", "WAVE:#FFC6F9:#7B0000");
            
            // Hardcode MaiRuy
            if (!targetNames.contains("MaiRuy")) targetNames.add("MaiRuy");
            nameToColor.put("mairuy", "WAVE:#FFF4FE:#FF5AE6");
        }

        // Local player always overrides remote ("" = None = strip tags, no color)
        if (mc.player != null) {
            String localName = mc.player.getScoreboardName();
            if (localName != null && !localName.isEmpty()) {
                String localNameLower = localName.toLowerCase();
                boolean isDev = "sorakkaa".equals(localNameLower) || "mairuy".equals(localNameLower)
                        || mc.player.getUUID().toString().equals("14b458e1-1374-4981-ab21-8bd942449ef7")
                        || mc.player.getUUID().toString().equals("43562a4a-93e2-437c-934c-64e17383ac00");
                if (mc.player.getUUID().toString().equals("14b458e1-1374-4981-ab21-8bd942449ef7") && !nameToColor.containsKey(localNameLower)) {
                    nameToColor.put(localNameLower, "WAVE:#FFC6F9:#7B0000");
                }
                if (mc.player.getUUID().toString().equals("43562a4a-93e2-437c-934c-64e17383ac00") && !nameToColor.containsKey(localNameLower)) {
                    nameToColor.put(localNameLower, "WAVE:#FFF4FE:#FF5AE6");
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

                        boolean beforeOk = (searchPos == 0) || !isUsernameChar(text.charAt(searchPos - 1));
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
                        if (colorCode != null && !colorCode.isEmpty()) {
                            selfChanged = true;
                            String originalCaseName = text.substring(idx, idx + name.length());
                            String before = text.substring(0, idx);
                            // Strip only trailing \u00A7X color/format codes right before the name
                            String cleanBefore = before.replaceAll("(\u00A7[0-9a-fA-Fk-rK-R])+$", "");
                            String after = text.substring(idx + name.length());
                            if (isSorakkaa) {
                                if (cleanBefore.endsWith("˚˖❀ ")) {
                                    cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 4);
                                } else if (cleanBefore.endsWith("˚˖❀")) {
                                    cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 3);
                                }
                                
                                if (after.toLowerCase().startsWith(" the mistress")) {
                                    originalCaseName = "˚˖❀ " + originalCaseName + " The Mistress";
                                    after = after.substring(" the mistress".length());
                                } else if (after.toLowerCase().startsWith(" the mistres")) {
                                    originalCaseName = "˚˖❀ " + originalCaseName + " The Mistress";
                                    after = after.substring(" the mistres".length());
                                } else {
                                    originalCaseName = "˚˖❀ " + originalCaseName + " The Mistress";
                                }
                            } else if (isMairuy) {
                                if (cleanBefore.endsWith("˚˖❀ ")) {
                                    cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 4);
                                } else if (cleanBefore.endsWith("˚˖❀")) {
                                    cleanBefore = cleanBefore.substring(0, cleanBefore.length() - 3);
                                }
                                
                                if (after.toLowerCase().startsWith(" fluk")) {
                                    originalCaseName = "˚˖❀ " + originalCaseName + " Fluk";
                                    after = after.substring(" fluk".length());
                                } else if (after.toLowerCase().startsWith("fluk")) {
                                    originalCaseName = "˚˖❀ " + originalCaseName + " Fluk";
                                    after = after.substring("fluk".length());
                                } else {
                                    originalCaseName = "˚˖❀ " + originalCaseName + " Fluk";
                                }
                            }

                            String normHex = normalizeHex(colorCode);
                            String finalBefore = cleanBefore;
                            if (!isSorakkaa && !isMairuy) finalBefore = finalBefore.replaceAll("\u00A7[lL]", "");
                            if (!isMairuy) finalBefore = finalBefore.replaceAll("\u00A7[oO]", "");
                            String finalAfter = after;
                            if (!isSorakkaa && !isMairuy) finalAfter = finalAfter.replaceAll("\u00A7[lL]", "");
                            if (!isMairuy) finalAfter = finalAfter.replaceAll("\u00A7[oO]", "");

                            String pfx = "";
                            String sfx = "";
                            if (!isSorakkaa && !isMairuy) {
                                pfx = ModConfig.INSTANCE.customPrefix.replace('&', '\u00A7');
                                sfx = ModConfig.INSTANCE.customSuffix.replace('&', '\u00A7');
                            }
                            
                            if (!pfx.isEmpty() && !pfx.endsWith(" ")) {
                                pfx = pfx + " ";
                            }
                            if (!sfx.isEmpty() && !sfx.startsWith(" ")) {
                                sfx = " " + sfx;
                            }
                            originalCaseName = pfx + originalCaseName + sfx;

                            if ("CHROMA".equalsIgnoreCase(colorCode)) {
                                net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                if (!finalBefore.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalBefore));
                                double animSpeed = isSorakkaa ? 0.50 : (isMairuy ? 0.67 : ModConfig.INSTANCE.pseudoAnimationSpeed);
                                long scaledTime = (long)(System.currentTimeMillis() * animSpeed);
                                long step = scaledTime / 150;
                                for (int ci = 0; ci < originalCaseName.length(); ci++) {
                                    int colIdx = Math.abs((int) ((step + ci) % CHROMA_CODES.length));
                                    net.minecraft.network.chat.TextColor cc = net.minecraft.network.chat.TextColor.fromLegacyFormat(
                                        net.minecraft.ChatFormatting.getByCode(CHROMA_CODES[colIdx].charAt(1)));
                                    net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY;
                                    if (cc != null) st = st.withColor(cc);
                                    st = applyCustomFont(st, nameLower);
                                    String charStr = String.valueOf(originalCaseName.charAt(ci));
                                    builder.append(net.minecraft.network.chat.Component.literal(charStr).withStyle(st));
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
                                    if (!finalBefore.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalBefore));
                                    int len = Math.max(1, originalCaseName.length() - 1);
                                    for (int ci = 0; ci < originalCaseName.length(); ci++) {
                                        float ratio = (float) ci / len;
                                        
                                        double animSpeed = isSorakkaa ? 0.50 : (isMairuy ? 0.67 : ModConfig.INSTANCE.pseudoAnimationSpeed);
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
                                        st = applyCustomFont(st, nameLower);
                                        String charStr = String.valueOf(originalCaseName.charAt(ci));
                                        builder.append(net.minecraft.network.chat.Component.literal(charStr).withStyle(st));
                                    }
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                } catch (Exception ignored) {}
                            } else if (normHex != null) {
                                try {
                                    int hexInt = Integer.parseInt(normHex.substring(1), 16);
                                    net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromRgb(hexInt);
                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    if (!finalBefore.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalBefore));
                                    net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY.withColor(tc);
                                    st = applyCustomFont(st, nameLower);
                                    String nameStr = originalCaseName.replaceAll("\u00A7[lLoO]", "");
                                    builder.append(net.minecraft.network.chat.Component.literal(nameStr).withStyle(st));
                                    if (!finalAfter.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalAfter));
                                    newSelf = builder.withStyle(component.getStyle());
                                } catch (Exception ignored) {}
                            } else {
                                net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(colorCode.length() > 1 ? colorCode.charAt(1) : 'r');
                                if (fmt != null) {
                                    net.minecraft.network.chat.TextColor tc = net.minecraft.network.chat.TextColor.fromLegacyFormat(fmt);
                                    net.minecraft.network.chat.MutableComponent builder = net.minecraft.network.chat.Component.literal("");
                                    if (!finalBefore.isEmpty()) builder.append(net.minecraft.network.chat.Component.literal(finalBefore));
                                    net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY.withColor(tc);
                                    st = applyCustomFont(st, nameLower);
                                    String nameStr = originalCaseName.replaceAll("\u00A7[lLoO]", "");
                                    builder.append(net.minecraft.network.chat.Component.literal(nameStr).withStyle(st));
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
