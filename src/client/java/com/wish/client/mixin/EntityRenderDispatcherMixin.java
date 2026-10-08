package com.wish.client.mixin;

import com.wish.client.features.JerryModeManager;
import com.wish.client.features.SorakaModeManager;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void onExtractEntity(E entity, float f, CallbackInfoReturnable<EntityRenderState> cir) {
        boolean isTargetEntity = entity instanceof net.minecraft.world.entity.monster.Monster || 
                                 entity instanceof net.minecraft.world.entity.animal.Animal || 
                                 entity instanceof net.minecraft.world.entity.player.Player;

        if (JerryModeManager.isActive() && isTargetEntity && !(entity instanceof Villager)) {
            Villager dummy = JerryModeManager.getDummyVillager(entity);
            if (dummy != null) {
                syncEntityPosAndRot(entity, dummy);
                EntityRenderDispatcher dispatcher = (EntityRenderDispatcher) (Object) this;
                cir.setReturnValue(dispatcher.extractEntity(dummy, f));
                return;
            }
        }

        if (SorakaModeManager.isActive() && isTargetEntity && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            RemotePlayer dummy = SorakaModeManager.getDummyPlayer(entity);
            if (dummy != null) {
                syncEntityPosAndRot(entity, dummy);
                EntityRenderDispatcher dispatcher = (EntityRenderDispatcher) (Object) this;
                EntityRenderState state = dispatcher.extractEntity(dummy, f);
                if (state != null && !entity.hasCustomName()) {
                    state.nameTag = null;
                    state.nameTagAttachment = null;
                }
                cir.setReturnValue(state);
            }
        }
    }

    private static void syncEntityPosAndRot(Entity entity, Entity dummy) {
        dummy.setPos(entity.getX(), entity.getY(), entity.getZ());
        dummy.setId(entity.getId());
        dummy.xo = entity.xo;
        dummy.yo = entity.yo;
        dummy.zo = entity.zo;
        dummy.xOld = entity.xOld;
        dummy.yOld = entity.yOld;
        dummy.zOld = entity.zOld;
        dummy.setYRot(entity.getYRot());
        dummy.setXRot(entity.getXRot());
        dummy.yRotO = entity.yRotO;
        dummy.xRotO = entity.xRotO;
        
        if (entity instanceof LivingEntity living && dummy instanceof LivingEntity dummyLiving) {
            dummyLiving.yHeadRot = living.yHeadRot;
            dummyLiving.yBodyRot = living.yBodyRot;
            dummyLiving.yHeadRotO = living.yHeadRotO;
            dummyLiving.yBodyRotO = living.yBodyRotO;
            dummyLiving.walkAnimation.setSpeed(living.walkAnimation.speed());
        }
        
        dummy.tickCount = entity.tickCount;
    }
}

