const CHALLENGE_TTL_SECONDS = 60;
const SESSION_TTL_SECONDS = 24 * 60 * 60;
const RECORD_TTL_SECONDS = 14 * 24 * 60 * 60;
const MAX_BATCH_UUIDS = 64;
const MAX_BODY_BYTES = 16384;

const RATE_WINDOW_MS = 60_000;
const RATE_LIMIT_MAX_BUCKETS = 4096;
const RATE_LIMITS = {
  challenge: 15,
  sync: 30,
  query: 60,
};

const UUID_RE = /^[0-9a-f]{32}$/;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const HEX_COLOR_RE = /^#[0-9A-Fa-f]{6}$/;
const ANIM_COLOR_RE = /^(GRADIENT|WAVE|BREATHE|BLINK):#[0-9A-Fa-f]{6}:#[0-9A-Fa-f]{6}$/i;

const rateBuckets = new Map();

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return corsJson(204, null);
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "");

    try {
      if (request.method === "GET" && path === "/health") {
        return corsJson(200, { ok: true, service: "wish-cosmetics-worker", storage: "d1" });
      }

      if (!env || !env.COSMETICS_DB) {
        return corsJson(503, { ok: false, error: "Database binding COSMETICS_DB not configured" });
      }

      if (request.method === "POST" && path === "/v1/cosmetics/challenge") {
        const limited = enforceRateLimit(request, "challenge");
        if (limited) return limited;
        return await handleChallenge(request, env);
      }
      if (request.method === "POST" && path === "/v1/cosmetics/sync") {
        const limited = enforceRateLimit(request, "sync");
        if (limited) return limited;
        return await handleSync(request, env);
      }
      if (request.method === "POST" && path === "/v1/cosmetics/query") {
        const limited = enforceRateLimit(request, "query");
        if (limited) return limited;
        return await handleQuery(request, env);
      }
      return corsJson(404, { ok: false, error: "Not found" });
    } catch (_err) {
      return corsJson(500, { ok: false, error: "Internal error" });
    }
  },

  async scheduled(_event, env) {
    if (!env || !env.COSMETICS_DB) return;
    const now = Date.now();
    await env.COSMETICS_DB
      .prepare("DELETE FROM challenges WHERE expires_at <= ?")
      .bind(now)
      .run();
    await env.COSMETICS_DB
      .prepare("DELETE FROM players WHERE expires_at <= ?")
      .bind(now)
      .run();
  },
};

function enforceRateLimit(request, routeKey) {
  const maxReqs = RATE_LIMITS[routeKey];
  if (!maxReqs) return null;

  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const bucketKey = `${routeKey}:${ip}`;
  const now = Date.now();

  let entry = rateBuckets.get(bucketKey);
  if (!entry || now >= entry.resetAt) {
    if (rateBuckets.size >= RATE_LIMIT_MAX_BUCKETS) {
      pruneRateBuckets(now);
    }
    entry = { count: 1, resetAt: now + RATE_WINDOW_MS };
    rateBuckets.set(bucketKey, entry);
    return null;
  }

  entry.count += 1;
  if (entry.count > maxReqs) {
    const retryAfterSec = Math.max(1, Math.ceil((entry.resetAt - now) / 1000));
    return corsJson(
      429,
      { ok: false, error: "Too many requests", retryAfterSec },
      { "Retry-After": String(retryAfterSec) }
    );
  }
  return null;
}

function pruneRateBuckets(now) {
  for (const [k, v] of rateBuckets.entries()) {
    if (now >= v.resetAt) {
      rateBuckets.delete(k);
    }
  }
  while (rateBuckets.size >= RATE_LIMIT_MAX_BUCKETS) {
    const oldestKey = rateBuckets.keys().next().value;
    if (oldestKey === undefined) break;
    rateBuckets.delete(oldestKey);
  }
}

async function handleChallenge(request, env) {
  const body = await readJsonSafe(request);
  if (!body) return corsJson(400, { ok: false, error: "Invalid JSON" });

  const uuid = normalizeCompactUuid(body.uuid);
  const clientNonce = typeof body.clientNonce === "string" ? body.clientNonce.trim() : "";
  if (!uuid || clientNonce.length < 8 || clientNonce.length > 64) {
    return corsJson(400, { ok: false, error: "Invalid challenge parameters" });
  }

  const randomBytes = crypto.getRandomValues(new Uint8Array(16));
  const serverNonce = toHex(randomBytes);
  const digestInput = new TextEncoder().encode(`wish-cosmetic:${uuid}:${serverNonce}:${clientNonce}`);
  const sha1Buf = await crypto.subtle.digest("SHA-1", digestInput);
  const serverId = mojangSignedHex(new Uint8Array(sha1Buf));

  const expiresAt = Date.now() + CHALLENGE_TTL_SECONDS * 1000;
  await env.COSMETICS_DB
    .prepare(
      `INSERT INTO challenges (uuid, server_id, expires_at)
       VALUES (?, ?, ?)
       ON CONFLICT(uuid) DO UPDATE SET
         server_id = excluded.server_id,
         expires_at = excluded.expires_at`
    )
    .bind(uuid, serverId, expiresAt)
    .run();

  return corsJson(200, { ok: true, serverId, expiresInSec: CHALLENGE_TTL_SECONDS });
}

