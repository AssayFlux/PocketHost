# PocketHost North Star

**Product:** AssayFlux PocketHost  
**Steward:** AssayFlux  
**Developer identity:** Designed by AssayFlux

## The North Star

**Make a personal Android device a practical, secure and understandable home for websites and small web applications—without requiring Termux or a rented traditional web-hosting server.**

PocketHost should make hosting approachable to a website owner while giving an experienced operator meaningful control. The phone is the origin server; the app is the control plane; the Rust engine is the hosting foundation. Public connectivity is provided by a supported tunnel and edge service where available.

This is an engineering ambition, not a promise of unlimited uptime or unlimited capacity. A phone remains subject to Android lifecycle policy, battery, heat, storage, mobile-network conditions and hardware limits. The baseline must remain usable on entry-level 4 GB RAM hardware; higher-end devices may provide more headroom but do not change the minimum support promise.

## Who it serves

1. **Website owner:** wants to publish an existing static site with a guided workflow.
2. **Independent developer:** wants to run small trusted backend applications from a phone.
3. **Technical operator:** wants visibility into domains, requests, runtime health, logs, limits and recovery.

The first release optimizes for the website owner, not for advanced server administrators. The compatibility baseline is Android 8.0 (API 26)+ on smartphones with at least 4 GB RAM, supporting ARMv7 and ARM64 without Termux.

## The core promise

Import a website → validate it → preview it locally → connect a domain/tunnel → publish → monitor → roll back safely.

A user should never need to understand ports, process supervisors or shell commands to complete the basic static-site workflow.

## Product principles

### 1. Native first
The primary product is a native Android app built with Kotlin and Jetpack Compose. No Termux dependency. Android permissions, storage access, notifications and service lifecycle must use supported Android APIs.

### 2. One engine, clear boundaries
Rust owns hosting and network request execution. Kotlin owns Android integration and the native interface. TypeScript owns the optional browser management console and typed web tooling. Python and Node.js are optional application runtimes—not dependencies of static hosting.

### 3. Static hosting before runtimes
The smallest, most reliable useful product is a static website server. Python and Node.js add significant packaging, compatibility, memory and security costs. They must not block the first usable release.

### 4. Security is a product feature
Default-deny administration, protected credentials, strict file boundaries, safe deployments, bounded resource use, redacted logs and explicit consent before executing user code.

### 5. Honest connectivity
A tunnel can avoid inbound port-forwarding and carrier CGNAT constraints, but the tunnel provider still supplies edge infrastructure. Android compatibility must be demonstrated on-device. Never label a site public until an external-network test succeeds.

### 6. Device-aware reliability
Show service state, resource pressure and reasons for failure. Recover safely from network changes and process restarts where Android permits. Never promise dedicated-server uptime from a phone.

### 7. Reversible operations
Deployments are versioned. A failed import or deployment must not destroy the currently active release. Backups and rollback are first-class operations.

### 8. Expand without rewriting
Keep the Rust core reusable outside Android. Version the Kotlin/Rust bridge and management API. Use shared schemas and explicit module contracts.

## What success looks like

A first-time user can:

- Import a static website from a folder or ZIP.
- See validation errors before publishing.
- Preview the website locally.
- Start and stop hosting with clear status.
- Publish through a verified tunnel and configured domain.
- Check basic traffic and logs.
- Update the site and roll back to a previous deployment.
- Understand when the phone, network or provider prevents availability.

An advanced user can:

- Manage multiple sites and hostnames.
- Configure resource and request limits.
- Inspect structured logs and health metrics.
- Enable a trusted Python or Node application when supported.
- Export configuration and restore backups.

## Non-goals for the initial release

- Replacing enterprise or high-availability hosting.
- Guaranteed 24/7 uptime.
- Unlimited traffic, bandwidth, concurrent connections or runtime workloads.
- Public multi-tenant hosting for untrusted users.
- A general-purpose Linux server or container platform.
- Running arbitrary uploaded code without explicit trust and security review.
- Exposing the PocketHost administration API as part of a public website.

## Decision filter

When evaluating a feature, ask:

1. Does it make the basic website workflow simpler, safer or more reliable?
2. Can it run within the target device's memory, CPU, thermal and storage budget?
3. Does it preserve the separation between public site traffic and administration?
4. Can it be tested on a real Android device?
5. Does it keep the core engine reusable and avoid unnecessary dependencies?

If the answer is no, defer it or redesign it.

## Product identity

- Name: **AssayFlux PocketHost**
- Package ID: `com.assayflux.pockethost`
- Rust core: `pockethost-core`
- Companion browser console: **PocketHost Console**
- Initial development version target: `0.1.0`
- Brand line: **Your website. Your device. Your control.**
