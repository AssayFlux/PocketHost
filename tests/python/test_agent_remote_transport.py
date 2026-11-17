import base64
import json
import tempfile
import unittest
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import Mock

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from pockethost_agent.remote import ControlPlaneClient, process_command
from pockethost_agent.store import AgentStore
from pockethost_agent.trust import canonical_json, public_key_id


class RemoteTransportTests(unittest.TestCase):
    def test_transport_requires_https_except_loopback(self):
        with self.assertRaises(ValueError):
            ControlPlaneClient("http://control.example.com", "x" * 40)
        with self.assertRaises(ValueError):
            ControlPlaneClient("https://user:pass@control.example.com", "x" * 40)
        with self.assertRaises(ValueError):
            ControlPlaneClient("https://control.example.com/path?token=x", "x" * 40)
        self.assertEqual(
            "http://127.0.0.1:8080",
            ControlPlaneClient("http://127.0.0.1:8080", "x" * 40).endpoint,
        )

    def test_transport_requires_long_token_and_bounded_timeout(self):
        with self.assertRaises(ValueError):
            ControlPlaneClient("https://control.example.com", "short")
        with self.assertRaises(ValueError):
            ControlPlaneClient("https://control.example.com", "x" * 40, timeout=90)
        with self.assertRaises(ValueError):
            ControlPlaneClient("https://control.example.com", "x" * 20 + "\n" + "x" * 20)

    def test_api_path_is_restricted_to_versioned_control_api(self):
        client = ControlPlaneClient("https://control.example.com", "x" * 40)
        with self.assertRaises(ValueError):
            client.request("GET", "//attacker.example/")
        with self.assertRaises(ValueError):
            client.request("GET", "/admin/secrets")

    def test_non_job_commands_are_rejected_and_acknowledged(self):
        client = Mock()
        with tempfile.TemporaryDirectory() as directory:
            store = AgentStore(Path(directory) / "agent.sqlite3")
            process_command(client, store, "device", {
                "id": "00000000-0000-4000-8000-000000000001",
                "kind": "shell", "payload": {"command": "id"},
            })
            client.acknowledge.assert_called_once_with(
                "00000000-0000-4000-8000-000000000001",
                False, {"error": "unsupported_agent_command"},
            )
            store.close()

    def test_signed_remote_job_executes_once_and_replay_returns_saved_result(self):
        with tempfile.TemporaryDirectory() as directory:
            device_id = "a" * 64
            owner = ec.generate_private_key(ec.SECP256R1())
            owner_der = owner.public_key().public_bytes(
                serialization.Encoding.DER,
                serialization.PublicFormat.SubjectPublicKeyInfo,
            )
            owner_id = public_key_id(owner_der)
            now = datetime.now(timezone.utc)
            stamp = lambda value: value.isoformat().replace("+00:00", "Z")
            grant = {
                "protocol": "pockethost.device/1.0",
                "grant_id": str(uuid.uuid4()),
                "owner_id": owner_id,
                "device_id": device_id,
                "role": "native_job",
                "generation": 1,
                "capabilities": ["native_job.report_health"],
                "denied_capabilities": [],
                "resource_budget": {
                    "cpu_millis": 100, "memory_bytes": 8 * 1024 * 1024,
                    "storage_bytes": 0, "network_bytes_per_day": 0,
                },
                "issued_at": stamp(now - timedelta(seconds=2)),
                "expires_at": stamp(now + timedelta(hours=1)),
                "policy_version": "1.0",
            }
            grant_signature = owner.sign(canonical_json(grant), ec.ECDSA(hashes.SHA256()))
            grant["signature"] = {
                "algorithm": "ecdsa-p256-sha256",
                "key_id": owner_id,
                "value": base64.b64encode(grant_signature).decode("ascii"),
            }
            store = AgentStore(Path(directory) / "agent.sqlite3")
            store.save_grant(grant, base64.b64encode(owner_der).decode("ascii"))
            envelope = {
                "protocol": "pockethost.device/1.0",
                "owner_id": owner_id,
                "target_device_id": device_id,
                "role_grant_id": grant["grant_id"],
                "job_id": str(uuid.uuid4()),
                "correlation_id": str(uuid.uuid4()),
                "idempotency_key": "health-check-idempotency-001",
                "kind": "native.health_check",
                "issued_at": stamp(now - timedelta(seconds=1)),
                "expires_at": stamp(now + timedelta(minutes=5)),
                "resource_budget": {"timeout_seconds": 30, "memory_bytes": 1024 * 1024},
                "payload": {"check_id": "remote-ci-check"},
            }
            job_signature = owner.sign(canonical_json(envelope), ec.ECDSA(hashes.SHA256()))
            envelope["signature"] = {
                "algorithm": "ecdsa-p256-sha256",
                "key_id": owner_id,
                "value": base64.b64encode(job_signature).decode("ascii"),
            }
            command = {
                "id": str(uuid.uuid4()),
                "kind": "agent_job",
                "payload": envelope,
            }
            client = Mock()
            process_command(client, store, device_id, command)
            process_command(client, store, device_id, command)
            self.assertEqual(2, client.acknowledge.call_count)
            first = client.acknowledge.call_args_list[0].args
            second = client.acknowledge.call_args_list[1].args
            self.assertTrue(first[1])
            self.assertTrue(second[1])
            self.assertEqual("healthy", first[2]["status"])
            self.assertEqual(first[2], second[2])
            row = store.db.execute(
                "SELECT state FROM jobs WHERE job_id=?", (envelope["job_id"],)
            ).fetchone()
            self.assertEqual("SUCCEEDED", row[0])
            store.close()

    def test_owner_signed_role_grant_is_installed_only_for_matching_device(self):
        with tempfile.TemporaryDirectory() as directory:
            device_id = "b" * 64
            owner = ec.generate_private_key(ec.SECP256R1())
            owner_der = owner.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
            owner_id = public_key_id(owner_der)
            now = datetime.now(timezone.utc)
            stamp = lambda value: value.isoformat().replace("+00:00", "Z")
            grant = {
                "protocol": "pockethost.device/1.0", "grant_id": str(uuid.uuid4()),
                "owner_id": owner_id, "device_id": device_id, "role": "native_job",
                "generation": 1, "capabilities": ["native_job.report_health"],
                "denied_capabilities": [],
                "resource_budget": {"cpu_millis": 100, "memory_bytes": 8388608, "storage_bytes": 0, "network_bytes_per_day": 0, "allow_battery_operation": False},
                "issued_at": stamp(now - timedelta(seconds=1)),
                "expires_at": stamp(now + timedelta(hours=1)), "policy_version": "1.0",
            }
            grant_sig = owner.sign(canonical_json(grant), ec.ECDSA(hashes.SHA256()))
            grant["signature"] = {"algorithm": "ecdsa-p256-sha256", "key_id": owner_id, "value": base64.b64encode(grant_sig).decode("ascii")}
            client = Mock()
            store = AgentStore(Path(directory) / "agent.sqlite3")
            process_command(client, store, device_id, {"id": str(uuid.uuid4()), "kind": "install_grant", "payload": {"grant": grant, "owner_public_key_spki": base64.b64encode(owner_der).decode("ascii")}})
            self.assertTrue(client.acknowledge.call_args.args[1])
            self.assertEqual(1, store.get_generation())
            self.assertEqual(grant["grant_id"], json.loads(store.db.execute("SELECT value FROM agent_state WHERE key='grant'").fetchone()[0])["grant_id"])
            client.reset_mock()
            process_command(client, store, device_id, {"id": str(uuid.uuid4()), "kind": "install_grant", "payload": {"grant": grant, "owner_public_key_spki": base64.b64encode(owner_der).decode("ascii")}})
            self.assertTrue(client.acknowledge.call_args.args[1])
            self.assertEqual("grant_already_installed", client.acknowledge.call_args.args[2]["status"])
            wrong = dict(grant)
            wrong["generation"] = 2
            wrong["device_id"] = "c" * 64
            client.reset_mock()
            process_command(client, store, device_id, {"id": str(uuid.uuid4()), "kind": "install_grant", "payload": {"grant": wrong, "owner_public_key_spki": base64.b64encode(owner_der).decode("ascii")}})
            self.assertFalse(client.acknowledge.call_args.args[1])
            self.assertEqual(1, store.get_generation())
            store.close()

    def test_invalid_owner_signature_is_never_enqueued(self):
        client = Mock()
        with tempfile.TemporaryDirectory() as directory:
            store = AgentStore(Path(directory) / "agent.sqlite3")
            command = {
                "id": str(uuid.uuid4()),
                "kind": "agent_job",
                "payload": {
                    "job_id": str(uuid.uuid4()),
                    "idempotency_key": "idempotency-key-invalid-001",
                    "signature": {"algorithm": "ecdsa-p256-sha256", "key_id": "wrong", "value": "AA=="},
                },
            }
            process_command(client, store, "device", command)
            client.acknowledge.assert_called_once()
            self.assertFalse(client.acknowledge.call_args.args[1])
            self.assertEqual(0, store.db.execute("SELECT count(*) FROM jobs").fetchone()[0])
            store.close()


if __name__ == "__main__":
    unittest.main()
