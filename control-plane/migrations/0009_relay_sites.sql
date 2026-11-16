CREATE TABLE IF NOT EXISTS relay_sites (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    hostname TEXT NOT NULL UNIQUE,
    site_id TEXT NOT NULL CHECK (site_id ~ '^[A-Za-z0-9_-]{1,64}$'),
    verification_token_hash CHAR(64) NOT NULL,
    verified_at TIMESTAMPTZ,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS relay_sites_device_idx ON relay_sites(device_id, enabled);
