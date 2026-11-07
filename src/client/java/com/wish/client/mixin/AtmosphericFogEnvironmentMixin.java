package com.wish.client.mixin;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {

    @Inject(
        method = "getBaseColor(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/Camera;IF)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void wish$overrideFogBaseColor(ClientLevel level, Camera camera, int renderDistance, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (ModConfig.INSTANCE.enableCustomSky) {
            // Pitch-black horizon fog matching the pitch-black sky
            cir.setReturnValue(0xFF000000);
        }
    }
}

