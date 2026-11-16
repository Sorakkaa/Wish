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
    private void wish$onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (GhostBlockManager.isGhostBlocksEnabled && com.wish.client.features.SkyblockDetector.isInF7OrM7) {
            BlockState resolved = GhostBlockManager.resolveVisualBlockState(cir.getReturnValue(), pos.asLong());
            if (resolved != null) {
                cir.setReturnValue(resolved);
            }
        }
    }
}
