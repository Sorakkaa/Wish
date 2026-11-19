package com.wish.client.mixin;

import com.wish.client.features.visual.WordReplacerManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MutableComponent.class, priority = 2000)
public class MutableComponentMixin {

    @Inject(method = "getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;", at = @At("RETURN"), cancellable = true)
    private void wish$onGetVisualOrderText(CallbackInfoReturnable<FormattedCharSequence> cir) {
        FormattedCharSequence result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        if (WordReplacerManager.isBypassed()) {
            cir.setReturnValue(WordReplacerManager.markBypassed(result));
            return;
        }
        if (WordReplacerManager.isAlreadyReplaced() || WordReplacerManager.isComponentMarkedReplaced((Component) (Object) this)) {
            cir.setReturnValue(WordReplacerManager.markReplaced(result));
            return;
        }
        cir.setReturnValue(WordReplacerManager.replaceCharSequence(result));
    }
}
