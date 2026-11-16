package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Optional;

@Mixin(value = GuiGraphicsExtractor.class, priority = 2000)
public class GuiGraphicsExtractorMixin {

    @ModifyVariable(
        method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$colorizeGuiComponent(Component text) {
        if (text == null) return null;
        return NameColorManager.colorizeGuiText(text);
    }

    @ModifyVariable(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$colorizeCenteredGuiComponent(Component text) {
        if (text == null) return null;
        return NameColorManager.colorizeGuiText(text);
    }

    @ModifyVariable(
        method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String wish$replaceGuiString(String text) {
        return WordReplacerManager.replacePlainString(text);
    }

    @ModifyVariable(
        method = "textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$replaceBackdropComponent(Component text) {
        return WordReplacerManager.replaceComponent(text);
    }

    @ModifyVariable(
        method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$replaceSingleTooltipComponent(Component text) {
        return WordReplacerManager.replaceComponent(text);
    }

    @ModifyVariable(
        method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private List<Component> wish$replaceTooltipComponents(List<Component> lines) {
        return WordReplacerManager.replaceComponentList(lines);
    }

    @ModifyVariable(
        method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private List<Component> wish$replaceGenericTooltipComponents(List<Component> lines) {
        return WordReplacerManager.replaceComponentList(lines);
    }
}
