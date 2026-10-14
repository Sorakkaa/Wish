package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiMessage.Line.class)
public class GuiMessageLineMixin {

    @Inject(method = "content", at = @At("RETURN"), cancellable = true)
    private void wish$onGetContent(CallbackInfoReturnable<FormattedCharSequence> cir) {
        FormattedCharSequence original = cir.getReturnValue();
        if (original != null) {
            FormattedCharSequence animated = NameColorManager.wrapAnimatedLine(original);
            if (animated != null && animated != original) {
                cir.setReturnValue(animated);
            }
        }
    }
}
