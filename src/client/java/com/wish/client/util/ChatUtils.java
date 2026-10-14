package com.wish.client.util;

public final class ChatUtils {

    private ChatUtils() {}

    /**
     * Determines whether a message or context belongs to Party, Guild, or All Chat.
     */
    public static String getChatPrefix(String text) {
        if (text == null) return "/ac ";
        String lower = text.toLowerCase().trim();
        boolean isParty = lower.contains("party") || lower.contains("p >") || lower.contains("[party]") || lower.startsWith("/pc ") || lower.startsWith("/p ") || lower.startsWith("/chat p");
        boolean isGuild = lower.contains("guild") || lower.contains("officer") || lower.contains("g >") || lower.contains("o >") || lower.contains("[guild]") || lower.startsWith("/gc ") || lower.startsWith("/g ") || lower.startsWith("/o ") || lower.startsWith("/chat g");

        if (isParty) return "/pc ";
        if (isGuild) return "/gc ";
        return "/ac ";
    }

    /**
     * Extracts sender username from chat line.
     */
    public static String extractNameFromChat(String text) {
        if (text == null) return null;
        int colonIdx = text.indexOf(':');
        if (colonIdx == -1) return null;
        String beforeColon = text.substring(0, colonIdx);
        beforeColon = beforeColon.replaceAll("(?i)\\u00A7[0-9a-fk-or]", "");
        String noBrackets = beforeColon.replaceAll("\\[.*?\\]", "").trim();
        String[] words = noBrackets.split("\\s+");
        if (words.length > 0) {
            String lastWord = words[words.length - 1];
            if (lastWord.equalsIgnoreCase("Mistress") && words.length > 1) {
                String secondLast = words[words.length - 2];
                if (secondLast.equalsIgnoreCase("the") && words.length > 2) {
                    return words[words.length - 3];
                }
                return secondLast;
            }
            return lastWord;
        }
        return null;
    }
}
