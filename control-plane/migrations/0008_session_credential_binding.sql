ALTER TABLE control_sessions ADD COLUMN IF NOT EXISTS user_token_hash CHAR(64);

UPDATE control_sessions s
SET user_token_hash = u.token_hash
FROM control_users u
WHERE s.user_id = u.id AND s.user_token_hash IS NULL;

ALTER TABLE control_sessions DROP CONSTRAINT IF EXISTS control_sessions_check;
ALTER TABLE control_sessions
    ADD CONSTRAINT control_sessions_credential_binding_check
    CHECK (
        (user_id IS NULL AND role='owner' AND bootstrap_token_hash IS NOT NULL AND user_token_hash IS NULL) OR
        (user_id IS NOT NULL AND bootstrap_token_hash IS NULL AND user_token_hash IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS control_sessions_expiry_idx ON control_sessions(expires_at);
