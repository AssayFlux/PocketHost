"""Minimal PocketHost WSGI starter application."""


def application(environ, start_response):
    body = (
        b"<!doctype html><html lang='en'><meta charset='utf-8'>"
        b"<meta name='viewport' content='width=device-width,initial-scale=1'>"
        b"<title>PocketHost Python</title>"
        b"<body><main><h1>Hello from PocketHost</h1>"
        b"<p>Your Python WSGI application is running.</p></main></body></html>"
    )
    start_response(
        "200 OK",
        [
            ("Content-Type", "text/html; charset=utf-8"),
            ("Content-Length", str(len(body))),
            ("X-Content-Type-Options", "nosniff"),
        ],
    )
    return [body]
