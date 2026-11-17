# Pocket Host 0.1.0 — Architecture Decisions

## ADR-001 — Mobile command center

**Decision:** Android and iOS mobile applications are the primary owner interfaces. The phone coordinates, authorizes and observes the fleet. Hosts do not depend on the phone process for ongoing workloads.

**Reason:** the phone is readily available to the owner, but it is mobile, power-constrained and intermittently connected. It must not become a runtime or availability bottleneck.

## ADR-002 — Independent components and fault domains

**Decision:** host agent, website ingress, service component, runtime provider, storage provider, tunnel connector, control plane and edge gateway have distinct lifecycle, state, health, permissions and recovery. Interconnection uses authenticated versioned contracts.

**Reason:** a failure should be contained to the smallest responsible component.

## ADR-003 — Control plane and data plane separation

**Decision:** management, identity, enrollment, commands and audit are separate from visitor traffic, service-to-service traffic and MCU operations. Public website ingress never exposes management or device-control endpoints.

## ADR-004 — Local autonomy and offline operation

**Decision:** each host stores desired state, authorization lease, active release and recovery data locally. Hosts continue the last authorized configuration during mobile/control-plane outages. Sensitive new changes wait for authorization.

## ADR-005 — Mobile parity

**Decision:** Android and iOS use one versioned control contract and equivalent fleet-management capabilities. Platform-specific UI and OS restrictions are respected. iOS is not promised as an always-on arbitrary host.

## ADR-006 — Capability-based device support

**Decision:** devices advertise measured hardware/OS/security capabilities. Service placement uses exact compatibility profiles. Raspberry Pi is an SBC; MCU capabilities are bounded and board-specific.

## ADR-007 — Multi-component, multi-runtime websites

**Decision:** a website is a service graph whose components can use different runtime providers and run on eligible hosts. Each component has independent process identity, storage, resource budget and explicit internal routes.

## ADR-008 — Independent tunnel providers

**Decision:** Local Network, first-party AssayFlux Relay and Cloudflare Tunnel are independent connectivity choices. Relay and Cloudflare may operate concurrently. Failure in one does not interrupt the others or local hosting.

## ADR-009 — DemonCore remains separate

**Decision:** DemonCore owns MCU firmware, DCP, board safety and constrained fleet behavior. Pocket Host integrates through an authenticated allow-listed adapter. Repositories remain independently buildable and deployable.

## ADR-010 — Version 0.1.0 baseline

**Decision:** 0.1.0 is the product and architecture baseline. Independently deployed components declare their own versions, protocol compatibility, build provenance and rollback path.
