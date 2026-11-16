# POCKET HOST — INTEGRATION AND RELEASE ACCEPTANCE

Use PASS, FAIL or NOT TESTED for every check. Record exact commit SHA, workflow, API level, test count, failure count, skips and artifact identifiers. Never infer a pass from source presence.

## 1. CI evidence

- Quality: `.github/workflows/quality.yml`.
- Android integration: `.github/workflows/android-integration.yml`.
- Release: `.github/workflows/release.yml`.

Quality and Android integration are separate. Exact-SHA API 26/33/35 evidence is required for the release candidate.

## 2. Runtime migration and isolation

- Install matching host and companion APKs.
- Verify Binder signature/caller/protocol checks.
- Test transfer bounds, corrupt archive, interruption and rollback.
- Start Python WSGI and Node app; route real HTTP through Rust.
- Kill/restart companion and runtime; static origin must remain available.
- Gate 2 requires distinct per-project isolated UIDs, filesystem denial, OS-enforced egress denial and adversarial native tests. Companion UID separation alone is not a pass.

## 3. Website and local management

- Import valid/invalid folder and ZIP.
- Test GET, HEAD, MIME, ranges, large streams, SPA fallback and unknown host.
- Test hostname duplicates, malformed host, traversal, encoded separators and symlinks.
- Pair local console with explicit app approval.
- Test invalid/expired pairing, rate limits, session revocation, CORS and PNA.
- Confirm management API remains loopback-only and inaccessible from every public hostname.

## 4. Cloudflare external verification

Reuse existing named tunnel. Verify token storage/redaction, Rust-only origin, valid external TLS and expected response from another network. Test unknown host, private-site Access, Wi-Fi/mobile transition, screen-off and connector restart.

## 5. AssayFlux Relay (future gate)

Not applicable until implemented. Then test enrollment, mutual authentication, domain ownership, stream routing, revocation, visitor modes, network transition, backpressure, rate limits, no phone-IP exposure to ordinary visitors, no management route and independent security review.

## 6. Capacity and operations

Test cached/uncached assets, response sizes, keep-alive, runtime profiles, tunnel overhead, slow clients, cancellation, overload and graceful drain. Record throughput, p50/p95/p99, errors, queue depth, memory, CPU, temperature and battery. Fault-test backup corruption, wrong passphrase, interruption, low storage and rollback.

## 7. Release

Verify signed host/companion APKs, matching signer, package IDs, version, minSdk, ABIs, checksums, upgrades and downloaded artifact integrity. Physical ARMv7/ARM64 acceptance and independent security review are separate mandatory gates.
