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
                statuses = []
                limited = runtime._RequestBodyLimit(
                    lambda environ, start_response: self.fail("Oversized request reached the WSGI app")
                )
                body = limited(
                    {"CONTENT_LENGTH": str(runtime.MAX_REQUEST_BODY_BYTES + 1)},
                    lambda status, headers: statuses.append(status),
                )
                self.assertEqual(["413 Payload Too Large"], statuses)
                self.assertEqual([b""], list(body))
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


    def test_request_body_limit_rejects_malformed_and_negative_lengths(self):
        for raw_length in ("not-a-number", "-1"):
            statuses = []
            app = runtime._RequestBodyLimit(
                lambda environ, start_response: self.fail("Invalid request reached the WSGI app")
            )
            response = app(
                {"CONTENT_LENGTH": raw_length},
                lambda status, headers: statuses.append(status),
            )
            self.assertEqual(["400 Bad Request"], statuses)
            self.assertEqual([b""], list(response))

    def test_request_body_limit_allows_exact_limit(self):
        called = []
        expected = [b"accepted"]
        app = runtime._RequestBodyLimit(
            lambda environ, start_response: called.append(True) or expected
        )
        response = app(
            {"CONTENT_LENGTH": str(runtime.MAX_REQUEST_BODY_BYTES)},
            lambda status, headers: None,
        )
        self.assertEqual([True], called)
        self.assertIs(expected, response)

    def test_failed_wsgi_import_does_not_leave_runtime_running(self):
        with tempfile.TemporaryDirectory() as project:
            pathlib.Path(project, "wsgi.py").write_text(
                "application = 42\n", encoding="utf-8"
            )
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]
            with self.assertRaisesRegex(TypeError, "callable"):
                runtime.start(project, port)
            self.assertEqual("STOPPED", runtime.status())
            self.assertNotIn("_pockethost_user_wsgi", __import__("sys").modules)

if __name__ == "__main__":
    unittest.main()
