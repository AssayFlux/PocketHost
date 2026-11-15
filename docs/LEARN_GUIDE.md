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

Biometric unlock is not yet implemented. Never expose the management API through the public website hostname.
