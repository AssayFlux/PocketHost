"""Conservative typed-job executor; unknown work is rejected, never shell-executed."""
from __future__ import annotations

import hashlib
import json
import os
import platform
import time
import uuid
from datetime import datetime, timezone
from typing import Any

SUPPORTED = {
    "native.health_check": ("native_job", "native_job.report_health", {"check_id": "identifier"}),
}


def _valid_value(kind: str, value: Any) -> bool:
    if kind == "identifier":
        return isinstance(value, str) and 1 <= len(value) <= 128 and all(c.isalnum() or c in "._-" for c in value)
    return False


def validate_job(envelope: dict[str, Any], grant: dict[str, Any]) -> None:
    if envelope.get("protocol") != "pockethost.device/1.0":
        raise ValueError("Unsupported job protocol.")
    try:
        uuid.UUID(envelope["job_id"])
        uuid.UUID(envelope["correlation_id"])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("Job and correlation IDs must be UUIDs.") from error
    idempotency_key = envelope.get("idempotency_key")
    if not isinstance(idempotency_key, str) or not 16 <= len(idempotency_key) <= 128:
        raise ValueError("Job idempotency key is invalid.")
    if envelope.get("target_device_id") != grant.get("device_id"):
        raise ValueError("Job targets another device.")
    if envelope.get("role_grant_id") != grant.get("grant_id") or envelope.get("owner_id") != grant.get("owner_id"):
        raise ValueError("Job is not bound to the active role grant.")
    kind = envelope.get("kind")
    if kind not in SUPPORTED:
        raise ValueError("Job kind has no installed executor; default deny.")
    required_role, required_capability, schema = SUPPORTED[kind]
    if grant.get("role") != required_role:
        raise ValueError("Job kind is not permitted by the active role.")
    if required_capability not in grant.get("capabilities", []):
        raise ValueError("Active role grant does not include the required capability.")
    payload = envelope.get("payload")
    if not isinstance(payload, dict) or set(payload) != set(schema):
        raise ValueError("Job payload has missing or unknown properties.")
    if any(not _valid_value(schema[key], value) for key, value in payload.items()):
        raise ValueError("Job payload value is invalid.")
    job_budget = envelope.get("resource_budget")
    grant_budget = grant.get("resource_budget", {})
    if not isinstance(job_budget, dict) or not {"timeout_seconds", "memory_bytes"}.issubset(job_budget):
        raise ValueError("Job resource budget is missing required limits.")
    if (not isinstance(job_budget["timeout_seconds"], int) or not 1 <= job_budget["timeout_seconds"] <= 86400
            or not isinstance(job_budget["memory_bytes"], int) or job_budget["memory_bytes"] < 1):
        raise ValueError("Job resource budget is invalid.")
    if job_budget["memory_bytes"] > grant_budget.get("memory_bytes", 0):
        raise ValueError("Job memory request exceeds the active role grant.")
    if job_budget.get("storage_bytes", 0) > grant_budget.get("storage_bytes", 0):
        raise ValueError("Job storage request exceeds the active role grant.")
    if job_budget.get("network_bytes", 0) > grant_budget.get("network_bytes_per_day", 0):
        raise ValueError("Job network request exceeds the active role grant.")
    try:
        issued = datetime.fromisoformat(envelope["issued_at"].replace("Z", "+00:00"))
        expires = datetime.fromisoformat(envelope["expires_at"].replace("Z", "+00:00"))
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("Job timestamps are invalid.") from error
    now = datetime.now(timezone.utc)
    if issued.tzinfo is None or expires.tzinfo is None or issued > now or expires <= now or expires <= issued:
        raise ValueError("Job is expired or not yet valid.")


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
