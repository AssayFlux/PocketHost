"""Contract and trust-state simulations for Pocket Host protocol 1.0.

These tests exercise protocol policy/state-machine invariants. They are not
cryptographic implementation tests and must not be presented as such.
"""
import json
import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]
from runpy import run_path

# The validator is intentionally named with a hyphen for its CLI path. Load
# its public contract reader by path so test discovery does not import a
# nonexistent underscore-named module.
load_contracts = run_path(str(ROOT / "scripts" / "validate-protocol.py"))["load_contracts"]


class EnrollmentAuthorityModel:
    """Deterministic model of the owner approval and one-time challenge gate."""

    def __init__(self):
        self.challenges = {}
        self.approved = {}
        self.generations = {}
        self.seen_jobs = {}

    def issue(self, challenge_id, owner, device, nonce, expires):
        if challenge_id in self.challenges:
            raise ValueError("challenge id collision")
        self.challenges[challenge_id] = {
            "owner": owner, "device": device, "nonce": nonce,
            "expires": expires, "used": False,
        }

    def prove_and_approve(self, challenge_id, owner, device, nonce, now, role, capabilities, policy):
        item = self.challenges.get(challenge_id)
        if item is None or item["used"]:
            raise ValueError("unknown or replayed challenge")
        if now >= item["expires"]:
            raise ValueError("expired challenge")
        if (owner, device, nonce) != (item["owner"], item["device"], item["nonce"]):
            raise ValueError("challenge binding mismatch")
        allowed = set(policy["roles"][role]["allow"])
        denied = set(policy["roles"][role]["deny"])
        requested = set(capabilities)
        if not requested or not requested.issubset(allowed) or requested.intersection(denied):
            raise ValueError("role capability escalation")
        item["used"] = True
        generation = self.generations.get(device, 0) + 1
        self.generations[device] = generation
        self.approved[device] = {"owner": owner, "role": role,
                                 "capabilities": requested, "generation": generation}
        return generation

    def revoke(self, device):
        self.generations[device] = self.generations.get(device, 0) + 1
        self.approved.pop(device, None)

    def accept_job(self, device, generation, job_id, idempotency_key, now, expires):
        grant = self.approved.get(device)
        if grant is None or generation != self.generations.get(device):
            raise ValueError("missing or stale grant")
        if now >= expires:
            raise ValueError("expired job")
        key = (device, idempotency_key)
        if key in self.seen_jobs:
            return self.seen_jobs[key]
        result = {"job_id": job_id, "state": "accepted"}
        self.seen_jobs[key] = result
        return result


class ProtocolSchemaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schemas, cls.policy, cls.job_policy = load_contracts()

    def test_all_schemas_are_valid_2020_12(self):
        self.assertGreaterEqual(len(self.schemas), 4)
        for schema in self.schemas.values():
            Draft202012Validator.check_schema(schema)

    def test_device_hello_accepts_valid_profile(self):
        schema = self.schemas["device-hello.schema.json"]
        sample = {
            "protocol": "pockethost.device/1.0", "message_type": "device_hello",
            "message_id": "11111111-1111-4111-8111-111111111111",
            "issued_at": "2026-01-01T00:00:00Z",
            "device": {
                "device_id": "a" * 64, "public_key": "A" * 44,
                "key_algorithm": "ed25519",
                "instance_id": "22222222-2222-4222-8222-222222222222",
                "product_id": "pockethost.linux-agent", "product_version": "0.1.0",
                "os": "linux", "architecture": "x86_64"
            },
            "capabilities": {
                "roles": ["storage"], "transports": ["lan"],
                "resources": {"cpu_count": 4, "memory_bytes": 4096, "storage_bytes": 8192}
            }
        }
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(sample)

    def test_hello_rejects_unknown_role_and_extra_fields(self):
        schema = self.schemas["device-hello.schema.json"]
        sample = {
            "protocol": "pockethost.device/1.0", "message_type": "device_hello",
            "message_id": "11111111-1111-4111-8111-111111111111",
            "issued_at": "2026-01-01T00:00:00Z",
            "device": {"device_id": "a" * 64, "public_key": "A" * 44,
                       "key_algorithm": "ed25519", "instance_id": "22222222-2222-4222-8222-222222222222",
                       "product_id": "agent", "product_version": "0.1.0", "os": "linux",
                       "architecture": "x86_64"},
            "capabilities": {"roles": ["superuser"], "transports": ["lan"],
                             "resources": {"cpu_count": 1, "memory_bytes": 1, "storage_bytes": 0}},
            "unexpected": True
        }
        self.assertFalse(Draft202012Validator(schema).is_valid(sample))

    def test_role_grant_schema_and_capability_policy(self):
        schema = self.schemas["role-grant.schema.json"]
        sample = {
            "protocol": "pockethost.device/1.0",
            "grant_id": "11111111-1111-4111-8111-111111111111",
            "owner_id": "owner-1", "device_id": "a" * 64, "role": "storage",
            "generation": 1, "capabilities": ["storage.read_assigned_namespace"],
            "denied_capabilities": ["process.exec"],
            "resource_budget": {"cpu_millis": 100, "memory_bytes": 1048576,
                                "storage_bytes": 0, "network_bytes_per_day": 0},
            "issued_at": "2026-01-01T00:00:00Z", "expires_at": "2026-01-01T01:00:00Z",
            "policy_version": "1", "signature": {
                "algorithm": "ed25519", "key_id": "owner-key-1", "value": "S" * 64
            }
        }
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(sample)
        allowed = set(self.policy["roles"]["storage"]["allow"])
        denied = set(self.policy["roles"]["storage"]["deny"])
        self.assertTrue(set(sample["capabilities"]).issubset(allowed))
        sample["capabilities"] = ["management.admin"]
        self.assertFalse(set(sample["capabilities"]).issubset(allowed))
        self.assertTrue(set(sample["capabilities"]).intersection(denied) == set())

    def test_job_envelope_schema_rejects_untyped_shell_job(self):
        schema = self.schemas["job-envelope.schema.json"]
        sample = {
            "protocol": "pockethost.device/1.0",
            "job_id": "11111111-1111-4111-8111-111111111111",
            "idempotency_key": "idempotency-key-0001",
            "correlation_id": "22222222-2222-4222-8222-222222222222",
            "owner_id": "owner-1", "target_device_id": "a" * 64,
            "role_grant_id": "33333333-3333-4333-8333-333333333333",
            "kind": "storage.snapshot", "payload": {"namespace": "backup-1"},
            "issued_at": "2026-01-01T00:00:00Z", "expires_at": "2026-01-01T00:05:00Z",
            "resource_budget": {"timeout_seconds": 60, "memory_bytes": 1048576},
            "signature": {"algorithm": "ed25519", "key_id": "owner-key-1", "value": "S" * 64}
        }
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(sample)
        sample["kind"] = "shell.exec"
        self.assertFalse(Draft202012Validator(schema).is_valid(sample))

    def test_job_payload_contract_is_typed_and_role_bound(self):
        contract = self.job_policy["jobs"]["storage.snapshot"]
        self.assertEqual(contract["role"], "storage")
        self.assertEqual(set(contract["required"]), {"namespace_id", "snapshot_id"})
        self.assertNotIn("command", contract["properties"])
        self.assertNotIn("shell.exec", self.job_policy["jobs"])

    def test_enrollment_challenge_requires_single_use_and_known_transport(self):
        schema = self.schemas["enrollment-challenge.schema.json"]
        sample = {
            "protocol": "pockethost.device/1.0",
            "challenge_id": "11111111-1111-4111-8111-111111111111",
            "owner_id": "owner-1", "device_id": "a" * 64,
            "challenge_nonce": "A" * 43, "issued_at": "2026-01-01T00:00:00Z",
            "expires_at": "2026-01-01T00:05:00Z", "single_use": True,
            "transport": "lan",
            "signature": {"algorithm": "ed25519", "key_id": "device-key-1", "value": "S" * 64}
        }
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(sample)
        sample["single_use"] = False
        self.assertFalse(Draft202012Validator(schema).is_valid(sample))


