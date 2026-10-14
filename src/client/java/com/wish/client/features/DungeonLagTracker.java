package com.wish.client.features;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonLagTracker {
    public static long clientLagMs = 0;
    public static long serverLagMs = 0;
    public static long pingLagMs = 0;
    public static long lastScoreboardChangeRealTime = 0;
    public static long lastRunEndTimeMs = 0;
    private static long startScoreboardTime = -1;
    private static long lastSeenScoreboardTime = -1;
    private static long realStartTimeMs = 0;
    private static long officialServerTime = 0;
    public static boolean inRun = false;
    public static boolean hasRunEndedThisInstance = false;
    private static volatile boolean endTriggered = false;
    private static long lastTickTime = 0;

    public static boolean isInRun() {
        return inRun || ModConfig.INSTANCE.alwaysInM7F7;
    }

    private static final Pattern TIME_MS_PATTERN = Pattern.compile("(?:(\\d{1,2})m\\s*)?(\\d{1,2})(?:\\.(\\d+))?s");
    private static final Pattern TIME_COLON_PATTERN = Pattern.compile("(?:\\b|Time:\\s*)(\\d{1,2}):(\\d{2})(?:\\.(\\d+))?\\b");
    private static final Pattern DEFEATED_PATTERN = Pattern.compile("(?s).*Defeated.*\\s+in\\s+.*");
    private static final Pattern SCORE_PATTERN = Pattern.compile("(?si).*(Team Score|Score d'équipe|Score de l'équipe|Score total|Score\\s*:).*");
    private static final Pattern EXTRA_STATS_PATTERN = Pattern.compile("(?si).*(> EXTRA STATS <|> STATISTIQUES).*");
    private static final Pattern CLEAR_TIME_PATTERN = Pattern.compile("(?si).*(Dungeon clear time|Temps d'achèvement|Temps du donjon|Temps de réussite|Temps total).*");

    public static boolean checkScoreboardLine(String cleanLine) {
        if (!ModConfig.INSTANCE.enableLagTimeLost) return false;
        if (cleanLine == null || cleanLine.isEmpty()) return false;

        String lower = cleanLine.toLowerCase();
        if (lower.contains("time") || lower.contains("temps")) {
            long timeMs = extractTime(cleanLine);
            if (!isInRun() && timeMs >= 0) {
                if ((SkyblockDetector.isInDungeon || ModConfig.INSTANCE.alwaysInM7F7) && System.currentTimeMillis() - lastRunEndTimeMs > 10000 && startRun()) {
                    startScoreboardTime = timeMs;
                    lastSeenScoreboardTime = timeMs;
                    realStartTimeMs = System.currentTimeMillis();
                    lastScoreboardChangeRealTime = realStartTimeMs;
                }
            } else if (isInRun() && timeMs >= 0 && startScoreboardTime == -1) {
                startScoreboardTime = timeMs;
                lastSeenScoreboardTime = timeMs;
                realStartTimeMs = System.currentTimeMillis();
                lastScoreboardChangeRealTime = realStartTimeMs;
            } else if (isInRun() && timeMs > lastSeenScoreboardTime) {
                long jump = timeMs - lastSeenScoreboardTime;
                long currentSpike = 0;
                
                if (jump > 1000) {
                    long pingSpike = jump - 1000;
                    pingLagMs += pingSpike;
                    currentSpike = pingSpike;
                }

                long totalServerElapsed = timeMs - startScoreboardTime;
                long totalRealElapsed = System.currentTimeMillis() - realStartTimeMs;
                
                long expectedRealElapsed = totalServerElapsed + clientLagMs;
                long newServerLag = totalRealElapsed - expectedRealElapsed;
                if (newServerLag < 0) newServerLag = 0;
                
                serverLagMs = newServerLag;
                lastSeenScoreboardTime = timeMs;
                lastScoreboardChangeRealTime = System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }

    public static void onClientTick() {
        if (!ModConfig.INSTANCE.enableLagTimeLost) {
            HudStateContainer.updateLagState(false, 0, null);
            return;
        }
        if (!isInRun()) {
            lastTickTime = 0;
            HudStateContainer.updateLagState(false, 0, null);
            return;
        }

        long now = System.currentTimeMillis();
        if (lastTickTime > 0) {
            long delta = now - lastTickTime;
            if (delta >= 500) {
                clientLagMs += delta;
                if (lastScoreboardChangeRealTime > 0) {
                    lastScoreboardChangeRealTime += delta;
                }
            }
        }
        lastTickTime = now;

        long ms = 0;
        boolean isCurrentlyLagging = false;
        if (lastScoreboardChangeRealTime > 0) {
            long timeSinceUpdate = now - lastScoreboardChangeRealTime;
            if (timeSinceUpdate > 1500) { // 1500ms means 500ms of lag
                ms = timeSinceUpdate - 1000;
                isCurrentlyLagging = true;
            }
        }
        Minecraft mc = Minecraft.getInstance();
        HudStateContainer.updateLagState(isCurrentlyLagging, ms, mc.font);
    }

    public static void onChatMessage(String text) {
        if (!ModConfig.INSTANCE.enableLagTimeLost) return;
        if (text == null) return;
        String clean = text.replaceAll("(?i)\\u00A7[0-9a-fk-or]", "").replace('\u00A0', ' ').trim();
        
        // Check start of dungeon
        if (clean.contains("The Dungeon starts in") ||
            clean.contains("The dungeon starts in") ||
            clean.contains("Le donjon commence dans") ||
            clean.contains("Here, I found this map when I first entered the dungeon") ||
            clean.contains("Tiens, j'ai trouvé cette carte") ||
            clean.contains("Gate opens in") ||
            clean.contains("La porte s'ouvre dans") ||
            (clean.contains("Starting in") && clean.contains("second"))) {
            startRun();
            return;
        }

        // Check boss defeat time
        boolean isDefeated = DEFEATED_PATTERN.matcher(clean).matches();
        if (isDefeated) {
            long defTime = extractTime(clean);
            if (defTime > 0) {
                officialServerTime = defTime;
            }
        }

        // Check end of dungeon triggers
        boolean isScore = SCORE_PATTERN.matcher(clean).matches();
        boolean isExtraStats = EXTRA_STATS_PATTERN.matcher(clean).matches();
        boolean isClearTime = CLEAR_TIME_PATTERN.matcher(clean).matches();

        if (isClearTime) {
            long clearTime = extractTime(clean);
            if (clearTime > 0) {
                officialServerTime = clearTime;
            }
        }

        if ((isInRun() || officialServerTime > 0) && !endTriggered && (isScore || isExtraStats || isClearTime)) {
            endTriggered = true;

            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(600);
                } catch (Exception ignored) {}

                long totalLagMs = clientLagMs + serverLagMs + pingLagMs;

                double seconds = totalLagMs / 1000.0;
                String timeStr;
                if (seconds >= 60) {
                    int m = (int) (seconds / 60);
                    double s = seconds % 60;
                    timeStr = String.format(java.util.Locale.US, "%dm %.2fs", m, s);
                } else {
                    timeStr = String.format(java.util.Locale.US, "%.2fs", seconds);
                }
                
                if (ModConfig.INSTANCE.sendLagTimeLost) {
                    sendPartyMessage("Time lost to lag: " + timeStr);
                }

                inRun = false;
                hasRunEndedThisInstance = true;
                endTriggered = false;
                officialServerTime = 0;
                clientLagMs = 0;
                serverLagMs = 0;
                pingLagMs = 0;
                lastScoreboardChangeRealTime = 0;
                lastRunEndTimeMs = System.currentTimeMillis();
            });
        }
    }

    public static void onScoreboardParsed(boolean foundTimer) {
        if (isInRun() && !foundTimer) {
            // Timer is hidden by the server (e.g. boss fight). Pause ping lag tracking.
            lastScoreboardChangeRealTime = System.currentTimeMillis();
        }
    }

    public static void forceEndRun() {
        inRun = false;
        hasRunEndedThisInstance = false;
        clientLagMs = 0;
        serverLagMs = 0;
        pingLagMs = 0;
        lastScoreboardChangeRealTime = 0;
    }

    private static boolean startRun() {
        if (hasRunEndedThisInstance) return false;
        clientLagMs = 0;
        serverLagMs = 0;
        pingLagMs = 0;
        officialServerTime = 0;
        inRun = true;
        endTriggered = false;
        lastTickTime = 0;
        startScoreboardTime = -1;
        lastSeenScoreboardTime = -1;
        lastScoreboardChangeRealTime = 0;
        realStartTimeMs = System.currentTimeMillis();
        return true;
    }

    public static long extractTime(String text) {
        if (text == null) return -1;

        Matcher m = TIME_MS_PATTERN.matcher(text);
        if (m.find()) {
            try {
                long min = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                if (m.group(3) != null) {
                    String msStr = m.group(3);
                    if (msStr.length() == 1) ms = Long.parseLong(msStr) * 100;
                    else if (msStr.length() == 2) ms = Long.parseLong(msStr) * 10;
                    else if (msStr.length() == 3) ms = Long.parseLong(msStr);
                    else ms = Long.parseLong(msStr.substring(0, 3));
                }
                return (min * 60 + sec) * 1000 + ms;
            } catch (Exception ignored) {}
        }

        Matcher m2 = TIME_COLON_PATTERN.matcher(text);
        if (m2.find()) {
            try {
                long min = Long.parseLong(m2.group(1));
                long sec = Long.parseLong(m2.group(2));
                long ms = 0;
                if (m2.group(3) != null) {
                    String msStr = m2.group(3);
                    if (msStr.length() == 1) ms = Long.parseLong(msStr) * 100;
                    else if (msStr.length() == 2) ms = Long.parseLong(msStr) * 10;
                    else if (msStr.length() == 3) ms = Long.parseLong(msStr);
                    else ms = Long.parseLong(msStr.substring(0, 3));
                }
                return (min * 60 + sec) * 1000 + ms;
            } catch (Exception ignored) {}
        }

        return -1;
    }

    private static void sendPartyMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            var player = mc.player;
            if (player != null && player.connection != null) {
                player.connection.sendCommand("pc " + message);
            }
        });
    }
}
