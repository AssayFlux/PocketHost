# Pocket Host Linux Node Agent 0.2.0-preview

A separately installable Linux agent package with its own P-256 identity, durable assignment state and default-deny typed job executor. It is not a general remote shell.

## Install

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install ./agents/linux
pockethost-agent identity
sudo sh agents/linux/install-systemd.sh
```

The agent stores its P-256 private identity key with mode 0600 and state under
`/var/lib/pockethost-agent` for the system service (or
`~/.local/state/pockethost-agent` for a user-run agent). The systemd installer
creates `/etc/pockethost-agent.env` with mode 0600. Set
`POCKETHOST_ALLOWED_NAMESPACES` to the comma-separated namespace IDs assigned
to this agent; it is empty by default, so storage and backup access remain denied.

## Owner pairing and grant

1. Run `pockethost-agent identity` and transfer the public SPKI identity to the mobile Command Center.
2. The owner creates a protocol 1.0 enrollment challenge.
3. Copy the complete challenge + candidate proof-request JSON into `proof-request.json`. Run `pockethost-agent answer-challenge proof-request.json --owner-public-key BASE64_DER_SPKI`; submit the proof signature to the owner for approval. The proof binds the challenge and every advertised metadata field.
4. Export the owner-signed role grant and install it with `pockethost-agent install-grant grant.json --owner-public-key BASE64_DER_SPKI`.

## Authenticated remote job delivery (preview)

The independent Linux agent can now poll the existing Pocket Host control-plane
device API over HTTPS. The control-plane bearer token authenticates the transport
account; it does **not** replace the mobile owner's P-256 signature or the
installed role grant. Every `agent_job` command must carry a signed protocol job
envelope, and the agent rechecks the current grant and job signature before
executing it.

1. Register this Linux worker as a separate device in the control plane and retain
   its one-time device token securely. This is a distinct control-plane device
   record from the mobile phone.
2. Set `POCKETHOST_CONTROL_ENDPOINT=https://your-control-host` and
   `POCKETHOST_CONTROL_TOKEN=...` in `/etc/pockethost-agent.env`. Never put the
   token in a command-line argument, repository file or issue.
3. Restart the service: `sudo systemctl restart pockethost-agent`.
4. Queue an `agent_job` command for that Linux device. The payload must be the
   complete owner-signed job envelope matching the installed grant. Unsupported
   command kinds and job kinds are rejected.
5. Check `sudo journalctl -u pockethost-agent -f` and the control-plane device
   heartbeat/command acknowledgement.

The remote client requires HTTPS (HTTP is accepted only for loopback development),
validates the bearer-token format, verifies TLS using the platform trust store,
refuses redirects, bounds response sizes and uses bounded reconnect backoff.
If no endpoint and token are configured, systemd retains the local-inbox preview
mode. Control-plane device-token rotation immediately revokes the old transport
credential.

For development without systemd, set `POCKETHOST_AGENT_HOME` and
`POCKETHOST_CONTROL_TOKEN`, then run:

```sh
pockethost-agent-remote --endpoint https://your-control-host
```

## Supported local executors

- `native.health_check`
- `storage.snapshot`
- `backup.create`
- `backup.verify`

Storage snapshots are confined to the root-configured namespace allow-list,
reject symlink traversal and unsafe archive paths, enforce granted byte budgets,
and verify archive CRC plus SHA-256. Unsupported job kinds and arbitrary shell
or script payloads are rejected.

## Security boundary and remaining limitations

- Control-plane HTTPS bearer authentication is not mutual TLS. Job authority is separately enforced by owner-signed envelopes and signed role grants.
- Enrollment and grant exchange are still manually transferred; the mobile Command Center does not yet provision this Linux worker or generate/queue signed remote job envelopes end to end.
- LAN discovery, BLE/USB pairing, automatic owner-grant renewal/revocation polling, signed package updates/rollback and cross-platform Windows/macOS/SBC agents are not implemented.
- Build/publish and traffic adapters, backup restore/replication/scheduling, DemonCore integration and physical-device acceptance remain outstanding.
- The Android runtime companion remains a separate Android package/UID and is not the general-purpose Linux worker agent. Runtime packs remain on the mobile website-serving node.

Do not describe this preview as a production fleet agent until the remaining
platform, lifecycle, transport and end-to-end acceptance gates pass.
