# Phase 3 — Dual-stack diagnostics and Dynamic DNS

Branch: `Alpha`

## Implemented in source

- Public IPv4 and IPv6 are detected independently from two external address endpoints per family. Conflicting responses fail closed rather than selecting an arbitrary address.
- Publishing validation rejects loopback, private, link-local, multicast, CGNAT, documentation and non-global IPv6 results.
- The native Rust gateway now binds independent IPv4 and IPv6 sockets for loopback, LAN and public-origin modes. IPv6 is explicitly v6-only at the socket layer, preventing one family from shadowing the other.
- The native gateway's bounded connection queue, per-site limits, rate limits and graceful shutdown are shared across both listeners.
- Diagnostics enumerate device interface addresses. IPv4 in 100.64.0.0/10 is reported as likely carrier-grade NAT; RFC1918 IPv4 is reported as upstream NAT without claiming it is definitely carrier CGNAT.
- Inbound TCP reachability can be checked independently for IPv4 and IPv6 using the configured gateway port and Check-Host.net remote nodes. This probe is **opt-in** because the public IP and port are sent to a third-party service. A successful result proves only that the selected port was reachable from the responding probes at that time.
- DDNS updates A and AAAA independently. One family failure does not suppress a successful update for the other family.
- PocketHost persists Cloudflare record IDs it created or that the owner explicitly adopted. Background work updates only those managed IDs; duplicate records and unowned existing records are not overwritten.
- The foreground UI has an explicit adopt-existing confirmation. Background synchronization never adopts an unowned record.
- After each update, DNS-over-HTTPS answers are checked. DNS-only records must return the expected origin address. Proxied records must return an answer but are not expected to reveal the origin address.
- WorkManager schedules unique periodic work at Android's 15-minute minimum interval, with a connected-network constraint and exponential backoff. A 30-second minimum interval also rate-limits foreground and worker attempts; an in-process synchronization lock prevents concurrent duplicate record creation. The schedule is restored when the app opens.
- Each A/AAAA result is recorded independently. A bounded local history retains up to 100 family results; notifications report successful sync and attention/retry states.

## Security and operational boundaries

- Cloudflare DNS credentials continue to use the Android Keystore-backed encrypted token store.
- The ownership map stores DNS record IDs and host/type keys, not API tokens.
- DNS updates do not change unrelated record types or records that PocketHost does not own.
- Background DDNS is best-effort: Android battery optimization, Doze, force-stop and manufacturer restrictions can delay or prevent scheduled work.
- A local listener and an externally detected IP do not alone establish public availability. Use the external probe and validate from a separate client network.
- A failed external TCP probe can be caused by CGNAT, upstream firewall, router forwarding, mobile carrier filtering or a closed listener. It does not identify the exact cause.
- Cloudflare proxied DNS can return Cloudflare edge IPs; the app therefore verifies answer presence for proxied records instead of incorrectly comparing edge addresses with the origin.
- This phase does not make an origin reachable through CGNAT, create port forwarding, open carrier firewalls, or guarantee inbound IPv6 firewall policy.

## External probe API

The optional remote TCP check uses the documented Check-Host.net API:
https://check-host.net/about/api

The app must disclose the third-party disclosure and obtain explicit user consent before dispatching a probe.

## Verification gates

- Rust unit test verifies that the native gateway serves requests over both IPv4 and IPv6 loopback.
- Android instrumentation covers IPv4/IPv6 public-address scope validation, CGNAT range classification and independent family status.
- Android instrumentation covers unique periodic WorkManager scheduling and cancellation.
- Android lint, Kotlin compilation, native Android ABI build, emulator instrumentation and full APK build must pass on the exact final Alpha commit.
- Physical-device dual-stack and carrier-network external probes remain environment-dependent; CI cannot prove that a specific mobile carrier permits inbound connections.
