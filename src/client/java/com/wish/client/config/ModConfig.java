package com.wish.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wish.client.util.WishPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class ModConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Config");
	private static final Path CONFIG_DIR = WishPaths.getConfigDir();
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
	private static final Path OLD_CONFIG_FILE = WishPaths.getLegacyConfigFile("wish_config.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CURRENT_SCHEMA_VERSION = 2;

	public static ModConfig INSTANCE = new ModConfig();

	public int configVersion = 0;
	public String ignoredUpdateVersion = "";

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
	public boolean enableBadge = false;
	public String spoofedName = "";
	public int pseudoFont = 0;
	public int pseudoAnimation = 0;
	public float pseudoAnimationSpeed = 1.00f;
	public float playerSizeX = 1.00f;
	public float playerSizeY = 1.00f;
	public float playerSizeZ = 1.00f;
	public boolean playerSizeEnabled = true;
	public int cosmeticVisibility = 0;

	public int selectedWing = 0;
	public int selectedHalo = 0;
	public int selectedParticle = 0;
	public boolean cosmeticCustomColorEnabled = false;
	public String cosmeticCustomColor = "#FF55FF";
	public boolean enableCosmeticAutoSync = true;
	public String cosmeticSyncApiUrl = "";

	public boolean enablePlayerSpin = false;
	public float playerSpinSpeedY = 0.00f;

	public boolean enableCustomBlocksF7M7 = true;
	public boolean enableGlassBlocksF7M7 = true;
	public boolean enableLagTimeLost = true;
	public boolean sendLagTimeLost = false;
	public int lagHudX = 10;
	public int lagHudY = 10;
	public float lagHudScale = 1.0f;
	public boolean enableUpdateCheck = true;
	public String customHexColor = "#FFFFFF";
	public String customHexColor2 = "#FFFFFF";
	public String menuAccentColor = "#FFFFFF";
	public boolean alwaysInM7F7 = false;
	public boolean enableCustomItemModels = true;
	public java.util.Map<String, String> itemModelOverrides = new ConcurrentHashMap<>();

	public boolean enableMiningAutomaton = true;
	public boolean enableAutomatonTracker = true;
	public boolean enablePrecursorCityScanner = true;
	public boolean enableAutomatonGlow = true;
	public String automatonGlowColor = "#FFFFFF";
	public int automatonHudX = 10;
	public int automatonHudY = 60;
	public float automatonHudScale = 1.0f;
	public boolean automatonUseInstaSell = false;
	public boolean automatonHideSackChat = false;
	public boolean automatonSackMode = true;
	public boolean automatonShowTotalStats = false;
	public int automatonTotalKills = 0;
	public long automatonTotalActiveMs = 0;
	public long automatonTotalActiveSeconds = 0;
	public java.util.Map<String, Integer> automatonTotalDrops = new ConcurrentHashMap<>();
	public int automatonPauseTimeout = 10;

	public boolean enableMineshaftPortalGlow = true;
	public String mineshaftPortalGlowColor = "#AA55FF";

	public boolean enableLyrics = false;
	public int lyricsMode = 0;
	public String lyricsColor = "#FFFFFF";
	public float lyricsAlpha = 1.0f;
	public float lyricsScale = 3.0f;
	public int lyricsSyncOffsetMs = 0;
	public boolean lyricsDemoMode = false;

	public boolean enableCustomTime = false;
	public int customTimeTicks = 6000;
	public boolean customTimeFrozen = true;

	public boolean enableCustomWeather = false;
	public int customWeatherMode = 0;
	public float customWeatherCount = 1.0f;
	public float customWeatherSize = 1.0f;
	public boolean customWeatherWind = true;
	public float customWeatherWindStrength = 1.0f;
	public boolean customWeatherThroughWalls = false;

	public boolean enableCustomSky = false;
	public int customSkyMode = 0;
	public boolean customSkyRotate = false;

	public boolean enableSlayerCarry = true;
	public boolean enableBossSpawnHud = true;
	public int bossHudX = 200;
	public int bossHudY = 60;
	public float bossHudScale = 1.0f;
	public boolean enableSlayerGlow = true;
	public boolean enableSlayerAutoCoords = true;
	public String slayerGlowColor = "#FF55AA";
	public boolean customSkyShowStars = true;
	public boolean customSkyShowSunMoon = true;

	public boolean enableOtherPlayersSize = false;
	public boolean smolPeopleIncludeSelf = false;

	public boolean enableCatPawTrails = false;
	public boolean catPawTrailIncludeOthers = false;
	public String catPawTrailColor = "#FFB0DE";
	public float catPawTrailScale = 1.0f;
	public float catPawTrailDuration = 3.0f;

	public boolean enableWordReplacer = false;
	public boolean wordReplacerWholeWord = true;
	public boolean wordReplacerCaseSensitive = false;
	public java.util.List<WordReplacementRule> wordReplacements = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static class WordReplacementRule {
		public String from = "";
		public String to = "";
		public boolean enabled = true;

		public WordReplacementRule() {
		}

		public WordReplacementRule(String from, String to, boolean enabled) {
			this.from = from != null ? from : "";
			this.to = to != null ? to : "";
			this.enabled = enabled;
		}
	}

	private static final java.util.concurrent.ScheduledExecutorService SAVE_EXECUTOR =
			java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "Wish-Config-Save");
				t.setDaemon(true);
				return t;
			});
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
						INSTANCE.itemModelOverrides = new ConcurrentHashMap<>();
					} else {
						INSTANCE.itemModelOverrides = new ConcurrentHashMap<>(INSTANCE.itemModelOverrides);
					}
					if (INSTANCE.automatonTotalDrops == null) {
						INSTANCE.automatonTotalDrops = new ConcurrentHashMap<>();
					} else {
						INSTANCE.automatonTotalDrops = new ConcurrentHashMap<>(INSTANCE.automatonTotalDrops);
					}
					INSTANCE.wordReplacements = com.wish.client.features.visual.WordReplacerManager.sanitizeRules(INSTANCE.wordReplacements);
					com.wish.client.features.visual.WordReplacerManager.invalidateCache();
					if (INSTANCE.automatonTotalActiveMs == 0 && INSTANCE.automatonTotalActiveSeconds > 0) {
						INSTANCE.automatonTotalActiveMs = INSTANCE.automatonTotalActiveSeconds * 1000L;
					}
					INSTANCE.lyricsDemoMode = false;
					if (INSTANCE.lyricsScale <= 0.1f) {
						INSTANCE.lyricsScale = 3.0f;
					}
					if (INSTANCE.spoofedName == null) {
						INSTANCE.spoofedName = "";
					} else if (INSTANCE.spoofedName.length() > 32) {
						INSTANCE.spoofedName = INSTANCE.spoofedName.substring(0, 32);
					}
					if (INSTANCE.lyricsSyncOffsetMs == -500 || INSTANCE.lyricsSyncOffsetMs == 1000) {
						INSTANCE.lyricsSyncOffsetMs = 0;
					}
					if (INSTANCE.lyricsColor == null || !INSTANCE.lyricsColor.startsWith("#") || INSTANCE.lyricsColor.length() != 7) {
						INSTANCE.lyricsColor = "#FFFFFF";
					}
					if (Float.isNaN(INSTANCE.lyricsAlpha) || INSTANCE.lyricsAlpha < 0.0f || INSTANCE.lyricsAlpha > 1.0f) {
						INSTANCE.lyricsAlpha = Float.isNaN(INSTANCE.lyricsAlpha) ? 1.0f : Math.max(0.0f, Math.min(1.0f, INSTANCE.lyricsAlpha));
					}
					if (INSTANCE.customSkyMode < 0 || INSTANCE.customSkyMode >= com.wish.client.features.visual.CustomSkyManager.MODE_NAMES.length) {
						INSTANCE.customSkyMode = 0;
					}
					if (Float.isNaN(INSTANCE.customWeatherCount) || INSTANCE.customWeatherCount < 0.1f || INSTANCE.customWeatherCount > 3.0f) {
						INSTANCE.customWeatherCount = Float.isNaN(INSTANCE.customWeatherCount) ? 1.0f : Math.max(0.1f, Math.min(3.0f, INSTANCE.customWeatherCount));
					}
					if (Float.isNaN(INSTANCE.customWeatherSize) || INSTANCE.customWeatherSize < 0.25f || INSTANCE.customWeatherSize > 2.5f) {
						INSTANCE.customWeatherSize = Float.isNaN(INSTANCE.customWeatherSize) ? 1.0f : Math.max(0.25f, Math.min(2.5f, INSTANCE.customWeatherSize));
					}
					if (Float.isNaN(INSTANCE.customWeatherWindStrength) || INSTANCE.customWeatherWindStrength < 0.25f || INSTANCE.customWeatherWindStrength > 2.0f) {
						INSTANCE.customWeatherWindStrength = Float.isNaN(INSTANCE.customWeatherWindStrength) ? 1.0f : Math.max(0.25f, Math.min(2.0f, INSTANCE.customWeatherWindStrength));
					}
					int parsedPortalRgb = com.wish.client.color.ColorUtils.parseHexRgb(INSTANCE.mineshaftPortalGlowColor, -1);
					INSTANCE.mineshaftPortalGlowColor = parsedPortalRgb >= 0
							? com.wish.client.color.ColorUtils.formatHexRgb(parsedPortalRgb)
							: "#AA55FF";
					int parsedCatPawRgb = (INSTANCE.catPawTrailColor != null && INSTANCE.catPawTrailColor.startsWith("#") && INSTANCE.catPawTrailColor.length() <= 7)
							? com.wish.client.color.ColorUtils.parseHexRgb(INSTANCE.catPawTrailColor, -1)
							: -1;
					INSTANCE.catPawTrailColor = parsedCatPawRgb >= 0
							? com.wish.client.color.ColorUtils.formatHexRgb(parsedCatPawRgb)
							: "#FFB0DE";
					if (Float.isNaN(INSTANCE.catPawTrailScale) || INSTANCE.catPawTrailScale <= 0.1f) {
						INSTANCE.catPawTrailScale = 1.0f;
					} else {
						INSTANCE.catPawTrailScale = Math.max(0.5f, Math.min(2.0f, INSTANCE.catPawTrailScale));
					}
					if (Float.isNaN(INSTANCE.catPawTrailDuration) || INSTANCE.catPawTrailDuration <= 0.1f) {
						INSTANCE.catPawTrailDuration = 3.0f;
					} else {
						INSTANCE.catPawTrailDuration = Math.max(1.0f, Math.min(6.0f, INSTANCE.catPawTrailDuration));
					}
					INSTANCE.selectedWing = com.wish.client.features.cosmetic.PlayerCosmeticsManager.clampId(INSTANCE.selectedWing);
					INSTANCE.selectedHalo = com.wish.client.features.cosmetic.PlayerCosmeticsManager.clampId(INSTANCE.selectedHalo);
					INSTANCE.selectedParticle = com.wish.client.features.cosmetic.PlayerCosmeticsManager.clampId(INSTANCE.selectedParticle);
					int parsedCosmeticRgb = (INSTANCE.cosmeticCustomColor != null && INSTANCE.cosmeticCustomColor.startsWith("#") && INSTANCE.cosmeticCustomColor.length() <= 7)
							? com.wish.client.color.ColorUtils.parseHexRgb(INSTANCE.cosmeticCustomColor, -1)
							: -1;
					INSTANCE.cosmeticCustomColor = parsedCosmeticRgb >= 0
							? com.wish.client.color.ColorUtils.formatHexRgb(parsedCosmeticRgb)
							: "#FF55FF";
					INSTANCE.cosmeticSyncApiUrl = INSTANCE.cosmeticSyncApiUrl != null ? INSTANCE.cosmeticSyncApiUrl.trim() : "";
					if (INSTANCE.ignoredUpdateVersion == null) {
						INSTANCE.ignoredUpdateVersion = "";
					} else {
						INSTANCE.ignoredUpdateVersion = INSTANCE.ignoredUpdateVersion.trim();
					}
					if (INSTANCE.configVersion < CURRENT_SCHEMA_VERSION) {
						INSTANCE.configVersion = CURRENT_SCHEMA_VERSION;
						INSTANCE.save();
					}

					if (toRead.equals(OLD_CONFIG_FILE)) {
						INSTANCE.saveImmediately();
					}
				}
			} else {
				INSTANCE.configVersion = CURRENT_SCHEMA_VERSION;
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load config file", e);
			INSTANCE = new ModConfig();
		}
	}

	public void save() {
		com.wish.client.features.cosmetic.CosmeticSyncManager.notifyLocalCosmeticsChanged();
		synchronized (SAVE_LOCK) {
			if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
				pendingSaveTask.cancel(false);
			}
			pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
				synchronized (SAVE_LOCK) {
					try {
						String json = GSON.toJson(this);
						WishPaths.writeStringAtomically(CONFIG_FILE, json);
					} catch (Exception e) {
						LOGGER.error("Failed to save config asynchronously", e);
					}
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
				String json = GSON.toJson(this);
				WishPaths.writeStringAtomically(CONFIG_FILE, json);
			} catch (Exception e) {
				LOGGER.error("Failed to save config immediately", e);
			}
		}
	}
}
