package com.wish.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.wish.client.config.ModConfig;
import com.wish.client.features.visual.CustomSkyManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyRenderer.class, priority = 500)
public abstract class SkyRendererMixin {

    @Shadow @Final private RenderTarget renderTarget;

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
        at = @At("RETURN")
    )
    private void wish$applyCustomSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (ModConfig.INSTANCE.enableCustomSky) {
            int mode = ModConfig.INSTANCE.customSkyMode;
            if (state.skybox == DimensionType.Skybox.NONE || state.skybox == DimensionType.Skybox.END) {
                state.skybox = DimensionType.Skybox.OVERWORLD;
            }
            state.skyColor = CustomSkyManager.getSkyColor();
            state.sunriseAndSunsetColor = 0;
            // Keep the bottom dark disc disabled so stars render across the full 360-degree skybox
            state.shouldRenderDarkDisc = false;

            if (ModConfig.INSTANCE.customSkyShowStars) {
                state.starBrightness = Math.max(state.starBrightness, 0.85f);
            } else {
                state.starBrightness = 0.0f;
            }

            if (ModConfig.INSTANCE.customSkyShowSunMoon) {
                state.rainBrightness = Math.max(0.8f, state.rainBrightness);
            } else {
                state.rainBrightness = 0.0f;
            }
        }
    }

    @Inject(method = "renderSkyDisc(I)V", at = @At("HEAD"), cancellable = true)
    private void wish$onRenderSkyDisc(int skyColor, CallbackInfo ci) {
        if (ModConfig.INSTANCE.enableCustomSky && ModConfig.INSTANCE.customSkyMode != 0) {
            if (CustomSkyManager.renderCustomSky(this.renderTarget)) {
                ci.cancel();
            }
        }
    }
}
