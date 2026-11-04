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

        if (cosMode == 2) {
            sizeable.wish$setScale(1.0f, 1.0f, 1.0f);
            return;
        }

        if (cosMode == 1 && !isLocalPlayer) {
            sizeable.wish$setScale(1.0f, 1.0f, 1.0f);
            return;
        }

        if (!ModConfig.INSTANCE.playerSizeEnabled && !isCustomPlayer) {
            sizeable.wish$setScale(1.0f, 1.0f, 1.0f);
            return;
        }

        if (isCustomPlayer) {
            scaleX = customData.scaleX();
            scaleY = customData.scaleY();
            scaleZ = customData.scaleZ();
        } else if (isLocalPlayer) {
            scaleX = ModConfig.INSTANCE.playerSizeX;
            scaleY = ModConfig.INSTANCE.playerSizeY;
            scaleZ = ModConfig.INSTANCE.playerSizeZ;
        }

        sizeable.wish$setScale(scaleX, scaleY, scaleZ);

        boolean isGuiOpen = mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
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

        if (sizeable.wish$hasCustomScale() && scaleY != 1.0f && renderState.nameTagAttachment != null) {
            renderState.nameTagAttachment = new net.minecraft.world.phys.Vec3(
                renderState.nameTagAttachment.x,
                renderState.nameTagAttachment.y * scaleY,
                renderState.nameTagAttachment.z
            );
        }
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"))
    private void onScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (state instanceof ISizeableState sizeable) {
            if (sizeable.wish$hasCustomScale()) {
                poseStack.scale(sizeable.wish$getScaleX(), sizeable.wish$getScaleY(), sizeable.wish$getScaleZ());
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
