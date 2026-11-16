# POCKET HOST — LEARN GUIDE

This guide explains the product as it exists and clearly labels planned capabilities. Read `docs/NORTH_STAR.md` for the full primary-version sequence.

## 1. What Pocket Host does

Pocket Host runs a website engine on your Android phone. Rust serves static website files and routes configured hostnames. Optional Python and Node runtimes can host small applications. The phone is the website origin; an external tunnel is only a way for visitors to reach it.

Pocket Host does not require root or Termux. Android power, thermal, memory, carrier-network and OEM background restrictions still apply.

## 2. Static website publishing

1. Build your website using its usual tools.
2. Find the production output folder containing root `index.html`.
3. Open Sites and import the folder or a ZIP.
4. Let Pocket Host validate file paths, size and site structure.
5. Preview locally.
6. Configure hostname and publish.
7. If public, choose a configured tunnel and verify HTTPS from another network.
8. Back up before replacing a working deployment.

Static hosting is the foundational capability and must remain independent of optional runtimes and remote services.

## 3. Networking choices

- **Local Network:** access from devices on the same trusted LAN; no public tunnel.
- **Cloudflare Tunnel:** reuse the owner's existing named tunnel and route its Public Hostname to the Rust website listener.
- **AssayFlux Relay:** an initial bounded HTTP path exists in source, but it is not deployed or available as a production connector yet. It uses an outbound authenticated phone stream and verified hostnames; WebSocket/streaming and deployment validation remain open.
- **Direct Hosting:** advanced owner-managed exposure; not a default recommendation.

A tunnel process showing ready does not prove public website reachability. Test from a separate network. Never expose the management API through a public hostname.

## 4. Python and Node

Python expects root `wsgi.py` with callable `application`. Node expects a self-contained project with prebundled dependencies and a server entry. Automatic pip/npm installation and arbitrary shell workflows are not provided.

**Important security limitation:** Python and Node currently share the companion app UID. Per-project OS isolation and outbound egress denial are not yet accepted. Run only code you own and trust. Do not run visitor-submitted or arbitrary third-party code.

## 5. Owner versus visitor authentication

Management authentication controls who can change Pocket Host settings, sites, devices and tunnels. Visitor authentication controls who can see a website. These must remain separate.

Future visitor modes are Public, Private and Restricted. They must not be presented as available until gateway/site policy enforcement is implemented.

## 6. Capacity and device health

A phone's capacity depends on device model, response sizes, route mix, network, heat, battery and background policy. No universal visitor count is promised. Capacity Lab is an initial local benchmark, not a production guarantee. Sustainable capacity requires repeated tests with latency, errors, throughput, memory and thermal evidence.

## 7. Backup and recovery

Encrypted .afbackup files contain supported website/runtime project data, routes and selected non-secret settings. Device-bound credentials, sessions, tunnel tokens and Keystore keys are excluded. Keep backup and passphrase separately. Review executable project contents before sharing a backup.

## 8. Troubleshooting principles

- Website local works, public fails: inspect tunnel route, DNS, TLS and external verification.
- Connector ready, hostname fails: local health is not public proof; test from another network.
- Runtime fails, static works: inspect runtime logs without exposing secrets; static origin should remain available.
- Device overheats or memory is low: reduce workload and allow governor to degrade service.
- Restore fails: preserve original backup and last-known-good site; do not repeatedly overwrite recovery data.

## 9. Security vocabulary

Source present, compiled, automated-tested, emulator-verified, device-verified, externally-verified and production-approved are different states. A separate companion UID is not a per-project sandbox. A design document is not implementation evidence.
