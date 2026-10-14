package com.wish.client.mixin;

import com.wish.client.features.AutomatonHudOverlay;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractContainerScreen.class, AbstractRecipeBookScreen.class})
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean focused, CallbackInfoReturnable<Boolean> cir) {
        if (event != null && AutomatonHudOverlay.mouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }
}

