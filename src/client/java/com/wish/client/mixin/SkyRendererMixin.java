package com.wish.client.mixin;

import com.wish.client.config.ModConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
        at = @At("RETURN")
    )
    private void wish$applyCustomSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (ModConfig.INSTANCE.enableCustomSky) {
            // Pure pitch-black sky dome
            state.skyColor = 0xFF000000;
            // Suppress sunrise orange fog disc so sky stays deep space black
            state.sunriseAndSunsetColor = 0;
            // Keep dark bottom hemisphere
            state.shouldRenderDarkDisc = true;
            // Ensure sun and moon stay visible
            state.rainBrightness = Math.max(0.6f, state.rainBrightness);
        }
    }
}

