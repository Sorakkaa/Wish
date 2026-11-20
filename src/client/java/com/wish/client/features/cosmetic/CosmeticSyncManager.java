package com.wish.client.features.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.wish.client.WishClient;
import com.wish.client.color.ColorUtils;
import com.wish.client.color.CustomPlayerManager;
import com.wish.client.color.NameColorManager;
import com.wish.client.config.ModConfig;
import com.wish.client.features.visual.RealPlayerFilter;
import com.wish.client.util.HttpClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CosmeticSyncManager {
    private static final String MOJANG_JOIN_URL = "https://sessionserver.mojang.com/session/minecraft/join";
    private static final String DEFAULT_SYNC_API_URL = "https://wish-cosmetics-worker.wish-sync-mc.workers.dev";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long UPLOAD_DEBOUNCE_MS = 700L;
    private static final long QUERY_INTERVAL_MS = 15_000L;
    private static final long NEW_PLAYER_QUERY_MIN_INTERVAL_MS = 1_200L;
    private static final long PLAYER_CACHE_TTL_MS = 15_000L;
    private static final int MAX_BATCH_UUIDS = 64;
    private static final long BASE_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ScheduledExecutorService DEBOUNCE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Wish-CosmeticSync-Debounce");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Wish-CosmeticSync-Net");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private static final ConcurrentHashMap<UUID, Long> lastQueriedByUuid = new ConcurrentHashMap<>();
    private static final AtomicBoolean uploadInProgress = new AtomicBoolean(false);
    private static final AtomicBoolean uploadPending = new AtomicBoolean(false);
    private static final AtomicBoolean queryInProgress = new AtomicBoolean(false);
    private static final Object SCHED_LOCK = new Object();

    private static ScheduledFuture<?> pendingDebounceTask;
    private static volatile String sessionToken;
    private static volatile long sessionTokenExpiresAtMs;
    private static volatile UUID sessionTokenUuid;
    private static volatile long lastQuerySweepMs;
    private static volatile long lastImmediateQueryMs;
    private static volatile ClientLevel lastObservedLevel;
    private static volatile boolean uploadRetryNeeded = false;
    private static volatile long uploadBackoffUntilMs;
    private static volatile long currentUploadBackoffMs = BASE_BACKOFF_MS;
    private static volatile long queryBackoffUntilMs;
    private static volatile long currentQueryBackoffMs = BASE_BACKOFF_MS;
    private static volatile String uploadStatus = "Sync ready";
    private static volatile boolean uploadSucceeded = false;
    private static volatile int lastQueryAppliedCount = -1;

    private static final long MOJANG_PROPAGATION_DELAY_MS = 250L;

    record PlayerQueryCandidate(UUID uuid, String username) {
    }

    record MojangCertificateProof(
            String publicKeyB64,
            long expiresAtMs,
            String keySignatureB64,
            String challengeSignatureB64
    ) {
    }

    private record SyncUploadResult(boolean ok, String errorDetail) {
        static final SyncUploadResult SUCCESS = new SyncUploadResult(true, null);

        static SyncUploadResult failure(String detail) {
            return new SyncUploadResult(false, detail);
        }
    }

    private CosmeticSyncManager() {
    }

    public static String getStatusText() {
        if (resolveEndpointUrl() == null) {
            return "Local/LAN P2P Active";
        }
        if (!ModConfig.INSTANCE.enableCosmeticAutoSync && !uploadSucceeded) {
            return "Auto-sync disabled";
        }
        if (!uploadSucceeded) {
            return uploadStatus;
        }
        if (lastQueryAppliedCount > 0) {
            return "Synced (" + lastQueryAppliedCount + " players)";
        }
        return uploadStatus;
    }

    public static void init() {
        CosmeticLocalPeerSync.ensureStarted();
    }

    private static String resolveEndpointUrl() {
        String configured = validateSyncEndpointUrl(ModConfig.INSTANCE.cosmeticSyncApiUrl);
        return configured != null ? configured : validateSyncEndpointUrl(DEFAULT_SYNC_API_URL);
    }

    public static void onWorldJoin() {
        lastQueriedByUuid.clear();
        lastQuerySweepMs = 0L;
        lastImmediateQueryMs = 0L;
        lastObservedLevel = null;
        lastQueryAppliedCount = -1;
        CosmeticLocalPeerSync.onLocalChangedOrJoin();
        if (resolveEndpointUrl() == null) {
            uploadStatus = "Local/LAN P2P Active";
            return;
        }
        if (!ModConfig.INSTANCE.enableCosmeticAutoSync) {
            uploadStatus = "Auto-sync disabled";
            return;
        }
        triggerManualSync();
    }

    public static void onDisconnect() {
        lastQueriedByUuid.clear();
        lastQuerySweepMs = 0L;
        lastImmediateQueryMs = 0L;
        lastObservedLevel = null;
        lastQueryAppliedCount = -1;
    }

    public static void notifyLocalCosmeticsChanged() {
        CosmeticLocalPeerSync.onLocalChangedOrJoin();
        if (resolveEndpointUrl() == null) {
            uploadStatus = "Local/LAN P2P Active";
            return;
        }
        if (!ModConfig.INSTANCE.enableCosmeticAutoSync) {
            return;
        }
        uploadRetryNeeded = true;
        synchronized (SCHED_LOCK) {
            if (pendingDebounceTask != null && !pendingDebounceTask.isDone()) {
                pendingDebounceTask.cancel(false);
            }
            pendingDebounceTask = DEBOUNCE_SCHEDULER.schedule(
                    CosmeticSyncManager::uploadLocalAsync,
                    UPLOAD_DEBOUNCE_MS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public static void triggerManualSync() {
        CosmeticLocalPeerSync.onLocalChangedOrJoin();
        if (resolveEndpointUrl() == null) {
            uploadStatus = "Local/LAN P2P Active";
            return;
        }
        uploadBackoffUntilMs = 0L;
        currentUploadBackoffMs = BASE_BACKOFF_MS;
        queryBackoffUntilMs = 0L;
        currentQueryBackoffMs = BASE_BACKOFF_MS;
        uploadRetryNeeded = true;
        lastQueriedByUuid.clear();
        uploadLocalAsync();
        queryOnlinePlayersAsync(Minecraft.getInstance(), true);
    }

    public static void onClientTick(Minecraft mc) {
        if (mc == null || mc.level == null) {
            lastObservedLevel = null;
            return;
        }
        CosmeticLocalPeerSync.onTick(mc);
        if (resolveEndpointUrl() == null || !ModConfig.INSTANCE.enableCosmeticAutoSync) {
            return;
        }
        long now = System.currentTimeMillis();
        if (uploadRetryNeeded && now >= uploadBackoffUntilMs && !uploadInProgress.get()) {
            uploadLocalAsync();
        }

        boolean levelSwitched = mc.level != lastObservedLevel;
        if (levelSwitched) {
            lastObservedLevel = mc.level;
            lastQuerySweepMs = 0L;
        }

        boolean hasUnqueriedNearbyPlayer = false;
        if (!levelSwitched && now - lastImmediateQueryMs >= NEW_PLAYER_QUERY_MIN_INTERVAL_MS) {
            for (AbstractClientPlayer worldPlayer : mc.level.players()) {
                UUID id = worldPlayer.getUUID();
                if (id != null && isQueryEligibleUuid(id) && !lastQueriedByUuid.containsKey(id)) {
                    hasUnqueriedNearbyPlayer = true;
                    break;
                }
            }
        }

        if (now < queryBackoffUntilMs) {
            return;
        }
        if (levelSwitched || hasUnqueriedNearbyPlayer || now - lastQuerySweepMs >= QUERY_INTERVAL_MS) {
            lastQuerySweepMs = now;
            lastImmediateQueryMs = now;
            queryOnlinePlayersAsync(mc, levelSwitched);
        }
    }

    private static void uploadLocalAsync() {
        ModConfig cfg = ModConfig.INSTANCE;
        String endpoint = resolveEndpointUrl();
        if (endpoint == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < uploadBackoffUntilMs) {
            uploadRetryNeeded = true;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        User user = mc != null ? mc.getUser() : null;
        if (user == null || user.getProfileId() == null || user.getName() == null) {
            uploadRetryNeeded = true;
            return;
        }
        if (!uploadInProgress.compareAndSet(false, true)) {
            uploadPending.set(true);
            return;
        }

        UUID uuid = user.getProfileId();
        String compactUuid = uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
        String username = user.getName();
        String accessToken = user.getAccessToken();
        CustomPlayerManager.SyncedCosmeticRecord localRecord = buildLocalSyncRecord(uuid, username, cfg);

        NETWORK_EXECUTOR.submit(() -> {
            boolean shouldScheduleFollowUp = false;
            try {
                uploadStatus = "Syncing with Mojang...";
                String cachedBearer = getValidSessionToken(uuid);
                if (cachedBearer != null) {
                    SyncUploadResult tokenResult = postSyncPayload(endpoint, cachedBearer, compactUuid, null, null, localRecord);
                    if (tokenResult.ok()) {
                        clearUploadBackoff();
                        uploadRetryNeeded = false;
                        uploadSucceeded = true;
                        uploadStatus = "Synced (session active)";
                        return;
                    }
                    invalidateSessionToken();
                }

                if (accessToken == null || accessToken.isBlank() || accessToken.equals("0")) {
                    uploadRetryNeeded = false;
                    uploadSucceeded = false;
                    uploadStatus = "Offline session (Mojang auth required)";
                    return;
                }

                String clientNonce = generateRandomNonceHex();
                String serverId = requestChallengeServerId(endpoint, compactUuid, clientNonce);
                if (serverId == null) {
                    uploadRetryNeeded = true;
                    uploadSucceeded = false;
                    recordUploadBackoff("Sync server unreachable");
                    return;
                }

                MojangCertificateProof certProof = buildCertificateProof(mc, compactUuid, serverId);
                if (certProof == null) {
                    int mojangStatus = joinMojangSession(accessToken, compactUuid, serverId);
                    if (mojangStatus != 204 && mojangStatus != 200) {
                        uploadRetryNeeded = true;
                        uploadSucceeded = false;
                        String msg = mojangStatus > 0
                                ? "Mojang session failed (HTTP " + mojangStatus + ")"
                                : "Mojang session join failed";
                        WishClient.LOGGER.warn("[Wish] Cosmetic sync {}", msg);
                        recordUploadBackoff(msg);
                        return;
                    }
                    Thread.sleep(MOJANG_PROPAGATION_DELAY_MS);
                }

                SyncUploadResult syncResult = postSyncPayload(endpoint, null, compactUuid, serverId, certProof, localRecord);
                if (syncResult.ok()) {
                    clearUploadBackoff();
                    uploadRetryNeeded = false;
                    uploadSucceeded = true;
                    uploadStatus = certProof != null ? "Synced (Mojang cert)" : "Synced (Mojang verified)";
                } else {
                    uploadRetryNeeded = true;
                    uploadSucceeded = false;
                    String reason = (syncResult.errorDetail() != null && !syncResult.errorDetail().isBlank())
                            ? syncResult.errorDetail()
                            : "Sync verification rejected";
                    recordUploadBackoff(reason);
                }
            } catch (Exception e) {
                uploadRetryNeeded = true;
                uploadSucceeded = false;
                WishClient.LOGGER.warn("[Wish] Cosmetic upload failed: {}", e.getMessage());
                recordUploadBackoff("Sync error");
            } finally {
                uploadInProgress.set(false);
                if (uploadPending.getAndSet(false)) {
                    shouldScheduleFollowUp = true;
                }
            }
            if (shouldScheduleFollowUp) {
                uploadLocalAsync();
            }
        });
    }

    private static void queryOnlinePlayersAsync(Minecraft mc, boolean force) {
        if (mc == null || mc.level == null) {
            return;
        }
        String endpoint = resolveEndpointUrl();
        if (endpoint == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<PlayerQueryCandidate> worldCandidates = new ArrayList<>();
        for (AbstractClientPlayer worldPlayer : mc.level.players()) {
            UUID id = worldPlayer.getUUID();
            if (id != null) {
                worldCandidates.add(new PlayerQueryCandidate(id, worldPlayer.getScoreboardName()));
            }
        }
        List<PlayerQueryCandidate> tabCandidates = new ArrayList<>();
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                GameProfile profile = info.getProfile();
                if (profile != null && profile.id() != null) {
                    tabCandidates.add(new PlayerQueryCandidate(profile.id(), profile.name()));
                }
            }
        }
        List<UUID> batch = collectCandidateQueryUuids(worldCandidates, tabCandidates, now, force);
        if (batch.isEmpty() || !queryInProgress.compareAndSet(false, true)) {
            return;
        }

        for (UUID id : batch) {
            lastQueriedByUuid.put(id, now);
        }

        NETWORK_EXECUTOR.submit(() -> {
            try {
                String payload = buildBatchQueryJson(batch);
                HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + "/v1/cosmetics/query"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("User-Agent", "Wish-Client/" + WishClient.MOD_VERSION)
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() != 200) {
                    WishClient.LOGGER.warn("[Wish] Cosmetic query failed with HTTP {}", resp.statusCode());
                    recordQueryBackoff("Query HTTP " + resp.statusCode());
                    return;
                }
                int applied = parseAndApplyBatchResponse(resp.body());
                clearQueryBackoff();
                lastQueryAppliedCount = applied;
            } catch (Exception e) {
                WishClient.LOGGER.debug("[Wish] Cosmetic query failed: {}", e.getMessage());
                recordQueryBackoff("Query error");
            } finally {
                queryInProgress.set(false);
            }
        });
    }

    static List<UUID> collectCandidateQueryUuids(
            Collection<PlayerQueryCandidate> worldPlayers,
            Collection<PlayerQueryCandidate> tabPlayers,
            long now,
            boolean force
    ) {
        Set<UUID> ordered = new LinkedHashSet<>();
        if (worldPlayers != null) {
            for (PlayerQueryCandidate candidate : worldPlayers) {
                if (candidate == null || candidate.uuid() == null) {
                    continue;
                }
                if (isQueryEligibleUuid(candidate.uuid()) && shouldQueryUuid(candidate.uuid(), now, force)) {
                    ordered.add(candidate.uuid());
                    if (ordered.size() >= MAX_BATCH_UUIDS) {
                        return new ArrayList<>(ordered);
                    }
                }
            }
        }
        if (tabPlayers != null) {
            for (PlayerQueryCandidate candidate : tabPlayers) {
                if (candidate == null || candidate.uuid() == null) {
                    continue;
                }
                if (RealPlayerFilter.isValidRealPlayerIdentity(candidate.uuid(), candidate.username(), null, true)
                        && shouldQueryUuid(candidate.uuid(), now, force)) {
                    ordered.add(candidate.uuid());
                    if (ordered.size() >= MAX_BATCH_UUIDS) {
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    private static boolean isQueryEligibleUuid(UUID uuid) {
        int ver = uuid.version();
        return ver == 4 || ver == 3;
    }

    private static boolean shouldQueryUuid(UUID uuid, long now, boolean force) {
        if (force) {
            return true;
        }
        Long last = lastQueriedByUuid.get(uuid);
        return last == null || (now - last) >= PLAYER_CACHE_TTL_MS;
    }

    private static String requestChallengeServerId(String endpoint, String compactUuid, String clientNonce) {
        try {
            String body = buildChallengeJson(compactUuid, clientNonce);
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + "/v1/cosmetics/challenge"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "Wish-Client/" + WishClient.MOD_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                WishClient.LOGGER.warn("[Wish] Cosmetic challenge failed with HTTP {}", resp.statusCode());
                return null;
            }
            JsonObject obj = HttpClientUtils.parseJsonObject(resp.body());
            if (obj == null || !obj.has("serverId") || obj.get("serverId").isJsonNull()) {
                return null;
            }
            String serverId = obj.get("serverId").getAsString().trim();
            return serverId.isEmpty() ? null : serverId;
        } catch (Exception e) {
            WishClient.LOGGER.warn("[Wish] Cosmetic challenge error: {}", e.getMessage());
            return null;
        }
    }

    private static int joinMojangSession(String accessToken, String compactUuid, String serverId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", accessToken);
            body.addProperty("selectedProfile", compactUuid);
            body.addProperty("serverId", serverId);
            HttpRequest req = HttpRequest.newBuilder(URI.create(MOJANG_JOIN_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode();
        } catch (Exception e) {
            WishClient.LOGGER.warn("[Wish] Mojang session join error: {}", e.getMessage());
            return -1;
        }
    }

    private static MojangCertificateProof buildCertificateProof(Minecraft mc, String compactUuid, String serverId) {
        if (mc == null) {
            return null;
        }
        try {
            ProfileKeyPairManager keyManager = mc.getProfileKeyPairManager();
            if (keyManager == null || keyManager == ProfileKeyPairManager.EMPTY_KEY_MANAGER) {
                return null;
            }
            ProfileKeyPair keyPair = keyManager.prepareKeyPair().get(5, TimeUnit.SECONDS).orElse(null);
            if (keyPair == null || keyPair.privateKey() == null || keyPair.publicKey() == null) {
                return null;
            }
            ProfilePublicKey.Data data = keyPair.publicKey().data();
            if (data == null || data.key() == null || data.keySignature() == null || data.hasExpired()) {
                return null;
            }
            byte[] encodedKey = data.key().getEncoded();
            if (encodedKey == null || encodedKey.length == 0) {
                return null;
            }
            String challengePayload = buildCertChallengeString(compactUuid, serverId);
            String challengeSigB64 = signSha256RsaBase64(keyPair.privateKey(), challengePayload.getBytes(StandardCharsets.UTF_8));
            if (challengeSigB64 == null) {
                return null;
            }
            return new MojangCertificateProof(
                    Base64.getEncoder().encodeToString(encodedKey),
                    data.expiresAt().toEpochMilli(),
                    Base64.getEncoder().encodeToString(data.keySignature()),
                    challengeSigB64
            );
        } catch (Exception e) {
            WishClient.LOGGER.debug("[Wish] Profile key pair unavailable, falling back to session join: {}", e.getMessage());
            return null;
        }
    }

    static byte[] buildProfilePublicKeySignedPayload(UUID uuid, long expiresAtMs, byte[] spkiPublicKey) {
        byte[] buf = new byte[24 + spkiPublicKey.length];
        ByteBuffer.wrap(buf)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .putLong(expiresAtMs)
                .put(spkiPublicKey);
        return buf;
    }

    static String buildCertChallengeString(String compactUuid, String serverId) {
        return "wish-cosmetic-cert:" + compactUuid + ":" + serverId;
    }

    static String signSha256RsaBase64(PrivateKey privateKey, byte[] payload) {
        if (privateKey == null || payload == null) {
            return null;
        }
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(payload);
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            WishClient.LOGGER.warn("[Wish] Failed signing cosmetic challenge: {}", e.getMessage());
            return null;
        }
    }

    static boolean verifySha256RsaBase64(PublicKey publicKey, byte[] payload, String sigBase64) {
        if (publicKey == null || payload == null || sigBase64 == null) {
            return false;
        }
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(payload);
            return sig.verify(Base64.getDecoder().decode(sigBase64));
        } catch (Exception e) {
            return false;
        }
    }

    private static SyncUploadResult postSyncPayload(
            String endpoint,
            String bearerToken,
            String compactUuid,
            String serverId,
            MojangCertificateProof certProof,
            CustomPlayerManager.SyncedCosmeticRecord localRecord
    ) {
        try {
            String json = buildSyncUploadJson(compactUuid, serverId, certProof, localRecord);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + "/v1/cosmetics/sync"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "Wish-Client/" + WishClient.MOD_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (bearerToken != null && !bearerToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            HttpResponse<String> resp = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                String serverErr = extractServerError(resp.body());
                String detail = serverErr != null
                        ? "HTTP " + resp.statusCode() + ": " + serverErr
                        : "HTTP " + resp.statusCode();
                WishClient.LOGGER.warn("[Wish] Cosmetic sync upload failed ({})", detail);
                return SyncUploadResult.failure(detail);
            }
            JsonObject obj = HttpClientUtils.parseJsonObject(resp.body());
            if (obj != null && obj.has("token") && obj.get("token").isJsonPrimitive()) {
                String tok = obj.get("token").getAsString().trim();
                long ttlSec = obj.has("expiresInSec") && obj.get("expiresInSec").isJsonPrimitive()
                        ? obj.get("expiresInSec").getAsLong()
                        : 3600L;
                if (!tok.isEmpty()) {
                    sessionToken = tok;
                    sessionTokenUuid = parseFlexibleUuid(compactUuid);
                    sessionTokenExpiresAtMs = System.currentTimeMillis() + Math.max(60L, ttlSec - 30L) * 1000L;
                }
            }
            return SyncUploadResult.SUCCESS;
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            WishClient.LOGGER.warn("[Wish] Cosmetic sync upload exception: {}", msg);
            return SyncUploadResult.failure("Upload error (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String extractServerError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonObject obj = HttpClientUtils.parseJsonObject(body);
        if (obj != null && obj.has("error") && obj.get("error").isJsonPrimitive()) {
            String err = obj.get("error").getAsString().trim();
            return err.isEmpty() ? null : err;
        }
        return null;
    }

    private static String getValidSessionToken(UUID uuid) {
        String tok = sessionToken;
        if (tok == null || sessionTokenUuid == null || !sessionTokenUuid.equals(uuid)) {
            return null;
        }
        return System.currentTimeMillis() < sessionTokenExpiresAtMs ? tok : null;
    }

    private static void invalidateSessionToken() {
        sessionToken = null;
        sessionTokenUuid = null;
        sessionTokenExpiresAtMs = 0L;
    }

    private static void recordUploadBackoff(String reason) {
        uploadStatus = reason;
        uploadBackoffUntilMs = System.currentTimeMillis() + currentUploadBackoffMs;
        currentUploadBackoffMs = Math.min(MAX_BACKOFF_MS, currentUploadBackoffMs * 2L);
    }

    private static void clearUploadBackoff() {
        uploadBackoffUntilMs = 0L;
        currentUploadBackoffMs = BASE_BACKOFF_MS;
    }

    private static void recordQueryBackoff(String reason) {
        if (!uploadSucceeded) {
            return;
        }
        queryBackoffUntilMs = System.currentTimeMillis() + currentQueryBackoffMs;
        currentQueryBackoffMs = Math.min(MAX_BACKOFF_MS, currentQueryBackoffMs * 2L);
    }

    private static void clearQueryBackoff() {
        queryBackoffUntilMs = 0L;
        currentQueryBackoffMs = BASE_BACKOFF_MS;
    }

    private static String generateRandomNonceHex() {
        byte[] buf = new byte[16];
        SECURE_RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : buf) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static String computeMojangServerIdHash(String serverIdSeed, byte[] sharedSecret, byte[] publicKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            if (serverIdSeed != null) {
                sha1.update(serverIdSeed.getBytes(StandardCharsets.ISO_8859_1));
            }
            if (sharedSecret != null) {
                sha1.update(sharedSecret);
            }
            if (publicKey != null) {
                sha1.update(publicKey);
            }
            return new BigInteger(sha1.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    public static String validateSyncEndpointUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String trimmed = rawUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return null;
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            String lowerHost = host.toLowerCase(Locale.ROOT);
            boolean isLoopback = "127.0.0.1".equals(lowerHost) || "localhost".equals(lowerHost);
            if ("https".equals(lowerScheme) || ("http".equals(lowerScheme) && isLoopback)) {
                String clean = uri.toString();
                while (clean.endsWith("/")) {
                    clean = clean.substring(0, clean.length() - 1);
                }
                return clean;
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String buildChallengeJson(String compactUuid, String clientNonce) {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", compactUuid);
        obj.addProperty("clientNonce", clientNonce);
        return obj.toString();
    }

    public static CustomPlayerManager.SyncedCosmeticRecord buildLocalSyncRecord(UUID uuid, String username, ModConfig cfg) {
        int wing = PlayerCosmeticsManager.clampId(cfg.selectedWing);
        int halo = PlayerCosmeticsManager.clampId(cfg.selectedHalo);
        int particle = PlayerCosmeticsManager.clampId(cfg.selectedParticle);
        String cosmeticColor = cfg.cosmeticCustomColorEnabled
                ? ColorUtils.formatHexRgb(ColorUtils.parseHexRgb(cfg.cosmeticCustomColor, PlayerCosmeticsManager.DEFAULT_ACCENT_RGB))
                : null;
        boolean isRepo = CustomPlayerManager.isRepositoryPlayer(uuid);

        String color;
        Double animSpeed;
        if (!cfg.enableNameColor) {
            color = "";
            animSpeed = null;
        } else if (NameColorManager.hasLocalCustomColorConfigured(cfg)) {
            color = CustomPlayerManager.sanitizeSyncedColorCode(NameColorManager.getCurrentColorCode());
            animSpeed = Double.valueOf(cfg.pseudoAnimationSpeed);
        } else {
            color = isRepo ? null : "";
            animSpeed = isRepo ? null : Double.valueOf(cfg.pseudoAnimationSpeed);
        }

        Integer font = (cfg.pseudoFont != 0) ? Integer.valueOf(cfg.pseudoFont) : (isRepo ? null : Integer.valueOf(0));
        String spoofedName = null;

        boolean hasCustomSize = Math.abs(cfg.playerSizeX - 1.0f) > 1e-4f
                || Math.abs(cfg.playerSizeY - 1.0f) > 1e-4f
                || Math.abs(cfg.playerSizeZ - 1.0f) > 1e-4f;
        Float scaleX;
        Float scaleY;
        Float scaleZ;
        if (!cfg.playerSizeEnabled) {
            scaleX = 1.0f;
            scaleY = 1.0f;
            scaleZ = 1.0f;
        } else if (hasCustomSize || !isRepo) {
            scaleX = cfg.playerSizeX;
            scaleY = cfg.playerSizeY;
            scaleZ = cfg.playerSizeZ;
        } else {
            scaleX = null;
            scaleY = null;
            scaleZ = null;
        }

        Float spinSpeedY = cfg.enablePlayerSpin
                ? Float.valueOf(cfg.playerSpinSpeedY)
                : (isRepo ? null : Float.valueOf(0.0f));

        return new CustomPlayerManager.SyncedCosmeticRecord(
                uuid, username, wing, halo, particle, cosmeticColor,
                color, font, animSpeed, spoofedName, scaleX, scaleY, scaleZ, spinSpeedY
        );
    }

    static void writeSyncedRecordFields(JsonObject obj, CustomPlayerManager.SyncedCosmeticRecord rec, boolean includeNullCosmeticColor) {
        obj.addProperty("wing", PlayerCosmeticsManager.clampId(rec.wingId()));
        obj.addProperty("halo", PlayerCosmeticsManager.clampId(rec.haloId()));
        obj.addProperty("particle", PlayerCosmeticsManager.clampId(rec.particleId()));
        int parsed = ColorUtils.parseHexRgb(rec.cosmeticColor(), -1);
        if (parsed >= 0) {
            obj.addProperty("cosmeticColor", ColorUtils.formatHexRgb(parsed));
        } else if (includeNullCosmeticColor) {
            obj.add("cosmeticColor", com.google.gson.JsonNull.INSTANCE);
        }
        if (rec.color() != null) {
            obj.addProperty("color", rec.color());
        }
        if (rec.font() != null) {
            obj.addProperty("font", rec.font());
        }
        if (rec.animSpeed() != null && Double.isFinite(rec.animSpeed())) {
            obj.addProperty("animSpeed", rec.animSpeed());
        }
        if (rec.scaleX() != null && Float.isFinite(rec.scaleX())) {
            obj.addProperty("scaleX", rec.scaleX());
        }
        if (rec.scaleY() != null && Float.isFinite(rec.scaleY())) {
            obj.addProperty("scaleY", rec.scaleY());
        }
        if (rec.scaleZ() != null && Float.isFinite(rec.scaleZ())) {
            obj.addProperty("scaleZ", rec.scaleZ());
        }
        if (rec.spinSpeedY() != null && Float.isFinite(rec.spinSpeedY())) {
            obj.addProperty("spinSpeedY", rec.spinSpeedY());
        }
    }

    static CustomPlayerManager.SyncedCosmeticRecord readSyncedRecordFields(UUID uuid, String username, JsonObject pObj) {
        int wing = readOptionalInt(pObj, "wing", 0);
        int halo = readOptionalInt(pObj, "halo", 0);
        int particle = readOptionalInt(pObj, "particle", 0);
        String cosmeticColor = readOptionalString(pObj, "cosmeticColor");
        String color = readOptionalString(pObj, "color");
        Integer font = readOptionalBoxedInt(pObj, "font");
        Double animSpeed = readOptionalBoxedDouble(pObj, "animSpeed");
        String spoofedName = null;
        Float scaleX = readOptionalBoxedFloat(pObj, "scaleX");
        Float scaleY = readOptionalBoxedFloat(pObj, "scaleY");
        Float scaleZ = readOptionalBoxedFloat(pObj, "scaleZ");
        Float spinSpeedY = readOptionalBoxedFloat(pObj, "spinSpeedY");
        return new CustomPlayerManager.SyncedCosmeticRecord(
                uuid, username, wing, halo, particle, cosmeticColor,
                color, font, animSpeed, spoofedName, scaleX, scaleY, scaleZ, spinSpeedY
        );
    }

    private static String readOptionalString(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int readOptionalInt(JsonObject obj, String key, int def) {
        Integer boxed = readOptionalBoxedInt(obj, key);
        return boxed != null ? boxed : def;
    }

    private static Integer readOptionalBoxedInt(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Double readOptionalBoxedDouble(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                double v = obj.get(key).getAsDouble();
                return Double.isFinite(v) ? v : null;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Float readOptionalBoxedFloat(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                float v = obj.get(key).getAsFloat();
                return Float.isFinite(v) ? v : null;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String buildSyncUploadJson(
            String uuid,
            String serverId,
            CustomPlayerManager.SyncedCosmeticRecord record
    ) {
        return buildSyncUploadJson(uuid, serverId, null, record);
    }

    static String buildSyncUploadJson(
            String uuid,
            String serverId,
            MojangCertificateProof certProof,
            CustomPlayerManager.SyncedCosmeticRecord record
    ) {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", uuid != null ? uuid : "");
        obj.addProperty("username", record.username() != null ? record.username() : "");
        if (serverId != null && !serverId.isBlank()) {
            obj.addProperty("serverId", serverId);
        }
        if (certProof != null) {
            JsonObject certObj = new JsonObject();
            certObj.addProperty("publicKey", certProof.publicKeyB64());
            certObj.addProperty("expiresAtMs", certProof.expiresAtMs());
            certObj.addProperty("keySignature", certProof.keySignatureB64());
            obj.add("cert", certObj);
            obj.addProperty("challengeSignature", certProof.challengeSignatureB64());
        }
        writeSyncedRecordFields(obj, record, true);
        return obj.toString();
    }

    public static String buildSyncUploadJson(
            String uuid,
            String username,
            String serverId,
            int wingId,
            int haloId,
            int particleId,
            String colorHex
    ) {
        return buildSyncUploadJson(uuid, serverId,
                new CustomPlayerManager.SyncedCosmeticRecord(null, username, wingId, haloId, particleId, colorHex));
    }

    public static String buildBatchQueryJson(Collection<UUID> uuids) {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();
        if (uuids != null) {
            int count = 0;
            for (UUID id : uuids) {
                if (id != null) {
                    arr.add(id.toString());
                    if (++count >= MAX_BATCH_UUIDS) {
                        break;
                    }
                }
            }
        }
        obj.add("uuids", arr);
        return obj.toString();
    }

    public static int parseAndApplyBatchResponse(String jsonBody) {
        JsonObject root = HttpClientUtils.parseJsonObject(jsonBody);
        if (root == null || !root.has("players") || !root.get("players").isJsonObject()) {
            return 0;
        }
        JsonObject playersObj = root.getAsJsonObject("players");
        List<CustomPlayerManager.SyncedCosmeticRecord> records = new ArrayList<>(playersObj.size());
        for (Map.Entry<String, JsonElement> entry : playersObj.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            UUID uuid = parseFlexibleUuid(entry.getKey());
            if (uuid == null) {
                continue;
            }
            JsonObject pObj = entry.getValue().getAsJsonObject();
            String username = readOptionalString(pObj, "username");
            records.add(readSyncedRecordFields(uuid, username, pObj));
        }
        return CustomPlayerManager.applySyncedBatch(records);
    }

    private static UUID parseFlexibleUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() == 32 && trimmed.indexOf('-') < 0) {
            trimmed = trimmed.substring(0, 8) + "-" + trimmed.substring(8, 12) + "-"
                    + trimmed.substring(12, 16) + "-" + trimmed.substring(16, 20) + "-" + trimmed.substring(20);
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
