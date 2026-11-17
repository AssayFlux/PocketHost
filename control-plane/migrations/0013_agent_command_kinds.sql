-- Keep the database command-kind constraint aligned with the allow-list exposed by
-- the control-plane API. Without this migration the API accepts these commands
-- but PostgreSQL rejects them at INSERT time.
ALTER TABLE device_commands
    DROP CONSTRAINT IF EXISTS device_commands_kind_check;

ALTER TABLE device_commands
    ADD CONSTRAINT device_commands_kind_check
    CHECK (kind IN (
        'apply_config',
        'deploy_site',
        'site_action',
        'tunnel_action',
        'restart_runtime',
        'agent_job',
        'install_grant'
    ));
