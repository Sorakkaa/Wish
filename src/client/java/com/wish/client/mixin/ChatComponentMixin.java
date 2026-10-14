package com.wish.client.mixin;

import com.wish.client.color.NameColorManager;
import com.wish.client.config.ModConfig;
import com.wish.client.features.SkyblockDetector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /**
     * Colorize the message Component for ALL messages entering the chat,
     * including those added by other mods via addClientSystemMessage()
     * which bypass Fabric's ClientReceiveMessageEvents.MODIFY_GAME.
     * colorizeText is idempotent so double-processing is safe.
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component wish$colorizeIncomingMessage(Component message) {
        if (message == null) return message;
        try {
            String text = message.getString();
            // Skip our own [Wish] messages
            if (text != null && text.contains("[Wish]")) return message;
            Component colored = NameColorManager.colorizeText(message, true);
            return colored != null ? colored : message;
        } catch (Exception ignored) {}
        return message;
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void wish$onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (message != null && ModConfig.INSTANCE.enableMiningAutomaton) {
            String text = message.getString();
            if (text != null) {
                String clean = SkyblockDetector.stripColorCodes(text).trim();
                // Process sack notifications in AutomatonManager and hide them from chat view
                if (clean.startsWith("[Sacks]") || clean.contains("[Sacks]")) {
                    com.wish.client.features.AutomatonManager.onChatMessage(message);
                    ci.cancel();
                }
            }
        }
    }
}
