package com.wish.client.features.visual;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealPlayerFilterTest {

    @Test
    void isValidMinecraftUsernameAcceptsValidNamesAndRejectsNpcOrInvalidFormats() {
        assertTrue(RealPlayerFilter.isValidMinecraftUsername("x"));
        assertTrue(RealPlayerFilter.isValidMinecraftUsername("Steve"));
        assertTrue(RealPlayerFilter.isValidMinecraftUsername("Player_123456789"));

        assertFalse(RealPlayerFilter.isValidMinecraftUsername(null));
        assertFalse(RealPlayerFilter.isValidMinecraftUsername(""));
        assertFalse(RealPlayerFilter.isValidMinecraftUsername("NameTooLongForMc1"));
        assertFalse(RealPlayerFilter.isValidMinecraftUsername("[NPC] Jerry"));
        assertFalse(RealPlayerFilter.isValidMinecraftUsername("CIT-12ab34cd"));
        assertFalse(RealPlayerFilter.isValidMinecraftUsername("\u00A7cShadow"));
        assertFalse(RealPlayerFilter.isValidMinecraftUsername("Two Words"));
    }

    @Test
    void isValidRealPlayerIdentityAllowsLiveOnlineAndOfflinePlayersOnly() {
        UUID onlineV4 = UUID.randomUUID();
        UUID offlineV3 = UUID.nameUUIDFromBytes("OfflinePlayer:Player123".getBytes(StandardCharsets.UTF_8));
        UUID npcV2 = new UUID((onlineV4.getMostSignificantBits() & ~0x0000_0000_0000_F000L) | 0x0000_0000_0000_2000L,
                onlineV4.getLeastSignificantBits());

        assertTrue(RealPlayerFilter.isValidRealPlayerIdentity(onlineV4, "Soraka", "Soraka", true));
        assertTrue(RealPlayerFilter.isValidRealPlayerIdentity(offlineV3, "Player123", "Player123", true));

        assertFalse(RealPlayerFilter.isValidRealPlayerIdentity(npcV2, "Jerry", "Jerry", true));
        assertFalse(RealPlayerFilter.isValidRealPlayerIdentity(onlineV4, "Soraka", "Soraka", false));
        assertFalse(RealPlayerFilter.isValidRealPlayerIdentity(null, "Soraka", "Soraka", true));
        assertFalse(RealPlayerFilter.isValidRealPlayerIdentity(onlineV4, "[NPC] Banker", "[NPC] Banker", true));
        assertFalse(RealPlayerFilter.isValidRealPlayerIdentity(onlineV4, "Soraka", "\u00A7cSoraka", true));
    }
}
