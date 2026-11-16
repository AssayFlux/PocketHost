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

## 4. Chosen target: isolated per-project service cells

The selected design is an Android isolated service process for each hosted project, not a shared companion UID and not a separately installed APK for every site. Android documents that an isolated service runs under a special isolated UID and has no permissions of its own.

The normal runtime companion becomes a trusted broker only. It does not import project modules, start CPython or execute Node. Each declared cell service has a unique process name and is bound to exactly one project while active. The initial pool is four isolated cells; this is a bounded concurrent runtime capacity, not a limit on static sites. Revisit the pool size only after low-memory device measurements.

The broker creates a private parent directory inaccessible by ordinary path traversal. It passes a descriptor for only the selected project's directory and its scratch/runtime directory. The cell addresses those roots through /proc/self/fd/<n>. The parent remains mode 0700; only the passed child directory is traversable. The cell must not receive a sibling descriptor or a descriptor for the parent.

The broker also creates a loopback listener socket and passes its descriptor to the cell. The cell accepts visitor connections on that pre-created listener. It does not create TCP/UDP/raw sockets. Rust continues to proxy to the loopback port and remains the only public/LAN ingress. The current proof tests both a connected socket FD and a host-created listener FD.

**Critical proof gate:** SELinux may prevent an isolated app from traversing app-data-labelled files even when a directory descriptor and Unix mode bits are passed. The current instrumentation tests own-project read/write and sibling traversal. If this fails, use the descriptor-only archive/VFS fallback in [SECURITY_GATE_2_EXECUTION_PLAN.md](SECURITY_GATE_2_EXECUTION_PLAN.md); do not chmod the companion parent or expose the full files directory.

The normal broker may retain only the permissions it needs to receive projects, create listener descriptors and manage cells. Project code runs only in isolated cells. Remove the worker package's INTERNET permission once the brokered-listener design is integrated and verified.

### Runtime integration requirements

- Python: one CPython interpreter per cell; WSGI server adopts the passed listener FD; Chaquopy files/cache paths must be redirected to that cell's passed runtime directory.
- Node: one Node environment per cell; Node adopts the passed listener FD using its existing-FD server support; project and cache paths use the cell's descriptors.
- Rust: retain hostname/site routing and loopback reverse proxy; the broker registers each cell's loopback port and lifecycle state.
- Lifecycle: revoke descriptors, stop and reap the isolated process before reusing a cell; prove child processes cannot outlive the cell.
- Resource controls: enforce RLIMITs where Android permits them and measure PSS/CPU/FD/storage; do not claim a hard RAM quota without OS evidence.

### Rejected shortcuts

- One isolated process shared by multiple sites: UID and filesystem boundary are shared.
- Same companion UID with separate processes/directories: projects can still read each other's files.
- Python/Node monkey-patches, DNS allowlists or path validation as the primary boundary: bypassable.
- Device-wide VPN as the only per-project egress control: conflicts with user VPNs and does not isolate same-UID projects.
- One separately installed worker APK per project as the default: adds package-install prompts, signing/update complexity and repeated runtime storage; retain only as a fallback if isolated service file capabilities prove impossible.

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
