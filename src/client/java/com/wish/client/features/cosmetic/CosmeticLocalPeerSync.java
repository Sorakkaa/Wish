package com.wish.client.features.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wish.client.WishClient;
import com.wish.client.color.CustomPlayerManager;
import com.wish.client.config.ModConfig;
import com.wish.client.util.HttpClientUtils;
import com.wish.client.util.WishPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

final class CosmeticLocalPeerSync {
    private static final int PORT_BASE = 49761;
    private static final int PORT_COUNT = 4;
    private static final int MAX_PACKET_BYTES = 2048;
    private static final int MAX_SHARED_FILE_BYTES = 64 * 1024;
    private static final int MAX_CACHED_PEERS = 64;
    private static final long ENTRY_TTL_MS = 2 * 60 * 60 * 1000L;
    private static final long PEER_SYNC_INTERVAL_MS = 3_500L;
    private static final long CACHE_HEARTBEAT_MS = 60_000L;
    private static final Pattern VALID_MC_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Gson COMPACT_GSON = new GsonBuilder().create();
    private static final Object FILE_LOCK = new Object();

    private static final ExecutorService PEER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Wish-Cosmetic-P2P-IO");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean LISTENER_STARTED = new AtomicBoolean(false);
    private static volatile long lastPeerSweepMs;
    private static volatile long lastSharedFileWriteMs;
    private static volatile String lastBroadcastPayload = "";

    private CosmeticLocalPeerSync() {
    }

    static void ensureStarted() {
        if (!LISTENER_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread recvThread = new Thread(CosmeticLocalPeerSync::runUdpReceiveLoop, "Wish-Cosmetic-P2P-Recv");
        recvThread.setDaemon(true);
        recvThread.start();
    }

    static void onLocalChangedOrJoin() {
        ensureStarted();
        lastPeerSweepMs = 0L;
        publishAndPollAsync();
    }

    static void onTick(Minecraft mc) {
        if (mc == null || mc.level == null) {
            return;
        }
        ensureStarted();
        long now = System.currentTimeMillis();
        if (now - lastPeerSweepMs < PEER_SYNC_INTERVAL_MS) {
            return;
        }
        lastPeerSweepMs = now;
        publishAndPollAsync();
    }

    static void publishAndPollAsync() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc != null ? mc.getUser() : null;
        UUID localUuid = user != null ? user.getProfileId() : null;
        String localName = user != null ? user.getName() : null;
        ModConfig cfg = ModConfig.INSTANCE;
        CustomPlayerManager.SyncedCosmeticRecord localRecord =
                (localUuid != null && localName != null && VALID_MC_NAME_PATTERN.matcher(localName.trim()).matches())
                        ? CosmeticSyncManager.buildLocalSyncRecord(localUuid, localName.trim(), cfg)
                        : null;

        PEER_EXECUTOR.submit(() -> {
            if (localRecord != null) {
                updateSharedCacheFile(localRecord);
                broadcastUdpState(localRecord);
            } else {
                pollSharedCacheFile(null);
            }
        });
    }

    private static void broadcastUdpState(CustomPlayerManager.SyncedCosmeticRecord localRecord) {
        String packetJson = buildPeerPacketJson(localRecord);
        byte[] data = packetJson.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket sender = new DatagramSocket()) {
            sender.setBroadcast(true);
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            InetAddress lanBroadcast = InetAddress.getByName("255.255.255.255");
            for (int offset = 0; offset < PORT_COUNT; offset++) {
                int port = PORT_BASE + offset;
                try {
                    sender.send(new DatagramPacket(data, data.length, loopback, port));
                    sender.send(new DatagramPacket(data, data.length, lanBroadcast, port));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            WishClient.LOGGER.debug("[Wish] P2P cosmetic broadcast skipped: {}", e.getMessage());
        }
    }

    private static void runUdpReceiveLoop() {
        DatagramSocket socket = null;
        for (int offset = 0; offset < PORT_COUNT; offset++) {
            try {
                socket = new DatagramSocket(PORT_BASE + offset);
                socket.setReuseAddress(true);
                break;
            } catch (Exception ignored) {
                socket = null;
            }
        }
        if (socket == null) {
            return;
        }

        byte[] buf = new byte[MAX_PACKET_BYTES];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
            try {
                socket.receive(packet);
                int len = Math.min(packet.getLength(), MAX_PACKET_BYTES);
                String json = new String(packet.getData(), packet.getOffset(), len, StandardCharsets.UTF_8);
                parseAndApplyPeerPacket(json);
            } catch (Exception e) {
                if (socket.isClosed()) {
                    break;
                }
            }
        }
    }

    static String buildPeerPacketJson(UUID uuid, String username, int wing, int halo, int particle) {
        return buildPeerPacketJson(new CustomPlayerManager.SyncedCosmeticRecord(uuid, username, wing, halo, particle, null));
    }

    static String buildPeerPacketJson(CustomPlayerManager.SyncedCosmeticRecord record) {
        JsonObject obj = new JsonObject();
        obj.addProperty("wishP2P", 1);
        obj.addProperty("uuid", record.uuid().toString());
        obj.addProperty("username", record.username());
        CosmeticSyncManager.writeSyncedRecordFields(obj, record, false);
        obj.addProperty("updatedAt", System.currentTimeMillis());
        return obj.toString();
    }

