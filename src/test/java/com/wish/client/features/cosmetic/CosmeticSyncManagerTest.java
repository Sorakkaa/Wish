package com.wish.client.features.cosmetic;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticSyncManagerTest {

    @Test
    void mojangServerIdHashMatchesProtocolTwosComplementSpec() {
        assertEquals("4ed1f46bbe04bc756bcb17c0c7ce3e4632f06a48",
                CosmeticSyncManager.computeMojangServerIdHash("Notch", new byte[0], new byte[0]));
        assertEquals("-7c9d5b0044c130109a5d7b5fb5c317c02b4e28c1",
                CosmeticSyncManager.computeMojangServerIdHash("jeb_", new byte[0], new byte[0]));
        assertEquals("88e16a1019277b15d58faf0541e11910eb756f6",
                CosmeticSyncManager.computeMojangServerIdHash("simon", new byte[0], new byte[0]));
    }

    @Test
    void validateSyncEndpointUrlRequiresHttpsExceptLocalhostLoopback() {
        assertEquals("https://wish-cosmetics.example.workers.dev",
                CosmeticSyncManager.validateSyncEndpointUrl("https://wish-cosmetics.example.workers.dev/"));
        assertEquals("http://127.0.0.1:8787",
                CosmeticSyncManager.validateSyncEndpointUrl("http://127.0.0.1:8787/"));
        assertEquals("http://localhost:8787",
                CosmeticSyncManager.validateSyncEndpointUrl("http://localhost:8787"));

        assertNull(CosmeticSyncManager.validateSyncEndpointUrl(null));
        assertNull(CosmeticSyncManager.validateSyncEndpointUrl("   "));
        assertNull(CosmeticSyncManager.validateSyncEndpointUrl("http://evil.example.com"));
        assertNull(CosmeticSyncManager.validateSyncEndpointUrl("https://user:pass@evil.example.com"));
        assertNull(CosmeticSyncManager.validateSyncEndpointUrl("https://evil.example.com?foo=bar"));
        assertNull(CosmeticSyncManager.validateSyncEndpointUrl("file:///etc/passwd"));
    }

    @Test
    void jsonBuildersClampCosmeticsAndIncludeExpectedFields() {
        String upload = CosmeticSyncManager.buildSyncUploadJson(
                "0123456789abcdef0123456789abcdef",
                "PlayerOne",
                "server-hash-123",
                99,
                2,
                -4,
                "#aa55ff"
        );
        assertTrue(upload.contains("\"wing\":10"));
        assertTrue(upload.contains("\"halo\":2"));
        assertTrue(upload.contains("\"particle\":0"));
        assertTrue(upload.contains("\"cosmeticColor\":\"#AA55FF\""));
        assertTrue(upload.contains("\"serverId\":\"server-hash-123\""));

        UUID u1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String query = CosmeticSyncManager.buildBatchQueryJson(List.of(u1));
        assertTrue(query.contains(u1.toString()));
        assertNotNull(query.getBytes(StandardCharsets.UTF_8));
    }
}
