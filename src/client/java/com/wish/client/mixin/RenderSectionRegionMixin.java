package com.wish.client.mixin;

import com.wish.client.features.GhostBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.chunk.RenderSectionRegion.class)
public class RenderSectionRegionMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (GhostBlockManager.isGhostBlocksEnabled && com.wish.client.features.SkyblockDetector.isInF7OrM7) {
            BlockState originalState = cir.getReturnValue();
            if (originalState != null && !originalState.isAir()) {
                if (originalState.getBlock() == net.minecraft.world.level.block.Blocks.NETHER_BRICK_FENCE) {
                    cir.setReturnValue(GhostBlockManager.CACHED_WARPED_FENCE_STATE);
                    return;
                }
                
                String ghost = GhostBlockManager.getGhostBlockMaterial(pos.asLong());
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
