package com.wish.client.color;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.wish.client.WishClient;
import com.wish.client.features.cosmetic.PlayerCosmeticsManager;
import com.wish.client.util.HttpClientUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class CustomPlayerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-CustomPlayers");

    private static final String REMOTE_URL = "https://raw.githubusercontent.com/Sorakkaa/Wish/main/custom_players.json";
    private static final String MOJANG_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long JOIN_COOLDOWN_MS = 60 * 1000L;
    private static final long LIVE_SYNC_INTERVAL_MS = 500L;
    private static final long MOJANG_CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final long MOJANG_RETRY_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final Pattern VALID_MC_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern LEGACY_COLOR_STRIP_PATTERN = Pattern.compile("\u00A7.");

    public record CustomPlayerData(
            UUID uuid,
            String color,
            int font,
            double animSpeed,
            boolean badge,
            String badgeIcon,
            float scaleX,
            float scaleY,
            float scaleZ,
            float spinSpeedY,
            int wingId,
            int haloId,
            int particleId,
            String cosmeticColor,
            String spoofedName
    ) {
        public CustomPlayerData(
                UUID uuid,
                String color,
                int font,
                double animSpeed,
                boolean badge,
                String badgeIcon,
                float scaleX,
                float scaleY,
                float scaleZ,
                float spinSpeedY
        ) {
            this(uuid, color, font, animSpeed, badge, badgeIcon, scaleX, scaleY, scaleZ, spinSpeedY, 0, 0, 0, null, "");
        }

        public CustomPlayerData(
                UUID uuid,
                String color,
                int font,
                double animSpeed,
                boolean badge,
                String badgeIcon,
                float scaleX,
                float scaleY,
                float scaleZ,
                float spinSpeedY,
                int wingId,
                int haloId,
                int particleId,
                String cosmeticColor
        ) {
            this(uuid, color, font, animSpeed, badge, badgeIcon, scaleX, scaleY, scaleZ, spinSpeedY,
                    wingId, haloId, particleId, cosmeticColor, "");
        }
    }

    public record SyncedCosmeticRecord(
            UUID uuid,
            String username,
            int wingId,
            int haloId,
            int particleId,
            String cosmeticColor,
            String color,
            Integer font,
            Double animSpeed,
            String spoofedName,
            Float scaleX,
            Float scaleY,
            Float scaleZ,
            Float spinSpeedY
    ) {
        public SyncedCosmeticRecord(
                UUID uuid,
                String username,
                int wingId,
                int haloId,
                int particleId,
                String cosmeticColor
        ) {
            this(uuid, username, wingId, haloId, particleId, cosmeticColor,
                    null, null, null, null, null, null, null, null);
        }
    }

    private record SyncedCosmeticState(
            int wingId,
            int haloId,
            int particleId,
            String cosmeticColor,
            String color,
            Integer font,
            Double animSpeed,
            String spoofedName,
            Float scaleX,
            Float scaleY,
            Float scaleZ,
            Float spinSpeedY
    ) {
    }

    public record ResolvedCustomPlayer(String liveName, CustomPlayerData data) {
    }

    private record MojangNameEntry(String name, long fetchedAtMs) {
    }

    private static volatile Map<UUID, CustomPlayerData> repoPlayers = Collections.emptyMap();
    private static volatile Map<UUID, CustomPlayerData> byUuid = Collections.emptyMap();
    private static volatile List<CustomPlayerData> allPlayers = Collections.emptyList();
    private static volatile Map<String, CustomPlayerData> resolvedByNameLower = Collections.emptyMap();
    private static volatile List<ResolvedCustomPlayer> resolvedPlayers = Collections.emptyList();

    private static final ConcurrentHashMap<UUID, SyncedCosmeticState> syncedCosmeticsByUuid = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> liveNamesByUuid = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, MojangNameEntry> mojangNamesByUuid = new ConcurrentHashMap<>();

    private static volatile long lastFetchTimeMs = 0L;
    private static volatile long lastLiveSyncMs = 0L;
    private static final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
    private static final AtomicBoolean mojangResolveInProgress = new AtomicBoolean(false);
    private static volatile boolean initialized = false;

    private CustomPlayerManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        loadEmbeddedFallback();
        loadDevFileIfPresent();
        fetchAsync();
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        syncLiveBindings(mc, false);
        long now = System.currentTimeMillis();
        if (now - lastFetchTimeMs >= REFRESH_INTERVAL_MS && !fetchInProgress.get()) {
            fetchAsync();
        }
    }

    public static void onPlayerJoin() {
        liveNamesByUuid.clear();
        Minecraft mc = Minecraft.getInstance();
        syncLiveBindings(mc, true);
        long now = System.currentTimeMillis();
        if (now - lastFetchTimeMs >= JOIN_COOLDOWN_MS && !fetchInProgress.get()) {
            fetchAsync();
        }
    }

    public static synchronized void onDisconnect() {
        lastLiveSyncMs = 0L;
        boolean hadLive = !liveNamesByUuid.isEmpty();
        boolean hadSynced = !syncedCosmeticsByUuid.isEmpty();
        liveNamesByUuid.clear();
        syncedCosmeticsByUuid.clear();
        if (hadSynced) {
            recomputeMergedPlayersLocked();
        } else if (hadLive) {
            rebuildResolvedBindings();
        }
    }

    private static void loadEmbeddedFallback() {
        try (InputStream is = CustomPlayerManager.class.getResourceAsStream("/assets/wish/custom_players.json")) {
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                parseAndApply(json, false);
            }
        } catch (Exception e) {
            LOGGER.warn("[Wish] Failed to load embedded custom_players.json fallback", e);
        }
    }

    private static boolean loadDevFileIfPresent() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return false;
        }
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path[] candidates = new Path[]{
                    gameDir.resolve("../custom_players.json").normalize(),
                    gameDir.resolve("custom_players.json").normalize()
            };
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate)) {
                    String json = Files.readString(candidate, StandardCharsets.UTF_8);
                    if (parseAndApply(json, true)) {
                        LOGGER.debug("[Wish] Loaded custom_players.json from local dev workspace: {}", candidate);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Wish] Could not read dev custom_players.json", e);
        }
        return false;
    }

    public static void fetchAsync() {
        if (!fetchInProgress.compareAndSet(false, true)) return;
        lastFetchTimeMs = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String urlWithCacheBust = REMOTE_URL + "?t=" + (System.currentTimeMillis() / 30000L);
                URL url = URI.create(urlWithCacheBust).toURL();
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Wish-Client/" + WishClient.MOD_VERSION);
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                conn.setRequestProperty("Pragma", "no-cache");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String body = HttpClientUtils.readBoundedUtf8(is, HttpClientUtils.DEFAULT_MAX_BYTES);
                        parseAndApply(body, true);
                    }
                }
                loadDevFileIfPresent();
                resolveMissingMojangNamesAsync();
            } catch (Exception e) {
                LOGGER.debug("[Wish] Could not fetch remote custom_players.json: {}", e.getMessage());
                loadDevFileIfPresent();
                resolveMissingMojangNamesAsync();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                fetchInProgress.set(false);
            }
        });
    }

    static Map<UUID, CustomPlayerData> parseJsonToMap(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return null;
        try {
            JsonObject root = HttpClientUtils.parseJsonObject(jsonStr);
            if (root == null) return null;

            Map<UUID, CustomPlayerData> parsed = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String uuidKey = entry.getKey().trim();
                if (!entry.getValue().isJsonObject()) continue;

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidKey);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                CustomPlayerData data = parsePlayerEntry(uuid, entry.getValue().getAsJsonObject());
                parsed.put(uuid, data);
            }
            return parsed;
        } catch (Exception e) {
            LOGGER.warn("[Wish] Failed to parse custom_players.json", e);
            return null;
        }
    }

    private static CustomPlayerData parsePlayerEntry(UUID uuid, JsonObject obj) {
        String color = sanitizeString(getString(obj, "color", ""), 64);
        int font = clampInt(getInt(obj, "font", 0), 0, 31);
        double animSpeed = clampDouble(getDouble(obj, "animSpeed", 0.5), 0.01, 10.0);

        boolean badge = true;
        String badgeIcon = "\uE001";
        if (obj.has("badge") && !obj.get("badge").isJsonNull()) {
            if (obj.get("badge").isJsonPrimitive() && obj.get("badge").getAsJsonPrimitive().isBoolean()) {
                badge = obj.get("badge").getAsBoolean();
            } else {
                String bStr = getString(obj, "badge", "");
                if ("false".equalsIgnoreCase(bStr) || "off".equalsIgnoreCase(bStr) || "none".equalsIgnoreCase(bStr)) {
                    badge = false;
                } else {
                    badge = true;
                    if (isShieldBadgeToken(bStr)) {
                        badgeIcon = "\uE002";
                    }
                }
            }
        }
        String bIconStr = getString(obj, "badgeIcon", null);
        if (bIconStr != null && isShieldBadgeToken(bIconStr)) {
            badgeIcon = "\uE002";
        }

        float scaleX = clampFloat(getFloat(obj, "scaleX", 1.0f), -5.0f, 5.0f);
        float scaleY = clampFloat(getFloat(obj, "scaleY", 1.0f), -5.0f, 5.0f);
        float scaleZ = clampFloat(getFloat(obj, "scaleZ", 1.0f), -5.0f, 5.0f);
        float spinSpeedY = clampFloat(getFloat(obj, "spinSpeedY", 0.0f), -20.0f, 20.0f);
        int wingId = PlayerCosmeticsManager.clampId(getInt(obj, "wing", 0));
        int haloId = PlayerCosmeticsManager.clampId(getInt(obj, "halo", 0));
        int particleId = PlayerCosmeticsManager.clampId(getInt(obj, "particle", 0));
        int parsedCosRgb = ColorUtils.parseHexRgb(getString(obj, "cosmeticColor", null), -1);
        String cosmeticColor = parsedCosRgb >= 0 ? ColorUtils.formatHexRgb(parsedCosRgb) : null;
        String spoofedName = sanitizeSyncedSpoofedName(getString(obj, "spoofedName", ""));

        return new CustomPlayerData(
                uuid, color, font, animSpeed, badge, badgeIcon, scaleX, scaleY, scaleZ, spinSpeedY,
                wingId, haloId, particleId, cosmeticColor, spoofedName != null ? spoofedName : ""
        );
    }

    private static boolean isShieldBadgeToken(String value) {
        return "shield".equalsIgnoreCase(value)
                || "square".equalsIgnoreCase(value)
                || "2".equals(value)
                || value.contains("\uE002");
    }

    private static synchronized boolean parseAndApply(String jsonStr, boolean mergeWithExisting) {
        Map<UUID, CustomPlayerData> parsed = parseJsonToMap(jsonStr);
        if (parsed == null || parsed.isEmpty()) return false;

        Map<UUID, CustomPlayerData> nextRepo = mergeWithExisting ? new HashMap<>(repoPlayers) : new HashMap<>();
        nextRepo.putAll(parsed);
        repoPlayers = Collections.unmodifiableMap(nextRepo);
        recomputeMergedPlayersLocked();
        return true;
    }

    private static synchronized void recomputeMergedPlayersLocked() {
        Map<UUID, CustomPlayerData> merged = new HashMap<>(repoPlayers);
        for (Map.Entry<UUID, SyncedCosmeticState> entry : syncedCosmeticsByUuid.entrySet()) {
            UUID uuid = entry.getKey();
            SyncedCosmeticState synced = entry.getValue();
            CustomPlayerData base = merged.get(uuid);
            if (base != null) {
                merged.put(uuid, new CustomPlayerData(
                        uuid,
                        synced.color() != null ? synced.color() : base.color(),
                        synced.font() != null ? synced.font() : base.font(),
                        synced.animSpeed() != null ? synced.animSpeed() : base.animSpeed(),
                        base.badge(),
                        base.badgeIcon(),
                        synced.scaleX() != null ? synced.scaleX() : base.scaleX(),
                        synced.scaleY() != null ? synced.scaleY() : base.scaleY(),
                        synced.scaleZ() != null ? synced.scaleZ() : base.scaleZ(),
                        synced.spinSpeedY() != null ? synced.spinSpeedY() : base.spinSpeedY(),
                        synced.wingId(),
                        synced.haloId(),
                        synced.particleId(),
                        synced.cosmeticColor(),
                        synced.spoofedName() != null ? synced.spoofedName() : base.spoofedName()
                ));
            } else if (synced.wingId() > 0 || synced.haloId() > 0 || synced.particleId() > 0
                    || hasActiveProfileOverrides(synced)) {
                merged.put(uuid, new CustomPlayerData(
                        uuid,
                        synced.color() != null ? synced.color() : "",
                        synced.font() != null ? synced.font() : 0,
                        synced.animSpeed() != null ? synced.animSpeed() : 1.0,
                        false,
                        "\uE001",
                        synced.scaleX() != null ? synced.scaleX() : 1.0f,
                        synced.scaleY() != null ? synced.scaleY() : 1.0f,
                        synced.scaleZ() != null ? synced.scaleZ() : 1.0f,
                        synced.spinSpeedY() != null ? synced.spinSpeedY() : 0.0f,
                        synced.wingId(),
                        synced.haloId(),
                        synced.particleId(),
                        synced.cosmeticColor(),
                        synced.spoofedName() != null ? synced.spoofedName() : ""
                ));
            }
        }
        applyPlayersInternal(merged);
    }

    private static boolean hasActiveProfileOverrides(SyncedCosmeticState state) {
        if (state == null) {
            return false;
        }
        return (state.color() != null && !state.color().isEmpty())
                || (state.font() != null && state.font() != 0)
                || (state.spoofedName() != null && !state.spoofedName().isEmpty())
                || (state.scaleX() != null && Math.abs(state.scaleX() - 1.0f) > 1e-4f)
                || (state.scaleY() != null && Math.abs(state.scaleY() - 1.0f) > 1e-4f)
                || (state.scaleZ() != null && Math.abs(state.scaleZ() - 1.0f) > 1e-4f)
                || (state.spinSpeedY() != null && Math.abs(state.spinSpeedY()) > 1e-4f);
    }

    public static synchronized boolean upsertSyncedCosmetics(
            UUID uuid,
            String username,
            int wingId,
            int haloId,
            int particleId,
            String cosmeticColor
    ) {
        if (uuid == null) {
            return false;
        }
        return applySyncedBatch(List.of(new SyncedCosmeticRecord(uuid, username, wingId, haloId, particleId, cosmeticColor))) > 0;
    }

    public static synchronized int applySyncedBatch(Collection<SyncedCosmeticRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        boolean anyStateChanged = false;
        boolean anyNameAdded = false;
        int appliedCount = 0;

        for (SyncedCosmeticRecord rec : records) {
            if (rec == null || rec.uuid() == null) {
                continue;
            }
            int w = PlayerCosmeticsManager.clampId(rec.wingId());
            int h = PlayerCosmeticsManager.clampId(rec.haloId());
            int p = PlayerCosmeticsManager.clampId(rec.particleId());
            int parsedColor = ColorUtils.parseHexRgb(rec.cosmeticColor(), -1);
            String cleanColor = parsedColor >= 0 ? ColorUtils.formatHexRgb(parsedColor) : null;
            String cleanNameColor = sanitizeSyncedColorCode(rec.color());
            Integer cleanFont = rec.font() != null ? clampInt(rec.font(), 0, 31) : null;
            Double cleanAnimSpeed = (rec.animSpeed() != null && Double.isFinite(rec.animSpeed()))
                    ? clampDouble(rec.animSpeed(), 0.01, 10.0)
                    : null;
            String cleanSpoof = null;
            Float cleanScaleX = (rec.scaleX() != null && Float.isFinite(rec.scaleX()))
                    ? clampFloat(rec.scaleX(), -5.0f, 5.0f)
                    : null;
            Float cleanScaleY = (rec.scaleY() != null && Float.isFinite(rec.scaleY()))
                    ? clampFloat(rec.scaleY(), -5.0f, 5.0f)
                    : null;
            Float cleanScaleZ = (rec.scaleZ() != null && Float.isFinite(rec.scaleZ()))
                    ? clampFloat(rec.scaleZ(), -5.0f, 5.0f)
                    : null;
            Float cleanSpinSpeedY = (rec.spinSpeedY() != null && Float.isFinite(rec.spinSpeedY()))
                    ? clampFloat(rec.spinSpeedY(), -20.0f, 20.0f)
                    : null;

            SyncedCosmeticState nextState = new SyncedCosmeticState(
                    w, h, p, cleanColor,
                    cleanNameColor, cleanFont, cleanAnimSpeed, cleanSpoof,
                    cleanScaleX, cleanScaleY, cleanScaleZ, cleanSpinSpeedY
            );
            if (w == 0 && h == 0 && p == 0
                    && !hasActiveProfileOverrides(nextState)
                    && !repoPlayers.containsKey(rec.uuid())) {
                if (syncedCosmeticsByUuid.remove(rec.uuid()) != null) {
                    anyStateChanged = true;
                }
            } else {
                SyncedCosmeticState prev = syncedCosmeticsByUuid.put(rec.uuid(), nextState);
                if (!nextState.equals(prev)) {
                    anyStateChanged = true;
                }
            }

            if (rec.username() != null && VALID_MC_NAME_PATTERN.matcher(rec.username().trim()).matches()) {
                String cleanName = sanitizeString(rec.username(), 16);
                if (!cleanName.isEmpty() && !cleanName.equals(liveNamesByUuid.get(rec.uuid()))) {
                    liveNamesByUuid.put(rec.uuid(), cleanName);
                    anyNameAdded = true;
                }
            }
            appliedCount++;
        }

        if (anyStateChanged) {
            recomputeMergedPlayersLocked();
        } else if (anyNameAdded) {
            rebuildResolvedBindings();
        }
        return appliedCount;
    }

    public static String sanitizeSyncedColorCode(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = CONTROL_CHAR_PATTERN.matcher(raw).replaceAll("").trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > 64) {
            return null;
        }
        if ("CHROMA".equalsIgnoreCase(trimmed)) {
            return "CHROMA";
        }
        int singleRgb = parseStrictHexRgb(trimmed);
        if (singleRgb >= 0) {
            return ColorUtils.formatHexRgb(singleRgb);
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String mode = trimmed.substring(0, colonIdx).toUpperCase(Locale.ROOT);
            if ("GRADIENT".equals(mode) || "WAVE".equals(mode) || "BREATHE".equals(mode) || "BLINK".equals(mode)) {
                String[] parts = trimmed.split(":");
                if (parts.length == 3) {
                    int c1 = parseStrictHexRgb(parts[1]);
                    int c2 = parseStrictHexRgb(parts[2]);
                    if (c1 >= 0 && c2 >= 0) {
                        return mode + ":" + ColorUtils.formatHexRgb(c1) + ":" + ColorUtils.formatHexRgb(c2);
                    }
                }
            }
        }
        return null;
    }

    private static int parseStrictHexRgb(String raw) {
        if (raw == null) {
            return -1;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return -1;
        }
        for (int i = 0; i < 6; i++) {
            if (Character.digit(s.charAt(i), 16) == -1) {
                return -1;
            }
        }
        return ColorUtils.parseHexRgb("#" + s, -1);
    }

    public static String sanitizeSyncedSpoofedName(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = CONTROL_CHAR_PATTERN.matcher(raw).replaceAll("");
        clean = LEGACY_COLOR_STRIP_PATTERN.matcher(clean).replaceAll("").replace("\u00A7", "").trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("http://") || lower.contains("https://")) {
            return "";
        }
        if (clean.length() > 24) {
            clean = clean.substring(0, 24);
        }
        return clean;
    }

    private static synchronized void applyPlayersInternal(Map<UUID, CustomPlayerData> nextByUuid) {
        byUuid = Collections.unmodifiableMap(new HashMap<>(nextByUuid));
        allPlayers = Collections.unmodifiableList(new ArrayList<>(byUuid.values()));
        rebuildResolvedBindings();
    }

    static synchronized void setTestState(Map<UUID, CustomPlayerData> players, Map<UUID, String> liveNames) {
        liveNamesByUuid.clear();
        syncedCosmeticsByUuid.clear();
        if (liveNames != null) {
            for (Map.Entry<UUID, String> e : liveNames.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
                    liveNamesByUuid.put(e.getKey(), sanitizeString(e.getValue(), 32));
                }
            }
        }
        mojangNamesByUuid.clear();
        repoPlayers = players != null ? Collections.unmodifiableMap(new HashMap<>(players)) : Collections.emptyMap();
        recomputeMergedPlayersLocked();
    }

    private static synchronized void rebuildResolvedBindings() {
        Map<UUID, CustomPlayerData> currentByUuid = byUuid;
        if (currentByUuid.isEmpty()) {
            resolvedByNameLower = Collections.emptyMap();
            resolvedPlayers = Collections.emptyList();
            NameColorManager.invalidateTargetCache();
            return;
        }
        Map<String, CustomPlayerData> nextByNameLower = new HashMap<>(currentByUuid.size());
        List<ResolvedCustomPlayer> nextResolved = new ArrayList<>(currentByUuid.size());
        for (Map.Entry<UUID, CustomPlayerData> entry : currentByUuid.entrySet()) {
            UUID uuid = entry.getKey();
            CustomPlayerData data = entry.getValue();
            String resolvedName = liveNamesByUuid.get(uuid);
            if (resolvedName == null || resolvedName.isEmpty()) {
                MojangNameEntry mojang = mojangNamesByUuid.get(uuid);
                if (mojang != null && !mojang.name().isEmpty()) {
                    resolvedName = mojang.name();
                }
            }
            if (resolvedName != null && !resolvedName.isEmpty()) {
                nextByNameLower.put(resolvedName.toLowerCase(Locale.ROOT), data);
                nextResolved.add(new ResolvedCustomPlayer(resolvedName, data));
            }
        }
        resolvedByNameLower = Collections.unmodifiableMap(nextByNameLower);
        resolvedPlayers = Collections.unmodifiableList(nextResolved);
        NameColorManager.invalidateTargetCache();
    }

    public static Collection<CustomPlayerData> getPlayers() {
        return allPlayers;
    }

    public static List<ResolvedCustomPlayer> getResolvedPlayers() {
        return resolvedPlayers;
    }

    public static CustomPlayerData getPlayer(UUID uuid) {
        return uuid != null ? byUuid.get(uuid) : null;
    }

    public static boolean isRepositoryPlayer(UUID uuid) {
        return uuid != null && repoPlayers.containsKey(uuid);
    }

    public static CustomPlayerData getPlayer(UUID uuid, String fallbackName) {
        CustomPlayerData byId = getPlayer(uuid);
        if (byId != null) {
            return byId;
        }
        return getPlayerByResolvedName(fallbackName);
    }

    public static CustomPlayerData getPlayerByResolvedName(String name) {
        if (name == null || name.isEmpty()) return null;
        return resolvedByNameLower.get(name.toLowerCase(Locale.ROOT));
    }

    public static boolean observePlayer(UUID uuid, String currentName) {
        if (uuid == null || currentName == null || currentName.isEmpty() || !byUuid.containsKey(uuid)) {
            return false;
        }
        String cleanName = sanitizeString(currentName, 32);
        if (cleanName.isEmpty()) {
            return false;
        }
        String previous = liveNamesByUuid.get(uuid);
        if (cleanName.equals(previous)) {
            return false;
        }
        liveNamesByUuid.put(uuid, cleanName);
        rebuildResolvedBindings();
        return true;
    }

    public static void syncLiveBindings(Minecraft mc, boolean force) {
        if (byUuid.isEmpty() || mc == null || mc.level == null) {
            return;
        }
        boolean changed = false;
        if (mc.player != null) {
            changed |= putLiveBindingIfChanged(mc.player.getUUID(), mc.player.getScoreboardName());
        }
        long now = System.currentTimeMillis();
        if (force || now - lastLiveSyncMs >= LIVE_SYNC_INTERVAL_MS) {
            lastLiveSyncMs = now;
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                for (PlayerInfo info : connection.getOnlinePlayers()) {
                    GameProfile profile = info.getProfile();
                    if (profile != null) {
                        changed |= putLiveBindingIfChanged(profile.id(), profile.name());
                    }
                }
            }
            for (AbstractClientPlayer worldPlayer : mc.level.players()) {
                changed |= putLiveBindingIfChanged(worldPlayer.getUUID(), worldPlayer.getScoreboardName());
            }
        }
        if (changed) {
            rebuildResolvedBindings();
        }
    }

    private static boolean putLiveBindingIfChanged(UUID uuid, String rawName) {
        if (uuid == null || rawName == null || rawName.isEmpty() || !byUuid.containsKey(uuid)) {
            return false;
        }
        String cleanName = sanitizeString(rawName, 32);
        if (cleanName.isEmpty()) {
            return false;
        }
        String previous = liveNamesByUuid.get(uuid);
        if (cleanName.equals(previous)) {
            return false;
        }
        liveNamesByUuid.put(uuid, cleanName);
        return true;
    }

    private static void resolveMissingMojangNamesAsync() {
        Map<UUID, CustomPlayerData> snapshot = byUuid;
        if (snapshot.isEmpty() || !mojangResolveInProgress.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            boolean anyUpdated = false;
            try {
                long now = System.currentTimeMillis();
                for (UUID uuid : snapshot.keySet()) {
                    MojangNameEntry existing = mojangNamesByUuid.get(uuid);
                    if (existing != null) {
                        long ttl = existing.name().isEmpty() ? MOJANG_RETRY_COOLDOWN_MS : MOJANG_CACHE_TTL_MS;
                        if (now - existing.fetchedAtMs() < ttl) {
                            continue;
                        }
                    }
                    String fetched = fetchMojangProfileName(uuid);
                    if (fetched != null) {
                        mojangNamesByUuid.put(uuid, new MojangNameEntry(fetched, now));
                        if (existing == null || !fetched.equals(existing.name())) {
                            anyUpdated = true;
                        }
                    } else if (existing == null) {
                        mojangNamesByUuid.put(uuid, new MojangNameEntry("", now));
                    }
                }
                if (anyUpdated) {
                    rebuildResolvedBindings();
                }
            } finally {
                mojangResolveInProgress.set(false);
            }
        });
    }

    private static String fetchMojangProfileName(UUID uuid) {
        String compactUuid = uuid.toString().replace("-", "");
        if (compactUuid.length() != 32) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(MOJANG_PROFILE_URL + compactUuid).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Wish-Client/" + WishClient.MOD_VERSION);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream is = conn.getInputStream()) {
                String body = HttpClientUtils.readBoundedUtf8(is, HttpClientUtils.DEFAULT_MAX_BYTES);
                return parseMojangProfileName(body);
            }
        } catch (Exception e) {
            LOGGER.debug("[Wish] Could not resolve Mojang username for {}: {}", uuid, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static String parseMojangProfileName(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return null;
        }
        try {
            JsonObject obj = HttpClientUtils.parseJsonObject(jsonBody);
            if (obj == null) {
                return null;
            }
            String rawName = getString(obj, "name", "");
            String cleanName = sanitizeString(rawName, 32);
            return VALID_MC_NAME_PATTERN.matcher(cleanName).matches() ? cleanName : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sanitizeString(String s, int maxLen) {
        if (s == null) return "";
        String clean = s.replace("\r", "").replace("\n", "").trim();
        if (clean.length() > maxLen) {
            clean = clean.substring(0, maxLen);
        }
        return clean;
    }

    private static String getString(JsonObject obj, String key, String def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                double v = obj.get(key).getAsDouble();
                return Double.isFinite(v) ? v : def;
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                float v = obj.get(key).getAsFloat();
                return Float.isFinite(v) ? v : def;
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static int clampInt(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static double clampDouble(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private static float clampFloat(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
