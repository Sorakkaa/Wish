package com.wish.client.features.visual;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wish.client.config.ModConfig;
import com.wish.client.util.HttpClientUtils;
import com.wish.client.util.WishPaths;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final Pattern WHITESPACE_SPLIT_PATTERN = Pattern.compile("\\s+");
    private static final Pattern LINE_SPLIT_PATTERN = Pattern.compile("\\r?\\n");
    private static final Gson GSON = new Gson();
    private static final int MAX_COMPANION_BYTES = 512 * 1024;

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
                String[] split = WHITESPACE_SPLIT_PATTERN.split(this.text);
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
    private static volatile boolean configScreenOpen = false;
    private static final AtomicLong FETCH_SEQUENCE = new AtomicLong(0L);

    public static void setConfigScreenOpen(boolean open) {
        configScreenOpen = open;
    }

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
        if (!ModConfig.INSTANCE.enableLyrics && !configScreenOpen) {
            isPlaying = false;
            return;
        }

        try {
            String configuredProvider = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider.trim() : "";

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                pollLinuxPlayerctl();
                return;
            }

            if ("YTM".equalsIgnoreCase(configuredProvider) || "YouTube Music".equalsIgnoreCase(configuredProvider)) {
                if (pollYtmCompanion()) {
                    return;
                }
            }

            pollWindowsMedia();
        } catch (Exception e) {
            LOGGER.debug("Error polling media source", e);
        }
    }

    private static boolean isAllowedMusicPlayer(String playerName, String configuredProvider) {
        if (playerName == null || playerName.isEmpty()) return false;
        String p = playerName.toLowerCase().trim();

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

        return isYtm || isSpotify || isDeezer;
    }

    private static String decodeMprisTrackId(String trackId) {
        if (trackId == null || trackId.isEmpty()) return "";
        int lastSlash = trackId.lastIndexOf('/');
        String segment = (lastSlash >= 0 && lastSlash < trackId.length() - 1)
            ? trackId.substring(lastSlash + 1).trim()
            : trackId.trim();
        segment = segment.replace("'", "").replace("\"", "");
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
            String userHome = System.getProperty("user.home");
            if (userHome == null || userHome.isBlank()) return null;
            Path cfg = Path.of(userHome, ".config", "YouTube Music Desktop App", "config.json");
            if (Files.isRegularFile(cfg)) {
                String str = Files.readString(cfg, StandardCharsets.UTF_8);
                JsonObject obj = HttpClientUtils.parseJsonObject(str);
                JsonObject state = HttpClientUtils.getObjectOrNull(obj, "state");
                String v = HttpClientUtils.getStringOrEmpty(state, "lastVideoId").trim();
                if (v.length() == 11) return v;
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read local YTM Desktop config: {}", e.getMessage());
        }
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
        HttpURLConnection conn = null;
        try {
            int port = ModConfig.INSTANCE.ytmPort > 0 ? ModConfig.INSTANCE.ytmPort : 9863;
            URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/state");
            conn = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
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
                String body;
                try (InputStream in = conn.getInputStream()) {
                    body = HttpClientUtils.readBoundedUtf8(in, MAX_COMPANION_BYTES);
                }
                JsonObject json = HttpClientUtils.parseJsonObject(body);
                if (json == null) return false;

                String title = "";
                String artist = "";
                long posMs = 0;
                long durationMs = 0;
                boolean playing = false;

                JsonObject v = HttpClientUtils.getObjectOrNull(json, "video");
                if (v != null) {
                    title = HttpClientUtils.getStringOrEmpty(v, "title");
                    artist = HttpClientUtils.getStringOrEmpty(v, "author");
                    String vidId = HttpClientUtils.getStringOrEmpty(v, "id");
                    if (!vidId.isEmpty()) {
                        lastYtmVideoId = vidId;
                    }
                    if (v.has("durationSeconds") && v.get("durationSeconds").isJsonPrimitive()) {
                        durationMs = (long) (v.get("durationSeconds").getAsDouble() * 1000);
                    } else if (v.has("totalSeconds") && v.get("totalSeconds").isJsonPrimitive()) {
                        durationMs = (long) (v.get("totalSeconds").getAsDouble() * 1000);
                    }
                }
                JsonObject pl = HttpClientUtils.getObjectOrNull(json, "player");
                if (pl != null) {
                    if (pl.has("seekbarCurrentPosition") && pl.get("seekbarCurrentPosition").isJsonPrimitive()) {
                        posMs = (long) (pl.get("seekbarCurrentPosition").getAsDouble() * 1000);
                    }
                    if (pl.has("trackState") && pl.get("trackState").isJsonPrimitive()) {
                        playing = (pl.get("trackState").getAsInt() == 1);
                    }
                }

                currentDetectedPlayer = "ytm";
                updateTrackState(title, artist, posMs, durationMs, playing);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to poll local YTM companion: {}", e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
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
        candidates.sort((a, b) -> b[3].compareTo(a[3]));
        String[] best = candidates.get(0);
        return new String[]{ best[0], best[1], best[2] };
    }

    private static void pollWindowsMedia() {
        try {
            String providerParam = ModConfig.INSTANCE.musicProvider != null && !ModConfig.INSTANCE.musicProvider.equalsIgnoreCase("None")
                ? ModConfig.INSTANCE.musicProvider.trim() : "ALL";

            String[] jnaTrack = detectWindowsTrackViaJna(providerParam);
            if (jnaTrack != null && jnaTrack.length >= 3 && !jnaTrack[2].isEmpty()) {
                currentDetectedPlayer = jnaTrack[0];
                if (!jnaTrack[0].contains("ytm") && !jnaTrack[0].contains("youtube")) {
                    lastYtmVideoId = "";
                }
                String jnaKey = (jnaTrack[2] + "|||" + jnaTrack[1]).toLowerCase().trim();
                long durationForUpdate = jnaKey.equals(currentTrackKey) ? currentDurationMs : 0L;
                updateTrackState(jnaTrack[2], jnaTrack[1], -1, durationForUpdate, true);
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
                lastKnownProgressMs += Math.max(0, now - lastKnownTimeMs);
                lastKnownTimeMs = now;
            } else if (progressMs <= 500 && lastKnownProgressMs > 2500 && (durationMs <= 0 || lastKnownProgressMs < durationMs - 2000)) {
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
            String expectedVideoId = lastYtmVideoId != null ? lastYtmVideoId : "";
            lastFetchedVideoId = expectedVideoId;
            LOGGER.debug("Track changed: '{}' by '{}' [Player: {}, Status: {}]", currentTitle, currentArtist, currentDetectedPlayer, isPlaying ? "Playing" : "Paused");
            fetchLyricsAsync(currentTitle, currentArtist, currentDurationMs, newKey, expectedVideoId);
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

    private static final Map<String, List<LyricLine>> LYRICS_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, List<LyricLine>>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<LyricLine>> eldest) {
                return size() > 128;
            }
        }
    );

    private static void fetchLyricsAsync(String title, String artist, long durationMs, String expectedTrackKey, String expectedVideoId) {
        String safeVid = expectedVideoId != null ? expectedVideoId : "";
        String cacheKey = (title + "|||" + artist + "|||" + safeVid).toLowerCase(Locale.ROOT).trim();
        final long fetchSeq = FETCH_SEQUENCE.incrementAndGet();

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
                List<LyricLine> parsed = fetchLyricsFromAllSources(title, artist, durationMs, safeVid);
                synchronized (LyricsManager.class) {
                    if (fetchSeq != FETCH_SEQUENCE.get() || !expectedTrackKey.equals(currentTrackKey)) {
                        return;
                    }
                }
                synchronized (activeLyrics) {
                    activeLyrics.clear();
                    if (parsed != null && !parsed.isEmpty()) {
                        activeLyrics.addAll(parsed);
                        LYRICS_CACHE.put(cacheKey, parsed);
                    }
                }
            } finally {
                if (fetchSeq == FETCH_SEQUENCE.get()) {
                    isFetchingLyrics = false;
                }
            }
        });
    }

    private static List<LyricLine> fetchLyricsFromAllSources(String title, String artist, long durationMs, String ytmVideoId) {
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

        boolean isYtm = !isSpotifyOrDeezer && (
            "YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || "YouTube Music".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || detPlayer.contains("ytm")
            || detPlayer.contains("youtube")
            || !ytmVideoId.isEmpty()
        );
        if (isYtm) {
            List<LyricLine> ytmLyrics = fetchLyricsFromYouTubeMusic(ytmVideoId, title, artist, true);
            if (ytmLyrics != null && !ytmLyrics.isEmpty()) {
                return ytmLyrics;
            }
        }

        String cleanTitle = cleanTrackTitle(title);
        String cleanArtist = cleanTrackArtist(artist);
        String primaryArtist = extractPrimaryArtist(artist);
        boolean isRemix = isRemixOrEdit(title);

        List<LyricLine> res;

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

        res = fetchLyricsFromLrclib(cleanTitle, cleanArtist, durationMs);
        if (res != null && !res.isEmpty()) return res;
        if (!primaryArtist.equalsIgnoreCase(cleanArtist) && !primaryArtist.isEmpty()) {
            res = fetchLyricsFromLrclib(cleanTitle, primaryArtist, durationMs);
            if (res != null && !res.isEmpty()) return res;
        }

        if (!isRemix) {
            res = fetchLyricsFromYouTubeMusic(null, cleanTitle, primaryArtist, false);
            if (res != null && !res.isEmpty()) return res;
        }

        res = fetchLyricsFromNetEase(cleanTitle, primaryArtist, durationMs);
        if (res != null && !res.isEmpty()) return res;

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

        if (candidateDurationMs > 0 && actualDurationMs > 0 && Math.abs(candidateDurationMs - actualDurationMs) <= 8000) {
            LOGGER.debug("Lyrics for '{}' already match track duration ({}s vs {}s). No scaling needed.", title, candidateDurationMs / 1000, actualDurationMs / 1000);
            return lines;
        }

        if (candidateDurationMs > 0 && actualDurationMs > 0 && (candidateDurationMs > actualDurationMs * 1.5 || actualDurationMs > candidateDurationMs * 1.5)) {
            LOGGER.debug("Rejecting lyrics for '{}': length mismatch (audio: {}s, candidate: {}s). Floating musical emojis will be shown.", title, actualDurationMs / 1000, candidateDurationMs / 1000);
            return Collections.emptyList();
        }

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

        if (!isSpeedUp && !isSlowed && candidateDurationMs > 0 && actualDurationMs > 0 && Math.abs(candidateDurationMs - actualDurationMs) > 20000) {
            LOGGER.debug("Skipping candidate for '{}': duration gap too large ({}s vs {}s)", title, candidateDurationMs / 1000, actualDurationMs / 1000);
            return Collections.emptyList();
        }
        return lines;
    }

    private static List<LyricLine> loadLocalLyrics(String title, String artist) {
        try {
            Path dir = WishPaths.getConfigDir().resolve("lyrics").normalize();
            Files.createDirectories(dir);

            Path customFile = WishPaths.resolveContained(dir, "custom.lrc");
            if (Files.isRegularFile(customFile)) {
                String content = Files.readString(customFile, StandardCharsets.UTF_8);
                List<LyricLine> lines = parseNetEaseLrc(content);
                if (!lines.isEmpty()) {
                    LOGGER.debug("Loaded lyrics from local file: custom.lrc");
                    return lines;
                }
            }

            String safeTitle = SAFE_FILENAME_PATTERN.matcher(title).replaceAll("_").toLowerCase(Locale.ROOT);
            String safeClean = SAFE_FILENAME_PATTERN.matcher(cleanTrackTitle(title)).replaceAll("_").toLowerCase(Locale.ROOT);
            String safeArtistClean = SAFE_FILENAME_PATTERN.matcher(artist + "_" + safeClean).replaceAll("_").toLowerCase(Locale.ROOT);

            String[] candidateNames = {
                safeTitle + ".lrc",
                safeClean + ".lrc",
                safeArtistClean + ".lrc"
            };

            for (String candidateName : candidateNames) {
                Path candidatePath = WishPaths.resolveContained(dir, candidateName);
                if (Files.isRegularFile(candidatePath)) {
                    String content = Files.readString(candidatePath, StandardCharsets.UTF_8);
                    List<LyricLine> lines = parseNetEaseLrc(content);
                    if (!lines.isEmpty()) {
                        LOGGER.debug("Loaded lyrics from local file: {}", candidatePath.getFileName());
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

            JsonObject root = HttpClientUtils.parseJsonObject(searchJson);
            JsonObject result = HttpClientUtils.getObjectOrNull(root, "result");
            JsonArray songs = HttpClientUtils.getArrayOrNull(result, "songs");
            if (songs == null) return Collections.emptyList();
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

                JsonObject lObj = HttpClientUtils.parseJsonObject(lyricJson);
                JsonObject lrcObj = HttpClientUtils.getObjectOrNull(lObj, "lrc");
                String rawLrc = HttpClientUtils.getStringOrEmpty(lrcObj, "lyric");
                if (!rawLrc.trim().isEmpty()) {
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
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch lyrics from NetEase", e);
        }
        return Collections.emptyList();
    }

    private static List<LyricLine> parseNetEaseLrc(String lrc) {
        List<LyricLine> list = new ArrayList<>();
        if (lrc == null || lrc.isEmpty()) return list;

        String[] lines = LINE_SPLIT_PATTERN.split(lrc);

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

            if (durationMs > 0) {
                long roundedSec = Math.round(durationMs / 1000.0);
                String directUrl = "https://lrclib.net/api/get?track_name=" + encodedTitle + "&artist_name=" + encodedArtist + "&duration=" + roundedSec;
                String rawJson = httpGet(directUrl);
                if (rawJson != null) {
                    List<LyricLine> res = parseLyricsFromJson(rawJson, durationMs, title);
                    if (res != null && !res.isEmpty()) return res;
                }
            }

            String[] searchUrls = {
                "https://lrclib.net/api/search?track_name=" + encodedTitle + (artist.isEmpty() ? "" : "&artist_name=" + encodedArtist),
                "https://lrclib.net/api/search?q=" + URLEncoder.encode(title + (artist.isEmpty() ? "" : " " + artist), StandardCharsets.UTF_8)
            };

            String normTargetTitle = normalizeForMatch(cleanTrackTitle(title));
            String normTargetArtist = normalizeForMatch(extractPrimaryArtist(artist));

            for (String searchUrl : searchUrls) {
                String searchJson = httpGet(searchUrl);
                if (searchJson == null) continue;

                JsonArray arr = HttpClientUtils.parseJsonArray(searchJson);
                if (arr == null) continue;
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

                    if (!normTargetTitle.isEmpty() && !normCandTrack.isEmpty()) {
                        if (!normCandTrack.contains(normTargetTitle) && !normTargetTitle.contains(normCandTrack)) {
                            continue;
                        }
                    }

                    long gap = 0L;
                    if (durationMs > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
                        gap = Math.abs((long) (o.get("duration").getAsDouble() * 1000) - durationMs);
                    }

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
                try (InputStream in = conn.getInputStream()) {
                    return HttpClientUtils.readBoundedUtf8(in, HttpClientUtils.DEFAULT_MAX_BYTES);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("HTTP GET failed for {}: {}", urlStr, e.getMessage());
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
                try (InputStream in = conn.getInputStream()) {
                    return HttpClientUtils.readBoundedUtf8(in, HttpClientUtils.DEFAULT_MAX_BYTES);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("HTTP POST failed for {}: {}", urlStr, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String buildYouTubeContextJson(String clientName, String clientVersion, String key1, String val1, String key2, String val2) {
        JsonObject clientObj = new JsonObject();
        clientObj.addProperty("clientName", clientName);
        clientObj.addProperty("clientVersion", clientVersion);
        JsonObject contextObj = new JsonObject();
        contextObj.add("client", clientObj);

        JsonObject payload = new JsonObject();
        if (key1 != null && val1 != null) {
            payload.addProperty(key1, val1);
        }
        if (key2 != null && val2 != null) {
            payload.addProperty(key2, val2);
        }
        payload.add("context", contextObj);
        return GSON.toJson(payload);
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
                String query = (title + (artist == null || artist.isEmpty() ? "" : " " + artist)).trim();
                String searchSongsBody = buildYouTubeContextJson("WEB_REMIX", "1.20240101.01.00", "query", query, "params", "EgWKAQIIAWoKEAkQBRAKEAMQBA==");
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
                    String searchAnyBody = buildYouTubeContextJson("WEB_REMIX", "1.20240101.01.00", "query", query, null, null);
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
                    String nextBody = buildYouTubeContextJson(client[0], client[1], "videoId", vid, null, null);
                    String nextJson = httpPostJson("https://music.youtube.com/youtubei/v1/next", nextBody);
                    if (nextJson == null) continue;

                    JsonObject nextObj = HttpClientUtils.parseJsonObject(nextJson);
                    JsonObject singleCol = HttpClientUtils.getObjectOrNull(HttpClientUtils.getObjectOrNull(nextObj, "contents"), "singleColumnMusicWatchNextResultsRenderer");
                    JsonObject watchNext = HttpClientUtils.getObjectOrNull(HttpClientUtils.getObjectOrNull(singleCol, "tabbedRenderer"), "watchNextTabbedResultsRenderer");
                    JsonArray tabs = HttpClientUtils.getArrayOrNull(watchNext, "tabs");
                    if (tabs != null) {
                        for (JsonElement tabEl : tabs) {
                            if (!tabEl.isJsonObject()) continue;
                            JsonObject tabR = HttpClientUtils.getObjectOrNull(tabEl.getAsJsonObject(), "tabRenderer");
                            String tabTitle = HttpClientUtils.getStringOrEmpty(tabR, "title");
                            if ("Lyrics".equalsIgnoreCase(tabTitle) || tabTitle.toLowerCase(Locale.ROOT).contains("lyric")) {
                                JsonObject browseEp = HttpClientUtils.getObjectOrNull(HttpClientUtils.getObjectOrNull(tabR, "endpoint"), "browseEndpoint");
                                String bid = HttpClientUtils.getStringOrEmpty(browseEp, "browseId");
                                if (!bid.isEmpty()) {
                                    lyricsBrowseId = bid;
                                    break;
                                }
                            }
                        }
                    }

                    if (lyricsBrowseId != null) break;
                }

                if (lyricsBrowseId == null) continue;

                String browseAndroidBody = buildYouTubeContextJson("ANDROID_MUSIC", "8.05.50", "browseId", lyricsBrowseId, null, null);
                String browseAndroidJson = httpPostJson("https://music.youtube.com/youtubei/v1/browse", browseAndroidBody);
                if (browseAndroidJson != null) {
                    JsonObject browseObj = HttpClientUtils.parseJsonObject(browseAndroidJson);
                    if (browseObj != null) {
                        List<LyricLine> timedLines = parseYouTubeMusicTimedLyrics(browseObj);
                        if (timedLines != null && !timedLines.isEmpty()) {
                            return timedLines;
                        }
                    }
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
        JsonObject obj = HttpClientUtils.parseJsonObject(jsonStr);
        String lrc = HttpClientUtils.getStringOrEmpty(obj, "syncedLyrics");
        if (!lrc.isEmpty()) {
            List<LyricLine> list = parseLrcString(lrc);
            if (!list.isEmpty()) {
                long candDur = 0;
                if (obj.has("duration") && obj.get("duration").isJsonPrimitive()) {
                    candDur = (long) (obj.get("duration").getAsDouble() * 1000);
                }
                return adjustTimingsForRemix(list, title, durationMs, candDur);
            }
        }
        return Collections.emptyList();
    }

    public static List<LyricLine> parseLrcString(String lrc) {
        return parseNetEaseLrc(lrc);
    }

    public static long getCurrentProgressMs() {
        if (ModConfig.INSTANCE.lyricsDemoMode) {
            long total = 18000;
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
