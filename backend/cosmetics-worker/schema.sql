CREATE TABLE IF NOT EXISTS challenges (
  uuid TEXT PRIMARY KEY,
  server_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_challenges_expires_at
  ON challenges (expires_at);

CREATE TABLE IF NOT EXISTS players (
  uuid TEXT PRIMARY KEY,
  payload TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_players_expires_at
  ON players (expires_at);
