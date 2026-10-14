package com.wish.client.features;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrecursorCityLocator {

    public enum DetectionConfidence {
        MANUAL(100),
        EXACT_ALTAR(90),
        SCOREBOARD(80),
        STRUCTURE_SIGNATURE(60),
        CHAT_CALLOUT(40);

        public final int weight;
        DetectionConfidence(int weight) {
            this.weight = weight;
        }
    }

    private static BlockPos foundCityPos = null;
    private static DetectionConfidence currentConfidence = null;
    private static String detectionMethod = "";
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel = null;
    private static long lastChunkScanTime = 0;
    private static int bestStructureCount = 0;
    private static final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();

    // Chat coordinate regex (matches e.g. "city 650 82 720", "city at 650 720", "pc: 650 80 720", "precursor city 680 750")
    private static final Pattern CHAT_COORD_PATTERN = Pattern.compile(
            "(?i)(?:\\bcity\\b|precursor|\\bpc\\b|lost\\s+city)[^0-9]{0,15}?([5-8][0-9]{2})[\\s,]+(?:([3-9][0-9]|1[0-5][0-9])[\\s,]+)?([5-8][0-9]{2})"
    );

    public static boolean hasFoundCity() {
        return foundCityPos != null;
    }

    public static boolean hasExactAltar() {
        return foundCityPos != null && currentConfidence == DetectionConfidence.EXACT_ALTAR;
    }

    public static BlockPos getCityPos() {
        return foundCityPos;
    }

    public static String getDetectionMethod() {
        return detectionMethod;
    }

    public static DetectionConfidence getConfidence() {
        return currentConfidence;
    }

    public static void reset() {
        foundCityPos = null;
        currentConfidence = null;
        detectionMethod = "";
        bestStructureCount = 0;
        scannedChunks.clear();
    }

    public static boolean isInPrecursorBounds(int x, int z) {
        // Precursor Remnants quadrant is X: 513..825 and Z: 513..825, strictly outside the Crystal Nucleus
        return x >= 513 && x <= 825 && z >= 513 && z <= 825 && !isInNucleus(x, z);
    }

    public static boolean isInNucleus(int x, int z) {
        // Crystal Nucleus occupies the central dome: ~455 to ~566 on X and Z
        return x >= 455 && x <= 566 && z >= 455 && z <= 566;
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || !ModConfig.INSTANCE.enablePrecursorCityScanner) {
            if (foundCityPos != null) reset();
            return;
        }
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        // Auto-reset when switching worlds or lobbies
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }

        // Immediately clear any false waypoint that was placed inside the Crystal Nucleus
        if (foundCityPos != null && (!isInPrecursorBounds(foundCityPos.getX(), foundCityPos.getZ()) || isInNucleus(foundCityPos.getX(), foundCityPos.getZ()))) {
            reset();
        }

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        boolean inChCoords = (px >= 190 && px <= 860 && pz >= 190 && pz <= 860);
        if (!SkyblockDetector.isInSkyblock || (!SkyblockDetector.isInCrystalHollows && !inChCoords)) {
            if (foundCityPos != null) {
                reset();
            }
            return;
        }

        // 1. Scoreboard Zone Check: If standing in Lost Precursor City
        if (SkyblockDetector.isInPrecursorCity) {
            if (foundCityPos == null || currentConfidence == null || currentConfidence.weight < DetectionConfidence.SCOREBOARD.weight) {
                setCityPos(mc.player.blockPosition(), DetectionConfidence.SCOREBOARD, "Scoreboard: Precursor City");
            }
        }

        // 2. Scan Loaded Entities for Exact Altar: Professor Robot NPC and Altar Pedestals
        Iterable<Entity> allEntities = null;
        if (mc.level instanceof com.wish.client.mixin.ClientLevelAccessor cla) {
            var getter = cla.wish$getEntities();
            if (getter != null) {
                allEntities = getter.getAll();
            }
        }
        if (allEntities == null) {
            allEntities = mc.level.entitiesForRendering();
        }

        for (Entity entity : allEntities) {
            if (entity == null || entity == mc.player) continue;

            BlockPos entityPos = entity.blockPosition();
            // MUST be located out in the Precursor Remnants biome and NOT in the Nucleus!
            if (!isInPrecursorBounds(entityPos.getX(), entityPos.getZ()) || isInNucleus(entityPos.getX(), entityPos.getZ())) {
                continue;
            }

            String rawName = null;
            if (entity.hasCustomName() && entity.getCustomName() != null) {
                rawName = entity.getCustomName().getString();
            } else if (entity.getDisplayName() != null) {
                rawName = entity.getDisplayName().getString();
            } else if (entity.getName() != null) {
                rawName = entity.getName().getString();
            }

            if (rawName == null || rawName.isEmpty()) continue;
            String clean = SkyblockDetector.stripColorCodes(rawName).toLowerCase().trim();

            // Ignore Crystal Nucleus placement instructions
            if (clean.contains("place it here") || clean.contains("find this crystal")) {
                continue;
            }

            // Exclusively check for the Professor Robot NPC, Automaton Prime, or the 6 Precursor Altar pedestals
            // (Strictly avoid generic "relic pedestal" or "core pedestal" from unrelated ruins!)
            if (clean.contains("professor robot")
                    || clean.contains("automaton prime")
                    || clean.contains("place control switch") || clean.contains("place ftx")
                    || clean.contains("place robotron reflector") || clean.contains("place electron")
                    || clean.contains("place synthetic heart") || clean.contains("place superlite")) {
                setCityPos(entityPos, DetectionConfidence.EXACT_ALTAR, "Altar: " + clean);
                return;
            }
        }

        // 3. Scan Loaded Chunks for Structure Signature (high range up to render distance: 150-250m)
        long now = System.currentTimeMillis();
        if (now - lastChunkScanTime >= 400 && !hasExactAltar()) {
            lastChunkScanTime = now;
            scanLoadedChunks(mc);
        }
    }

    private static void scanLoadedChunks(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int rd = mc.options.renderDistance().get();

        int minCX = Math.max(32, playerChunkX - rd);
        int maxCX = Math.min(51, playerChunkX + rd);
        int minCZ = Math.max(32, playerChunkZ - rd);
        int maxCZ = Math.min(51, playerChunkZ + rd);

        if (minCX > maxCX || minCZ > maxCZ) return;

        var chunkSource = mc.level.getChunkSource();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (hasExactAltar()) return;
                // Skip chunks entirely within Crystal Nucleus (chunks 28..35)
                if (cx <= 35 && cz <= 35) continue;

                LevelChunk chunk = chunkSource.getChunk(cx, cz, false);
                if (chunk != null) {
                    checkChunkForCity(chunk);
                }
            }
        }
    }

    public static boolean checkChunkForCity(LevelChunk chunk) {
        if (chunk == null) return false;
        var pos = chunk.getPos();
        int cx = pos.x();
        int cz = pos.z();

        // Quadrant check: Precursor Remnants is chunks 32..51 on X and Z (blocks 512..831)
        if (cx < 32 || cx > 51 || cz < 32 || cz > 51) {
            return false;
        }

        // Must not be in the Crystal Nucleus (chunks 28..35 on BOTH X and Z)
        if (cx <= 35 && cz <= 35) {
            return false;
        }

        long chunkKey = pos.pack();
        if (scannedChunks.contains(chunkKey)) {
            return false;
        }
        scannedChunks.add(chunkKey);

        LevelChunkSection[] sections = chunk.getSections();
        if (sections == null || sections.length == 0) return false;

        int stoneBrickCount = 0;
        int deepslateCount = 0;
        int chiseledDeepslateCount = 0;
        int ironBarsCount = 0;
        int ironBlockCount = 0;

        long sumX = 0, sumY = 0, sumZ = 0;
        int totalKeyBlocks = 0;
        int minY = 256;
        int maxY = -64;

        for (int secIdx = 0; secIdx < sections.length; secIdx++) {
            LevelChunkSection section = sections[secIdx];
            if (section == null || section.hasOnlyAir()) continue;

            int secY = chunk.getSectionYFromSectionIndex(secIdx);
            int startY = secY * 16;
            // Precursor City structures are between Y: 30 and Y: 150
            if (startY < 20 || startY > 155) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = startY + y;
                if (worldY < 30 || worldY > 150) continue;

                for (int x = 0; x < 16; x++) {
                    int worldX = cx * 16 + x;
                    if (worldX < 513 || worldX > 825) continue;

                    for (int z = 0; z < 16; z++) {
                        int worldZ = cz * 16 + z;
                        if (worldZ < 513 || worldZ > 825) continue;
                        if (isInNucleus(worldX, worldZ)) continue;

                        BlockState state = section.getBlockState(x, y, z);
                        var block = state.getBlock();

                        boolean isStructure = false;
                        if (block == Blocks.STONE_BRICKS
                                || block == Blocks.CRACKED_STONE_BRICKS
                                || block == Blocks.MOSSY_STONE_BRICKS
                                || block == Blocks.CHISELED_STONE_BRICKS
                                || block == Blocks.STONE_BRICK_STAIRS
                                || block == Blocks.STONE_BRICK_SLAB
                                || block == Blocks.STONE_BRICK_WALL) {
                            stoneBrickCount++;
                            isStructure = true;
                        } else if (block == Blocks.POLISHED_DEEPSLATE
                                || block == Blocks.POLISHED_DEEPSLATE_STAIRS
                                || block == Blocks.POLISHED_DEEPSLATE_SLAB
                                || block == Blocks.POLISHED_DEEPSLATE_WALL
                                || block == Blocks.DEEPSLATE_BRICKS
                                || block == Blocks.CRACKED_DEEPSLATE_BRICKS
                                || block == Blocks.DEEPSLATE_TILES) {
                            deepslateCount++;
                            isStructure = true;
                        } else if (block == Blocks.CHISELED_DEEPSLATE) {
                            chiseledDeepslateCount++;
                            deepslateCount++;
                            isStructure = true;
                        } else if (block == Blocks.IRON_BARS) {
                            ironBarsCount++;
                            isStructure = true;
                        } else if (block == Blocks.IRON_BLOCK) {
                            ironBlockCount++;
                            isStructure = true;
                        }

                        if (isStructure) {
                            sumX += worldX;
                            sumY += worldY;
                            sumZ += worldZ;
                            totalKeyBlocks++;
                            if (worldY < minY) minY = worldY;
                            if (worldY > maxY) maxY = worldY;
                        }
                    }
                }
            }
        }

        int heightSpan = (maxY >= minY) ? (maxY - minY) : 0;
        boolean isCity = false;

        // Condition 1: High density of city structure blocks (45+ blocks) spanning multiple Y levels
        if (totalKeyBlocks >= 45 && heightSpan >= 8) {
            isCity = true;
        } else if (totalKeyBlocks >= 70) {
            isCity = true;
        } else if (chiseledDeepslateCount >= 2 && totalKeyBlocks >= 30) {
            // Precursor Altar pedestals are built of Chiseled Deepslate
            isCity = true;
        } else if (ironBlockCount >= 8 && totalKeyBlocks >= 30) {
            // Automaton Prime / Altar room
            isCity = true;
        }

        if (isCity) {
            BlockPos foundPos = new BlockPos((int) (sumX / totalKeyBlocks), (int) (sumY / totalKeyBlocks), (int) (sumZ / totalKeyBlocks));
            // Only update if this chunk has higher density or if we don't have a structure waypoint yet
            if (totalKeyBlocks > bestStructureCount || currentConfidence == null || currentConfidence.weight < DetectionConfidence.STRUCTURE_SIGNATURE.weight) {
                bestStructureCount = totalKeyBlocks;
                setCityPos(foundPos, DetectionConfidence.STRUCTURE_SIGNATURE, "City Structure (" + totalKeyBlocks + " blocks)");
            }
            return true;
        }

        return false;
    }

    public static void onChatMessage(String rawMessage) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || rawMessage == null) return;
        String clean = SkyblockDetector.stripColorCodes(rawMessage).replace('\u00A0', ' ').trim();
        if (clean.isEmpty() || clean.contains("[Wish]") || clean.contains("[Automaton]") || clean.contains("[Precursor City]")) return;

        Matcher m = CHAT_COORD_PATTERN.matcher(clean);
        if (m.find()) {
            try {
                int x = Integer.parseInt(m.group(1));
                int y = 80;
                if (m.group(2) != null) {
                    y = Integer.parseInt(m.group(2));
                }
                int z = Integer.parseInt(m.group(3));

                if (isInPrecursorBounds(x, z)) {
                    BlockPos pos = new BlockPos(x, y, z);
                    setCityPos(pos, DetectionConfidence.CHAT_CALLOUT, "Chat Callout");
                }
            } catch (Exception ignored) {}
        }
    }

    public static boolean setCityPos(BlockPos pos, DetectionConfidence confidence, String method) {
        if (pos == null) return false;
        if (!isInPrecursorBounds(pos.getX(), pos.getZ()) || isInNucleus(pos.getX(), pos.getZ())) {
            return false;
        }

        // Never downgrade an already discovered position to a lower confidence detection
        if (foundCityPos != null && currentConfidence != null && currentConfidence.weight > confidence.weight) {
            return false;
        }

        foundCityPos = pos;
        currentConfidence = confidence;
        detectionMethod = method;
        return true;
    }

    public static int getDistanceToPlayer(Player player) {
        if (foundCityPos == null || player == null) return 0;
        double dx = (foundCityPos.getX() + 0.5) - player.getX();
        double dy = (foundCityPos.getY() + 0.5) - player.getY();
        double dz = (foundCityPos.getZ() + 0.5) - player.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public static void renderWaypoint(GuiGraphicsExtractor guiGraphics, Minecraft mc, int screenW, int screenH) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || mc.player == null || mc.level == null) return;
        if (foundCityPos == null) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        boolean inChCoords = (px >= 190 && px <= 860 && pz >= 190 && pz <= 860);
        if (!SkyblockDetector.isInCrystalHollows && !inChCoords) return;

        BlockPos targetPos = foundCityPos;
        double dx = (targetPos.getX() + 0.5) - mc.player.getX();
        double dy = (targetPos.getY() + 0.5) - mc.player.getY();
        double dz = (targetPos.getZ() + 0.5) - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));

        var camera = mc.gameRenderer.mainCamera();
        var camPos = camera.position();

        double cdx = (targetPos.getX() + 0.5) - camPos.x;
        double cdy = (targetPos.getY() + 1.5) - camPos.y;
        double cdz = (targetPos.getZ() + 0.5) - camPos.z;

        org.joml.Quaternionf invRot = new org.joml.Quaternionf(camera.rotation()).conjugate();
        org.joml.Vector3f camSpace = invRot.transform(new org.joml.Vector3f((float) cdx, (float) cdy, (float) cdz));

        float depth = -camSpace.z;
        if (depth <= 0.5f) {
            return;
        }

        double fovRad = Math.toRadians(mc.options.fov().get());
        double halfScreenH = screenH / 2.0;
        double halfScreenW = screenW / 2.0;
        double focalLength = halfScreenH / Math.tan(fovRad / 2.0);

        double projX = halfScreenW + (camSpace.x / depth) * focalLength;
        double projY = halfScreenH - (camSpace.y / depth) * focalLength;

        if (projX < 0 || projX > screenW || projY < 0 || projY > screenH) {
            return;
        }

        // Pure transparent text: NO background box, NO border, NO effect when approaching
        String label = "§b✦ Precursor City §7(" + dist + "m)";
        int textW = mc.font.width(label);
        int drawX = (int) projX - textW / 2;
        int drawY = (int) projY - 4;

        guiGraphics.text(mc.font, label, drawX, drawY, 0xFFFFFFFF, true);
    }
}
