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

The limits are independent: a compressed ZIP can be below 500 MB but expand beyond 500 MB and be rejected. The Rust server also has a separate 50 MiB per-file response limit.

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

The current website backup is passphrase-encrypted. Use a strong unique passphrase and store it safely. The current backup implementation requires hosting to be stopped before export. Cloudflare credentials, runtime projects and unrelated app settings are not part of the portable website backup unless a later contract explicitly adds them.

## 7. Optional runtimes

Python 3.11 and Node.js 24.22.1 exist in a separate companion APK and are in progress. They are not required for v0.1.0 static hosting. Do not run untrusted projects as though the companion were a hardened multi-tenant sandbox.

## 8. Public internet access

Public access through AssayFlux Relay or Cloudflare Tunnel is later-release work and requires separate setup and external-network verification. A local URL or a tunnel process reporting ready does not prove public DNS/TLS/visitor access.

## 9. Report a problem

Record:
- Pocket Host version and source/build identity if available.
- Phone model, Android version and ABI.
- Import source type, archive size and extracted size.
- Hosting mode, port and whether LAN is enabled.
- Exact error, relevant app logs and time of failure.
- Whether the same test works after a clean restart.

Do not share tunnel tokens, passphrases, private keys or other secrets.
