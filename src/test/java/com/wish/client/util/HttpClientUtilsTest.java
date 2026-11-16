package com.wish.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientUtilsTest {

    @Test
    void isHttpsUrlAcceptsOnlyValidHttpsUrlsWithHost() {
        assertTrue(HttpClientUtils.isHttpsUrl("https://api.hypixel.net/v2/skyblock/bazaar"));
        assertTrue(HttpClientUtils.isHttpsUrl("  HTTPS://lrclib.net/api/get  "));
        assertFalse(HttpClientUtils.isHttpsUrl("http://127.0.0.1:9863/query"));
        assertFalse(HttpClientUtils.isHttpsUrl("file:///etc/passwd"));
        assertFalse(HttpClientUtils.isHttpsUrl("https:///missing-host"));
        assertFalse(HttpClientUtils.isHttpsUrl("not a url"));
        assertFalse(HttpClientUtils.isHttpsUrl(null));
        assertFalse(HttpClientUtils.isHttpsUrl("   "));
    }

    @Test
    void readBoundedUtf8ReadsWithinLimitAndRejectsOversizedStreams() throws IOException {
        byte[] exact = "hello".getBytes(StandardCharsets.UTF_8);
        assertEquals("hello", HttpClientUtils.readBoundedUtf8(new ByteArrayInputStream(exact), 5));
        assertEquals("", HttpClientUtils.readBoundedUtf8(null, 5));

        byte[] tooLarge = "abcdef".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> HttpClientUtils.readBoundedUtf8(new ByteArrayInputStream(tooLarge), 5));
    }

    @Test
    void jsonHelpersHandleMalformedAndUnexpectedTypesSafely() {
        assertNull(HttpClientUtils.parseJsonObject(null));
        assertNull(HttpClientUtils.parseJsonObject("{not-json"));
        assertNull(HttpClientUtils.parseJsonObject("[1, 2, 3]"));
        assertNull(HttpClientUtils.parseJsonArray("{\"a\":1}"));

        JsonObject obj = HttpClientUtils.parseJsonObject("{\"title\":\"Song\",\"meta\":{\"ok\":true},\"items\":[10],\"bad\":null}");
        assertNotNull(obj);
        assertEquals("Song", HttpClientUtils.getStringOrEmpty(obj, "title"));
        assertEquals("", HttpClientUtils.getStringOrEmpty(obj, "missing"));
        assertEquals("", HttpClientUtils.getStringOrEmpty(obj, "meta"));
        assertNotNull(HttpClientUtils.getObjectOrNull(obj, "meta"));
        assertNull(HttpClientUtils.getObjectOrNull(obj, "title"));
        assertNull(HttpClientUtils.getObjectOrNull(obj, "bad"));

        JsonArray arr = HttpClientUtils.getArrayOrNull(obj, "items");
        assertNotNull(arr);
        assertEquals(1, arr.size());
        assertNull(HttpClientUtils.getArrayOrNull(obj, "title"));
    }
}
