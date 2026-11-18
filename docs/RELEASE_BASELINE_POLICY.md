# Pocket Host — Immutable Release Baseline Policy

## Purpose

A successful release baseline is a preserved, reproducible product snapshot. The v0.1.0 source and artifacts must remain identifiable after development continues.

## v0.1.0 baseline procedure

The baseline is created only after all ten release gates (G0–G9) are PASS and the owner approves the release.

1. Freeze the exact release-candidate source commit.
2. Run the complete required CI, Android install/upgrade, physical-device, external-network, security, reliability and recovery acceptance.
3. Produce the release APK and any separately shipped v0.1.0 Relay artifacts.
4. Record SHA-256 for every release artifact, the source commit SHA, workflow run URLs, target device/OS/ABI matrix, test reports, known issues and reviewer approval.
5. Create an annotated, signed release tag named `v0.1.0` where signing credentials are available. The tag must point to the exact accepted source commit and must never be moved or deleted.
6. Preserve a `release/v0.1.0-baseline` branch at that same commit. Enable branch protection/rulesets to prohibit direct pushes and force-pushes where repository permissions allow.
7. Start v0.1.1 work on a separate branch from the baseline commit. Do not merge later development back into the v0.1.0 baseline.

## Change policy

- No feature, refactor, dependency refresh or ordinary bug-fix commit is added to the v0.1.0 baseline after it is locked.
- Later releases branch from the immutable v0.1.0 commit and carry forward changes through normal reviewed development.
- If a critical security issue requires correction after release, create a separately reviewed successor patch release from the preserved baseline. Never rewrite the original tag or silently alter the baseline branch.
- A release tag, source SHA and artifact digest are the authoritative identity of the baseline. Branch protection is an administrative control, not a claim that repository owners or GitHub administrators are technically incapable of changing repository policy.

## Release evidence ledger

The release record must contain:

| Field | Required evidence |
|---|---|
| Version | v0.1.0 |
| Source | Full commit SHA and immutable tag |
| Artifacts | APK/Relay artifact names and SHA-256 |
| Build | CI workflow run URLs and toolchain versions |
| Targets | Exact Android devices, Android versions, ABIs and Relay platform |
| Gates | G0–G9 states, test commands, logs and reviewer |
| External acceptance | DNS, TLS, HTTP redirect, visitor content and management-route isolation from an independent network |
| Recovery | Restart, process-death, network-loss, low-storage and backup/restore results |
| Approval | Owner sign-off and release date |

## Baseline versus later milestones

- **v0.1.0:** First Hosting. This baseline is locked after successful acceptance.
- **v0.1.1:** Workbench, Runtime & Linux Relay Installation. Development starts from the v0.1.0 baseline on a separate branch.
- **v0.1.2:** Connected Device Fabric. Development starts from the accepted v0.1.1 line.

A planned feature is not a delivered feature. The release ledger and G0–G9 evidence determine what is supported.
