# PocketHost North Star

**Product:** AssayFlux PocketHost  
**Steward:** AssayFlux  
**Developer identity:** Designed by AssayFlux  
**Package:** `com.assayflux.pockethost`

## The North Star

**Make affordable, dependable and understandable self-hosting possible on an Android phone—without Termux, root or a mandatory cloud server.**

PocketHost should become the first choice for people with limited budgets or modest hosting needs: publish a static website, add a small trusted backend when needed, understand the actual capacity of the phone, and let the system protect itself from overload.

The **phone is the website origin**. Rust handles HTTP serving and routing. Kotlin owns Android lifecycle and native controls. TypeScript owns the management console. Python and Node.js are optional trusted application runtimes. Cloudflare Tunnel provides outbound public connectivity. The optional remote control plane is for management only; it is neither the website host nor a prerequisite for local hosting.

The owner already has a domain and connected Cloudflare Tunnel. Reuse the existing named tunnel and verify its Public Hostname origin. Never tell the owner to create a duplicate tunnel.

## Who it serves

1. **Budget-conscious site owner:** needs low-cost static hosting and a simple publishing workflow.
2. **Independent developer:** needs a small trusted Python or Node backend for development or lightweight production workloads.
3. **Technical operator:** needs clear capacity, logs, alerts, controls, deployment history and recovery.
4. **Learning and testing user:** needs a safe way to run a website or backend temporarily from hardware they already own.

## Core product promise

Import → validate → preview → publish → observe → protect → recover.

A first-time user should not need a shell, Termux, root, or deep server knowledge to publish a static website. Advanced users can tune limits and runtimes, but safe defaults remain the default.

## Product principles

### 1. Phone-first hosting
The phone runs the actual website server. The core product must work locally without a VPS, remote control plane or paid cloud service. Optional external providers must be clearly identified.

### 2. Native, no-root operation
Use Kotlin and Jetpack Compose with supported Android APIs. Target Android 8.0 (API 26)+, a 4 GB RAM baseline and ARMv7/ARM64 compatibility. No Termux and no root requirement.

### 3. Rust is the central request engine
Rust owns public HTTP ingress, static serving, hostname routing, reverse proxying, bounded admission, and engine metrics. Kotlin owns Android integration. JNI remains coarse-grained and versioned.

### 4. Static hosting is foundational
Static hosting must work independently of Python, Node.js, remote management and payment integrations. Optional components must not destabilize the static origin.

### 5. Measure before making capacity claims
Do not invent a maximum visitor count. Discover hardware, benchmark representative workloads, calculate sustainable safe capacity and show the workload and test conditions. A measured safe envelope is more useful than a theoretical peak.

### 6. Protect the device automatically
A Resource Governor must adapt to CPU load, memory pressure, thermal state, battery, storage and observed queue/latency. It must bound queues, throttle, reject gracefully and recover with hysteresis. It must never promise to override Android's own process-kill decisions.

### 7. Owner control without unsafe defaults
Provide Simple and Advanced modes, presets and custom settings. Schedules such as 30% capacity for three hours must be first-class. Capacity percentages refer to the calibrated safe budget, not raw CPU utilization.

### 8. Security is not a checkbox
Separate website traffic from management. Protect credentials. Restrict network and filesystem access. Treat Python/Node code as trusted until strong per-project isolation is proven. Use payment providers rather than handling card data. Do not claim PCI compliance without assessment.

### 9. Auditable operations
Keep bounded, redacted operational logs, daily reports, conventional tamper-evident audit records and recoverable backups. Use local hash chaining and signed checkpoints.

### 10. Honest availability
A phone is not a cloud region. Battery, heat, Doze, OEM process management, mobile network, carrier NAT, storage and power outages affect availability. Explain degraded states and never promise unlimited traffic or 24/7 uptime.

### 11. Reversible changes
Use staged deployment, integrity checks, rollback and tested restore. A failed upload or runtime restart must not destroy the last known-good static site.

### 12. Small, testable boundaries
Version API contracts and schemas. Keep static hosting independent from optional runtimes. Make each module observable and testable. Prefer fewer reliable capabilities over a large set of superficial toggles.

### 13. Consistent product interface
The interface should feel modern, calm and operational—not like a generic developer console. Preserve the AssayFlux palette: deep teal `#24434A`, bronze `#B18B5E`, ivory `#F6F4EF` and slate `#657174`. Use clear card hierarchy, restrained bronze highlights, readable metadata and responsive spacing. The native Android dashboard must scroll vertically on compact screens and keep Simple/Advanced modes. The TypeScript console must adapt to mobile widths and clearly show disconnected states rather than invented live data.

## What success looks like

### Simple Mode
- Import a folder or ZIP containing a static site.
- Validate it before activation.
- Preview locally.
- Start/stop hosting with clear status.
- Connect the existing Cloudflare Tunnel and verify the public hostname externally.
- Publish updates and roll back.
- See a concise health/capacity indicator and daily summary.

### Advanced Mode
- Manage multiple sites and hostnames.
- Configure per-site resource budgets and request limits.
- Inspect live metrics, capacity graphs, logs and alerts.
- Run trusted Python WSGI or Node applications with clear limitations.
- Schedule custom capacity profiles, including 30% for three hours.
- Configure backup, retention, remote management and security settings.
- Review payment-provider events and audit records if the payment module is enabled.

## Explicit non-goals

- Replacing enterprise cloud infrastructure or offering cloud-region availability.
- Unlimited bandwidth, traffic, concurrency or guaranteed visitor counts.
- Mandatory VPS or mandatory remote control plane.
- General-purpose Linux, root access, Docker or arbitrary shell hosting.
- Public multi-tenant execution of hostile code without a reviewed sandbox.
- Storing card numbers, CVVs, PINs or payment secrets in website projects.
- Claiming PCI compliance or security certification without independent evidence.

## Decision filter

Before implementing a feature, ask:

1. Does it make self-hosting simpler, safer, more affordable or more reliable?
2. Does it preserve the phone as the actual website origin?
3. Can it run within measured target-device resource budgets?
4. Does it preserve separation between visitor traffic and administration?
5. Does it have a clear threat model and privacy impact?
6. Can it be tested on API 26+ and physical ARMv7/ARM64 hardware?
7. Does it have observable behavior, failure handling and documentation?
8. Is it genuinely needed, or does it add complexity without helping the target user?

If it fails these tests, defer or redesign it.

## Identity

- Name: **AssayFlux PocketHost**
- Developer: **Designed by AssayFlux**
- Package: `com.assayflux.pockethost`
- Core engine: `pockethost-core`
- Browser console: **PocketHost Console**
- Initial version target: `0.1.0`
- Brand line: **Your website. Your device. Your control.**

## Source-of-truth documents

- [Development Context](DEVELOPMENT_CONTEXT.md) — mandatory handoff and contribution rules.
- [Roadmap](ROADMAP.md) — sequence and priorities.
- [Master Plan](MASTER_PLAN.md) — architecture and contracts.
- [Implementation Status](PHASE_0_STATUS.md) — actual evidence, not aspiration.
- [Completion Milestone](COMPLETION_MILESTONE.md) — release definition.
- [Adaptive Capacity, Security, Payments and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md) — detailed new workstream.
