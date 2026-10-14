package com.wish.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Config");
	private static final Path CONFIG_DIR = Path.of("config", "wish");
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
	private static final Path OLD_CONFIG_FILE = Path.of("config", "wish_config.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static ModConfig INSTANCE = new ModConfig();

	public String messageFormat = "{title} - {artist}";
	public String musicProvider = "None"; 
	public int ytmPort = 9863;
	public int deezerPort = 9865;
	public String authToken = null;
	public boolean enableAc = true; 
	public boolean enableGc = true; 
	public boolean enablePc = true; 
	public boolean enableSong = true; 
	public boolean enableMeow = true; 
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
	public boolean enableNameColor = true; 
	public boolean enableF5NameTag = true; 
	public String customPrefix = "";
	public String customSuffix = "";
	public int pseudoFont = 0; 
	public int pseudoAnimation = 0; 
	public float pseudoAnimationSpeed = 1.00f; 
	public float playerSizeX = 1.00f;
	public float playerSizeY = 1.00f;
	public float playerSizeZ = 1.00f;
	public boolean playerSizeEnabled = true;
	public int cosmeticVisibility = 0; 
	
	public boolean enablePlayerSpin = false;
	public float playerSpinSpeedX = 0.0f;
	public float playerSpinSpeedY = 0.00f;
	public float playerSpinSpeedZ = 0.0f;
	
	public boolean enableCustomBlocksF7M7 = true;
	public boolean enableLagTimeLost = true;
	public boolean sendLagTimeLost = false;
	public int lagHudX = 10;
	public int lagHudY = 10;
	public float lagHudScale = 1.0f;
	public boolean enableUpdateCheck = true;
	public String customHexColor = "#FFFFFF"; 
	public String customHexColor2 = "#FFFFFF"; 
	public boolean enableGradient = true; 
	public boolean enableSlayerCarry = true;
	public boolean enableBossSpawnHud = true;
	public String slayerGlowColor = "#FFFFFF";
	public String menuAccentColor = "#FFFFFF"; 
	public int bossHudX = 100;
	public int bossHudY = 50;
	public float bossHudScale = 2.0f;
	public boolean alwaysInM7F7 = false;
	public boolean enableCustomItemModels = true;
	public java.util.Map<String, String> itemModelOverrides = new java.util.HashMap<>();

	
	public boolean enableMiningAutomaton = true;
	public boolean enableAutomatonTracker = true;
	public boolean enablePrecursorCityScanner = true;
	public boolean enableAutomatonGlow = true;
	public String automatonGlowColor = "#FFFFFF";
	public boolean enableAutomatonProfitTracker = true;
	public boolean enableAutomatonKillsHud = true;
	public boolean enableAutomatonDropsDetail = false;
	public boolean enableAutomatonSoundAlert = true;
	public int automatonHudX = 10;
	public int automatonHudY = 60;
	public float automatonHudScale = 1.0f;
	public boolean automatonUseInstaSell = false;
	public boolean automatonHideSackChat = false;
	public boolean automatonSendDropChat = false;
	public boolean automatonSackMode = true; 
	public boolean automatonTrackInventoryDrops = false;
	public boolean automatonShowTotalStats = false;
	public int automatonTotalKills = 0;
	public long automatonTotalActiveMs = 0;
	public long automatonTotalActiveSeconds = 0;
	public java.util.Map<String, Integer> automatonTotalDrops = new java.util.HashMap<>();

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
					if (INSTANCE.automatonTotalDrops == null) {
						INSTANCE.automatonTotalDrops = new java.util.HashMap<>();
					}
					if (INSTANCE.automatonTotalActiveMs == 0 && INSTANCE.automatonTotalActiveSeconds > 0) {
						INSTANCE.automatonTotalActiveMs = INSTANCE.automatonTotalActiveSeconds * 1000L;
					}
					
					if (toRead.equals(OLD_CONFIG_FILE)) {
						INSTANCE.saveImmediately();
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load config file", e);
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
					LOGGER.error("Failed to save config asynchronously", e);
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
				LOGGER.error("Failed to save config immediately", e);
			}
		}
	}
}
