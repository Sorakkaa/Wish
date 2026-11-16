package com.wish.client.util;

import com.wish.client.config.ModConfig;
import com.wish.client.features.SkyblockDetector;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ChatUtils {

    private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[.*?\\]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private ChatUtils() {}

    public static String getChatPrefix(String text) {
        if (text == null) return "/ac ";
        String lower = SkyblockDetector.stripColorCodes(text).toLowerCase(Locale.ROOT).trim();
        int colonIdx = lower.indexOf(':');
        String header = colonIdx != -1 ? lower.substring(0, colonIdx) : lower;

        boolean isParty = header.startsWith("party >") || header.startsWith("p >") || header.contains("[party]")
                || lower.startsWith("/pc ") || lower.startsWith("/p ") || lower.startsWith("/chat p");
        boolean isGuild = header.startsWith("guild >") || header.startsWith("officer >")
                || header.startsWith("g >") || header.startsWith("o >") || header.contains("[guild]")
                || lower.startsWith("/gc ") || lower.startsWith("/g ") || lower.startsWith("/o ") || lower.startsWith("/chat g");

        if (isParty) return "/pc ";
        if (isGuild) return "/gc ";
        return "/ac ";
    }

    public static String extractNameFromChat(String text) {
        if (text == null) return null;
        int colonIdx = text.indexOf(':');
        if (colonIdx == -1) return null;
        String beforeColon = SkyblockDetector.stripColorCodes(text.substring(0, colonIdx));
        String noBrackets = BRACKETS_PATTERN.matcher(beforeColon).replaceAll("").trim();

        Minecraft mc = Minecraft.getInstance();
        String myName = (mc != null && mc.player != null) ? mc.player.getScoreboardName() : null;
        String spoof = (ModConfig.INSTANCE.spoofedName != null) ? ModConfig.INSTANCE.spoofedName.trim() : null;
        String beforeLower = beforeColon.toLowerCase(Locale.ROOT);

        if (spoof != null && !spoof.isEmpty() && beforeLower.contains(spoof.toLowerCase(Locale.ROOT))) {
            return myName != null ? myName : spoof;
        }
        if (myName != null && beforeLower.contains(myName.toLowerCase(Locale.ROOT))) {
            return myName;
        }

        if (noBrackets.isEmpty()) return null;
        String[] words = WHITESPACE_PATTERN.split(noBrackets);
        if (words.length > 0 && !words[words.length - 1].isEmpty()) {
            return words[words.length - 1];
        }
        return null;
    }
}
