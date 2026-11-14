import importlib.util
import pathlib
import socket
import tempfile
import unittest
import urllib.request

MODULE = pathlib.Path(__file__).parents[2] / "app" / "src" / "main" / "python" / "pockethost_runtime.py"
SPEC = importlib.util.spec_from_file_location("pockethost_runtime_tested", MODULE)
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)


class PythonHostRuntimeTest(unittest.TestCase):
    def test_wsgi_app_serves_and_stops(self):
        with tempfile.TemporaryDirectory() as project:
            pathlib.Path(project, "wsgi.py").write_text(
                "def application(environ, start_response):\n"
                "    body = b'PocketHost WSGI test'\n"
                "    start_response('200 OK', [('Content-Type', 'text/plain'), "
                "('Content-Length', str(len(body)))])\n"
                "    return [body]\n",
                encoding="utf-8",
            )
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]

            try:
                self.assertEqual("RUNNING", runtime.start(project, port))
                self.assertEqual("ALREADY_RUNNING", runtime.start(project, port))
                self.assertEqual("RUNNING", runtime.status())
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/", timeout=5) as response:
                    self.assertEqual(200, response.status)
                    self.assertEqual(b"PocketHost WSGI test", response.read())
                with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
                    client.sendall(
                        b"POST / HTTP/1.0\\r\\n"
                        b"Host: 127.0.0.1\\r\\n"
                        + f"Content-Length: {runtime.MAX_REQUEST_BODY_BYTES + 1}\\r\\n".encode()
                        + b"\\r\\n"
                    )
                    response = client.recv(1024)
                self.assertIn(b"413 Payload Too Large", response)
            finally:
                self.assertEqual("STOPPED", runtime.stop())
            self.assertEqual("STOPPED", runtime.status())

    def test_rejects_invalid_port(self):
        with tempfile.TemporaryDirectory() as project:
            with self.assertRaisesRegex(ValueError, "1024"):
                runtime.start(project, 80)

    def test_requires_root_wsgi_entrypoint(self):
        with tempfile.TemporaryDirectory() as project:
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]
            with self.assertRaisesRegex(ValueError, "wsgi.py"):
                runtime.start(project, port)


if __name__ == "__main__":
    unittest.main()
