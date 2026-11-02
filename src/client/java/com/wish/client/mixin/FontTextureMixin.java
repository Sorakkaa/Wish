package com.wish.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
        if (label != null) {
            String name = label.get();
            if (name != null && name.startsWith("wish:")) {
                this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            }
        }
    }
}

