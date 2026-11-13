package com.wish.client.mixin;

import com.wish.client.features.visual.TimeManager;
import net.minecraft.client.ClientClockManager;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Higher priority than other client time-changer mods (e.g. NoammAddons) so Wish Time Changer
 * overrides timeline sampling consistently when enabled.
 */
@Mixin(value = AttributeTrackSampler.class, priority = 2000)
public class AttributeTrackSamplerMixin {

    @Shadow
    @Final
    private ClockManager clockManager;

    @ModifyArg(
        method = "applyTimeBased",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/KeyframeTrackSampler;sample(J)Ljava/lang/Object;"
        ),
        index = 0
    )
    private long wish$overrideSampleTicks(long ticks) {
        if (this.clockManager instanceof ClientClockManager && TimeManager.isCustomTimeActive()) {
            return TimeManager.getCustomTicks();
        }
        return ticks;
    }
}

