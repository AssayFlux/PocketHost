"""PocketHost embedded Python WSGI host.

A project must provide wsgi.py with a callable named application.
This runtime intentionally binds to loopback unless LAN access is explicitly enabled.
Imported project code is untrusted executable code and runs with the app's privileges.
"""
from __future__ import annotations

import importlib.util
import os
import sys
import threading
from socketserver import ThreadingMixIn
from wsgiref.simple_server import WSGIRequestHandler, WSGIServer, make_server

_lock = threading.RLock()
_server = None
_thread = None
_module_name = None


class _BoundedThreadingWSGIServer(ThreadingMixIn, WSGIServer):
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 32
    max_workers = 16

    def __init__(self, *args, **kwargs):
        self._slots = threading.BoundedSemaphore(self.max_workers)
        super().__init__(*args, **kwargs)

    def process_request(self, request, client_address):
        if not self._slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except BaseException:
            self._slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self._slots.release()


class _QuietRequestHandler(WSGIRequestHandler):
    def log_message(self, format, *args):
        # Request logging is handled by PocketHost's Android diagnostics layer.
        return


def _load_application(project_root: str):
    root = os.path.realpath(project_root)
    entry = os.path.join(root, "wsgi.py")
    if not os.path.isdir(root):
        raise ValueError("Python project directory does not exist.")
    if not os.path.isfile(entry):
        raise ValueError("Python project must contain wsgi.py.")
    if root not in sys.path:
        sys.path.insert(0, root)

    name = "_pockethost_user_wsgi"
    previous = sys.modules.pop(name, None)
    spec = importlib.util.spec_from_file_location(name, entry)
    if spec is None or spec.loader is None:
        raise ImportError("Unable to load wsgi.py.")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    try:
        spec.loader.exec_module(module)
        application = getattr(module, "application", None)
        if not callable(application):
            raise TypeError("wsgi.py must expose a callable named application.")
        return application, name
    except BaseException:
        sys.modules.pop(name, None)
        if previous is not None:
            sys.modules[name] = previous
        raise


def start(project_root: str, port: int, allow_lan: bool = False) -> str:
    global _server, _thread, _module_name
    port = int(port)
    if port < 1024 or port > 65535:
        raise ValueError("Python port must be between 1024 and 65535.")
    with _lock:
        if _server is not None:
            return "ALREADY_RUNNING"
        application, module_name = _load_application(project_root)
        host = "0.0.0.0" if bool(allow_lan) else "127.0.0.1"
        server = make_server(
            host,
            port,
            application,
            server_class=_BoundedThreadingWSGIServer,
            handler_class=_QuietRequestHandler,
        )
        thread = threading.Thread(
            target=server.serve_forever,
            kwargs={"poll_interval": 0.25},
            name="PocketHost-Python-WSGI",
            daemon=True,
        )
        try:
            thread.start()
        except BaseException:
            server.server_close()
            sys.modules.pop(module_name, None)
            raise
        _server, _thread, _module_name = server, thread, module_name
        return "RUNNING"


def stop() -> str:
    global _server, _thread, _module_name
    with _lock:
        server, thread, module_name = _server, _thread, _module_name
        if server is None:
            return "STOPPED"
        _server = _thread = _module_name = None
    server.shutdown()
    server.server_close()
    if thread is not None and thread is not threading.current_thread():
        thread.join(timeout=3.0)
    if module_name:
        sys.modules.pop(module_name, None)
    return "STOPPED"


def status() -> str:
    with _lock:
        return "RUNNING" if _server is not None and _thread is not None and _thread.is_alive() else "STOPPED"
