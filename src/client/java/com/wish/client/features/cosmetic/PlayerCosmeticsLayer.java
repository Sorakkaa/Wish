package com.wish.client.features.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wish.client.WishClient;
import com.wish.client.util.ISizeableState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class PlayerCosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier ATLAS_ID = Identifier.fromNamespaceAndPath("wish", "cosmetic_atlas_v8");
    private static final Identifier WING_ATLAS_ID = Identifier.fromNamespaceAndPath("wish", "wing_pixel_atlas_v1");

    private static volatile boolean atlasReady;
    private static volatile boolean atlasFailed;
    private static volatile RenderType cachedSolidRenderType;
    private static volatile RenderType cachedGlowRenderType;
    private static volatile RenderType cachedWingRenderType;

    public PlayerCosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            AvatarRenderState state,
            float limbAngle,
            float limbDistance
    ) {
        if (state.isInvisible || state.isSpectator || !(state instanceof ISizeableState cosmeticState)) {
            return;
        }
        int wingId = cosmeticState.wish$getWingId();
        int haloId = cosmeticState.wish$getHaloId();
        int particleId = cosmeticState.wish$getParticleId();
        if (wingId == 0 && haloId == 0 && particleId == 0) {
            return;
        }
        if (!ensureAtlasRegistered()) {
            return;
        }

        float ageTicks = state.ageInTicks;
        float walkSpeed = state.walkAnimationSpeed;
        boolean crouching = state.isCrouching;
        boolean fallFlying = state.isFallFlying;

        if (wingId > 0 || particleId > 0) {
            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            float baseFlap = PlayerCosmeticsManager.computeWingFlapAngleRad(wingId, ageTicks, walkSpeed, crouching, fallFlying);
            float tipFold = PlayerCosmeticsManager.computeWingTipFoldRad(wingId, baseFlap, ageTicks);

            if (wingId > 0) {
                collector.submitCustomGeometry(poseStack, cachedWingRenderType, (pose, vc) ->
                        CosmeticWingRenderer.renderWingsSolid(pose, vc, lightCoords, wingId, baseFlap, tipFold, ageTicks));
            }
            collector.submitCustomGeometry(poseStack, cachedGlowRenderType, (pose, vc) -> {
                if (wingId > 0) {
                    CosmeticWingRenderer.renderWingsGlow(pose, vc, wingId, baseFlap, tipFold, ageTicks);
                }
                if (particleId > 0) {
                    CosmeticHaloParticleRenderer.renderParticles(pose, vc, particleId, ageTicks);
                }
            });
            poseStack.popPose();
        }

        if (haloId > 0) {
            poseStack.pushPose();
            getParentModel().head.translateAndRotate(poseStack);
            float floatY = PlayerCosmeticsManager.computeHaloFloatOffset(haloId, ageTicks);
            collector.submitCustomGeometry(poseStack, cachedSolidRenderType, (pose, vc) ->
                    CosmeticHaloParticleRenderer.renderHaloSolid(pose, vc, haloId, floatY, ageTicks));
            collector.submitCustomGeometry(poseStack, cachedGlowRenderType, (pose, vc) ->
                    CosmeticHaloParticleRenderer.renderHaloGlow(pose, vc, haloId, floatY, ageTicks));
            poseStack.popPose();
        }
    }

    private static boolean ensureAtlasRegistered() {
        if (atlasReady && cachedSolidRenderType != null && cachedGlowRenderType != null && cachedWingRenderType != null) {
            return true;
        }
        if (atlasFailed) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        NativeImage image = null;
        NativeImage wingImage = null;
        try {
            image = new NativeImage(NativeImage.Format.RGBA, CosmeticAtlasMath.ATLAS_WIDTH, CosmeticAtlasMath.ATLAS_HEIGHT, false);
            for (int y = 0; y < CosmeticAtlasMath.ATLAS_HEIGHT; y++) {
                for (int x = 0; x < CosmeticAtlasMath.ATLAS_WIDTH; x++) {
                    image.setPixelABGR(x, y, CosmeticAtlasMath.sampleAtlasPixelAbgr(x, y));
                }
            }
            CosmeticAtlasTexture texture = new CosmeticAtlasTexture(image);
            image = null;
            mc.getTextureManager().register(ATLAS_ID, texture);

            wingImage = new NativeImage(NativeImage.Format.RGBA, CosmeticWingAtlasMath.ATLAS_WIDTH, CosmeticWingAtlasMath.ATLAS_HEIGHT, false);
            for (int y = 0; y < CosmeticWingAtlasMath.ATLAS_HEIGHT; y++) {
                for (int x = 0; x < CosmeticWingAtlasMath.ATLAS_WIDTH; x++) {
                    wingImage.setPixelABGR(x, y, CosmeticWingAtlasMath.sampleWingAtlasAbgr(x, y));
                }
            }
            CosmeticAtlasTexture wingTexture = new CosmeticAtlasTexture(wingImage);
            wingImage = null;
            mc.getTextureManager().register(WING_ATLAS_ID, wingTexture);

            cachedSolidRenderType = RenderTypes.entityTranslucent(ATLAS_ID);
            cachedGlowRenderType = RenderTypes.entityTranslucentEmissive(ATLAS_ID);
            cachedWingRenderType = RenderTypes.entityTranslucent(WING_ATLAS_ID);
            atlasReady = true;
            return true;
        } catch (Exception e) {
            if (image != null) {
                image.close();
            }
            if (wingImage != null) {
                wingImage.close();
            }
            atlasFailed = true;
            WishClient.LOGGER.error("[Wish] Failed to generate procedural cosmetic atlas", e);
            return false;
        }
    }

    private static final class CosmeticAtlasTexture extends DynamicTexture {
        CosmeticAtlasTexture(NativeImage image) {
            super(() -> "Wish Cosmetic Atlas v7", image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }
}
