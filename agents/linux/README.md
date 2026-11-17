# Pocket Host Linux Node Agent 0.1.0

A separately installable Linux agent package. It has its own device identity, durable assignment state and a default-deny typed job executor. It is not a general remote shell.

## Install

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install ./agents/linux
pockethost-agent identity
sudo sh agents/linux/install-systemd.sh
```

The agent stores its P-256 private identity key with mode 0600 and state under
`~/.local/state/pockethost-agent` (override with `POCKETHOST_AGENT_HOME`). The systemd installer creates `/etc/pockethost-agent.env`; set `POCKETHOST_ALLOWED_NAMESPACES` to the comma-separated namespace IDs assigned to this agent. It is empty by default, so storage and backup access are denied until an administrator configures the allow-list.

## Initial pairing flow

1. Run `pockethost-agent identity` and transfer the public SPKI identity to the mobile Command Center.
2. The owner creates a protocol 1.0 enrollment challenge.
3. Copy the mobile screen's complete challenge + candidate proof-request JSON into `proof-request.json`. Run `pockethost-agent answer-challenge proof-request.json --owner-public-key BASE64_DER_SPKI`; submit the proof signature to the owner for approval. The agent verifies the owner's challenge signature and signs both the challenge and every advertised metadata field.
4. Export the owner-signed role grant and install it with
   `pockethost-agent install-grant grant.json --owner-public-key BASE64_DER_SPKI`.
5. Run a supported typed job using `pockethost-agent run-job job.json`.

The first Linux slice implements the cryptographic and durable local agent boundary, a native health-check executor, and bounded local namespace snapshot/backup verification jobs. Unsupported job kinds are rejected. It does not yet include LAN discovery, a remote enrollment endpoint, network job transport, signed package updates or automatic rollback. Do not expose it to an untrusted
network or describe it as a production fleet agent until those gates are implemented.

## Security boundary

- No shell command or arbitrary script is accepted from a job envelope.
- Grants are verified against the shared role capability policy and owner P-256 key.
- Device target, grant ID, owner, role and generation are bound to each job.
- State uses SQLite WAL and FULL synchronous durability.
- Identity private key is local and owner key material is never imported.
