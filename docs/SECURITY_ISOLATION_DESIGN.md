# Pocket Host — Per-project isolation and network security design

**Status:** Gate 2 is open. This document records the verified gap and the engineering design/acceptance work; it is not a claim that the sandbox is implemented.

## 1. Threat model

Treat a hosted Python or Node project as potentially malicious once it is exposed to visitor traffic. A compromised project must not be able to:

- Read, modify or delete another site's source, uploads, configuration or runtime state.
- Read host-app files, account/session material, Cloudflare credentials, backups or management secrets.
- Reach the local management API or other host services without an explicit authenticated broker operation.
- Initiate arbitrary TCP, UDP, raw-socket, DNS, IPv4 or IPv6 traffic.
- Reach private, loopback, link-local, multicast, broadcast or cloud metadata destinations.
- Persist across project deletion/replacement or contaminate another project's interpreter/process state.
- Exhaust the phone without bounded memory, CPU, process, file-descriptor and I/O budgets.

Static hosting and the Rust public ingress must remain usable if an optional runtime is stopped, compromised or unavailable.

## 2. Verified current implementation

The following statements describe source, not a successful security certification:

| Area | Current behavior | Security conclusion |
|---|---|---|
| Host boundary | Runtime companion has a different Android package UID from the host | Host-private file protection is improved |
| Project boundary | All projects are extracted under one companion `files/projects` directory | No per-project UID or filesystem isolation |
| Python | Multi-site WSGI imports multiple project roots into one interpreter/process | One project can potentially inspect sibling project files and process state |
| Node | One active Node app runs in companion `:node` process | Node shares companion UID and can access companion-owned files |
| Python ingress | WSGI binds to `127.0.0.1`; LAN override removed from runtime API | Listener is loopback-only |
| Node ingress | Preload rewrites ordinary `net.Server.listen` binds to `127.0.0.1` | Not a restriction on native modules, UDP or outbound sockets |
| Outbound network | Companion manifest grants Android `INTERNET` | No OS-enforced per-project egress denial |
| Loopback | Companion can connect to host loopback; host can reach companion loopback | Management/API endpoints are reachable at network layer and rely on authentication |
| Archive import | Size/count/path/credential-name checks and staged activation | Useful input hardening, not a sandbox or complete secret scanner |

The existing isolated-process probe showed that Android can provide a distinct isolated UID and deny host-private file access, but that process could not use the current loopback TCP path. The current companion architecture therefore cannot be made safe merely by setting `android:isolatedProcess=true`.

## 3. Non-negotiable design constraints

- Android 8.0 / API 26 minimum; test API 26, 33 and 35.
- No root, Termux, custom kernel, or privileged device-owner assumption.
- Rust remains the only public/LAN website ingress and continues to serve static sites independently.
- Reuse the owner's existing Cloudflare Tunnel; never expose the management API publicly.
- Keep Python and Node as supported runtime options; do not claim security by silently disabling them.
- Do not require a device-wide VPN as the sole security boundary. Android permits only one active VPN service, and it can conflict with a user's existing VPN.
- No user project code receives host credentials or a general-purpose network capability.

## 4. Target execution-cell architecture

Every hosted project needs its own OS-enforced identity and private storage. A project cell must not share a Linux UID, writable directory, interpreter globals, process namespace or runtime cache with another project.

The preferred implementation spike is to compare these Android mechanisms:

### Option A — isolated per-project worker process

Use an Android isolated service identity for each project execution cell. The host/Rust broker passes only the project files and accepted visitor socket descriptors required by that cell. The cell must not receive general network permission or access to the host/companion app data directories.

This option is acceptable only if the team proves all of the following on API 26+:
- A distinct isolated UID is allocated per concurrently active project, not merely one UID shared by all service instances.
- Python and Node can load the transferred project without granting access to host or sibling-project storage.
- Rust can pass an already-accepted visitor socket descriptor to the cell and relay request/response bytes without giving the cell general socket creation/connect capability.
- The cell cannot create outbound TCP/UDP/raw sockets or use DNS, IPv6, alternate transports or native code to bypass the restriction.
- Process death, descriptor revocation, cleanup and project replacement do not leak access between cells.

The existing loopback test is not evidence that this architecture works; it must be re-designed around brokered file/socket descriptors.

### Option B — one signed worker package per project

