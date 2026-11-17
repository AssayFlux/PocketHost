# Pocket Host — Website Deployment Contract

## Serving model

The Android phone is the first website-serving node. It serves static website files through the bundled Rust engine in both mobile modes:

| Mode | Static website server | Fleet controls |
|---|---|---|
| Server only | Enabled | Hidden/disabled |
| Command Center + Server | Enabled | Enabled when fleet release is accepted |

Local static hosting is independent of the remote control plane, Relay, Cloudflare and optional language runtimes.

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

The Rust engine's 50 MiB per-file response limit is separate. A site may contain many files whose combined size is up to the import cap, but an individual asset above the server response cap will not be served until that engine limit is changed and separately accepted. The current full-app backup has a 50 MiB static-site cap and 200 MiB total uncompressed cap, so it does not back up the full 500 MB import allowance. This must be resolved or explicitly approved before v0.1.0 release.

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

The Rust engine defaults to loopback. LAN mode explicitly binds to all network interfaces and must be user-enabled. The port is configurable. Public ingress is not a v0.1.0 release requirement.

## v0.1.1 Workbench publishing

Workbench has a separate private draft workspace. Save and commit do not change the active release. Publishing later adds isolated preview, diff, immutable staging, digest verification, explicit owner approval, atomic activation and rollback. See [WORKBENCH.md](WORKBENCH.md).

## v0.2.0 public ingress

Local Network, AssayFlux Relay and Cloudflare Tunnel are independent providers. Public hostname verification, DNS/TLS, access policy and management-route isolation require external acceptance. Tunnel readiness is not proof of public reachability.
