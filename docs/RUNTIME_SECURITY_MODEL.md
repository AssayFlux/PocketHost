# PocketHost runtime security model and release gaps

## Runtime trust boundary

The main PocketHost APK and Runtime Worker APK have different Android application UIDs. Node.js and CPython project code execute in the Runtime Worker package, not in the main PocketHost package. The companion Binder service is protected by a signature-level permission and checks the calling package and signing certificate.

**This is not a per-project operating-system sandbox.** The Python WSGI host explicitly loads trusted project modules in the companion process. Node.js runs in the companion's `:node` process, but under the same companion application UID. A project can therefore potentially access other files and data that are readable to that companion UID. The `RuntimeCellProbeService` and seccomp tests validate an isolated-process feasibility/security probe; they are not proof that production Python/Node hosting currently runs inside a separate isolated UID or that the tested seccomp policy is applied to every customer process.

The owner acknowledgement in `RuntimeTrustGate` is therefore a trust gate, not a sandbox. Run only code you trust. Do not use the current runtime worker to execute arbitrary third-party projects or hostile multi-tenant workloads. Sensitive files are filtered during project transfer, but this does not replace OS-level per-project isolation.

## Implemented hosting model

- Static websites are served by the native Rust HTTP engine under the main app's service lifecycle.
- Node.js and Python execute in the separately installed Runtime Worker package.
- The worker and host communicate over a signature-protected Binder protocol.
- Visitor ingress is routed through the local hosting gateway; the phone's public reachability depends on its network, firewall and carrier.
- A public relay/gateway is required when direct inbound routing is not available (for example, CGNAT).

## Explicitly incomplete product areas

- The mobile Deployments page currently describes the missing publish-to-live and historical rollback controls. Workbench snapshots are local drafts, not production deployments.
- In-app Cloudflare Access policy management is not implemented. Access policies must be managed and verified in Cloudflare's dashboard.
- A green DNS update does not prove that the phone is reachable from the public internet.
- The Android integration workflow does not substitute for a physical Vivo Y95 acceptance test.

## Recommended production gate

Before describing PocketHost as safe for untrusted multi-tenant code, implement and test a real per-project execution boundary (separate Android isolated process/UID or another enforceable sandbox), capability-scoped file access, explicit network policy, process/resource limits, and lifecycle supervision for every production runtime. Test that a compromised site cannot read another site's files, host-app data, credentials, or runtime control Binder.
