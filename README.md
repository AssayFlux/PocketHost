# Pocket Host

Pocket Host is an Android-first, self-hosted platform for managing and serving websites and application workloads from an Android device, with optional secure public access and connected-device management.

## Current development line

- Active branch: `Alpha`
- Current planning baseline: `8879ead946fe985d056970d1614068d1baea562b`
- Release target: `v0.1.0` (Alpha; not yet declared complete)
- Architecture direction: native Android services and Rust/JNI relay components. Termux is not the relay architecture.

## Project documents

- [Repository baseline](docs/BASELINE.md): what exists now and what remains unverified.
- [North Star](docs/NORTH_STAR.md): long-term product direction and boundaries.
- [Preliminary five-step plan](docs/PRELIMINARY_PLAN.md): the scoped sequence for completing the first useful release.

## Development rule

All current planning and implementation commits for this effort go directly to `Alpha`, as requested. Do not create or switch to another development branch, and do not start a later-version upgrade track before v0.1.0 has passed its acceptance gates and the owner has tested it.

A feature is not complete merely because source code or a screen exists. Completion requires relevant automated checks and, where applicable, emulator, physical-device, and real-network evidence.