    static boolean parseAndApplyPeerPacket(String json) {
        JsonObject obj = HttpClientUtils.parseJsonObject(json);
        if (obj == null || !obj.has("wishP2P") || !obj.has("uuid") || !obj.has("username")) {
            return false;
        }
        try {
            UUID uuid = UUID.fromString(obj.get("uuid").getAsString().trim());
            String username = obj.get("username").getAsString().trim();
            if (!VALID_MC_NAME_PATTERN.matcher(username).matches()) {
                return false;
            }
            CustomPlayerManager.SyncedCosmeticRecord record =
                    CosmeticSyncManager.readSyncedRecordFields(uuid, username, obj);
            return CustomPlayerManager.applySyncedBatch(List.of(record)) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path resolveSharedCachePath() {
        String rawTmp = System.getProperty("java.io.tmpdir");
        if (rawTmp == null || rawTmp.isBlank()) {
            return null;
        }
        Path tmpDir = Path.of(rawTmp).toAbsolutePath().normalize();
        Path file = tmpDir.resolve("wish-cosmetics-sync-v1.json").normalize();
        return file.startsWith(tmpDir) ? file : null;
    }

    private static void updateSharedCacheFile(CustomPlayerManager.SyncedCosmeticRecord localRecord) {
        Path file = resolveSharedCachePath();
        if (file == null) {
            return;
        }
        synchronized (FILE_LOCK) {
            try {
                Map<UUID, Long> timestamps = new LinkedHashMap<>();
                Map<UUID, CustomPlayerManager.SyncedCosmeticRecord> map = readValidRecordsLocked(file, timestamps);
                long now = System.currentTimeMillis();
                map.put(localRecord.uuid(), localRecord);
                timestamps.put(localRecord.uuid(), now);

                applyRemoteRecords(map, localRecord.uuid());

                JsonObject root = new JsonObject();
                int count = 0;
                for (CustomPlayerManager.SyncedCosmeticRecord rec : map.values()) {
                    if (count >= MAX_CACHED_PEERS) {
                        break;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("username", rec.username());
                    CosmeticSyncManager.writeSyncedRecordFields(entry, rec, false);
                    entry.addProperty("updatedAt", timestamps.getOrDefault(rec.uuid(), now));
                    root.add(rec.uuid().toString(), entry);
                    count++;
                }

                JsonObject payloadWithoutLocalTimestamp = root.deepCopy();
                if (payloadWithoutLocalTimestamp.has(localRecord.uuid().toString())) {
                    payloadWithoutLocalTimestamp.getAsJsonObject(localRecord.uuid().toString()).remove("updatedAt");
                }
                String compareKey = COMPACT_GSON.toJson(payloadWithoutLocalTimestamp);
                if (!compareKey.equals(lastBroadcastPayload) || (now - lastSharedFileWriteMs) >= CACHE_HEARTBEAT_MS || !Files.exists(file)) {
                    lastBroadcastPayload = compareKey;
                    lastSharedFileWriteMs = now;
                    WishPaths.writeStringAtomically(file, COMPACT_GSON.toJson(root));
                }
            } catch (Exception e) {
                WishClient.LOGGER.debug("[Wish] Failed updating shared local cosmetic cache: {}", e.getMessage());
            }
        }
    }

    private static void pollSharedCacheFile(UUID localUuid) {
        Path file = resolveSharedCachePath();
        if (file == null) {
            return;
        }
        synchronized (FILE_LOCK) {
            applyRemoteRecords(readValidRecordsLocked(file, null), localUuid);
        }
    }

    private static void applyRemoteRecords(Map<UUID, CustomPlayerManager.SyncedCosmeticRecord> map, UUID localUuid) {
        if (map.isEmpty()) {
            return;
        }
        List<CustomPlayerManager.SyncedCosmeticRecord> batch = new ArrayList<>(map.size());
        for (CustomPlayerManager.SyncedCosmeticRecord rec : map.values()) {
            if (localUuid == null || !localUuid.equals(rec.uuid())) {
                batch.add(rec);
            }
        }
        if (!batch.isEmpty()) {
            CustomPlayerManager.applySyncedBatch(batch);
        }
    }

    private static Map<UUID, CustomPlayerManager.SyncedCosmeticRecord> readValidRecordsLocked(Path file, Map<UUID, Long> outTimestamps) {
        Map<UUID, CustomPlayerManager.SyncedCosmeticRecord> result = new LinkedHashMap<>();
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_SHARED_FILE_BYTES) {
                return result;
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = HttpClientUtils.parseJsonObject(raw);
            if (obj == null) {
                return result;
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(e.getKey().trim());
                    JsonObject val = e.getValue().getAsJsonObject();
                    long updatedAt = val.has("updatedAt") ? val.get("updatedAt").getAsLong() : now;
                    if (now - updatedAt > ENTRY_TTL_MS) {
                        continue;
                    }
                    String username = val.has("username") ? val.get("username").getAsString().trim() : "";
                    if (!VALID_MC_NAME_PATTERN.matcher(username).matches()) {
                        continue;
                    }
                    result.put(uuid, CosmeticSyncManager.readSyncedRecordFields(uuid, username, val));
                    if (outTimestamps != null) {
                        outTimestamps.put(uuid, updatedAt);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
