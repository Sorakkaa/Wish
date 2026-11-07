package com.wish.client.mixin;

import com.wish.client.features.visual.TimeManager;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public class ClientClockManagerMixin {

    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void wish$overrideTotalTicks(Holder<WorldClock> holder, CallbackInfoReturnable<Long> cir) {
        if (TimeManager.isCustomTimeActive()) {
            cir.setReturnValue(TimeManager.getCustomTicks());
        }
    }
}

