package com.wish.client.mixin;

import com.wish.client.features.AutomatonHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void onExtractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null) {
                int screenW = mc.getWindow().getGuiScaledWidth();
                int screenH = mc.getWindow().getGuiScaledHeight();
                AutomatonHudOverlay.render(g, mc, screenW, screenH, mx, my, true);
            }
        }
    }
}
