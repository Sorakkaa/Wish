package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = GuiGraphicsExtractor.class, priority = 2000)
public class GuiGraphicsExtractorMixin {

    @ModifyVariable(
        method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$colorizeGuiComponent(Component text) {
        if (text == null) return null;
        return NameColorManager.colorizeText(text, false, NameColorManager.isTabOverlayActive(), NameColorManager.isRenderingScoreboard);
    }

    @ModifyVariable(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$colorizeCenteredGuiComponent(Component text) {
        if (text == null) return null;
        return NameColorManager.colorizeText(text, false, NameColorManager.isTabOverlayActive(), NameColorManager.isRenderingScoreboard);
    }
}

