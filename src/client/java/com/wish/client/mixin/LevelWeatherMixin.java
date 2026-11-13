package com.wish.client.mixin;

import com.wish.client.features.visual.WeatherManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelWeatherMixin {

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void wish$overrideRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide() && WeatherManager.isCustomWeatherActive()) {
            cir.setReturnValue(WeatherManager.getRainLevel());
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void wish$overrideThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide() && WeatherManager.isCustomWeatherActive()) {
            cir.setReturnValue(WeatherManager.getThunderLevel());
        }
    }
}

