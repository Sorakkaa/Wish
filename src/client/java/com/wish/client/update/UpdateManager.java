package com.wish.client.update;

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
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class UpdateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-Updater");

    private static final String UPDATE_URL = "https://api.github.com/repos/Sorakkaa/Wish/releases/latest";
    

    private static boolean hasCheckedForUpdates = false;
    private static volatile net.minecraft.network.chat.Component pendingMsg1 = null;
    private static volatile net.minecraft.network.chat.Component pendingMsg2 = null;

    public static void onPlayerJoin() {
        if (pendingMsg1 != null && pendingMsg2 != null) {
            WishClient.sendLocalChatMessage(pendingMsg1);
            WishClient.sendLocalChatMessage(pendingMsg2);
            pendingMsg1 = null;
            pendingMsg2 = null;
        }
    }

    public static void checkForUpdates() {
        onPlayerJoin();
        if (!com.wish.client.config.ModConfig.INSTANCE.enableUpdateCheck) return;
        if (hasCheckedForUpdates) return;
        hasCheckedForUpdates = true;
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("Checking for Wish updates...");
                URL url = URI.create(UPDATE_URL).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Wish-Client/" + WishClient.MOD_VERSION);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                    }

                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    if (json.has("tag_name")) {
                        String latestVersion = json.get("tag_name").getAsString();
                        if (latestVersion.startsWith("v") || latestVersion.startsWith("V")) {
                            latestVersion = latestVersion.substring(1);
                        }

                        if (isNewerVersion(WishClient.MOD_VERSION, latestVersion)) {
                            String downloadUrl = "https://github.com/Sorakkaa/Wish/releases/latest";
                            if (json.has("assets") && json.getAsJsonArray("assets").size() > 0) {
                                JsonObject asset = json.getAsJsonArray("assets").get(0).getAsJsonObject();
                                if (asset.has("browser_download_url")) {
                                    downloadUrl = asset.get("browser_download_url").getAsString();
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
                                WishClient.sendLocalChatMessage(msg1);
                                WishClient.sendLocalChatMessage(msg2);
                            } else {
                                pendingMsg1 = msg1;
                                pendingMsg2 = msg2;
                            }
                        } else {
                            LOGGER.info("Wish is up to date (v" + WishClient.MOD_VERSION + ")");
                        }
                    }
                } else if (conn.getResponseCode() == 404) {
                    LOGGER.debug("No release published on GitHub yet (HTTP 404).");
                } else {
                    LOGGER.warn("Failed to check for updates, HTTP response code: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to check for updates", e);
            }
        });
    }

    private static boolean isNewerVersion(String current, String latest) {
        
        
        try {
            String[] currParts = current.split("\\.");
            String[] lateParts = latest.split("\\.");
            
            int length = Math.max(currParts.length, lateParts.length);
            for (int i = 0; i < length; i++) {
                int c = i < currParts.length ? Integer.parseInt(currParts[i]) : 0;
                int l = i < lateParts.length ? Integer.parseInt(lateParts[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
            return false;
        } catch (Exception e) {
            return !current.equals(latest); 
        }
    }

}
