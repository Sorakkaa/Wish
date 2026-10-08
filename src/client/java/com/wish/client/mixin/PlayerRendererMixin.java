package com.wish.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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

    @org.spongepowered.asm.mixin.Unique
    private static final java.util.UUID wish$SORAKKAA_UUID = java.util.UUID.fromString("14b458e1-1374-4981-ab21-8bd942449ef7");
    @org.spongepowered.asm.mixin.Unique
    private static final java.util.UUID wish$MAIRUY_UUID = java.util.UUID.fromString("43562a4a-93e2-437c-934c-64e17383ac00");

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
        
        boolean isSorakkaa = wish$SORAKKAA_UUID.equals(entity.getUUID()) || "sorakkaa".equalsIgnoreCase(entity.getScoreboardName());
        boolean isMairuy = wish$MAIRUY_UUID.equals(entity.getUUID()) || "mairuy".equalsIgnoreCase(entity.getScoreboardName());
        sizeable.wish$setSorakkaa(isSorakkaa);
        sizeable.wish$setMairuy(isMairuy);

        float scaleX = 1.0f;
        float scaleY = 1.0f;
        float scaleZ = 1.0f;
        
        boolean isDev = isSorakkaa || isMairuy;

        // Mode 2: Tout cacher (Hide All) -> scale is normal (1, 1, 1) for everyone
        if (cosMode == 2) {
            sizeable.wish$setScale(1.0f, 1.0f, 1.0f);
            return;
        }

        // Mode 1: Que les miens (Own Only) -> other players stay normal (1, 1, 1)
        if (cosMode == 1 && !isLocalPlayer) {
            sizeable.wish$setScale(1.0f, 1.0f, 1.0f);
            return;
        }

        // Add toggle check
        if (!ModConfig.INSTANCE.playerSizeEnabled && !isDev) {
            sizeable.wish$setScale(1.0f, 1.0f, 1.0f);
            return;
        }

        if (isSorakkaa) {
            scaleX = 1.0f;
            scaleY = 0.30f;
            scaleZ = 0.01f;
        } else if (isMairuy) {
            scaleX = 3.00f;
            scaleY = 1.67f;
            scaleZ = 0.01f;
        } else if (isLocalPlayer) {
            scaleX = ModConfig.INSTANCE.playerSizeX;
            scaleY = ModConfig.INSTANCE.playerSizeY;
            scaleZ = ModConfig.INSTANCE.playerSizeZ;
        }

        sizeable.wish$setScale(scaleX, scaleY, scaleZ);

        boolean isGuiOpen = mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
        boolean canRenderNametag = (cosMode == 0 && (isLocalPlayer || isSorakkaa || isMairuy)) || (cosMode == 1 && isLocalPlayer);
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
            boolean doSpin = cosMode != 2 && sizeable.wish$isLocalPlayer() && !sizeable.wish$isMairuy() && ModConfig.INSTANCE.enablePlayerSpin;
            if (doSpin) {
                float spinSpeedY = ModConfig.INSTANCE.playerSpinSpeedY;
                if (spinSpeedY != 0) {
                    double time = (double) System.currentTimeMillis();
                    float angleY = (float) (((time * spinSpeedY) / 10.0) % 360.0);
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angleY));
                }
            }
        }
    }
}
