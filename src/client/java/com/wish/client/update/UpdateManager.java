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
                    if (json.has("tag_name") && json.has("assets")) {
                        String latestVersion = json.get("tag_name").getAsString();
                        // Usually tags have a 'v' prefix like 'v1.6.0'
                        if (latestVersion.startsWith("v") || latestVersion.startsWith("V")) {
                            latestVersion = latestVersion.substring(1);
                        }

                        if (isNewerVersion(WishClient.MOD_VERSION, latestVersion)) {
                            LOGGER.info("New version found: " + latestVersion + ". Looking for assets...");
                            
                            var assets = json.getAsJsonArray("assets");
                            if (assets.size() > 0) {
                                JsonObject asset = assets.get(0).getAsJsonObject();
                                if (asset.has("browser_download_url")) {
                                    String downloadUrl = asset.get("browser_download_url").getAsString();
                                    LOGGER.info("Found download URL: " + downloadUrl);

                                    net.minecraft.network.chat.Style prefixStyle = net.minecraft.network.chat.Style.EMPTY
                                        .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFC6F9))
                                        .withBold(true);

                                    net.minecraft.network.chat.MutableComponent prefix = net.minecraft.network.chat.Component.literal("[Song")
                                        .append(net.minecraft.network.chat.Component.literal("kkaa]\u00A0"))
                                        .withStyle(prefixStyle);

                                    net.minecraft.network.chat.MutableComponent msg1 = net.minecraft.network.chat.Component.literal("")
                                        .append(prefix)
                                        .append(net.minecraft.network.chat.Component.literal("A new update (v" + latestVersion + ") is available!").withStyle(net.minecraft.ChatFormatting.GREEN));

                                    net.minecraft.network.chat.MutableComponent msg2 = net.minecraft.network.chat.Component.literal("")
                                        .append(prefix)
                                        .append(net.minecraft.network.chat.Component.literal(downloadUrl).withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN).withUnderlined(true)));

                                    if (net.minecraft.client.Minecraft.getInstance().player != null) {
                                        WishClient.sendLocalChatMessage(msg1);
                                        WishClient.sendLocalChatMessage(msg2);
                                    } else {
                                        pendingMsg1 = msg1;
                                        pendingMsg2 = msg2;
                                    }
                                }
                            }
                        } else {
                            LOGGER.info("Wish is up to date (v" + WishClient.MOD_VERSION + ")");
                        }
                    }
                } else {
                    LOGGER.error("Failed to check for updates, HTTP response code: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to check for updates", e);
            }
        });
    }

    private static boolean isNewerVersion(String current, String latest) {
        // Simple string comparison for versions like "1.0.0" vs "1.0.1"
        // In a real scenario, you might want to split by "." and compare integers
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
            return !current.equals(latest); // Fallback
        }
    }

}
