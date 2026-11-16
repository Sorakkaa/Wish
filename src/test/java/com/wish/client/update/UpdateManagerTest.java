package com.wish.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManagerTest {

    @Test
    void isNewerVersionComparesSemanticSegmentsAndPrefixes() {
        assertTrue(UpdateManager.isNewerVersion("0.1.2", "v0.1.3"));
        assertTrue(UpdateManager.isNewerVersion("0.9.5", "0.10.0"));
        assertTrue(UpdateManager.isNewerVersion("V1.0.0", "1.0.1"));
        assertFalse(UpdateManager.isNewerVersion("0.1.3", "0.1.3"));
        assertFalse(UpdateManager.isNewerVersion("0.2.0", "0.1.9"));
        assertFalse(UpdateManager.isNewerVersion("Unknown", "9.9.9"));
        assertFalse(UpdateManager.isNewerVersion(null, "1.0.0"));
    }

    @Test
    void parseReleaseJsonPrefersMainJarAndSanitizesChangelog() {
        JsonObject release = new JsonObject();
        release.addProperty("tag_name", "v0.2.0");
        release.addProperty("html_url", "https://github.com/Sorakkaa/Wish/releases/tag/v0.2.0");
        release.addProperty("body", "## What's New\n- §cAdded 3D world lyrics\n- Improved under-roof weather\n![banner](https://example.com/img.png)\n- Line 4\n- Line 5 extra");

        JsonArray assets = new JsonArray();
        JsonObject sourcesJar = new JsonObject();
        sourcesJar.addProperty("name", "wish-0.2.0-sources.jar");
        sourcesJar.addProperty("browser_download_url", "https://github.com/Sorakkaa/Wish/releases/download/v0.2.0/wish-0.2.0-sources.jar");
        assets.add(sourcesJar);

        JsonObject mainJar = new JsonObject();
        mainJar.addProperty("name", "wish-0.2.0.jar");
        mainJar.addProperty("browser_download_url", "https://github.com/Sorakkaa/Wish/releases/download/v0.2.0/wish-0.2.0.jar");
        assets.add(mainJar);

        release.add("assets", assets);

        UpdateManager.UpdateInfo info = UpdateManager.parseReleaseJson(release, "0.1.2");
        assertNotNull(info);
        assertEquals("0.2.0", info.version());
        assertEquals("wish-0.2.0.jar", info.assetName());
        assertEquals("https://github.com/Sorakkaa/Wish/releases/download/v0.2.0/wish-0.2.0.jar", info.directDownloadUrl());
        assertEquals("https://github.com/Sorakkaa/Wish/releases/tag/v0.2.0", info.releasePageUrl());
        assertFalse(info.changelogSummary().contains("§"));
        assertFalse(info.changelogSummary().contains("!["));
        assertTrue(info.changelogSummary().contains("Added 3D world lyrics"));

        assertNull(UpdateManager.parseReleaseJson(release, "0.2.0"));
    }

    @Test
    void selectBestDownloadUrlRejectsInsecureLinks() {
        JsonObject release = new JsonObject();
        JsonArray assets = new JsonArray();
        JsonObject httpAsset = new JsonObject();
        httpAsset.addProperty("name", "wish-0.2.0.jar");
        httpAsset.addProperty("browser_download_url", "http://insecure.example.com/wish.jar");
        assets.add(httpAsset);
        release.add("assets", assets);

        assertEquals(
                "https://github.com/Sorakkaa/Wish/releases/latest",
                UpdateManager.selectBestDownloadUrl(release, "https://github.com/Sorakkaa/Wish/releases/latest")
        );
    }

    @Test
    void shouldNotifyRespectsEnableFlagAndIgnoredVersion() {
        UpdateManager.UpdateInfo info = new UpdateManager.UpdateInfo(
                "0.2.0",
                "https://github.com/Sorakkaa/Wish/releases/latest",
                "wish-0.2.0.jar",
                "https://github.com/Sorakkaa/Wish/releases/tag/v0.2.0",
                "Changelog"
        );

        assertTrue(UpdateManager.shouldNotify(info, true, ""));
        assertFalse(UpdateManager.shouldNotify(info, false, ""));
        assertFalse(UpdateManager.shouldNotify(info, true, "0.2.0"));
        assertFalse(UpdateManager.shouldNotify(info, true, "v0.2.0"));
        assertTrue(UpdateManager.shouldNotify(info, true, "0.1.9"));
    }

    @Test
    void buildOdinBannerComponentContainsFramedLinesAndInteractiveLinks() {
        UpdateManager.UpdateInfo info = new UpdateManager.UpdateInfo(
                "0.2.0",
                "https://github.com/Sorakkaa/Wish/releases/download/v0.2.0/wish-0.2.0.jar",
                "wish-0.2.0.jar",
                "https://github.com/Sorakkaa/Wish/releases/tag/v0.2.0",
                "Added 3D sky glow & karaoke"
        );

        Component banner = UpdateManager.buildOdinBannerComponent(info, 78, 12);
        String flat = banner.getString();
        assertTrue(flat.contains("Wish update available: v0.2.0"));
        assertTrue(flat.contains("Click the links below to download the latest version."));
        assertTrue(flat.contains("Direct Download"));
        assertTrue(flat.contains("GitHub Link"));
        assertTrue(flat.contains("Ignore"));

        List<ClickEvent> clickEvents = new ArrayList<>();
        List<TextColor> colors = new ArrayList<>();
        banner.visit((style, text) -> {
            if (style.getClickEvent() != null) {
                clickEvents.add(style.getClickEvent());
            }
            if (style.getColor() != null) {
                colors.add(style.getColor());
            }
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);

        assertTrue(clickEvents.contains(new ClickEvent.OpenUrl(URI.create(info.directDownloadUrl()))));
        assertTrue(clickEvents.contains(new ClickEvent.OpenUrl(URI.create(info.releasePageUrl()))));
        assertTrue(clickEvents.contains(new ClickEvent.RunCommand("/wish update ignore 0.2.0")));
        assertTrue(colors.contains(TextColor.fromRgb(0xFFC6F9)));
    }

    @Test
    void shouldScheduleSessionJoinIgnoresHubTransfersAndAllowsNewJoins() {
        assertTrue(UpdateManager.shouldScheduleSessionJoin("server:mc.hypixel.net", null, null, false, ""));
        assertFalse(UpdateManager.shouldScheduleSessionJoin("server:mc.hypixel.net", null, null, true, "server:mc.hypixel.net"));
        assertTrue(UpdateManager.shouldScheduleSessionJoin("solo", null, null, true, "server:mc.hypixel.net"));
    }

    @Test
    void buildOdinBannerComponentAppliesRequestedCenterPaddingToAllLines() {
        UpdateManager.UpdateInfo info = new UpdateManager.UpdateInfo(
                "0.2.0",
                "https://github.com/Sorakkaa/Wish/releases/download/v0.2.0/wish-0.2.0.jar",
                "wish-0.2.0.jar",
                "https://github.com/Sorakkaa/Wish/releases/tag/v0.2.0",
                "Added 3D sky glow & karaoke"
        );

        Component banner = UpdateManager.buildOdinBannerComponent(info, 78, 14, 8, 11);
        String flat = banner.getString();
        assertTrue(flat.contains("              Wish update available: v0.2.0"));
        assertTrue(flat.contains("        Click the links below to download the latest version."));
        assertTrue(flat.contains("           Direct Download"));

        List<ClickEvent> clickEvents = new ArrayList<>();
        banner.visit((style, text) -> {
            if (style.getClickEvent() != null) {
                clickEvents.add(style.getClickEvent());
            }
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);

        assertTrue(clickEvents.contains(new ClickEvent.OpenUrl(URI.create(info.directDownloadUrl()))));
        assertTrue(clickEvents.contains(new ClickEvent.OpenUrl(URI.create(info.releasePageUrl()))));
        assertTrue(clickEvents.contains(new ClickEvent.RunCommand("/wish update ignore 0.2.0")));
    }
}
