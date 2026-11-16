package com.wish.client.features.visual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LyricsParserTest {

    @Test
    void cleanTrackTitleAndArtistStripNoiseTags() {
        assertEquals("Nightcall", LyricsManager.cleanTrackTitle("Nightcall (Official Video)"));
        assertEquals("Midnight", LyricsManager.cleanTrackTitle("Midnight - 2024 Remaster"));
        assertEquals("Starlight", LyricsManager.cleanTrackTitle("Starlight feat. Guest"));
        assertEquals("Kavinsky", LyricsManager.cleanTrackArtist("Kavinsky - Topic"));
        assertEquals("", LyricsManager.cleanTrackTitle(null));
    }

    @Test
    void parseLrcStringSortsExpandsMultiTagsAndSkipsMetadata() {
        String lrc = """
                [ti:Demo Track]
                [ar:Demo Artist]
                [00:10.50]Second line
                [00:05.25][00:20:00]Chorus line
                [00:01.00]Composer: Someone
                """;

        List<LyricsManager.LyricLine> parsed = LyricsManager.parseLrcString(lrc);
        assertEquals(3, parsed.size());

        assertEquals(5250L, parsed.get(0).timeMs);
        assertEquals("Chorus line", parsed.get(0).text);

        assertEquals(10500L, parsed.get(1).timeMs);
        assertEquals("Second line", parsed.get(1).text);

        assertEquals(20000L, parsed.get(2).timeMs);
        assertEquals("Chorus line", parsed.get(2).text);
    }
}
