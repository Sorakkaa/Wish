package com.wish.client.mixin;

import com.wish.client.util.ISizeableState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void wish$applySmolPeopleTransform(T state, CallbackInfo ci) {
        if (state instanceof ISizeableState sizeable && sizeable.wish$isSmolPeople()) {
            HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
            // PlayerRendererMixin already scales the PoseStack by 0.5x at the feet origin;
            // scaling head (and its hat child) by 1.5x gives 0.75x world head proportions without +12.0f limb offsets.
            self.head.xScale *= 1.5f;
            self.head.yScale *= 1.5f;
            self.head.zScale *= 1.5f;
        }
    }
}

