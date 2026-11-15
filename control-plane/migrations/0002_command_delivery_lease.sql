ALTER TABLE device_commands
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivery_attempts INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS device_commands_lease_idx
    ON device_commands(device_id, state, delivered_at);
