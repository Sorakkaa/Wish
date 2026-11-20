package com.wish.client.features;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningAbilityTrackerTest {

    @Test
    void tracksMiningSpeedBoostActiveCooldownAndReadyFormatting() {
        MiningAbilityTracker tracker = new MiningAbilityTracker();
        long t0 = 10_000L;

        assertEquals("\u26CF Speed Boost: READY", tracker.formatFullLine(null, t0));

        assertFalse(tracker.handleChatLine("You used your Mining Speed Boost Pickaxe Ability!", t0));
        assertEquals(MiningAbilityTracker.AbilityState.ACTIVE, tracker.getState());
        assertEquals("\u26CF Speed Boost: 15s Active", tracker.formatFullLine(null, t0));
        assertEquals("\u26CF Speed Boost: 11s Active", tracker.formatFullLine(null, t0 + 4_500L));

        assertFalse(tracker.handleChatLine("Your Mining Speed Boost has expired!", t0 + 15_000L));
        assertEquals(MiningAbilityTracker.AbilityState.COOLDOWN, tracker.getState());
        assertEquals("\u26CF Speed Boost: 84s Cooldown", tracker.formatFullLine(null, t0 + 15_000L + 21_000L));

        assertTrue(tracker.handleChatLine("Mining Speed Boost is now available!", t0 + 120_000L));
        assertEquals(MiningAbilityTracker.AbilityState.READY, tracker.getState());
        assertEquals("\u26CF Speed Boost: READY", tracker.formatFullLine(null, t0 + 120_000L));
    }

    @Test
    void supportsOtherPickaxeAbilitiesAndCustomTemplate() {
        MiningAbilityTracker tracker = new MiningAbilityTracker();
        long t0 = 5_000L;

        tracker.handleChatLine("You used your Pickobulus Pickaxe Ability!", t0);
        assertEquals(MiningAbilityTracker.AbilityState.COOLDOWN, tracker.getState());
        assertEquals("\u26CF Pickobulus: 110s Cooldown", tracker.formatFullLine(null, t0));

        tracker.handleChatLine("Pickobulus is now available!", t0 + 110_000L);
        assertEquals(MiningAbilityTracker.AbilityState.READY, tracker.getState());

        tracker.handleChatLine("You used your Maniac Miner Pickaxe Ability!", t0 + 200_000L);
        assertEquals("Miner Boost: 10s Active", tracker.formatFullLine("Miner Boost", t0 + 200_000L));
        assertEquals("Mining -> Maniac Miner: 10s Active", tracker.formatFullLine("Mining -> {full}", t0 + 200_000L));
    }

    @Test
    void parsesHeldPickaxeLoreAndTabCooldownUpdates() {
        MiningAbilityTracker tracker = new MiningAbilityTracker();
        tracker.handleToolLore(List.of(
                "Breaking Power 9",
                "Pickaxe Ability: Vein Seeker  RIGHT CLICK",
                "Highlights nearby ores and grants +1 Mining Spread for 14s.",
                "Cooldown: 1m 30s"
        ));

        assertTrue(tracker.hasSeenAbility());
        assertEquals("Vein Seeker", tracker.getAbilityName());

        long t0 = 50_000L;
        tracker.handleChatLine("You used your Vein Seeker Pickaxe Ability!", t0);
        assertEquals("\u26CF Vein Seeker: 14s Active", tracker.formatFullLine(null, t0));

        tracker.handleTabPickaxeLine("Vein Seeker: 1m 24s", t0 + 20_000L);
        tracker.handleChatLine("Your Vein Seeker is on cooldown for 1m 24s.", t0 + 20_000L);
        assertEquals(MiningAbilityTracker.AbilityState.COOLDOWN, tracker.getState());
        assertEquals("\u26CF Vein Seeker: 84s Cooldown", tracker.formatFullLine(null, t0 + 20_000L));
    }

    @Test
    void parsesDivansDrillBlueCheeseMultilineLoreAndHandlesEarlySwitchCancel() {
        MiningAbilityTracker tracker = new MiningAbilityTracker();
        tracker.handleToolLore(List.of(
                "Breaking Power 10",
                "Blue Cheese Goblin Omelette Part",
                "Adds +1 Level to all of your unlocked Heart of",
                "the Mountain perks.",
                "\u29BF Ability: Mining Speed Boost RIGHT CLICK",
                "Grants +300%  Mining Speed for",
                "20s.",
                "Cooldown: 108s",
                "Lustrous Bonus"
        ), true);

        long t0 = 100_000L;
        tracker.handleChatLine("You used your Mining Speed Boost Pickaxe Ability!", t0);
        assertEquals(MiningAbilityTracker.AbilityState.ACTIVE, tracker.getState());
        assertEquals("\u26CF Speed Boost: 20s Active", tracker.formatFullLine(null, t0));
        assertEquals("\u26CF Speed Boost: 5s Active", tracker.formatFullLine(null, t0 + 15_000L));

        tracker.handleChatLine("Your Mining Speed Boost has expired!", t0 + 16_000L);
        assertEquals(MiningAbilityTracker.AbilityState.COOLDOWN, tracker.getState());
        assertEquals("\u26CF Speed Boost: 92s Cooldown", tracker.formatFullLine(null, t0 + 16_000L));

        assertTrue(tracker.handleChatLine("Mining Speed Boost is now available!", t0 + 108_000L));
        assertEquals(MiningAbilityTracker.AbilityState.READY, tracker.getState());

        long t1 = 300_000L;
        tracker.handleChatLine("You used your Mining Speed Boost Pickaxe Ability!", t1);
        assertEquals("\u26CF Speed Boost: 20s Active", tracker.formatFullLine(null, t1));

        tracker.handleChatLine("Your Mining Speed Boost was cancelled!", t1 + 15_500L);
        assertEquals(MiningAbilityTracker.AbilityState.COOLDOWN, tracker.getState());
        assertEquals("\u26CF Speed Boost: 93s Cooldown", tracker.formatFullLine(null, t1 + 15_500L));

        long t2 = 500_000L;
        tracker.handleChatLine("You used your Mining Speed Boost Pickaxe Ability!", t2);
        assertEquals(MiningAbilityTracker.AbilityState.ACTIVE, tracker.getState());
        tracker.handleChatLine("Your Pickaxe Ability was cancelled!", t2 + 16_000L);
        assertEquals(MiningAbilityTracker.AbilityState.COOLDOWN, tracker.getState());
        assertEquals("\u26CF Speed Boost: 92s Cooldown", tracker.formatFullLine(null, t2 + 16_000L));
    }

    @Test
    void addsBlueCheeseBonusFallbackWhenLoreOmitsDuration() {
        MiningAbilityTracker tracker = new MiningAbilityTracker();
        tracker.handleToolLore(List.of(
                "\u29BF Ability: Mining Speed Boost RIGHT CLICK",
                "Cooldown: 108s"
        ), true);

        long t0 = 10_000L;
        tracker.handleChatLine("You used your Mining Speed Boost Pickaxe Ability!", t0);
        assertEquals("\u26CF Speed Boost: 20s Active", tracker.formatFullLine(null, t0));
    }
}
