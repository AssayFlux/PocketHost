"""Authenticated HTTPS polling transport for the independent Linux node agent.

The control-plane bearer token authenticates the enrolled agent account. Every
job still requires a separate owner signature and a currently valid role grant.
"""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit
import uuid

from .runner import execute_job
from .store import AgentStore
from .trust import verify_job_signature, verify_role_grant

MAX_RESPONSE_BYTES = 10 * 1024 * 1024
MAX_RESULT_BYTES = 64 * 1024


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class ControlPlaneClient:
    def __init__(self, endpoint: str, token: str, timeout: float = 20.0):
        parsed = urlsplit(endpoint.strip().rstrip("/"))
        if (parsed.scheme != "https" and not
                (parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"})):
            raise ValueError("Control-plane transport requires HTTPS (HTTP is localhost-only).")
        if (not parsed.hostname or parsed.username or parsed.password or parsed.query or
                parsed.fragment or parsed.path not in {"", "/"}):
            raise ValueError("Invalid control-plane endpoint.")
        if (not 32 <= len(token) <= 256 or not token.isascii() or
                not all(ch.isalnum() or ch in "-_." for ch in token)):
            raise ValueError("Control-plane device token format is invalid.")
        if not 1 <= timeout <= 60:
            raise ValueError("HTTP timeout must be between 1 and 60 seconds.")
        self.endpoint = endpoint.strip().rstrip("/")
        self.token = token
        self.timeout = timeout
        self.opener = urllib.request.build_opener(NoRedirect)

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        if not path.startswith("/v1/") or path.startswith("//"):
            raise ValueError("Invalid control-plane API path.")
        data = None if payload is None else json.dumps(
            payload, sort_keys=True, separators=(",", ":"), allow_nan=False
        ).encode("utf-8")
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.endpoint + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError("Control-plane returned HTTP " + str(response.status))
                body = response.read(MAX_RESPONSE_BYTES + 1)
                if len(body) > MAX_RESPONSE_BYTES:
                    raise RuntimeError("Control-plane response exceeds the size limit.")
                if not body:
                    return None
                return json.loads(body)
        except urllib.error.HTTPError as error:
            # Do not follow redirects: a bearer token must never be forwarded to
            # another origin or silently downgraded to an untrusted endpoint.
            raise RuntimeError("Control-plane returned HTTP " + str(error.code)) from error

    def heartbeat(self, device_id: str, state: dict[str, Any]) -> None:
        self.request("POST", "/v1/device/heartbeat", {
            "state": {"agent": state, "agent_device_id": device_id},
            "config_version": 0,
        })

    def poll(self) -> list[dict[str, Any]]:
        value = self.request("GET", "/v1/device/commands")
        if not isinstance(value, list) or len(value) > 32:
            raise RuntimeError("Control-plane command response is invalid.")
        if not all(isinstance(item, dict) for item in value):
            raise RuntimeError("Control-plane command response contains an invalid item.")
        return value

    def acknowledge(self, command_id: str, success: bool, result: dict[str, Any]) -> None:
        encoded = json.dumps(result, separators=(",", ":"), allow_nan=False).encode()
        if len(encoded) > MAX_RESULT_BYTES:
            success = False
            result = {"error": "agent_result_exceeds_64_kib"}
        self.request("POST", "/v1/device/commands/" + command_id + "/ack", {
            "success": success, "result": result,
        })


def _active_grant(store: AgentStore, device_id: str) -> tuple[dict[str, Any], str]:
    grant_row = store.db.execute("SELECT value FROM agent_state WHERE key='grant'").fetchone()
    owner_row = store.db.execute("SELECT value FROM agent_state WHERE key='owner_public_key'").fetchone()
    if not grant_row or not owner_row:
        raise ValueError("No verified owner role grant is installed.")
    grant = json.loads(grant_row[0])
    owner_key = owner_row[0]
    policy_path = Path(__file__).resolve().parent / "ROLE_CAPABILITY_POLICY.json"
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    verify_role_grant(grant, owner_key, device_id, store.get_generation(), policy)
    return grant, owner_key


def process_command(client: ControlPlaneClient, store: AgentStore, device_id: str,
                    command: dict[str, Any]) -> None:
    command_id = command.get("id")
    if not isinstance(command_id, str):
        raise ValueError("Remote command ID is invalid.")
    try:
        uuid.UUID(command_id)
    except (TypeError, ValueError) as error:
        raise ValueError("Remote command ID is invalid.") from error
    command_kind = command.get("kind")
    envelope = command.get("payload")
    if command_kind == "install_grant":
        if not isinstance(envelope, dict):
            client.acknowledge(command_id, False, {"error": "signed_role_grant_required"})
            return
        grant = envelope.get("grant")
        owner_key = envelope.get("owner_public_key_spki")
        if not isinstance(grant, dict) or not isinstance(owner_key, str):
            client.acknowledge(command_id, False, {"error": "signed_role_grant_required"})
            return
        try:
            policy_path = Path(__file__).resolve().parent / "ROLE_CAPABILITY_POLICY.json"
            policy = json.loads(policy_path.read_text(encoding="utf-8"))
            current_row = store.db.execute("SELECT value FROM agent_state WHERE key='grant'").fetchone()
            owner_row = store.db.execute("SELECT value FROM agent_state WHERE key='owner_public_key'").fetchone()
            if (current_row and owner_row and store.get_generation() == grant.get("generation")
                    and owner_row[0] == owner_key and json.loads(current_row[0]) == grant):
                client.acknowledge(command_id, True, {
                    "status": "grant_already_installed", "generation": grant["generation"],
                    "role": grant["role"],
                })
                return
            verify_role_grant(grant, owner_key, device_id, store.get_generation() + 1, policy)
            store.save_grant(grant, owner_key)
            client.acknowledge(command_id, True, {
                "status": "grant_installed", "generation": grant["generation"],
                "role": grant["role"],
            })
        except Exception as error:
            client.acknowledge(command_id, False, {"error": str(error)[:256]})
        return
    if command_kind != "agent_job":
        client.acknowledge(command_id, False, {"error": "unsupported_agent_command"})
        return
    if not isinstance(envelope, dict):
        client.acknowledge(command_id, False, {"error": "signed_job_envelope_required"})
        return
    job_id = envelope.get("job_id")
    idempotency_key = envelope.get("idempotency_key")
    if not isinstance(job_id, str) or not isinstance(idempotency_key, str):
        client.acknowledge(command_id, False, {"error": "job_identity_required"})
        return
    try:
        grant, owner_key = _active_grant(store, device_id)
        verify_job_signature(envelope, owner_key)
    except Exception as error:
        client.acknowledge(command_id, False, {"error": str(error)[:256]})
        return

    # Duplicate delivery after a lost ACK returns the durable prior result; it
    # never re-runs a completed side effect.
    inserted = store.enqueue_once(job_id, idempotency_key, envelope)
    existing = store.db.execute(
        "SELECT state,result FROM jobs WHERE job_id=?", (job_id,)
    ).fetchone()
    if not inserted:
        if not existing:
            client.acknowledge(command_id, False, {"error": "idempotency_key_conflict"})
            return
        state, saved_result = existing
        if state in {"SUCCEEDED", "FAILED"}:
            result = json.loads(saved_result or "{}")
            client.acknowledge(command_id, state == "SUCCEEDED", result)
            return
        # RUNNING is recovered to QUEUED on process start; do not steal a live
        # execution if another worker thread/process owns it.
        client.acknowledge(command_id, False, {"error": "job_already_in_progress"})
        return

    if not store.claim_job(job_id):
        client.acknowledge(command_id, False, {"error": "job_claim_failed"})
        return
    try:
        grant, owner_key = _active_grant(store, device_id)
        verify_job_signature(envelope, owner_key)
        # Re-validate the signed grant immediately before executing a side effect.
        grant, owner_key = _active_grant(store, device_id)
        verify_job_signature(envelope, owner_key)
        result = execute_job(envelope, grant)
        store.complete_job(job_id, result)
        client.acknowledge(command_id, True, result)
    except Exception as error:
        failure = {"error": str(error)[:256]}
        store.fail_job(job_id, failure["error"])
        client.acknowledge(command_id, False, failure)


def run_remote(endpoint: str, token: str, root: Path, poll_interval: float = 15.0) -> None:
    if not 1 <= poll_interval <= 300:
        raise ValueError("Remote poll interval must be between 1 and 300 seconds.")
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    if os.name == "posix":
        os.chmod(root, 0o700)
    _, device_id, _ = __import__(
        "pockethost_agent.trust", fromlist=["load_or_create_identity"]
    ).load_or_create_identity(root)
    store = AgentStore(root / "agent-state.sqlite3")
    client = ControlPlaneClient(endpoint, token)
    store.recover_interrupted_jobs()
    retry = poll_interval
    try:
        while True:
            try:
                try:
                    _active_grant(store, device_id)
                    grant_state = "GRANTED"
                except (ValueError, KeyError, TypeError):
                    grant_state = "UNASSIGNED"
                client.heartbeat(device_id, {
                    "status": "ONLINE", "protocol": "pockethost.device/1.0",
                    "agent_version": "0.2.0-preview", "grant_state": grant_state,
                })
                for command in client.poll():
                    process_command(client, store, device_id, command)
                retry = poll_interval
                time.sleep(poll_interval)
            except (OSError, RuntimeError, ValueError, json.JSONDecodeError):
                time.sleep(retry)
                retry = min(300.0, max(poll_interval, retry * 2))
    finally:
        store.close()
