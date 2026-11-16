# Gate 2 — trusted broker and project filesystem implementation

**Scope of this increment:** broker primitives and filesystem capability handling (implementation source added; Android integration remains unverified until Gate 8).

## Trusted broker responsibilities

The host-side `RuntimeCellLeaseRegistry` now provides:
- bounded cell reservations (default four; hard configuration ceiling eight);
- one active lease per project;
- explicit RESERVED → ACTIVE → DEAD lifecycle;
- activation only with a positive UID distinct from the host UID;
- rejection of duplicate active isolated UIDs;
- no reuse of an ACTIVE slot until the bound process death path calls `markProcessDied`;
- fail-closed admission when the pool is full rather than silently sharing a process/UID.

The `RuntimeProjectCapabilityBroker` keeps project roots inside the trusted host process. It issues random, process-local capability tokens and opens only one requested file descriptor at a time. Tokens are revocable; already-transferred descriptors are not magically revoked, so revocation of an active cell requires terminating that cell and closing its descriptors.

These are broker primitives, not evidence that the existing runtime companion has already been migrated to isolated cells. The Binder service binding, runtime startup, death-recipient wiring and project migration must use these primitives before customer code can be treated as isolated.

## Filesystem capability rules

- No project directory descriptor or host path is sent to a cell.
- The root and each intermediate directory are opened with `O_DIRECTORY | O_NOFOLLOW`.
- The final component is opened with `O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK`.
- Only regular files are eligible; symlinks, traversal, absolute paths and special files are rejected.
- File size is bounded to 64 MiB.
- Host writes can create/truncate or append only inside existing project directories; created files use owner read/write mode.
- Parent-directory creation remains a trusted broker operation and must not be delegated to project code.

## Important limits and next integration work

The current production Python/Node worker still stores project trees in companion-private storage and shares the companion UID. The new lease/capability primitives do not change that runtime path by themselves. Android isolated processes also cannot be assumed to access app-private paths directly. The service must receive only broker-opened descriptors, and the Python/Node filesystem adapter must route all project file operations through the brokered capability interface.

A passed file descriptor remains usable until the receiver closes it or its process exits; a token revocation cannot retract a duplicated descriptor. Do not expose the token to project code. Do not weaken SELinux or grant the cell broad storage/network permissions.

## Verification status

- Source-level broker primitives added.
- Android API 26/33/35 service binding, UID uniqueness, FD transfer, runtime filesystem compatibility and adversarial tests: **deferred to Gate 8** by owner instruction.
- Python/Node are still owner-trusted-code-only. This increment does not pass Gate 2 or authorize untrusted/third-party project execution.
