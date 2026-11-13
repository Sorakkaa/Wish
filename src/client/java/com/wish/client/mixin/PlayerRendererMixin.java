package com.wish.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wish.client.color.CustomPlayerManager;
import com.wish.client.color.NameColorManager;
import com.wish.client.config.ModConfig;
import com.wish.client.util.ISizeableState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void onExtractRenderState(Avatar entity, AvatarRenderState renderState, float f, CallbackInfo ci) {
        if (!(renderState instanceof ISizeableState sizeable)) return;

        int cosMode = ModConfig.INSTANCE.cosmeticVisibility;

        if (cosMode != 2 && com.wish.client.features.SorakaModeManager.isActive()) {
            renderState.skin = com.wish.client.features.SorakaModeManager.SORAKA_SKIN;
            renderState.showHat = true;
            renderState.showJacket = true;
            renderState.showLeftPants = true;
            renderState.showRightPants = true;
            renderState.showLeftSleeve = true;
            renderState.showRightSleeve = true;
        }
        
        var mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && entity.getId() == mc.player.getId();
        sizeable.wish$setLocalPlayer(isLocalPlayer);
        
        CustomPlayerManager.CustomPlayerData customData = CustomPlayerManager.getPlayer(entity.getUUID(), entity.getScoreboardName());
        boolean isCustomPlayer = customData != null;
        sizeable.wish$setCustomPlayer(isCustomPlayer);
        sizeable.wish$setSpinSpeedY(isCustomPlayer ? customData.spinSpeedY() : 0.0f);

        float scaleX = 1.0f;
        float scaleY = 1.0f;
        float scaleZ = 1.0f;

        if (cosMode != 2 && !(cosMode == 1 && !isLocalPlayer)) {
            if (isCustomPlayer) {
                scaleX = customData.scaleX();
                scaleY = customData.scaleY();
                scaleZ = customData.scaleZ();
            } else if (isLocalPlayer && ModConfig.INSTANCE.playerSizeEnabled) {
                scaleX = ModConfig.INSTANCE.playerSizeX;
                scaleY = ModConfig.INSTANCE.playerSizeY;
                scaleZ = ModConfig.INSTANCE.playerSizeZ;
            }
        }
        sizeable.wish$setScale(scaleX, scaleY, scaleZ);

        // Skip Smol People if the player (self or other) already has a modified scale (Wish custom size or vanilla scale)
        boolean hasModifiedScale = Math.abs(scaleX - 1.0f) > 1e-4f
                || Math.abs(scaleY - 1.0f) > 1e-4f
                || Math.abs(scaleZ - 1.0f) > 1e-4f
                || Math.abs(entity.getScale() - 1.0f) > 1e-4f;

        boolean applySmol = ModConfig.INSTANCE.enableOtherPlayersSize
                && !hasModifiedScale
                && (!isLocalPlayer || ModConfig.INSTANCE.smolPeopleIncludeSelf);
        sizeable.wish$setSmolPeople(applySmol);

        var currentScreen = mc.gui != null ? mc.gui.screen() : null;
        boolean isGuiOpen = currentScreen != null && !(currentScreen instanceof net.minecraft.client.gui.screens.ChatScreen);
        boolean canRenderNametag = (cosMode == 0 && (isLocalPlayer || isCustomPlayer)) || (cosMode == 1 && isLocalPlayer);
        if (canRenderNametag && !mc.options.getCameraType().isFirstPerson() && !isGuiOpen) {
            if (renderState.nameTag == null) {
                net.minecraft.network.chat.Component nameComp = entity.getDisplayName();
                renderState.nameTag = NameColorManager.colorizeText(nameComp);
                if (renderState.nameTagAttachment == null) {
                    renderState.nameTagAttachment = entity.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, entity.getViewYRot(f));
                }
            }
        }

        float effectiveScaleY = scaleY * (applySmol ? 0.5f : 1.0f);
        if (applySmol) {
            renderState.shadowRadius *= 0.5f;
        }
        if (effectiveScaleY != 1.0f && renderState.nameTagAttachment != null) {
            double headCompensation = applySmol ? 0.25 : 0.0;
            renderState.nameTagAttachment = new net.minecraft.world.phys.Vec3(
                renderState.nameTagAttachment.x,
                renderState.nameTagAttachment.y * effectiveScaleY + headCompensation,
                renderState.nameTagAttachment.z
            );
        }
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"))
    private void onScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (state instanceof ISizeableState sizeable) {
            float sx = sizeable.wish$hasCustomScale() ? sizeable.wish$getScaleX() : 1.0f;
            float sy = sizeable.wish$hasCustomScale() ? sizeable.wish$getScaleY() : 1.0f;
            float sz = sizeable.wish$hasCustomScale() ? sizeable.wish$getScaleZ() : 1.0f;
            if (sizeable.wish$isSmolPeople()) {
                sx *= 0.5f;
                sy *= 0.5f;
                sz *= 0.5f;
            }
            if (sx != 1.0f || sy != 1.0f || sz != 1.0f) {
                // Scaling PoseStack at feet origin ensures third-party cosmetic RenderLayers (e.g. Essential) scale and anchor cleanly
                poseStack.scale(sx, sy, sz);
            }
            int cosMode = ModConfig.INSTANCE.cosmeticVisibility;
            boolean doSpin = false;
            float spinSpeedY = 0.0f;

            if (cosMode != 2) {
                if (sizeable.wish$isCustomPlayer() && (cosMode == 0 || sizeable.wish$isLocalPlayer())) {
                    if (sizeable.wish$getSpinSpeedY() != 0.0f) {
                        doSpin = true;
                        spinSpeedY = sizeable.wish$getSpinSpeedY();
                    } else if (sizeable.wish$isLocalPlayer() && ModConfig.INSTANCE.enablePlayerSpin) {
                        doSpin = true;
                        spinSpeedY = ModConfig.INSTANCE.playerSpinSpeedY;
                    }
                } else if (sizeable.wish$isLocalPlayer() && ModConfig.INSTANCE.enablePlayerSpin) {
                    doSpin = true;
                    spinSpeedY = ModConfig.INSTANCE.playerSpinSpeedY;
                }
            }

            if (doSpin && spinSpeedY != 0) {
                double time = (double) System.currentTimeMillis();
                float angleY = (float) (((time * spinSpeedY) / 10.0) % 360.0);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angleY));
            }
        }
    }
}
