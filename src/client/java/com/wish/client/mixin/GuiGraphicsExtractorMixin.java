package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(
        method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;"
        )
    )
    private void wish$beforeTextComponentInner(Font font, Component text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        WordReplacerManager.beginAlreadyReplaced();
    }

    @Inject(
        method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            shift = At.Shift.AFTER
        )
    )
    private void wish$afterTextComponentInner(Font font, Component text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        WordReplacerManager.endAlreadyReplaced();
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

    @Inject(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;"
        )
    )
    private void wish$beforeCenteredComponentInner(Font font, Component text, int x, int y, int color, CallbackInfo ci) {
        WordReplacerManager.beginAlreadyReplaced();
    }

    @Inject(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
            shift = At.Shift.AFTER
        )
    )
    private void wish$afterCenteredComponentInner(Font font, Component text, int x, int y, int color, CallbackInfo ci) {
        WordReplacerManager.endAlreadyReplaced();
    }

    @ModifyVariable(
        method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String wish$replaceGuiString(String text) {
        return WordReplacerManager.replacePlainString(text);
    }

    @Inject(
        method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"
        )
    )
    private void wish$beforeTextStringInner(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        WordReplacerManager.beginAlreadyReplaced();
    }

    @Inject(
        method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            shift = At.Shift.AFTER
        )
    )
    private void wish$afterTextStringInner(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        WordReplacerManager.endAlreadyReplaced();
    }

    @ModifyVariable(
        method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedCharSequence wish$replaceGuiCharSequence(FormattedCharSequence text) {
        return WordReplacerManager.replaceCharSequence(text);
    }

    @ModifyVariable(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String wish$replaceCenteredGuiString(String text) {
        return WordReplacerManager.replacePlainString(text);
    }

    @Inject(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"
        )
    )
    private void wish$beforeCenteredStringInner(Font font, String text, int x, int y, int color, CallbackInfo ci) {
        WordReplacerManager.beginAlreadyReplaced();
    }

    @Inject(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            shift = At.Shift.AFTER
        )
    )
    private void wish$afterCenteredStringInner(Font font, String text, int x, int y, int color, CallbackInfo ci) {
        WordReplacerManager.endAlreadyReplaced();
    }

    @ModifyVariable(
        method = "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedCharSequence wish$replaceCenteredCharSequence(FormattedCharSequence text) {
        return WordReplacerManager.replaceCharSequence(text);
    }

    @ModifyVariable(
        method = "textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$replaceBackdropComponent(Component text) {
        if (text == null) return null;
        return NameColorManager.colorizeGuiText(text);
    }

    @ModifyVariable(
        method = "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component wish$replaceDirectTooltipComponent(Component text) {
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
