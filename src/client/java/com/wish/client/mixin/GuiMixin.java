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
    private static final int GLASS_BORDER_BOSS = 0x00000000;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // --- 1. LAG TIME LOST HUD (Zero-Alloc & Responsive) ---
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

        // --- 2. BOSS SPAWN HUD (Zero-Alloc & Responsive) ---
        if (ModConfig.INSTANCE.enableBossSpawnHud) {
            var boss = HudStateContainer.getBossSnapshot();
            if (boss.isVisible()) {
                int padX = 8;
                int padY = 5;
                int cardW = boss.maxWidth() + padX * 2;
                int cardH = mc.font.lineHeight * 2 + 2 + padY * 2;

                int x = GuiAnchor.TOP_CENTER.resolveX(ModConfig.INSTANCE.bossHudX, cardW, screenW);
                int y = GuiAnchor.TOP_CENTER.resolveY(ModConfig.INSTANCE.bossHudY, cardH, screenH);
                float scale = Math.max(0.2f, ModConfig.INSTANCE.bossHudScale);

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(x, y);
                guiGraphics.pose().scale(scale, scale);

                RenderUtils.drawGlassPanel(guiGraphics, 0, 0, cardW, cardH, GLASS_BG, GLASS_BORDER_BOSS);
                
                int w1 = mc.font.width(boss.line1());
                int w2 = mc.font.width(boss.line2());
                guiGraphics.text(mc.font, boss.line1(), (cardW - w1) / 2, padY, 0xFFFFAA00);
                guiGraphics.text(mc.font, boss.line2(), (cardW - w2) / 2, padY + mc.font.lineHeight + 2, 0xFFFF5555);

                guiGraphics.pose().popMatrix();
            }
        }
    }
}
