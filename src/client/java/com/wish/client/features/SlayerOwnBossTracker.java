package com.wish.client.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerOwnBossTracker {

    public static final SlayerOwnBossTracker INSTANCE = new SlayerOwnBossTracker();

    public static final int MISSING_BOSS_RESET_TICKS = 60;
    public static final int SLAIN_BOSS_RESET_TICKS = 5;
    public static final long MIN_REANNOUNCE_COOLDOWN_MS = 4000L;
    private static final int MAX_PARTY_MEMBERS = 64;

    private static final Pattern RANK_TAG_PATTERN = Pattern.compile("\\[[^\\]]+\\]\\s*");
    private static final Pattern USERNAME_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final String[] KNOWN_SLAYER_BOSSES = {
        "revenant",
        "atoned",
        "tarantula",
        "constrictor",
        "sven",
        "voidgloom",
        "inferno demonlord",
        "riftstalker"
    };

    private final List<String> partyMembers = new ArrayList<>();
    private boolean inParty;
    private boolean announcedForCurrentBoss;
    private int bossMissingTicks;
    private boolean bossSlainChatSeen;
    private long lastAnnounceMs;

    public SlayerOwnBossTracker() {
    }

    public synchronized void reset() {
        inParty = false;
        partyMembers.clear();
        resetOwnBossState();
    }

    public synchronized void resetOwnBossState() {
        announcedForCurrentBoss = false;
        bossMissingTicks = 0;
        bossSlainChatSeen = false;
        lastAnnounceMs = 0L;
    }

    public synchronized boolean isInParty() {
        return inParty;
    }

    public synchronized boolean hasOtherPartyMembers() {
        return inParty && !partyMembers.isEmpty();
    }

    public synchronized boolean isBossActive() {
        return announcedForCurrentBoss;
    }

    public synchronized boolean isPartyMember(String rawName) {
        if (!inParty || rawName == null || rawName.isEmpty()) {
            return false;
        }
        for (int i = 0; i < partyMembers.size(); i++) {
            if (rawName.equalsIgnoreCase(partyMembers.get(i))) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<String> getPartyMembersSnapshot() {
        return List.copyOf(partyMembers);
    }

    public boolean canAutoSendCoords(
            boolean enableSlayerCarry,
            boolean enableAutoCoords,
            boolean hasTrackedCarries,
            boolean hasNearbyPartyCarrier
    ) {
        if (!enableSlayerCarry || !enableAutoCoords || hasTrackedCarries || !hasNearbyPartyCarrier) {
            return false;
        }
        return hasOtherPartyMembers();
    }

    public synchronized boolean tryAnnounceOwnBoss(long nowMs, boolean nearBlazeSplitDemon) {
        if (announcedForCurrentBoss || nearBlazeSplitDemon) {
            return false;
        }
        if (lastAnnounceMs > 0L && (nowMs - lastAnnounceMs) < MIN_REANNOUNCE_COOLDOWN_MS) {
            return false;
        }
        announcedForCurrentBoss = true;
        lastAnnounceMs = nowMs;
        bossMissingTicks = 0;
        bossSlainChatSeen = false;
        return true;
    }

    public synchronized void onOwnBossTickEnd(boolean anyOwnBossEntityAlive, boolean nearLastBossSplitAlive) {
        if (anyOwnBossEntityAlive || nearLastBossSplitAlive) {
            bossMissingTicks = 0;
            return;
        }
        if (!announcedForCurrentBoss) {
            return;
        }
        bossMissingTicks++;
        int threshold = bossSlainChatSeen ? SLAIN_BOSS_RESET_TICKS : MISSING_BOSS_RESET_TICKS;
        if (bossMissingTicks >= threshold) {
            announcedForCurrentBoss = false;
            bossMissingTicks = 0;
            bossSlainChatSeen = false;
        }
    }

    public synchronized void onChatMessage(String rawMessage, String selfName) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }
        String clean = SkyblockDetector.stripColorCodes(rawMessage).trim();
        if (clean.isEmpty() || clean.contains("[Wish]")) {
            return;
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        String selfLower = selfName != null ? selfName.trim().toLowerCase(Locale.ROOT) : "";

        if (isSlayerQuestEndMessage(lower)) {
            bossSlainChatSeen = true;
        }

        if (isPartyExitOrDisbandMessage(lower)) {
            inParty = false;
            partyMembers.clear();
            return;
        }

        if (lower.startsWith("party > ")) {
            handlePartyChatMessage(clean, selfLower);
            return;
        }

        if (lower.startsWith("party members (1)")) {
            inParty = true;
            partyMembers.clear();
            return;
        }

        if (lower.startsWith("party leader:")
                || lower.startsWith("party moderators:")
                || lower.startsWith("party members:")
                || lower.startsWith("you'll be partying with:")) {
            handleListedPartyMembers(clean, selfLower);
            return;
        }

        handlePartyTransitionMessage(clean, lower, selfLower);
    }

    private void handlePartyChatMessage(String clean, String selfLower) {
        inParty = true;
        int colonIdx = clean.indexOf(':', 8);
        if (colonIdx <= 8) {
            return;
        }
        String senderPart = clean.substring(8, colonIdx);
        String sender = extractLastUsername(senderPart);
        addPartyMemberIfOther(sender, selfLower);
    }

    private void handleListedPartyMembers(String clean, String selfLower) {
        inParty = true;
        int colonIdx = clean.indexOf(':');
        if (colonIdx < 0 || colonIdx + 1 >= clean.length()) {
            return;
        }
        String listPart = clean.substring(colonIdx + 1)
                .replace('●', ' ')
                .replace('○', ' ')
                .replace('?', ' ')
                .replace(',', ' ');
        String strippedRanks = RANK_TAG_PATTERN.matcher(listPart).replaceAll(" ");
        Matcher m = USERNAME_TOKEN_PATTERN.matcher(strippedRanks);
        while (m.find()) {
            addPartyMemberIfOther(m.group(), selfLower);
        }
    }

    private void handlePartyTransitionMessage(String clean, String lower, String selfLower) {
        if (lower.startsWith("you have joined ")) {
            int aposIdx = lower.indexOf("'s party");
            if (aposIdx < 0) {
                aposIdx = lower.indexOf("\u2019s party");
            }
            if (aposIdx > 16) {
                inParty = true;
                addPartyMemberIfOther(extractLastUsername(clean.substring(16, aposIdx)), selfLower);
            }
            return;
        }

        if (lower.endsWith(" joined the party.")) {
            inParty = true;
            String prefix = clean.substring(0, clean.length() - " joined the party.".length());
            addPartyMemberIfOther(extractLastUsername(prefix), selfLower);
            return;
        }

        if (lower.contains(" was transferred to ")) {
            handlePartyTransferMessage(clean, lower, selfLower);
            return;
        }

        int promoIdx = lower.indexOf(" has been promoted to party ");
        if (promoIdx > 0) {
            inParty = true;
            addPartyMemberIfOther(extractLastUsername(clean.substring(0, promoIdx)), selfLower);
            return;
        }

        String removedName = extractRemovedMemberName(clean, lower);
        if (removedName != null && !removedName.isEmpty()) {
            removePartyMember(removedName);
        }
    }

    private void handlePartyTransferMessage(String clean, String lower, String selfLower) {
        inParty = true;
        int toIdx = lower.indexOf(" to ");
        if (toIdx < 0) {
            return;
        }
        int afterTo = toIdx + 4;
        int byIdx = lower.indexOf(" by ", afterTo);
        int becauseIdx = lower.indexOf(" because ", afterTo);
        int endNewLeader = clean.length();
        if (byIdx >= 0) {
            endNewLeader = Math.min(endNewLeader, byIdx);
        }
        if (becauseIdx >= 0) {
            endNewLeader = Math.min(endNewLeader, becauseIdx);
        }
        addPartyMemberIfOther(extractLastUsername(clean.substring(afterTo, endNewLeader)), selfLower);

        if (byIdx >= 0) {
            addPartyMemberIfOther(extractFirstUsername(clean.substring(byIdx + 4)), selfLower);
        } else if (becauseIdx >= 0 && lower.indexOf(" left", becauseIdx) > becauseIdx) {
            String leftSegment = clean.substring(becauseIdx + 9, lower.indexOf(" left", becauseIdx));
            String leftUser = extractLastUsername(leftSegment);
            if (!leftUser.isEmpty()) {
                removePartyMember(leftUser);
            }
        }
    }

    private static String extractRemovedMemberName(String clean, String lower) {
        int leftIdx = lower.indexOf(" has left the party.");
        if (leftIdx > 0) {
            return extractLastUsername(clean.substring(0, leftIdx));
        }
        int removedIdx = lower.indexOf(" was removed from ");
        if (removedIdx > 0) {
            return extractLastUsername(clean.substring(0, removedIdx));
        }
        int kickedPartyIdx = lower.indexOf(" has been kicked from the party.");
        if (kickedPartyIdx > 0) {
            return extractLastUsername(clean.substring(0, kickedPartyIdx));
        }
        if (lower.startsWith("kicked ")) {
            int reasonIdx = lower.indexOf(" because ", 7);
            String segment = reasonIdx > 7 ? clean.substring(7, reasonIdx) : clean.substring(7);
            return extractLastUsername(segment);
        }
        return null;
    }

    private void addPartyMemberIfOther(String rawName, String selfLower) {
        if (rawName == null || rawName.isEmpty()) {
            return;
        }
        String norm = rawName.toLowerCase(Locale.ROOT);
        if (!selfLower.isEmpty() && norm.equals(selfLower)) {
            return;
        }
        if (!partyMembers.contains(norm) && partyMembers.size() < MAX_PARTY_MEMBERS) {
            partyMembers.add(norm);
        }
    }

    private void removePartyMember(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return;
        }
        String norm = rawName.toLowerCase(Locale.ROOT);
        partyMembers.remove(norm);
    }

    private static boolean isSlayerQuestEndMessage(String lower) {
        return lower.contains("slayer boss slain")
                || lower.contains("slayer quest complete")
                || lower.contains("slayer quest failed")
                || lower.contains("your slayer quest has failed");
    }

    private static boolean isPartyExitOrDisbandMessage(String lower) {
        return lower.contains("you left the party.")
                || lower.contains("you have been kicked from the party")
                || lower.contains("you are not currently in a party")
                || lower.contains("you are not in a party")
                || lower.contains("the party was disbanded")
                || lower.contains("has disbanded the party")
                || lower.contains("all party members have left");
    }

    private static String extractLastUsername(String segment) {
        if (segment == null || segment.isEmpty()) {
            return "";
        }
        String noRanks = RANK_TAG_PATTERN.matcher(segment).replaceAll(" ");
        Matcher m = USERNAME_TOKEN_PATTERN.matcher(noRanks);
        String last = "";
        while (m.find()) {
            last = m.group();
        }
        return last;
    }

    private static String extractFirstUsername(String segment) {
        if (segment == null || segment.isEmpty()) {
            return "";
        }
        String noRanks = RANK_TAG_PATTERN.matcher(segment).replaceAll(" ");
        Matcher m = USERNAME_TOKEN_PATTERN.matcher(noRanks);
        return m.find() ? m.group() : "";
    }

    public static boolean isBlazeSplitDemonName(String strippedLowerName) {
        if (strippedLowerName == null || strippedLowerName.isEmpty()) {
            return false;
        }
        return strippedLowerName.contains("typhoeus") || strippedLowerName.contains("quazii");
    }

    public static boolean isPetNametag(String strippedLowerName) {
        return strippedLowerName != null && strippedLowerName.contains("[lv");
    }

    public static boolean isOwnSlayerBossHologram(String strippedLowerName, String selfNameLower) {
        if (strippedLowerName == null || strippedLowerName.isEmpty()
                || selfNameLower == null || selfNameLower.isEmpty()) {
            return false;
        }
        if (isPetNametag(strippedLowerName) || isBlazeSplitDemonName(strippedLowerName)) {
            return false;
        }
        if (matchesSpawnedBy(strippedLowerName, selfNameLower)) {
            return true;
        }
        return matchesPossessiveSlayerBoss(strippedLowerName, selfNameLower);
    }

    private static boolean matchesSpawnedBy(String lower, String selfLower) {
        int idx = lower.indexOf("spawned by:");
        int cursor = idx >= 0 ? idx + "spawned by:".length() : -1;
        if (cursor < 0) {
            idx = lower.indexOf("spawned by ");
            cursor = idx >= 0 ? idx + "spawned by ".length() : -1;
        }
        if (cursor < 0) {
            return false;
        }
        cursor = skipWhitespaceAndRanks(lower, cursor);
        if (!lower.startsWith(selfLower, cursor)) {
            return false;
        }
        int end = cursor + selfLower.length();
        return end >= lower.length() || !isUsernameChar(lower.charAt(end));
    }

    private static boolean matchesPossessiveSlayerBoss(String lower, String selfLower) {
        int searchFrom = 0;
        while (searchFrom < lower.length()) {
            int pos = lower.indexOf(selfLower, searchFrom);
            if (pos < 0) {
                return false;
            }
            boolean leftOk = pos == 0 || !isUsernameChar(lower.charAt(pos - 1));
            int afterName = pos + selfLower.length();
            if (leftOk && afterName + 2 <= lower.length()) {
                char quote = lower.charAt(afterName);
                if ((quote == '\'' || quote == '\u2019') && lower.charAt(afterName + 1) == 's') {
                    String rest = lower.substring(afterName + 2);
                    for (String bossKeyword : KNOWN_SLAYER_BOSSES) {
                        if (rest.contains(bossKeyword)) {
                            return true;
                        }
                    }
                }
            }
            searchFrom = pos + 1;
        }
        return false;
    }

    private static int skipWhitespaceAndRanks(String s, int pos) {
        int len = s.length();
        while (pos < len) {
            char c = s.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            if (c == '[') {
                int close = s.indexOf(']', pos + 1);
                if (close > pos) {
                    pos = close + 1;
                    continue;
                }
            }
            break;
        }
        return pos;
    }

    private static boolean isUsernameChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }
}
