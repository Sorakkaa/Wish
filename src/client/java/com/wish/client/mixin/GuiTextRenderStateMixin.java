package com.wish.client.mixin;

import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiTextRenderState.class, priority = 2000)
public class GuiTextRenderStateMixin {

    @Shadow
    @Final
    @Mutable
    private FormattedCharSequence text;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wish$onConstructTextState(
            Font font,
            FormattedCharSequence rawText,
            Matrix3x2fc pose,
            int x,
            int y,
            int color,
            int backgroundColor,
            boolean dropShadow,
            boolean includeEmpty,
            ScreenRectangle scissor,
            CallbackInfo ci
    ) {
        if (this.text == null) {
            return;
        }
        if (WordReplacerManager.isBypassed()) {
            this.text = WordReplacerManager.markBypassed(this.text);
        } else if (WordReplacerManager.isAlreadyReplaced()) {
            this.text = WordReplacerManager.markReplaced(this.text);
        } else {
            this.text = WordReplacerManager.replaceCharSequence(this.text);
        }
    }
}
