# Pocket Host — Website Hosting Limits and Security Contract

**Applies to:** v0.1.0 Alpha local static website hosting  
**Status:** implementation contract; verification is recorded separately.

## Shared limits

| Resource | Alpha limit |
|---|---:|
| Registered sites | 100 |
| Hostname routes | 1,000 total; 100 per site |
| Website files | 5,000 |
| Files plus directories | 10,000 entries |
| Relative directory depth | 32 |
| Total expanded website content | 500,000,000 bytes |
| Individual file import | 500,000,000 bytes, subject to total site budget |
| Text editor read/write | 256 KiB |
| HTTP response file | 500,000,000 bytes |
| HTTP header list | 16 KiB |
| Concurrent server connections | 16 global; per-site admission is bounded |

ZIP input is capped at 500,000,000 compressed bytes and 500,000,000 expanded bytes. Backups use authenticated AES-GCM encryption and share the website file, entry, depth and expanded-byte budgets. Temporary restore data is authenticated before activation. Actual free storage can be lower than the configured budget; write failures must abort staging and preserve the active deployment.

## Path and content rules

- Paths are relative, normalized and contained within the active site's canonical root.
- Symbolic links are unsupported in deployed website trees. Import, file management, backup and serving must not follow them.
- Sensitive-path checks apply at import/management boundaries and again to canonical resolved targets at serving time, preventing harmless-looking aliases from exposing credential files.
- ZIP entries reject traversal, absolute paths, duplicate/conflicting entries, case-colliding portable names, unsafe names, excessive depth and sensitive paths.
- MIME types are selected by the hosting engine; responses use `X-Content-Type-Options: nosniff`. Unknown extensions are served as binary content.
- Extensionless SPA routes may fall back to root `index.html`; missing paths with file extensions remain 404.

## Deployment and recovery

Imports are staged under app-private storage. The active deployment is moved into release history before activation; failed activation attempts restore the previous deployment. Startup recovery validates release candidates without following links and enforces the same entry/depth/byte/file budgets. Invalid active data is quarantined rather than silently deleted. Up to five safe prior releases are retained.

## Independence and scope

Static hosting is provided by the Android app and native Rust engine. It must not require runtime-worker, control plane, Linux agent or web console. Runtime-backed routes are optional and are not part of the static-hosting availability guarantee. Multi-site hostnames are routed through the native engine; each route must resolve only to its registered site root.

## Verification boundary

Host-side Rust tests and Android source/instrumentation compilation are not equivalent to LAN acceptance on a physical phone. Loopback/LAN visitor checks, actual storage exhaustion, large backup round-trips and physical-device/network behavior remain integrated acceptance items unless a named automated test explicitly covers them.
