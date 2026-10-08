package com.wish.client.mixin;

import com.wish.client.config.ModConfig;
import com.wish.client.features.SlayerCarryManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (ModConfig.INSTANCE.enableSlayerCarry) {
            Entity entity = (Entity) (Object) this;
            if (SlayerCarryManager.isActualBossEntity(entity)) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                // Add hasLineOfSight check so it doesn't render through walls (no ESP)
                if (mc.player != null && mc.player.hasLineOfSight(entity)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        if (ModConfig.INSTANCE.enableSlayerCarry) {
            Entity entity = (Entity) (Object) this;
                String glow = ModConfig.INSTANCE.slayerGlowColor;
                if (glow != null && glow.length() == 7 && glow.charAt(0) == '#') {
                    try {
                        int color = Integer.parseInt(glow, 1, 7, 16);
                        cir.setReturnValue(color);
                    } catch (Exception ignored) {}
                }
        }
    }
}
