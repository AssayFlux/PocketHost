CREATE TABLE IF NOT EXISTS owner_passkeys (
    credential_id TEXT PRIMARY KEY,
    credential JSONB NOT NULL,
    display_name TEXT NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS owner_passkeys_active_idx
    ON owner_passkeys(created_at)
    WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS owner_recovery_codes (
    id UUID PRIMARY KEY,
    code_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS owner_auth_policy (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    passkey_required BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO owner_auth_policy(id, passkey_required)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE control_sessions
    ADD COLUMN IF NOT EXISTS owner_passkey_id TEXT
    REFERENCES owner_passkeys(credential_id) ON DELETE SET NULL;

ALTER TABLE control_sessions
    DROP CONSTRAINT IF EXISTS control_sessions_credential_binding_check;

ALTER TABLE control_sessions
    ADD CONSTRAINT control_sessions_credential_binding_check
    CHECK (
        (user_id IS NULL AND role = 'owner' AND bootstrap_token_hash IS NOT NULL
            AND user_token_hash IS NULL AND owner_passkey_id IS NULL) OR
        (user_id IS NOT NULL AND bootstrap_token_hash IS NULL
            AND user_token_hash IS NOT NULL AND owner_passkey_id IS NULL) OR
        (user_id IS NULL AND role = 'owner' AND bootstrap_token_hash IS NULL
            AND user_token_hash IS NULL AND owner_passkey_id IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS control_sessions_passkey_active_idx
    ON control_sessions(owner_passkey_id, expires_at)
    WHERE revoked_at IS NULL AND owner_passkey_id IS NOT NULL;
