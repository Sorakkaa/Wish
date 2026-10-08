package com.wish.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

	private static final Path CONFIG_DIR = Path.of("config", "wish");
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
	private static final Path OLD_CONFIG_FILE = Path.of("config", "wish_config.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static ModConfig INSTANCE = new ModConfig();

	public String messageFormat = "{title} - {artist}";
	public String musicProvider = "None"; // "None", "YTM", "Spotify", "Deezer"
	public int ytmPort = 9863;
	public int deezerPort = 9865;
	public String authToken = null;
	public boolean enableAc = true; // /ac ou /a (All Chat / Public Hypixel)
	public boolean enableGc = true; // /gc ou /g (Guild Chat)
	public boolean enablePc = true; // /pc ou /p (Party Chat)
	public boolean enableSong = true; // commande !song activée
	public boolean enableMeow = true; // commande !meow activée
	public boolean enableWanted = true;
	public boolean enableKiss = true;
	public boolean enableFeed = true;
	public boolean enablePoke = true;
	public boolean enablePat = true;
	public boolean enableHug = true;
	public boolean enableSus = true;
	public boolean enableRizz = true;
	public boolean enableJerry = true;
	public boolean enableSoraka = true;
	public boolean enableIq = true;
	public boolean enableSleep = true;
	public boolean enableYuri = true;
	public boolean enableNameColor = true; // couleur du pseudo activée
	public boolean enableF5NameTag = true; // afficher notre propre nametag en f5
	public String customPrefix = "";
	public String customSuffix = "";
	public int pseudoFont = 0; // 0=gras, 1=normal, 2=italique
	public int pseudoAnimation = 0; // 0=none, 1=chroma
	public float pseudoAnimationSpeed = 1.0f; // Multiplier for animation speed
	public float playerSizeX = 1.0f;
	public float playerSizeY = 1.0f;
	public float playerSizeZ = 1.0f;
	public boolean playerSizeEnabled = true;
	public int cosmeticVisibility = 0; // 0 = Show All, 1 = Mine Only, 2 = Hide All
	
	public boolean enablePlayerSpin = false;
	public float playerSpinSpeedX = 0.0f;
	public float playerSpinSpeedY = 1.0f;
	public float playerSpinSpeedZ = 0.0f;
	
	public boolean enableCustomBlocksF7M7 = true;
	public boolean enableLagTimeLost = true;
	public boolean sendLagTimeLost = false;
	public int lagHudX = 10;
	public int lagHudY = 10;
	public float lagHudScale = 1.0f;
	public boolean enableUpdateCheck = true;
	public String customHexColor = "#FF55AA"; // couleur Hex personnalisée (ex: #FF55AA)
	public String customHexColor2 = "#55FFFF"; // deuxième couleur pour le dégradé
	public boolean enableGradient = false; // activer le dégradé à 2 couleurs
	public boolean enableSlayerCarry = true;
	public boolean enableBossSpawnHud = true;
	public String slayerGlowColor = "#FF55AA";
	public String menuAccentColor = "#9D00FF"; // Couleur du thème du menu
	public int bossHudX = 100;
	public int bossHudY = 50;
	public float bossHudScale = 2.0f;
	public boolean alwaysInM7F7 = false;
	public long lastDiscordWebhookSentTime = 0;
	public String discordWebhookUrl = "";
	public boolean enableCustomItemModels = true;
	public java.util.Map<String, String> itemModelOverrides = new java.util.HashMap<>();

	private static final java.util.concurrent.ScheduledExecutorService SAVE_EXECUTOR = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
	private static java.util.concurrent.ScheduledFuture<?> pendingSaveTask = null;
	private static final Object SAVE_LOCK = new Object();

	public static void load() {
		try {
			Files.createDirectories(CONFIG_DIR);
			Path toRead = null;
			if (Files.exists(CONFIG_FILE)) {
				toRead = CONFIG_FILE;
			} else if (Files.exists(OLD_CONFIG_FILE)) {
				toRead = OLD_CONFIG_FILE;
			}

			if (toRead != null) {
				String json = Files.readString(toRead, StandardCharsets.UTF_8);
				ModConfig loaded = GSON.fromJson(json, ModConfig.class);
				if (loaded != null) {
					INSTANCE = loaded;
					if (INSTANCE.itemModelOverrides == null) {
						INSTANCE.itemModelOverrides = new java.util.HashMap<>();
					}
					// If read from old file, save to new path
					if (toRead.equals(OLD_CONFIG_FILE)) {
						INSTANCE.saveImmediately();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			INSTANCE = new ModConfig();
		}
	}

	public void save() {
		synchronized (SAVE_LOCK) {
			if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
				pendingSaveTask.cancel(false);
			}
			pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
				try {
					Files.createDirectories(CONFIG_FILE.getParent());
					String json = GSON.toJson(this);
					Files.writeString(CONFIG_FILE, json, StandardCharsets.UTF_8);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}, 80, java.util.concurrent.TimeUnit.MILLISECONDS);
		}
	}

	public void saveImmediately() {
		synchronized (SAVE_LOCK) {
			if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
				pendingSaveTask.cancel(false);
			}
			try {
				Files.createDirectories(CONFIG_FILE.getParent());
				String json = GSON.toJson(this);
				Files.writeString(CONFIG_FILE, json, StandardCharsets.UTF_8);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
