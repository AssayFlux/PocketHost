import base64
import json
import tempfile
import unittest
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from pockethost_agent.trust import canonical_json, load_or_create_identity, public_key_id, verify_role_grant
from pockethost_agent.store import AgentStore
from pockethost_agent.runner import execute_job, validate_job

POLICY = json.loads((Path(__file__).resolve().parents[2] / "protocol" / "ROLE_CAPABILITY_POLICY.json").read_text())


class LinuxAgentTests(unittest.TestCase):
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
        return grant, base64.b64encode(owner_der).decode()

    def test_grant_signature_and_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            _, device_id, _ = load_or_create_identity(Path(directory))
            grant, owner_key = self.make_grant(device_id)
            verify_role_grant(grant, owner_key, device_id, 1, POLICY)
            grant["capabilities"].append("management.admin")
            with self.assertRaises(ValueError):
                verify_role_grant(grant, owner_key, device_id, 1, POLICY)

    def test_target_and_generation_are_enforced(self):
        with tempfile.TemporaryDirectory() as directory:
            _, device_id, _ = load_or_create_identity(Path(directory))
            grant, owner_key = self.make_grant(device_id)
            with self.assertRaises(ValueError):
                verify_role_grant(grant, owner_key, "0" * 64, 1, POLICY)
            with self.assertRaises(ValueError):
                verify_role_grant(grant, owner_key, device_id, 2, POLICY)

    def test_typed_job_default_deny(self):
        grant = {"grant_id": "g", "owner_id": "o", "device_id": "d", "role": "native_job"}
        job = {"protocol": "pockethost.device/1.0", "target_device_id": "d", "role_grant_id": "g",
               "owner_id": "o", "kind": "native.health_check", "issued_at": "2026-11-17T00:00:00Z",
               "expires_at": "2026-11-17T00:05:00Z", "payload": {"check_id": "node-1"}}
        self.assertEqual("healthy", execute_job(job, grant)["status"])
        job["kind"] = "process.exec"
        with self.assertRaises(ValueError):
            validate_job(job, grant)

    def test_durable_generation_and_idempotency(self):
        with tempfile.TemporaryDirectory() as directory:
            store = AgentStore(Path(directory) / "state.sqlite3")
            grant = {"generation": 3}
            store.save_grant(grant)
            self.assertEqual(3, store.get_generation())
            job = {"kind": "native.health_check"}
            self.assertTrue(store.enqueue_once("job-1", "idempotency-0001", job))
            self.assertFalse(store.enqueue_once("job-2", "idempotency-0001", job))
            store.close()


if __name__ == "__main__":
    unittest.main()
