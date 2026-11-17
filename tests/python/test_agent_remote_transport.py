import unittest
from unittest.mock import Mock

from pockethost_agent.remote import ControlPlaneClient, process_command
from pockethost_agent.store import AgentStore


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

    def test_api_path_is_restricted_to_versioned_control_api(self):
        client = ControlPlaneClient("https://control.example.com", "x" * 40)
        with self.assertRaises(ValueError):
            client.request("GET", "//attacker.example/")
        with self.assertRaises(ValueError):
            client.request("GET", "/admin/secrets")

    def test_non_job_commands_are_rejected_and_acknowledged(self):
        client = Mock()
        with __import__("tempfile").TemporaryDirectory() as directory:
            store = AgentStore(__import__("pathlib").Path(directory) / "agent.sqlite3")
            process_command(client, store, "device", {
                "id": "00000000-0000-4000-8000-000000000001",
                "kind": "shell", "payload": {"command": "id"},
            })
            client.acknowledge.assert_called_once_with(
                "00000000-0000-4000-8000-000000000001",
                False, {"error": "unsupported_agent_command"},
            )
            store.close()


if __name__ == "__main__":
    unittest.main()
