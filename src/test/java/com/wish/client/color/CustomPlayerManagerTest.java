package com.wish.client.color;

import com.wish.client.features.cosmetic.CosmeticSyncManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomPlayerManagerTest {

    @AfterEach
    void resetCustomPlayers() {
        CustomPlayerManager.setTestState(Collections.emptyMap(), Collections.emptyMap());
    }

    @Test
    void parseJsonToMapAcceptsUuidEntriesWithoutUsernameField() {
        UUID uuid = UUID.fromString("14b458e1-1374-4981-ab21-8bd942449ef7");
        String json = """
                {
                  "14b458e1-1374-4981-ab21-8bd942449ef7": {
                    "color": "WAVE:#FFC6F9:#7B0000",
                    "font": 1,
                    "animSpeed": 0.5,
                    "badge": true,
                    "scaleX": 1.0,
                    "scaleY": 0.3,
                    "scaleZ": 0.01,
                    "spinSpeedY": 2.5
                  },
                  "not-a-uuid": {
                    "color": "#FFFFFF"
                  }
                }
                """;

        Map<UUID, CustomPlayerManager.CustomPlayerData> parsed = CustomPlayerManager.parseJsonToMap(json);
        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        CustomPlayerManager.CustomPlayerData data = parsed.get(uuid);
        assertNotNull(data);
        assertEquals(uuid, data.uuid());
        assertEquals("WAVE:#FFC6F9:#7B0000", data.color());
        assertEquals(1, data.font());
        assertEquals(0.5, data.animSpeed(), 1e-6);
        assertTrue(data.badge());
        assertEquals(0.3f, data.scaleY(), 1e-5f);
        assertEquals(2.5f, data.spinSpeedY(), 1e-5f);
    }

    @Test
    void observePlayerRebindsRenamedUsernameAndEvictsStaleUsername() {
        UUID uuid = UUID.fromString("43562a4a-93e2-437c-934c-64e17383ac00");
        CustomPlayerManager.CustomPlayerData data = new CustomPlayerManager.CustomPlayerData(
                uuid, "CHROMA", 3, 1.0, true, "\uE001", 1.0f, 1.0f, 1.0f, 0.0f
        );
        CustomPlayerManager.setTestState(Map.of(uuid, data), Map.of(uuid, "OldUsername"));

        assertNotNull(CustomPlayerManager.getPlayer(uuid));
        assertNotNull(CustomPlayerManager.getPlayerByResolvedName("oldusername"));
        assertFalse(CustomPlayerManager.observePlayer(uuid, "OldUsername"), "Identical name should not trigger rebuild");

        assertTrue(CustomPlayerManager.observePlayer(uuid, "NewUsername"));
        assertNull(CustomPlayerManager.getPlayerByResolvedName("oldusername"), "Previous username must be evicted");
        assertEquals(data, CustomPlayerManager.getPlayerByResolvedName("NewUsername"));
        assertEquals(1, CustomPlayerManager.getResolvedPlayers().size());
        assertEquals("NewUsername", CustomPlayerManager.getResolvedPlayers().get(0).liveName());
    }

    @Test
    void parseMojangProfileNameValidatesUsernameFormat() {
        assertEquals("Valid_User9", CustomPlayerManager.parseMojangProfileName("{\"id\":\"abc\",\"name\":\"Valid_User9\"}"));
        assertNull(CustomPlayerManager.parseMojangProfileName("{\"name\":\"has space\"}"));
        assertNull(CustomPlayerManager.parseMojangProfileName("{\"name\":\"NameLongerThanSixteenChars\"}"));
        assertNull(CustomPlayerManager.parseMojangProfileName("invalid-json"));
    }

    @Test
    void parseJsonAndMergeSyncedCosmeticsPreservesRepositoryBadges() {
        UUID repoUuid = UUID.fromString("14b458e1-1374-4981-ab21-8bd942449ef7");
        UUID remoteUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        CustomPlayerManager.CustomPlayerData repoPlayer = new CustomPlayerManager.CustomPlayerData(
                repoUuid, "#FF00AA", 2, 1.0, true, "\uE002", 1.0f, 0.8f, 1.0f, 1.5f
        );
        CustomPlayerManager.setTestState(Map.of(repoUuid, repoPlayer), Map.of(repoUuid, "RepoUser"));

        String batchJson = """
                {
                  "ok": true,
                  "players": {
                    "14b458e1-1374-4981-ab21-8bd942449ef7": {
                      "username": "RepoUser",
                      "wing": 2,
                      "halo": 4,
                      "particle": 1,
                      "cosmeticColor": "#00ffaa"
                    },
                    "22222222-2222-2222-2222-222222222222": {
                      "username": "SyncedFriend",
                      "wing": 25,
                      "halo": 8,
                      "particle": 10,
                      "cosmeticColor": "#112233",
                      "badge": true
                    }
                  }
                }
                """;

        int count = CosmeticSyncManager.parseAndApplyBatchResponse(batchJson);
        assertEquals(2, count);

        CustomPlayerManager.CustomPlayerData mergedRepo = CustomPlayerManager.getPlayer(repoUuid);
        assertNotNull(mergedRepo);
        assertTrue(mergedRepo.badge());
        assertEquals("\uE002", mergedRepo.badgeIcon());
        assertEquals("#FF00AA", mergedRepo.color());
        assertEquals(0.8f, mergedRepo.scaleY(), 1e-5f);
        assertEquals(2, mergedRepo.wingId());
        assertEquals(4, mergedRepo.haloId());
        assertEquals(1, mergedRepo.particleId());
        assertEquals("#00FFAA", mergedRepo.cosmeticColor());

        CustomPlayerManager.CustomPlayerData mergedRemote = CustomPlayerManager.getPlayer(remoteUuid, "SyncedFriend");
        assertNotNull(mergedRemote);
        assertFalse(mergedRemote.badge(), "Sync payload must never grant repository badges");
        assertEquals(10, mergedRemote.wingId(), "Out-of-range wing ID must be clamped to 10");
        assertEquals(8, mergedRemote.haloId());
        assertEquals(10, mergedRemote.particleId());
        assertEquals("#112233", mergedRemote.cosmeticColor());
    }
}
