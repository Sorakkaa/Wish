package com.wish.client.features.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.wish.client.WishClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Lyrics");
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Wish-Lyrics-Worker");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService LYRICS_FETCH_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Wish-Lyrics-Fetcher");
        t.setDaemon(true);
        return t;
    });

    public static volatile boolean liveDebug = false;
    public static volatile String lastDebugError = null;
    public static volatile String lastDetectionMethod = "None";
    public static volatile String lastRawOutput = "None";
    public static volatile String lastLyricsSource = "None";
    public static volatile int lastLyricsCount = 0;
    public static volatile String lastAttemptedSong = "";
    public static volatile String lastLrclibStatus = "None";
    public static volatile String lastYtmStatus = "None";
    public static volatile String lastNetEaseStatus = "None";
    public static volatile long lastPollTimeMs = 0;

    public static class LyricLine {
        public final long timeMs;
        public final long endTimeMs;
        public final String text;
        public final List<String> words;
        public final float[] wordProgressStarts;

        public LyricLine(long timeMs, String text) {
            this(timeMs, 0L, text);
        }

        public LyricLine(long timeMs, long endTimeMs, String text) {
            this.timeMs = timeMs;
            this.endTimeMs = endTimeMs;
            // Clean enhanced LRC word timestamp tags like <00:12.34> or <12.34>
            String cleaned = text != null ? text.replaceAll("<\\d{1,2}:\\d{2}(?:[\\.:]\\d+)?>|<\\d+(?:[\\.:]\\d+)?>", " ").trim() : "";
            this.text = cleaned;
            this.words = new ArrayList<>();
            if (!this.text.isEmpty()) {
                String[] split = this.text.split("\\s+");
                for (String w : split) {
                    if (!w.isEmpty()) words.add(w);
                }
            }
            this.wordProgressStarts = new float[this.words.size()];
            if (!this.words.isEmpty()) {
                float[] weights = new float[this.words.size()];
                float totalWeight = 0f;
                for (int i = 0; i < this.words.size(); i++) {
                    String w = this.words.get(i);
                    String clean = w.replaceAll("[^a-zA-Z0-9À-ÿ]", "");
                    int charLen = Math.max(1, Math.min(10, clean.length()));
                    float weight = 1.3f + (charLen * 0.25f);
                    if (w.endsWith(",") || w.endsWith(";") || w.endsWith(":") || w.endsWith(".") || w.endsWith("!") || w.endsWith("?")) {
                        weight += 0.9f;
                    }
                    weights[i] = weight;
                    totalWeight += weight;
                }
                float accum = 0f;
                for (int i = 0; i < this.words.size(); i++) {
                    this.wordProgressStarts[i] = totalWeight > 0f ? (accum / totalWeight) : ((float) i / this.words.size());
                    accum += weights[i];
                }
            }
        }
    }

    public static class LyricState {
        public final LyricLine currentLine;
        public final LyricLine nextLine;
        public final float lineProgress;
        public final int activeWordIndex;
        public final boolean active;
        public final String title;
        public final String artist;

        public LyricState(LyricLine currentLine, LyricLine nextLine, float lineProgress, int activeWordIndex, boolean active, String title, String artist) {
            this.currentLine = currentLine;
            this.nextLine = nextLine;
            this.lineProgress = lineProgress;
            this.activeWordIndex = activeWordIndex;
            this.active = active;
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "";
        }
    }

    private static final List<LyricLine> activeLyrics = new ArrayList<>();
    private static final List<LyricLine> demoLyrics = new ArrayList<>();
    private static String currentTrackKey = "";
    public static volatile String currentTitle = "";
    public static volatile String currentArtist = "";
    public static volatile long currentDurationMs = 0;
    public static volatile boolean isPlaying = false;
    public static volatile long lastKnownProgressMs = 0;
    public static volatile long lastKnownTimeMs = 0;
    public static volatile String lastYtmVideoId = "";
    public static volatile String currentDetectedPlayer = "";
    private static volatile String lastFetchedVideoId = "";
    private static long demoStartTime = 0;

    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2}(?:[\\.:]\\d+)?)\\](.*)");

    static {
        // Sample demo track matching the aesthetic of the screenshots
        demoLyrics.add(new LyricLine(0, "Chase me, chase me, tell me how you hate"));
        demoLyrics.add(new LyricLine(3600, "You ain't gotta say it baby, I know"));
        demoLyrics.add(new LyricLine(7200, "I know, I know, I know"));
        demoLyrics.add(new LyricLine(10800, "Looking at the sky with stars in our eyes"));
        demoLyrics.add(new LyricLine(14400, "Running through the night, never look behind"));
    }

    private LyricsManager() {}

    public static void init() {
        demoStartTime = System.currentTimeMillis();
        EXECUTOR.scheduleWithFixedDelay(LyricsManager::pollMediaSource, 400, 400, TimeUnit.MILLISECONDS);
    }

    public static void onClientTick(Minecraft mc) {
        if (ModConfig.INSTANCE.lyricsDemoMode && demoStartTime == 0) {
            demoStartTime = System.currentTimeMillis();
        }
    }

    public static void toggleDemoMode() {
        ModConfig.INSTANCE.lyricsDemoMode = !ModConfig.INSTANCE.lyricsDemoMode;
        if (ModConfig.INSTANCE.lyricsDemoMode) {
            demoStartTime = System.currentTimeMillis();
        }
        ModConfig.INSTANCE.save();
    }

    private static volatile boolean isFetchingLyrics = false;

    public static boolean isFetchingLyrics() {
        return isFetchingLyrics;
    }

    public static synchronized int getLyricsLineCount() {
        return ModConfig.INSTANCE.lyricsDemoMode ? demoLyrics.size() : activeLyrics.size();
    }

    private static void pollMediaSource() {
        if (ModConfig.INSTANCE.lyricsDemoMode) return;
        Minecraft mc = Minecraft.getInstance();
        boolean inConfigScreen = mc != null && mc.gui != null && mc.gui.screen() instanceof com.wish.client.gui.WishConfigScreen;
        if (!ModConfig.INSTANCE.enableLyrics && !inConfigScreen) return;

        try {
            String configuredProvider = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider.trim() : "";

            // 1. On Linux, playerctl is local MPRIS IPC with zero latency and no HTTP rate limits
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                pollLinuxPlayerctl();
                return;
            }

            // 2. Direct companion query if YouTube Music provider is selected
            if ("YTM".equalsIgnoreCase(configuredProvider) || "YouTube Music".equalsIgnoreCase(configuredProvider)) {
                if (pollYtmCompanion()) {
                    return;
                }
            }

            // 3. Fallback to OS-specific media listeners
            if ("YTM".equalsIgnoreCase(configuredProvider)) {
                pollYtmCompanion();
            } else {
                pollWindowsMedia();
            }
        } catch (Exception e) {
            LOGGER.debug("Error polling media source", e);
        }
    }

    private static boolean isAllowedMusicPlayer(String playerName, String configuredProvider) {
        if (playerName == null || playerName.isEmpty()) return false;
        String p = playerName.toLowerCase().trim();

        // 1. Strictly block all web browsers and general video/media players
        if (p.contains("chromium") || p.contains("chrome") || p.contains("firefox")
                || p.contains("brave") || p.contains("edge") || p.contains("opera")
                || p.contains("vivaldi") || p.contains("zen") || p.contains("epiphany")
                || p.contains("safari") || p.contains("vlc") || p.contains("mpv")
                || p.contains("totem") || p.contains("celluloid") || p.contains("plasma-browser-integration")
                || p.contains("kdeconnect") || p.contains("systemd")) {
            return false;
        }

        String prov = configuredProvider != null ? configuredProvider.trim() : "";

        boolean isYtm = p.contains("ytm") || p.contains("youtube-music") || p.contains("youtubemusic")
                || p.contains("youtube_music") || p.contains("th_ch");
        boolean isSpotify = p.contains("spotify") || p.startsWith("ncspot");
        boolean isDeezer = p.contains("deezer") || p.contains("deezplayer");

        if ("YTM".equalsIgnoreCase(prov) || "YouTube Music".equalsIgnoreCase(prov)) {
            return isYtm;
        } else if ("Spotify".equalsIgnoreCase(prov)) {
            return isSpotify;
        } else if ("Deezer".equalsIgnoreCase(prov)) {
            return isDeezer;
        }

        // When provider is "None" or not specifically set, accept ONLY YTM, Spotify, or Deezer
        return isYtm || isSpotify || isDeezer;
    }

    private static String decodeMprisTrackId(String trackId) {
        if (trackId == null || trackId.isEmpty()) return "";
        int lastSlash = trackId.lastIndexOf('/');
        String segment = (lastSlash >= 0 && lastSlash < trackId.length() - 1)
            ? trackId.substring(lastSlash + 1).trim()
            : trackId.trim();
        segment = segment.replace("'", "").replace("\"", "");
        // Unescape D-Bus hex escape sequences like _2d_ (-) and _5f_ (_)
        Matcher m = Pattern.compile("_([0-9a-fA-F]{2})_").matcher(segment);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            char c = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(c)));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    private static String getLocalYtmDesktopVideoId() {
        try {
            java.io.File cfg = new java.io.File(System.getProperty("user.home"), ".config/YouTube Music Desktop App/config.json");
            if (cfg.exists() && cfg.isFile()) {
                String str = java.nio.file.Files.readString(cfg.toPath(), StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(str).getAsJsonObject();
                if (obj.has("state") && obj.getAsJsonObject("state").has("lastVideoId")) {
                    String v = obj.getAsJsonObject("state").get("lastVideoId").getAsString().trim();
                    if (v.length() == 11) return v;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void pollLinuxPlayerctl() {
        try {
            long tStart = System.currentTimeMillis();
            String configuredProvider = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider.trim() : "";

            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                "playerctl --all-players metadata --format '{{playerName}}|||{{status}}|||{{position}}|||{{artist}}|||{{title}}|||{{mpris:length}}|||{{mpris:trackid}}' 2>/dev/null");
            Process p = pb.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.contains("|||")) lines.add(l);
                }
            } finally {
                if (p.isAlive()) p.destroyForcibly();
            }

            long latency = System.currentTimeMillis() - tStart;

            if (lines.isEmpty()) {
                if ("YTM".equalsIgnoreCase(configuredProvider)) {
                    pollYtmCompanion();
                } else {
                    isPlaying = false;
                }
                return;
            }

            String bestLine = null;
            int bestScore = Integer.MIN_VALUE;

            for (String l : lines) {
                String[] parts = l.split("\\|\\|\\|", -1);
                if (parts.length < 5) continue;
                String playerName = parts[0].trim().toLowerCase();
                String status = parts[1].trim();
                String artist = parts[3].trim();
                String title = parts[4].trim();

                // Strictly filter only target music services (never capture browser!)
                if (!isAllowedMusicPlayer(playerName, configuredProvider)) {
                    continue;
                }

                int score = 0;
                boolean isPl = "Playing".equalsIgnoreCase(status);
                if (isPl) score += 1000;

                if ("YTM".equalsIgnoreCase(configuredProvider) && (playerName.contains("ytm") || playerName.contains("youtube"))) {
                    score += 500;
                } else if ("Spotify".equalsIgnoreCase(configuredProvider) && playerName.contains("spotify")) {
                    score += 500;
                } else if ("Deezer".equalsIgnoreCase(configuredProvider) && playerName.contains("deezer")) {
                    score += 500;
                }

                if (!artist.isEmpty()) score += 100;
                if (!title.isEmpty()) score += 50;

                if (score > bestScore) {
                    bestScore = score;
                    bestLine = l;
                }
            }

            if (bestLine != null) {
                String[] parts = bestLine.split("\\|\\|\\|", -1);
                String playerName = parts[0].trim().toLowerCase();
                String status = parts[1].trim();
                long posUs = 0;
                try {
                    posUs = Long.parseLong(parts[2].trim());
                } catch (Exception ignored) {}
                long posMs = posUs / 1000 + (latency / 2);
                String artist = parts[3].trim();
                String title = parts[4].trim();
                long lenUs = 0;
                if (parts.length >= 6) {
                    try {
                        lenUs = Long.parseLong(parts[5].trim());
                    } catch (Exception ignored) {}
                }
                long durationMs = lenUs / 1000;

                // Only extract YTM videoId when the active player is actually YouTube Music!
                // When Spotify or Deezer is playing, clear lastYtmVideoId so it never contaminates Spotify lyrics.
                currentDetectedPlayer = playerName;
                boolean isYtmPlayer = playerName.contains("ytm") || playerName.contains("youtube") || playerName.contains("th_ch");
                if (isYtmPlayer) {
                    String newVid = "";
                    if (parts.length >= 7) {
                        String decoded = decodeMprisTrackId(parts[6]);
                        if (decoded.length() == 11) {
                            newVid = decoded;
                        }
                    }
                    if (newVid.length() != 11) {
                        String localVid = getLocalYtmDesktopVideoId();
                        if (localVid != null && localVid.length() == 11) {
                            newVid = localVid;
                        }
                    }
                    lastYtmVideoId = newVid;
                } else {
                    lastYtmVideoId = "";
                }

                updateTrackState(title, artist, posMs, durationMs, "Playing".equalsIgnoreCase(status));
            } else if ("YTM".equalsIgnoreCase(configuredProvider)) {
                pollYtmCompanion();
            } else {
                isPlaying = false;
            }
        } catch (Exception ignored) {}
    }

    private static volatile long ytmBackoffUntil = 0;

    private static boolean pollYtmCompanion() {
        if (System.currentTimeMillis() < ytmBackoffUntil) return false;
        try {
            int port = ModConfig.INSTANCE.ytmPort > 0 ? ModConfig.INSTANCE.ytmPort : 9863;
            URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/state");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);
            if (ModConfig.INSTANCE.authToken != null) {
                conn.setRequestProperty("Authorization", ModConfig.INSTANCE.authToken);
            }
            int code = conn.getResponseCode();
            if (code == 429) {
                ytmBackoffUntil = System.currentTimeMillis() + 3000;
                return false;
            }
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();

                    String title = "";
                    String artist = "";
                    long posMs = 0;
                    long durationMs = 0;
                    boolean playing = false;

                    if (json.has("video") && !json.get("video").isJsonNull()) {
                        JsonObject v = json.getAsJsonObject("video");
                        if (v.has("title") && !v.get("title").isJsonNull()) title = v.get("title").getAsString();
                        if (v.has("author") && !v.get("author").isJsonNull()) artist = v.get("author").getAsString();
                        if (v.has("id") && !v.get("id").isJsonNull()) {
                            lastYtmVideoId = v.get("id").getAsString();
                        }
                        if (v.has("durationSeconds") && !v.get("durationSeconds").isJsonNull()) {
                            durationMs = (long) (v.get("durationSeconds").getAsDouble() * 1000);
                        } else if (v.has("totalSeconds") && !v.get("totalSeconds").isJsonNull()) {
                            durationMs = (long) (v.get("totalSeconds").getAsDouble() * 1000);
                        }
                    }
                    if (json.has("player") && !json.get("player").isJsonNull()) {
                        JsonObject pl = json.getAsJsonObject("player");
                        if (pl.has("seekbarCurrentPosition") && !pl.get("seekbarCurrentPosition").isJsonNull()) {
                            posMs = (long) (pl.get("seekbarCurrentPosition").getAsDouble() * 1000);
                        }
                        if (pl.has("trackState") && !pl.get("trackState").isJsonNull()) {
                            playing = (pl.get("trackState").getAsInt() == 1);
                        }
                    }

                    currentDetectedPlayer = "ytm";
                    updateTrackState(title, artist, posMs, durationMs, playing);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String getPowerShellExecutable() {
        String sysRoot = System.getenv("SystemRoot");
        if (sysRoot != null && !sysRoot.isEmpty()) {
            Path defaultPs = Path.of(sysRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.exists(defaultPs)) {
                return defaultPs.toString();
            }
        }
        return "powershell.exe";
    }

    public static void ensureScriptExtracted(Path scriptPath) {
        try {
            try (java.io.InputStream in = LyricsManager.class.getResourceAsStream("/assets/wish/scripts/get_song.ps1")) {
                if (in != null) {
                    byte[] jarBytes = in.readAllBytes();
                    boolean needsWrite = true;
                    if (Files.exists(scriptPath)) {
                        try {
                            byte[] existingBytes = Files.readAllBytes(scriptPath);
                            if (java.util.Arrays.equals(jarBytes, existingBytes)) {
                                needsWrite = false;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (needsWrite) {
                        Files.createDirectories(scriptPath.getParent());
                        Files.write(scriptPath, jarBytes, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                        LOGGER.info("Extracted updated get_song.ps1 to {}", scriptPath.toAbsolutePath());
                    }
                } else {
                    lastDebugError = "get_song.ps1 missing from mod resources";
                }
            }
        } catch (Exception e) {
            lastDebugError = "Failed to extract script: " + e.getMessage();
            LOGGER.warn("Failed to extract get_song.ps1: {}", e.getMessage());
        }
    }

    private static void pollWindowsMedia() {
        try {
            long tStart = System.currentTimeMillis();
            lastPollTimeMs = tStart;
            Path scriptPath = Path.of("config", "wish", "get_song.ps1");
            ensureScriptExtracted(scriptPath);

            if (!Files.exists(scriptPath)) {
                lastDebugError = "get_song.ps1 missing at " + scriptPath.toAbsolutePath();
                return;
            }

            String providerParam = ModConfig.INSTANCE.musicProvider != null && !ModConfig.INSTANCE.musicProvider.equalsIgnoreCase("None")
                ? ModConfig.INSTANCE.musicProvider.trim() : "ALL";
            ProcessBuilder pb = new ProcessBuilder(
                getPowerShellExecutable(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", scriptPath.toAbsolutePath().toString(),
                "-provider", providerParam, "-detailed"
            );
            Process p = pb.start();
            String line = null;
            String errorLine = null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                boolean finished = p.waitFor(3000, TimeUnit.MILLISECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    lastDebugError = "PowerShell timed out (>3000ms)";
                    return;
                }
                line = br.readLine();
                errorLine = er.readLine();
            } finally {
                if (p.isAlive()) p.destroyForcibly();
            }

            if (errorLine != null && !errorLine.trim().isEmpty()) {
                lastDebugError = "PowerShell error: " + errorLine.trim();
                LOGGER.debug("PowerShell stderr: {}", errorLine);
            }

            long latency = System.currentTimeMillis() - tStart;
            lastRawOutput = line != null ? line : (errorLine != null ? "ERR: " + errorLine : "null");
            lastDetectionMethod = "Windows Media / Process";

            if (line != null && !line.trim().isEmpty()) {
                lastDebugError = null;
                if (line.contains("|||")) {
                    String[] parts = line.split("\\|\\|\\|", -1);
                    if (parts.length >= 6) {
                        String playerName = parts[0].trim().toLowerCase();
                        String status = parts[1].trim();
                        long posMs = -1;
                        try {
                            posMs = Long.parseLong(parts[2].trim());
                        } catch (Exception ignored) {}
                        if (posMs >= 0) {
                            posMs += (latency / 2);
                        }
                        String artist = parts[3].trim();
                        String title = parts[4].trim();
                        long durationMs = 0;
                        try {
                            durationMs = Long.parseLong(parts[5].trim());
                        } catch (Exception ignored) {}

                        currentDetectedPlayer = playerName;
                        if (!playerName.contains("ytm") && !playerName.contains("youtube")) {
                            lastYtmVideoId = "";
                        }
                        updateTrackState(title, artist, posMs, durationMs, "Playing".equalsIgnoreCase(status));
                        return;
                    } else if (parts.length >= 3) {
                        String playerName = parts[0].trim().toLowerCase();
                        String artist = parts[1].trim();
                        String title = parts[2].trim();
                        currentDetectedPlayer = playerName;
                        if (!playerName.contains("ytm") && !playerName.contains("youtube")) {
                            lastYtmVideoId = "";
                        }
                        updateTrackState(title, artist, -1, 0, true);
                        return;
                    }
                } else if (line.contains(" - ")) {
                    String[] parts = line.split(" - ", 2);
                    currentDetectedPlayer = "windows";
                    lastYtmVideoId = "";
                    updateTrackState(parts[0].trim(), parts[1].trim(), -1, 0, true);
                    return;
                } else {
                    currentDetectedPlayer = "windows";
                    lastYtmVideoId = "";
                    updateTrackState(line.trim(), "", -1, 0, true);
                    return;
                }
            }
            isPlaying = false;
        } catch (Exception e) {
            lastDebugError = "Poll exception: " + e.getMessage();
            LOGGER.debug("Error polling Windows media", e);
        }
    }

    private static synchronized void updateTrackState(String title, String artist, long progressMs, long durationMs, boolean playing) {
        String t = title != null ? title.trim() : "";
        String a = artist != null ? artist.trim() : "";

        // If artist is missing or generic YouTube title like 'Artist - Song'
        if (a.isEmpty() || a.equalsIgnoreCase("youtube") || a.equalsIgnoreCase("youtube music") || a.equalsIgnoreCase("unknown")) {
            if (t.contains(" - ")) {
                String[] split = t.split(" - ", 2);
                a = split[0].trim();
                t = split[1].trim();
            }
        }

        currentTitle = t;
        currentArtist = a;
        currentDurationMs = durationMs;
        isPlaying = playing;

        String newKey = (currentTitle + "|||" + currentArtist).toLowerCase().trim();
        boolean trackChanged = !newKey.isEmpty() && !newKey.equals(currentTrackKey);
        boolean videoChanged = !lastYtmVideoId.isEmpty() && !lastYtmVideoId.equals(lastFetchedVideoId);

        long now = System.currentTimeMillis();
        if (trackChanged) {
            lastKnownProgressMs = Math.max(0, progressMs);
            lastKnownTimeMs = now;
        } else if (playing) {
            if (progressMs < 0) {
                // Position not reported by player fallback: advance smoothly with real time
                lastKnownProgressMs += Math.max(0, now - lastKnownTimeMs);
                lastKnownTimeMs = now;
            } else if (progressMs <= 500 && lastKnownProgressMs > 2500 && (durationMs <= 0 || lastKnownProgressMs < durationMs - 2000)) {
                // Guard against MPRIS players reporting 0 mid-track
                lastKnownProgressMs += Math.max(0, now - lastKnownTimeMs);
                lastKnownTimeMs = now;
            } else {
                long expected = lastKnownProgressMs + (now - lastKnownTimeMs);
                long diff = Math.abs(progressMs - expected);
                if (diff > 200) {
                    lastKnownProgressMs = Math.max(0, progressMs);
                    lastKnownTimeMs = now;
                } else {
                    lastKnownProgressMs = Math.max(0, expected + (progressMs - expected) / 2);
                    lastKnownTimeMs = now;
                }
            }
        } else {
            if (progressMs >= 0) {
                lastKnownProgressMs = progressMs;
            }
            lastKnownTimeMs = now;
        }

        if (trackChanged || videoChanged) {
            currentTrackKey = newKey;
            lastFetchedVideoId = lastYtmVideoId;
            debugLog("Track changed: '" + currentTitle + "' by '" + currentArtist + "' [Player: " + currentDetectedPlayer + ", Status: " + (isPlaying ? "Playing" : "Paused") + "]");
            fetchLyricsAsync(currentTitle, currentArtist, currentDurationMs);
        }
    }

    public static String cleanTrackTitle(String title) {
        if (title == null) return "";
        String s = title;
        // Strip parenthesized or bracketed suffixes: (feat. ...), (ft. ...), (with ...), (speed up), (official video), (deluxe), etc.
        s = s.replaceAll("(?i)\\s*[\\(\\[][^\\]\\)]*(?:speed|sped|slow|official|audio|video|lyrics?|music video|visualizer|clip|remix|prod|feat|ft\\.|with|hd|4k|version|ver\\.|edit|remaster|deluxe|anniversary|bonus|explicit|clean|live|from\\s)[^\\]\\)]*[\\)\\]]", "");
        // Strip trailing ft./feat./with
        s = s.replaceAll("(?i)\\s*(?:[-–—]\\s*)?(?:ft\\.|feat\\.|featuring|with)\\s+.*$", "");
        // Strip trailing Spotify suffixes like - Remastered 2020, - 2011 Remaster, - Single, - EP, - Radio Edit, - From "..."
        s = s.replaceAll("(?i)\\s*[-–—]\\s*(?:\\d{4}\\s+remaster|remastered|remaster|single|ep|album|radio\\s+edit|original\\s+mix|bonus\\s+track|live|mono|stereo|from\\s+|bande\\s+originale|version).*$", "");
        return s.trim();
    }

    public static String cleanTrackArtist(String artist) {
        if (artist == null) return "";
        String s = artist;
        s = s.replaceAll("(?i)\\s*-\\s*topic$", "");
        return s.trim();
    }

    public static String extractPrimaryArtist(String artist) {
        String clean = cleanTrackArtist(artist);
        if (clean.isEmpty()) return "";
        String[] parts = clean.split("(?i)\\s*(?:,|;|/|&|\\bet\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b|\\bx\\b)\\s*");
        if (parts.length > 0 && !parts[0].trim().isEmpty()) {
            return parts[0].trim();
        }
        return clean;
    }

    private static final java.util.Map<String, List<LyricLine>> LYRICS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static void fetchLyricsAsync(String title, String artist, long durationMs) {
        String cacheKey = (title + "|||" + artist + "|||" + lastYtmVideoId).toLowerCase().trim();
        synchronized (activeLyrics) {
            activeLyrics.clear();
        }
        List<LyricLine> cached = LYRICS_CACHE.get(cacheKey);
        if (cached != null) {
            synchronized (activeLyrics) {
                activeLyrics.clear();
                activeLyrics.addAll(cached);
            }
            isFetchingLyrics = false;
            lastLyricsSource = "Memory Cache";
            lastLyricsCount = cached.size();
            debugLog("Loaded " + cached.size() + " lyric lines from cache for '" + title + "'");
            return;
        }

        isFetchingLyrics = true;
        LYRICS_FETCH_EXECUTOR.execute(() -> {
            try {
                debugLog("Searching lyrics for '" + title + "' by '" + artist + "' (duration: " + (durationMs / 1000) + "s)...");
                List<LyricLine> parsed = fetchLyricsFromAllSources(title, artist, durationMs);
                synchronized (activeLyrics) {
                    activeLyrics.clear();
                    if (parsed != null && !parsed.isEmpty()) {
                        activeLyrics.addAll(parsed);
                        LYRICS_CACHE.put(cacheKey, parsed);
                        lastLyricsCount = parsed.size();
                        debugLog("Loaded " + parsed.size() + " lyric lines from " + lastLyricsSource + " for '" + title + "'!");
                    } else {
                        lastLyricsCount = 0;
                        debugLog("No synchronized lyrics found online for '" + title + "'. Showing floating music notes.");
                    }
                }
            } finally {
                isFetchingLyrics = false;
            }
        });
    }

    private static List<LyricLine> fetchLyricsFromAllSources(String title, String artist, long durationMs) {
        lastAttemptedSong = title + (artist.isEmpty() ? "" : " - " + artist);

        // 0. Highest priority: User's local .lrc file in .minecraft/config/wish/lyrics/
        List<LyricLine> local = loadLocalLyrics(title, artist);
        if (local != null && !local.isEmpty()) {
            lastLyricsSource = "Local LRC file (" + local.size() + " lines)";
            return local;
        }

        String detPlayer = currentDetectedPlayer != null ? currentDetectedPlayer.toLowerCase() : "";
        boolean isSpotifyOrDeezer = "Spotify".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || "Deezer".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || detPlayer.contains("spotify")
            || detPlayer.contains("ncspot")
            || detPlayer.contains("deezer");

        // 1. When using YouTube Music: Try fetching official synchronized lyrics from YouTube Music first
        boolean isYtm = !isSpotifyOrDeezer && (
            "YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || "YouTube Music".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || detPlayer.contains("ytm")
            || detPlayer.contains("youtube")
            || (lastYtmVideoId != null && !lastYtmVideoId.isEmpty())
        );
        if (isYtm) {
            List<LyricLine> ytmLyrics = fetchLyricsFromYouTubeMusic(lastYtmVideoId, title, artist, durationMs, true);
            if (ytmLyrics != null && !ytmLyrics.isEmpty()) {
                LOGGER.info("Using official synchronized YouTube Music lyrics for '{}' ({} lines)", title, ytmLyrics.size());
                lastLyricsSource = "YouTube Music (" + ytmLyrics.size() + " lines)";
                return ytmLyrics;
            }
            LOGGER.info("No official YouTube Music lyrics found for '{}', falling back to LRCLIB and NetEase...", title);
        }

        String cleanTitle = cleanTrackTitle(title);
        String cleanArtist = cleanTrackArtist(artist);
        String primaryArtist = extractPrimaryArtist(artist);
        boolean isRemix = isRemixOrEdit(title);

        List<LyricLine> res = null;

        // 2. Primary for speedup / remix: Search LRCLIB with the exact full title first
        // (If a speedup-specific synced .lrc exists, it is timed perfectly to the fast audio)
        if (isRemix) {
            res = fetchLyricsFromLrclib(title, cleanArtist, durationMs);
            if (res != null && !res.isEmpty()) {
                lastLyricsSource = "LRCLIB Remix (" + res.size() + " lines)";
                return res;
            }

            if (!primaryArtist.equalsIgnoreCase(cleanArtist)) {
                res = fetchLyricsFromLrclib(title, primaryArtist, durationMs);
                if (res != null && !res.isEmpty()) {
                    lastLyricsSource = "LRCLIB Remix Primary (" + res.size() + " lines)";
                    return res;
                }
            }

            res = fetchLyricsFromNetEase(title, primaryArtist, durationMs);
            if (res != null && !res.isEmpty()) {
                lastLyricsSource = "NetEase Remix (" + res.size() + " lines)";
                return res;
            }
        }

        // 3. Primary for Spotify / Deezer: LRCLIB with clean title & artist (and primary artist fallback)
        res = fetchLyricsFromLrclib(cleanTitle, cleanArtist, durationMs);
        if (res != null && !res.isEmpty()) {
            lastLyricsSource = "LRCLIB (" + res.size() + " lines)";
            return res;
        }
        if (!primaryArtist.equalsIgnoreCase(cleanArtist) && !primaryArtist.isEmpty()) {
            res = fetchLyricsFromLrclib(cleanTitle, primaryArtist, durationMs);
            if (res != null && !res.isEmpty()) {
                lastLyricsSource = "LRCLIB Primary Artist (" + res.size() + " lines)";
                return res;
            }
        }

        // 4. Official Musixmatch / LyricFind timed lyrics via YouTube Music Songs catalog
        // (Spotify uses Musixmatch, so tracks that have lyrics on Spotify but are missing on LRCLIB are found here!)
        if (!isRemix) {
            res = fetchLyricsFromYouTubeMusic(null, cleanTitle, primaryArtist, durationMs, false);
            if (res != null && !res.isEmpty()) {
                LOGGER.info("Using official Musixmatch/YTM timed lyrics for Spotify track '{}' ({} lines)", title, res.size());
                lastLyricsSource = "Musixmatch/YTM (" + res.size() + " lines)";
                return res;
            }
        }

        // 5. Fallback: NetEase Cloud Music with clean title & primary artist + duration matching
        res = fetchLyricsFromNetEase(cleanTitle, primaryArtist, durationMs);
        if (res != null && !res.isEmpty()) {
            lastLyricsSource = "NetEase (" + res.size() + " lines)";
            return res;
        }

        // 6. Final fallback: LRCLIB with raw title & primary artist
        if (!cleanTitle.equalsIgnoreCase(title)) {
            res = fetchLyricsFromLrclib(title, primaryArtist, durationMs);
            if (res != null && !res.isEmpty()) {
                lastLyricsSource = "LRCLIB Raw Title (" + res.size() + " lines)";
                return res;
            }
        }

        // If no accurate lyrics found, return empty so only floating notes/emojis show!
        lastLyricsSource = "None found";
        return Collections.emptyList();
    }

    public static boolean isSpeedUpTrack(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.contains("speedup") || lower.contains("speed up") || lower.contains("sped up")
            || lower.contains("nightcore") || lower.contains("acceleré") || lower.contains("accelere")
            || lower.contains("fast ver");
    }

    private static boolean isRemixOrEdit(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return isSpeedUpTrack(title) || lower.contains("remix") || lower.contains("slowed")
            || lower.contains("edit") || lower.contains("flip") || lower.contains("mix")
            || lower.contains("mashup") || lower.contains("bootleg");
    }

    private static List<LyricLine> adjustTimingsForRemix(List<LyricLine> lines, String title, long actualDurationMs) {
        return adjustTimingsForRemix(lines, title, actualDurationMs, 0);
    }

    private static List<LyricLine> adjustTimingsForRemix(List<LyricLine> lines, String title, long actualDurationMs, long candidateDurationMs) {
        if (lines == null || lines.size() < 2) return lines;
        String lower = title != null ? title.toLowerCase() : "";
        boolean isSpeedUp = isSpeedUpTrack(title);
        boolean isSlowed = lower.contains("slowed") || lower.contains("slow down");

        // 1. If candidate duration matches audio duration (within 8 seconds), the lyrics are ALREADY timed to this version!
        if (candidateDurationMs > 0 && actualDurationMs > 0 && Math.abs(candidateDurationMs - actualDurationMs) <= 8000) {
            LOGGER.info("Lyrics for '{}' already match track duration ({}s vs {}s). No scaling needed.", title, candidateDurationMs / 1000, actualDurationMs / 1000);
            return lines;
        }

        // Reject length mismatches (e.g. 69s TikTok edit vs 294s original song) to avoid displaying wrong verse
        if (candidateDurationMs > 0 && actualDurationMs > 0 && (candidateDurationMs > actualDurationMs * 1.5 || actualDurationMs > candidateDurationMs * 1.5)) {
            LOGGER.info("Rejecting lyrics for '{}': length mismatch (audio: {}s, candidate: {}s). Floating musical emojis will be shown.", title, actualDurationMs / 1000, candidateDurationMs / 1000);
            return Collections.emptyList();
        }

        // 2. Precise tempo scaling when lyrics from a differently-paced version were matched
        double ratio = 1.0;
        if (isSpeedUp && candidateDurationMs > 0 && actualDurationMs > 0 && candidateDurationMs > actualDurationMs + 8000) {
            double r = (double) actualDurationMs / (double) candidateDurationMs;
            if (r >= 0.55 && r <= 0.95) {
                ratio = r;
            }
        } else if (isSlowed && candidateDurationMs > 0 && actualDurationMs > 0 && candidateDurationMs < actualDurationMs - 8000) {
            double r = (double) actualDurationMs / (double) candidateDurationMs;
            if (r >= 1.05 && r <= 1.55) {
                ratio = r;
            }
        } else if (isSpeedUp && actualDurationMs > 0) {
            // No candidate duration known: only scale if the last lyric line extends beyond fast audio duration
            long lastLyricMs = lines.get(lines.size() - 1).timeMs;
            if (lastLyricMs > actualDurationMs * 0.95) {
                double r = (double) actualDurationMs / (double) (lastLyricMs + 6000);
                if (r >= 0.60 && r <= 0.95) {
                    ratio = r;
                }
            }
        }

        if (Math.abs(ratio - 1.0) > 0.03) {
            LOGGER.info("Adjusting lyrics tempo for '{}' by factor {}", title, String.format(java.util.Locale.US, "%.2f", ratio));
            List<LyricLine> scaled = new ArrayList<>();
            for (LyricLine l : lines) {
                long scaledEnd = l.endTimeMs > 0 ? (long) (l.endTimeMs * ratio) : 0L;
                scaled.add(new LyricLine((long) (l.timeMs * ratio), scaledEnd, l.text));
            }
            return scaled;
        }

        // For non-remix tracks, reject candidates whose duration differs by > 20s (e.g. Music Video with long cinematic intro)
        if (!isSpeedUp && !isSlowed && candidateDurationMs > 0 && actualDurationMs > 0 && Math.abs(candidateDurationMs - actualDurationMs) > 20000) {
            LOGGER.info("Skipping candidate for '{}': duration gap too large ({}s vs {}s)", title, candidateDurationMs / 1000, actualDurationMs / 1000);
            return Collections.emptyList();
        }
        return lines;
    }

    private static List<LyricLine> loadLocalLyrics(String title, String artist) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return Collections.emptyList();

            File dir = new File(mc.gameDirectory, "config/wish/lyrics");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 1. Universal user override: custom.lrc
            File customFile = new File(dir, "custom.lrc");
            if (customFile.exists() && customFile.isFile()) {
                String content = Files.readString(customFile.toPath(), StandardCharsets.UTF_8);
                List<LyricLine> lines = parseNetEaseLrc(content);
                if (!lines.isEmpty()) {
                    LOGGER.info("Loaded lyrics from local file: custom.lrc");
                    return lines;
                }
            }

            // 2. Specific song file matches: <title>.lrc, <clean_title>.lrc
            String safeTitle = title.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
            String safeClean = cleanTrackTitle(title).replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();

            File[] candidates = {
                new File(dir, safeTitle + ".lrc"),
                new File(dir, safeClean + ".lrc"),
                new File(dir, (artist + "_" + safeClean).replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase() + ".lrc")
            };

            for (File f : candidates) {
                if (f.exists() && f.isFile()) {
                    String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                    List<LyricLine> lines = parseNetEaseLrc(content);
                    if (!lines.isEmpty()) {
                        LOGGER.info("Loaded lyrics from local file: {}", f.getName());
                        return lines;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking local lyrics files", e);
        }
        return Collections.emptyList();
    }

    private static final String[] METADATA_KEYWORDS = {
        "制作", "作词", "作曲", "编曲", "混音", "母带", "录音", "吉他", "贝斯", "鼓",
        "监制", "出品", "发行", "统筹", "企划", "文案", "封面", "producer", "lyricist",
        "composer", "written by", "arranged by", "mixed by", "mastered by", "produced by",
        "paroles :", "paroles par", "paroles de", "auteur :", "compositeur :", "vocals :", "vocals by",
        "recorded by", "published by", "op:", "sp:"
    };

    private static List<LyricLine> fetchLyricsFromNetEase(String title, String artist, long durationMs) {
        try {
            String query = (title + (artist == null || artist.isEmpty() ? "" : " " + artist)).trim();
            if (query.isEmpty()) return Collections.emptyList();

            String searchUrl = "https://music.163.com/api/search/get/?s=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=1&limit=6";
            String searchJson = httpGet(searchUrl);
            if (searchJson == null || searchJson.isEmpty()) return Collections.emptyList();

            JsonObject root = JsonParser.parseString(searchJson).getAsJsonObject();
            if (!root.has("result") || !root.getAsJsonObject("result").has("songs")) return Collections.emptyList();

            JsonArray songs = root.getAsJsonObject("result").getAsJsonArray("songs");
            List<JsonObject> candidates = new ArrayList<>();
            for (JsonElement el : songs) {
                if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                    candidates.add(el.getAsJsonObject());
                }
            }
            if (durationMs > 0) {
                candidates.sort(Comparator.comparingLong(s -> {
                    long d = s.has("duration") && !s.get("duration").isJsonNull() ? s.get("duration").getAsLong() : 0L;
                    return d > 0 ? Math.abs(d - durationMs) : Long.MAX_VALUE / 2;
                }));
            }

            for (JsonObject s : candidates) {
                long songId = s.get("id").getAsLong();
                long candDur = s.has("duration") && !s.get("duration").isJsonNull() ? s.get("duration").getAsLong() : 0L;
                if (durationMs > 0 && candDur > 0 && !isRemixOrEdit(title) && Math.abs(candDur - durationMs) > 12000) {
                    continue;
                }

                String lyricUrl = "https://music.163.com/api/song/lyric?id=" + songId + "&lv=1&kv=1&tv=-1";
                String lyricJson = httpGet(lyricUrl);
                if (lyricJson == null || lyricJson.isEmpty()) continue;

                JsonObject lObj = JsonParser.parseString(lyricJson).getAsJsonObject();
                if (lObj.has("lrc") && lObj.getAsJsonObject("lrc").has("lyric")) {
                    String rawLrc = lObj.getAsJsonObject("lrc").get("lyric").getAsString();
                    if (rawLrc != null && !rawLrc.trim().isEmpty()) {
                        List<LyricLine> lines = parseNetEaseLrc(rawLrc);
                        if (!lines.isEmpty()) {
                            List<LyricLine> adjusted = adjustTimingsForRemix(lines, title, durationMs, candDur);
                            if (!adjusted.isEmpty()) {
                                LOGGER.info("Successfully fetched synced lyrics from NetEase for '{}' (songId={})", query, songId);
                                return adjusted;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch lyrics from NetEase", e);
        }
        return Collections.emptyList();
    }

    private static List<LyricLine> parseNetEaseLrc(String lrc) {
        List<LyricLine> list = new ArrayList<>();
        if (lrc == null || lrc.isEmpty()) return list;

        String[] lines = lrc.split("\\r?\\n");
        Pattern tagPattern = Pattern.compile("\\[(\\d{1,3}):(\\d{2}(?:[\\.:]\\d+)?)\\]");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")
                    || line.startsWith("[by:") || line.startsWith("[offset:") || line.startsWith("[length:")) {
                continue;
            }

            Matcher tagMatcher = tagPattern.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            int lastTagEnd = 0;

            while (tagMatcher.find()) {
                try {
                    int minutes = Integer.parseInt(tagMatcher.group(1));
                    String secPart = tagMatcher.group(2).replace(':', '.');
                    double seconds = Double.parseDouble(secPart);
                    long totalMs = (long) (minutes * 60000 + seconds * 1000);
                    timestamps.add(totalMs);
                    lastTagEnd = tagMatcher.end();
                } catch (Exception ignored) {}
            }

            if (!timestamps.isEmpty()) {
                String text = line.substring(lastTagEnd).trim();

                // Filter out credit/producer metadata headers
                String lower = text.toLowerCase();
                boolean isMeta = false;
                for (String kw : METADATA_KEYWORDS) {
                    if (lower.contains(kw) && (lower.contains(":") || lower.contains("：") || lower.startsWith(kw))) {
                        isMeta = true;
                        break;
                    }
                }
                if (isMeta) continue;

                for (long t : timestamps) {
                    list.add(new LyricLine(t, text));
                }
            }
        }
        list.sort(Comparator.comparingLong(a -> a.timeMs));
        return list;
    }

    private static List<LyricLine> fetchLyricsFromLrclib(String title, String artist, long durationMs) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String encodedArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8);

            // 1. Direct get endpoint with rounded duration (exact match within ±2s on LRCLIB)
            if (durationMs > 0) {
                long roundedSec = Math.round(durationMs / 1000.0);
                String directUrl = "https://lrclib.net/api/get?track_name=" + encodedTitle + "&artist_name=" + encodedArtist + "&duration=" + roundedSec;
                String rawJson = httpGet(directUrl);
                if (rawJson != null) {
                    List<LyricLine> res = parseLyricsFromJson(rawJson, durationMs, title);
                    if (res != null && !res.isEmpty()) return res;
                }
            }

            // 2. Search endpoints: first structured search (track_name & artist_name), then general search (q)
            String[] searchUrls = {
                "https://lrclib.net/api/search?track_name=" + encodedTitle + (artist.isEmpty() ? "" : "&artist_name=" + encodedArtist),
                "https://lrclib.net/api/search?q=" + URLEncoder.encode(title + (artist.isEmpty() ? "" : " " + artist), StandardCharsets.UTF_8)
            };

            String normTargetTitle = normalizeForMatch(cleanTrackTitle(title));
            String normTargetArtist = normalizeForMatch(extractPrimaryArtist(artist));

            for (String searchUrl : searchUrls) {
                String searchJson = httpGet(searchUrl);
                if (searchJson == null) continue;

                JsonElement root = JsonParser.parseString(searchJson);
                if (!root.isJsonArray()) continue;

                JsonArray arr = root.getAsJsonArray();
                JsonObject bestSynced = null;
                long bestScore = Long.MAX_VALUE;
                long bestGap = Long.MAX_VALUE;

                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) continue;
                    String syncedStr = o.get("syncedLyrics").getAsString();
                    if (syncedStr == null || syncedStr.trim().isEmpty()) continue;

                    String candTrack = o.has("trackName") && !o.get("trackName").isJsonNull() ? o.get("trackName").getAsString() : "";
                    String candArtist = o.has("artistName") && !o.get("artistName").isJsonNull() ? o.get("artistName").getAsString() : "";
                    String normCandTrack = normalizeForMatch(cleanTrackTitle(candTrack));
                    String normCandArtist = normalizeForMatch(candArtist);

                    // Verify title relevance so search never picks an unrelated song
                    if (!normTargetTitle.isEmpty() && !normCandTrack.isEmpty()) {
                        if (!normCandTrack.contains(normTargetTitle) && !normTargetTitle.contains(normCandTrack)) {
                            continue;
                        }
                    }

                    long gap = 0L;
                    if (durationMs > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
                        gap = Math.abs((long) (o.get("duration").getAsDouble() * 1000) - durationMs);
                    }

                    // Penalize candidates where artist doesn't match primary artist
                    long artistPenalty = 0L;
                    if (!normTargetArtist.isEmpty() && !normCandArtist.isEmpty()
                            && !normCandArtist.contains(normTargetArtist) && !normTargetArtist.contains(normCandArtist)) {
                        artistPenalty = 15000L;
                    }

                    long score = gap + artistPenalty;
                    if (score < bestScore) {
                        bestScore = score;
                        bestGap = gap;
                        bestSynced = o;
                    }
                }

                // Accept matching duration: standard songs within 25s, speedup/remix up to 75s gap
                long maxAllowedGap = isRemixOrEdit(title) ? 75000L : 25000L;
                if (bestSynced != null && (durationMs <= 0 || bestGap <= maxAllowedGap)) {
                    List<LyricLine> lines = parseLrcString(bestSynced.get("syncedLyrics").getAsString());
                    if (!lines.isEmpty()) {
                        long candDur = 0;
                        if (bestSynced.has("duration") && !bestSynced.get("duration").isJsonNull()) {
                            candDur = (long) (bestSynced.get("duration").getAsDouble() * 1000);
                        }
                        List<LyricLine> adjusted = adjustTimingsForRemix(lines, title, durationMs, candDur);
                        if (!adjusted.isEmpty()) {
                            return adjusted;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to retrieve lyrics from LRCLIB", e);
        }
        return Collections.emptyList();
    }

    private static String httpGet(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (urlStr.contains("lrclib.net")) {
                lastLrclibStatus = "HTTP " + code;
            } else if (urlStr.contains("163.com")) {
                lastNetEaseStatus = "HTTP " + code;
            }
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            if (urlStr.contains("lrclib.net")) {
                lastLrclibStatus = "Error: " + e.getMessage();
            } else if (urlStr.contains("163.com")) {
                lastNetEaseStatus = "Error: " + e.getMessage();
            }
        }
        return null;
    }

    private static String httpPostJson(String urlStr, String jsonBody) {
        try {
            URI uri = URI.create(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String normalizeForMatch(String text) {
        if (text == null) return "";
        // Strip diacritical marks/accents (é -> e, ç -> c) so French and international titles match
        String norm = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        // Retain unicode letters and digits across all alphabets
        return norm.replaceAll("[^\\p{L}\\p{N} ]", "").toLowerCase().trim();
    }

    private static List<LyricLine> fetchLyricsFromYouTubeMusic(String videoId, String title, String artist, long durationMs, boolean allowLocalYtmConfig) {
        try {
            List<String> candidateVids = new ArrayList<>();
            if (videoId != null && videoId.length() == 11) {
                candidateVids.add(videoId);
            } else if (allowLocalYtmConfig) {
                String localVid = getLocalYtmDesktopVideoId();
                if (localVid != null && localVid.length() == 11) {
                    candidateVids.add(localVid);
                }
            }

            // Search YouTube Music WEB_REMIX API using the "Songs" filter (EgWKAQIIAWoKEAkQBRAKEAMQBA==)
            // so we get official album audio tracks (matching Spotify timing) instead of Music Videos!
            if (candidateVids.isEmpty()) {
                String query = (title + (artist == null || artist.isEmpty() ? "" : " " + artist)).replace("\"", "\\\"").trim();
                String searchSongsBody = "{\"query\":\"" + query + "\",\"params\":\"EgWKAQIIAWoKEAkQBRAKEAMQBA==\",\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\"}}}";
                String searchJson = httpPostJson("https://music.youtube.com/youtubei/v1/search", searchSongsBody);
                if (searchJson != null) {
                    Matcher vm = Pattern.compile("\"videoId\":\\s*\"([a-zA-Z0-9_-]{11})\"").matcher(searchJson);
                    while (vm.find() && candidateVids.size() < 2) {
                        String found = vm.group(1);
                        if (!candidateVids.contains(found)) {
                            candidateVids.add(found);
                        }
                    }
                }
                if (candidateVids.isEmpty() && allowLocalYtmConfig) {
                    String searchAnyBody = "{\"query\":\"" + query + "\",\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\"}}}";
                    String searchAnyJson = httpPostJson("https://music.youtube.com/youtubei/v1/search", searchAnyBody);
                    if (searchAnyJson != null) {
                        Matcher vm = Pattern.compile("\"videoId\":\\s*\"([a-zA-Z0-9_-]{11})\"").matcher(searchAnyJson);
                        if (vm.find()) {
                            candidateVids.add(vm.group(1));
                        }
                    }
                }
            }

            if (candidateVids.isEmpty()) return null;

            String[][] clientPairs = {
                {"ANDROID_MUSIC", "8.05.50"},
                {"WEB_REMIX", "1.20240101.01.00"}
            };

            for (String vid : candidateVids) {
                String lyricsBrowseId = null;
                for (String[] client : clientPairs) {
                    String nextBody = "{\"videoId\":\"" + vid + "\",\"context\":{\"client\":{\"clientName\":\"" + client[0] + "\",\"clientVersion\":\"" + client[1] + "\"}}}";
                    String nextJson = httpPostJson("https://music.youtube.com/youtubei/v1/next", nextBody);
                    if (nextJson == null) continue;

                    try {
                        JsonObject nextObj = JsonParser.parseString(nextJson).getAsJsonObject();
                        JsonArray tabs = nextObj.getAsJsonObject("contents")
                            .getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                            .getAsJsonObject("tabbedRenderer")
                            .getAsJsonObject("watchNextTabbedResultsRenderer")
                            .getAsJsonArray("tabs");

                        for (JsonElement tabEl : tabs) {
                            JsonObject tabR = tabEl.getAsJsonObject().getAsJsonObject("tabRenderer");
                            if (tabR != null && tabR.has("title")) {
                                String tabTitle = tabR.get("title").getAsString();
                                if ("Lyrics".equalsIgnoreCase(tabTitle) || tabTitle.toLowerCase().contains("lyric")) {
                                    if (tabR.has("endpoint")) {
                                        lyricsBrowseId = tabR.getAsJsonObject("endpoint").getAsJsonObject("browseEndpoint").get("browseId").getAsString();
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (lyricsBrowseId != null) break;
                }

                if (lyricsBrowseId == null) continue;

                // Fetch lyrics using browseId with ANDROID_MUSIC timedLyricsData (millisecond accuracy)
                String browseAndroidBody = "{\"browseId\":\"" + lyricsBrowseId + "\",\"context\":{\"client\":{\"clientName\":\"ANDROID_MUSIC\",\"clientVersion\":\"8.05.50\"}}}";
                String browseAndroidJson = httpPostJson("https://music.youtube.com/youtubei/v1/browse", browseAndroidBody);
                if (browseAndroidJson != null) {
                    try {
                        JsonObject browseObj = JsonParser.parseString(browseAndroidJson).getAsJsonObject();
                        List<LyricLine> timedLines = parseYouTubeMusicTimedLyrics(browseObj);
                        if (timedLines != null && !timedLines.isEmpty()) {
                            return timedLines;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch lyrics directly from YouTube Music", e);
        }
        return null;
    }

    private static List<LyricLine> parseYouTubeMusicStaticLyrics(JsonObject browseObj, long durationMs) {
        try {
            if (!browseObj.has("contents")) return null;
            JsonObject contents = browseObj.getAsJsonObject("contents");
            if (!contents.has("sectionListRenderer")) return null;
            JsonArray sections = contents.getAsJsonObject("sectionListRenderer").getAsJsonArray("contents");
            for (JsonElement secEl : sections) {
                JsonObject sec = secEl.getAsJsonObject();
                if (sec.has("musicDescriptionShelfRenderer")) {
                    JsonObject shelf = sec.getAsJsonObject("musicDescriptionShelfRenderer");
                    if (shelf.has("description") && shelf.getAsJsonObject("description").has("runs")) {
                        JsonArray runs = shelf.getAsJsonObject("description").getAsJsonArray("runs");
                        StringBuilder sb = new StringBuilder();
                        for (JsonElement r : runs) {
                            if (r.isJsonObject() && r.getAsJsonObject().has("text")) {
                                sb.append(r.getAsJsonObject().get("text").getAsString());
                            }
                        }
                        String plain = sb.toString().trim();
                        if (!plain.isEmpty()) {
                            return parsePlainLyrics(plain, durationMs);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static List<LyricLine> parseYouTubeMusicTimedLyrics(JsonObject browseObj) {
        try {
            if (!browseObj.has("contents")) return null;
            JsonObject contents = browseObj.getAsJsonObject("contents");
            if (!contents.has("elementRenderer")) return null;
            JsonObject elR = contents.getAsJsonObject("elementRenderer");
            if (!elR.has("newElement")) return null;
            JsonObject newEl = elR.getAsJsonObject("newElement");
            JsonObject type = newEl.getAsJsonObject("type");
            JsonObject compType = type.getAsJsonObject("componentType");
            JsonObject model = compType.getAsJsonObject("model");
            if (!model.has("timedLyricsModel")) return null;
            JsonObject timedModel = model.getAsJsonObject("timedLyricsModel");
            if (!timedModel.has("lyricsData")) return null;
            JsonObject lyricsData = timedModel.getAsJsonObject("lyricsData");
            if (!lyricsData.has("timedLyricsData")) return null;
            JsonArray arr = lyricsData.getAsJsonArray("timedLyricsData");
            if (arr != null && arr.size() > 0) {
                List<LyricLine> list = new ArrayList<>();
                for (JsonElement e : arr) {
                    JsonObject item = e.getAsJsonObject();
                    if (item.has("lyricLine") && !item.get("lyricLine").isJsonNull()) {
                        String text = item.get("lyricLine").getAsString().trim();
                        if (text.equals("♪") || text.equals("♫") || text.equals("♬")) {
                            text = "";
                        }
                        if (item.has("cueRange") && !item.get("cueRange").isJsonNull()) {
                            JsonObject cue = item.getAsJsonObject("cueRange");
                            if (cue.has("startTimeMilliseconds")) {
                                long startMs = Long.parseLong(cue.get("startTimeMilliseconds").getAsString());
                                long endMs = 0L;
                                if (cue.has("endTimeMilliseconds")) {
                                    try {
                                        endMs = Long.parseLong(cue.get("endTimeMilliseconds").getAsString());
                                    } catch (Exception ignored) {}
                                }
                                list.add(new LyricLine(startMs, endMs, text));
                            }
                        }
                    }
                }
                if (!list.isEmpty()) {
                    list.sort(Comparator.comparingLong(a -> a.timeMs));
                    return list;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static List<LyricLine> parseLyricsFromJson(String jsonStr, long durationMs, String title) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            // Strictly require syncedLyrics with timestamps to prevent wrong or unsynced words
            if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                String lrc = obj.get("syncedLyrics").getAsString();
                List<LyricLine> list = parseLrcString(lrc);
                if (!list.isEmpty()) {
                    long candDur = 0;
                    if (obj.has("duration") && !obj.get("duration").isJsonNull()) {
                        candDur = (long) (obj.get("duration").getAsDouble() * 1000);
                    }
                    return adjustTimingsForRemix(list, title, durationMs, candDur);
                }
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    public static List<LyricLine> parsePlainLyrics(String plain, long durationMs) {
        List<LyricLine> list = new ArrayList<>();
        if (plain == null || plain.isEmpty()) return list;

        String[] rawLines = plain.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String trimmed = l.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("[") && !trimmed.endsWith("]")) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) return list;

        long effectiveDuration = durationMs > 5000 ? durationMs : (long) lines.size() * 3500L;
        // Natural intro offset so plain text lyrics never begin abruptly at 0:00
        long introMs = Math.min(8000L, effectiveDuration / 12);
        long vocalDuration = effectiveDuration - introMs - Math.min(6000L, effectiveDuration / 15);
        long step = Math.max(1400L, vocalDuration / Math.max(1, lines.size()));
        for (int i = 0; i < lines.size(); i++) {
            long timeMs = introMs + (i * step);
            list.add(new LyricLine(timeMs, lines.get(i)));
        }
        return list;
    }

    public static List<LyricLine> parseLrcString(String lrc) {
        List<LyricLine> list = new ArrayList<>();
        if (lrc == null || lrc.isEmpty()) return list;

        String[] lines = lrc.split("\\r?\\n");
        Pattern tagPattern = Pattern.compile("\\[(\\d{1,3}):(\\d{2}(?:[\\.:]\\d+)?)\\]");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")
                    || line.startsWith("[by:") || line.startsWith("[offset:") || line.startsWith("[length:")) {
                continue;
            }

            Matcher tagMatcher = tagPattern.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            int lastTagEnd = 0;

            while (tagMatcher.find()) {
                try {
                    int minutes = Integer.parseInt(tagMatcher.group(1));
                    String secPart = tagMatcher.group(2).replace(':', '.');
                    double seconds = Double.parseDouble(secPart);
                    long totalMs = (long) (minutes * 60000 + seconds * 1000);
                    timestamps.add(totalMs);
                    lastTagEnd = tagMatcher.end();
                } catch (Exception ignored) {}
            }

            if (!timestamps.isEmpty()) {
                String text = line.substring(lastTagEnd).trim();
                for (long t : timestamps) {
                    list.add(new LyricLine(t, text));
                }
            }
        }
        list.sort(Comparator.comparingLong(a -> a.timeMs));
        return list;
    }

    public static long getCurrentProgressMs() {
        if (ModConfig.INSTANCE.lyricsDemoMode) {
            long total = 18000; // 18s loop
            long elapsed = (System.currentTimeMillis() - demoStartTime) + ModConfig.INSTANCE.lyricsSyncOffsetMs;
            return ((elapsed % total) + total) % total;
        }
        long base = lastKnownProgressMs;
        if (isPlaying) {
            long elapsed = System.currentTimeMillis() - lastKnownTimeMs;
            base += elapsed;
        }
        return Math.max(0, base + ModConfig.INSTANCE.lyricsSyncOffsetMs);
    }

    public static LyricState getActiveLyricState() {
        boolean isDemo = ModConfig.INSTANCE.lyricsDemoMode;
        List<LyricLine> source = isDemo ? demoLyrics : activeLyrics;

        synchronized (source) {
            if (source.isEmpty()) {
                if (isDemo) {
                    source = demoLyrics;
                } else if (isPlaying && !currentTitle.isEmpty()) {
                    // Fallback when no synced lyrics available: empty line so 3D music note emojis spawn
                    LyricLine line1 = new LyricLine(0, "");
                    return new LyricState(line1, null, 0.5f, 0, true, currentTitle, currentArtist);
                } else {
                    return new LyricState(null, null, 0f, 0, false, "", "");
                }
            }

            long progress = getCurrentProgressMs();

            if (!source.isEmpty() && progress < source.get(0).timeMs) {
                LyricLine first = source.get(0);
                LyricLine introLine = new LyricLine(0, "");
                return new LyricState(introLine, first, 0f, 0, isDemo || isPlaying, isDemo ? "Chase Me" : currentTitle, isDemo ? "Trapex" : currentArtist);
            }

            LyricLine cur = null;
            LyricLine next = null;
            long curStart = 0;
            long curEnd = 0;

            for (int i = 0; i < source.size(); i++) {
                LyricLine line = source.get(i);
                if (line.timeMs <= progress) {
                    cur = line;
                    curStart = line.timeMs;
                    if (i + 1 < source.size()) {
                        next = source.get(i + 1);
                        curEnd = next.timeMs;
                    } else {
                        next = null;
                        curEnd = (line.endTimeMs > curStart) ? line.endTimeMs : (curStart + 4000);
                    }
                } else {
                    if (cur == null && i == 0) {
                        next = line;
                    }
                    break;
                }
            }

            if (cur == null) {
                return new LyricState(null, null, 0f, 0, isDemo || isPlaying, isDemo ? "Chase Me" : currentTitle, isDemo ? "Trapex" : currentArtist);
            }

            long gap = (next != null) ? Math.max(150L, next.timeMs - curStart) : Math.max(150L, curEnd - curStart);
            long effectiveGap = (cur.endTimeMs > curStart && cur.endTimeMs - curStart <= gap)
                ? (cur.endTimeMs - curStart) : gap;

            boolean isFast = isSpeedUpTrack(isDemo ? "" : currentTitle);
            long perWordMs = isFast ? 210L : 270L;
            long wordEstimatedDur = Math.max(450L, cur.words.size() * perWordMs);

            long vocalDur;
            if (effectiveGap <= 1100L) {
                vocalDur = Math.max(120L, (long) (effectiveGap * 0.88));
            } else {
                vocalDur = Math.min((long) (effectiveGap * 0.85), Math.max(500L, wordEstimatedDur));
            }

            // If line singing has completely finished and there's still a noticeable pause until next line, show pauseLine
            if (next != null && gap > 3800 && (progress - curStart) > (vocalDur + 700)) {
                LyricLine pauseLine = new LyricLine(curStart + vocalDur + 700, "");
                return new LyricState(pauseLine, next, 1f, 0, isDemo || isPlaying, isDemo ? "Chase Me" : currentTitle, isDemo ? "Trapex" : currentArtist);
            }

            float lineProg = Math.max(0f, Math.min(1f, (float) (progress - curStart) / vocalDur));
            int wordIdx = 0;
            if (!cur.words.isEmpty() && cur.wordProgressStarts != null && cur.wordProgressStarts.length == cur.words.size()) {
                for (int i = 0; i < cur.wordProgressStarts.length; i++) {
                    if (lineProg >= cur.wordProgressStarts[i]) {
                        wordIdx = i;
                    } else {
                        break;
                    }
                }
            } else if (!cur.words.isEmpty()) {
                wordIdx = Math.min(cur.words.size() - 1, (int) (lineProg * cur.words.size()));
            }

            String title = isDemo ? "Chase Me" : currentTitle;
            String artist = isDemo ? "Trapex" : currentArtist;
            return new LyricState(cur, next, lineProg, wordIdx, isDemo || isPlaying, title, artist);
        }
    }

    public static synchronized String getCurrentTitle() {
        return ModConfig.INSTANCE.lyricsDemoMode ? "Chase Me" : currentTitle;
    }

    public static synchronized String getCurrentArtist() {
        return ModConfig.INSTANCE.lyricsDemoMode ? "Trapex" : currentArtist;
    }

    public static synchronized long getProgressMs() {
        return getCurrentProgressMs();
    }

    public static synchronized long getDurationMs() {
        return ModConfig.INSTANCE.lyricsDemoMode ? 184000L : currentDurationMs;
    }

    public static synchronized boolean isTrackPlaying() {
        return ModConfig.INSTANCE.lyricsDemoMode || isPlaying;
    }

    public static synchronized boolean hasSyncedLyrics() {
        return ModConfig.INSTANCE.lyricsDemoMode || !activeLyrics.isEmpty();
    }

    public static void debugLog(String message) {
        LOGGER.info("[Wish-Lyrics] {}", message);
        if (liveDebug) {
            WishClient.sendLocalChatMessage("§d§l[Lyrics-Debug] §f" + message);
        }
    }

    public static void toggleLiveDebug() {
        liveDebug = !liveDebug;
        if (liveDebug) {
            WishClient.sendLocalChatMessage("§d§l🎵 WISH LYRICS §8» §aLive debug mode ENABLED! Detailed lyrics & player logs will appear in chat.");
        } else {
            WishClient.sendLocalChatMessage("§d§l🎵 WISH LYRICS §8» §cLive debug mode DISABLED.");
        }
    }

    public static void runDiagnostics() {
        WishClient.sendLocalChatMessage("§d§l🎵 WISH LYRICS §8» §eRunning full diagnostics, please wait...");
        LYRICS_FETCH_EXECUTOR.execute(() -> {
            try {
                List<String> report = new ArrayList<>();
                report.add("§d§l━━━━━━━━━━━━ WISH LYRICS DIAGNOSTICS ━━━━━━━━━━━━");

                // 1. Mod Configuration
                boolean lyricsOn = ModConfig.INSTANCE.enableLyrics;
                String prov = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider : "None";
                boolean demo = ModConfig.INSTANCE.lyricsDemoMode;

                report.add("§7[Config] §f3D Lyrics: " + (lyricsOn ? "§aEnabled" : "§cDISABLED §7(/wish -> Visual)"));
                report.add("§7[Config] §fMusic Provider: §b" + prov + ("None".equalsIgnoreCase(prov) ? " §c(No platform selected!)" : ""));
                if (demo) {
                    report.add("§7[Config] §eDemo Mode is currently ACTIVE §7(Looping Chase Me)");
                }

                // 2. OS & Script Check
                String os = System.getProperty("os.name");
                report.add("§7[System] §fOperating System: §e" + os);

                boolean isWin = os.toLowerCase().contains("win");
                boolean isLinux = os.toLowerCase().contains("linux");

                if (isWin) {
                    Path scriptPath = Path.of("config", "wish", "get_song.ps1");
                    ensureScriptExtracted(scriptPath);
                    boolean scriptExists = Files.exists(scriptPath);
                    report.add("§7[System] §fPowerShell Script: " + (scriptExists ? "§aFound (" + scriptPath.toAbsolutePath() + ")" : "§cMISSING!"));
                    report.add("§7[System] §fPowerShell Path: §e" + getPowerShellExecutable());

                    report.add("§7[System] §fQuerying Windows Media & Processes...");
                    try {
                        String providerParam = !prov.equalsIgnoreCase("None") ? prov.trim() : "ALL";
                        ProcessBuilder pb = new ProcessBuilder(
                            getPowerShellExecutable(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                            "-File", scriptPath.toAbsolutePath().toString(),
                            "-provider", providerParam, "-detailed", "-debug"
                        );
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        List<String> psOutput = new ArrayList<>();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                            String l;
                            while ((l = br.readLine()) != null) {
                                psOutput.add(l.trim());
                            }
                        } finally {
                            if (p.isAlive()) p.destroyForcibly();
                        }

                        boolean finished = p.waitFor(6000, TimeUnit.MILLISECONDS);
                        if (!finished) {
                            p.destroyForcibly();
                            report.add("§7[System] §cPowerShell query timed out (>6000ms)!");
                        }

                        String detectedLine = null;
                        List<String> debugLines = new ArrayList<>();
                        List<String> errorLines = new ArrayList<>();
                        for (String l : psOutput) {
                            if (l.startsWith("DEBUG:")) {
                                debugLines.add(l.substring(6).trim());
                            } else if (l.contains("|||") || l.contains(" - ")) {
                                detectedLine = l;
                            } else if (!l.isEmpty()) {
                                errorLines.add(l);
                            }
                        }

                        int spotifyProcs = -1;
                        for (String dbg : debugLines) {
                            if (dbg.contains("Spotify processes:") || dbg.contains("Spotify processes running:")) {
                                try {
                                    String countStr = dbg.substring(dbg.lastIndexOf(':') + 1).trim();
                                    spotifyProcs = Integer.parseInt(countStr);
                                } catch (Exception ignored) {}
                            }
                        }

                        if ("Spotify".equalsIgnoreCase(prov) || "ALL".equalsIgnoreCase(prov)) {
                            if (spotifyProcs == 0) {
                                report.add("§7[Spotify] §c❌ Spotify process not found! Is Spotify open?");
                            } else if (spotifyProcs > 0) {
                                report.add("§7[Spotify] §a✔ Spotify is running §7(" + spotifyProcs + " processes found)");
                            }
                        }

                        if (detectedLine != null) {
                            report.add("§7[Player] §a✔ Track detected: §f" + detectedLine);
                        } else {
                            report.add("§7[Player] §c❌ No media track detected by Windows Media / Processes.");
                            for (String dbg : debugLines) {
                                report.add("§8  ↳ " + dbg);
                            }
                            if (debugLines.isEmpty() && !errorLines.isEmpty()) {
                                for (String err : errorLines) {
                                    report.add("§c  ↳ " + err);
                                }
                            }
                        }
                    } catch (Exception e) {
                        report.add("§7[System] §cError running PowerShell: " + e.getMessage());
                    }
                } else if (isLinux) {
                    report.add("§7[Linux] §fUsing playerctl (MPRIS)");
                }

                // 3. Current Mod State
                report.add("§7[Wish State] §fCurrent Track: §b" + (currentTitle.isEmpty() ? "None" : currentTitle + " §7by §b" + currentArtist));
                report.add("§7[Wish State] §fDetected Player: §e" + (currentDetectedPlayer.isEmpty() ? "None" : currentDetectedPlayer));
                report.add("§7[Wish State] §fPlayback Status: " + (isPlaying ? "§aPlaying" : "§ePaused/Idle"));
                report.add("§7[Wish State] §fActive Lyric Lines: " + (activeLyrics.isEmpty() ? "§c0 loaded" : "§a" + activeLyrics.size() + " lines loaded"));
                if (lastLyricsSource != null && !lastLyricsSource.equals("None")) {
                    report.add("§7[Wish State] §fLast Lyrics Source: §a" + lastLyricsSource);
                }
                if (lastDebugError != null) {
                    report.add("§7[Wish State] §cLast Recorded Error: §f" + lastDebugError);
                }

                // 4. Test Connectivity to LRCLIB
                report.add("§7[Network] §fTesting LRCLIB API connectivity...");
                try {
                    String testUrl = "https://lrclib.net/api/get?track_name=Blinding+Lights&artist_name=The+Weeknd&duration=200";
                    long t0 = System.currentTimeMillis();
                    String json = httpGet(testUrl);
                    long ping = System.currentTimeMillis() - t0;
                    if (json != null && json.contains("syncedLyrics")) {
                        report.add("§7[Network] §a✔ LRCLIB is reachable! (" + ping + "ms response)");
                    } else {
                        report.add("§7[Network] §e⚠ LRCLIB returned unexpected response (" + lastLrclibStatus + ")");
                    }
                } catch (Exception e) {
                    report.add("§7[Network] §c❌ Could not reach LRCLIB: " + e.getMessage());
                }

                // 5. Actionable Advice
                report.add("§d§l──────────────── DIAGNOSTIC SUMMARY ────────────────");
                if (!lyricsOn) {
                    report.add("§c⚠ 3D Lyrics is disabled! Go to /wish -> Visual -> 3D Lyrics and click the dot to enable.");
                } else if ("None".equalsIgnoreCase(prov)) {
                    report.add("§c⚠ No music provider selected! Open /wish -> Song Config or 3D Lyrics and select 'Spotify'.");
                } else if ("YTM".equalsIgnoreCase(prov)) {
                    report.add("§e⚠ Provider is set to 'YTM'. If using Spotify, switch Provider to 'Spotify' in /wish!");
                } else if (currentTitle.isEmpty() && isWin) {
                    report.add("§e💡 Spotify troubleshooting checklist:");
                    report.add("§7  1. Make sure Spotify desktop app is open and playing music.");
                    report.add("§7  2. In Spotify: Settings -> Display -> enable 'Show desktop overlay when using media keys'.");
                    report.add("§7  3. If running Minecraft as Administrator, run Spotify as Administrator too (or vice versa).");
                    report.add("§7  4. If using Spotify Web, note that Spotify Desktop application is required.");
                } else if (!currentTitle.isEmpty() && activeLyrics.isEmpty()) {
                    report.add("§e💡 Track is detected, but no synced lyrics exist on LRCLIB/NetEase for this song.");
                    report.add("§7  You can put your own .lrc file in .minecraft/config/wish/lyrics/<title>.lrc!");
                } else if (!activeLyrics.isEmpty()) {
                    report.add("§a🎉 All systems OK! Lyrics are loaded and synced for '" + currentTitle + "'.");
                }
                report.add("§d§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                for (String line : report) {
                    WishClient.sendLocalChatMessage(line);
                }
            } catch (Exception e) {
                LOGGER.error("Error running lyrics diagnostics", e);
                WishClient.sendLocalChatMessage("§c[Wish] Diagnostics failed: " + e.getMessage());
            }
        });
    }

    public static void testFetchLyrics(String trackQuery) {
        WishClient.sendLocalChatMessage("§d§l🎵 WISH LYRICS §8» §7Testing lyrics lookup for: §f\"" + trackQuery + "\"...");
        LYRICS_FETCH_EXECUTOR.execute(() -> {
            try {
                String title = trackQuery;
                String artist = "";
                if (trackQuery.contains(" - ")) {
                    String[] sp = trackQuery.split(" - ", 2);
                    artist = sp[0].trim();
                    title = sp[1].trim();
                }
                List<LyricLine> res = fetchLyricsFromAllSources(title, artist, 0);
                if (res != null && !res.isEmpty()) {
                    WishClient.sendLocalChatMessage("§d§l🎵 WISH LYRICS §8» §aFound " + res.size() + " lyric lines from " + lastLyricsSource + "!");
                    WishClient.sendLocalChatMessage("§7Line 1: §f" + res.get(0).text + " §8(@" + res.get(0).timeMs + "ms)");
                    if (res.size() > 1) {
                        WishClient.sendLocalChatMessage("§7Line 2: §f" + res.get(1).text + " §8(@" + res.get(1).timeMs + "ms)");
                    }
                } else {
                    WishClient.sendLocalChatMessage("§d§l🎵 WISH LYRICS §8» §cNo synced lyrics found for \"" + trackQuery + "\".");
                }
            } catch (Exception e) {
                WishClient.sendLocalChatMessage("§cError testing lyrics: " + e.getMessage());
            }
        });
    }
}
