package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Font.class, priority = 2000)
public class FontMixin {

    @Unique
    private static FormattedText wish$normalizeFormattedText(FormattedText text) {
        if (text instanceof Component component) {
            Component colored = NameColorManager.colorizeGuiText(component);
            return colored != null ? colored : component;
        }
        return text;
    }

    @ModifyVariable(
            method = "width(Ljava/lang/String;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private String wish$replaceWidthString(String text) {
        return WordReplacerManager.replacePlainString(text);
    }

    @ModifyVariable(
            method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private FormattedText wish$normalizeWidthFormattedText(FormattedText text) {
        return wish$normalizeFormattedText(text);
    }

    @ModifyVariable(
            method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private FormattedText wish$normalizeSplitFormattedText(FormattedText text) {
        return wish$normalizeFormattedText(text);
    }

    @ModifyVariable(
            method = "splitIgnoringLanguage(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private FormattedText wish$normalizeSplitIgnoringLanguageFormattedText(FormattedText text) {
        return wish$normalizeFormattedText(text);
    }

    @ModifyVariable(
            method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private FormattedCharSequence wish$replaceWidthCharSequence(FormattedCharSequence text) {
        return WordReplacerManager.replaceCharSequence(text);
    }

    @ModifyVariable(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private String wish$replacePrepareTextString(String text) {
        return WordReplacerManager.replacePlainString(text);
    }

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private FormattedCharSequence wish$replacePrepareTextCharSequence(FormattedCharSequence text) {
        return WordReplacerManager.replaceCharSequence(text);
    }

    @ModifyVariable(
            method = "prepare8xTextOutline(Lnet/minecraft/util/FormattedCharSequence;FFI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private FormattedCharSequence wish$replaceOutlineCharSequence(FormattedCharSequence text) {
        return WordReplacerManager.replaceCharSequence(text);
    }
}
