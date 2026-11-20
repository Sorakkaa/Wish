package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import com.wish.client.config.ModConfig;
import com.wish.client.features.CustomModelManager;
import com.wish.client.features.HudStateContainer;
import com.wish.client.gui.GuiAnchor;
import com.wish.client.gui.WishModelScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {

    @Shadow
    private ItemStack lastToolHighlight;

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

    @Unique
    private Component wish$resolveCustomSelectedItemName() {
        if (this.lastToolHighlight == null || this.lastToolHighlight.isEmpty()) {
            return null;
        }
        return CustomModelManager.getCustomNameComponent(this.lastToolHighlight);
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void wish$onHudTick(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if ((mc.gui == null || !(mc.gui.screen() instanceof WishModelScreen))
                && CustomModelManager.consumeToolHighlightRefresh()) {
            this.lastToolHighlight = ItemStack.EMPTY;
        }
    }

    @ModifyArg(
        method = "extractSelectedItemName",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"
        ),
        index = 0
    )
    private FormattedText wish$measureSelectedItemName(FormattedText text) {
        Component custom = wish$resolveCustomSelectedItemName();
        return custom != null ? custom : text;
    }

    @ModifyArg(
        method = "extractSelectedItemName",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"
        ),
        index = 1
    )
    private Component wish$renderSelectedItemName(Component text) {
        Component custom = wish$resolveCustomSelectedItemName();
        return custom != null ? custom : text;
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void wish$onExtractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
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

        if (ModConfig.INSTANCE.enableMiningAbilityHud) {
            var mining = HudStateContainer.getMiningAbilitySnapshot();
            if (mining.isVisible()) {
                int padX = 4;
                int padY = 3;
                int cardW = Math.max(mining.headerWidth(), mining.detailWidth()) + padX * 2;
                int cardH = mc.font.lineHeight * 2 + 2 + padY * 2;
                int mx = GuiAnchor.TOP_LEFT.resolveX(ModConfig.INSTANCE.miningAbilityHudX, cardW, screenW);
                int my = GuiAnchor.TOP_LEFT.resolveY(ModConfig.INSTANCE.miningAbilityHudY, cardH, screenH);
                float scale = Math.max(0.2f, ModConfig.INSTANCE.miningAbilityHudScale);

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(mx, my);
                guiGraphics.pose().scale(scale, scale);

                guiGraphics.text(mc.font, mining.header(), padX, padY, 0xFFFFFFFF);
                guiGraphics.text(mc.font, mining.detailText(), padX, padY + mc.font.lineHeight + 2, 0xFFFFFFFF);

                guiGraphics.pose().popMatrix();
            }
        }

        if (ModConfig.INSTANCE.enableSlayerCarry && ModConfig.INSTANCE.enableBossSpawnHud) {
            var boss = HudStateContainer.getBossSnapshot();
            if (boss.isVisible()) {
                int padX = 8;
                int padY = 5;
                int cardW = Math.max(boss.titleWidth(), boss.ownerWidth()) + padX * 2;
                int cardH = mc.font.lineHeight * 2 + 2 + padY * 2;
                int bx = GuiAnchor.TOP_LEFT.resolveX(ModConfig.INSTANCE.bossHudX, cardW, screenW);
                int by = GuiAnchor.TOP_LEFT.resolveY(ModConfig.INSTANCE.bossHudY, cardH, screenH);
                float scale = Math.max(0.2f, ModConfig.INSTANCE.bossHudScale);

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(bx, by);
                guiGraphics.pose().scale(scale, scale);

                guiGraphics.text(mc.font, boss.title(), (cardW - boss.titleWidth()) / 2, padY, 0xFFFFFFFF);
                guiGraphics.text(mc.font, boss.owner(), (cardW - boss.ownerWidth()) / 2, padY + mc.font.lineHeight + 2, 0xFF55FF55);

                guiGraphics.pose().popMatrix();
            }
        }
    }
}
