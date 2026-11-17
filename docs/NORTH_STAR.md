# Pocket Host — Product North Star 0.1.0

## Mission

Make personal and community-owned infrastructure approachable: users control a fleet of capable devices from the mobile phone they already carry, while each device independently runs the services assigned to it.

## Product promise

- Android and iOS phones are the primary command centers.
- Capable phones, tablets, computers, servers, VPSs, SBCs and embedded devices can contribute supported host capabilities.
- Microcontrollers contribute bounded, board-verified edge capabilities.
- Websites may contain multiple independently supervised components and runtimes.
- Devices are interconnected through authenticated, scoped and versioned protocols.
- A mobile app, Internet link, remote control plane, tunnel or single workload is never a required single point of failure for unrelated services.

## Five invariants

1. **Phone commands; hosts operate.** The mobile app coordinates and authorizes. Each host locally supervises workloads and persists last-known-good state.
2. **Independent by default.** Separate process, identity, storage, lifecycle, resource budget and recovery for each component where the platform supports it.
3. **Connected by explicit permission.** No implicit trust from LAN, device discovery, same account, runtime package or shared Wi-Fi.
4. **Graceful degradation.** A broken service is contained. Unrelated hosts, sites, runtimes, tunnels and edge devices continue.
5. **Capability-based promises.** A service is offered only when the exact device profile passes compatibility and security verification.

## Command center

Android (Kotlin/Compose) and iOS (Swift/SwiftUI) are first-class mobile control clients using one versioned API and common product interaction model. The phone is the primary owner interface, not the authoritative sole storage of fleet state. An optional remote control plane enables off-LAN management; direct authenticated LAN control supports local operation. The browser console is secondary.

iOS is a control client and must respect Apple's background execution rules. Do not promise an iPhone as an always-on arbitrary application server.

## Device ecosystem

- Full hosts: Android, Linux, Windows, macOS, capable servers/VPS and capable SBCs.
- Compact hosts: embedded Linux and resource-limited SBCs with narrower workload profiles.
- MCU edge nodes: ESP32 and other supported boards for telemetry, device APIs, bounded automation and protocol bridging.
- Constrained nodes: sensors, actuators and event producers.

Raspberry Pi is an SBC. Microcontrollers are not assumed to execute general-purpose web runtimes.

## Interconnection model

Control plane handles identity, enrollment, desired state, authorization, command queue and audit. Data plane handles website traffic, internal service calls, artifact transfer and device telemetry. Public visitor ingress is isolated from management and MCU administration. Local host agents continue their authorized work when remote services are unreachable.

AssayFlux Relay and Cloudflare Tunnel are independent optional visitor ingress providers and may run simultaneously. Local Network hosting is independent of both.

## Service model

A website is a service graph: static frontend, one or more runtime components, data services and optional edge capabilities. Components declare their runtime, architecture, resource needs, persistence, health checks and permissions. The scheduler places them only on eligible hosts. Runtime software may be shared; project execution identity and data are not.

## Failure model

A failure in the mobile app, control plane, host, site, runtime, storage provider, tunnel, edge gateway or MCU must have a documented bounded impact. Each component has health reporting, resource ceilings, timeout/cancellation, restart policy, durable state and rollback. No global restart for a local component failure.

## Release identity

Pocket Host 0.1.0 is the baseline product architecture. See [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) and [MASTER_PLAN.md](MASTER_PLAN.md) for normative boundaries and delivery gates.
