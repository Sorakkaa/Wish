package com.wish.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MusicManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Music");
    private static final String APP_ID = "wish";
    private static volatile long lastRequestTime = 0;
    private static volatile boolean isPairingActive = false;
    private static volatile boolean isFetchingState = false;

    private MusicManager() {}

    private static void saveToken(String token) {
        ModConfig.INSTANCE.authToken = token;
        ModConfig.INSTANCE.save();
    }

    public static void fetchAndSendSongWithDelay(String chatPrefix) {
        String provider = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider.trim() : "None";

        long now = System.currentTimeMillis();
        if (now - lastRequestTime < 2500 || isFetchingState) {
            return;
        }
        lastRequestTime = now;
        isFetchingState = true;

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1500); 

                // 1. Direct check: If 3D Lyrics is active and currently playing, use the cached playing song
                if (ModConfig.INSTANCE.enableLyrics && !ModConfig.INSTANCE.lyricsDemoMode && com.wish.client.features.visual.LyricsManager.isPlaying) {
                    String lTitle = com.wish.client.features.visual.LyricsManager.getCurrentTitle();
                    String lArtist = com.wish.client.features.visual.LyricsManager.getCurrentArtist();
                    if (lTitle != null && !lTitle.trim().isEmpty()) {
                        String songInfo = (lArtist != null && !lArtist.trim().isEmpty()) ? lTitle + " - " + lArtist : lTitle;
                        sendWinMediaSongToChat(songInfo, chatPrefix);
                        return;
                    }
                }

                // 2. Query system media (playerctl on Linux, PowerShell on Windows) - works for YTM, Spotify, Deezer
                String sysSong = fetchSystemMediaSong();
                if (sysSong != null && !sysSong.trim().isEmpty()) {
                    sendWinMediaSongToChat(sysSong, chatPrefix);
                    return;
                }

                // 3. Try Companion HTTP API if token is configured
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

                if ("YTM".equalsIgnoreCase(provider) || "YouTube Music".equalsIgnoreCase(provider)) {
                    if (ModConfig.INSTANCE.authToken == null) {
                        WishClient.sendLocalChatMessage("§e[Wish] §cNo song detected on YouTube Music (or pair API in /wish).");
                    } else if (apiConnectionFailed) {
                        WishClient.sendLocalChatMessage("§e[Wish] §cFailed to connect to YTM API! Is YouTube Music open?");
                    } else {
                        WishClient.sendLocalChatMessage("§e[Wish] §cNo song currently playing on YTM. Start your music and try again!");
                    }
                } else if ("None".equalsIgnoreCase(provider) || provider.isEmpty()) {
                    WishClient.sendLocalChatMessage("§e[Wish] §cNo song detected on Spotify, Deezer, or YouTube Music. Start your music and try again!");
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
        if (ModConfig.INSTANCE.enableLyrics && !ModConfig.INSTANCE.lyricsDemoMode && com.wish.client.features.visual.LyricsManager.isPlaying) {
            String lTitle = com.wish.client.features.visual.LyricsManager.getCurrentTitle();
            String lArtist = com.wish.client.features.visual.LyricsManager.getCurrentArtist();
            if (lTitle != null && !lTitle.trim().isEmpty()) {
                return (lArtist != null && !lArtist.trim().isEmpty()) ? lTitle + " - " + lArtist : lTitle;
            }
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                String targetPlayers = "spotify,spotifyd,spotify_player,ncspot,ytmdesktop,youtube-music,youtubemusic,youtube_music,deezer,deezer-desktop,deezplayer";
                String prov = ModConfig.INSTANCE.musicProvider;
                if ("Spotify".equalsIgnoreCase(prov)) {
                    targetPlayers = "spotify,spotifyd,spotify_player,ncspot";
                } else if ("YTM".equalsIgnoreCase(prov) || "YouTube Music".equalsIgnoreCase(prov)) {
                    targetPlayers = "ytmdesktop,youtube-music,youtubemusic,youtube_music";
                } else if ("Deezer".equalsIgnoreCase(prov)) {
                    targetPlayers = "deezer,deezer-desktop,deezplayer";
                }

                ProcessBuilder pb = new ProcessBuilder("playerctl", "-p", targetPlayers, "metadata", "--format", "{{title}} - {{artist}}");
                Process p = pb.start();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    boolean finished = p.waitFor(2000, TimeUnit.MILLISECONDS);
                    if (!finished) {
                        p.destroyForcibly();
                        return null;
                    }
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.equals("-")) {
                            return trimmed;
                        }
                    }
                } finally {
                    if (p.isAlive()) {
                        p.destroyForcibly();
                    }
                }
            } else if (os.contains("win")) {
                String providerParam = ModConfig.INSTANCE.musicProvider != null && !ModConfig.INSTANCE.musicProvider.equalsIgnoreCase("None")
                    ? ModConfig.INSTANCE.musicProvider.trim() : "ALL";
                Path scriptPath = Path.of("config", "wish", "get_song.ps1");
                com.wish.client.features.visual.LyricsManager.ensureScriptExtracted(scriptPath);

                if (Files.exists(scriptPath)) {
                    ProcessBuilder pb = new ProcessBuilder(
                        com.wish.client.features.visual.LyricsManager.getPowerShellExecutable(),
                        "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-File", scriptPath.toAbsolutePath().toString(),
                        "-provider", providerParam
                    );
                    Process p = pb.start();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        boolean finished = p.waitFor(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (finished) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                String trimmed = line.trim();
                                if (!trimmed.isEmpty() && !trimmed.startsWith("DEBUG:")) {
                                    return trimmed;
                                }
                            }
                        } else {
                            p.destroyForcibly();
                        }
                    } finally {
                        if (p.isAlive()) {
                            p.destroyForcibly();
                        }
                    }
                }

                // Fallback to native Win32 JNA window title detection if PowerShell returned nothing
                String[] jnaTrack = com.wish.client.features.visual.LyricsManager.detectWindowsTrackViaJna(providerParam);
                if (jnaTrack != null && jnaTrack.length >= 3 && !jnaTrack[2].isEmpty()) {
                    return !jnaTrack[1].isEmpty() ? jnaTrack[2] + " - " + jnaTrack[1] : jnaTrack[2];
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
            boolean handedOff = false;
            HttpURLConnection conn = null;
            try {
                URL url = URI.create(getBaseUrl() + "/auth/requestcode").toURL();
                conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
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

                int codeStatus = conn.getResponseCode();
                if (codeStatus == 200) {
                    String respStr = readResponse(conn);
                    JsonObject respJson = JsonParser.parseString(respStr).getAsJsonObject();
                    if (respJson.has("code")) {
                        String code = respJson.get("code").getAsString();
                        WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §fCode: §e§l" + code + " §8(§aApprove in " + provider + " popup!§8)");
                        handedOff = true;
                        exchangeCodeForToken(code);
                        return;
                    }
                } else {
                    WishClient.sendLocalChatMessage("§d§l[Wish] §cFailed to request " + provider + " (code " + codeStatus + ")");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to pair with music provider " + provider, e);
                WishClient.sendLocalChatMessage("§d§l[Wish] §cError: Make sure " + provider + " application is open!");
            } finally {
                if (conn != null) conn.disconnect();
                if (!handedOff) {
                    isPairingActive = false;
                }
            }
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
                String format = ModConfig.INSTANCE.messageFormat;
                if (format == null || format.trim().isEmpty()) {
                    format = "{title} - {artist}";
                }
                String body = format.replace("{title}", title).replace("{artist}", artist);
                WishClient.sendServerChatMessage(chatPrefix, body);
                return;
            }
        } catch (Exception ignored) {}

        String winMediaSong = fetchSystemMediaSong();
        if (winMediaSong != null && !winMediaSong.trim().isEmpty()) {
            sendWinMediaSongToChat(winMediaSong, chatPrefix);
            return;
        }

        WishClient.sendLocalChatMessage("§e[Wish] §cNo song currently playing.");
    }

    private static void sendWinMediaSongToChat(String winMediaSong, String chatPrefix) {
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
