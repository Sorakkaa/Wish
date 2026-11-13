package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerTabOverlay.class, priority = 2000)
public class PlayerTabOverlayMixin {

    @org.spongepowered.asm.mixin.injection.Inject(method = "extractRenderState", at = @At("HEAD"))
    private void wish$onExtractRenderStateHead(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        NameColorManager.isRenderingTab = true;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "extractRenderState", at = @At("RETURN"))
    private void wish$onExtractRenderStateReturn(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        NameColorManager.isRenderingTab = false;
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void wish$onGetNameForDisplay(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        if (original != null) {
            Component colored = NameColorManager.colorizeText(original, false, true);
            if (colored != null) {
                cir.setReturnValue(colored);
            }
        }
    }
}
