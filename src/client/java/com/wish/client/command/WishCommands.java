package com.wish.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import com.wish.client.features.AutomatonManager;
import com.wish.client.features.CustomModelManager;
import com.wish.client.gui.EditLagHudScreen;
import com.wish.client.gui.WishConfigScreen;
import com.wish.client.gui.WishModelScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.io.File;

public final class WishCommands {

    private WishCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SuggestionProvider<FabricClientCommandSource> playerSuggestor = (context, builder) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                String remaining = builder.getRemaining().toLowerCase();
                for (Player player : mc.level.players()) {
                    String name = player.getName().getString();
                    if (name != null && name.toLowerCase().startsWith(remaining)) {
                        builder.suggest(name);
                    }
                }
            }
            return builder.buildFuture();
        };

        dispatcher.register(ClientCommands.literal("wish")
            .executes(context -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.gui.setScreen(new WishConfigScreen(mc.gui.screen())));
                return 1;
            })
            .then(ClientCommands.literal("gui")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new EditLagHudScreen(mc.gui.screen())));
                    return 1;
                })
            )
            .then(ClientCommands.literal("model")
                .executes(context -> openModelScreen())
            )
        );
    }

    private static int openModelScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getMainHandItem().isEmpty()) {
            WishClient.sendModelEmptyHandWarning();
            return 0;
        }
        String sbId = CustomModelManager.getSkyblockId(mc.player.getMainHandItem());
        if (sbId == null || sbId.isEmpty()) {
            WishClient.sendModelNoSkyblockIdWarning();
            return 0;
        }
        mc.execute(() -> mc.gui.setScreen(new WishModelScreen(mc.gui.screen())));
        return 1;
    }
}