async function handleSync(request, env) {
  const body = await readJsonSafe(request);
  if (!body) return corsJson(400, { ok: false, error: "Invalid JSON" });

  const uuid = normalizeCompactUuid(body.uuid);
  const username = typeof body.username === "string" ? body.username.trim() : "";
  if (!uuid || !NAME_RE.test(username)) {
    return corsJson(400, { ok: false, error: "Invalid uuid or username" });
  }

  let verifiedBy = "";
  let issuedToken = null;

  const authHeader = request.headers.get("Authorization") || "";
  if (authHeader.startsWith("Bearer ") && env.HMAC_SECRET) {
    const token = authHeader.slice(7).trim();
    const tokenUuid = await verifyHmacToken(token, env.HMAC_SECRET);
    if (tokenUuid === uuid) {
      verifiedBy = "token";
    }
  }

  if (!verifiedBy) {
    const serverId = typeof body.serverId === "string" ? body.serverId.trim() : "";
    if (!serverId || serverId.length > 64) {
      return corsJson(401, { ok: false, error: "Missing serverId or valid token" });
    }

    const now = Date.now();
    const consumed = await env.COSMETICS_DB
      .prepare("DELETE FROM challenges WHERE uuid = ? AND server_id = ? AND expires_at > ?")
      .bind(uuid, serverId, now)
      .run();

    if (!consumed || !consumed.meta || consumed.meta.changes !== 1) {
      return corsJson(401, { ok: false, error: "Challenge expired or invalid" });
    }

    const mojangOk = await verifyMojangHasJoined(uuid, username, serverId);
    if (!mojangOk) {
      return corsJson(403, { ok: false, error: "Mojang session verification failed" });
    }
    verifiedBy = "mojang";
    if (env.HMAC_SECRET) {
      issuedToken = await signHmacToken(uuid, env.HMAC_SECRET, SESSION_TTL_SECONDS);
    }
  }

  const now = Date.now();
  const record = buildSanitizedPlayerRecord(uuid, username, body, now);
  const expiresAt = now + RECORD_TTL_SECONDS * 1000;

  await env.COSMETICS_DB
    .prepare(
      `INSERT INTO players (uuid, payload, updated_at, expires_at)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(uuid) DO UPDATE SET
         payload = excluded.payload,
         updated_at = excluded.updated_at,
         expires_at = excluded.expires_at`
    )
    .bind(uuid, JSON.stringify(record), now, expiresAt)
    .run();

  const resp = { ok: true, verifiedBy, player: record };
  if (issuedToken) {
    resp.token = issuedToken;
    resp.expiresInSec = SESSION_TTL_SECONDS;
  }
  return corsJson(200, resp);
}

function buildSanitizedPlayerRecord(uuid, username, body, now) {
  const record = {
    uuid: formatDashedUuid(uuid),
    username,
    wing: clampCosmeticId(body.wing),
    halo: clampCosmeticId(body.halo),
    particle: clampCosmeticId(body.particle),
    cosmeticColor: normalizeColor(body.cosmeticColor),
    updatedAt: now,
  };

  const nameColor = normalizeNameColor(body.color);
  if (nameColor !== undefined) record.color = nameColor;
  const font = clampOptionalInt(body.font, 0, 31);
  if (font !== undefined) record.font = font;
  const animSpeed = clampOptionalNum(body.animSpeed, 0.01, 10.0);
  if (animSpeed !== undefined) record.animSpeed = animSpeed;
  const scaleX = clampOptionalNum(body.scaleX, -5.0, 5.0);
  if (scaleX !== undefined) record.scaleX = scaleX;
  const scaleY = clampOptionalNum(body.scaleY, -5.0, 5.0);
  if (scaleY !== undefined) record.scaleY = scaleY;
  const scaleZ = clampOptionalNum(body.scaleZ, -5.0, 5.0);
  if (scaleZ !== undefined) record.scaleZ = scaleZ;
  const spinSpeedY = clampOptionalNum(body.spinSpeedY, -20.0, 20.0);
  if (spinSpeedY !== undefined) record.spinSpeedY = spinSpeedY;
  return record;
}

