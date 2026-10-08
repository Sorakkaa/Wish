package com.wish.client;

import com.wish.client.config.ModConfig;
import com.wish.client.gui.WishConfigScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import com.wish.client.features.SkyblockDetector;
import com.wish.client.features.GhostBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WishClient implements ClientModInitializer {

	public static final String MOD_ID = "wish";
	public static final String MOD_VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
	private static final String APP_ID = "wish";
	private static final Logger LOGGER = LoggerFactory.getLogger("wish");

	private static long lastRequestTime = 0;
	private static volatile boolean isPairingActive = false;
	public static String currentSongText = null;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Wish Client Mod " + MOD_VERSION);
		
		com.wish.client.update.UpdateManager.checkForUpdates();
		ModConfig.load();
		com.wish.client.color.NameColorManager.init();
		com.wish.client.features.GhostBlockManager.init();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			SkyblockDetector.onClientTick(mc);
			com.wish.client.features.DungeonLagTracker.onClientTick();
			com.wish.client.features.SlayerCarryManager.onClientTick(mc);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			com.wish.client.color.NameColorManager.syncLocalPlayerColor();
			com.wish.client.update.UpdateManager.checkForUpdates();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			com.wish.client.features.SlayerCarryManager.stopTracking();
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
				String prefix = getChatPrefix(lower);
				if (prefix.equals("/ac ") && !ModConfig.INSTANCE.enableAc) return;
				if (prefix.equals("/pc ") && !ModConfig.INSTANCE.enablePc) return;
				if (prefix.equals("/gc ") && !ModConfig.INSTANCE.enableGc) return;
				fetchAndSendSongWithDelay(prefix);
			}
			checkFunCommands(lower, message);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			com.mojang.brigadier.suggestion.SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> playerSuggestor = (context, builder) -> {
				Minecraft mc = Minecraft.getInstance();
				if (mc.level != null) {
					String remaining = builder.getRemaining().toLowerCase();
					for (net.minecraft.world.entity.player.Player player : mc.level.players()) {
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
						mc.execute(() -> mc.gui.setScreen(new com.wish.client.gui.EditLagHudScreen(mc.gui.screen())));
						return 1;
					})
				)
				.then(ClientCommands.literal("model")
					.executes(context -> {
						Minecraft mc = Minecraft.getInstance();
						if (mc.player == null || mc.player.getMainHandItem().isEmpty()) {
							sendModelEmptyHandWarning();
							return 0;
						}
						mc.execute(() -> mc.gui.setScreen(new com.wish.client.gui.WishModelScreen(mc.gui.screen())));
						return 1;
					})
				)
				.then(ClientCommands.literal("carry")
					.then(ClientCommands.argument("player", StringArgumentType.string()).suggests(playerSuggestor)
						.then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								if (!ModConfig.INSTANCE.enableSlayerCarry) {
									sendLocalChatMessage("§c[CarryTracker] Slayer Carry feature is disabled in config!");
									return 0;
								}
								String player = StringArgumentType.getString(context, "player");
								int amount = IntegerArgumentType.getInteger(context, "amount");
								com.wish.client.features.SlayerCarryManager.setTrackedPlayer(player, amount);
								return 1;
							})
						)
					)
				)
				.then(ClientCommands.literal("stopcarry")
					.executes(context -> {
						com.wish.client.features.SlayerCarryManager.stopTracking();
						return 1;
					})
					.then(ClientCommands.argument("player", StringArgumentType.string()).suggests(playerSuggestor)
						.executes(context -> {
							String player = StringArgumentType.getString(context, "player");
							com.wish.client.features.SlayerCarryManager.stopTrackingPlayer(player);
							return 1;
						})
					)
				)
			);

			dispatcher.register(ClientCommands.literal("model")
				.executes(context -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null || mc.player.getMainHandItem().isEmpty()) {
						sendModelEmptyHandWarning();
						return 0;
					}
					mc.execute(() -> mc.gui.setScreen(new com.wish.client.gui.WishModelScreen(mc.gui.screen())));
					return 1;
				})
			);
		});

		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay) return message;
			
			String text = message.getString();
			if (text != null && text.contains("[Wish]")) {
				return message;
			}
			
			return com.wish.client.color.NameColorManager.colorizeText(message, true);
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String text = message.getString();
			if (text == null || text.contains("[Wish]")) return;

			if (!ModConfig.INSTANCE.enableSong) return;

			String lower = text.replace('\u00A0', ' ').toLowerCase().trim();
			if (lower.contains("!song")) {
				String prefix = getChatPrefix(lower);
				if (prefix.equals("/pc ") && ModConfig.INSTANCE.enablePc) {
					fetchAndSendSongWithDelay("/pc ");
				} else if (prefix.equals("/gc ") && ModConfig.INSTANCE.enableGc) {
					fetchAndSendSongWithDelay("/gc ");
				} else if (prefix.equals("/ac ") && ModConfig.INSTANCE.enableAc) {
					fetchAndSendSongWithDelay("/ac ");
				}
			} else {
				checkFunCommands(lower, text);
			}
		});
	}

	private static String getChatPrefix(String text) {
		String lower = text.toLowerCase().trim();
		boolean isParty = lower.contains("party") || lower.contains("p >") || lower.contains("[party]") || lower.startsWith("/pc ") || lower.startsWith("/p ") || lower.startsWith("/chat p");
		boolean isGuild = lower.contains("guild") || lower.contains("officer") || lower.contains("g >") || lower.contains("o >") || lower.contains("[guild]") || lower.startsWith("/gc ") || lower.startsWith("/g ") || lower.startsWith("/o ") || lower.startsWith("/chat g");
		
		if (isParty) return "/pc ";
		if (isGuild) return "/gc ";
		return "/ac ";
	}



	private static void checkFunCommands(String lower, String text) {
		if (lower.contains("!meow") && ModConfig.INSTANCE.enableMeow) processFunCommand(text, "!meow");
		else if (lower.contains("!wanted") && ModConfig.INSTANCE.enableWanted) processFunCommand(text, "!wanted");
		else if (lower.contains("!kiss") && ModConfig.INSTANCE.enableKiss) processFunCommand(text, "!kiss");
		else if (lower.contains("!feed") && ModConfig.INSTANCE.enableFeed) processFunCommand(text, "!feed");
		else if (lower.contains("!poke") && ModConfig.INSTANCE.enablePoke) processFunCommand(text, "!poke");
		else if (lower.contains("!pat") && ModConfig.INSTANCE.enablePat) processFunCommand(text, "!pat");
		else if (lower.contains("!hug") && ModConfig.INSTANCE.enableHug) processFunCommand(text, "!hug");
		else if (lower.contains("!sus") && ModConfig.INSTANCE.enableSus) processFunCommand(text, "!sus");
		else if (lower.contains("!rizz") && ModConfig.INSTANCE.enableRizz) processFunCommand(text, "!rizz");
		else if (lower.contains("!jerry") && ModConfig.INSTANCE.enableJerry) processFunCommand(text, "!jerry");
		else if (lower.contains("!soraka") && ModConfig.INSTANCE.enableSoraka) processFunCommand(text, "!soraka");
		else if (lower.contains("!iq") && ModConfig.INSTANCE.enableIq) processFunCommand(text, "!iq");
		else if (lower.contains("!sleep") && ModConfig.INSTANCE.enableSleep) processFunCommand(text, "!sleep");
		else if (lower.contains("!yuri") && ModConfig.INSTANCE.enableYuri) processFunCommand(text, "!yuri");
	}

	private static long lastCommandTime = 0;

	private static void processFunCommand(String text, String command) {
		long now = System.currentTimeMillis();
		if (now - lastCommandTime < 1500) return; // 1.5 seconds cooldown
		lastCommandTime = now;

		String lower = text.replace('\u00A0', ' ').toLowerCase().trim();
		String explicitTarget = null;
		int cmdIdx = text.toLowerCase().indexOf(command);
		if (cmdIdx != -1 && text.length() > cmdIdx + command.length()) {
			String afterCmd = text.substring(cmdIdx + command.length()).replaceAll("(?i)\\u00A7[0-9a-fk-or]", "").trim();
			if (!afterCmd.isEmpty()) {
				String[] words = afterCmd.split("\\s+");
				String arg = words[0];
				if (arg.matches("^[a-zA-Z0-9_]{2,16}$")) {
					explicitTarget = arg;
					try {
						Minecraft mc = Minecraft.getInstance();
						if (mc.player != null && mc.player.connection != null) {
							for (net.minecraft.client.multiplayer.PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
								if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
									String pName = info.getProfile().name();
									if (pName.toLowerCase().startsWith(arg.toLowerCase())) {
										explicitTarget = pName;
										break;
									}
								}
							}
						}
					} catch (Throwable ignored) {
					}
				}
			}
		}

		String senderName = extractNameFromChat(text);
		Minecraft mc = Minecraft.getInstance();
		String myName = mc.player != null ? mc.player.getScoreboardName() : "Me";

		// The target of the action: explicit target if specified, otherwise the sender
		String target = explicitTarget != null ? explicitTarget : (senderName != null ? senderName : myName);
		// The actor executing the command: the sender who typed it in chat, or me if I typed it
		String actor = senderName != null ? senderName : myName;
		boolean isSelfTarget = actor.equalsIgnoreCase(target);

		String msg = "";
		if (command.equals("!meow")) {
			int pct = new java.util.Random().nextInt(101);
			msg = target + " is " + pct + "% cat... " + (pct > 80 ? "nyaa :3" : (pct < 20 ? "closet barker." : "mrp?"));
		} else if (command.equals("!wanted")) {
			msg = "WANTED: " + target + " for ninjaing Necron's Handle (Bounty: 1 coin and a stick).";
		} else if (command.equals("!kiss")) {
			if (isSelfTarget) {
				msg = actor + " kissed their reflection in the mirror... Narcissist.";
			} else {
				msg = actor + " gave " + target + " a little kiss <3";
			}
		} else if (command.equals("!feed")) {
			if (isSelfTarget) {
				msg = actor + " ate a pack of cookies in secret.";
			} else {
				msg = actor + " fed " + target + " a cookie. Eat and be quiet.";
			}
		} else if (command.equals("!poke")) {
			if (isSelfTarget) {
				msg = actor + " pinched themself. Yeah, you're awake.";
			} else {
				msg = actor + " poked " + target + "! Hey, wake up!";
			}
		} else if (command.equals("!pat")) {
			if (isSelfTarget) {
				msg = actor + " patted their own head. Good job champ.";
			} else {
				msg = actor + " gently patted " + target + "'s head. Good boy/girl.";
			}
		} else if (command.equals("!hug")) {
			if (isSelfTarget) {
				msg = actor + " is hugging a body pillow while crying.";
			} else {
				msg = actor + " gave " + target + " a big warm hug!";
			}
		} else if (command.equals("!sus")) {
			int pct = new java.util.Random().nextInt(101);
			msg = target + " is " + pct + "% sus " + (pct > 75 ? "(vote them out right now)" : (pct < 25 ? "(clean)" : "(kinda iffy..)"));
		} else if (command.equals("!rizz")) {
			int rizz = new java.util.Random().nextInt(101);
			msg = target + " has " + rizz + "% rizz " + (rizz > 85 ? "(W rizz)" : (rizz < 25 ? "(Negative rizz, stop talking)" : ""));
		} else if (command.equals("!jerry")) {
			com.wish.client.features.SorakaModeManager.deactivate();
			com.wish.client.features.JerryModeManager.activate();
			msg = "[Jerry] Jerry has taken full control.";
		} else if (command.equals("!soraka")) {
			com.wish.client.features.JerryModeManager.deactivate();
			com.wish.client.features.SorakaModeManager.activate();
			msg = "[Soraka] Yes, that was a banana. Nobody expects the banana.";
		} else if (command.equals("!iq")) {
			int iq = new java.util.Random().nextInt(180) + 1;
			msg = target + "'s IQ: " + iq + (iq < 50 ? " (can't find the W key)" : (iq > 140 ? " (galaxy brain)" : " (average Skyblock player)"));
		} else if (command.equals("!sleep")) {
			msg = actor + ": 'Wake up, my bed exploded.'";
		} else if (command.equals("!yuri")) {
			String p1 = actor;
			String p2 = target;
			boolean hasTwo = false;
			if (cmdIdx != -1 && text.length() > cmdIdx + command.length()) {
				String afterCmd = text.substring(cmdIdx + command.length()).replaceAll("(?i)\\u00A7[0-9a-fk-or]", "").trim();
				if (!afterCmd.isEmpty()) {
					String[] words = afterCmd.split("\\s+");
					if (words.length >= 2) {
						hasTwo = true;
						p1 = words[0];
						p2 = words[1];
					}
				}
			}
			if (hasTwo) {
				msg = p1 + " & " + p2 + ": " + new java.util.Random().nextInt(101) + "% yuri!";
			} else {
				msg = target + ": " + new java.util.Random().nextInt(101) + "% yuri.";
			}
		}

		String prefix = getChatPrefix(lower);

		final String finalMsg = msg;
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(300);
			} catch (Exception ignored) {}
			if (prefix.equals("/pc ") && ModConfig.INSTANCE.enablePc) {
				sendServerChatMessage("/pc ", finalMsg);
			} else if (prefix.equals("/gc ") && ModConfig.INSTANCE.enableGc) {
				sendServerChatMessage("/gc ", finalMsg);
			} else if (prefix.equals("/ac ") && ModConfig.INSTANCE.enableAc) {
				sendServerChatMessage("/ac ", finalMsg);
			}
		});
	}



	private static String extractNameFromChat(String text) {
		int colonIdx = text.indexOf(':');
		if (colonIdx == -1) return null;
		String beforeColon = text.substring(0, colonIdx);
		beforeColon = beforeColon.replaceAll("(?i)\\u00A7[0-9a-fk-or]", "");
		String noBrackets = beforeColon.replaceAll("\\[.*?\\]", "").trim();
		String[] words = noBrackets.split("\\s+");
		if (words.length > 0) {
			String lastWord = words[words.length - 1];
			if (lastWord.equalsIgnoreCase("Mistress") && words.length > 1) {
				String secondLast = words[words.length - 2];
				if (secondLast.equalsIgnoreCase("the") && words.length > 2) {
					return words[words.length - 3];
				}
				return secondLast;
			}
			return lastWord;
		}
		return null;
	}



	private static void saveToken(String token) {
		ModConfig.INSTANCE.authToken = token;
		ModConfig.INSTANCE.save();
	}

	private static volatile boolean isFetchingState = false;

	private static void fetchAndSendSongWithDelay(String chatPrefix) {
		String provider = ModConfig.INSTANCE.musicProvider;
		if (provider == null || "None".equalsIgnoreCase(provider)) {
			sendLocalChatMessage("§d§l🎵 WISH §8» §cNo music platform selected! Please go to /wish -> Song Config to select your platform.");
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastRequestTime < 2500 || isFetchingState) {
			return;
		}
		lastRequestTime = now;
		isFetchingState = true;

		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(1500); // 1.5s delay as requested

				// 1. Try HTTP Companion API if paired
				boolean apiConnectionFailed = false;
				if (ModConfig.INSTANCE.authToken != null) {
					try {
						HttpURLConnection conn = createAuthenticatedConnection("/state", "GET");
						int responseCode = conn.getResponseCode();
						if (responseCode == 200) {
							String content = readResponse(conn);
							parseAndSendToChat(content, chatPrefix);
							return;
						} else {
							apiConnectionFailed = true;
						}
					} catch (Exception e) {
						apiConnectionFailed = true;
					}
				}

				// 2. Try System Media Session (playerctl on Linux, PowerShell on Windows) - ONLY IF NOT YTM
				if (!"YTM".equalsIgnoreCase(provider)) {
					String winMediaSong = fetchSystemMediaSong();
					if (winMediaSong != null && !winMediaSong.trim().isEmpty()) {
						sendWinMediaSongToChat(winMediaSong, chatPrefix);
						return;
					}
				}

				// 3. Fallback message when no song is playing anywhere
				if ("YTM".equalsIgnoreCase(provider)) {
					if (ModConfig.INSTANCE.authToken == null) {
						sendLocalChatMessage("§e[Wish] §cYou haven't connected your API! Click 'Pair API' in /wish");
					} else if (apiConnectionFailed) {
						sendLocalChatMessage("§e[Wish] §cFailed to connect to YTM API! Is the Companion App open?");
					} else {
						sendLocalChatMessage("§e[Wish] §cNo song currently playing on YTM. Start your music and try again!");
					}
				} else {
					sendLocalChatMessage("§e[Wish] §cNo song currently playing on " + provider + ". Start your music and try again!");
				}
			} catch (Exception e) {
				sendLocalChatMessage("§c[Wish] Error reading music.");
			} finally {
				isFetchingState = false;
			}
		});
	}

	private static String fetchSystemMediaSong() {
		try {
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("linux")) {
				ProcessBuilder pb = new ProcessBuilder("bash", "-c", "playerctl -a metadata --format '{{album}}|||{{title}} - {{artist}}' | grep -v '^|||' | head -n 1");
				Process p = pb.start();
				try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
					String line = br.readLine();
					if (line != null && line.contains("|||")) {
						String[] parts = line.split("\\|\\|\\|");
						if (parts.length > 1) {
							return parts[1].trim();
						}
					}
				} finally {
					if (p.isAlive()) {
						p.destroyForcibly();
					}
				}
			} else if (os.contains("win")) {
				Path scriptPath = Path.of("config", "wish", "get_song.ps1");
				try (InputStream in = WishClient.class.getResourceAsStream("/assets/wish/scripts/get_song.ps1")) {
					if (in != null) {
						Files.createDirectories(scriptPath.getParent());
						Files.copy(in, scriptPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (Exception ignored) {}

				if (!Files.exists(scriptPath)) return null;

				String providerParam = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider : "YTM";
				ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toAbsolutePath().toString(), "-provider", providerParam);
				Process p = pb.start();
				try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
					String line = br.readLine();
					if (line != null && !line.trim().isEmpty()) {
						return line.trim();
					}
				} finally {
					if (p.isAlive()) {
						p.destroyForcibly();
					}
				}
			}
		} catch (Exception ignored) {}
		return null;
	}

	private static String getBaseUrl() {
		String provider = ModConfig.INSTANCE.musicProvider;
		int port = 9863;
		if ("Deezer".equalsIgnoreCase(provider)) {
			port = ModConfig.INSTANCE.deezerPort > 0 ? ModConfig.INSTANCE.deezerPort : 9865;
		} else {
			port = ModConfig.INSTANCE.ytmPort > 0 ? ModConfig.INSTANCE.ytmPort : 9863;
		}
		return "http://127.0.0.1:" + port + "/api/v1";
	}

	public static void requestPairing() {
		String provider = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider : "None";
		if ("None".equalsIgnoreCase(provider)) {
			sendLocalChatMessage("§d§l🎵 WISH §8» §cPlease select a platform (YouTube Music, Spotify, or Deezer) in /wish -> Song Config first!");
			return;
		}
		if ("Deezer".equalsIgnoreCase(provider) || "Spotify".equalsIgnoreCase(provider)) {
			sendLocalChatMessage("§d§l🎵 WISH §8» §a" + provider + " uses native Windows auto-detection. Play your music on " + provider + "!");
			return;
		}
		if (isPairingActive) {
			sendLocalChatMessage("§e[Wish] §fPairing in progress... Allow the connection in " + provider + "!");
			return;
		}
		isPairingActive = true;

		CompletableFuture.runAsync(() -> {
			try {
				URL url = URI.create(getBaseUrl() + "/auth/requestcode").toURL();
				HttpURLConnection conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				conn.setConnectTimeout(2000);
				conn.setReadTimeout(2000);
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setRequestProperty("User-Agent", "Wish/1.0.0");

				JsonObject jsonBody = new JsonObject();
				jsonBody.addProperty("appId", APP_ID);
				jsonBody.addProperty("appName", "Wish");
				jsonBody.addProperty("appVersion", "1.0.0");

				try (OutputStream os = conn.getOutputStream()) {
					os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
				}

				if (conn.getResponseCode() == 200) {
					String respStr = readResponse(conn);
					JsonObject respJson = JsonParser.parseString(respStr).getAsJsonObject();
					if (respJson.has("code")) {
						String code = respJson.get("code").getAsString();
						sendLocalChatMessage("§d§l🎵 WISH §8» §fCode: §e§l" + code + " §8(§aApprove in " + provider + " popup!§8)");
						exchangeCodeForToken(code);
						return;
					}
				} else {
					sendLocalChatMessage("§d§l🎵 WISH §8» §cFailed to request " + provider + " (code " + conn.getResponseCode() + ")");
				}
			} catch (Exception e) {
				sendLocalChatMessage("§d§l🎵 WISH §8» §cError: Make sure " + provider + " application is open!");
			}
			isPairingActive = false;
		});
	}

	private static void exchangeCodeForToken(String code) {
		CompletableFuture.runAsync(() -> {
			try {
				for (int i = 0; i < 18; i++) {
					try {
						Thread.sleep(2500);
						URL url = URI.create(getBaseUrl() + "/auth/request").toURL();
						HttpURLConnection conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
						conn.setRequestMethod("POST");
						conn.setDoOutput(true);
						conn.setConnectTimeout(2000);
						conn.setReadTimeout(2000);
						conn.setRequestProperty("Content-Type", "application/json");
						conn.setRequestProperty("User-Agent", "Wish/1.0.0");

						JsonObject jsonBody = new JsonObject();
						jsonBody.addProperty("appId", APP_ID);
						jsonBody.addProperty("code", code);

						try (OutputStream os = conn.getOutputStream()) {
							os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
						}

						if (conn.getResponseCode() == 200) {
							String respStr = readResponse(conn);
							JsonObject respJson = JsonParser.parseString(respStr).getAsJsonObject();
							if (respJson.has("token")) {
								String token = respJson.get("token").getAsString();
								saveToken(token);
								sendLocalChatMessage("§d§l🎵 WISH §8» §a§lSuccessfully paired with YouTube Music!");
								return;
							}
						}
					} catch (Exception ignored) {
					}
				}
				sendLocalChatMessage("§d§l🎵 WISH §8» §cAuthorization timeout. Type !song again.");
			} finally {
				isPairingActive = false;
			}
		});
	}

	private static HttpURLConnection createAuthenticatedConnection(String endpoint, String method) throws Exception {
		URL url = URI.create(getBaseUrl() + endpoint).toURL();
		HttpURLConnection conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
		conn.setRequestMethod(method);
		conn.setConnectTimeout(1000);
		conn.setReadTimeout(1000);
		conn.setRequestProperty("User-Agent", "Wish/1.0.0");
		if (ModConfig.INSTANCE.authToken != null) {
			conn.setRequestProperty("Authorization", ModConfig.INSTANCE.authToken);
		}
		return conn;
	}

	private static String readResponse(HttpURLConnection conn) throws Exception {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder content = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line);
			}
			return content.toString();
		}
	}

	private static void parseAndSendToChat(String jsonBody, String chatPrefix) {
		try {
			JsonObject json = JsonParser.parseString(jsonBody).getAsJsonObject();
			String title = "";
			String artist = "";

			if (json.has("video") && !json.get("video").isJsonNull()) {
				JsonObject video = json.getAsJsonObject("video");
				if (video.has("title") && !video.get("title").isJsonNull()) title = video.get("title").getAsString();
				if (video.has("author") && !video.get("author").isJsonNull()) artist = video.get("author").getAsString();
				if (artist.isEmpty() && video.has("artist") && !video.get("artist").isJsonNull()) artist = video.get("artist").getAsString();
			}

			if (title.isEmpty() && json.has("player") && !json.get("player").isJsonNull()) {
				JsonObject player = json.getAsJsonObject("player");
				if (player.has("track") && !player.get("track").isJsonNull()) {
					JsonObject track = player.getAsJsonObject("track");
					if (track.has("title") && !track.get("title").isJsonNull()) title = track.get("title").getAsString();
					if (track.has("author") && !track.get("author").isJsonNull()) artist = track.get("author").getAsString();
					if (artist.isEmpty() && track.has("artist") && !track.get("artist").isJsonNull()) artist = track.get("artist").getAsString();
				}
			}

			if (title.isEmpty() && json.has("title") && !json.get("title").isJsonNull()) {
				title = json.get("title").getAsString();
				if (json.has("artist") && !json.get("artist").isJsonNull()) artist = json.get("artist").getAsString();
				if (artist.isEmpty() && json.has("author") && !json.get("author").isJsonNull()) artist = json.get("author").getAsString();
			}

			if (!title.isEmpty()) {
				currentSongText = "§f🎵 §b" + title + (artist.isEmpty() ? "" : " §7- " + artist);
				String format = ModConfig.INSTANCE.messageFormat;
				if (format == null || format.trim().isEmpty()) {
					format = "{title} - {artist}";
				}
				String body = format.replace("{title}", title).replace("{artist}", artist);
				sendServerChatMessage(chatPrefix, body);
				return;
			}
		} catch (Exception ignored) {}

		if (!"YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)) {
			String winMediaSong = fetchSystemMediaSong();
			if (winMediaSong != null && !winMediaSong.trim().isEmpty()) {
				sendWinMediaSongToChat(winMediaSong, chatPrefix);
				return;
			}
		}

		sendLocalChatMessage("§e[Wish] §cNo song currently playing.");
	}

	private static void sendWinMediaSongToChat(String winMediaSong, String chatPrefix) {
		currentSongText = "§f🎵 §b" + winMediaSong;
		String format = ModConfig.INSTANCE.messageFormat;
		if (format == null || format.trim().isEmpty()) {
			format = "{title} - {artist}";
		}
		String title = winMediaSong;
		String artist = "";
		if (winMediaSong.contains(" - ")) {
			String[] parts = winMediaSong.split(" - ", 2);
			title = parts[0].trim();
			artist = parts[1].trim();
		}
		String body = format.replace("{title}", title).replace("{artist}", artist);
		sendServerChatMessage(chatPrefix, body);
	}

	public static void sendLocalChatMessage(String message) {
		String cleanMsg = message.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "")
			.replace("🎵 WISH » ", "")
			.replace("[Wish] ", "")
			.replace("[CarryTracker] ", "")
			.trim();

		net.minecraft.network.chat.MutableComponent comp = net.minecraft.network.chat.Component.literal("[Wish] ")
			.withStyle(net.minecraft.network.chat.Style.EMPTY
				.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xffc6f9))
				.withBold(true))
			.append(net.minecraft.network.chat.Component.literal(cleanMsg)
				.withStyle(net.minecraft.network.chat.Style.EMPTY
					.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x55ff55))
					.withBold(false)));

		sendLocalChatMessage(comp);
	}

	public static void sendModelEmptyHandWarning() {
		net.minecraft.network.chat.MutableComponent comp = net.minecraft.network.chat.Component.literal("[Wish] ")
			.withStyle(net.minecraft.network.chat.Style.EMPTY
				.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xffc6f9))
				.withBold(true))
			.append(net.minecraft.network.chat.Component.literal("Please hold an item in your main hand to customize its model!")
				.withStyle(net.minecraft.network.chat.Style.EMPTY
					.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xff5555))
					.withBold(false)));
		sendLocalChatMessage(comp);
	}

	public static void sendLocalChatMessage(net.minecraft.network.chat.Component component) {
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
