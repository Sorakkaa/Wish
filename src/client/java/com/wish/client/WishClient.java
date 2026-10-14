package com.wish.client;

import com.wish.client.command.WishCommands;
import com.wish.client.config.ModConfig;
import com.wish.client.features.AutomatonManager;
import com.wish.client.features.DungeonLagTracker;
import com.wish.client.features.FunChatCommands;
import com.wish.client.features.GhostBlockManager;
import com.wish.client.features.PrecursorCityLocator;
import com.wish.client.features.SkyblockDetector;
import com.wish.client.features.SlayerCarryManager;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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

	public static String currentSongText = null;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Wish Client Mod " + MOD_VERSION);

		UpdateManager.checkForUpdates();
		ModConfig.load();
		com.wish.client.color.NameColorManager.init();
		GhostBlockManager.init();
		AutomatonManager.init();

		
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> WishCommands.register(dispatcher));

		
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			SkyblockDetector.onClientTick(mc);
			DungeonLagTracker.onClientTick();
			SlayerCarryManager.onClientTick(mc);
			PrecursorCityLocator.onClientTick(mc);
			AutomatonManager.onClientTick(mc);
		});

		
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			com.wish.client.color.NameColorManager.syncLocalPlayerColor();
			UpdateManager.checkForUpdates();
			AutomatonManager.fetchBazaarPricesAsync();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SlayerCarryManager.stopTracking();
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

		
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay) return message;

			String text = message.getString();
			if (text != null && text.contains("[Wish]")) {
				return message;
			}

			return com.wish.client.color.NameColorManager.colorizeText(message, true);
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
				if (ModConfig.INSTANCE.enableMiningAutomaton) {
					AutomatonManager.onChatMessage(message);
					if (ModConfig.INSTANCE.enablePrecursorCityScanner) {
						PrecursorCityLocator.onChatMessage(text);
					}
				}
			}

			if (text != null && text.contains("[Wish]")) return;
			if (!ModConfig.INSTANCE.enableSong) return;

			String lower = text != null ? text.replace('\u00A0', ' ').toLowerCase().trim() : "";
			if (lower.contains("!song")) {
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
		String cleanMsg = message.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "")
			.replace("🎵 WISH » ", "")
			.replace("[Wish] ", "")
			.replace("[CarryTracker] ", "")
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
