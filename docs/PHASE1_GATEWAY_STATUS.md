# Phase 1 — Unified hosting lifecycle and gateway status

Branch: `Alpha`

## Implemented

- `SiteRegistry` remains the authoritative persistent hostname-to-site registry for static, Python WSGI and Node workloads.
- `HostingGateway` is the lifecycle boundary used by `HostingService`; the existing Rust engine remains the single HTTP listener and Host-based router.
- Gateway startup reconciles the route table, bind mode and port. A SHA-256 digest is persisted for change detection; visitor credential hashes are not persisted in the digest preference.
- Explicit bind modes are `loopback`, `lan` and `public-origin`. Legacy `allow_lan` settings are mapped for compatibility.
- Public-origin startup fails closed unless owner consent, successful network diagnostics and HTTPS/certificate readiness are all recorded. The native JNI boundary independently rejects public-origin requests without its explicit consent argument.
- The Rust proxy returns 502 for an unavailable runtime and 504 for an upstream timeout; a failing upstream does not stop the listener or unrelated hostname routes.
- Existing Android runtime supervisors remain independent. Python and Node start/restart failures are logged separately.
- The management API remains bound to 127.0.0.1 and is not routed through the public website gateway.
- Existing Rust graceful shutdown drains active connections for a bounded interval.
- Certificate validity, expiry, SAN coverage and private-key matching checks were extracted into reusable `TlsCertificateLifecycle` and wired back into Native Relay. Tests cover loopback, missing and malformed certificate cases.

## Deliberate security boundaries

- The current Native Relay ACME issuer is HTTP-01 and requires public TCP/80 reachability. It is **not** reused as the direct-origin issuer because that assumption is unsuitable for many Android/mobile networks.
- Direct-origin ACME DNS-01, certificate persistence for the direct gateway and HTTPS listener integration remain Phase 2 work.
- Public-origin binding remains disabled until HTTPS and diagnostics are ready. A successful HTTP listener start is not represented as public hosting success.
- This gateway implementation currently binds IPv4. IPv6 listener support and per-family reachability are part of Phase 3.
- Cloudflare Tunnel and Native Relay remain separate legacy/optional systems; neither is required by the direct-origin gateway.

## Verification added or retained

- Android instrumentation: unified gateway routes two static hostnames independently.
- Android instrumentation: invalid bind modes and public-origin consent are rejected.
- Android instrumentation: public-origin requires diagnostics and HTTPS readiness.
- Android instrumentation: reusable certificate lifecycle checks fail closed.
- Rust test: multiple static hosts remain isolated; an unavailable runtime route returns 502 while static sites continue serving.
- Existing Python multi-site and Node runtime smoke tests remain in the CI matrix.

## Phase 1 exit gate

Phase 1 implementation is subject to the Android lint/unit tests, Rust tests, native Android ABI build and emulator instrumentation on the exact Alpha head. Do not treat the phase as verified until those workflows complete successfully. External direct-origin reachability, DNS-01 issuance, TLS handshakes and dual-stack public publishing are intentionally not Phase 1 claims.