Generate/install a worker package for each project, with a unique Android package UID, private project directory, the same owner-controlled signing identity and no general `INTERNET` permission. Rust brokers inbound sockets to the worker through a capability-limited IPC interface.

This option gives a natural Android package/UID boundary but has substantial product costs: package installation consent, package-count limits, APK duplication/size, updates, uninstall/recovery, signature rotation and API 26+ compatibility. It must be evaluated before adoption.

### Rejected as primary controls

- Python import hooks, monkey-patching `socket`, Node preload wrappers or hostname allowlists: bypassable by native code/raw sockets and not an OS security boundary.
- Canonical path checks, archive filters or separate directories under one UID: code running as that UID can still access sibling files.
- A shared companion UID with one process per project: process separation alone does not separate filesystem permissions.
- A device-wide `VpnService` as the only egress control: one VPN is active at a time, it is not per-project when projects share a UID, and loopback handling must be proven rather than assumed.

## 5. Network capability model

Project code should receive no general network capability. Rust owns inbound public/LAN sockets and passes a connected/accepted stream capability to the correct project cell. The cell may respond only on that stream.

The security test matrix must attempt:
- IPv4 and IPv6 TCP connect, listen and bind.
- UDP send/receive, DNS over UDP/TCP, DNS-over-HTTPS and custom resolvers.
- Raw sockets and native/JNI networking.
- Loopback management ports and other app-local services.
- RFC1918, carrier-grade NAT, link-local, multicast, broadcast and cloud metadata destinations.
- Numeric IP literals, alternate address encodings, redirects, DNS rebinding and IPv4-mapped IPv6.
- File-descriptor reuse, inherited descriptors and attempts to use another project's broker channel.

A passing result must come from OS-enforced denial or capability absence, not a language-level wrapper. The only permitted network operation is the brokered visitor stream.

## 6. Required adversarial tests

### Filesystem
1. Project A attempts to read, list, overwrite and delete Project B's marker and files.
2. Project A attempts to read host files, management/session stores, tunnel credentials, backup keys and logs.
3. Test symlink, hard-link, traversal, Unicode-normalization and archive collision attacks.
4. Test decompression bombs, excessive entry counts, sparse/large files, duplicate names and interrupted activation.
5. Delete/reinstall/restart a project and prove stale descriptors, caches and process state cannot access the replacement or another project.

### Network
1. Run the full IPv4/IPv6 TCP/UDP/raw/DNS matrix above from Python, Node, native code and child threads/processes.
2. Attempt management API access, local ports, private/link-local/metadata addresses and DNS rebinding.
3. Verify Rust can still route a real inbound request to the isolated project through the brokered descriptor.
4. Verify a project cannot retain or replay a socket descriptor after stop/revoke.

### Resource and lifecycle
1. Bound memory/PSS, CPU time, threads/processes, open files, disk writes, request body and response sizes.
2. Test infinite loops, fork/thread storms, slow clients, decompression and file-write exhaustion.
3. Kill/restart the cell while other projects and static hosting continue.
4. Test Android process reclamation, Doze, foreground-service policy, low storage and thermal pressure.

Run the same tests on API 26, 33 and 35. Preserve failing payloads and test reports as CI artifacts. Independent security review is required before untrusted code is enabled.

## 7. Interim operating policy

Until Gate 2 passes:
- Python/Node are for owner-controlled, reviewed code only.
- Do not run visitor-submitted code, public user scripts, marketplace packages or unknown dependencies.
- Do not put credentials, API keys, Cloudflare tokens or private keys in project roots.
- Keep the management API authenticated and loopback-only.
- Keep Rust as the only public/LAN ingress.
- Static hosting remains the safe independent path.

## 8. Gate 2 exit criteria

Gate 2 is complete only when all are true:

- Two concurrently active projects have distinct OS-enforced identities and private storage.
- Cross-project and host-private read/write attempts fail under adversarial tests.
- Project code has no general outbound network capability; all listed bypass attempts fail.
- Rust successfully brokers visitor traffic to Python and Node without granting worker network egress.
- Management endpoints, secrets and private targets are inaccessible from project code.
- Resource and lifecycle limits have measured, repeatable tests.
- API 26/33/35 CI passes on the same commit.
- Independent security review has no unresolved critical/high findings.

Until then, report Gate 2 as **incomplete** and do not describe Pocket Host as a secure multi-tenant code-hosting platform.
