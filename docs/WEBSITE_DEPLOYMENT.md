# Pocket Host 0.1.0 — Website and Service Deployment

## Deployment model

A website is a versioned service graph. It may contain a static frontend, multiple runtime components, persistent data services and approved edge-device capabilities. Components may run on one host or multiple compatible hosts.

## Component manifest

Every component declares:
- Stable component and site identity.
- Runtime/provider and version.
- OS, architecture, ABI and dependency requirements.
- Entry point, health/readiness checks and startup policy.
- CPU, memory, storage, connection and queue budgets.
- Persistent volumes and backup policy.
- Required secrets and capability grants.
- Internal service routes and visitor routes.
- Update, rollback and compatibility policy.

## Placement

The scheduler places a component only on a host whose signed capability profile satisfies the manifest. Placement can be pinned or policy-selected. Resource limits and data locality are considered. A registered device is not automatically eligible.

## Deployment lifecycle

1. Validate manifest, signature, compatibility and policy.
2. Stage immutable artifacts and data migrations.
3. Verify hashes, file paths, quotas and publisher identity.
4. Create isolated component identity and capabilities.
5. Start in staged state and run health checks.
6. Atomically activate routes.
7. Retain last-known-good release for rollback.
8. Record deployment result and audit event.

## Routing

Visitor traffic enters through Local Network, AssayFlux Relay or Cloudflare Tunnel and terminates at the selected host's website ingress. The site router forwards only declared routes to healthy components. Internal component calls use authenticated service identity and explicit allow-lists. Management and device-control APIs are never visitor routes.

## Multi-runtime support

A single site may use multiple runtime providers. Python, Node, PHP and other languages are supported only when a compatible provider exists for the target OS/architecture and has passed isolation and resource acceptance. Installing a runtime pack does not create an isolated project identity.

## Static-first behavior

Static content is served independently from dynamic runtimes. A Python/Node/runtime crash must not stop static content or sibling components. Site-level health reports component-specific state rather than masking a failed dependency.

## Acceptance

Test invalid manifests, incompatible architecture, missing runtime, signature failure, archive traversal, interrupted deployment, failed health check, rollback, component crash, cross-host routing, host loss, control-plane outage and public-ingress isolation.
