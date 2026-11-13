package com.wish.client.mixin;

import com.wish.client.features.CustomModelManager;
import com.wish.client.gui.WishModelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    @Unique
    private static ItemStack wish$resolveStack(ItemStack stack) {
        if (CustomModelManager.bypassReplacement) return stack;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.screen() instanceof WishModelScreen) {
            return stack;
        }
        return CustomModelManager.getReplacement(stack);
    }

    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true)
    private ItemStack wish$modifyItemLayersStack(ItemStack stack) {
        return wish$resolveStack(stack);
    }

    @ModifyVariable(method = "shouldPlaySwapAnimation", at = @At("HEAD"), argsOnly = true)
    private ItemStack wish$modifySwapAnimationStack(ItemStack stack) {
        return wish$resolveStack(stack);
    }

    @ModifyVariable(method = "swapAnimationScale", at = @At("HEAD"), argsOnly = true)
    private ItemStack wish$modifySwapScaleStack(ItemStack stack) {
        return wish$resolveStack(stack);
    }
}
