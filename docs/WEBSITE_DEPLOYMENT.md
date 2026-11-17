# Pocket Host 0.1.0 — Website Deployment and Runtime Placement

## Serving model

The mobile website-serving node is the final website origin in the 0.1.0 product baseline. The mobile owner selects exactly one of two modes: Server only or Command Center + Server. Server only is a serving-only mode and cannot enroll or assign worker devices. Python 3.11 and Node.js 24.22.1 are currently optional runtimes in the separate Android `runtime-worker` companion APK on this serving node; both are in progress. The future independent Runtime Library is post-baseline.

Worker devices support the serving node through assigned infrastructure jobs: storage, traffic proxy/cache, build and publish preparation, backups and approved native backend/job adapters. They do not silently host customer-selected language runtime projects in this baseline.

## Website composition

A website may contain:
- Static frontend.
- One or more customer-selected language components.
- Persistent data and object-storage references.
- Build artifacts and deployment releases.
- Approved worker-backed storage, cache or native job services.

A component declares language/runtime version, resource limits, health check, storage namespace, network permissions, secrets and release compatibility.

## Current runtime reality and future manager

- The main app's static HTML/CSS/JavaScript server works without Python or Node.js.
- Python 3.11 (Chaquopy) and Node.js mobile 24.22.1 (JNI/libnode) are present in the separate `runtime-worker` companion APK; they are currently delivered together, not as independent packages.
- The app has host-side runtime client/lifecycle integration, but both runtime implementations remain in progress and require Android device acceptance.
- Both runtimes share the companion application UID. This is not per-project OS isolation; execute only trusted owner-controlled projects until adversarial isolation and egress tests pass.
- The future Runtime Library will provide one separately signed/downloadable package per language. Its 20 manifests are catalog metadata only; no independent package artifacts or completed Runtime Manager are present.
- The main-app TypeScript-to-JavaScript compiler is a separate Step 4 requirement and is not yet bundled/verified. It is not a server-side runtime.

## Workbench and live release separation

Workbench is the editing and release-review surface for a site. It keeps a distinct workspace from the active immutable release. Visitors continue to receive the active release while the owner edits, saves, previews and commits. A local commit is not itself a deployment.

Publishing validates and stages an immutable artifact, previews and health-checks it, presents a diff and requires explicit owner approval before atomic activation. A failed build or interrupted activation must preserve or restore the previous known-good release. Rollback is an audited activation of a prior immutable release. See [Workbench specification](WORKBENCH.md).

The current SiteFileManager targets the deployed site tree and is not sufficient as the final Workbench data model. SiteImporter release activation/recovery code must be inspected and tested before being relied on for this workflow.

## Build and publish

1. Owner chooses source and target release.
2. Assigned build worker receives only scoped source/build inputs.
3. Worker builds/tests and returns immutable artifact plus digest and provenance.
4. Mobile serving node verifies signature/digest/compatibility and scans metadata.
5. Release is staged in isolated storage and health-checked.
6. Owner-authorized deployment atomically activates routes.
7. Last-known-good release remains available for rollback.

Build workers cannot directly write the active serving directory, change DNS, alter tunnel routes or activate a release.

## Storage and traffic workers

Storage nodes expose only encrypted, owner-assigned namespaces. Traffic nodes receive exact approved site routes and bounded proxy/cache permissions. Neither role can access owner management, runtime brokers, device enrollment or MCU control.

## Acceptance

Test static hosting with the companion absent/stopped, Python/Node lifecycle and companion process failures, trusted-code acknowledgement, cross-project access and network escape, TypeScript compilation once implemented, invalid artifact, build failure, interrupted activation, rollback, storage outage, traffic worker outage and command-center disconnect.
