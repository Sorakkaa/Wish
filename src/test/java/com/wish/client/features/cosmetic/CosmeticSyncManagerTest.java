package com.wish.client.features.cosmetic;

import com.wish.client.color.CustomPlayerManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void candidateCollectionPrioritizesWorldPlayersAndFiltersFakeHypixelTabEntries() {
        List<CosmeticSyncManager.PlayerQueryCandidate> tabPlayers = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            UUID fakeUuid = new UUID(0x0000000000002000L, i + 1L);
            tabPlayers.add(new CosmeticSyncManager.PlayerQueryCandidate(fakeUuid, "!A-" + (char) ('a' + (i % 20))));
        }
        UUID tabRealUuid = UUID.fromString("33333333-3333-4333-8333-333333333333");
        tabPlayers.add(new CosmeticSyncManager.PlayerQueryCandidate(tabRealUuid, "TabFriend"));

        UUID friendWorldUuid = UUID.fromString("44444444-4444-4444-8444-444444444444");
        UUID npcWorldUuid = new UUID(0x0000000000002000L, 999L);
        List<CosmeticSyncManager.PlayerQueryCandidate> worldPlayers = List.of(
                new CosmeticSyncManager.PlayerQueryCandidate(npcWorldUuid, "FakeNpc"),
                new CosmeticSyncManager.PlayerQueryCandidate(friendWorldUuid, "NearbyFriend")
        );

        List<UUID> batch = CosmeticSyncManager.collectCandidateQueryUuids(worldPlayers, tabPlayers, 10_000L, true);
        assertEquals(2, batch.size());
        assertEquals(friendWorldUuid, batch.get(0));
        assertEquals(tabRealUuid, batch.get(1));
        assertFalse(batch.contains(npcWorldUuid));
    }

    @Test
    void certificateProofBuildsValidMojangPayloadAndChallengeSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair mojangRoot = kpg.generateKeyPair();
        KeyPair playerPair = kpg.generateKeyPair();

        UUID uuid = UUID.fromString("14b458e1-1374-4981-ab21-8bd942449ef7");
        String compactUuid = "14b458e113744981ab218bd942449ef7";
        String serverId = "-2105ecb48bbba86f4e37212d281e70d396c2f825";
        long expiresAtMs = System.currentTimeMillis() + 3_600_000L;

        byte[] certPayload = CosmeticSyncManager.buildProfilePublicKeySignedPayload(
                uuid, expiresAtMs, playerPair.getPublic().getEncoded()
        );
        Signature rootSigner = Signature.getInstance("SHA1withRSA");
        rootSigner.initSign(mojangRoot.getPrivate());
        rootSigner.update(certPayload);
        byte[] keySig = rootSigner.sign();

        Signature rootVerifier = Signature.getInstance("SHA1withRSA");
        rootVerifier.initVerify(mojangRoot.getPublic());
        rootVerifier.update(certPayload);
        assertTrue(rootVerifier.verify(keySig));

        String challengeMsg = CosmeticSyncManager.buildCertChallengeString(compactUuid, serverId);
        byte[] challengeBytes = challengeMsg.getBytes(StandardCharsets.UTF_8);
        String challengeSigB64 = CosmeticSyncManager.signSha256RsaBase64(playerPair.getPrivate(), challengeBytes);
        assertNotNull(challengeSigB64);
        assertTrue(CosmeticSyncManager.verifySha256RsaBase64(playerPair.getPublic(), challengeBytes, challengeSigB64));

        CosmeticSyncManager.MojangCertificateProof proof = new CosmeticSyncManager.MojangCertificateProof(
                Base64.getEncoder().encodeToString(playerPair.getPublic().getEncoded()),
                expiresAtMs,
                Base64.getEncoder().encodeToString(keySig),
                challengeSigB64
        );
        String json = CosmeticSyncManager.buildSyncUploadJson(
                compactUuid,
                serverId,
                proof,
                new CustomPlayerManager.SyncedCosmeticRecord(uuid, "Sorakkaa", 1, 2, 3, "#FF55FF")
        );
        assertTrue(json.contains("\"cert\":{"));
        assertTrue(json.contains("\"challengeSignature\":"));
        assertTrue(json.contains("\"expiresAtMs\":" + expiresAtMs));
    }
}
