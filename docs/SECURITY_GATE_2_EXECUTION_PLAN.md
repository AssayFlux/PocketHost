# SECURITY GATE 2 — PER-PROJECT EXECUTION ISOLATION

**Priority:** P0 · **Status:** open · **Acceptance:** not passed  
**Parent authority:** `docs/NORTH_STAR.md`, Gate 2  
**Scope:** Python and Node project execution on Android 8/API 26+ without root or Termux.

## 1. Objective

Treat every hosted application as potentially compromised by a malicious request. A project must not read another project's files, access host or management secrets, create arbitrary outbound network connections, or interfere with another project's process. Rust remains the only website ingress. Static hosting remains available if any runtime cell fails.

## 2. Verified current boundary

- Host package: `com.assayflux.pockethost`.
- Companion package: `com.assayflux.pockethost.worker`.
- Companion has a distinct Android UID from host.
- Python and Node projects still share companion UID/private storage.
- Python WSGI multi-site worker can load multiple project roots into one process.
- Companion currently has INTERNET permission; listener bind restrictions are not egress restrictions.
- Signature-protected Binder and bounded archive transfer are useful broker controls, not project sandboxing.
- Isolated service, Unix-domain listener and native seccomp policy source is a feasibility probe. No production cell migration or accepted API matrix is claimed.

Until this gate passes, Python/Node are owner-trusted-code-only. Do not run visitor-submitted or arbitrary third-party code.

## Android/runtime constraints verified during implementation research

- Android documents `android:isolatedProcess="true"` as a process with no permissions of its own; communication is through the Service API. `Context.bindIsolatedService` can create multiple instances of one isolated service component from API 29. API 26–28 therefore need a fixed manifest-declared slot pool; do not assume dynamic instance names exist on the minimum supported OS. Do not use shared-isolated-process binding for project cells because it co-locates services.
- Chaquopy's Android platform initializes from the Application context, extracts bootstrap assets under `getFilesDir()/chaquopy`, and configures Python's `HOME` and temporary directory from app-private files/cache paths. The current Node service similarly reads projects from companion `filesDir/projects` and writes runtime state/cache under `noBackupFilesDir`. Those paths cannot simply be reused from a no-permissions isolated UID.
- The viable direction to prove is a trusted host-side broker passing per-cell project and scratch directory descriptors, with a runtime-specific bootstrap adapter that uses only those descriptors. Chaquopy's importer currently canonicalizes its asset root with `realpath`, so a `/proc/self/fd/N` path must be tested rather than assumed to work. Node's cwd/cache path handling must be tested the same way. If either runtime cannot be made descriptor-rooted without weakening the boundary, replace that runtime integration strategy rather than granting the cell companion app-data access.
- The host app now has a `RuntimeCellCapabilityBrokerBinder` adapter which obtains `Binder.getCallingUid()` at transaction receipt and exposes lease-bound issue, read-only PFD and bounded write operations. The isolated-UID probe calls this host Binder and its instrumentation source verifies that the host UID cannot impersonate the cell, and that the cell can read/write only through broker operations. This is still a feasibility test path: the Binder is not yet wired into the production runtime launcher or lifecycle supervisor. Never trust a parcelled UID.

## 2.5 External platform research (2026-11-16)

- Android's official Context API documents bindIsolatedService as API 29+. It can create multiple isolated instances from one service declaration on API 29+, but API 26–28 cannot use that API and therefore need fixed manifest-declared components if multiple simultaneous UIDs are required. Keep each project in a separate isolated process; never use shared-isolated-process binding for hostile projects. Reference: https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,int,java.lang.String,java.util.concurrent.Executor,android.content.ServiceConnection).
- Chaquopy's AndroidPlatform constructor uses the Application context; its Android guide places Python HOME in app internal storage and extracts some package data files onto the filesystem. Its GenericPlatform only configures PYTHONPATH and is documented as a generic Python installation, not a proven Android isolated-service bootstrap. The Chaquopy FAQ confirms that Python normally runs in the app process and that moving it to another process complicates the integration. Therefore neither changing process names nor swapping in GenericPlatform is evidence that Python can run securely in an isolated UID. References: https://chaquo.com/chaquopy/doc/current/java/com/chaquo/python/android/AndroidPlatform.html ; https://chaquo.com/chaquopy/doc/current/android.html ; https://chaquo.com/chaquopy/doc/current/faq.html.
- Linux seccomp documentation requires PR_SET_NO_NEW_PRIVS (or suitable privilege) before installing an unprivileged filter and documents syscall/argument filtering. The current probe uses TSYNC and a denylist; its final ALLOW fallback is not a strict syscall allowlist. Reference: https://man7.org/linux/man-pages/man2/seccomp.2.html.

These sources confirm the integration constraints; they do not prove runtime compatibility. The required next engineering proof remains a real Python and Node project executing inside distinct isolated UIDs with descriptor-only project storage and verified egress denial on API 26/33/35.
## 3. Security invariants

1. One active project per isolated UID/process; no shared interpreter or writable project state.
2. Project code receives only its own data/runtime capabilities.
3. Host credentials, account/session data, tunnel tokens, backup keys and management state are inaccessible.
4. Project code cannot create general-purpose sockets, connect outbound, resolve arbitrary DNS or reach loopback/private/link-local/metadata networks.
5. Only explicitly brokered visitor traffic reaches the project.
6. The broker never imports or executes project code.
7. Policy installs before any project module/native extension executes; failure is fail-closed.
8. Cell crash/overload cannot stop static hosting.
9. Limits cover processes, file descriptors, memory, storage, logs, request time and restart rate.
10. Security claims are supported by adversarial tests and independent review.

