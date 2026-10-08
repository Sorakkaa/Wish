package com.wish.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component onAddMessage(Component message) {
        if (message != null) {
            String text = message.getString();
            if (text != null && !text.contains("[Wish]")) {
                com.wish.client.features.DungeonLagTracker.onChatMessage(text);
            }
        }
        return message;
    }
}
