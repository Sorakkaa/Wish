package com.wish.client.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GhostBlockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-GhostBlocks");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    
    public static Map<Long, String> ghostBlocks = new java.util.concurrent.ConcurrentHashMap<>();
    public static Map<Long, Map<Long, String>> ghostBlocksByChunk = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, net.minecraft.world.level.block.Block> BLOCK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final net.minecraft.world.level.block.state.BlockState CACHED_WARPED_FENCE_STATE = net.minecraft.world.level.block.Blocks.WARPED_FENCE.defaultBlockState();
    public static boolean isGhostBlocksEnabled = true;
    public static boolean isGlassGhostBlocksEnabled = true;

    public static void init() {
        load();
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
            LOGGER.error("Failed to load ghost blocks from assets", e);
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
}
