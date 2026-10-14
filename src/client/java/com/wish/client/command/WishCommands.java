package com.wish.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import com.wish.client.features.AutomatonManager;
import com.wish.client.features.CustomModelManager;
import com.wish.client.features.SlayerCarryManager;
import com.wish.client.gui.EditLagHudScreen;
import com.wish.client.gui.WishConfigScreen;
import com.wish.client.gui.WishModelScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

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
            .then(ClientCommands.literal("carry")
                .then(ClientCommands.argument("player", StringArgumentType.string()).suggests(playerSuggestor)
                    .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            if (!ModConfig.INSTANCE.enableSlayerCarry) {
                                WishClient.sendLocalChatMessage("§c[CarryTracker] Slayer Carry feature is disabled in config!");
                                return 0;
                            }
                            String player = StringArgumentType.getString(context, "player");
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            SlayerCarryManager.setTrackedPlayer(player, amount);
                            return 1;
                        })
                    )
                )
            )
            .then(ClientCommands.literal("stopcarry")
                .executes(context -> {
                    SlayerCarryManager.stopTracking();
                    return 1;
                })
                .then(ClientCommands.argument("player", StringArgumentType.string()).suggests(playerSuggestor)
                    .executes(context -> {
                        String player = StringArgumentType.getString(context, "player");
                        SlayerCarryManager.stopTrackingPlayer(player);
                        return 1;
                    })
                )
            )
            .then(ClientCommands.literal("automatonresetsession")
                .executes(context -> {
                    AutomatonManager.resetSessionStats();
                    WishClient.sendLocalChatMessage("§6§l[Wish] §aAutomaton session tracker stats have been reset!");
                    return 1;
                })
            )
            .then(ClientCommands.literal("automatonresettotal")
                .executes(context -> {
                    AutomatonManager.resetTotalStats();
                    WishClient.sendLocalChatMessage("§6§l[Wish] §cAutomaton total lifetime stats have been reset!");
                    return 1;
                })
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
