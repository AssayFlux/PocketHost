# Node.js Runtime Provider — Pocket Host 0.1.0

## Placement

Node.js is an optional customer-language runtime installed on the mobile website-serving node only in 0.1.0. It is not installed on storage, traffic, build or other worker nodes. Build workers may produce immutable artifacts; the mobile serving node verifies and activates them.

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
