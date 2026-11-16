CREATE TABLE IF NOT EXISTS control_sessions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES control_users(id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
    role TEXT NOT NULL CHECK (role IN ('owner','editor','viewer')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    bootstrap_token_hash CHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (user_id IS NULL AND role='owner' AND bootstrap_token_hash IS NOT NULL) OR
        (user_id IS NOT NULL AND bootstrap_token_hash IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS control_sessions_user_active_idx
    ON control_sessions(user_id, expires_at)
    WHERE revoked_at IS NULL;
