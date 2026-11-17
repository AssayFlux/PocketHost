# Pocket Host 0.1.0 — User Guide

## 1. Choose the mobile mode

- **Command Center:** use the phone to manage devices; mobile website serving is off.
- **Server:** use the phone to serve websites.
- **Command Center + Server:** manage the fleet and serve websites from the same phone.

The operating system may limit background hosting. The app shows only modes supported by the device and OS.

## 2. Add and verify a device

1. Install the matching Pocket Host software on the target device.
2. Open Devices in the mobile command center.
3. Discover on Wi-Fi/Bluetooth, scan its QR code, enter its device ID/code, or use a supported USB/remote flow.
4. Compare the device fingerprint, platform, software version and capabilities.
5. Review the requested role and permissions.
6. Approve the one-time challenge.
7. Confirm the device is enrolled and connected.

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

1. Enable Server or Command Center + Server mode on the mobile serving node.
2. Create/import the website.
3. Choose static hosting and the language components it needs.
4. Install only the required signed language runtime packs on the mobile serving node.
5. Optionally assign a worker to build/test the website or store encrypted data.
6. Review the artifact digest and test result.
7. Approve activation on the mobile serving node.
8. Select Local Network, AssayFlux Relay or Cloudflare Tunnel for visitor access.

In 0.1.0 customer-selected language runtimes execute on the mobile serving node. Worker devices provide infrastructure support; they do not silently become Python/Node runtime hosts.

Static websites do not require a language runtime.

## 5. Connect devices

- Wi-Fi/LAN is preferred for secure control and bulk transfers.
- Bluetooth supports nearby discovery, pairing and small control messages.
- USB works only on supported hardware and OS APIs.
- Remote links use authenticated encrypted connections.

Changing transport does not change device identity or permissions.

## 6. Monitor and recover

Review device health, assigned roles, job state, resource pressure, storage, traffic and last-seen time. Pause or revoke a role if a device is compromised. A device continues its last approved local jobs during a temporary phone/control-plane outage, within its authorization lease and local policy.

## 7. Edge devices

Use a supported Raspberry Pi/SBC gateway for DemonCore/MCU integration. MCU devices expose only board-approved sensor, actuator and telemetry capabilities. They are not general-purpose website runtime hosts.

## 8. Security

Use strong owner authentication, device-bound keys, one-time enrollment challenges and least-privilege role grants. Do not upload device keys with website source. Backups do not clone device identity. Test restore and keep recovery material separate.
