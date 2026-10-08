package com.wish.client.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class GhostBlockManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // We store positions as Long for easy JSON serialization, and the block ID as the value
    public static Map<Long, String> ghostBlocks = new java.util.concurrent.ConcurrentHashMap<>();
    public static Map<Long, Map<Long, String>> ghostBlocksByChunk = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, net.minecraft.world.level.block.Block> BLOCK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final net.minecraft.world.level.block.state.BlockState CACHED_WARPED_FENCE_STATE = net.minecraft.world.level.block.Blocks.WARPED_FENCE.defaultBlockState();
    public static boolean isGhostBlocksEnabled = true;
    public static boolean isGlassGhostBlocksEnabled = true;
    public static String currentGhostMaterial = "minecraft:moss_block";
    public static BlockPos pos1 = null;
    public static BlockPos pos2 = null;
    
    public static KeyMapping keyPos1;
    public static KeyMapping keyPos2;
    public static KeyMapping keySoloHit;

    public static final net.minecraft.client.KeyMapping.Category GHOST_CATEGORY = net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.tryParse("wish:ghostblocks"));

    public static void init() {
        load();

        keyPos1 = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.wish.ghostpos1",
            -1, // UNBOUND
            GHOST_CATEGORY
        ));
        keyPos2 = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.wish.ghostpos2",
            -1, // UNBOUND
            GHOST_CATEGORY
        ));
        keySoloHit = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.wish.ghostsolo",
            -1, // UNBOUND
            GHOST_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyPos1.consumeClick()) {
                if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    setPos1(hit.getBlockPos());
                }
            }
            while (keyPos2.consumeClick()) {
                if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    setPos2(hit.getBlockPos());
                }
            }
            while (keySoloHit.consumeClick()) {
                if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    setGhostBlockAt(hit.getBlockPos());
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("ghostfill")
                .executes(context -> {
                    fillZone();
                    return 1;
                })
                .then(ClientCommands.argument("block", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(context -> {
                        String block = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "block");
                        if (!block.contains(":")) {
                            block = "minecraft:" + block;
                        }
                        currentGhostMaterial = block;
                        fillZone();
                        return 1;
                    })
                )
            );
            dispatcher.register(ClientCommands.literal("ghostclear")
                .executes(context -> {
                    clearZone();
                    return 1;
                })
            );
        });
    }

    private static void sendMsg(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
        }
    }

    public static void load() {
        try {
            String json = null;
            try (java.io.InputStream in = GhostBlockManager.class.getResourceAsStream("/assets/wish/ghostblocks.json")) {
                if (in != null) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            if (json != null && !json.isEmpty()) {
                java.lang.reflect.Type type = new TypeToken<Map<Long, String>>(){}.getType();
                Map<Long, String> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    ghostBlocks = new java.util.concurrent.ConcurrentHashMap<>(loaded);
                    rebuildChunkMap();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (ghostBlocks == null) ghostBlocks = new java.util.concurrent.ConcurrentHashMap<>();
        }
    }

    private static void rebuildChunkMap() {
        ghostBlocksByChunk.clear();
        for (Map.Entry<Long, String> entry : ghostBlocks.entrySet()) {
            if ("removed".equals(entry.getValue())) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            long chunkPos = (((long)(pos.getX() >> 4)) << 32) | ((long)(pos.getZ() >> 4) & 0xFFFFFFFFL);
            ghostBlocksByChunk.computeIfAbsent(chunkPos, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(entry.getKey(), entry.getValue());
        }
    }

    public static void save() {
        // Disabled saving to bake ghostblocks into the mod
    }

    public static void setCurrentGhostMaterial(String material) {
        currentGhostMaterial = material;
    }

    public static void changeAllGhostBlocks(String newMaterial) {
        String oldMaterial = currentGhostMaterial;
        currentGhostMaterial = newMaterial;
        for (Map.Entry<Long, String> entry : ghostBlocks.entrySet()) {
            if (entry.getValue().equals(oldMaterial)) {
                entry.setValue(newMaterial);
            }
        }
        save();
        Minecraft client = Minecraft.getInstance();
        if (isGhostBlocksEnabled && client.levelExtractor != null) {
            client.levelExtractor.allChanged();
        }
    }

    public static void createBackup() {
        // Sauvegarde et chargement déjà gérés
    }

    public static void restoreBackup() {
        // Sauvegarde et chargement déjà gérés
    }

    public static void setPos1(BlockPos pos) {
        pos1 = pos;
        sendMsg(Minecraft.getInstance(), "§a[GhostBlocks] Pos1 set to " + pos.toShortString());
    }

    public static void setPos2(BlockPos pos) {
        pos2 = pos;
        sendMsg(Minecraft.getInstance(), "§a[GhostBlocks] Pos2 set to " + pos.toShortString());
    }

    public static void setGhostBlockAt(BlockPos pos) {
        if (ghostBlocks.containsKey(pos.asLong()) && !ghostBlocks.get(pos.asLong()).equals("removed")) {
            removeGhostBlockAt(pos);
            return;
        }
        ghostBlocks.put(pos.asLong(), currentGhostMaterial);
        rebuildChunkMap();
        save();
        sendMsg(Minecraft.getInstance(), "§a[GhostBlocks] Placed " + currentGhostMaterial + " at " + pos.toShortString());
        if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
    }

    public static String getGhostBlockMaterial(long posKey) {
        return ghostBlocks.get(posKey);
    }

    public static net.minecraft.world.level.block.state.BlockState getGhostBlockVisualState(net.minecraft.world.level.block.state.BlockState original, String ghostMaterial) {
        if ("minecraft:air".equals(ghostMaterial)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        
        net.minecraft.world.level.block.Block baseGhostBlock = BLOCK_CACHE.computeIfAbsent(ghostMaterial, mat -> {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(mat);
            if (id == null) return null;
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).map(ref -> ref.value()).orElse(null);
        });
        if (baseGhostBlock == null) return original;
        
        if (original.getBlock() instanceof net.minecraft.world.level.block.StairBlock && baseGhostBlock instanceof net.minecraft.world.level.block.StairBlock) {
            try {
                return baseGhostBlock.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, original.getValue(net.minecraft.world.level.block.StairBlock.FACING))
                    .setValue(net.minecraft.world.level.block.StairBlock.HALF, original.getValue(net.minecraft.world.level.block.StairBlock.HALF))
                    .setValue(net.minecraft.world.level.block.StairBlock.SHAPE, original.getValue(net.minecraft.world.level.block.StairBlock.SHAPE))
                    .setValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED, original.getValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED));
            } catch (Exception e) { return baseGhostBlock.defaultBlockState(); }
        }
        
        if (original.getBlock() instanceof net.minecraft.world.level.block.SlabBlock && baseGhostBlock instanceof net.minecraft.world.level.block.SlabBlock) {
            try {
                return baseGhostBlock.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, original.getValue(net.minecraft.world.level.block.SlabBlock.TYPE))
                    .setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, original.getValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED));
            } catch (Exception e) { return baseGhostBlock.defaultBlockState(); }
        }

        if (original.getBlock() instanceof net.minecraft.world.level.block.FenceBlock && baseGhostBlock instanceof net.minecraft.world.level.block.FenceBlock) {
            try {
                return baseGhostBlock.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceBlock.NORTH, original.getValue(net.minecraft.world.level.block.FenceBlock.NORTH))
                    .setValue(net.minecraft.world.level.block.FenceBlock.SOUTH, original.getValue(net.minecraft.world.level.block.FenceBlock.SOUTH))
                    .setValue(net.minecraft.world.level.block.FenceBlock.EAST, original.getValue(net.minecraft.world.level.block.FenceBlock.EAST))
                    .setValue(net.minecraft.world.level.block.FenceBlock.WEST, original.getValue(net.minecraft.world.level.block.FenceBlock.WEST))
                    .setValue(net.minecraft.world.level.block.FenceBlock.WATERLOGGED, original.getValue(net.minecraft.world.level.block.FenceBlock.WATERLOGGED));
            } catch (Exception e) { return baseGhostBlock.defaultBlockState(); }
        }

        return baseGhostBlock.defaultBlockState();
    }

    public static void removeGhostBlockAt(BlockPos pos) {
        if (ghostBlocks.containsKey(pos.asLong()) && !ghostBlocks.get(pos.asLong()).equals("removed")) {
            ghostBlocks.put(pos.asLong(), "removed");
            rebuildChunkMap();
            save();
            sendMsg(Minecraft.getInstance(), "§c[GhostBlocks] Removed ghost block at " + pos.toShortString());
            if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
        } else {
            sendMsg(Minecraft.getInstance(), "§e[GhostBlocks] No ghost block found at " + pos.toShortString());
        }
    }

    public static void fillZone() {
        if (pos1 == null || pos2 == null) {
            sendMsg(Minecraft.getInstance(), "§c[GhostBlocks] Pos1 or Pos2 is not set!");
            return;
        }
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    ghostBlocks.put(BlockPos.asLong(x, y, z), currentGhostMaterial);
                    count++;
                }
            }
        }
        rebuildChunkMap();
        save();
        sendMsg(Minecraft.getInstance(), "§a[GhostBlocks] Filled " + count + " blocks with " + currentGhostMaterial);
        if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
    }

    public static void clearZone() {
        if (pos1 == null || pos2 == null) {
            sendMsg(Minecraft.getInstance(), "§c[GhostBlocks] Pos1 or Pos2 is not set!");
            return;
        }
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long key = BlockPos.asLong(x, y, z);
                    if (ghostBlocks.containsKey(key) && !ghostBlocks.get(key).equals("removed")) {
                        ghostBlocks.put(key, "removed");
                        count++;
                    }
                }
            }
        }
        rebuildChunkMap();
        save();
        sendMsg(Minecraft.getInstance(), "§c[GhostBlocks] Cleared " + count + " ghost blocks from zone.");
        if (Minecraft.getInstance().levelExtractor != null) Minecraft.getInstance().levelExtractor.allChanged();
    }
}
