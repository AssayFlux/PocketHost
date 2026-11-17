"""Conservative typed-job executor; unknown work is rejected, never shell-executed."""
from __future__ import annotations

import hashlib
import json
import os
import platform
import time
from typing import Any

SUPPORTED = {
    "native.health_check": ("native_job", {"check_id": "identifier"}),
}


def _valid_value(kind: str, value: Any) -> bool:
    if kind == "identifier":
        return isinstance(value, str) and 1 <= len(value) <= 128 and all(c.isalnum() or c in "._-" for c in value)
    return False


def validate_job(envelope: dict[str, Any], grant: dict[str, Any]) -> None:
    if envelope.get("protocol") != "pockethost.device/1.0":
        raise ValueError("Unsupported job protocol.")
    if envelope.get("target_device_id") != grant.get("device_id"):
        raise ValueError("Job targets another device.")
    if envelope.get("role_grant_id") != grant.get("grant_id") or envelope.get("owner_id") != grant.get("owner_id"):
        raise ValueError("Job is not bound to the active role grant.")
    kind = envelope.get("kind")
    if kind not in SUPPORTED:
        raise ValueError("Job kind has no installed executor; default deny.")
    required_role, schema = SUPPORTED[kind]
    if grant.get("role") != required_role:
        raise ValueError("Job kind is not permitted by the active role.")
    payload = envelope.get("payload")
    if not isinstance(payload, dict) or set(payload) != set(schema):
        raise ValueError("Job payload has missing or unknown properties.")
    if any(not _valid_value(schema[key], value) for key, value in payload.items()):
        raise ValueError("Job payload value is invalid.")
    issued = envelope.get("issued_at")
    expires = envelope.get("expires_at")
    if not isinstance(issued, str) or not isinstance(expires, str):
        raise ValueError("Job timestamps are required.")


def execute_job(envelope: dict[str, Any], grant: dict[str, Any]) -> dict[str, Any]:
    validate_job(envelope, grant)
    started = time.monotonic()
    return {
        "check_id": envelope["payload"]["check_id"],
        "status": "healthy",
        "platform": platform.platform(),
        "architecture": platform.machine(),
        "python": platform.python_version(),
        "agent_version": "0.1.0",
        "uptime_check_ms": int((time.monotonic() - started) * 1000),
        "kernel": os.uname().release if hasattr(os, "uname") else "unknown",
    }


def result_sha256(result: dict[str, Any]) -> str:
    return hashlib.sha256(json.dumps(result, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
