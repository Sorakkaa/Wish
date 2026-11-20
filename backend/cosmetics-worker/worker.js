const CHALLENGE_TTL_SECONDS = 60;
const SESSION_TTL_SECONDS = 24 * 60 * 60;
const RECORD_TTL_SECONDS = 14 * 24 * 60 * 60;
const PUBLIC_KEYS_CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_BATCH_UUIDS = 64;
const MAX_BODY_BYTES = 16384;

const MOJANG_PUBLIC_KEYS_URL = "https://api.minecraftservices.com/publickeys";
const MOJANG_FALLBACK_CERT_KEYS_B64 = [
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==",
];

let cachedCertKeys = null;
let cachedCertKeysFetchedAt = 0;

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

    if (body.cert && typeof body.challengeSignature === "string") {
      const certCheck = await verifyMojangCertificateAndChallenge(uuid, serverId, body.cert, body.challengeSignature);
      if (!certCheck.ok) {
        return corsJson(403, { ok: false, error: certCheck.error });
      }
      verifiedBy = "mojang-cert";
    } else {
      const mojangCheck = await verifyMojangHasJoined(uuid, username, serverId);
      if (!mojangCheck.ok) {
        return corsJson(403, {
          ok: false,
          error: `Mojang session verification failed (Mojang HTTP ${mojangCheck.status})`,
        });
      }
      verifiedBy = "mojang";
    }
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

async function verifyMojangCertificateAndChallenge(compactUuid, serverId, cert, challengeSignatureB64) {
  if (!cert || typeof cert !== "object") {
    return { ok: false, error: "Missing Mojang certificate" };
  }
  const publicKeyB64 = typeof cert.publicKey === "string" ? cert.publicKey.trim() : "";
  const keySigB64 = typeof cert.keySignature === "string" ? cert.keySignature.trim() : "";
  const expiresAtMs = Number(cert.expiresAtMs);
  if (!publicKeyB64 || !keySigB64 || publicKeyB64.length > 2048 || keySigB64.length > 2048) {
    return { ok: false, error: "Malformed Mojang certificate fields" };
  }
  if (!Number.isSafeInteger(expiresAtMs) || expiresAtMs <= Date.now() - 60_000) {
    return { ok: false, error: "Mojang certificate expired" };
  }
  const cleanChallengeSig = challengeSignatureB64.trim();
  if (!cleanChallengeSig || cleanChallengeSig.length > 2048) {
    return { ok: false, error: "Malformed challenge signature" };
  }

  const publicKeyBytes = decodeBase64Bytes(publicKeyB64);
  const keySigBytes = decodeBase64Bytes(keySigB64);
  const challengeSigBytes = decodeBase64Bytes(cleanChallengeSig);
  if (!publicKeyBytes || !keySigBytes || !challengeSigBytes || publicKeyBytes.length < 64) {
    return { ok: false, error: "Invalid base64 certificate encoding" };
  }

  const certPayload = new Uint8Array(24 + publicKeyBytes.length);
  for (let i = 0; i < 16; i++) {
    certPayload[i] = parseInt(compactUuid.slice(i * 2, i * 2 + 2), 16);
  }
  new DataView(certPayload.buffer).setBigInt64(16, BigInt(expiresAtMs), false);
  certPayload.set(publicKeyBytes, 24);

  const rootKeys = await getMojangPlayerCertificateKeys();
  let certTrusted = false;
  for (const rootKey of rootKeys) {
    try {
      if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", rootKey, keySigBytes, certPayload)) {
        certTrusted = true;
        break;
      }
    } catch {
    }
  }
  if (!certTrusted) {
    return { ok: false, error: "Mojang certificate signature invalid" };
  }

  try {
    const playerKey = await crypto.subtle.importKey(
      "spki",
      publicKeyBytes,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );
    const challengeMsg = new TextEncoder().encode(`wish-cosmetic-cert:${compactUuid}:${serverId}`);
    const challengeValid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, challengeSigBytes, challengeMsg);
    if (!challengeValid) {
      return { ok: false, error: "Challenge signature invalid" };
    }
    return { ok: true };
  } catch {
    return { ok: false, error: "Invalid player public key" };
  }
}

async function getMojangPlayerCertificateKeys() {
  const now = Date.now();
  if (cachedCertKeys && cachedCertKeys.length > 0 && now - cachedCertKeysFetchedAt < PUBLIC_KEYS_CACHE_TTL_MS) {
    return cachedCertKeys;
  }

  const keyB64Set = new Set(MOJANG_FALLBACK_CERT_KEYS_B64);
  try {
    const res = await fetch(MOJANG_PUBLIC_KEYS_URL, {
      method: "GET",
      headers: {
        "User-Agent": "Wish-Cosmetics-Worker/1.1.7",
        Accept: "application/json",
      },
      signal: AbortSignal.timeout(3000),
    });
    if (res.status === 200) {
      const data = await res.json().catch(() => null);
      if (data && Array.isArray(data.playerCertificateKeys)) {
        for (const entry of data.playerCertificateKeys) {
          if (entry && typeof entry.publicKey === "string" && entry.publicKey.trim()) {
            keyB64Set.add(entry.publicKey.trim());
          }
        }
      }
    }
  } catch {
  }

  const imported = [];
  for (const b64 of keyB64Set) {
    const spki = decodeBase64Bytes(b64);
    if (!spki) continue;
    try {
      const key = await crypto.subtle.importKey(
        "spki",
        spki,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" },
        false,
        ["verify"]
      );
      imported.push(key);
    } catch {
    }
  }
  if (imported.length > 0) {
    cachedCertKeys = imported;
    cachedCertKeysFetchedAt = now;
  }
  return imported;
}

function decodeBase64Bytes(b64) {
  try {
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      out[i] = bin.charCodeAt(i);
    }
    return out;
  } catch {
    return null;
  }
}

async function verifyMojangHasJoined(expectedCompactUuid, username, serverId) {
  const url =
    `https://sessionserver.mojang.com/session/minecraft/hasJoined` +
    `?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`;
  let lastStatus = 0;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await fetch(url, {
        method: "GET",
        headers: {
          "User-Agent": "Wish-Cosmetics-Worker/1.1.7",
          Accept: "application/json",
        },
        signal: AbortSignal.timeout(4000),
      });
      lastStatus = res.status;
      if (res.status === 200) {
        const profile = await res.json().catch(() => null);
        if (!profile || typeof profile.id !== "string") {
          return { ok: false, status: 200 };
        }
        return { ok: normalizeCompactUuid(profile.id) === expectedCompactUuid, status: 200 };
      }
      if (res.status !== 204 || attempt === 1) {
        return { ok: false, status: res.status };
      }
      await new Promise((resolve) => setTimeout(resolve, 250));
    } catch {
      lastStatus = 0;
      if (attempt === 1) {
        return { ok: false, status: 0 };
      }
    }
  }
  return { ok: false, status: lastStatus };
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
