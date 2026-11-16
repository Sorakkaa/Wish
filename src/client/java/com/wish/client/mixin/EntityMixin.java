package com.wish.client.mixin;

import com.wish.client.config.ModConfig;
import com.wish.client.features.AutomatonManager;
import com.wish.client.features.SkyblockDetector;
import com.wish.client.features.SlayerCarryManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void wish$onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        boolean checkAutomaton = ModConfig.INSTANCE.enableMiningAutomaton && ModConfig.INSTANCE.enableAutomatonGlow && SkyblockDetector.isInPrecursorCity;
        boolean checkSlayer = ModConfig.INSTANCE.enableSlayerCarry && ModConfig.INSTANCE.enableSlayerGlow;
        if (!checkAutomaton && !checkSlayer) return;

        Entity entity = (Entity) (Object) this;
        var level = entity.level();
        if (level == null || !level.isClientSide()) return;

        if (checkAutomaton && AutomatonManager.isGlowingAutomaton(entity)) {
            cir.setReturnValue(true);
            return;
        }
        if (checkSlayer && SlayerCarryManager.isActualBossEntity(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void wish$onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        boolean checkAutomaton = ModConfig.INSTANCE.enableMiningAutomaton && ModConfig.INSTANCE.enableAutomatonGlow && SkyblockDetector.isInPrecursorCity;
        boolean checkSlayer = ModConfig.INSTANCE.enableSlayerCarry && ModConfig.INSTANCE.enableSlayerGlow;
        if (!checkAutomaton && !checkSlayer) return;

        Entity entity = (Entity) (Object) this;
        var level = entity.level();
        if (level == null || !level.isClientSide()) return;

        if (checkAutomaton && AutomatonManager.isGlowingAutomaton(entity)) {
            cir.setReturnValue(AutomatonManager.getGlowColorRgb());
            return;
        }
        if (checkSlayer && SlayerCarryManager.isActualBossEntity(entity)) {
            cir.setReturnValue(SlayerCarryManager.getGlowColorRgb());
        }
    }
}
