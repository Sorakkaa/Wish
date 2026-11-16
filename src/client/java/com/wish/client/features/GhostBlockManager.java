package com.wish.client.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GhostBlockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-GhostBlocks");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<Long, String> ghostBlocks = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, net.minecraft.world.level.block.Block> BLOCK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final net.minecraft.world.level.block.state.BlockState CACHED_WARPED_FENCE_STATE = net.minecraft.world.level.block.Blocks.WARPED_FENCE.defaultBlockState();
    public static volatile boolean isGhostBlocksEnabled = true;
    public static volatile boolean isGlassGhostBlocksEnabled = true;

    public static void init() {
        isGhostBlocksEnabled = com.wish.client.config.ModConfig.INSTANCE.enableCustomBlocksF7M7;
        isGlassGhostBlocksEnabled = com.wish.client.config.ModConfig.INSTANCE.enableGlassBlocksF7M7;
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
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load ghost blocks from assets", e);
            if (ghostBlocks == null) ghostBlocks = new java.util.concurrent.ConcurrentHashMap<>();
        }
    }

    public static net.minecraft.world.level.block.state.BlockState resolveVisualBlockState(
            net.minecraft.world.level.block.state.BlockState originalState, long posKey) {
        if (originalState == null || originalState.isAir()) {
            return null;
        }
        if (originalState.getBlock() == net.minecraft.world.level.block.Blocks.NETHER_BRICK_FENCE) {
            return CACHED_WARPED_FENCE_STATE;
        }
        String ghost = getGhostBlockMaterial(posKey);
        if (ghost == null) {
            return null;
        }
        if (ghost.contains("glass") && !isGlassGhostBlocksEnabled) {
            return null;
        }
        if ("minecraft:air".equals(ghost)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return getGhostBlockVisualState(originalState, ghost);
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
