"""Protocol 1.0 trust primitives for the independently installed Linux agent."""
from __future__ import annotations

import base64
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.exceptions import InvalidSignature

PROTOCOL = "pockethost.device/1.0"


def canonical_json(value: dict[str, Any]) -> bytes:
    # Protocol payloads forbid floating point values; sorted compact UTF-8 JSON is
    # the canonical subset used by the mobile implementation for these objects.
    def reject_float(item: Any) -> None:
        if isinstance(item, float):
            raise ValueError("Floating-point values are forbidden in signed protocol payloads.")
        if isinstance(item, dict):
            for child in item.values():
                reject_float(child)
        elif isinstance(item, list):
            for child in item:
                reject_float(child)
    reject_float(value)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def public_key_id(public_key_der: bytes) -> str:
    return hashlib.sha256(public_key_der).hexdigest()


def load_or_create_identity(state_dir: Path) -> tuple[ec.EllipticCurvePrivateKey, str, str]:
    state_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(state_dir, 0o700)
    key_path = state_dir / "device-identity-p256.pem"
    if key_path.exists():
        if os.name == "posix" and key_path.stat().st_mode & 0o077:
            raise PermissionError("Device identity key permissions are too broad; expected 0600.")
        key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
        if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
            raise ValueError("Stored identity is not a P-256 private key.")
    else:
        key = ec.generate_private_key(ec.SECP256R1())
        fd = os.open(key_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as stream:
            stream.write(key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            ))
            stream.flush()
            os.fsync(stream.fileno())
    public_der = key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    device_id = public_key_id(public_der)
    return key, device_id, base64.b64encode(public_der).decode("ascii")


def sign_challenge(private_key: ec.EllipticCurvePrivateKey, challenge: dict[str, Any]) -> str:
    if challenge.get("protocol") != PROTOCOL:
        raise ValueError("Unsupported enrollment protocol.")
    payload = {key: value for key, value in challenge.items() if key != "signature"}
    return base64.b64encode(private_key.sign(canonical_json(payload), ec.ECDSA(hashes.SHA256()))).decode("ascii")


def verify_role_grant(
    grant: dict[str, Any], owner_public_key_der_b64: str, device_id: str,
    minimum_generation: int, role_policy: dict[str, Any],
) -> None:
    if grant.get("protocol") != PROTOCOL or grant.get("device_id") != device_id:
        raise ValueError("Grant protocol or target device does not match this agent.")
    if not isinstance(grant.get("generation"), int) or grant["generation"] < minimum_generation:
        raise ValueError("Grant generation is stale.")
    try:
        issued = datetime.fromisoformat(grant["issued_at"].replace("Z", "+00:00"))
        expires = datetime.fromisoformat(grant["expires_at"].replace("Z", "+00:00"))
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("Grant timestamps are invalid.") from error
    now = datetime.now(timezone.utc)
    if issued.tzinfo is None or expires.tzinfo is None or issued > now or expires <= now or expires <= issued:
        raise ValueError("Grant is not currently valid.")
    role = grant.get("role")
    roles = role_policy.get("roles", {})
    if role not in roles:
        raise ValueError("Unknown role is denied.")
    allowed = set(roles[role].get("allow", []))
    denied = set(roles[role].get("deny", []))
    capabilities = grant.get("capabilities")
    denied_capabilities = set(grant.get("denied_capabilities", []))
    if not isinstance(capabilities, list) or len(capabilities) != len(set(capabilities)):
        raise ValueError("Grant capabilities must be a unique list.")
    if not set(capabilities).issubset(allowed) or set(capabilities) & denied or denied_capabilities & set(capabilities):
        raise ValueError("Grant exceeds the role capability policy.")
    budget = grant.get("resource_budget")
    if not isinstance(budget, dict):
        raise ValueError("Missing resource budget.")
    required = {"cpu_millis", "memory_bytes", "storage_bytes", "network_bytes_per_day"}
    if not required.issubset(budget) or any(not isinstance(budget[k], int) or budget[k] < 0 for k in required):
        raise ValueError("Invalid resource budget.")
    signature = grant.get("signature", {})
    if signature.get("algorithm") != "ecdsa-p256-sha256":
        raise ValueError("Unsupported owner signature algorithm.")
    owner_der = base64.b64decode(owner_public_key_der_b64, validate=True)
    owner_key = serialization.load_der_public_key(owner_der)
    if not isinstance(owner_key, ec.EllipticCurvePublicKey) or not isinstance(owner_key.curve, ec.SECP256R1):
        raise ValueError("Owner key must be P-256.")
    owner_id = public_key_id(owner_der)
    if owner_id != grant.get("owner_id") or signature.get("key_id") != owner_id:
        raise ValueError("Owner key fingerprint does not match grant owner.")
    payload = {key: value for key, value in grant.items() if key != "signature"}
    try:
        owner_key.verify(base64.b64decode(signature["value"], validate=True), canonical_json(payload), ec.ECDSA(hashes.SHA256()))
    except (InvalidSignature, KeyError, ValueError) as error:
        raise ValueError("Role grant signature is invalid.") from error


def verify_job_signature(envelope: dict[str, Any], owner_public_key_der_b64: str) -> None:
    signature = envelope.get("signature", {})
    if signature.get("algorithm") != "ecdsa-p256-sha256":
        raise ValueError("Unsupported job signature algorithm.")
    owner_der = base64.b64decode(owner_public_key_der_b64, validate=True)
    owner_key = serialization.load_der_public_key(owner_der)
    owner_id = public_key_id(owner_der)
    if signature.get("key_id") != owner_id or envelope.get("owner_id") != owner_id:
        raise ValueError("Job signer does not match the active owner identity.")
    if not isinstance(owner_key, ec.EllipticCurvePublicKey) or not isinstance(owner_key.curve, ec.SECP256R1):
        raise ValueError("Job owner key must be P-256.")
    payload = {key: value for key, value in envelope.items() if key != "signature"}
    try:
        owner_key.verify(base64.b64decode(signature["value"], validate=True), canonical_json(payload), ec.ECDSA(hashes.SHA256()))
    except (InvalidSignature, KeyError, ValueError) as error:
        raise ValueError("Job envelope signature is invalid.") from error
