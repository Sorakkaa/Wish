package com.wish.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public final class MusicManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Music");
    private static final String APP_ID = "wish";
    private static long lastRequestTime = 0;
    private static volatile boolean isPairingActive = false;
    private static volatile boolean isFetchingState = false;
    public static String currentSongText = null;

    private MusicManager() {}

    private static void saveToken(String token) {
        ModConfig.INSTANCE.authToken = token;
        ModConfig.INSTANCE.save();
    }

    public static void fetchAndSendSongWithDelay(String chatPrefix) {
        String provider = ModConfig.INSTANCE.musicProvider;
        if (provider == null || "None".equalsIgnoreCase(provider)) {
            WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §cNo music platform selected! Please go to /wish -> Song Config to select your platform.");
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
                Thread.sleep(1500); 

                
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

                
                if (!"YTM".equalsIgnoreCase(provider)) {
                    String winMediaSong = fetchSystemMediaSong();
                    if (winMediaSong != null && !winMediaSong.trim().isEmpty()) {
                        sendWinMediaSongToChat(winMediaSong, chatPrefix);
                        return;
                    }
                }

                
                if ("YTM".equalsIgnoreCase(provider)) {
                    if (ModConfig.INSTANCE.authToken == null) {
                        WishClient.sendLocalChatMessage("§e[Wish] §cYou haven't connected your API! Click 'Pair API' in /wish");
                    } else if (apiConnectionFailed) {
                        WishClient.sendLocalChatMessage("§e[Wish] §cFailed to connect to YTM API! Is the Companion App open?");
                    } else {
                        WishClient.sendLocalChatMessage("§e[Wish] §cNo song currently playing on YTM. Start your music and try again!");
                    }
                } else {
                    WishClient.sendLocalChatMessage("§e[Wish] §cNo song currently playing on " + provider + ". Start your music and try again!");
                }
            } catch (Exception e) {
                LOGGER.error("Error reading song state", e);
                WishClient.sendLocalChatMessage("§c[Wish] Error reading music.");
            } finally {
                isFetchingState = false;
            }
        });
    }

    public static String fetchSystemMediaSong() {
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
                try (InputStream in = MusicManager.class.getResourceAsStream("/assets/wish/scripts/get_song.ps1")) {
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
        } catch (Exception e) {
            LOGGER.debug("Could not fetch system media song", e);
        }
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
            WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §cPlease select a platform (YouTube Music, Spotify, or Deezer) in /wish -> Song Config first!");
            return;
        }
        if ("Deezer".equalsIgnoreCase(provider) || "Spotify".equalsIgnoreCase(provider)) {
            WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §a" + provider + " uses native Windows auto-detection. Play your music on " + provider + "!");
            return;
        }
        if (isPairingActive) {
            WishClient.sendLocalChatMessage("§e[Wish] §fPairing in progress... Allow the connection in " + provider + "!");
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
                        WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §fCode: §e§l" + code + " §8(§aApprove in " + provider + " popup!§8)");
                        exchangeCodeForToken(code);
                        return;
                    }
                } else {
                    WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §cFailed to request " + provider + " (code " + conn.getResponseCode() + ")");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to pair with music provider " + provider, e);
                WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §cError: Make sure " + provider + " application is open!");
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
                                WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §a§lSuccessfully paired with YouTube Music!");
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §cAuthorization timeout. Type !song again.");
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
                WishClient.currentSongText = currentSongText;
                String format = ModConfig.INSTANCE.messageFormat;
                if (format == null || format.trim().isEmpty()) {
                    format = "{title} - {artist}";
                }
                String body = format.replace("{title}", title).replace("{artist}", artist);
                WishClient.sendServerChatMessage(chatPrefix, body);
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

        WishClient.sendLocalChatMessage("§e[Wish] §cNo song currently playing.");
    }

    private static void sendWinMediaSongToChat(String winMediaSong, String chatPrefix) {
        currentSongText = "§f🎵 §b" + winMediaSong;
        WishClient.currentSongText = currentSongText;
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
        WishClient.sendServerChatMessage(chatPrefix, body);
    }
}
