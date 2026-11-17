# Python Runtime Provider — Pocket Host 0.1.0

## Component contract

A Python website component declares its Python version, architecture, entry point, dependencies, resource budget, health check, persistent data and required capabilities.

The Android provider currently integrates CPython 3.11 through Chaquopy in the runtime companion. Its WSGI adapter expects a root `wsgi.py` exporting callable `application(environ, start_response)`. Dependencies are bundled; arbitrary on-device package installation is not implied.

## Request path

Visitor -> selected ingress provider -> Rust website ingress -> site router -> authenticated component listener -> Python WSGI -> Rust -> visitor.

The Python component must not expose a public listener or management route.

## Isolation

The Android companion currently provides a host-versus-companion package boundary, not a distinct OS identity per project. Python projects may share the companion UID/interpreter. Run only owner-controlled trusted code until per-project OS isolation and egress enforcement are proven.

Other host platforms require their own Python provider adapter and security profile. Runtime versions and wheels must match OS, architecture, ABI and libc/toolchain.

## Failure behavior

- A Python component crash affects only that component.
- Static content and unrelated runtimes remain available.
- Health checks, request limits, memory/CPU budgets and restart backoff are host-supervised.
- Updates stage a new component release and roll back on failed health checks.
