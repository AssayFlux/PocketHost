# Pocket Host — Learn and Quick Start

This guide is also available inside the Android app under **Learn**. It describes the current implementation and calls out limitations rather than promising unsupported behavior.

## Quick start
1. Open **Files** and import a static website folder or ZIP archive.
2. Confirm that the site contains an entry page such as `index.html`.
3. Open **Dashboard** and start hosting.
4. Use **Preview site** to verify the local website.
5. Configure the existing Cloudflare Tunnel and Public Hostname to reach the Rust website listener when public access is required.

## Local, LAN and public access
- Loopback access is available only on the phone.
- LAN access requires enabling the LAN option and restarting hosting after changing it. Devices must be on a reachable local network.
- Public access uses the owner's existing Cloudflare Tunnel and hostname. The hostname must route to the Rust website listener, never the management API.
- Cloudflare edge HTTPS does not itself authenticate website visitors. Use Cloudflare Access for private sites.

## Dashboard and capacity
The dashboard reports current hosting state, static-engine traffic totals, uptime, device telemetry and admission budget. Metrics are not necessarily inclusive of Python, Node or tunnel traffic.

Capacity Lab performs a bounded local static GET sweep. Run it on a cool, adequately charged phone. Its safe concurrency ceiling is workload/device-specific. A percentage profile controls admission concurrency, not CPU use or a guaranteed visitor count.

## Website frameworks and languages

Pocket Host can host built static output from HTML/CSS/JavaScript, TypeScript and frontend frameworks such as React, Vue, Svelte and Angular. For build steps, Next.js static export boundaries, and current Python/Node runtime requirements, see the [Website Deployment Guide](WEBSITE_DEPLOYMENT.md).

## Python and Node runtimes
These runtimes are for trusted code only. Python currently supports one WSGI application. Node runs one active application with prebundled dependencies. Both run under the Pocket Host app identity; neither is an OS-enforced sandbox. Do not run untrusted projects.

## Activity and diagnostics
Use Activity to inspect operational events, filter errors, search and export redacted logs or diagnostics. Review exports before sharing and remove private information that should not leave the device.

## Backup and restore
The current encrypted backup covers static website content. It does not include runtime projects, credentials, all settings or remote-control state. Keep the passphrase separately; it cannot be recovered.

## Device safety and availability
Keep the phone ventilated, monitor battery/temperature/storage and use conservative capacity settings. Android Doze, OEM power policies, process termination and network changes can interrupt hosting. Pocket Host does not guarantee 24/7 uptime or unlimited traffic.

## Known product limitations
- Built-in visitor password protection is not currently implemented.
- Python/Node hostile-code isolation is not implemented.
- Full configuration and runtime backup is not implemented.
- Local management sessions are not a replacement for Owner/Operator account management.
- Physical-device, screen-size, large-font and keyboard acceptance must be completed before a production release.


## Host Owner and Operator accounts

1. Pair a browser with the phone only when you can approve the six-digit request in the Android app.
2. Use the first approved Owner session to create the Owner username and a unique password of at least 12 characters.
3. Use the Owner credentials for subsequent sign-ins.
4. From the Owner session, create or revoke Operator accounts.
5. Operators can inspect data, operate sites and adjust capacity. Owner-only settings and account administration are rejected by the device API.

Password verifiers use PBKDF2-HMAC-SHA256; the account registry is encrypted with Android Keystore AES-GCM. Browser tokens are tab-scoped; device sessions expire after 12 hours and are revocable.

The management console supports changing your current password. A successful change revokes all other sessions for that account and returns a replacement session for the current browser. If the Owner password is lost, request a fresh pairing challenge and approve it directly in the Android app; the device-approved pairing session can reset the Owner password and invalidates all prior Owner/pairing sessions. Ordinary logged-in Owner sessions cannot invoke this recovery route.

Native biometric app-launch unlock is a separate local UI gate. It does not replace Owner/Operator API authentication or protect against a compromised Android operating system. Never expose the management API through the public website hostname.


## Latest implementation note — 2026-11-15

The website file manager now supports individual file upload (50 MiB maximum), file/folder rename, text-file editing and deletion. The Rust static server falls back to root `index.html` for extensionless client-side routes; missing asset paths with extensions remain 404. Read the [Website Deployment Guide](WEBSITE_DEPLOYMENT.md) before choosing a framework or backend runtime.

The management console supports changing an Owner or Operator password. Owner recovery requires approving a fresh pairing challenge on the Android phone. Biometric app-launch unlock is optional and remains separate from management API authentication.

Main commit `34f8d5329223d59e984e98b977e92d86e6efd555` passed all six Quality CI jobs in [run 43703703022](https://github.com/AssayFlux/PocketHost/actions/runs/43703703022). Android integration [run 43703702835](https://github.com/AssayFlux/PocketHost/actions/runs/43703702835) was pending at reconciliation time. Runtime sandboxing, complete backup, physical-device/network acceptance and production release remain outstanding.
