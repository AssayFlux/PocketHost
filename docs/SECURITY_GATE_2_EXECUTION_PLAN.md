# Security Gate 2 — Execution Plan and Technical Decision Record

**Status:** native seccomp-BPF network-policy probe and isolated-UID instrumentation service are implemented in source; Gate 2 is not complete. Production Python/Node cell migration, FD-backed runtime storage, Unix-domain visitor transport, and full adversarial/API-matrix evidence remain open.  
**Applies to:** Android API 26+, ARMv7/ARM64, no root, no Termux.  
**Security objective:** execute each Python or Node site as hostile code inside an OS-enforced boundary while preserving Rust website ingress and static hosting.

## 1. Security invariants

1. One active site per isolated execution cell. No two projects share an execution process, interpreter globals, UID or writable project directory.
2. The cell runs as an Android isolated process with no Android permissions. The normal companion is a trusted broker only; it must never import or execute customer project code.
3. A cell receives only two network capabilities: the visitor listener/accepted stream explicitly opened by the trusted host, and no general-purpose socket-creation capability.
4. A cell receives only file capabilities for its own project and runtime scratch space. Host data, sibling project roots, account/session data, tunnel credentials, backup keys and management state remain inaccessible.
5. Rust remains the only website ingress and the only component exposed through the existing Cloudflare Tunnel. The management API is never placed on the public hostname.
6. Static hosting must continue if any runtime cell crashes, is stopped, is overloaded or is unavailable.
7. No language-level monkey-patch, import hook, Node preload, hostname allowlist or path-validation check may be counted as the security boundary.

## 2. Chosen architecture: isolated execution cells + capability broker

### 2.1 Process and UID boundary

Use Android services declared with `android:isolatedProcess="true"`, each assigned a unique manifest process name (for example `:cell_0`, `:cell_1`, `:cell_2`, `:cell_3`). Android documents that an isolated service runs with a special isolated UID and has no permissions of its own.

Each live cell is assigned exactly one project. A pool of statically declared isolated service components provides a bounded number of concurrent runtime sites; it is not a shared-UID worker pool. The broker reserves a free cell, binds it, verifies the reported UID differs from the host and all other active cells, then transfers that site's capabilities. No project is imported into the normal companion process.

The initial pool size is four cells. This is a concurrent runtime limit, not a static-site limit. Static sites remain independent and can be numerous. The pool size is a tunable capacity decision after memory measurements; do not increase it merely to claim unlimited hosting.

### 2.2 File capability boundary

The trusted broker creates a private parent directory under the companion's app-private storage (mode 0700). Each project root is a child directory. Only that project's root and a separate scratch/cache directory are made traversable/writable for the isolated UID, and the broker passes open directory descriptors through Binder.

The isolated cell addresses its own root through `/proc/self/fd/<project-root-fd>`; it must not receive a path to the private parent or a descriptor for a sibling. The parent remains inaccessible by ordinary path traversal. The cell may read/write only inside its passed root and scratch directory.

**This is a hypothesis until the API matrix proves it.** Android SELinux may reject access to app-data-labelled files even when the descriptor is passed and Unix mode bits permit traversal. The current instrumentation spike tests:
- Read a file beneath the passed project directory FD.
- Create a file in that directory and have the host read the result.
- Attempt `../sibling/secret.txt` and require denial.
- Keep the host-private parent inaccessible.

If SELinux rejects the directory-FD path, do not weaken SELinux or expose the whole companion files directory. Switch to a descriptor-only workspace: the broker passes one bounded project archive FD; the cell uses an in-memory/FD-backed filesystem adapter, and all persistent reads/writes are brokered through per-project file descriptors. Python and Node compatibility work must be proven before selecting this fallback.

### 2.3 Network capability boundary

The host app creates an AF_UNIX stream listener in the abstract namespace using a cryptographically random per-cell name and passes its listener FD to the trusted companion broker. The broker forwards that FD to the isolated cell; it does not create sockets. Rust connects using a host-side `sockaddr_un` adapter; the cell accepts only on the passed listener. The cell never creates a socket. On each accepted stream, the cell checks `SO_PEERCRED` and accepts only the host app UID. The random abstract name is never exposed to project code. Rust remains the only public/LAN ingress and the existing Cloudflare Tunnel continues to terminate at Rust.

