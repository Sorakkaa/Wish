package com.wish.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wish.client.WishClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class UpdateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Updater");

    private static final String UPDATE_URL = "https://api.github.com/repos/Sorakkaa/Wish/releases/latest";

    private static volatile boolean hasCheckedForUpdates = false;
    private static volatile net.minecraft.network.chat.Component pendingMsg1 = null;
    private static volatile net.minecraft.network.chat.Component pendingMsg2 = null;

    public static void onPlayerJoin() {
        final net.minecraft.network.chat.Component msg1 = pendingMsg1;
        final net.minecraft.network.chat.Component msg2 = pendingMsg2;
        if (msg1 != null && msg2 != null) {
            pendingMsg1 = null;
            pendingMsg2 = null;
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
                WishClient.sendLocalChatMessage(msg1);
                WishClient.sendLocalChatMessage(msg2);
            });
        }
    }

    public static void checkForUpdates() {
        if (!com.wish.client.config.ModConfig.INSTANCE.enableUpdateCheck) return;
        onPlayerJoin();
        if (hasCheckedForUpdates) return;
        hasCheckedForUpdates = true;
        CompletableFuture.runAsync(() -> {
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
                if (responseCode == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                    }

                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    if (json.has("tag_name") && !json.get("tag_name").isJsonNull()) {
                        String latestVersion = json.get("tag_name").getAsString();
                        if (latestVersion.startsWith("v") || latestVersion.startsWith("V")) {
                            latestVersion = latestVersion.substring(1);
                        }

                        if (isNewerVersion(WishClient.MOD_VERSION, latestVersion)) {
                            String downloadUrl = "https://github.com/Sorakkaa/Wish/releases/latest";
                            if (json.has("assets") && json.get("assets").isJsonArray()) {
                                JsonArray assets = json.getAsJsonArray("assets");
                                if (!assets.isEmpty() && assets.get(0).isJsonObject()) {
                                    JsonObject asset = assets.get(0).getAsJsonObject();
                                    if (asset.has("browser_download_url") && !asset.get("browser_download_url").isJsonNull()) {
                                        downloadUrl = asset.get("browser_download_url").getAsString();
                                    }
                                }
                            }

                            LOGGER.info("New version v{} available! Download URL: {}", latestVersion, downloadUrl);

                            net.minecraft.network.chat.Style prefixStyle = net.minecraft.network.chat.Style.EMPTY
                                .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFC6F9))
                                .withBold(true);

                            net.minecraft.network.chat.MutableComponent prefix = net.minecraft.network.chat.Component.literal("[Wish] ")
                                .withStyle(prefixStyle);

                            net.minecraft.network.chat.MutableComponent msg1 = net.minecraft.network.chat.Component.literal("")
                                .append(prefix)
                                .append(net.minecraft.network.chat.Component.literal("A new update (§a§lv" + latestVersion + "§r) is available!"));

                            final String finalUrl = downloadUrl;
                            net.minecraft.network.chat.MutableComponent msg2 = net.minecraft.network.chat.Component.literal("")
                                .append(prefix)
                                .append(net.minecraft.network.chat.Component.literal("§e§nClick here to download update")
                                    .withStyle(style -> style
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(URI.create(finalUrl)))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("§7Click to open: \n§b" + finalUrl)))));

                            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        Thread.sleep(5000);
                                    } catch (InterruptedException ignored) {}
                                    WishClient.sendLocalChatMessage(msg1);
                                    WishClient.sendLocalChatMessage(msg2);
                                });
                            } else {
                                pendingMsg1 = msg1;
                                pendingMsg2 = msg2;
                            }
                        } else {
                            LOGGER.debug("Wish is up to date (v" + WishClient.MOD_VERSION + ")");
                        }
                    }
                } else if (responseCode == 404) {
                    LOGGER.debug("No release published on GitHub yet (HTTP 404).");
                } else {
                    LOGGER.warn("Failed to check for updates, HTTP response code: " + responseCode);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to check for updates", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static boolean isNewerVersion(String current, String latest) {
        if (current == null || latest == null || "Unknown".equalsIgnoreCase(current)) return false;
        try {
            String[] currParts = current.split("-")[0].split("\\.");
            String[] lateParts = latest.split("-")[0].split("\\.");
            
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

}
