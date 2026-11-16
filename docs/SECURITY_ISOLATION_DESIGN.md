# SECURITY ISOLATION DESIGN — POCKET HOST

**Status:** design and proof work only; production sandbox not accepted.  
**Normative implementation plan:** `docs/SECURITY_GATE_2_EXECUTION_PLAN.md`.

## 1. Threat model

Treat Python/Node application code as potentially compromised. It must not read sibling projects, host-private files, management sessions, tunnel credentials, backup keys or device-bound secrets. It must not create arbitrary outbound network connections, reach loopback/private/link-local/metadata endpoints, persist across deletion, or exhaust the phone without bounded controls.

Rust static hosting must remain available when a runtime cell crashes or is stopped.

## 2. Current verified boundary

| Area | Current source behavior | Conclusion |
|---|---|---|
| Host/companion | Different Android package UIDs | Host-private file separation improved |
| Project identity | Shared companion UID | No per-project isolation |
| Python | Multiple site roots may share WSGI process | Cross-project state/file access risk |
| Node | Separate process but same companion UID | Not a distinct security identity |
| Python listener | Pinned loopback | Listener exposure control only |
| Node preload | Restricts ordinary TCP listen calls | Not native or outbound network control |
| Egress | Companion INTERNET permission | No OS-enforced per-project denial |
| Archive checks | Bounds and path/credential filters | Import hardening, not sandboxing |

## 3. Required architecture

One Android isolated service process/UID per executable project. Trusted broker handles allocation, archive staging, lifecycle and capability transfer but never executes customer code. A cell receives only its own project/scratch data capability and a host-created visitor listener capability. Rust remains the only public ingress.

A native fail-closed kernel policy must be installed before Python/Node initialization. Deny socket creation and outbound network paths; permit only the explicitly brokered visitor transport. Filesystem and network controls must be enforced by OS/kernel mechanisms, not language-level monkey patches.

## 4. Feasibility risks

Android 8/API 26 SELinux and isolated-process behavior may reject directory-FD or socket-FD access. The previous Binder-passed TCP socket probe failed under SELinux. The newer abstract Unix-domain listener and seccomp probe is still a proof spike. Do not assume it passes because it compiles. If descriptors are blocked, test a brokered descriptor-backed storage/stream design; never weaken SELinux, expose parent directories or grant INTERNET to the project cell to make a demo work.

## 5. Adversarial acceptance

- Distinct UIDs for two concurrent projects.
- Own project read/write succeeds; sibling, parent and host paths fail.
- Credentials, account/session state and tunnel tokens inaccessible.
- socket/socketpair/connect/bind, DNS, TCP, UDP, raw, IPv4/IPv6 and native bypasses fail.
- Loopback management, private, link-local and metadata targets unreachable.
- Child/native code cannot escape the same restrictions.
- Revoked/stale FDs and cross-project stream injection fail.
- Cell crash, OOM, timeout and restart do not affect static hosting.
- API 26, 33 and 35 pass on one exact SHA; independent review accepts findings.

## 6. Operational policy

Until acceptance, Python/Node are owner-trusted-code-only. Never expose marketplace or visitor-submitted execution. Do not claim multi-tenant sandboxing, egress enforcement, hard CPU quotas or production security. See North Star Gate 2 for release dependency and exit criteria.
