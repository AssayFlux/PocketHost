# Pocket Host Interoperability Protocol

**Protocol ID:** pockethost.device/1.0  
**Release assignment:** v0.2.0 Connected Device Fabric

This directory contains the machine-readable contracts shared by Android Pocket Host, the optional control plane and separately installed device agents. Implementations must consume the schemas and policies here rather than inventing platform-specific message formats.

## Compatibility rules

- JSON Schema 2020-12 defines message structure.
- Unknown protocol major versions are rejected.
- Within a major version, only additive optional fields are compatible.
- Changes to signature canonicalization, authorization, required fields or state transitions require a new major version.
- Unknown roles, capabilities, operations and board commands are rejected by default.

## Message flow

Candidate agent presents identity and proof. Mobile owner verifies and approves an exact role. A signed grant is delivered to the agent. Jobs are typed, target-bound, grant-bound, expiring and idempotent. The agent persists state and verifies authorization before side effects. Revocation advances grant generation.

## Schemas and policy

- schemas/: versioned message JSON Schemas.
- ROLE_CAPABILITY_POLICY.json: default-deny role/capability allow-list.
- Job policy: typed operations and bounded payload/role policy.
- Validation scripts and tests: protocol shape and trust-state simulations.

## Product boundaries

The mobile device remains the website-serving node in both operating modes. server_only disables fleet administration; command_center_server enables it. Neither mode creates a standalone command-center-only configuration.

Static website serving does not depend on this protocol, control plane or remote agents. The protocol is used for the v0.2.0 connected device fabric. Python/Node runtime companion IPC is a separate Android integration and must not be confused with the cross-device agent protocol.

See [Device Trust and Protocol](../docs/STEP_1_TRUST_AND_PROTOCOL.md), [Host Agent Contract](../docs/HOST_AGENT_CONTRACT.md) and [Release Gates](../docs/RELEASE_GATES.md).
