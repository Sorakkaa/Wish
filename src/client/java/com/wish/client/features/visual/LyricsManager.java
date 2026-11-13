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

    private static final Pattern WORD_TIMESTAMP_TAG_PATTERN = Pattern.compile("<\\d{1,2}:\\d{2}(?:[\\.:]\\d+)?>|<\\d+(?:[\\.:]\\d+)?>");
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-zA-Z0-9À-ÿ]");
    private static final Pattern MPRIS_HEX_ESCAPE_PATTERN = Pattern.compile("_([0-9a-fA-F]{2})_");
    private static final Pattern LRC_TAG_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2}(?:[\\.:]\\d+)?)\\]");
    private static final Pattern YTM_VIDEO_ID_PATTERN = Pattern.compile("\"videoId\":\\s*\"([a-zA-Z0-9_-]{11})\"");
    private static final Pattern CLEAN_PARENS_PATTERN = Pattern.compile("(?i)\\s*[\\(\\[][^\\]\\)]*(?:speed|sped|slow|official|audio|video|lyrics?|music video|visualizer|clip|remix|prod|feat|ft\\.|with|hd|4k|version|ver\\.|edit|remaster|deluxe|anniversary|bonus|explicit|clean|live|from\\s)[^\\]\\)]*[\\)\\]]");
    private static final Pattern CLEAN_FEAT_PATTERN = Pattern.compile("(?i)\\s*(?:[-–—]\\s*)?(?:ft\\.|feat\\.|featuring|with)\\s+.*$");
    private static final Pattern CLEAN_SUFFIX_PATTERN = Pattern.compile("(?i)\\s*[-–—]\\s*(?:\\d{4}\\s+remaster|remastered|remaster|single|ep|album|radio\\s+edit|original\\s+mix|bonus\\s+track|live|mono|stereo|from\\s+|bande\\s+originale|version).*$");
    private static final Pattern CLEAN_TOPIC_PATTERN = Pattern.compile("(?i)\\s*-\\s*topic$");
    private static final Pattern PRIMARY_ARTIST_SPLIT_PATTERN = Pattern.compile("(?i)\\s*(?:,|;|/|&|\\bet\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b|\\bx\\b)\\s*");
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}");
    private static final Pattern NON_LETTER_DIGIT_PATTERN = Pattern.compile("[^\\p{L}\\p{N} ]");
    private static final Pattern WIN_BROWSER_SUFFIX_PATTERN = Pattern.compile("(?i)\\s+[-\\u2013\\u2014]\\s+(Google Chrome|Microsoft\\u200B?\\s*Edge|Brave|Mozilla Firefox|Opera(\\s+GX)?|Vivaldi|Zen(\\s+Browser)?|Arc|Waterfox)$");
    private static final Pattern WIN_DEEZER_TITLE_PATTERN = Pattern.compile("(?i)(?:[-\\u2013\\u2014|\\u2022]\\s*Deezer$|^Deezer\\s*[-\\u2013\\u2014|\\u2022])");
    private static final Pattern WIN_SPOTIFY_TITLE_PATTERN = Pattern.compile("(?i)(?:[-\\u2013\\u2014|\\u2022]\\s*Spotify(?:\\s+Free|\\s+Premium)?$|^Spotify\\s*[-\\u2013\\u2014|\\u2022])");
    private static final Pattern WIN_YTM_TITLE_PATTERN = Pattern.compile("(?i)(?:[-\\u2013\\u2014|\\u2022]\\s*YouTube Music$)");
    private static final Pattern WIN_PLATFORM_AFFIX_PATTERN = Pattern.compile("(?i)(?:\\s+[-\\u2013\\u2014|\\u2022]\\s*(?:Deezer|YouTube Music|Spotify(?:\\s+Free|\\s+Premium)?)$|^(?:Deezer|Spotify(?:\\s+Free|\\s+Premium)?)\\s*[-\\u2013\\u2014|\\u2022]\\s+)");
    private static final Pattern WIN_BULLET_SPLIT_PATTERN = Pattern.compile("^(.+?)\\s+\\u2022\\s+(.+)$");
    private static final Pattern WIN_DASH_SPLIT_PATTERN = Pattern.compile("^(.+?)\\s+[-\\u2013\\u2014]\\s+(.+)$");
    private static volatile boolean scriptExtractedOnce = false;
    private static volatile long lastWindowsPsPollMs = 0;

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
            String cleaned = text != null ? WORD_TIMESTAMP_TAG_PATTERN.matcher(text).replaceAll(" ").trim() : "";
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
                    String clean = NON_ALNUM_PATTERN.matcher(w).replaceAll("");
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

        public LyricState(LyricLine currentLine, LyricLine nextLine, float lineProgress, int activeWordIndex, boolean active, String title) {
            this.currentLine = currentLine;
            this.nextLine = nextLine;
            this.lineProgress = lineProgress;
            this.activeWordIndex = activeWordIndex;
            this.active = active;
            this.title = title != null ? title : "";
        }
    }

    private static final List<LyricLine> activeLyrics = new ArrayList<>();
    private static final List<LyricLine> demoLyrics = new ArrayList<>();
    private static final LyricLine EMPTY_LINE = new LyricLine(0, "");
    private static final LyricState INACTIVE_STATE = new LyricState(null, null, 0f, 0, false, "");
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

    private static volatile boolean isFetchingLyrics = false;

    public static boolean isFetchingLyrics() {
        return isFetchingLyrics;
    }

    public static int getLyricsLineCount() {
        if (ModConfig.INSTANCE.lyricsDemoMode) return demoLyrics.size();
        synchronized (activeLyrics) {
            return activeLyrics.size();
        }
    }

    private static void pollMediaSource() {
        if (ModConfig.INSTANCE.lyricsDemoMode) return;
        Minecraft mc = Minecraft.getInstance();
        boolean inConfigScreen = mc != null && mc.gui != null && mc.gui.screen() instanceof com.wish.client.gui.WishConfigScreen;
        if (!ModConfig.INSTANCE.enableLyrics && !inConfigScreen) {
            isPlaying = false;
            return;
        }

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
            pollWindowsMedia();
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
        Matcher m = MPRIS_HEX_ESCAPE_PATTERN.matcher(segment);
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

            ProcessBuilder pb = new ProcessBuilder(
                "playerctl", "--all-players", "metadata", "--format",
                "{{playerName}}|||{{status}}|||{{position}}|||{{artist}}|||{{title}}|||{{mpris:length}}|||{{mpris:trackid}}"
            );
            Process p = pb.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                if (p.waitFor(1500, TimeUnit.MILLISECONDS)) {
                    String l;
                    while ((l = br.readLine()) != null) {
                        if (l.contains("|||")) lines.add(l);
                    }
                } else {
                    p.destroyForcibly();
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
        if (scriptExtractedOnce && Files.exists(scriptPath)) {
            return;
        }
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
                    scriptExtractedOnce = true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract get_song.ps1: {}", e.getMessage());
        }
    }

    private static boolean isIgnoredWindowsMediaTitle(String title) {
        if (title == null) return true;
        String low = title.trim().toLowerCase();
        if (low.isEmpty()) return true;
        switch (low) {
            case "spotify":
            case "spotify free":
            case "spotify premium":
            case "deezer":
            case "deezer - music & podcasts":
            case "youtube music":
            case "youtube music desktop app":
            case "advertisement":
            case "publicité":
            case "publicite":
            case "pub":
            case "audio ad":
            case "default ime":
            case "msctfime ui":
            case "gdi+ window":
            case "anglehiddenwindow":
            case "systemtray_main":
            case "activemovie window":
            case "cicerouiwndframe":
            case "mediacontextnotificationwindow":
                return true;
            default:
                return low.startsWith("publicit") || low.startsWith("gdi+ window");
        }
    }

    /**
     * Native Win32 JNA window enumeration fallback (< 1ms, zero process spawning).
     * Returns String[]{ matchedProvider, artist, title } or null if no active track window is found.
     */
    public static String[] detectWindowsTrackViaJna(String providerParam) {
        String prov = providerParam != null ? providerParam.trim() : "ALL";
        final List<String[]> candidates = new ArrayList<>();
        try {
            com.sun.jna.platform.win32.User32 user32 = com.sun.jna.platform.win32.User32.INSTANCE;
            user32.EnumWindows((hWnd, data) -> {
                try {
                    if (!user32.IsWindowVisible(hWnd)) {
                        return true;
                    }
                    int textLen = user32.GetWindowTextLength(hWnd);
                    if (textLen <= 0 || textLen > 512) {
                        return true;
                    }
                    char[] titleBuf = new char[textLen + 1];
                    int readLen = user32.GetWindowText(hWnd, titleBuf, titleBuf.length);
                    if (readLen <= 0) {
                        return true;
                    }
                    String rawTitle = new String(titleBuf, 0, readLen).trim();
                    if (rawTitle.isEmpty() || isIgnoredWindowsMediaTitle(rawTitle)) {
                        return true;
                    }

                    com.sun.jna.ptr.IntByReference pidRef = new com.sun.jna.ptr.IntByReference();
                    user32.GetWindowThreadProcessId(hWnd, pidRef);
                    long pid = Integer.toUnsignedLong(pidRef.getValue());
                    String exeName = "";
                    try {
                        exeName = ProcessHandle.of(pid)
                            .flatMap(ph -> ph.info().command())
                            .map(cmd -> {
                                int slash = Math.max(cmd.lastIndexOf('\\'), cmd.lastIndexOf('/'));
                                return (slash >= 0 ? cmd.substring(slash + 1) : cmd).toLowerCase();
                            })
                            .orElse("");
                    } catch (Throwable ignored) {}
                    if (exeName.isEmpty()) {
                        try {
                            String fp = com.sun.jna.platform.WindowUtils.getProcessFilePath(hWnd);
                            if (fp != null && !fp.isEmpty()) {
                                int slash = Math.max(fp.lastIndexOf('\\'), fp.lastIndexOf('/'));
                                exeName = (slash >= 0 ? fp.substring(slash + 1) : fp).toLowerCase();
                            }
                        } catch (Throwable ignored) {}
                    }

                    String cleanTitle = WIN_BROWSER_SUFFIX_PATTERN.matcher(rawTitle).replaceAll("").trim();

                    String matchedProvider = "";
                    boolean isNativeApp = false;
                    if (exeName.contains("deezer")) {
                        matchedProvider = "deezer";
                        isNativeApp = true;
                    } else if (exeName.contains("spotify")) {
                        matchedProvider = "spotify";
                        isNativeApp = true;
                    } else if (exeName.contains("youtubemusic") || exeName.contains("youtube-music") || exeName.contains("youtube music")) {
                        matchedProvider = "youtubemusic";
                        isNativeApp = true;
                    } else if (WIN_DEEZER_TITLE_PATTERN.matcher(cleanTitle).find()) {
                        matchedProvider = "deezer";
                    } else if (WIN_SPOTIFY_TITLE_PATTERN.matcher(cleanTitle).find()) {
                        matchedProvider = "spotify";
                    } else if (WIN_YTM_TITLE_PATTERN.matcher(cleanTitle).find()) {
                        matchedProvider = "youtubemusic";
                    }

                    if (matchedProvider.isEmpty()) {
                        return true;
                    }

                    if ("Deezer".equalsIgnoreCase(prov) && !"deezer".equals(matchedProvider)) return true;
                    if (("YTM".equalsIgnoreCase(prov) || "YouTube Music".equalsIgnoreCase(prov)) && !"youtubemusic".equals(matchedProvider)) return true;
                    if ("Spotify".equalsIgnoreCase(prov) && !"spotify".equals(matchedProvider)) return true;

                    String clean = WIN_PLATFORM_AFFIX_PATTERN.matcher(cleanTitle).replaceAll("").trim();
                    if (isIgnoredWindowsMediaTitle(clean)) {
                        return true;
                    }

                    String artist = "";
                    String title = clean;
                    Matcher bulletMatcher = WIN_BULLET_SPLIT_PATTERN.matcher(clean);
                    Matcher dashMatcher = WIN_DASH_SPLIT_PATTERN.matcher(clean);
                    if (bulletMatcher.matches()) {
                        title = bulletMatcher.group(1).trim();
                        artist = bulletMatcher.group(2).trim();
                    } else if (dashMatcher.matches()) {
                        if ("spotify".equals(matchedProvider)) {
                            artist = dashMatcher.group(1).trim();
                            title = dashMatcher.group(2).trim();
                        } else {
                            title = dashMatcher.group(1).trim();
                            artist = dashMatcher.group(2).trim();
                        }
                    } else if (isNativeApp && "spotify".equals(matchedProvider)) {
                        // Native Spotify.exe always formats playing tracks as "Artist - Song Title"
                        return true;
                    }

                    if (!isIgnoredWindowsMediaTitle(title)) {
                        candidates.add(new String[]{ matchedProvider, artist, title, isNativeApp ? "1" : "0" });
                    }
                } catch (Throwable ignored) {}
                return true;
            }, null);
        } catch (Throwable e) {
            LOGGER.debug("JNA window enumeration fallback error", e);
        }

        if (candidates.isEmpty()) {
            return null;
        }
        // Prefer native desktop apps over browser tabs
        candidates.sort((a, b) -> b[3].compareTo(a[3]));
        String[] best = candidates.get(0);
        return new String[]{ best[0], best[1], best[2] };
    }

    private static void pollWindowsMedia() {
        try {
            long now = System.currentTimeMillis();
            String providerParam = ModConfig.INSTANCE.musicProvider != null && !ModConfig.INSTANCE.musicProvider.equalsIgnoreCase("None")
                ? ModConfig.INSTANCE.musicProvider.trim() : "ALL";

            // 1. Fast native Win32 JNA check (< 1ms) on every poll to detect track changes without process overhead
            String[] jnaTrack = detectWindowsTrackViaJna(providerParam);
            boolean jnaTrackChanged = false;
            if (jnaTrack != null) {
                String jnaKey = (jnaTrack[2] + "|||" + jnaTrack[1]).toLowerCase().trim();
                jnaTrackChanged = !jnaKey.equals(currentTrackKey);
            }

            // Throttle PowerShell execution to once every 1500ms unless a new track was just detected
            if (!jnaTrackChanged && (now - lastWindowsPsPollMs < 1500)) {
                if (jnaTrack != null) {
                    currentDetectedPlayer = jnaTrack[0];
                    if (!jnaTrack[0].contains("ytm") && !jnaTrack[0].contains("youtube")) {
                        lastYtmVideoId = "";
                    }
                    updateTrackState(jnaTrack[2], jnaTrack[1], -1, currentDurationMs, true);
                }
                return;
            }
            lastWindowsPsPollMs = now;

            long tStart = System.currentTimeMillis();
            Path scriptPath = Path.of("config", "wish", "get_song.ps1");
            ensureScriptExtracted(scriptPath);

            String line = null;
            if (Files.exists(scriptPath)) {
                ProcessBuilder pb = new ProcessBuilder(
                    getPowerShellExecutable(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", scriptPath.toAbsolutePath().toString(),
                    "-provider", providerParam, "-detailed"
                );
                Process p = pb.start();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                     BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    boolean finished = p.waitFor(3000, TimeUnit.MILLISECONDS);
                    if (finished) {
                        String l;
                        while ((l = br.readLine()) != null) {
                            String trimmed = l.trim();
                            if (!trimmed.isEmpty() && !trimmed.startsWith("DEBUG:")) {
                                line = trimmed;
                                if (trimmed.contains("|||")) {
                                    break;
                                }
                            }
                        }
                        String errorLine = er.readLine();
                        if (errorLine != null && !errorLine.trim().isEmpty()) {
                            LOGGER.debug("PowerShell stderr: {}", errorLine);
                        }
                    } else {
                        p.destroyForcibly();
                    }
                } finally {
                    if (p.isAlive()) p.destroyForcibly();
                }
            }

            long latency = System.currentTimeMillis() - tStart;

            if (line != null && !line.trim().isEmpty()) {
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

            // 2. Fallback to JNA window title detection if PowerShell returned nothing or failed
            if (jnaTrack != null) {
                currentDetectedPlayer = jnaTrack[0];
                if (!jnaTrack[0].contains("ytm") && !jnaTrack[0].contains("youtube")) {
                    lastYtmVideoId = "";
                }
                updateTrackState(jnaTrack[2], jnaTrack[1], -1, 0, true);
                return;
            }

            isPlaying = false;
        } catch (Exception e) {
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
        boolean trackChanged = !currentTitle.isEmpty() && !newKey.equals(currentTrackKey);
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
            LOGGER.debug("Track changed: '{}' by '{}' [Player: {}, Status: {}]", currentTitle, currentArtist, currentDetectedPlayer, isPlaying ? "Playing" : "Paused");
            fetchLyricsAsync(currentTitle, currentArtist, currentDurationMs, newKey);
        }
    }

    public static String cleanTrackTitle(String title) {
        if (title == null) return "";
        String s = CLEAN_PARENS_PATTERN.matcher(title).replaceAll("");
        s = CLEAN_FEAT_PATTERN.matcher(s).replaceAll("");
        s = CLEAN_SUFFIX_PATTERN.matcher(s).replaceAll("");
        return s.trim();
    }

    public static String cleanTrackArtist(String artist) {
        if (artist == null) return "";
        return CLEAN_TOPIC_PATTERN.matcher(artist).replaceAll("").trim();
    }

    public static String extractPrimaryArtist(String artist) {
        String clean = cleanTrackArtist(artist);
        if (clean.isEmpty()) return "";
        String[] parts = PRIMARY_ARTIST_SPLIT_PATTERN.split(clean);
        if (parts.length > 0 && !parts[0].trim().isEmpty()) {
            return parts[0].trim();
        }
        return clean;
    }

    private static final java.util.Map<String, List<LyricLine>> LYRICS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static void fetchLyricsAsync(String title, String artist, long durationMs, String expectedTrackKey) {
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
            return;
        }

        isFetchingLyrics = true;
        LYRICS_FETCH_EXECUTOR.execute(() -> {
            try {
                List<LyricLine> parsed = fetchLyricsFromAllSources(title, artist, durationMs);
                synchronized (LyricsManager.class) {
                    if (!expectedTrackKey.equals(currentTrackKey)) {
                        return;
                    }
                }
                synchronized (activeLyrics) {
                    activeLyrics.clear();
                    if (parsed != null && !parsed.isEmpty()) {
                        activeLyrics.addAll(parsed);
                        if (LYRICS_CACHE.size() >= 128) {
                            LYRICS_CACHE.clear();
                        }
                        LYRICS_CACHE.put(cacheKey, parsed);
                    }
                }
            } finally {
                isFetchingLyrics = false;
            }
        });
    }

    private static List<LyricLine> fetchLyricsFromAllSources(String title, String artist, long durationMs) {
        // 0. Highest priority: User's local .lrc file in .minecraft/config/wish/lyrics/
        List<LyricLine> local = loadLocalLyrics(title, artist);
        if (local != null && !local.isEmpty()) {
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
            List<LyricLine> ytmLyrics = fetchLyricsFromYouTubeMusic(lastYtmVideoId, title, artist, true);
            if (ytmLyrics != null && !ytmLyrics.isEmpty()) {
                return ytmLyrics;
            }
        }

        String cleanTitle = cleanTrackTitle(title);
        String cleanArtist = cleanTrackArtist(artist);
        String primaryArtist = extractPrimaryArtist(artist);
        boolean isRemix = isRemixOrEdit(title);

        List<LyricLine> res;

        // 2. Primary for speedup / remix: Search LRCLIB with the exact full title first
        if (isRemix) {
            res = fetchLyricsFromLrclib(title, cleanArtist, durationMs);
            if (res != null && !res.isEmpty()) return res;

            if (!primaryArtist.equalsIgnoreCase(cleanArtist)) {
                res = fetchLyricsFromLrclib(title, primaryArtist, durationMs);
                if (res != null && !res.isEmpty()) return res;
            }

            res = fetchLyricsFromNetEase(title, primaryArtist, durationMs);
            if (res != null && !res.isEmpty()) return res;
        }

        // 3. Primary for Spotify / Deezer: LRCLIB with clean title & artist (and primary artist fallback)
        res = fetchLyricsFromLrclib(cleanTitle, cleanArtist, durationMs);
        if (res != null && !res.isEmpty()) return res;
        if (!primaryArtist.equalsIgnoreCase(cleanArtist) && !primaryArtist.isEmpty()) {
            res = fetchLyricsFromLrclib(cleanTitle, primaryArtist, durationMs);
            if (res != null && !res.isEmpty()) return res;
        }

        // 4. Official Musixmatch / LyricFind timed lyrics via YouTube Music Songs catalog
        if (!isRemix) {
            res = fetchLyricsFromYouTubeMusic(null, cleanTitle, primaryArtist, false);
            if (res != null && !res.isEmpty()) return res;
        }

        // 5. Fallback: NetEase Cloud Music with clean title & primary artist + duration matching
        res = fetchLyricsFromNetEase(cleanTitle, primaryArtist, durationMs);
        if (res != null && !res.isEmpty()) return res;

        // 6. Final fallback: LRCLIB with raw title & primary artist
        if (!cleanTitle.equalsIgnoreCase(title)) {
            res = fetchLyricsFromLrclib(title, primaryArtist, durationMs);
            if (res != null && !res.isEmpty()) return res;
        }

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

    private static List<LyricLine> adjustTimingsForRemix(List<LyricLine> lines, String title, long actualDurationMs, long candidateDurationMs) {
        if (lines == null || lines.size() < 2) return lines;
        String lower = title != null ? title.toLowerCase() : "";
        boolean isSpeedUp = isSpeedUpTrack(title);
        boolean isSlowed = lower.contains("slowed") || lower.contains("slow down");

        // 1. If candidate duration matches audio duration (within 8 seconds), the lyrics are ALREADY timed to this version!
        if (candidateDurationMs > 0 && actualDurationMs > 0 && Math.abs(candidateDurationMs - actualDurationMs) <= 8000) {
            LOGGER.debug("Lyrics for '{}' already match track duration ({}s vs {}s). No scaling needed.", title, candidateDurationMs / 1000, actualDurationMs / 1000);
            return lines;
        }

        // Reject length mismatches (e.g. 69s TikTok edit vs 294s original song) to avoid displaying wrong verse
        if (candidateDurationMs > 0 && actualDurationMs > 0 && (candidateDurationMs > actualDurationMs * 1.5 || actualDurationMs > candidateDurationMs * 1.5)) {
            LOGGER.debug("Rejecting lyrics for '{}': length mismatch (audio: {}s, candidate: {}s). Floating musical emojis will be shown.", title, actualDurationMs / 1000, candidateDurationMs / 1000);
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
            LOGGER.debug("Adjusting lyrics tempo for '{}' by factor {}", title, String.format(java.util.Locale.US, "%.2f", ratio));
            List<LyricLine> scaled = new ArrayList<>();
            for (LyricLine l : lines) {
                long scaledEnd = l.endTimeMs > 0 ? (long) (l.endTimeMs * ratio) : 0L;
                scaled.add(new LyricLine((long) (l.timeMs * ratio), scaledEnd, l.text));
            }
            return scaled;
        }

        // For non-remix tracks, reject candidates whose duration differs by > 20s (e.g. Music Video with long cinematic intro)
        if (!isSpeedUp && !isSlowed && candidateDurationMs > 0 && actualDurationMs > 0 && Math.abs(candidateDurationMs - actualDurationMs) > 20000) {
            LOGGER.debug("Skipping candidate for '{}': duration gap too large ({}s vs {}s)", title, candidateDurationMs / 1000, actualDurationMs / 1000);
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
                    LOGGER.debug("Loaded lyrics from local file: custom.lrc");
                    return lines;
                }
            }

            // 2. Specific song file matches: <title>.lrc, <clean_title>.lrc
            String safeTitle = SAFE_FILENAME_PATTERN.matcher(title).replaceAll("_").toLowerCase();
            String safeClean = SAFE_FILENAME_PATTERN.matcher(cleanTrackTitle(title)).replaceAll("_").toLowerCase();

            File[] candidates = {
                new File(dir, safeTitle + ".lrc"),
                new File(dir, safeClean + ".lrc"),
                new File(dir, SAFE_FILENAME_PATTERN.matcher(artist + "_" + safeClean).replaceAll("_").toLowerCase() + ".lrc")
            };

            for (File f : candidates) {
                if (f.exists() && f.isFile()) {
                    String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                    List<LyricLine> lines = parseNetEaseLrc(content);
                    if (!lines.isEmpty()) {
                        LOGGER.debug("Loaded lyrics from local file: {}", f.getName());
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
                                LOGGER.debug("Successfully fetched synced lyrics from NetEase for '{}' (songId={})", query, songId);
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

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")
                    || line.startsWith("[by:") || line.startsWith("[offset:") || line.startsWith("[length:")) {
                continue;
            }

            Matcher tagMatcher = LRC_TAG_PATTERN.matcher(line);
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
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(urlStr);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String httpPostJson(String urlStr, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(urlStr);
            conn = (HttpURLConnection) uri.toURL().openConnection();
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
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String normalizeForMatch(String text) {
        if (text == null) return "";
        String norm = DIACRITICS_PATTERN.matcher(java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)).replaceAll("");
        return NON_LETTER_DIGIT_PATTERN.matcher(norm).replaceAll("").toLowerCase().trim();
    }

    private static List<LyricLine> fetchLyricsFromYouTubeMusic(String videoId, String title, String artist, boolean allowLocalYtmConfig) {
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

            if (candidateVids.isEmpty()) {
                String query = (title + (artist == null || artist.isEmpty() ? "" : " " + artist)).replace("\"", "\\\"").trim();
                String searchSongsBody = "{\"query\":\"" + query + "\",\"params\":\"EgWKAQIIAWoKEAkQBRAKEAMQBA==\",\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\"}}}";
                String searchJson = httpPostJson("https://music.youtube.com/youtubei/v1/search", searchSongsBody);
                if (searchJson != null) {
                    Matcher vm = YTM_VIDEO_ID_PATTERN.matcher(searchJson);
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
                        Matcher vm = YTM_VIDEO_ID_PATTERN.matcher(searchAnyJson);
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

    public static List<LyricLine> parseLrcString(String lrc) {
        return parseNetEaseLrc(lrc);
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
                if (!isDemo && isPlaying && !currentTitle.isEmpty()) {
                    return new LyricState(EMPTY_LINE, null, 0.5f, 0, true, currentTitle);
                }
                return INACTIVE_STATE;
            }

            long progress = getCurrentProgressMs();
            String title = isDemo ? "Chase Me" : currentTitle;

            if (progress < source.get(0).timeMs) {
                return new LyricState(EMPTY_LINE, source.get(0), 0f, 0, isDemo || isPlaying, title);
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
                    break;
                }
            }

            if (cur == null) {
                return new LyricState(null, null, 0f, 0, isDemo || isPlaying, title);
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

            if (next != null && gap > 3800 && (progress - curStart) > (vocalDur + 700)) {
                return new LyricState(EMPTY_LINE, next, 1f, 0, isDemo || isPlaying, title);
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

            return new LyricState(cur, next, lineProg, wordIdx, isDemo || isPlaying, title);
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

    public static boolean hasSyncedLyrics() {
        if (ModConfig.INSTANCE.lyricsDemoMode) return true;
        synchronized (activeLyrics) {
            return !activeLyrics.isEmpty();
        }
    }
}
