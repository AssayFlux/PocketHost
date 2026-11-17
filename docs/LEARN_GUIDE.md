# Pocket Host 0.1.0 — User Guide

## 1. Choose the mobile mode

- **Server only:** use the phone to serve websites. Fleet device enrollment, verification approvals and role assignment are unavailable.
- **Command Center + Server:** manage and verify the fleet, assign device jobs and serve websites from the same phone.

The operating system may limit background hosting. The app shows only modes supported by the device and OS.

## 2. Add and verify a device

1. Install the matching Pocket Host software on the target device.
2. Open Devices in the mobile command center.
3. In the current Android implementation, use the manual enrollment form to enter the candidate device identity and metadata, create the signed challenge, and transfer the proof-request document to the candidate agent.
4. Verify the returned metadata-bound proof signature and compare the device fingerprint, platform, software version and capabilities.
5. Review the requested role, capabilities and resource budget.
6. Confirm owner approval using the app's current biometric/device-credential confirmation flow.
7. Confirm the device appears in the local registry. This is a local trust foundation, not yet a fully connected LAN discovery or agent lifecycle.

A verification code is short-lived and single-use. It is not a permanent password. Discovery alone never enrolls or trusts a device.

## 3. Assign a job

Choose an enrolled device and assign a compatible role:
- Storage.
- Traffic handling.
- Build and publish preparation.
- Native backend or scheduled jobs.
- Backup and recovery.
- Edge gateway for DemonCore/MCUs.

Review data access, network routes, permissions, resource budgets and expiry before approval. A device may be rejected if its OS or hardware cannot safely perform the job.

## 4. Publish a website

1. Enable Server only or Command Center + Server mode on the mobile serving node.
2. Create/import the website.
3. Start with static HTML, CSS and browser JavaScript; these do not require the runtime companion.
4. TypeScript website compilation is a planned built-in main-app capability; it is not yet available in the current Android build.
5. If a site needs Python WSGI or Node.js server-side execution, install the separate runtime companion APK. These integrations are in progress, shipped together, and should run only trusted owner-controlled code until isolation testing is complete.
6. Optionally assign a worker to build/test the website or store encrypted data.
7. Review the artifact digest and test result.
8. Approve activation on the mobile serving node.
9. Select Local Network, AssayFlux Relay or Cloudflare Tunnel for visitor access.

Python and Node.js currently run through the optional Android companion on the mobile serving node; the future 20-package Runtime Library is not yet delivered. Worker devices provide infrastructure support; they do not silently become customer-language runtime hosts.

Static websites do not require a language runtime.

## 5. Connect devices

- Wi-Fi/LAN is the preferred target transport for secure control and bulk transfers; current enrollment is still a manual challenge/proof exchange.
- Bluetooth discovery/pairing and USB provisioning are planned transport paths, not yet end-to-end supported flows.
- Remote links use authenticated encrypted connections; the current Linux agent HTTPS bearer preview is not mutual TLS.

LAN discovery and authenticated transport are still being implemented. BLE and USB are planned transport paths, not currently supported end-to-end pairing flows. Changing transport must never change device identity or permissions.

## 6. Monitor and recover

Review device health, assigned roles, job state, resource pressure, storage, traffic and last-seen time. Pause or revoke a role if a device is compromised. A device continues its last approved local jobs during a temporary phone/control-plane outage, within its authorization lease and local policy.

## 7. Edge devices

Use a supported Raspberry Pi/SBC gateway for DemonCore/MCU integration. MCU devices expose only board-approved sensor, actuator and telemetry capabilities. They are not general-purpose website runtime hosts.

## 8. Security

Use strong owner authentication, device-bound keys, one-time enrollment challenges and least-privilege role grants. Do not upload device keys with website source. Backups do not clone device identity. Test restore and keep recovery material separate.
