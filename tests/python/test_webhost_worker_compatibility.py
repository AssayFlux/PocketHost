from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class WebHostRuntimeCompatibilityTests(unittest.TestCase):
    def test_worker_allowlists_the_new_webhost_application_id(self):
        service = (ROOT / "runtime-worker/src/main/java/com/assayflux/pockethost/worker/RuntimeWorkerService.kt").read_text()
        manifest = (ROOT / "runtime-worker/src/main/AndroidManifest.xml").read_text()
        self.assertIn('"com.assayflux.webhost"', service)
        self.assertIn('android:name="com.assayflux.webhost"', manifest)

    def test_worker_preserves_runtime_owned_cms_state(self):
        service = (ROOT / "runtime-worker/src/main/java/com/assayflux/pockethost/worker/RuntimeWorkerService.kt").read_text()
        self.assertIn("private fun preserveRuntimeState", service)
        self.assertIn("if (destination.exists()) preserveRuntimeState(destination, staging)", service)
        self.assertIn("MAX_PERSISTENT_STATE_BYTES = 512L * 1024L * 1024L", service)

    def test_worker_exposes_exact_python_project_status_transaction(self):
        service = (ROOT / "runtime-worker/src/main/java/com/assayflux/pockethost/worker/RuntimeWorkerService.kt").read_text()
        self.assertIn("TRANSACTION_PYTHON_PROJECT_STATUS -> pythonProjectStatus", service)
        self.assertIn("TRANSACTION_PYTHON_PROJECT_STATUS = IBinder.FIRST_CALL_TRANSACTION + 17", service)

    def test_worker_embeds_the_flask_cms_dependency(self):
        gradle = (ROOT / "runtime-worker/build.gradle.kts").read_text()
        self.assertIn('install("Flask==3.1.1")', gradle)


if __name__ == "__main__":
    unittest.main()
