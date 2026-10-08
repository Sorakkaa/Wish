package com.wish.client.features;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerScoreEntry;
import com.wish.client.config.ModConfig;

public class SkyblockDetector {

    public static boolean isInSkyblock = false;
    public static boolean isInDungeon = false;
    public static boolean isInF7OrM7 = false;
    private static int tickCounter = 0;

    public static void onClientTick(Minecraft mc) {
        tickCounter++;
        // On vérifie seulement toutes les 2 ticks (0.1 seconde) pour ne pas lag
        if (tickCounter >= 2) {
            tickCounter = 0;
            updateStatus(mc);
        }
    }

    private static final java.util.regex.Pattern F7_M7_PATTERN = java.util.regex.Pattern.compile(".*\\b(f7|m7)\\b.*");

    public static String stripColorCodes(String input) {
        if (input == null || input.isEmpty() || input.indexOf('§') == -1) return input != null ? input : "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void updateStatus(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            isInSkyblock = false;
            isInDungeon = false;
            isInF7OrM7 = false;
            return;
        }

        boolean newSkyblock = false;
        boolean newDungeon = false;
        boolean newF7M7 = false;
        
        if (ModConfig.INSTANCE.alwaysInM7F7) {
            newSkyblock = true;
            newDungeon = true;
            newF7M7 = true;
        } else {
            Scoreboard scoreboard = mc.level.getScoreboard();
            if (scoreboard != null) {
                Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
                if (objective != null) {
                    String title = objective.getDisplayName().getString();
                    String cleanTitle = stripColorCodes(title);
                    if (cleanTitle.contains("SKYBLOCK") || cleanTitle.contains("SKIBLOCK")) {
                        newSkyblock = true;
                        boolean foundDungeonTimer = false;
                        boolean foundFloorLine = false;

                        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                            String owner = entry.owner();
                            String displayText = entry.display() != null ? entry.display().getString() : "";
                            
                            PlayerTeam team = scoreboard.getPlayersTeam(owner);
                            String prefix = team != null ? team.getPlayerPrefix().getString() : "";
                            String suffix = team != null ? team.getPlayerSuffix().getString() : "";
                            
                            String fullLine = prefix + owner + suffix + displayText;
                            String cleanLine = stripColorCodes(fullLine);

                            if (com.wish.client.features.DungeonLagTracker.checkScoreboardLine(cleanLine)) {
                                foundDungeonTimer = true;
                            }

                            String lower = cleanLine.toLowerCase();

                            // Dungeon detection (all floors: The Catacombs, Cleared:, Crypts, Keys, Secrets, Revives, etc.)
                            if (lower.contains("catacombs") ||
                                lower.contains("the catacombs") ||
                                lower.contains("dungeon cleared") ||
                                lower.contains("cleared:") ||
                                lower.contains("crypts:") ||
                                lower.contains("secrets found:") ||
                                lower.contains("alive dragons") ||
                                lower.contains("wither door") ||
                                lower.contains("blood door")) {
                                newDungeon = true;
                            }

                            // Specific F7/M7 detection:
                            // "The Catacombs (F7)" / "(M7)" / "Floor VII" / "Master Mode Floor VII" / "M7" / "F7"
                            if (lower.contains("(f7)") || lower.contains("(m7)") ||
                                lower.contains("floor vii") ||
                                lower.contains("floor 7") ||
                                F7_M7_PATTERN.matcher(lower).matches() ||
                                lower.contains("alive dragons")) {
                                newF7M7 = true;
                            }
                        }

                        // An F7/M7 floor is definitely inside a dungeon
                        if (newF7M7) {
                            newDungeon = true;
                        }

                        // Safety check: if we are not in dungeon, we cannot be in F7/M7
                        if (!newDungeon) {
                            newF7M7 = false;
                        }
                    }
                }
            }
        }

        isInSkyblock = newSkyblock;
        isInDungeon = newDungeon;

        if (!newDungeon) {
            com.wish.client.features.DungeonLagTracker.forceEndRun();
        }
        
        if (newF7M7 != isInF7OrM7) {
            isInF7OrM7 = newF7M7;
            if (mc.levelExtractor != null) {
                mc.levelExtractor.allChanged();
            }
        }
    }
}
