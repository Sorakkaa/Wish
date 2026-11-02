package com.wish.client.features.environment;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    public static class LyricLine {
        public final long timeMs;
        public final String text;
        public final List<String> words;

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text != null ? text.trim() : "";
            this.words = new ArrayList<>();
            if (!this.text.isEmpty()) {
                String[] split = this.text.split("\\s+");
                for (String w : split) {
                    if (!w.isEmpty()) words.add(w);
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
        EXECUTOR.scheduleWithFixedDelay(LyricsManager::pollMediaSource, 800, 800, TimeUnit.MILLISECONDS);
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

    private static void pollMediaSource() {
        if (!ModConfig.INSTANCE.enableLyrics) return;
        if (ModConfig.INSTANCE.lyricsDemoMode) return;

        try {
            String configuredProvider = ModConfig.INSTANCE.musicProvider != null ? ModConfig.INSTANCE.musicProvider.trim() : "";

            // 1. Direct companion query if YouTube Music provider is selected
            if ("YTM".equalsIgnoreCase(configuredProvider) || "YouTube Music".equalsIgnoreCase(configuredProvider)) {
                if (pollYtmCompanion()) {
                    return;
                }
            }

            // 2. Fallback to OS-specific media listeners
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                pollLinuxPlayerctl();
            } else if ("YTM".equalsIgnoreCase(configuredProvider)) {
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

                // Extract and decode trackid from MPRIS (e.g. /org/xosms/MediaPlayer2/Track/kMI_2d_5f757Wh8 -> kMI-_757Wh8)
                if (parts.length >= 7) {
                    String decoded = decodeMprisTrackId(parts[6]);
                    if (decoded.length() == 11) {
                        lastYtmVideoId = decoded;
                    }
                }
                if (lastYtmVideoId.length() != 11 && (playerName.contains("ytm") || playerName.contains("youtube"))) {
                    String localVid = getLocalYtmDesktopVideoId();
                    if (localVid != null && localVid.length() == 11) {
                        lastYtmVideoId = localVid;
                    }
                }

                updateTrackState(title, artist, posMs, durationMs, "Playing".equalsIgnoreCase(status));
            } else if ("YTM".equalsIgnoreCase(configuredProvider)) {
                pollYtmCompanion();
            } else {
                isPlaying = false;
            }
        } catch (Exception ignored) {}
    }

    private static boolean pollYtmCompanion() {
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
            if (conn.getResponseCode() == 200) {
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

                    updateTrackState(title, artist, posMs, durationMs, playing);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void pollWindowsMedia() {
        String title = currentTitle;
        String artist = currentArtist;
        if (title.isEmpty()) {
            String full = com.wish.client.music.MusicManager.fetchSystemMediaSong();
            if (full != null && !full.isEmpty()) {
                if (full.contains(" - ")) {
                    String[] parts = full.split(" - ", 2);
                    title = parts[0].trim();
                    artist = parts[1].trim();
                } else {
                    title = full.trim();
                }
                updateTrackState(title, artist, lastKnownProgressMs + 1000, 0, true);
            }
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

        long now = System.currentTimeMillis();
        if (playing && !currentTrackKey.isEmpty()) {
            long expected = lastKnownProgressMs + (now - lastKnownTimeMs);
            long diff = Math.abs(progressMs - expected);
            // If deviation is within normal 350ms, keep smooth continuous time to avoid micro-stutter
            if (diff > 350) {
                lastKnownProgressMs = Math.max(0, progressMs);
                lastKnownTimeMs = now;
            }
        } else {
            lastKnownProgressMs = Math.max(0, progressMs);
            lastKnownTimeMs = now;
        }

        String newKey = (currentTitle + "|||" + currentArtist).toLowerCase().trim();
        boolean trackChanged = !newKey.isEmpty() && !newKey.equals(currentTrackKey);
        boolean videoChanged = !lastYtmVideoId.isEmpty() && !lastYtmVideoId.equals(lastFetchedVideoId);
        if (trackChanged || videoChanged) {
            currentTrackKey = newKey;
            lastFetchedVideoId = lastYtmVideoId;
            fetchLyricsAsync(currentTitle, currentArtist, currentDurationMs);
        }
    }

    public static String cleanTrackTitle(String title) {
        if (title == null) return "";
        String s = title;
        // Strip (speed up), (sped up ver.), (slowed), (official video), (lyrics), [clip], etc.
        s = s.replaceAll("(?i)\\s*[\\(\\[][^\\]\\)]*(?:speed|sped|slow|official|audio|video|lyrics?|music video|visualizer|clip|remix|prod|feat|hd|4k|version|ver\\.|edit)[^\\]\\)]*[\\)\\]]", "");
        // Strip trailing ft./feat.
        s = s.replaceAll("(?i)\\s*(?:ft\\.|feat\\.|featuring)\\s+.*$", "");
        // Strip trailing dashes like - Single, - EP
        s = s.replaceAll("(?i)\\s*-\\s*(?:single|ep|album|remastered).*$", "");
        return s.trim();
    }

    public static String cleanTrackArtist(String artist) {
        if (artist == null) return "";
        String s = artist;
        s = s.replaceAll("(?i)\\s*-\\s*topic$", "");
        return s.trim();
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
            return;
        }

        EXECUTOR.execute(() -> {
            List<LyricLine> parsed = fetchLyricsFromAllSources(title, artist, durationMs);
            synchronized (activeLyrics) {
                activeLyrics.clear();
                if (parsed != null && !parsed.isEmpty()) {
                    activeLyrics.addAll(parsed);
                    LYRICS_CACHE.put(cacheKey, parsed);
                }
            }
        });
    }

    private static List<LyricLine> fetchLyricsFromAllSources(String title, String artist, long durationMs) {
        // 0. Highest priority: User's local .lrc file in .minecraft/config/wish/lyrics/
        List<LyricLine> local = loadLocalLyrics(title, artist);
        if (local != null && !local.isEmpty()) return local;

        // 1. When using YouTube Music: Fetch ONLY official synchronized lyrics from YouTube Music!
        // If YTM does not have synchronized lyrics for this track, return empty so only floating emojis/notes show!
        boolean isYtm = "YTM".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || "YouTube Music".equalsIgnoreCase(ModConfig.INSTANCE.musicProvider)
            || (lastYtmVideoId != null && !lastYtmVideoId.isEmpty());
        if (isYtm) {
            List<LyricLine> ytmLyrics = fetchLyricsFromYouTubeMusic(lastYtmVideoId, title, artist, durationMs);
            if (ytmLyrics != null && !ytmLyrics.isEmpty()) {
                LOGGER.info("Using official synchronized YouTube Music lyrics for '{}' ({} lines)", title, ytmLyrics.size());
                return ytmLyrics;
            }
            LOGGER.info("No official YouTube Music lyrics found for '{}' - displaying only emojis.", title);
            return Collections.emptyList();
        }

        String cleanTitle = cleanTrackTitle(title);
        String cleanArtist = cleanTrackArtist(artist);
        boolean isRemix = isRemixOrEdit(title);

        List<LyricLine> res = null;

        // 2. Primary for speedup / remix: Search LRCLIB with the exact full title first
        // (If a speedup-specific synced .lrc exists, it is timed perfectly to the fast audio)
        if (isRemix) {
            res = fetchLyricsFromLrclib(title, artist, durationMs);
            if (res != null && !res.isEmpty()) return res;

            res = fetchLyricsFromNetEase(title, artist);
            if (res != null && !res.isEmpty()) return res;
        }

        // 3. Primary: LRCLIB with clean title & clean artist + duration matching & precise scaling
        res = fetchLyricsFromLrclib(cleanTitle, cleanArtist, durationMs);
        if (res != null && !res.isEmpty()) {
            return res;
        }

        // 4. Fallback: NetEase Cloud Music with clean title & clean artist
        res = fetchLyricsFromNetEase(cleanTitle, cleanArtist);
        if (res != null && !res.isEmpty()) {
            return adjustTimingsForRemix(res, title, durationMs, 0);
        }

        // 5. Try LRCLIB with raw title & artist
        if (!cleanTitle.equalsIgnoreCase(title)) {
            res = fetchLyricsFromLrclib(title, artist, durationMs);
            if (res != null && !res.isEmpty()) {
                return res;
            }
        }

        // If no accurate lyrics found, return empty so only floating notes/emojis show!
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
            LOGGER.info("Adjusting lyrics tempo for '{}' by factor {:.2f}", title, ratio);
            List<LyricLine> scaled = new ArrayList<>();
            for (LyricLine l : lines) {
                scaled.add(new LyricLine((long) (l.timeMs * ratio), l.text));
            }
            return scaled;
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
        "composer", "written by", "arranged by", "mixed by", "mastered by", "op:", "sp:"
    };

    private static List<LyricLine> fetchLyricsFromNetEase(String title, String artist) {
        try {
            String query = (title + (artist == null || artist.isEmpty() ? "" : " " + artist)).trim();
            if (query.isEmpty()) return Collections.emptyList();

            String searchUrl = "https://music.163.com/api/search/get/?s=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=1&limit=4";
            String searchJson = httpGet(searchUrl);
            if (searchJson == null || searchJson.isEmpty()) return Collections.emptyList();

            JsonObject root = JsonParser.parseString(searchJson).getAsJsonObject();
            if (!root.has("result") || !root.getAsJsonObject("result").has("songs")) return Collections.emptyList();

            JsonArray songs = root.getAsJsonObject("result").getAsJsonArray("songs");
            for (JsonElement el : songs) {
                if (!el.isJsonObject()) continue;
                JsonObject s = el.getAsJsonObject();
                if (!s.has("id")) continue;
                long songId = s.get("id").getAsLong();

                String lyricUrl = "https://music.163.com/api/song/lyric?id=" + songId + "&lv=1&kv=1&tv=-1";
                String lyricJson = httpGet(lyricUrl);
                if (lyricJson == null || lyricJson.isEmpty()) continue;

                JsonObject lObj = JsonParser.parseString(lyricJson).getAsJsonObject();
                if (lObj.has("lrc") && lObj.getAsJsonObject("lrc").has("lyric")) {
                    String rawLrc = lObj.getAsJsonObject("lrc").get("lyric").getAsString();
                    if (rawLrc != null && !rawLrc.trim().isEmpty()) {
                        List<LyricLine> lines = parseNetEaseLrc(rawLrc);
                        if (!lines.isEmpty()) {
                            LOGGER.info("Successfully fetched synced lyrics from NetEase for '{}' (songId={})", query, songId);
                            return lines;
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
            Matcher m = LRC_PATTERN.matcher(line);
            if (m.matches()) {
                try {
                    int minutes = Integer.parseInt(m.group(1));
                    String secPart = m.group(2).replace(':', '.');
                    double seconds = Double.parseDouble(secPart);
                    long totalMs = (long) (minutes * 60000 + seconds * 1000);
                    String text = m.group(3).trim();

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

                    list.add(new LyricLine(totalMs, text));
                } catch (Exception ignored) {}
            }
        }
        list.sort(Comparator.comparingLong(a -> a.timeMs));
        return list;
    }

    private static List<LyricLine> fetchLyricsFromLrclib(String title, String artist, long durationMs) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String encodedArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8);

            // 1. Direct get endpoint with duration
            if (durationMs > 0) {
                String directUrl = "https://lrclib.net/api/get?track_name=" + encodedTitle + "&artist_name=" + encodedArtist + "&duration=" + (durationMs / 1000);
                String rawJson = httpGet(directUrl);
                if (rawJson != null) {
                    List<LyricLine> res = parseLyricsFromJson(rawJson, durationMs, title);
                    if (res != null && !res.isEmpty()) return res;
                }
            }

            // 2. Direct get endpoint WITHOUT duration (handles small duration discrepancies)
            String noDurUrl = "https://lrclib.net/api/get?track_name=" + encodedTitle + "&artist_name=" + encodedArtist;
            String rawJsonNoDur = httpGet(noDurUrl);
            if (rawJsonNoDur != null) {
                List<LyricLine> res = parseLyricsFromJson(rawJsonNoDur, durationMs, title);
                if (res != null && !res.isEmpty()) return res;
            }

            // 3. Fallback search endpoint with duration gap matching (proven method from Show The Lyrics)
            String searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(title + (artist.isEmpty() ? "" : " " + artist), StandardCharsets.UTF_8);
            String searchJson = httpGet(searchUrl);
            if (searchJson != null) {
                JsonElement root = JsonParser.parseString(searchJson);
                if (root.isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray();
                    // First pass: look for syncedLyrics, prioritizing candidate with closest duration
                    JsonObject bestSynced = null;
                    long bestGap = Long.MAX_VALUE;
                    for (JsonElement el : arr) {
                        if (el.isJsonObject()) {
                            JsonObject o = el.getAsJsonObject();
                            if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
                                long gap = 0L;
                                if (durationMs > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
                                    gap = Math.abs((long) (o.get("duration").getAsDouble() * 1000) - durationMs);
                                }
                                if (gap < bestGap) {
                                    bestGap = gap;
                                    bestSynced = o;
                                }
                            }
                        }
                    }
                    // Accept matching duration: standard songs within 8s, speedup/remix up to 75s gap
                    long maxAllowedGap = isRemixOrEdit(title) ? 75000L : 8000L;
                    if (bestSynced != null && (durationMs <= 0 || bestGap <= maxAllowedGap)) {
                        List<LyricLine> lines = parseLrcString(bestSynced.get("syncedLyrics").getAsString());
                        if (!lines.isEmpty()) {
                            long candDur = 0;
                            if (bestSynced.has("duration") && !bestSynced.get("duration").isJsonNull()) {
                                candDur = (long) (bestSynced.get("duration").getAsDouble() * 1000);
                            }
                            return adjustTimingsForRemix(lines, title, durationMs, candDur);
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
        return text.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase().trim();
    }

    private static List<LyricLine> fetchLyricsFromYouTubeMusic(String videoId, String title, String artist, long durationMs) {
        try {
            String vid = videoId;
            // 0. If videoId is not 11 chars, try reading from local YTM desktop config
            if (vid == null || vid.length() != 11) {
                String localVid = getLocalYtmDesktopVideoId();
                if (localVid != null && localVid.length() == 11) {
                    vid = localVid;
                }
            }

            // If still not found, search YouTube Music WEB_REMIX API
            if (vid == null || vid.length() != 11) {
                String searchBody = "{\"query\":\"" + (title + " " + artist).replace("\"", "\\\"") + "\",\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\"}}}";
                String searchJson = httpPostJson("https://music.youtube.com/youtubei/v1/search", searchBody);
                if (searchJson != null) {
                    Matcher vm = Pattern.compile("\"videoId\":\\s*\"([a-zA-Z0-9_-]{11})\"").matcher(searchJson);
                    if (vm.find()) {
                        vid = vm.group(1);
                    }
                }
            }

            if (vid == null || vid.length() != 11) return null;

            // 1. Get watch next data to find the lyrics browseId (try ANDROID_MUSIC then WEB_REMIX)
            String lyricsBrowseId = null;
            String[][] clientPairs = {
                {"ANDROID_MUSIC", "8.05.50"},
                {"WEB_REMIX", "1.20240101.01.00"}
            };

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

            if (lyricsBrowseId == null) return null;

            // 2. Fetch lyrics using browseId: Only use ANDROID_MUSIC timedLyricsData with millisecond accuracy.
            // If timed lyrics are not available on YTM, return null so only emojis are shown!
            String browseAndroidBody = "{\"browseId\":\"" + lyricsBrowseId + "\",\"context\":{\"client\":{\"clientName\":\"ANDROID_MUSIC\",\"clientVersion\":\"8.05.50\"}}}";
            String browseAndroidJson = httpPostJson("https://music.youtube.com/youtubei/v1/browse", browseAndroidBody);
            if (browseAndroidJson != null) {
                try {
                    JsonObject browseObj = JsonParser.parseString(browseAndroidJson).getAsJsonObject();
                    List<LyricLine> timedLines = parseYouTubeMusicTimedLyrics(browseObj);
                    if (timedLines != null && !timedLines.isEmpty()) {
                        LOGGER.info("Using official synchronized YouTube Music lyrics for '{}' ({} lines)", title, timedLines.size());
                        return timedLines;
                    }
                } catch (Exception ignored) {}
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
                        if (!text.isEmpty() && item.has("cueRange") && !item.get("cueRange").isJsonNull()) {
                            JsonObject cue = item.getAsJsonObject("cueRange");
                            if (cue.has("startTimeMilliseconds")) {
                                long startMs = Long.parseLong(cue.get("startTimeMilliseconds").getAsString());
                                list.add(new LyricLine(startMs, text));
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
        for (String line : lines) {
            line = line.trim();
            Matcher m = LRC_PATTERN.matcher(line);
            if (m.matches()) {
                try {
                    int minutes = Integer.parseInt(m.group(1));
                    String secPart = m.group(2).replace(':', '.');
                    double seconds = Double.parseDouble(secPart);
                    long totalMs = (long) (minutes * 60000 + seconds * 1000);
                    String text = m.group(3).trim();
                    // Keep empty lines as instrumental pauses
                    list.add(new LyricLine(totalMs, text));
                } catch (Exception ignored) {}
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
                long timeUntil = first.timeMs - progress;
                // If before the vocal starts, show music note emojis rather than premature lyric
                if (timeUntil > 800) {
                    LyricLine introLine = new LyricLine(0, "");
                    return new LyricState(introLine, first, 0f, 0, isPlaying, currentTitle, currentArtist);
                }
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
                        curEnd = curStart + 4000;
                    }
                } else {
                    if (cur == null && i == 0) {
                        next = line;
                    }
                    break;
                }
            }

            if (cur == null && !source.isEmpty()) {
                cur = source.get(0);
                curStart = 0;
                curEnd = cur.timeMs > 0 ? cur.timeMs : 4000;
                if (source.size() > 1) {
                    next = source.get(1);
                }
            }

            if (cur == null) {
                return new LyricState(null, null, 0f, 0, isDemo || isPlaying, isDemo ? "Chase Me" : currentTitle, isDemo ? "Trapex" : currentArtist);
            }

            long gap = (next != null) ? (next.timeMs - curStart) : (curEnd - curStart);
            long wordEstimatedDur = Math.max(1200L, cur.words.size() * 380L);
            long vocalDur;
            if (gap > 4500) {
                vocalDur = Math.min(gap - 1000L, Math.max(2400L, wordEstimatedDur));
            } else {
                vocalDur = Math.max(1000L, gap);
            }

            // If line singing has completely finished and there's still a noticeable pause until next line, show pauseLine
            if (next != null && gap > 4200 && (progress - curStart) > (vocalDur + 800)) {
                LyricLine pauseLine = new LyricLine(curStart + vocalDur + 800, "");
                return new LyricState(pauseLine, next, 1f, 0, isPlaying, isDemo ? "Chase Me" : currentTitle, isDemo ? "Trapex" : currentArtist);
            }

            float lineProg = Math.max(0f, Math.min(1f, (float) (progress - curStart) / vocalDur));
            int wordIdx = 0;
            if (!cur.words.isEmpty()) {
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
}