**Mandatory syscall enforcement:** before project code is imported or executed, the cell installs a seccomp-BPF filter with `PR_SET_NO_NEW_PRIVS` and thread synchronization (`SECCOMP_FILTER_FLAG_TSYNC`). The filter denies `socket`, `socketpair`, `connect`, `bind`, socket-call multiplexors, io_uring socket paths, and namespace changes. It permits `listen`, `accept` and `accept4` only when the descriptor argument equals the single listener FD passed by the broker. Any other listener/accept descriptor is denied. The filter is irreversible for the process and inherited by its threads/children. If filter installation or TSYNC fails, the cell must fail closed and must not import project code.

The isolated UID also has no Android permissions of its own. This is defense in depth; the seccomp boundary is what prevents native/JNI code, Python sockets, Node sockets, DNS, UDP, raw sockets and loopback management connections from opening or connecting other sockets. The process must not receive any other network socket FD. Close unexpected inherited AF_INET/AF_INET6 descriptors before project code starts.

The native policy implementation is wired into the runtime-worker native build as `libpockethost_sandbox`; `RuntimeCellProbeService` runs under `android:isolatedProcess=true`, installs the TSYNC filter before its probe, and the Android instrumentation test asserts that `socket()` and `connect()` return `EPERM` while `accept()` on the transferred abstract Unix-domain listener still serves a visitor request. The probe also verifies `SO_PEERCRED` identifies the host UID. This is a policy proof only: the probe does not launch Python or Node. Production Python and Node runtime services do not yet call this policy or adopt the Unix-domain listener.

The isolated cell must fail attempts using IPv4/IPv6 TCP, UDP, raw sockets, DNS resolvers, native/JNI calls, io_uring, loopback management endpoints, private/link-local/metadata targets and child processes. A passed listener FD is a one-way inbound capability, not general network permission.

The runtime worker package must not declare `INTERNET` after this migration: neither the broker nor isolated cells create network sockets. The host app retains the network permission for Rust, cloudflared and owner-approved network operations. The cell's abstract listener name is random and never exposed to project code; `SO_PEERCRED` additionally verifies the connecting UID.

### 2.4 Runtime adaptation

**Python:** one interpreter per isolated cell. Replace self-binding `make_server("127.0.0.1", port, ...)` with a WSGI server that adopts the passed listening socket FD; its accept loop is permitted only on that descriptor. Set the project root and scratch paths from the passed directory FDs. Chaquopy's AndroidPlatform reads/writes through `Application.getFilesDir()` and `getCacheDir()`, and stores extracted-asset hashes in `getSharedPreferences()`. Add a cell-aware Application adapter which redirects files/cache/code-cache paths to the passed runtime FD and provides per-cell in-memory or cell-local SharedPreferences. Test the real Chaquopy bootstrap and asset extraction in an isolated cell before migrating project imports. Never grant access to the companion's whole data directory.

**Node:** one Node environment per isolated cell. Adopt the passed listener FD through Node's supported existing-FD server path; the preload may translate the app's declared port into that already-open descriptor, but it is not the security control. Set project root and cache paths to the passed cell directories. Preserve the FD-backed project path during module resolution (including symlink-preserving entrypoint behavior) and test `process.cwd()`, `__dirname`, `fs.realpath`, relative asset reads and writes. Keep native addon loading disabled until its filesystem and process behavior has passed review.

**Routing:** the host app creates one loopback listener per active runtime project and records its port in the Rust site-routing table. It transfers the listener FD to the companion broker, which forwards it to the isolated cell. The broker records `project ID → cell ID → port → runtime type` but does not create network sockets. Hostname aliases for the same project share one cell/port. Python's current shared-interpreter `start_many` mode is replaced by one isolated project per cell. Node is no longer special-cased as a shared-UID process. The initial four-cell pool caps concurrently active runtime projects; static hosting remains independent.

### 2.5 Lifecycle, cleanup and slot reuse

- A cell is not reassigned until stop/revoke is acknowledged, its listener is closed, its Binder connection is dropped, and the isolated process is confirmed dead.
- Test child-process creation, detached processes, threads, fork/exec and descriptor inheritance. If descendants can outlive the service, implement process-group/cgroup cleanup or make the cell non-reusable until the OS confirms all processes for that isolated UID are gone.
- On project replacement, stop the cell, revoke listener and file descriptors, wait for process death, then atomically stage and activate the new root.
- On crash, Rust returns a bounded 502/503 for that runtime site; static sites remain available. The broker may restart the cell only after cleanup and backoff.
- No secrets are copied into the project root. Credential filename filtering is defense in depth, not a secret scanner.

