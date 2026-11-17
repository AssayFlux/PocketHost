import base64
import json
import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.exceptions import InvalidSignature

from pockethost_agent.trust import canonical_json, load_or_create_identity, public_key_id, verify_role_grant, verify_job_signature, sign_challenge
from pockethost_agent.store import AgentStore
from pockethost_agent.runner import execute_job, validate_job

POLICY = json.loads((Path(__file__).resolve().parents[2] / "protocol" / "ROLE_CAPABILITY_POLICY.json").read_text())
PACKAGED_POLICY = json.loads((Path(__file__).resolve().parents[2] / "agents" / "linux" / "src" / "pockethost_agent" / "ROLE_CAPABILITY_POLICY.json").read_text())


class LinuxAgentTests(unittest.TestCase):
    def test_packaged_role_policy_matches_protocol(self):
        self.assertEqual(POLICY, PACKAGED_POLICY)

    def test_identity_is_stable_and_private(self):
        with tempfile.TemporaryDirectory() as directory:
            key, device_id, public = load_or_create_identity(Path(directory))
            _, again, public_again = load_or_create_identity(Path(directory))
            self.assertEqual(device_id, again)
            self.assertEqual(public, public_again)
            self.assertEqual(0o600, (Path(directory) / "device-identity-p256.pem").stat().st_mode & 0o777)

    def make_grant(self, device_id):
        owner = ec.generate_private_key(ec.SECP256R1())
        owner_der = owner.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        grant = {
            "protocol": "pockethost.device/1.0", "grant_id": "00000000-0000-4000-8000-000000000001",
            "owner_id": public_key_id(owner_der), "device_id": device_id, "role": "native_job",
            "generation": 1, "capabilities": ["native_job.report_health"], "denied_capabilities": [],
            "resource_budget": {"cpu_millis": 100, "memory_bytes": 67108864, "storage_bytes": 0, "network_bytes_per_day": 0},
            "issued_at": "2026-11-17T00:00:00Z", "expires_at": "2027-11-17T00:00:00Z",
            "policy_version": "1.0"
        }
        signature = owner.sign(canonical_json(grant), ec.ECDSA(hashes.SHA256()))
        grant["signature"] = {"algorithm": "ecdsa-p256-sha256", "key_id": grant["owner_id"], "value": base64.b64encode(signature).decode()}
        return grant, base64.b64encode(owner_der).decode(), owner

    def test_enrollment_proof_binds_owner_challenge_and_candidate_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            device_key, device_id, device_public = load_or_create_identity(Path(directory))
            owner = ec.generate_private_key(ec.SECP256R1())
            owner_der = owner.public_key().public_bytes(
                serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
            )
            owner_id = public_key_id(owner_der)
            now = datetime.now(timezone.utc)
            challenge = {
                "protocol": "pockethost.device/1.0",
                "challenge_id": "00000000-0000-4000-8000-000000000001",
                "owner_id": owner_id,
                "device_id": device_id,
                "challenge_nonce": "A" * 43,
                "issued_at": now.isoformat().replace("+00:00", "Z"),
                "expires_at": (now + timedelta(minutes=5)).isoformat().replace("+00:00", "Z"),
                "single_use": True,
                "transport": "lan",
            }
            owner_signature = owner.sign(canonical_json(challenge), ec.ECDSA(hashes.SHA256()))
            challenge["signature"] = {
                "algorithm": "ecdsa-p256-sha256", "key_id": owner_id,
                "value": base64.b64encode(owner_signature).decode(),
            }
            candidate = {
                "architecture": "x86_64", "device_id": device_id,
                "display_name": "Linux worker", "os": "linux",
                "product": "pockethost-node-agent", "protocol_version": "1.0",
                "public_key_spki": device_public,
                "requested_capabilities": ["native_job.report_health"],
            }
            request = {"challenge": challenge, "candidate": candidate}
            proof = base64.b64decode(sign_challenge(
                device_key, request, base64.b64encode(owner_der).decode()
            ))
            unsigned = {key: value for key, value in challenge.items() if key != "signature"}
            payload = canonical_json(unsigned) + b"\\n" + canonical_json(candidate)
            device_key.public_key().verify(proof, payload, ec.ECDSA(hashes.SHA256()))
            altered = dict(candidate)
            altered["display_name"] = "Different device name"
            with self.assertRaises(InvalidSignature):
                device_key.public_key().verify(
                    proof, canonical_json(unsigned) + b"\\n" + canonical_json(altered),
                    ec.ECDSA(hashes.SHA256())
                )

    def test_grant_signature_and_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            _, device_id, _ = load_or_create_identity(Path(directory))
            grant, owner_key, owner_private = self.make_grant(device_id)
            verify_role_grant(grant, owner_key, device_id, 1, POLICY)
            expired = dict(grant)
            expired["expires_at"] = "2026-11-16T00:00:00Z"
            with self.assertRaises(ValueError):
                verify_role_grant(expired, owner_key, device_id, 1, POLICY)
            job = {"protocol": "pockethost.device/1.0", "owner_id": grant["owner_id"], "job_id": "00000000-0000-4000-8000-000000000002"}
            job["signature"] = {"algorithm": "ecdsa-p256-sha256", "key_id": grant["owner_id"], "value": base64.b64encode(owner_private.sign(canonical_json(job), ec.ECDSA(hashes.SHA256()))).decode()}
            verify_job_signature(job, owner_key)
            job["job_id"] = "00000000-0000-4000-8000-000000000003"
            with self.assertRaises(ValueError):
                verify_job_signature(job, owner_key)
            grant["capabilities"].append("management.admin")
            with self.assertRaises(ValueError):
                verify_role_grant(grant, owner_key, device_id, 1, POLICY)

    def test_target_and_generation_are_enforced(self):
        with tempfile.TemporaryDirectory() as directory:
            _, device_id, _ = load_or_create_identity(Path(directory))
            grant, owner_key, _ = self.make_grant(device_id)
            with self.assertRaises(ValueError):
                verify_role_grant(grant, owner_key, "0" * 64, 1, POLICY)
            with self.assertRaises(ValueError):
                verify_role_grant(grant, owner_key, device_id, 2, POLICY)

    def test_typed_job_default_deny(self):
        grant = {"grant_id": "00000000-0000-4000-8000-000000000001", "owner_id": "o", "device_id": "d" * 64, "role": "native_job", "capabilities": ["native_job.report_health"], "resource_budget": {"cpu_millis": 100, "memory_bytes": 4096, "storage_bytes": 0, "network_bytes_per_day": 0}}
        job = {"protocol": "pockethost.device/1.0", "target_device_id": "d" * 64, "role_grant_id": "00000000-0000-4000-8000-000000000001", "job_id": "00000000-0000-4000-8000-000000000002", "correlation_id": "00000000-0000-4000-8000-000000000003", "idempotency_key": "idempotency-key-0001",
               "owner_id": "o", "kind": "native.health_check", "issued_at": "2026-11-17T00:00:00Z",
               "expires_at": "2027-11-17T00:05:00Z", "resource_budget": {"timeout_seconds": 30, "memory_bytes": 1024}, "payload": {"check_id": "node-1"}}
        self.assertEqual("healthy", execute_job(job, grant)["status"])
        job["kind"] = "process.exec"
        with self.assertRaises(ValueError):
            validate_job(job, grant)

    def test_storage_snapshot_is_confined_bounded_and_verifiable(self):
        with tempfile.TemporaryDirectory() as directory:
            namespace_root = Path(directory) / "namespaces"
            snapshot_root = Path(directory) / "snapshots"
            (namespace_root / "site-1").mkdir(parents=True)
            (namespace_root / "site-1" / "index.html").write_text("Pocket Host")
            with patch.dict(os.environ, {
                "POCKETHOST_STORAGE_ROOT": str(namespace_root),
                "POCKETHOST_SNAPSHOT_ROOT": str(snapshot_root),
            }):
                grant = {
                    "grant_id": "00000000-0000-4000-8000-000000000001",
                    "owner_id": "owner", "device_id": "d" * 64, "role": "storage",
                    "capabilities": ["storage.snapshot"],
                    "resource_budget": {"cpu_millis": 100, "memory_bytes": 4096,
                                        "storage_bytes": 4096, "network_bytes_per_day": 0},
                }
                job = {
                    "protocol": "pockethost.device/1.0",
                    "job_id": "00000000-0000-4000-8000-000000000002",
                    "correlation_id": "00000000-0000-4000-8000-000000000003",
                    "idempotency_key": "storage-idempotency-0001",
                    "owner_id": "owner", "target_device_id": "d" * 64,
                    "role_grant_id": grant["grant_id"], "kind": "storage.snapshot",
                    "issued_at": "2026-11-17T00:00:00Z", "expires_at": "2027-11-17T00:05:00Z",
                    "resource_budget": {"timeout_seconds": 30, "memory_bytes": 1024, "storage_bytes": 4096},
                    "payload": {"namespace_id": "site-1", "snapshot_id": "snapshot-1"},
                }
                result = execute_job(job, grant)
                self.assertEqual(1, result["file_count"])
                grant["role"] = "backup_recovery"
                grant["capabilities"] = ["backup.verify_snapshot"]
                job["kind"] = "backup.verify"
                job["payload"] = {"snapshot_id": "snapshot-1"}
                verified = execute_job(job, grant)
                self.assertTrue(verified["verified"])
                job["resource_budget"]["storage_bytes"] = 8192
                with self.assertRaises(ValueError):
                    execute_job(job, grant)

    def test_durable_generation_and_idempotency(self):
        with tempfile.TemporaryDirectory() as directory:
            store = AgentStore(Path(directory) / "state.sqlite3")
            grant = {"generation": 3}
            store.save_grant(grant, "owner-public-key")
            self.assertEqual(3, store.get_generation())
            job = {"kind": "native.health_check"}
            self.assertTrue(store.enqueue_once("job-1", "idempotency-0001", job))
            self.assertFalse(store.enqueue_once("job-2", "idempotency-0001", job))
            self.assertTrue(store.claim_job("job-1"))
            self.assertFalse(store.claim_job("job-1"))
            store.complete_job("job-1", {"ok": True})
            self.assertTrue(store.enqueue_once("job-2", "idempotency-0002", job))
            self.assertTrue(store.claim_job("job-2"))
            self.assertEqual(1, store.recover_interrupted_jobs())
            state = store.db.execute("SELECT state FROM jobs WHERE job_id='job-2'").fetchone()[0]
            self.assertEqual("QUEUED", state)
            self.assertEqual(0o600, (Path(directory) / "state.sqlite3").stat().st_mode & 0o777)
            state = store.db.execute("SELECT state FROM jobs WHERE job_id='job-1'").fetchone()[0]
            self.assertEqual("SUCCEEDED", state)
            store.close()


if __name__ == "__main__":
    unittest.main()
