package com.wish.client.music;

import com.google.gson.JsonObject;
import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import com.wish.client.features.visual.LyricsManager;
import com.wish.client.util.HttpClientUtils;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MusicManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Music");
    private static final String APP_ID = "wish";
    private static final int MAX_COMPANION_RESPONSE_BYTES = 512 * 1024;
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

        CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute(() -> {
            try {
                if (ModConfig.INSTANCE.enableLyrics && !ModConfig.INSTANCE.lyricsDemoMode && com.wish.client.features.visual.LyricsManager.isPlaying) {
                    String lTitle = com.wish.client.features.visual.LyricsManager.getCurrentTitle();
                    String lArtist = com.wish.client.features.visual.LyricsManager.getCurrentArtist();
                    if (lTitle != null && !lTitle.trim().isEmpty()) {
                        String songInfo = (lArtist != null && !lArtist.trim().isEmpty()) ? lTitle + " - " + lArtist : lTitle;
                        sendWinMediaSongToChat(songInfo, chatPrefix);
                        return;
                    }
                }

                String sysSong = fetchSystemMediaSong();
                if (sysSong != null && !sysSong.trim().isEmpty()) {
                    sendWinMediaSongToChat(sysSong, chatPrefix);
                    return;
                }

                boolean apiConnectionFailed = false;
                if (ModConfig.INSTANCE.authToken != null) {
                    HttpURLConnection conn = null;
                    try {
                        conn = createAuthenticatedConnection("/state", "GET");
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
                    } finally {
                        if (conn != null) conn.disconnect();
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
                String[] jnaTrack = LyricsManager.detectWindowsTrackViaJna(providerParam);
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
                    JsonObject respJson = HttpClientUtils.parseJsonObject(respStr);
                    String code = HttpClientUtils.getStringOrEmpty(respJson, "code");
                    if (!code.isEmpty()) {
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
        pollTokenAttempt(code, 0);
    }

    private static void pollTokenAttempt(String code, int attempt) {
        if (attempt >= 18) {
            isPairingActive = false;
            WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §cAuthorization timeout. Type !song again.");
            return;
        }
        CompletableFuture.delayedExecutor(2500, TimeUnit.MILLISECONDS).execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = URI.create(getBaseUrl() + "/auth/request").toURL();
                conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
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
                    JsonObject respJson = HttpClientUtils.parseJsonObject(respStr);
                    String token = HttpClientUtils.getStringOrEmpty(respJson, "token");
                    if (!token.isEmpty()) {
                        saveToken(token);
                        isPairingActive = false;
                        WishClient.sendLocalChatMessage("§d§l🎵 WISH §8» §a§lSuccessfully paired with YouTube Music!");
                        return;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("YTM pairing poll attempt {} failed: {}", attempt + 1, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
            pollTokenAttempt(code, attempt + 1);
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
        try (InputStream in = conn.getInputStream()) {
            return HttpClientUtils.readBoundedUtf8(in, MAX_COMPANION_RESPONSE_BYTES);
        }
    }

    private static void parseAndSendToChat(String jsonBody, String chatPrefix) {
        try {
            JsonObject json = HttpClientUtils.parseJsonObject(jsonBody);
            if (json != null) {
                String title = "";
                String artist = "";

                JsonObject video = HttpClientUtils.getObjectOrNull(json, "video");
                if (video != null) {
                    title = HttpClientUtils.getStringOrEmpty(video, "title");
                    artist = HttpClientUtils.getStringOrEmpty(video, "author");
                    if (artist.isEmpty()) artist = HttpClientUtils.getStringOrEmpty(video, "artist");
                }

                if (title.isEmpty()) {
                    JsonObject player = HttpClientUtils.getObjectOrNull(json, "player");
                    JsonObject track = HttpClientUtils.getObjectOrNull(player, "track");
                    if (track != null) {
                        title = HttpClientUtils.getStringOrEmpty(track, "title");
                        artist = HttpClientUtils.getStringOrEmpty(track, "author");
                        if (artist.isEmpty()) artist = HttpClientUtils.getStringOrEmpty(track, "artist");
                    }
                }

                if (title.isEmpty()) {
                    title = HttpClientUtils.getStringOrEmpty(json, "title");
                    artist = HttpClientUtils.getStringOrEmpty(json, "artist");
                    if (artist.isEmpty()) artist = HttpClientUtils.getStringOrEmpty(json, "author");
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
            }
        } catch (Exception e) {
            LOGGER.debug("Could not parse companion JSON response", e);
        }

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
