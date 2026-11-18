# Pocket Host — Website Deployment Contract

## Serving model

The Android phone is the first website-serving node. It serves static website files through the bundled Rust engine in both mobile modes:

| Mode | Static website server | Fleet controls |
|---|---|---|
| Server only | Enabled | Hidden/disabled |
| Command Center + Server | Enabled | Enabled when fleet release is accepted |

Local static hosting is independent of the remote control plane, Relay, Cloudflare and optional language runtimes. Public access is an optional v0.1.0 layer; local/LAN hosting continues if a provider is stopped or unavailable.

## v0.1.0 website composition

Supported baseline: HTML, CSS, browser JavaScript and static assets such as images, fonts, icons, JSON and downloadable files, subject to importer and per-file response limits.

A website root contains <code>index.html</code>. ZIP import accepts the file at archive root or inside one top-level folder. Folder import selects the website root directly.

### Import limits

- ZIP archive input: 500,000,000 bytes maximum.
- Expanded website contents: 500,000,000 bytes maximum.
- Folder imports: 500,000,000 bytes total.
- Files: 5,000 maximum.
- Entries: 10,000 maximum.
- Nesting: 32 levels maximum.
- Unsafe paths, traversal, symlinks and sensitive files are rejected.

The Rust engine's 500,000,000-byte (500 MB decimal) per-file response limit is separate. A site may contain many files whose combined size is up to the import cap; the individual Rust response cap is 500,000,000 bytes. Manual single-file import remains capped at 50 MiB. The current full-app backup supports a 500,000,000-byte static-site root, 650,000,000-byte total uncompressed data and a 680,000,000-byte encrypted archive, so its static-site root limit matches the 500,000,000-byte importer expanded-content allowance. Optional Python and Node project roots are separately limited to 50 MiB and 100 MiB. Near-limit backup/restore, free-space and physical-device acceptance remain open.

## Separate website source repository

The owner also maintains the private repository [AssayFlux/AssayFlux.org](https://github.com/AssayFlux/AssayFlux.org). It is a separate website source repository, not part of Pocket Host. The v0.1.0 hosting flow does not automatically clone private GitHub repositories: build the site in its own workflow/desktop environment and import the resulting static output. The repository was inspected on 2026-11-17 and currently contains only `.gitignore`, `LICENSE` and a short `README.md`; no deployable website source or build output is present yet. Once the source is added, build its production output and import that folder (for Vite, `dist/`).

## Static build handling

v0.1.0 hosts already-built static output. For a Vite/React project, run the build on a desktop/CI environment and import the contents of the production dist directory. Raw TypeScript and JSX are not directly browser-executable. The main-app TypeScript compiler and Workbench build integration are v0.1.1.

## Import and activation lifecycle

1. User selects a folder or ZIP.
2. Importer creates a unique app-private staging directory.
3. Stream limits, path/name checks, file/entry/depth quotas and index validation are applied.
4. The old active site remains intact during validation.
5. A valid replacement is activated by filesystem rename; prior active content is retained as a release.
6. Interrupted activation is recovered to a safe last-known-good release.
7. Failed import removes staging and preserves the active site.

## Host listener

The Rust engine defaults to loopback. LAN mode explicitly binds to all network interfaces and must be user-enabled. The port is configurable. The public providers connect only to this website origin; they never expose the management API or arbitrary local ports.

## v0.1.1 Workbench publishing

Workbench has a separate private draft workspace. Save and commit do not change the active release. Publishing later adds isolated preview, diff, immutable staging, digest verification, explicit owner approval, atomic activation and rollback. See [WORKBENCH.md](WORKBENCH.md).

## v0.1.0 public domain hosting

Public ingress is delivered by two independent providers:

- **Cloudflare Tunnel:** the Android app runs the owner-configured named tunnel as an outbound connector. Configure the Cloudflare public hostname to route to `http://127.0.0.1:<website-port>`. The tunnel token is stored encrypted by Android Keystore. Cloudflare terminates visitor TLS and routes the request to the local Rust HTTP origin.
- **AssayFlux Relay (target):** the owner enables the native Relay service bundled in the Pocket Host Android APK in v0.1.0; Linux-native packaging is v0.1.1. A paid VPS, Docker and PostgreSQL are not product prerequisites. The Relay Node must nevertheless have public IPv4 with router port forwarding or globally routed IPv6. The Android host maintains an independent outbound authenticated connection; the gateway accepts DNS-verified hostnames and forwards bounded HTTP requests only to the registered site. Current source is not yet the target native gateway: the visitor gateway is coupled to the PostgreSQL fleet control-plane process, while the Android host-side RelayCredentialStore is already independent of RemoteControlAgent.

The v0.1.0 target is valid HTTPS and HTTP-to-HTTPS redirect by default; plain HTTP content serving is an explicit insecure opt-in. Current Relay Caddy configuration only redirects HTTP to HTTPS and has no plain-HTTP opt-in. Cloudflare's redirect policy is configured by the owner in Cloudflare. A hostname is assigned to one provider at a time. For a direct first-party Relay path, use a DNS-only A/AAAA record if Cloudflare hosts the zone; Cloudflare proxying keeps Cloudflare in the visitor HTTP path. Separate hostnames may use different providers; same-hostname active-active/failover requires a separate DNS/load-balancer layer and is not assumed.

The current combined Relay gateway and management API use separate listeners/hostnames, but share the same process and PostgreSQL dependency. The v0.1.0 native Android Relay service must preserve visitor/admin separation while removing the mandatory fleet control-plane and PostgreSQL dependency. The public visitor hostname may reach only the website route. The website host's local origin remains loopback/LAN scoped; only the separately authenticated Relay data-plane listener is exposed through the declared public edge forwarding. Verify DNS, TLS, correct site content, unknown-host rejection, management-route denial and provider independence from mobile data or another external network. A local readiness probe is not proof of public reachability.

See [Public Ingress v0.1.0](PUBLIC_INGRESS_V0_1_0.md), [AssayFlux Relay](ASSAYFLUX_RELAY.md) and [Cloudflare Tunnel](CLOUDFLARE_TUNNEL.md).
