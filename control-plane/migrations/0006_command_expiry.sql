ALTER TABLE device_commands
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours');

ALTER TABLE device_commands DROP CONSTRAINT IF EXISTS device_commands_state_check;
ALTER TABLE device_commands
    ADD CONSTRAINT device_commands_state_check
    CHECK (state IN ('queued','delivered','succeeded','failed','expired'));

CREATE INDEX IF NOT EXISTS device_commands_expiry_idx
    ON device_commands(device_id, state, expires_at);
