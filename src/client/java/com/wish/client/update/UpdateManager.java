package com.wish.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import com.wish.client.util.HttpClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class UpdateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Updater");

    private static final String UPDATE_URL = "https://api.github.com/repos/Sorakkaa/Wish/releases/latest";
    private static final String FALLBACK_DOWNLOAD_URL = "https://github.com/Sorakkaa/Wish/releases/latest";
    private static final long NOTIFICATION_DELAY_SECONDS = 7L;
    private static final long REFRESH_TTL_MS = 30L * 60L * 1000L;
    private static final int DEFAULT_CHAT_WIDTH_PX = 320;
    private static final int MAX_CHANGELOG_CHARS = 220;
    private static final int MAX_CHANGELOG_LINES = 4;

    private static final int COLOR_DIVIDER_PINK = 0xFFC6F9;
    private static final int COLOR_TITLE_PINK = 0xFFC6F9;
    private static final int COLOR_TITLE_WHITE = 0xFFFFFF;
    private static final int COLOR_SUBTITLE_GRAY = 0xAAAAAA;
    private static final int COLOR_LINK_DOWNLOAD = 0x55FF55;
    private static final int COLOR_LINK_GITHUB = 0xFFFFFF;
    private static final int COLOR_LINK_IGNORE = 0xFFC6F9;
    private static final String LINK_GAP = "     ";

    public record UpdateInfo(
            String version,
            String directDownloadUrl,
            String assetName,
            String releasePageUrl,
            String changelogSummary
    ) {}

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Wish-Update-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicLong JOIN_GENERATION = new AtomicLong(0L);
    private static final AtomicBoolean FETCH_IN_FLIGHT = new AtomicBoolean(false);

    private static volatile UpdateInfo cachedUpdate = null;
    private static volatile long lastFetchMs = 0L;
    private static volatile long activeJoinMs = 0L;
    private static volatile long notifiedGeneration = -1L;
    private static volatile boolean sessionBannerHandled = false;
    private static volatile String activeSessionKey = "";
    private static volatile WeakReference<Connection> activeConnectionRef = new WeakReference<>(null);

    public static void onPlayerJoin(Minecraft mc, Connection connection) {
        String sessionKey = resolveSessionKey(mc);
        Connection prevConn = activeConnectionRef.get();
        if (!shouldScheduleSessionJoin(sessionKey, connection, prevConn, sessionBannerHandled, activeSessionKey)) {
            activeConnectionRef = new WeakReference<>(connection);
            return;
        }

        activeSessionKey = sessionKey;
        activeConnectionRef = new WeakReference<>(connection);
        sessionBannerHandled = false;
        long gen = JOIN_GENERATION.incrementAndGet();
        activeJoinMs = System.currentTimeMillis();

        if (!ModConfig.INSTANCE.enableUpdateCheck) {
            return;
        }

        if (cachedUpdate == null || (System.currentTimeMillis() - lastFetchMs) > REFRESH_TTL_MS) {
            checkForUpdatesInternal(false);
        }

        SCHEDULER.schedule(() -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> tryDeliverJoinBanner(gen));
        }, NOTIFICATION_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public static void onDisconnect(Minecraft mc, Connection connection) {
        Screen screen = (mc != null && mc.gui != null) ? mc.gui.screen() : null;
        if (screen instanceof ServerReconfigScreen || screen instanceof ConnectScreen) {
            return;
        }
        resetSessionState();
    }

    public static void onReturnToMenu() {
        resetSessionState();
    }

    private static void resetSessionState() {
        sessionBannerHandled = false;
        activeSessionKey = "";
        activeConnectionRef = new WeakReference<>(null);
        JOIN_GENERATION.incrementAndGet();
    }

    static boolean shouldScheduleSessionJoin(
            String incomingSessionKey,
            Connection incomingConn,
            Connection previousConn,
            boolean alreadyHandled,
            String currentSessionKey
    ) {
        if (incomingConn != null && incomingConn == previousConn) {
            return false;
        }
        if (alreadyHandled && incomingSessionKey.equals(currentSessionKey)) {
            return false;
        }
        return !(!alreadyHandled && previousConn != null && incomingSessionKey.equals(currentSessionKey));
    }

    private static String resolveSessionKey(Minecraft mc) {
        if (mc == null) return "multi";
        if (mc.isLocalServer() || mc.hasSingleplayerServer()) {
            return "solo";
        }
        var server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + server.ip.trim().toLowerCase();
        }
        return "multi";
    }

    public static void checkForUpdates() {
        if (!ModConfig.INSTANCE.enableUpdateCheck) return;
        checkForUpdatesInternal(false);
    }

    public static void checkNowInteractive() {
        checkForUpdatesInternal(true);
    }

    public static void ignoreVersion(String rawVersion) {
        String clean = sanitizePlainVersion(rawVersion);
        if (clean.isEmpty()) return;
        ModConfig.INSTANCE.ignoredUpdateVersion = clean;
        ModConfig.INSTANCE.save();
        WishClient.sendLocalChatMessage(Component.literal("[Wish] ")
                .withStyle(Style.EMPTY.withColor(0xFFC6F9).withBold(true))
                .append(Component.literal("Ignored update alerts for v" + clean + ". Use /wish update clearignore to reset.")
                        .withStyle(Style.EMPTY.withColor(0xAAAAAA).withBold(false))));
    }

    public static void clearIgnoredVersion() {
        ModConfig.INSTANCE.ignoredUpdateVersion = "";
        ModConfig.INSTANCE.save();
        WishClient.sendLocalChatMessage(Component.literal("[Wish] ")
                .withStyle(Style.EMPTY.withColor(0xFFC6F9).withBold(true))
                .append(Component.literal("Cleared ignored update version filter.")
                        .withStyle(Style.EMPTY.withColor(0x55FF55).withBold(false))));
    }

    private static void checkForUpdatesInternal(boolean interactive) {
        if (!FETCH_IN_FLIGHT.compareAndSet(false, true)) {
            if (interactive) {
                UpdateInfo current = cachedUpdate;
                if (current != null) {
                    sendInteractiveBanner(current);
                } else {
                    WishClient.sendLocalChatMessage("Update check is already running...");
                }
            }
            return;
        }

        SCHEDULER.execute(() -> {
            HttpURLConnection conn = null;
            try {
                LOGGER.debug("Checking for Wish updates...");
                URL url = URI.create(UPDATE_URL).toURL();
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Wish-Client/" + WishClient.MOD_VERSION);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                lastFetchMs = System.currentTimeMillis();

                if (responseCode == 200) {
                    String body;
                    try (InputStream in = conn.getInputStream()) {
                        body = HttpClientUtils.readBoundedUtf8(in, HttpClientUtils.DEFAULT_MAX_BYTES);
                    }
                    JsonObject json = HttpClientUtils.parseJsonObject(body);
                    UpdateInfo info = parseReleaseJson(json, WishClient.MOD_VERSION);
                    cachedUpdate = info;
                    handleFetchResult(info, interactive);
                } else if (responseCode == 404) {
                    LOGGER.debug("No release published on GitHub yet (HTTP 404).");
                    if (interactive) {
                        WishClient.sendLocalChatMessage("No GitHub releases found yet.");
                    }
                } else {
                    LOGGER.warn("Failed to check for updates, HTTP response code: {}", responseCode);
                    if (interactive) {
                        WishClient.sendLocalChatMessage("§cUpdate check failed (HTTP " + responseCode + ").");
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to check for updates", e);
                if (interactive) {
                    WishClient.sendLocalChatMessage("§cFailed to check for updates.");
                }
            } finally {
                if (conn != null) conn.disconnect();
                FETCH_IN_FLIGHT.set(false);
            }
        });
    }

    private static void handleFetchResult(UpdateInfo info, boolean interactive) {
        if (info == null) {
            LOGGER.debug("Wish is up to date (v{})", WishClient.MOD_VERSION);
            if (interactive) {
                WishClient.sendLocalChatMessage("Wish is up to date (v" + WishClient.MOD_VERSION + ").");
            }
            return;
        }

        LOGGER.info("New version v{} available! Download URL: {}", info.version(), info.directDownloadUrl());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (interactive) {
                sendInteractiveBanner(info);
                return;
            }
            long gen = JOIN_GENERATION.get();
            long elapsedMs = System.currentTimeMillis() - activeJoinMs;
            if (gen > 0L && elapsedMs >= NOTIFICATION_DELAY_SECONDS * 1000L) {
                tryDeliverJoinBanner(gen);
            }
        });
    }

    private static void tryDeliverJoinBanner(long expectedGen) {
        if (expectedGen != JOIN_GENERATION.get() || notifiedGeneration == expectedGen) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        sessionBannerHandled = true;
        UpdateInfo info = cachedUpdate;
        if (!shouldNotify(info, ModConfig.INSTANCE.enableUpdateCheck, ModConfig.INSTANCE.ignoredUpdateVersion)) {
            return;
        }
        notifiedGeneration = expectedGen;
        mc.player.sendSystemMessage(buildOdinBannerForClient(info, mc));
    }

    private static void sendInteractiveBanner(UpdateInfo info) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(buildOdinBannerForClient(info, mc));
            }
        });
    }

    private static Component buildOdinBannerForClient(UpdateInfo info, Minecraft mc) {
        int chatWidthPx = DEFAULT_CHAT_WIDTH_PX;
        if (mc.options != null) {
            chatWidthPx = Math.max(180, ChatComponent.getWidth(mc.options.chatWidth().get()));
        }
        Font font = mc.font;
        int spacePx = font != null ? Math.max(1, font.width(" ")) : 4;
        int dividerSpaces = Math.max(28, Math.min(88, (chatWidthPx / spacePx) - 1));
        Component dividerMeasure = Component.literal(" ".repeat(dividerSpaces))
                .withStyle(Style.EMPTY.withColor(COLOR_DIVIDER_PINK).withStrikethrough(true));

        int barWidthPx = font != null ? font.width(dividerMeasure) : (dividerSpaces * spacePx);
        int titlePadSpaces = computeCenterPadSpaces(font, buildTitleContent(info), barWidthPx, spacePx);
        int subtitlePadSpaces = computeCenterPadSpaces(font, buildSubtitleContent(), barWidthPx, spacePx);
        int linksPadSpaces = computeCenterPadSpaces(font, buildLinksContent(info), barWidthPx, spacePx);

        return buildOdinBannerComponent(info, dividerSpaces, titlePadSpaces, subtitlePadSpaces, linksPadSpaces);
    }

    private static int computeCenterPadSpaces(Font font, Component content, int barWidthPx, int spacePx) {
        if (font == null || spacePx <= 0) return 0;
        int contentWidthPx = font.width(content);
        int leftPadPx = Math.max(0, (barWidthPx - contentWidthPx) / 2);
        return Math.min(40, Math.round((float) leftPadPx / spacePx));
    }

    static Component buildOdinBannerComponent(UpdateInfo info, int dividerSpaces, int linksPadSpaces) {
        return buildOdinBannerComponent(info, dividerSpaces, 0, 0, linksPadSpaces);
    }

    static Component buildOdinBannerComponent(
            UpdateInfo info,
            int dividerSpaces,
            int titlePadSpaces,
            int subtitlePadSpaces,
            int linksPadSpaces
    ) {
        int safeDivSpaces = Math.max(20, Math.min(100, dividerSpaces));
        int safeTitlePad = Math.max(0, Math.min(40, titlePadSpaces));
        int safeSubtitlePad = Math.max(0, Math.min(40, subtitlePadSpaces));
        int safeLinksPad = Math.max(0, Math.min(40, linksPadSpaces));

        String dividerText = " ".repeat(safeDivSpaces);
        Style dividerStyle = Style.EMPTY.withColor(COLOR_DIVIDER_PINK).withStrikethrough(true);
        Component divider = Component.literal(dividerText).withStyle(dividerStyle);

        MutableComponent title = Component.literal(" ".repeat(safeTitlePad)).append(buildTitleContent(info));
        MutableComponent subtitle = Component.literal(" ".repeat(safeSubtitlePad)).append(buildSubtitleContent());
        MutableComponent linksRow = Component.literal(" ".repeat(safeLinksPad)).append(buildLinksContent(info));

        return Component.empty()
                .append(divider).append("\n")
                .append(title).append("\n")
                .append(subtitle).append("\n\n")
                .append(linksRow).append("\n")
                .append(divider);
    }

    private static MutableComponent buildTitleContent(UpdateInfo info) {
        return Component.literal("Wish update available: ")
                .withStyle(Style.EMPTY.withColor(COLOR_TITLE_PINK).withBold(true))
                .append(Component.literal("v" + info.version())
                        .withStyle(Style.EMPTY.withColor(COLOR_TITLE_WHITE).withBold(true)));
    }

    private static Component buildSubtitleContent() {
        return Component.literal("Click the links below to download the latest version.")
                .withStyle(Style.EMPTY.withColor(COLOR_SUBTITLE_GRAY));
    }

    private static MutableComponent buildLinksContent(UpdateInfo info) {
        MutableComponent directDownloadLink = Component.literal("Direct Download")
                .withStyle(Style.EMPTY
                        .withColor(COLOR_LINK_DOWNLOAD)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(info.directDownloadUrl())))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Click to download " + info.assetName() + "\n")
                                        .withStyle(Style.EMPTY.withColor(COLOR_LINK_DOWNLOAD))
                                        .append(Component.literal(info.directDownloadUrl())
                                                .withStyle(Style.EMPTY.withColor(COLOR_TITLE_PINK))))));

        MutableComponent githubLink = Component.literal("GitHub Link")
                .withStyle(Style.EMPTY
                        .withColor(COLOR_LINK_GITHUB)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(info.releasePageUrl())))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Latest Release Notes (v" + info.version() + ")\n")
                                        .withStyle(Style.EMPTY.withColor(COLOR_TITLE_PINK).withBold(true))
                                        .append(Component.literal(info.changelogSummary())
                                                .withStyle(Style.EMPTY.withColor(0xE2E8F0).withBold(false))))));

        MutableComponent ignoreLink = Component.literal("Ignore")
                .withStyle(Style.EMPTY
                        .withColor(COLOR_LINK_IGNORE)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/wish update ignore " + info.version()))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Click to mute notifications for v" + info.version())
                                        .withStyle(Style.EMPTY.withColor(COLOR_SUBTITLE_GRAY)))));

        return Component.empty()
                .append(directDownloadLink)
                .append(Component.literal(LINK_GAP))
                .append(githubLink)
                .append(Component.literal(LINK_GAP))
                .append(ignoreLink);
    }

    static UpdateInfo parseReleaseJson(JsonObject json, String currentVersion) {
        if (json == null) return null;
        String rawTag = HttpClientUtils.getStringOrEmpty(json, "tag_name");
        if (rawTag.isEmpty()) return null;

        String latestVersion = stripLeadingVersionPrefix(rawTag);
        if (latestVersion.isEmpty() || !isNewerVersion(currentVersion, latestVersion)) {
            return null;
        }

        String releasePageUrl = selectReleasePageUrl(json);
        String directUrl = selectBestDownloadUrl(json, releasePageUrl);
        String assetName = selectAssetName(json, directUrl);
        String changelogSummary = sanitizeChangelogSummary(HttpClientUtils.getStringOrEmpty(json, "body"));

        return new UpdateInfo(latestVersion, directUrl, assetName, releasePageUrl, changelogSummary);
    }

    static String selectReleasePageUrl(JsonObject json) {
        if (json == null) return FALLBACK_DOWNLOAD_URL;
        String htmlUrl = HttpClientUtils.getStringOrEmpty(json, "html_url").trim();
        return HttpClientUtils.isHttpsUrl(htmlUrl) ? htmlUrl : FALLBACK_DOWNLOAD_URL;
    }

    static String selectBestDownloadUrl(JsonObject json, String fallbackUrl) {
        String safeFallback = HttpClientUtils.isHttpsUrl(fallbackUrl) ? fallbackUrl.trim() : FALLBACK_DOWNLOAD_URL;
        if (json == null) return safeFallback;

        JsonArray assets = HttpClientUtils.getArrayOrNull(json, "assets");
        if (assets == null || assets.isEmpty()) {
            return safeFallback;
        }

        String anyJarUrl = null;
        String firstHttpsUrl = null;

        for (JsonElement elem : assets) {
            if (!elem.isJsonObject()) continue;
            JsonObject asset = elem.getAsJsonObject();
            String url = HttpClientUtils.getStringOrEmpty(asset, "browser_download_url").trim();
            if (!HttpClientUtils.isHttpsUrl(url)) continue;

            if (firstHttpsUrl == null) {
                firstHttpsUrl = url;
            }
            String name = HttpClientUtils.getStringOrEmpty(asset, "name").trim().toLowerCase();
            if (name.endsWith(".jar")) {
                if (!name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-dev.jar")) {
                    return url;
                }
                if (anyJarUrl == null) {
                    anyJarUrl = url;
                }
            }
        }

        if (anyJarUrl != null) return anyJarUrl;
        if (firstHttpsUrl != null) return firstHttpsUrl;
        return safeFallback;
    }

    static String selectAssetName(JsonObject json, String selectedUrl) {
        if (json == null || selectedUrl == null) return "GitHub Release";
        JsonArray assets = HttpClientUtils.getArrayOrNull(json, "assets");
        if (assets == null) return "GitHub Release";
        for (JsonElement elem : assets) {
            if (!elem.isJsonObject()) continue;
            JsonObject asset = elem.getAsJsonObject();
            String url = HttpClientUtils.getStringOrEmpty(asset, "browser_download_url").trim();
            if (selectedUrl.equals(url)) {
                String name = sanitizePlainText(HttpClientUtils.getStringOrEmpty(asset, "name").trim());
                if (!name.isEmpty()) return name;
            }
        }
        return "GitHub Release";
    }

    static String sanitizeChangelogSummary(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "Click to open the GitHub release page.";
        }

        String[] rawLines = rawBody.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        int lineCount = 0;

        for (String line : rawLines) {
            String clean = sanitizePlainText(line)
                    .replaceFirst("^[#>\\s*-]+", "")
                    .trim();
            if (clean.isEmpty() || clean.startsWith("![")) {
                continue;
            }
            if (lineCount > 0) {
                out.append('\n');
            }
            int remaining = MAX_CHANGELOG_CHARS - out.length();
            if (remaining <= 4) {
                out.append("...");
                break;
            }
            if (clean.length() > remaining) {
                out.append(clean, 0, remaining - 3).append("...");
                break;
            }
            out.append(clean);
            lineCount++;
            if (lineCount >= MAX_CHANGELOG_LINES) {
                break;
            }
        }

        return out.isEmpty() ? "Click to open the GitHub release page." : out.toString();
    }

    static boolean shouldNotify(UpdateInfo info, boolean enabled, String ignoredVersion) {
        if (!enabled || info == null || info.version() == null || info.version().isEmpty()) {
            return false;
        }
        String cleanIgnored = stripLeadingVersionPrefix(ignoredVersion);
        return cleanIgnored.isEmpty() || !cleanIgnored.equalsIgnoreCase(info.version());
    }

    static boolean isNewerVersion(String current, String latest) {
        String cleanCurrent = stripLeadingVersionPrefix(current);
        String cleanLatest = stripLeadingVersionPrefix(latest);
        if (cleanCurrent.isEmpty() || cleanLatest.isEmpty() || "Unknown".equalsIgnoreCase(cleanCurrent)) {
            return false;
        }
        try {
            String[] currParts = cleanCurrent.split("-")[0].split("\\.");
            String[] lateParts = cleanLatest.split("-")[0].split("\\.");

            int length = Math.max(currParts.length, lateParts.length);
            for (int i = 0; i < length; i++) {
                int c = i < currParts.length ? Integer.parseInt(currParts[i]) : 0;
                int l = i < lateParts.length ? Integer.parseInt(lateParts[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripLeadingVersionPrefix(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            return trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private static String sanitizePlainVersion(String raw) {
        String stripped = stripLeadingVersionPrefix(sanitizePlainText(raw));
        return stripped.length() > 32 ? stripped.substring(0, 32) : stripped;
    }

    private static String sanitizePlainText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (c >= ' ' && c != 127) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
