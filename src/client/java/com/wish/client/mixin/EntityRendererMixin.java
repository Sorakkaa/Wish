package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    
    @Inject(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void onGetNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        if (original != null) {
            Component colored = NameColorManager.colorizeText(original);
            if (colored != null) {
                cir.setReturnValue(colored);
            }
        }
    }
}
