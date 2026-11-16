package com.wish.client.mixin;

import com.wish.client.features.CustomModelManager;
import com.wish.client.gui.WishModelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Unique
    private Component wish$resolveCustomHoverName() {
        if (CustomModelManager.bypassReplacement) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.screen() instanceof WishModelScreen) {
            return null;
        }
        ItemStack self = (ItemStack) (Object) this;
        return CustomModelManager.getCustomNameComponent(self);
    }

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void wish$onGetHoverName(CallbackInfoReturnable<Component> cir) {
        Component customName = wish$resolveCustomHoverName();
        if (customName != null) {
            cir.setReturnValue(customName);
        }
    }

    @Inject(method = "getStyledHoverName", at = @At("HEAD"), cancellable = true)
    private void wish$onGetStyledHoverName(CallbackInfoReturnable<Component> cir) {
        Component customName = wish$resolveCustomHoverName();
        if (customName != null) {
            cir.setReturnValue(customName);
        }
    }
}
