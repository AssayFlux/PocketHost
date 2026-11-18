# Pocket Host — Product North Star

## Purpose

Make personal infrastructure approachable and owner-controlled. A user starts by serving a real website from an Android phone, then gains a safe in-app development workflow and eventually a trusted network of separately installed devices.

## Product promises

1. The owner controls the phone's website-serving service.
2. Website serving works in both mobile modes.
3. Static websites do not require optional runtimes.
4. Local hosting remains useful without public ingress or the remote control plane.
5. Every external device is explicitly verified and granted only bounded roles.
6. Draft development never silently changes the live website.
7. Optional runtimes remain modular and independently managed.
8. Product claims are supported by G0–G9 evidence, not source presence.

## Three-release journey

- **v0.1.0 — First Hosting:** import a static website (500 MB compressed ZIP and 500 MB expanded site), serve locally and over Wi-Fi, and publish it through Cloudflare Tunnel or an owner-operated AssayFlux Relay Node on the owner's domain over HTTP/HTTPS. The v0.1.0 Relay Node runs on the owner's owner-controlled Android hardware running Pocket Host; Linux-native installation is v0.1.1. A paid VPS is not required, but public IPv4/IPv6 reachability is.
- **v0.1.1 — Workbench, Runtime & Linux Relay Installation:** edit, preview, diff, commit and publish safely; complete optional Python/Node integrations.
- **v0.1.2 — Connected Device Fabric:** enroll other devices, assign roles, coordinate work and scale Relay across multiple hosts/gateways with advanced failover.

The roadmap can expand after these first three versions.

## Mobile operating model

| Mode | Website server | Fleet administration |
|---|---|---|
| Server only | On | Off |
| Command Center + Server | On | On |

No standalone Command Center-only mode exists. Switching modes must not silently cancel already accepted autonomous work. v0.1.0 release acceptance includes local/LAN/public website serving; full fleet operation and multi-host Relay scaling are v0.1.2.

## Device fabric

A device may contribute storage, backup, traffic, build/publish preparation, native jobs or an edge-gateway role only after explicit owner approval. Each role has its own credentials, namespace, limits, health and revocation behavior. Unknown capabilities are treated as unsupported.

## Runtime placement

Static content is the first default. Python 3.11 and Node.js 24.22.1 already have implementations in the separate Android runtime-worker companion APK; they are in progress and assigned to v0.1.1 acceptance. They share the companion UID and are not yet a hardened hostile-code sandbox. The future 20-language Runtime Library is later expansion.

## Design promise

Pocket Host Design Language V1 remains the visual identity: elegant calligraphic brand moments, refined readable typography, minimal monochrome icons, semantic state colors, classic palettes and System/Light/Dark appearance. The roadmap is not a redesign.

## Success definition

A user imports a site and opens it from another device on the same Wi-Fi. The app clearly shows host state and respects Android permissions and lifecycle constraints. The team can reproduce the result from a named APK artifact and device test record. All G0–G9 gates pass before the version is released.
