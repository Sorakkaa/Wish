package com.wish.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WishPathsTest {

    @Test
    void resolveContainedAllowsPathsInsideBaseDir(@TempDir Path tempDir) {
        Path expected = tempDir.toAbsolutePath().normalize().resolve("lyrics.lrc");
        assertEquals(expected, WishPaths.resolveContained(tempDir, "lyrics.lrc"));
        assertEquals(expected, WishPaths.resolveContained(tempDir, "sub/../lyrics.lrc"));
    }

    @Test
    void resolveContainedRejectsPathTraversalAndNullArguments(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> WishPaths.resolveContained(null, "a.lrc"));
        assertThrows(IllegalArgumentException.class, () -> WishPaths.resolveContained(tempDir, null));
        assertThrows(IllegalArgumentException.class, () -> WishPaths.resolveContained(tempDir, "../secret.txt"));
        String siblingEscape = "../" + tempDir.getFileName() + "_sibling/secret.lrc";
        assertThrows(IllegalArgumentException.class, () -> WishPaths.resolveContained(tempDir, siblingEscape));
    }

    @Test
    void writeStringAtomicallyWritesAndReplacesWithoutTempResidue(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("nested/config.json");
        WishPaths.writeStringAtomically(target, "{\"enabled\":true}");
        assertEquals("{\"enabled\":true}", Files.readString(target));
        assertFalse(Files.exists(target.resolveSibling("config.json.tmp")));

        WishPaths.writeStringAtomically(target, "{\"enabled\":false}");
        assertEquals("{\"enabled\":false}", Files.readString(target));
        assertFalse(Files.exists(target.resolveSibling("config.json.tmp")));
    }
}