## 3. Implementation sequence

| Order | Work | Required evidence |
|---|---|---|
| 0 | Isolated UID proof | Two simultaneous isolated services report distinct UIDs; each differs from host and normal companion |
| 1 | Directory FD proof | Cell reads/writes only its passed root; sibling traversal and host-private reads fail on API 26/33/35 |
| 2 | Network FD proof | Cell serves a request on a host-created listener FD; socket creation/connect/DNS/UDP/raw/IPv6 attempts fail |
| 3 | Broker and slot manager | One project per cell, bounded slot allocation, authenticated control, descriptor revocation, crash recovery |
| 4 | Python migration | WSGI app runs in isolated cell, per-project interpreter, no outbound sockets, Rust routing passes end-to-end |
| 5 | Node migration | Node HTTP app runs in isolated cell using passed listener FD, no outbound sockets, Rust routing passes end-to-end |
| 6 | Adversarial hardening | Cross-project, filesystem, archive, network, process, resource and lifecycle attacks fail |
| 7 | Release verification | Same-commit API 26/33/35 CI, ABI APKs, reports, checksums and independent security review |

Do not implement Python and Node integration before the file and network capability proofs pass. This prevents a large runtime migration being built on an invalid Android assumption.

## 4. Adversarial acceptance matrix

### Filesystem
- Read/list/write/delete another site's marker, project files, runtime cache and uploads.
- Read host files, preferences, databases, account/session state, tunnel credentials, backups, signing material and logs.
- Attempt `..`, absolute paths, `/proc/self/fd` enumeration, symlink/hard-link tricks, Unicode normalization, case collisions and archive traversal.
- Attempt to open the app-private parent, sibling root, management storage and any FD not explicitly granted.
- Test archive bombs, excessive entries, duplicate names, sparse files, oversized files and interrupted activation.

### Network
- Create IPv4/IPv6 TCP, UDP and raw sockets; bind/listen/connect/send/receive.
- DNS via libc, TCP/UDP, custom resolver, DNS-over-HTTPS, numeric literals, IPv4-mapped IPv6 and rebinding.
- Reach loopback management, other app services, RFC1918, CGNAT, link-local, multicast, broadcast and cloud metadata addresses.
- Attempt native/JNI networking, child-process networking and use of every inherited/open descriptor.
- Prove only the host-created inbound listener/accepted stream works and that it cannot be repurposed for outbound connect.

### Process and resources
- Attempt fork/exec, detached child processes, thread storms, infinite loops, file-descriptor exhaustion and process persistence after stop.
- Apply and verify per-cell RLIMITs where Android permits them: CPU time, file size, open descriptors and child process count. Do not claim a hard RAM quota unless the OS actually enforces one.
- Measure PSS/RSS, native heap, CPU, thread count, open FDs, disk writes, request size, response size and queue time.
- Verify low-memory, thermal derating, Doze, FGS restrictions, process reclamation, cell crash and restart do not take down Rust static hosting.

## 5. Security policy before Gate 2 exit

Python and Node remain restricted to owner-controlled, reviewed code. Do not run visitor-submitted code, public scripts, marketplace packages or unknown dependencies. Keep management API authentication enabled. Never put host credentials or Cloudflare tokens in project roots.

## 6. Gate 2 exit criteria

Gate 2 is complete only when:
- Two simultaneously active project cells have distinct OS-enforced UIDs and private storage.
- Cross-project and host-private read/write attempts fail on API 26/33/35.
- Cells cannot create outbound IPv4/IPv6 TCP/UDP/raw sockets or bypass via DNS/native/child processes.
- The host-created listener capability supports real Python and Node visitor traffic through Rust.
- Management endpoints, credentials and private-network targets are inaccessible from project code.
- Descriptor revocation, project replacement, process cleanup, resource controls and static-origin continuity pass adversarial tests.
- All Android and quality CI jobs pass on the same commit.
- Independent security review has no unresolved critical/high findings.

Until all exit criteria are evidenced, Gate 2 is **incomplete** and Pocket Host must not be described as a secure multi-tenant code-hosting platform.
