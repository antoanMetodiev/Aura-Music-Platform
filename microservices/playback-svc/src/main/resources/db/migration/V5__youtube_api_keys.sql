-- YouTube Data API keys. Rows here take precedence over the YOUTUBE_API_KEY env var; several enabled
-- rows are used in rotation, and a key whose daily quota is exhausted (403 quotaExceeded) is benched
-- until the quota resets (midnight Pacific) so the next call goes out on another key.

CREATE TABLE playback.youtube_api_keys (
    id                    UUID PRIMARY KEY,
    label                 TEXT,
    api_key               TEXT NOT NULL,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    priority              INTEGER NOT NULL DEFAULT 0,
    quota_exhausted_until TIMESTAMPTZ,
    last_used_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (api_key)
);
