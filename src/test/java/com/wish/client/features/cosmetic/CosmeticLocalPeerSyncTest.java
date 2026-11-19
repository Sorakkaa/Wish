package com.wish.client.features.cosmetic;

import com.wish.client.color.CustomPlayerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticLocalPeerSyncTest {

    @AfterEach
    void cleanup() {
        CustomPlayerManager.onDisconnect();
    }

    @Test
    void peerPacketAppliesValidSyncedCosmeticsAndNeverGrantsBadges() {
        UUID friendUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        String friendPkt = CosmeticLocalPeerSync.buildPeerPacketJson(friendUuid, "SecondAcct", 99, 8, -3);
        assertTrue(CosmeticLocalPeerSync.parseAndApplyPeerPacket(friendPkt));
        CustomPlayerManager.CustomPlayerData friend = CustomPlayerManager.getPlayer(friendUuid, "SecondAcct");
        assertNotNull(friend);
        assertFalse(friend.badge(), "Non-repo account synced via P2P must never receive badges");
        assertEquals(10, friend.wingId());
        assertEquals(8, friend.haloId());
        assertEquals(0, friend.particleId());
    }

    @Test
    void malformedOrInvalidUsernamePeerPacketIsRejected() {
        assertFalse(CosmeticLocalPeerSync.parseAndApplyPeerPacket("not-json"));
        assertFalse(CosmeticLocalPeerSync.parseAndApplyPeerPacket(
                "{\"wishP2P\":1,\"uuid\":\"bad-uuid\",\"username\":\"ValidUser\",\"wing\":1}"));
        assertFalse(CosmeticLocalPeerSync.parseAndApplyPeerPacket(
                "{\"wishP2P\":1,\"uuid\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\",\"username\":\"Bad Name!\",\"wing\":1}"));
    }

    @Test
    void peerPacketSyncsCustomPseudoPlayerSizeAndSanitizesUnsafeFields() {
        UUID friendUuid = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        CustomPlayerManager.SyncedCosmeticRecord rec = new CustomPlayerManager.SyncedCosmeticRecord(
                friendUuid,
                "FriendTwo",
                2,
                4,
                3,
                null,
                "GRADIENT:#ff55aa:#55ffff",
                3,
                1.5,
                "§aCutie",
                0.75f,
                1.45f,
                0.75f,
                1.25f
        );
        String pkt = CosmeticLocalPeerSync.buildPeerPacketJson(rec);
        assertFalse(pkt.contains("spoofedName"), "Name Spoofer must never be serialized into P2P sync packets");
        assertFalse(pkt.contains("Cutie"));
        assertTrue(CosmeticLocalPeerSync.parseAndApplyPeerPacket(pkt));

        CustomPlayerManager.CustomPlayerData loaded = CustomPlayerManager.getPlayer(friendUuid, "FriendTwo");
        assertNotNull(loaded);
        assertFalse(loaded.badge());
        assertEquals(2, loaded.wingId());
        assertEquals(4, loaded.haloId());
        assertEquals(3, loaded.particleId());
        assertEquals("GRADIENT:#FF55AA:#55FFFF", loaded.color());
        assertEquals(3, loaded.font());
        assertEquals(1.5, loaded.animSpeed(), 1e-6);
        assertEquals("", loaded.spoofedName(), "Name Spoofer must never be applied via sync");
        assertEquals(0.75f, loaded.scaleX(), 1e-5f);
        assertEquals(1.45f, loaded.scaleY(), 1e-5f);
        assertEquals(1.25f, loaded.spinSpeedY(), 1e-5f);

        String unsafeJson = """
                {
                  "wishP2P": 1,
                  "uuid": "dddddddd-dddd-dddd-dddd-dddddddddddd",
                  "username": "FriendTwo",
                  "wing": 1,
                  "color": "not-valid-color",
                  "spoofedName": "https://phishing.example",
                  "scaleY": 999.0,
                  "badge": true
                }
                """;
        assertTrue(CosmeticLocalPeerSync.parseAndApplyPeerPacket(unsafeJson));
        CustomPlayerManager.CustomPlayerData clamped = CustomPlayerManager.getPlayer(friendUuid, "FriendTwo");
        assertNotNull(clamped);
        assertFalse(clamped.badge());
        assertEquals("", clamped.color());
        assertEquals("", clamped.spoofedName());
        assertEquals(5.0f, clamped.scaleY(), 1e-5f);
    }
}
