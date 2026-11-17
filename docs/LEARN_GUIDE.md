# Pocket Host 0.1.0 — Owner Guide

## 1. The Pocket Host model

Use the Android or iOS mobile command center to manage a fleet of devices. A fleet can include phones, computers, servers, Raspberry Pi-class boards and supported microcontrollers. Each device runs only the services supported by its verified capabilities.

The mobile phone is the owner's control center. Host devices supervise their own services and keep working from their last authorized configuration when the phone or remote control plane is unavailable.

## 2. Add a device

1. Open Devices in the mobile command center.
2. Choose nearby discovery, QR pairing or manual pairing.
3. Confirm the device identity fingerprint, owner and requested capabilities.
4. Approve the requested role and permissions.
5. Wait for the host to report its signed capability profile and health.
6. Review the device's supported service catalog before deploying workloads.

Discovery alone never enrolls or trusts a device.

## 3. Deploy a website or service

1. Create a website/service project.
2. Add one or more components (static frontend, Python, Node or another supported provider).
3. Review each component's runtime, resource budget, storage and network needs.
4. Select an eligible host or allow compatibility-aware placement.
5. Deploy the signed release and wait for health checks.
6. Configure the hostname and visitor access policy.
7. Choose local ingress or one or more enabled public tunnel providers.
8. Verify from an independent network and create a backup.

A website can contain multiple runtimes and components. Each component has independent health and controls.

## 4. Manage the fleet

From the mobile command center:
- View host health, stale/offline state and active services.
- Approve or revoke devices and team members.
- Start, stop, restart, update, drain and roll back a component.
- Inspect resource budgets, diagnostics and audit history.
- Configure Local Network, AssayFlux Relay and Cloudflare Tunnel independently.
- Back up and restore project data.
- Review security and pending approvals.

## 5. Offline behavior

If the phone disconnects, running services on hosts continue. If the remote control plane is unavailable, hosts keep their last authorized state. New sensitive changes may wait until an owner can authenticate. Reconnection reconciles desired and observed state.

## 6. Device capability guidance

- Android devices can host supported workloads within Android lifecycle, power, thermal and storage limits.
- iPhone is a command center; iOS background restrictions do not permit a general always-on arbitrary host promise.
- Linux, Windows and macOS hosts use their native service/security adapters.
- Raspberry Pi and other SBCs require exact model and OS profiles.
- Microcontrollers provide bounded device APIs, telemetry and control, not arbitrary full-stack runtimes by default.

## 7. Security and recovery

Use strong owner authentication, device-bound keys, scoped permissions and revocation. Do not upload secrets with website source. Backups do not automatically transfer device identity. Test restore and keep recovery material separate.

If one component fails, inspect that component's health and logs. Unrelated hosts and services should remain available. Avoid global restarts unless diagnostics identify a host-wide failure.
