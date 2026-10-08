package com.wish.client.mixin;

import com.wish.client.features.GhostBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public class SodiumLevelSliceMixin {

    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (GhostBlockManager.isGhostBlocksEnabled && com.wish.client.features.SkyblockDetector.isInF7OrM7) {
            BlockState originalState = cir.getReturnValue();
            if (originalState != null && !originalState.isAir()) {
                if (originalState.getBlock() == net.minecraft.world.level.block.Blocks.NETHER_BRICK_FENCE) {
                    cir.setReturnValue(GhostBlockManager.CACHED_WARPED_FENCE_STATE);
                    return;
                }
                
                long posKey = BlockPos.asLong(x, y, z);
                String ghost = GhostBlockManager.getGhostBlockMaterial(posKey);
                if (ghost != null) {
                    boolean isGlass = ghost.contains("glass");
                    if (isGlass && !GhostBlockManager.isGlassGhostBlocksEnabled) {
                        return;
                    }
                    if ("minecraft:air".equals(ghost)) {
                        cir.setReturnValue(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        return;
                    }
                    cir.setReturnValue(GhostBlockManager.getGhostBlockVisualState(originalState, ghost));
                }
            }
        }
    }
}
