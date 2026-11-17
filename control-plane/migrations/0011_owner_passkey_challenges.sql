CREATE TABLE IF NOT EXISTS owner_passkey_challenges (
    id UUID PRIMARY KEY,
    ceremony TEXT NOT NULL CHECK (ceremony IN ('registration', 'authentication')),
    session_id UUID REFERENCES control_sessions(id) ON DELETE CASCADE,
    state JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (
        (ceremony = 'registration' AND session_id IS NOT NULL) OR
        (ceremony = 'authentication' AND session_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS owner_passkey_challenges_expiry_idx
    ON owner_passkey_challenges(expires_at);
