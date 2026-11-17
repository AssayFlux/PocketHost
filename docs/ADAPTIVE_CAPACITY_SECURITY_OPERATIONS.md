# Pocket Host 0.1.0 — Capacity and Operations

## Objective

Every host advertises a conservative safe operating envelope based on measured hardware, workload and live health. The mobile command center observes and configures limits; the host agent enforces them locally even while disconnected.

## Capability profile

Record model, OS/firmware, architecture, CPU, memory, storage, filesystem, accelerators, network, power source, battery, thermal and lifecycle constraints where available. Unknown measurements remain unknown.

## Telemetry

Each metric includes timestamp, source, sample interval, validity and host identity. Relevant signals include:
- CPU and memory pressure.
- Storage headroom, I/O and inode/file-count pressure.
- Temperature, thermal throttling, battery and power state.
- Request throughput, latency percentiles, errors and active connections.
- Per-component CPU/memory, queue depth, restarts and health.
- Network RTT, loss, bandwidth observations and provider state.
- Runtime and ingress health.

Telemetry storage is bounded. Metrics collection must not block request handling or become a required runtime dependency.

## Admission and fairness

- Enforce per-host, per-site and per-component concurrency and resource budgets.
- Reserve capacity for host supervisor and management recovery.
- Apply backpressure and explicit overload responses.
- Use queue and connection limits; never allow unbounded buffering.
- Derate on heat, low memory, low battery, storage pressure or network degradation.
- Apply per-tenant fairness and avoid one component starving sibling services.
- Use circuit breakers and bounded restart backoff.
- Drain before planned updates or host removal.

## Failure containment

A component exceeding its budget is throttled, restarted or stopped according to its policy. The supervisor must not restart the entire host for a local component failure. Telemetry outage must not disable the service. Control-plane outage must not disable local admission control.

## Benchmarking

Capacity tests are opt-in, bounded and interruptible. Record workload profile, device/OS, software versions, throughput, p50/p95/p99 latency, errors, CPU, memory, thermal and energy impact. Distinguish observed peak, sustainable safe capacity and current admission budget. Do not publish a universal request count.

## Acceptance

Test sustained load, overload, slow clients, cancellation, large responses, memory pressure, thermal derating, low battery, storage exhaustion, telemetry outage, control-plane outage, component crash and host reboot. Demonstrate that unrelated components remain healthy.
