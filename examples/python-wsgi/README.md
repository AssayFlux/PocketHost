# PocketHost Python WSGI starter

This folder is a minimal example project for PocketHost's embedded CPython 3.11 runtime.

1. Copy this folder to your Android device.
2. In PocketHost, choose **Import Python WSGI project** and select this folder.
3. Choose a Python port different from the static website port (default: 8081).
4. Start the Python backend.
5. Open `http://127.0.0.1:8081` on the phone.

The project must contain a root-level `wsgi.py` which exports a callable named `application`.

## Security

Python code runs inside the PocketHost app process with the app's privileges. Import only code you trust. The runtime binds to loopback by default. Enabling LAN hosting exposes this backend to devices on the local network without visitor authentication; use it only on a trusted network.

The embedded runtime currently provides CPython 3.11. Native third-party packages require Android-compatible wheels and are not installed automatically from a project's `requirements.txt`.
