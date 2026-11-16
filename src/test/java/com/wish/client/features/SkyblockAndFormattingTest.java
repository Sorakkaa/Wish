package com.wish.client.features;

import com.wish.client.color.NameColorManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkyblockAndFormattingTest {

    @Test
    void stripColorCodesRemovesFormattingAndNormalizesNonBreakingSpaces() {
        assertEquals("", SkyblockDetector.stripColorCodes(null));
        assertEquals("", SkyblockDetector.stripColorCodes(""));
        assertEquals("✦ Precursor City", SkyblockDetector.stripColorCodes("§b✦\u00A0§dPrecursor\u00A0City§r"));
        assertEquals("Plain §", SkyblockDetector.stripColorCodes("Plain §"));
    }

    @Test
    void automatonFormattingHandlesUnitsTimeAndGemstoneSuffix() {
        assertEquals("500", AutomatonManager.formatCoins(500));
        assertEquals("1.5k", AutomatonManager.formatCoins(1_500));
        assertEquals("2.50M", AutomatonManager.formatCoins(2_500_000));
        assertEquals("1.25B", AutomatonManager.formatCoins(1_250_000_000));

        assertEquals("05m 09s", AutomatonManager.formatTime(309));
        assertEquals("1h 01m 05s", AutomatonManager.formatTime(3665));

        assertEquals("", AutomatonManager.getDisplayName(null));
        assertEquals("Flawed Ruby", AutomatonManager.getDisplayName("Flawed Ruby Gemstone"));
        assertEquals("Robotron Reflector", AutomatonManager.getDisplayName("Robotron Reflector"));
    }

    @Test
    void normalizeHexValidatesAndUppercasesSixDigitColors() {
        assertEquals("#FF00AA", NameColorManager.normalizeHex("ff00aa"));
        assertEquals("#00FF00", NameColorManager.normalizeHex("  #00ff00  "));
        assertNull(NameColorManager.normalizeHex(null));
        assertNull(NameColorManager.normalizeHex("#FFF"));
        assertNull(NameColorManager.normalizeHex("#GGGGGG"));
    }
}