## 4. Target architecture

### 4.1 Isolated execution cell
Use Android isolated service processes with unique isolated UIDs. A bounded pool may be used, but a cell is assigned to exactly one project for its lifetime. Verify the actual UID at bind time and reject reuse if process identity does not match the allocation.

The trusted companion broker handles enrollment, archive staging, service binding, capability transfer, health and cleanup. It must not load customer Python modules or start Node project code.

### 4.2 Filesystem capabilities
Preferred design: pass only a project directory descriptor and separate scratch descriptor, with no parent or sibling descriptors. Verify SELinux behavior on API 26/33/35. If app-data labels prevent safe descriptor access, do not weaken SELinux or chmod the parent. Evaluate a brokered descriptor-backed filesystem or dedicated isolated storage mechanism and prove Python/Node compatibility before adoption.

Tests must prove own-root read/write succeeds; sibling, parent, host, account, session, backup, token and management paths fail; traversal, symlink, procfs and descriptor reuse fail.

### 4.3 Visitor network capability
Host creates a private Unix-domain stream listener and retains its path for Rust. Only the intended listener capability is transferred to the isolated cell. The cell validates peer credentials and accepts visitor streams. No public/LAN listener is created by project code.

The exact descriptor-transfer route must be tested on Android 8 SELinux. If abstract namespace or FD passing fails, compare filesystem-domain sockets with strict directory permissions and a brokered accepted-stream model. Do not assume a transport works because it compiles.

### 4.4 Kernel policy
Install native seccomp-BPF with no-new-privileges and thread synchronization before runtime initialization. Deny socket creation, socketpair, connect, bind, socket multiplexors, io_uring networking and prohibited namespace/process syscalls. Before policy installation, close every inherited socket descriptor except the broker-created visitor listener; generic read/write syscalls can otherwise use an inherited connected socket as an egress channel. Cleanup failure must abort cell startup. Permit only the minimal operations required for the already-open visitor listener and runtime.

The current native probe implements inherited-socket cleanup and the instrumentation source supplies an already-connected TCP socket to verify closure. This remains probe-only: the policy still ends in an allow fallback and is not yet installed by a production Python/Node launcher. Do not accept a policy that simply blocks known hostname APIs. Test native syscalls, libc, DNS, IPv4/IPv6, TCP/UDP/raw sockets, loopback, private/link-local/metadata targets, child processes and native extensions. If policy cannot be applied to every thread, stop the cell.

### 4.5 Resource controls
Android process isolation is not a hard CPU quota. Implement explicit admission, memory ceilings where enforceable, process/FD limits, bounded project storage, request deadlines, log caps, restart backoff and kill/recovery. Document what Android cannot guarantee.

## 5. Implementation sequence

1. Build a minimal isolated-cell proof app and run API 26/33/35; use fixed declared service slots on API 26–28 and API-29+ isolated binding only where available.
2. Verify distinct simultaneous UIDs, file descriptor ownership and Unix listener peer identity.
3. Validate seccomp policy behavior, inherited connected-socket closure, and TSYNC failure handling with adversarial native test code.
4. Build broker slot manager, per-project allocation, identity binding and cleanup.
5. Implement per-cell project transfer and descriptor-backed storage; adapt Chaquopy's AndroidPlatform/importer and Node runtime cache/cwd to the brokered roots.
6. Migrate Python to one interpreter per cell; prove WSGI through Rust.
7. Migrate Node to one environment per cell; prove HTTP through Rust.
8. Implement process/resource limits, restart policy, update rollback and descriptor revocation.
9. Add cross-project, host-secret, egress, lifecycle, low-memory and static-continuity suites.
10. Obtain independent Android/Linux security review.

## 6. Required adversarial matrix

| Attack | Required result |
|---|---|
| Read sibling project path/FD | Denied |
| Read host files / Android shared data | Denied except explicitly granted capability |
| Read tunnel token, account DB, backup key | Denied |
| Path traversal, symlink escape, procfs tricks | Denied |
| socket/socketpair/connect/bind | Denied |
| TCP/UDP/raw/IPv6/DNS/native socket bypass | Denied |
| Loopback management API | Unreachable |
| Private, link-local and metadata destinations | Unreachable |
| Fork/exec or native extension bypass | Denied or confined to same cell with no extra privilege |
| Reuse stale/revoked listener descriptor | Denied |
| Cell crash / OOM / timeout | Static site remains available; cell recovers safely |
| Two concurrent projects | Distinct UIDs and no cross-access |

## 7. Acceptance gates

- Instrumentation passes API 26, 33 and 35 on the same release candidate SHA.
- Two actual Python/Node projects run in distinct isolated UIDs.
- Real HTTP requests route visitor → Rust → cell → Rust → visitor.
- All adversarial tests pass; test harness cannot weaken the property.
- Companion has no path to execute code outside the broker's authorization model.
- Static hosting continues under cell failure and overload.
- Independent security review records findings and disposition.

**No acceptance based on a probe, compile, language monkey-patch or documentation.** If API 26 cannot support the selected boundary, redesign the storage/transport architecture or explicitly revisit the supported platform contract with owner approval. Never silently drop security requirements.
