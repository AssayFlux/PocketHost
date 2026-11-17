# Pocket Host — Capacity and Operations

## Objective

Keep Android hosting predictable under bounded CPU, memory, storage, network and thermal conditions. Capacity limits are part of product behavior, not hidden implementation details.

## v0.1.0 website capacity

- ZIP archive maximum: 500,000,000 bytes.
- Expanded website maximum: 500,000,000 bytes.
- Maximum files: 5,000.
- Maximum total entries: 10,000.
- Maximum directory depth: 32.
- Rust static response maximum: 50 MiB per file (separate from import limits).
- Rust engine connection and per-site concurrency limits remain independently enforced.

The UI must distinguish import quota from per-file response limit. Reject oversized input before replacing the active site. Avoid reading the whole archive into memory; use streaming counters and bounded buffers.

## Telemetry

Metrics and logs must be bounded, non-blocking and privacy-preserving. Never record passphrases, tunnel tokens, private keys, raw visitor bodies or website source by default.

## Admission and fairness

- Refuse imports/builds when storage headroom is insufficient.
- Keep temporary staging and backup overhead in capacity estimates.
- Do not start optional runtimes when trust acknowledgement is absent.
- Runtime failure must not interrupt static serving.
- Explain battery saver, thermal throttling and OEM background restrictions.

## Failure containment

Import failure preserves active site. Interrupted activation recovers a known-good release. Backup failure does not truncate or corrupt source. Server stop/restart reports actual listener state. Optional ingress/runtime failures remain isolated.

## Later release capacity

v0.1.1 adds Workbench workspace/build/runtime budgets. v0.2.0 adds per-device role budgets, leases, backpressure and distributed scheduling.

## Acceptance

Measure import peak memory, disk usage, request concurrency, sustained serving, battery/thermal behavior, process restart, low-storage handling and log growth. Apply G0–G9.