async function handleQuery(request, env) {
  const body = await readJsonSafe(request);
  if (!body || !Array.isArray(body.uuids)) {
    return corsJson(400, { ok: false, error: "Missing uuids array" });
  }

  const uniqueUuids = [];
  const seen = new Set();
  for (const raw of body.uuids) {
    const clean = normalizeCompactUuid(raw);
    if (clean && !seen.has(clean)) {
      seen.add(clean);
      uniqueUuids.push(clean);
      if (uniqueUuids.length >= MAX_BATCH_UUIDS) break;
    }
  }

  if (uniqueUuids.length === 0) {
    return corsJson(200, { ok: true, players: {} });
  }

  const placeholders = uniqueUuids.map(() => "?").join(", ");
  const now = Date.now();
  const queryResult = await env.COSMETICS_DB
    .prepare(`SELECT payload FROM players WHERE uuid IN (${placeholders}) AND expires_at > ?`)
    .bind(...uniqueUuids, now)
    .all();

  const players = {};
  const rows = Array.isArray(queryResult?.results) ? queryResult.results : [];
  for (const row of rows) {
    if (!row || typeof row.payload !== "string") continue;
    try {
      const entry = JSON.parse(row.payload);
      if (entry && entry.uuid) {
        players[entry.uuid] = entry;
      }
    } catch {
    }
  }
  return corsJson(200, { ok: true, players });
}

async function verifyMojangHasJoined(expectedCompactUuid, username, serverId) {
  const url =
    `https://sessionserver.mojang.com/session/minecraft/hasJoined` +
    `?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`;
  const res = await fetch(url, {
    method: "GET",
    signal: AbortSignal.timeout(4000),
  });
  if (res.status !== 200) {
    return false;
  }
  const profile = await res.json().catch(() => null);
  if (!profile || typeof profile.id !== "string") {
    return false;
  }
  return normalizeCompactUuid(profile.id) === expectedCompactUuid;
}

function mojangSignedHex(bytes) {
  const negative = (bytes[0] & 0x80) !== 0;
  const working = new Uint8Array(bytes);
  if (negative) {
    let carry = 1;
    for (let i = working.length - 1; i >= 0; i--) {
      const inverted = (~working[i]) & 0xff;
      const sum = inverted + carry;
      working[i] = sum & 0xff;
      carry = sum >> 8;
    }
  }
  const hex = toHex(working).replace(/^0+/, "") || "0";
  return negative ? `-${hex}` : hex;
}

async function signHmacToken(uuid, secret, ttlSec) {
  const exp = Math.floor(Date.now() / 1000) + ttlSec;
  const payload = `${uuid}.${exp}`;
  const sig = await hmacSha256Hex(payload, secret);
  return `${payload}.${sig}`;
}

async function verifyHmacToken(token, secret) {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [uuid, expStr, sig] = parts;
  if (!UUID_RE.test(uuid)) return null;
  const exp = Number(expStr);
  if (!Number.isFinite(exp) || exp < Math.floor(Date.now() / 1000)) return null;
  const expectedSig = await hmacSha256Hex(`${uuid}.${expStr}`, secret);
  return constantTimeEqual(sig, expectedSig) ? uuid : null;
}

async function hmacSha256Hex(data, secret) {
  const keyData = new TextEncoder().encode(secret);
  const key = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sigBuf = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  return toHex(new Uint8Array(sigBuf));
}

function constantTimeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

function normalizeCompactUuid(raw) {
  if (typeof raw !== "string") return null;
  const compact = raw.replace(/-/g, "").trim().toLowerCase();
  return UUID_RE.test(compact) ? compact : null;
}

function formatDashedUuid(compact) {
  return `${compact.slice(0, 8)}-${compact.slice(8, 12)}-${compact.slice(12, 16)}-${compact.slice(16, 20)}-${compact.slice(20)}`;
}

function clampCosmeticId(val) {
  const n = Number(val);
  if (!Number.isInteger(n) || n < 0) return 0;
  return Math.min(10, n);
}

function normalizeColor(raw) {
  if (typeof raw !== "string") return null;
  const trimmed = raw.trim();
  return HEX_COLOR_RE.test(trimmed) ? trimmed.toUpperCase() : null;
}

function normalizeNameColor(raw) {
  if (typeof raw !== "string") return undefined;
  const clean = raw.replace(/[\x00-\x1f\x7f]/g, "").trim();
  if (clean.length === 0) return "";
  if (clean.length > 64) return undefined;
  if (clean.toUpperCase() === "CHROMA") return "CHROMA";
  if (HEX_COLOR_RE.test(clean)) return clean.toUpperCase();
  if (ANIM_COLOR_RE.test(clean)) {
    const parts = clean.split(":");
    return `${parts[0].toUpperCase()}:${parts[1].toUpperCase()}:${parts[2].toUpperCase()}`;
  }
  return undefined;
}

function clampOptionalInt(val, min, max) {
  if (val === null || val === undefined) return undefined;
  const n = Number(val);
  if (!Number.isInteger(n)) return undefined;
  return Math.max(min, Math.min(max, n));
}

function clampOptionalNum(val, min, max) {
  if (val === null || val === undefined) return undefined;
  const n = Number(val);
  if (!Number.isFinite(n)) return undefined;
  return Math.max(min, Math.min(max, n));
}

function toHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function readJsonSafe(request) {
  const len = Number(request.headers.get("content-length") || "0");
  if (len > MAX_BODY_BYTES) return null;
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function corsJson(status, payload, extraHeaders = {}) {
  const headers = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    ...extraHeaders,
  };
  if (payload === null) {
    return new Response(null, { status, headers });
  }
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...headers, "Content-Type": "application/json; charset=utf-8" },
  });
}
