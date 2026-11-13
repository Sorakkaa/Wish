package com.wish.client.color;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wish.client.WishClient;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class CustomPlayerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-CustomPlayers");

    private static final String REMOTE_URL = "https://raw.githubusercontent.com/Sorakkaa/Wish/main/custom_players.json";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes
    private static final long JOIN_COOLDOWN_MS = 60 * 1000L; // 1 minute cooldown on server join

    public record CustomPlayerData(
            UUID uuid,
            String name,
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
    }

    private static volatile Map<UUID, CustomPlayerData> byUuid = Collections.emptyMap();
    private static volatile Map<String, CustomPlayerData> byNameLower = Collections.emptyMap();
    private static volatile List<CustomPlayerData> allPlayers = Collections.emptyList();

    private static volatile long lastFetchTimeMs = 0L;
    private static final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
    private static boolean initialized = false;

    private CustomPlayerManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        loadEmbeddedFallback();
        loadDevFileIfPresent();
        fetchAsync();
    }

    public static void onClientTick() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTimeMs >= REFRESH_INTERVAL_MS && !fetchInProgress.get()) {
            fetchAsync();
        }
    }

    public static void onPlayerJoin() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTimeMs >= JOIN_COOLDOWN_MS && !fetchInProgress.get()) {
            fetchAsync();
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
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                    parseAndApply(sb.toString(), false);
                }
                loadDevFileIfPresent();
            } catch (Exception e) {
                LOGGER.debug("[Wish] Could not fetch remote custom_players.json: {}", e.getMessage());
                loadDevFileIfPresent();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                fetchInProgress.set(false);
            }
        });
    }

    private static synchronized boolean parseAndApply(String jsonStr, boolean mergeWithExisting) {
        if (jsonStr == null || jsonStr.isBlank()) return false;
        try {
            JsonElement rootEl = JsonParser.parseString(jsonStr);
            if (!rootEl.isJsonObject()) return false;
            JsonObject root = rootEl.getAsJsonObject();

            Map<UUID, CustomPlayerData> newByUuid = mergeWithExisting ? new HashMap<>(byUuid) : new HashMap<>();
            Map<String, CustomPlayerData> newByName = mergeWithExisting ? new HashMap<>(byNameLower) : new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String uuidKey = entry.getKey().trim();
                if (!entry.getValue().isJsonObject()) continue;

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidKey);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                JsonObject obj = entry.getValue().getAsJsonObject();
                String name = sanitizeString(getString(obj, "name", ""), 32);
                if (name.isEmpty()) continue;

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
                            if ("shield".equalsIgnoreCase(bStr) || "square".equalsIgnoreCase(bStr) || "2".equals(bStr) || bStr.contains("\uE002")) {
                                badgeIcon = "\uE002";
                            }
                        }
                    }
                }
                String bIconStr = getString(obj, "badgeIcon", null);
                if (bIconStr != null) {
                    if ("shield".equalsIgnoreCase(bIconStr) || "square".equalsIgnoreCase(bIconStr) || "2".equals(bIconStr) || bIconStr.contains("\uE002")) {
                        badgeIcon = "\uE002";
                    }
                }

                float scaleX = clampFloat(getFloat(obj, "scaleX", 1.0f), -5.0f, 5.0f);
                float scaleY = clampFloat(getFloat(obj, "scaleY", 1.0f), -5.0f, 5.0f);
                float scaleZ = clampFloat(getFloat(obj, "scaleZ", 1.0f), -5.0f, 5.0f);
                float spinSpeedY = clampFloat(getFloat(obj, "spinSpeedY", 0.0f), -20.0f, 20.0f);

                CustomPlayerData data = new CustomPlayerData(
                        uuid, name, color, font, animSpeed, badge, badgeIcon, scaleX, scaleY, scaleZ, spinSpeedY
                );

                newByUuid.put(uuid, data);
                newByName.put(name.toLowerCase(Locale.ROOT), data);
            }

            if (newByUuid.isEmpty()) {
                return false;
            }

            List<CustomPlayerData> newList = new ArrayList<>(newByUuid.values());
            byUuid = Collections.unmodifiableMap(newByUuid);
            byNameLower = Collections.unmodifiableMap(newByName);
            allPlayers = Collections.unmodifiableList(newList);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[Wish] Failed to parse custom_players.json", e);
            return false;
        }
    }

    public static Collection<CustomPlayerData> getPlayers() {
        return allPlayers;
    }

    public static CustomPlayerData getPlayer(UUID uuid, String name) {
        if (uuid != null) {
            CustomPlayerData byId = byUuid.get(uuid);
            if (byId != null) return byId;
        }
        if (name != null && !name.isEmpty()) {
            return byNameLower.get(name.toLowerCase(Locale.ROOT));
        }
        return null;
    }

    public static CustomPlayerData getPlayerByName(String name) {
        if (name == null || name.isEmpty()) return null;
        return byNameLower.get(name.toLowerCase(Locale.ROOT));
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

