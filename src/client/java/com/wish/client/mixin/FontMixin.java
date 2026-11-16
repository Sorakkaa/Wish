package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
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
}