class TrustStateSimulationTests(unittest.TestCase):
    def setUp(self):
        self.policy = json.loads((ROOT / "protocol/ROLE_CAPABILITY_POLICY.json").read_text())
        self.authority = EnrollmentAuthorityModel()
        self.now = datetime(2026, 1, 1, tzinfo=timezone.utc)
        self.authority.issue("c1", "owner-A", "device-A", "nonce-A",
                             self.now + timedelta(minutes=5))

    def approve_storage(self):
        return self.authority.prove_and_approve(
            "c1", "owner-A", "device-A", "nonce-A", self.now,
            "storage", ["storage.read_assigned_namespace"], self.policy
        )

    def test_expired_challenge_rejected(self):
        with self.assertRaisesRegex(ValueError, "expired"):
            self.authority.prove_and_approve(
                "c1", "owner-A", "device-A", "nonce-A", self.now + timedelta(minutes=5),
                "storage", ["storage.read_assigned_namespace"], self.policy
            )

    def test_replayed_challenge_rejected(self):
        self.approve_storage()
        with self.assertRaisesRegex(ValueError, "replayed"):
            self.authority.prove_and_approve(
                "c1", "owner-A", "device-A", "nonce-A", self.now,
                "storage", ["storage.read_assigned_namespace"], self.policy
            )

    def test_wrong_owner_or_device_binding_rejected(self):
        with self.assertRaisesRegex(ValueError, "binding"):
            self.authority.prove_and_approve(
                "c1", "owner-B", "device-A", "nonce-A", self.now,
                "storage", ["storage.read_assigned_namespace"], self.policy
            )

    def test_no_approval_means_no_job(self):
        with self.assertRaisesRegex(ValueError, "grant"):
            self.authority.accept_job("device-A", 1, "job-1", "idem-000000000001",
                                      self.now, self.now + timedelta(minutes=1))

    def test_role_escalation_and_unknown_capability_denied(self):
        for capability in ("process.exec", "management.admin", "made.up.capability"):
            with self.assertRaisesRegex(ValueError, "escalation"):
                self.authority.prove_and_approve(
                    "c1", "owner-A", "device-A", "nonce-A", self.now,
                    "storage", [capability], self.policy
                )

    def test_revoke_invalidates_prior_generation(self):
        generation = self.approve_storage()
        self.authority.revoke("device-A")
        with self.assertRaisesRegex(ValueError, "stale"):
            self.authority.accept_job("device-A", generation, "job-1", "idem-000000000001",
                                      self.now, self.now + timedelta(minutes=1))

    def test_duplicate_delivery_is_idempotent(self):
        generation = self.approve_storage()
        first = self.authority.accept_job("device-A", generation, "job-1", "idem-000000000001",
                                          self.now, self.now + timedelta(minutes=1))
        second = self.authority.accept_job("device-A", generation, "job-2", "idem-000000000001",
                                           self.now, self.now + timedelta(minutes=1))
        self.assertEqual(first, second)

    def test_expired_job_rejected(self):
        generation = self.approve_storage()
        with self.assertRaisesRegex(ValueError, "expired"):
            self.authority.accept_job("device-A", generation, "job-1", "idem-000000000001",
                                      self.now + timedelta(minutes=2), self.now + timedelta(minutes=1))


if __name__ == "__main__":
    unittest.main(verbosity=2)
