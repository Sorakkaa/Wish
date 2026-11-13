package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import com.wish.client.config.ModConfig;
import com.wish.client.features.HudStateContainer;
import com.wish.client.gui.GuiAnchor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {

    @Inject(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.BEFORE
        )
    )
    private void wish$beforeExtractScoreboardSidebar(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        NameColorManager.isRenderingScoreboard = true;
    }

    @Inject(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER
        )
    )
    private void wish$afterExtractScoreboardSidebar(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        NameColorManager.isRenderingScoreboard = false;
    }

    @ModifyArg(
        method = "displayScoreboardSidebar",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"
        ),
        index = 0
    )
    private FormattedText wish$measureScoreboardText(FormattedText text) {
        if (text instanceof Component comp) {
            Component colored = NameColorManager.colorizeText(
                comp,
                false,
                NameColorManager.isTabOverlayActive(),
                true
            );
            if (colored != null) {
                return colored;
            }
        }
        return text;
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        NameColorManager.isRenderingScoreboard = false;
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

        if (ModConfig.INSTANCE.enableBossSpawnHud) {
            var boss = HudStateContainer.getBossSnapshot();
            if (boss.isVisible()) {
                net.minecraft.network.chat.Component line1 = net.minecraft.network.chat.Component.literal("Boss Spawn")
                        .withStyle(s -> s.withColor(0xFFFF55FF).withBold(true));
                String line2 = boss.owner();
                int w1 = mc.font.width(line1);
                int w2 = mc.font.width(line2);
                int padX = 8;
                int padY = 5;
                int cardW = Math.max(w1, w2) + padX * 2;
                int bx = ModConfig.INSTANCE.bossHudX;
                int by = ModConfig.INSTANCE.bossHudY;
                float scale = Math.max(0.2f, ModConfig.INSTANCE.bossHudScale);

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(bx, by);
                guiGraphics.pose().scale(scale, scale);

                guiGraphics.text(mc.font, line1, (cardW - w1) / 2, padY, 0xFFFFFFFF);
                guiGraphics.text(mc.font, line2, (cardW - w2) / 2, padY + mc.font.lineHeight + 2, 0xFF55FF55);

                guiGraphics.pose().popMatrix();
            }
        }

        // Render 3D Lyrics & Atmosphere
        if (ModConfig.INSTANCE.enableLyrics) {
            com.wish.client.features.visual.LyricsRenderer.render(guiGraphics, mc, screenW, screenH);
        }
    }
}
