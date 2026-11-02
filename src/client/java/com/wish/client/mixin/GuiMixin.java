package com.wish.client.mixin;

import com.wish.client.config.ModConfig;
import com.wish.client.features.HudStateContainer;
import com.wish.client.gui.GuiAnchor;
import com.wish.client.gui.RenderUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {

    private static final int GLASS_BG = 0x00000000;
    private static final int GLASS_BORDER_LAG = 0x00000000;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        
        if (ModConfig.INSTANCE.enableLagTimeLost) {
            var lag = HudStateContainer.getLagSnapshot();
            if (lag.isLagging()) {
                int padX = 6;
                int padY = 4;
                int cardW = lag.textWidth() + padX * 2;
                int cardH = mc.font.lineHeight + padY * 2;

                int x = GuiAnchor.TOP_LEFT.resolveX(ModConfig.INSTANCE.lagHudX, cardW, screenW);
                int y = GuiAnchor.TOP_LEFT.resolveY(ModConfig.INSTANCE.lagHudY, cardH, screenH);
                float scale = Math.max(0.2f, ModConfig.INSTANCE.lagHudScale);

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(x, y);
                guiGraphics.pose().scale(scale, scale);

                RenderUtils.drawGlassPanel(guiGraphics, 0, 0, cardW, cardH, GLASS_BG, GLASS_BORDER_LAG);
                guiGraphics.text(mc.font, lag.formattedText(), padX, padY, 0xFFFFFFFF);

                guiGraphics.pose().popMatrix();
            }
        }

        
        var currentScreen = mc.gui != null ? mc.gui.screen() : null;
        if (currentScreen == null || currentScreen instanceof net.minecraft.client.gui.screens.ChatScreen) {
            com.wish.client.features.AutomatonHudOverlay.render(guiGraphics, mc, screenW, screenH, 0, 0, false);
        }

        
        if (ModConfig.INSTANCE.enableMiningAutomaton && ModConfig.INSTANCE.enablePrecursorCityScanner) {
            com.wish.client.features.PrecursorCityLocator.renderWaypoint(guiGraphics, mc, screenW, screenH);
        }

        // Render 3D Lyrics & Atmosphere
        if (ModConfig.INSTANCE.enableLyrics) {
            com.wish.client.features.environment.LyricsRenderer.render(guiGraphics, mc, screenW, screenH);
        }
    }
}
