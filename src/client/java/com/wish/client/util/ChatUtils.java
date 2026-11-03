package com.wish.client.util;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;

public final class ChatUtils {

    private ChatUtils() {}

    
    public static String getChatPrefix(String text) {
        if (text == null) return "/ac ";
        String lower = text.toLowerCase().trim();
        boolean isParty = lower.contains("party") || lower.contains("p >") || lower.contains("[party]") || lower.startsWith("/pc ") || lower.startsWith("/p ") || lower.startsWith("/chat p");
        boolean isGuild = lower.contains("guild") || lower.contains("officer") || lower.contains("g >") || lower.contains("o >") || lower.contains("[guild]") || lower.startsWith("/gc ") || lower.startsWith("/g ") || lower.startsWith("/o ") || lower.startsWith("/chat g");

        if (isParty) return "/pc ";
        if (isGuild) return "/gc ";
        return "/ac ";
    }

    
    public static String extractNameFromChat(String text) {
        if (text == null) return null;
        int colonIdx = text.indexOf(':');
        if (colonIdx == -1) return null;
        String beforeColon = text.substring(0, colonIdx);
        beforeColon = beforeColon.replaceAll("(?i)\\u00A7[0-9a-fk-or]", "");
        String noBrackets = beforeColon.replaceAll("\\[.*?\\]", "").trim();

        Minecraft mc = Minecraft.getInstance();
        String myName = (mc != null && mc.player != null) ? mc.player.getScoreboardName() : null;
        String spoof = (ModConfig.INSTANCE.spoofedName != null) ? ModConfig.INSTANCE.spoofedName.trim() : null;

        if (spoof != null && !spoof.isEmpty() && beforeColon.toLowerCase().contains(spoof.toLowerCase())) {
            return myName != null ? myName : spoof;
        }
        if (myName != null && beforeColon.toLowerCase().contains(myName.toLowerCase())) {
            return myName;
        }

        String[] words = noBrackets.split("\\s+");
        if (words.length > 0) {
            return words[words.length - 1];
        }
        return null;
    }
}
