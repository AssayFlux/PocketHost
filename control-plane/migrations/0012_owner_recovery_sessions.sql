ALTER TABLE control_sessions
    ADD COLUMN IF NOT EXISTS owner_recovery_id UUID
    REFERENCES owner_recovery_codes(id) ON DELETE CASCADE;

ALTER TABLE control_sessions
    DROP CONSTRAINT IF EXISTS control_sessions_credential_binding_check;

ALTER TABLE control_sessions
    ADD CONSTRAINT control_sessions_credential_binding_check
    CHECK (
        (user_id IS NULL AND role = 'owner' AND bootstrap_token_hash IS NOT NULL
            AND user_token_hash IS NULL AND owner_passkey_id IS NULL AND owner_recovery_id IS NULL) OR
        (user_id IS NOT NULL AND bootstrap_token_hash IS NULL
            AND user_token_hash IS NOT NULL AND owner_passkey_id IS NULL AND owner_recovery_id IS NULL) OR
        (user_id IS NULL AND role = 'owner' AND bootstrap_token_hash IS NULL
            AND user_token_hash IS NULL AND owner_passkey_id IS NOT NULL AND owner_recovery_id IS NULL) OR
        (user_id IS NULL AND role = 'owner' AND bootstrap_token_hash IS NULL
            AND user_token_hash IS NULL AND owner_passkey_id IS NULL AND owner_recovery_id IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS control_sessions_recovery_active_idx
    ON control_sessions(owner_recovery_id, expires_at)
    WHERE revoked_at IS NULL AND owner_recovery_id IS NOT NULL;
