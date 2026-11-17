# Pocket Host 0.1.0 — Website Deployment and Runtime Placement

## Serving model

The mobile website-serving node is the final website origin in the 0.1.0 product baseline. The mobile owner selects exactly one of two modes: Server only or Command Center + Server. Server only is a serving-only mode and cannot enroll or assign worker devices. Customer-selected language runtimes are installed on this mobile serving node only.

Worker devices support the serving node through assigned infrastructure jobs: storage, traffic proxy/cache, build and publish preparation, backups and approved native backend/job adapters. They do not silently host customer-selected Python/Node/PHP runtime components in this baseline.

## Website composition

A website may contain:
- Static frontend.
- One or more customer-selected language components.
- Persistent data and object-storage references.
- Build artifacts and deployment releases.
- Approved worker-backed storage, cache or native job services.

A component declares language/runtime version, resource limits, health check, storage namespace, network permissions, secrets and release compatibility.

## Runtime Manager

- Separate signed and versioned runtime packs per language and supported ABI.
- User explicitly selects which languages and versions to install.
- No unneeded language runtime is installed.
- Runtime manager verifies signatures, digests, license, ABI and compatibility.
- Each project/component executes under a separate OS-enforced identity and scoped brokered capabilities.
- Runtime process lifecycle, logs, health and resource budgets are independent.
- Runtime crash does not stop Rust ingress, static files or sibling runtime components.

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

Test static hosting with no runtimes, multiple selected languages on the mobile serving node, project isolation, runtime crash, worker loss, invalid artifact, build failure, interrupted activation, rollback, storage outage, traffic worker outage and command-center disconnect.
