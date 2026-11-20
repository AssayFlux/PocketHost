# Pocket Host — Product North Star

**Purpose:** define the direction of the product without confusing the long-term ambition with the v0.1.0 delivery promise.

## 1. Product vision

Pocket Host should let an owner turn an Android device into a useful, understandable and controllable hosting node. The owner can create and manage websites and supported application workloads, serve them locally or on a trusted LAN, and selectively expose them to the public internet through an authenticated and recoverable network path.

The Android device remains a first-class host. Remote management is an additional capability, not a prerequisite for the phone to host its own content.

## 2. Product principles

1. **Android-first, independently useful:** local website hosting and essential administration must work without a remote control plane.
2. **Native architecture:** Android services and native Rust/JNI components; no Termux-based relay dependency.
3. **Owner control:** explicit approval for pairing and sensitive actions; least privilege, revocable credentials and auditable state.
4. **Secure by demonstrated design:** default-deny boundaries, bounded resources, isolated site data, safe path handling and honest runtime security claims.
5. **Recoverable operation:** survive ordinary process, reboot and network transitions without silently losing routes, credentials or site state.
6. **Clear trust boundaries:** static hosting, trusted owner-run runtimes and hostile/untrusted workloads are different security classes.
7. **Evidence over feature count:** a feature is shipped only when its acceptance tests prove its intended behavior.
8. **Incremental compatibility:** preserve working code and introduce changes in small, reviewable steps.

## 3. Long-term capability map

### A. Android hosting node
- Manage multiple websites and their files.
- Import projects, inspect/edit content, back up and restore data.
- Serve static content reliably with safe path resolution, correct content types, bounded resource use and site separation.
- Provide clear service status, logs, diagnostics, schedules and recovery behavior.

### B. Secure public access
- Provide a native relay path with owner-approved device identity and pairing.
- Issue, rotate and revoke credentials safely.
- Enforce route-to-site isolation and durable revocation.
- Support a validated TLS and DNS ownership flow.
- Recover from connectivity changes and clearly report when the current network cannot support the selected exposure mode.

### C. Application runtimes
- Support explicitly declared runtime versions and workloads.
- Keep static hosting independent of optional runtime companions.
- Enforce resource, filesystem, network and lifecycle boundaries appropriate to each trust class.
- Add stronger isolation only when its guarantees can be tested and stated accurately.

### D. Remote operations and connected devices
- Allow the owner to enroll and authorize additional devices.
- Provide authenticated remote status, commands, audit history and revocation.
- Keep protocol contracts versioned and default-deny.
- Offer a web console and Linux agents as optional clients, not mandatory dependencies for local hosting.

### E. Developer and deployment experience
- Provide a safe site workspace, preview, change review, immutable releases and rollback.
- Separate drafts from visitor-facing production content.
- Require explicit owner approval for production activation.

## 4. Non-goals for the first preliminary plan

The following are not commitments for v0.1.0:
- Full multi-node orchestration, clustering, autoscaling or distributed hosting.
- A general-purpose hostile-code execution platform or claim of container-grade isolation on Android.
- Broad language/runtime catalogue support beyond specifically tested runtime paths.
- Full production deployment/build pipeline for arbitrary npm packages or scripts.
- Enterprise fleet administration, billing, multi-tenant SaaS or plugin marketplace.
- Automatic DNS management across every provider.
- Feature-complete web console and Linux agent parity.
- A promise of direct public reachability on every carrier/Wi-Fi network.

## 5. What v0.1.0 means

v0.1.0 remains an **Alpha** release. Its purpose is to deliver one dependable Android hosting node with tested local hosting, reliable service behavior, one fully validated public-access path, and essential owner/security controls. Optional or incomplete subsystems must be clearly labeled and must not weaken the core.

The precise acceptance contract is in [the preliminary five-step plan](PRELIMINARY_PLAN.md). Later versions are not to be planned as active implementation tracks until the owner has installed and tested v0.1.0 and decides what comes next.
