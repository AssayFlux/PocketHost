# Pocket Host — User Guide

This guide describes the v0.1.0 First Hosting target. The repository is still pre-release; follow the UI and limits in the APK you are testing and report mismatches.

## 1. What v0.1.0 does

Pocket Host turns a supported Android phone into a static website host. It serves HTML, CSS, browser JavaScript and static assets. It does not need the Python/Node companion APK to serve a static site.

## 2. Prepare a website

Your website must have an <code>index.html</code> file at its root.

- A folder import must point directly to the website root.
- A ZIP may contain <code>index.html</code> at its root, or one top-level folder containing <code>index.html</code>.
- A React/Vite project must be built elsewhere first. Import the production output (normally the contents of <code>dist/</code>), not the raw source tree.
- v0.1.0 does not compile TypeScript, install npm dependencies or run React/Vite builds on the phone.

## 3. Import limits

- Maximum ZIP archive input: 500,000,000 bytes (500 MB).
- Maximum extracted website contents: 500,000,000 bytes (500 MB).
- Maximum files: 5,000.
- Maximum total files and directories: 10,000.
- Maximum directory nesting: 32 levels.
- Unsafe paths, traversal entries, symlinks and sensitive key/secret files are rejected.

The limits are independent: a compressed ZIP can be below 500 MB but expand beyond 500 MB and be rejected. The Rust server also has a separate 500,000,000-byte (500 MB decimal) per-file response limit.

## 4. Start local hosting

1. Open Pocket Host and choose the website management area.
2. Import the folder or ZIP.
3. Review the import result and any validation message.
4. Start hosting.
5. Open the displayed local URL on the same phone.
6. Confirm the foreground hosting notification and service status.

If the app reports a recovery or import error, do not delete the active site directory manually. Preserve logs and use the supported recovery path.

## 5. Allow another device on the same Wi-Fi

1. Connect the phone and visitor device to the same trusted Wi-Fi network.
2. Explicitly enable LAN access in the hosting settings.
3. Start/restart hosting if the setting requires it.
4. Use the displayed LAN address and port on the second device.
5. Disable LAN access when it is no longer required.

LAN mode exposes the website listener to the local network. Use trusted networks and do not assume Wi-Fi itself authenticates visitors.

## 6. Backup and restore

The current full-app backup is passphrase-encrypted and includes the active site, optional runtime project data, hostname routes and an allow-list of non-secret settings. It excludes device-bound credentials, Cloudflare tokens, owner/device keys and sessions. Export/restore is disabled while hosting or optional runtimes are active. **Current limits:** 500,000,000 bytes for the static site root, 50 MiB for Python, 100 MiB for Node, 650,000,000 bytes total uncompressed and 680,000,000 bytes for the encrypted archive. The static-site backup cap matches the importer's 500,000,000-byte expanded-content allowance. Near-limit backup/restore and low-storage device tests remain required.

## 7. Optional runtimes

Python 3.11 and Node.js 24.22.1 exist in a separate companion APK and are in progress. They are not required for v0.1.0 static hosting. Do not run untrusted projects as though the companion were a hardened multi-tenant sandbox.

## 8. Public internet access

v0.1.0 supports two optional public paths for a custom domain:

- **Cloudflare Tunnel:** configure a named tunnel and public hostname in Cloudflare, then store its token in Pocket Host. Point the hostname to the local Rust website origin. Do not share the token.
- **AssayFlux Relay (target):** install the standalone Relay Node on your own Android device running Pocket Host (Linux-native installation is a v0.1.1 deliverable), verify the domain with the DNS TXT challenge, and bind the hostname to the selected Pocket Host site. You do not need a paid VPS, but the Relay Node must have public IPv4 with router port forwarding or globally routed IPv6. If it is behind CGNAT without global IPv6, it cannot accept public visitors.

The v0.1.0 target is valid HTTPS and HTTP-to-HTTPS redirect by default. Plain HTTP content serving is an explicit insecure option. The current Relay edge only redirects HTTP to HTTPS; plain-HTTP opt-in is not implemented yet. Cloudflare's HTTP policy is configured in the owner's zone. Verify the domain from mobile data or another external network. A local URL, saved token or tunnel process reporting ready does not prove public DNS/TLS/visitor access. The current Relay source is still coupled to fleet control-plane credentials and has no standalone native Android service integration. See [Public Ingress v0.1.0](PUBLIC_INGRESS_V0_1_0.md).

## 9. Report a problem

Record:
- Pocket Host version and source/build identity if available.
- Phone model, Android version and ABI.
- Import source type, archive size and extracted size.
- Hosting mode, port and whether LAN is enabled.
- Exact error, relevant app logs and time of failure.
- Whether the same test works after a clean restart.

Do not share tunnel tokens, passphrases, private keys or other secrets.
