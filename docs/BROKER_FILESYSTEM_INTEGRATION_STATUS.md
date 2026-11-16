# Broker and project-filesystem integration status

**Status: partial source implementation; not an accepted isolation boundary.**  
**Owner rule:** Android compilation and device/instrumentation testing are deferred until Gate 8.

## Implemented source on main

- `RuntimeCellLeaseRegistry` reserves a bounded slot for one project, rejects a second lease for the same project, records the isolated UID reported by the service, rejects duplicate active UIDs, and will not recycle an active slot until process death is observed.
- `RuntimeCellCapabilityBroker` binds each random, process-local file capability to the exact lease, project ID and isolated UID. Each operation re-checks that the lease remains active and that the trusted IPC boundary supplied the caller UID observed from Binder. Lease revocation invalidates all grants.
- `RuntimeProjectFileBroker` exposes read-only single-file descriptors and bounded broker-mediated writes. It rejects traversal and symlink components, checks regular-file type, and caps individual writes and resulting file size.
- Writable file descriptors are explicitly legacy-probe-only and must not be passed to project code.

## Required host integration

The classes above are not yet a complete broker service. The production host Binder boundary must:

1. Authenticate the companion package and signing identity.
2. Bind a declared isolated cell service and obtain its UID from a Binder transaction implemented inside that process.
3. Reserve/activate a lease only after checking the observed UID; never trust a UID or project ID supplied by project code.
4. Issue a project capability only for that active lease.
5. Route each file read/write through the host broker, passing `Binder.getCallingUid()` obtained inside the transaction.
6. Revoke all grants on Binder death, service disconnect, explicit stop, project replacement and host recovery.
7. Refuse allocation when the bounded cell pool is full; never fall back to the shared companion UID.

## Required runtime migration

The current `RuntimeWorkerService` still extracts projects under the companion's shared `filesDir/projects`, starts Python from that shared tree, and starts Node using the companion runtime service. The manifest currently declares only one isolated proof service, not a production cell pool. Therefore:

- Steps 2 and 3 are **not end-to-end complete**.
- Python and Node are **not yet isolated per project**.
- The new capability broker is not yet the filesystem adapter used by Python or Node.
- The existing shared-storage runtime path must not be described as a sandbox.
- Python/Node remain owner-trusted-code-only; do not enable arbitrary third-party or visitor-submitted code.

## Filesystem adapter acceptance

Before marking step 3 complete, migrate archive staging and runtime file access so that a project cell never receives a host path or parent/sibling directory descriptor. The adapter must provide only project-relative operations, enforce per-file and aggregate quotas, reject symlink/hard-link and special-file escapes, and revoke access after cell death. Test own-project read/write, sibling/parent/host-secret denial, path traversal, symlink races, stale capability use, concurrent writes, disk-full behavior and interrupted replacement.

## Cell broker acceptance

Before marking step 2 complete, wire lease allocation to real isolated service bind/unbind and Binder death handling. Prove two simultaneous projects have distinct observed UIDs; prove that a lease cannot be rebound to a different project or reused before process death; and prove every capability operation checks the actual Binder caller UID. A registry unit test alone is not evidence of Android UID isolation.

## Verification policy

No Android build or instrumentation run is performed before Gate 8, per the owner's sequencing instruction. Source presence is not runtime verification. The Gate 2 acceptance matrix remains open until API 26, 33 and 35 tests pass on one exact source SHA and independent security review is complete.
