package com.wish.client;

import com.wish.client.command.WishCommands;
import com.wish.client.config.ModConfig;
import com.wish.client.features.AutomatonManager;
import com.wish.client.features.DungeonLagTracker;
import com.wish.client.features.FunChatCommands;
import com.wish.client.features.GhostBlockManager;
import com.wish.client.features.JerryModeManager;
import com.wish.client.features.MineshaftPortalManager;
import com.wish.client.features.MiningAbilityManager;
import com.wish.client.features.PrecursorCityLocator;
import com.wish.client.features.SkyblockDetector;
import com.wish.client.features.SlayerCarryManager;
import com.wish.client.features.SorakaModeManager;
import com.wish.client.features.visual.CatPawTrailManager;
import com.wish.client.features.visual.LyricsRenderer;
import com.wish.client.music.MusicManager;
import com.wish.client.update.UpdateManager;
import com.wish.client.util.ChatUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WishClient implements ClientModInitializer {

	public static final String MOD_ID = "wish";
	public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
	public static final Logger LOGGER = LoggerFactory.getLogger("wish");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Wish Client Mod " + MOD_VERSION);

		ModConfig.load();
		UpdateManager.checkForUpdates();
		com.wish.client.color.CustomPlayerManager.init();
		com.wish.client.features.cosmetic.CosmeticSyncManager.init();
		GhostBlockManager.init();
		AutomatonManager.init();
		com.wish.client.features.visual.LyricsManager.init();
		LevelRenderEvents.COLLECT_SUBMITS.register(LyricsRenderer::renderWorld);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(CatPawTrailManager::renderWorld);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> WishCommands.register(dispatcher));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			SkyblockDetector.onClientTick(mc);
			DungeonLagTracker.onClientTick();
			PrecursorCityLocator.onClientTick(mc);
			AutomatonManager.onClientTick(mc);
			MineshaftPortalManager.onClientTick(mc);
			MiningAbilityManager.onClientTick(mc);
			CatPawTrailManager.onClientTick(mc);
			com.wish.client.color.CustomPlayerManager.onClientTick();
			com.wish.client.features.cosmetic.CosmeticSyncManager.onClientTick(mc);
			SlayerCarryManager.onClientTick(mc);
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen
					|| screen instanceof JoinMultiplayerScreen
					|| screen instanceof SelectWorldScreen
					|| screen instanceof DisconnectedScreen) {
				UpdateManager.onReturnToMenu();
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			com.wish.client.color.CustomPlayerManager.onPlayerJoin();
			com.wish.client.features.cosmetic.CosmeticSyncManager.onWorldJoin();
			UpdateManager.onPlayerJoin(client, handler != null ? handler.getConnection() : null);
			AutomatonManager.fetchBazaarPricesAsync(false);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			com.wish.client.color.CustomPlayerManager.onDisconnect();
			com.wish.client.features.cosmetic.CosmeticSyncManager.onDisconnect();
			UpdateManager.onDisconnect(client, handler != null ? handler.getConnection() : null);
			LyricsRenderer.onDisconnect();
			CatPawTrailManager.reset();
			SkyblockDetector.reset();
			AutomatonManager.onDisconnect();
			MineshaftPortalManager.reset();
			MiningAbilityManager.reset();
			PrecursorCityLocator.reset();
			SlayerCarryManager.reset();
			com.wish.client.features.SlayerOwnBossTracker.INSTANCE.reset();
			DungeonLagTracker.forceEndRun();
			JerryModeManager.deactivate();
			SorakaModeManager.deactivate();
		});

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			if (ModConfig.INSTANCE.enableMiningAutomaton && ModConfig.INSTANCE.enablePrecursorCityScanner && !PrecursorCityLocator.hasExactAltar()) {
				PrecursorCityLocator.checkChunkForCity(chunk);
			}
		});

		ClientSendMessageEvents.CHAT.register((message) -> {
			if (message == null) return;

			Minecraft mc = Minecraft.getInstance();
			boolean isHypixel = mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && mc.getCurrentServer().ip.toLowerCase().contains("hypixel");

			if (isHypixel && !message.startsWith("/")) {
				return;
			}

			String lower = message.trim().toLowerCase();
			if (lower.contains("!song") && ModConfig.INSTANCE.enableSong) {
				String prefix = ChatUtils.getChatPrefix(lower);
				if (prefix.equals("/ac ") && !ModConfig.INSTANCE.enableAc) return;
				if (prefix.equals("/pc ") && !ModConfig.INSTANCE.enablePc) return;
				if (prefix.equals("/gc ") && !ModConfig.INSTANCE.enableGc) return;
				MusicManager.fetchAndSendSongWithDelay(prefix);
			}
			FunChatCommands.checkFunCommands(lower, message);
		});

		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (overlay || message == null) return true;
			String text = message.getString();
			if (text == null) return true;

			String clean = SkyblockDetector.stripColorCodes(text).trim();
			return !clean.contains("[Wish] [Automaton] Drop!") && !clean.contains("[Wish] [Precursor City]");
		});

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			if (message == null) return true;
			String text = message.getString();
			if (text == null) return true;

			String clean = SkyblockDetector.stripColorCodes(text).trim();
			return !clean.contains("[Wish] [Automaton] Drop!") && !clean.contains("[Wish] [Precursor City]");
		});

		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			if (message == null) return;
			String text = message.getString();
			if (text != null && !text.contains("[Wish]")) {
				DungeonLagTracker.onChatMessage(text);
				SlayerCarryManager.onChatMessage(text);
				MiningAbilityManager.onChatMessage(text);
				if (ModConfig.INSTANCE.enableMiningAutomaton) {
					AutomatonManager.onChatMessage(message);
					if (ModConfig.INSTANCE.enablePrecursorCityScanner) {
						PrecursorCityLocator.onChatMessage(text);
					}
				}
			}
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (message == null || overlay) return;
			String text = message.getString();
			if (text != null && !text.contains("[Wish]")) {
				DungeonLagTracker.onChatMessage(text);
				SlayerCarryManager.onChatMessage(text);
				MiningAbilityManager.onChatMessage(text);
				if (ModConfig.INSTANCE.enableMiningAutomaton) {
					AutomatonManager.onChatMessage(message);
					if (ModConfig.INSTANCE.enablePrecursorCityScanner) {
						PrecursorCityLocator.onChatMessage(text);
					}
				}
			}

			if (text != null && text.contains("[Wish]")) return;

			String lower = text != null ? text.replace('\u00A0', ' ').toLowerCase().trim() : "";
			if (lower.contains("!song")) {
				if (!ModConfig.INSTANCE.enableSong) return;
				String prefix = ChatUtils.getChatPrefix(lower);
				if (prefix.equals("/pc ") && ModConfig.INSTANCE.enablePc) {
					MusicManager.fetchAndSendSongWithDelay("/pc ");
				} else if (prefix.equals("/gc ") && ModConfig.INSTANCE.enableGc) {
					MusicManager.fetchAndSendSongWithDelay("/gc ");
				} else if (prefix.equals("/ac ") && ModConfig.INSTANCE.enableAc) {
					MusicManager.fetchAndSendSongWithDelay("/ac ");
				}
			} else {
				FunChatCommands.checkFunCommands(lower, text);
			}
		});
	}

	public static void requestPairing() {
		MusicManager.requestPairing();
	}

	public static void sendLocalChatMessage(String message) {
		if (message == null || message.isEmpty()) return;
		if (message.contains("§")) {
			String stripped = SkyblockDetector.stripColorCodes(message);
			if (stripped.contains("[Wish]") || stripped.contains("WISH")) {
				sendLocalChatMessage(Component.literal(message));
			} else {
				MutableComponent prefixed = Component.literal("[Wish] ")
					.withStyle(Style.EMPTY
						.withColor(TextColor.fromRgb(0xffc6f9))
						.withBold(true))
					.append(Component.literal(message));
				sendLocalChatMessage(prefixed);
			}
			return;
		}
		String cleanMsg = message
			.replace("🎵 WISH » ", "")
			.replace("[Wish] ", "")
			.trim();

		MutableComponent comp = Component.literal("[Wish] ")
			.withStyle(Style.EMPTY
				.withColor(TextColor.fromRgb(0xffc6f9))
				.withBold(true))
			.append(Component.literal(cleanMsg)
				.withStyle(Style.EMPTY
					.withColor(TextColor.fromRgb(0x55ff55))
					.withBold(false)));

		sendLocalChatMessage(comp);
	}

	public static void sendModelEmptyHandWarning() {
		MutableComponent comp = Component.literal("[Wish] ")
			.withStyle(Style.EMPTY
				.withColor(TextColor.fromRgb(0xffc6f9))
				.withBold(true))
			.append(Component.literal("Please hold an item in your main hand to customize its model!")
				.withStyle(Style.EMPTY
					.withColor(TextColor.fromRgb(0xff5555))
					.withBold(false)));
		sendLocalChatMessage(comp);
	}

	public static void sendModelNoSkyblockIdWarning() {
		MutableComponent comp = Component.literal("[Wish] ")
			.withStyle(Style.EMPTY
				.withColor(TextColor.fromRgb(0xffc6f9))
				.withBold(true))
			.append(Component.literal("This item does not have a Skyblock ID! You must hold a Skyblock item.")
				.withStyle(Style.EMPTY
					.withColor(TextColor.fromRgb(0xff5555))
					.withBold(false)));
		sendLocalChatMessage(comp);
	}

	public static void sendLocalChatMessage(Component component) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			var player = mc.player;
			if (player != null) {
				player.sendSystemMessage(component);
			}
		});
	}

	public static void sendServerChatMessage(String chatPrefix, String message) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			var player = mc.player;
			if (player != null && player.connection != null) {
				String cleanBody = sanitizeForServerChat(message);
				boolean isHypixel = mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && mc.getCurrentServer().ip.toLowerCase().contains("hypixel");
				if (isHypixel && chatPrefix != null && !chatPrefix.trim().isEmpty()) {
					String cmd = chatPrefix.replace("/", "").trim() + " " + cleanBody;
					player.connection.sendCommand(cmd);
				} else {
					player.connection.sendChat(cleanBody);
				}
			}
		});
	}

	private static String sanitizeForServerChat(String msg) {
		StringBuilder sb = new StringBuilder();
		for (char c : msg.toCharArray()) {
			if (c != '§' && c >= ' ' && c != 127) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
