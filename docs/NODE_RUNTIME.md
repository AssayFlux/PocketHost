# Node.js Runtime Add-on — Future Runtime Library

**Status: optional future add-on.** Node.js is not part of the main Pocket Host app or Step 4 and is not required for HTML/CSS/JavaScript/TypeScript static websites. The current companion integration is an experimental foundation; this document is a future package specification, not a release claim.

## Placement

When released, Node.js will be an optional user-downloaded runtime package for the mobile website-serving node. It will not be installed on storage, traffic, build or other worker nodes. Build workers may produce immutable artifacts; the mobile serving node verifies and activates them.

## Current Android provider

The Android provider currently integrates Node.js mobile 24.22.1 through a native JNI bridge in the runtime companion. Dependencies must be bundled. Desktop Node package managers, arbitrary shell commands, process managers and unrestricted child processes are not implied.

## Security status

The Android Node process shares the runtime companion application UID with other runtime projects. A preload/listener restriction is not an OS-enforced network sandbox and does not block native/outbound socket bypasses. Run only owner-controlled trusted code until per-project OS isolation and egress enforcement pass adversarial tests.

## Runtime pack contract

Declare Node version, Android ABI, native dependency compatibility, license inventory, digest, signature, startup adapter, health check, CPU/memory limits and rollback version. User installs Node only when a site needs it.

## Acceptance

- Static site works when Node is absent or stopped.
- Process lifecycle, HTTP adapter, cancellation and shutdown are bounded.
- Node crash does not stop Python or static serving.
- Test project cross-access, native socket escape, malicious dependency, resource exhaustion and interrupted update.
- Upgrade and rollback preserve project data.
