package com.wish.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class HttpClientUtils {

    public static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;

    private HttpClientUtils() {}

    public static boolean isHttpsUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String readBoundedUtf8(InputStream in, int maxBytes) throws IOException {
        return new String(readBoundedBytes(in, maxBytes), StandardCharsets.UTF_8);
    }

    public static byte[] readBoundedBytes(InputStream in, int maxBytes) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("HTTP response exceeded maximum size limit of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    public static JsonObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static JsonArray parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static JsonObject getObjectOrNull(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement el = obj.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    public static JsonArray getArrayOrNull(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement el = obj.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    public static String getStringOrEmpty(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return "";
        }
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : "";
    }
}
