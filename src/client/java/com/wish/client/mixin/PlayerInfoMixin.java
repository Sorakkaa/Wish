package com.wish.client.mixin;

import com.wish.client.color.ComponentTextMatcher;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = PlayerInfo.class, priority = 2000)
public class PlayerInfoMixin {

    @ModifyVariable(method = "setTabListDisplayName", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component wish$stripIncomingTabDisplayName(Component displayName) {
        return displayName == null ? null : ComponentTextMatcher.stripMiniMessageFromComponent(displayName);
    }

}
