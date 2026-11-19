package com.wish.client.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlayerOwnBossTrackerTest {

    @Test
    void soloAndDisabledStatesBlockAutoSendCoords() {
        SlayerOwnBossTracker tracker = new SlayerOwnBossTracker();
        assertFalse(tracker.canAutoSendCoords(true, true, false, false));
        assertFalse(tracker.canAutoSendCoords(true, true, false, true));

        tracker.onChatMessage("§aYou have joined §b[MVP+] BigCarrier§a's party!", "MyPlayer");
        assertTrue(tracker.isInParty());
        assertTrue(tracker.hasOtherPartyMembers());
        assertTrue(tracker.isPartyMember("BigCarrier"));

        assertFalse(tracker.canAutoSendCoords(false, true, false, true));
        assertFalse(tracker.canAutoSendCoords(true, false, false, true));
        assertFalse(tracker.canAutoSendCoords(true, true, true, true));
        assertFalse(tracker.canAutoSendCoords(true, true, false, false));
        assertTrue(tracker.canAutoSendCoords(true, true, false, true));
    }

    @Test
    void partyMembershipTracksJoinsLeavesListsAndSelfExclusion() {
        SlayerOwnBossTracker tracker = new SlayerOwnBossTracker();
        tracker.onChatMessage("Party > [MVP+] MyPlayer: hello", "MyPlayer");
        assertTrue(tracker.isInParty());
        assertFalse(tracker.hasOtherPartyMembers());

        tracker.onChatMessage("[MVP+] CarrierOne joined the party.", "MyPlayer");
        assertTrue(tracker.isPartyMember("carrierone"));
        assertFalse(tracker.isPartyMember("MyPlayer"));

        tracker.onChatMessage("Party Members: [VIP] HelperTwo ● [MVP++] MyPlayer ● HelperThree ○", "MyPlayer");
        assertTrue(tracker.isPartyMember("HelperTwo"));
        assertTrue(tracker.isPartyMember("HelperThree"));
        assertEquals(3, tracker.getPartyMembersSnapshot().size());

        tracker.onChatMessage("[VIP] HelperTwo has left the party.", "MyPlayer");
        assertFalse(tracker.isPartyMember("HelperTwo"));
        assertEquals(2, tracker.getPartyMembersSnapshot().size());

        tracker.onChatMessage("[Wish] IgnoredFakeUser joined the party.", "MyPlayer");
        assertFalse(tracker.isPartyMember("IgnoredFakeUser"));

        tracker.onChatMessage("You left the party.", "MyPlayer");
        assertFalse(tracker.isInParty());
        assertFalse(tracker.hasOtherPartyMembers());
    }

    @Test
    void blazeSplitDemonsAndPetsDoNotMatchOwnBossHologram() {
        assertTrue(SlayerOwnBossTracker.isOwnSlayerBossHologram("spawned by: myplayer", "myplayer"));
        assertTrue(SlayerOwnBossTracker.isOwnSlayerBossHologram("spawned by: [mvp+] myplayer", "myplayer"));
        assertTrue(SlayerOwnBossTracker.isOwnSlayerBossHologram("myplayer's inferno demonlord iv", "myplayer"));

        assertFalse(SlayerOwnBossTracker.isOwnSlayerBossHologram("spawned by: myplayer2", "myplayer"));
        assertFalse(SlayerOwnBossTracker.isOwnSlayerBossHologram("[lv100] myplayer's subzero wisp", "myplayer"));
        assertFalse(SlayerOwnBossTracker.isOwnSlayerBossHologram("myplayer's typhoeus", "myplayer"));
        assertFalse(SlayerOwnBossTracker.isOwnSlayerBossHologram("myplayer's quazii", "myplayer"));

        assertTrue(SlayerOwnBossTracker.isBlazeSplitDemonName("☠ typhoeus 45m❤"));
        assertTrue(SlayerOwnBossTracker.isBlazeSplitDemonName("☠ quazii 45m❤"));
        assertFalse(SlayerOwnBossTracker.isBlazeSplitDemonName("☠ inferno demonlord iv 150m❤"));
    }

    @Test
    void blazeT4SplitPhasesDoNotDuplicateAnnouncements() {
        SlayerOwnBossTracker tracker = new SlayerOwnBossTracker();
        tracker.onChatMessage("You have joined [MVP+] Carrier's party!", "MyPlayer");

        assertFalse(tracker.tryAnnounceOwnBoss(1_000L, true));
        assertTrue(tracker.tryAnnounceOwnBoss(1_000L, false));
        assertTrue(tracker.isBossActive());

        assertFalse(tracker.tryAnnounceOwnBoss(15_000L, false));
        for (int i = 0; i < 80; i++) {
            tracker.onOwnBossTickEnd(false, true);
        }
        assertTrue(tracker.isBossActive());
        assertFalse(tracker.tryAnnounceOwnBoss(25_000L, false));

        tracker.onChatMessage("  NICE! SLAYER BOSS SLAIN!", "MyPlayer");
        for (int i = 0; i < SlayerOwnBossTracker.SLAIN_BOSS_RESET_TICKS; i++) {
            tracker.onOwnBossTickEnd(false, false);
        }
        assertFalse(tracker.isBossActive());
        assertTrue(tracker.tryAnnounceOwnBoss(30_000L, false));
    }

    @Test
    void resetOwnBossStatePreservesPartyWhileDisconnectResetClearsAll() {
        SlayerOwnBossTracker tracker = new SlayerOwnBossTracker();
        tracker.onChatMessage("You have joined [MVP+] Carrier's party!", "MyPlayer");
        assertTrue(tracker.tryAnnounceOwnBoss(1_000L, false));

        tracker.resetOwnBossState();
        assertFalse(tracker.isBossActive());
        assertTrue(tracker.hasOtherPartyMembers());

        tracker.reset();
        assertFalse(tracker.isBossActive());
        assertFalse(tracker.hasOtherPartyMembers());
        assertFalse(tracker.isInParty());
    }
}
