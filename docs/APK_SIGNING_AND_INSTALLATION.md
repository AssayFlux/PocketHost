# PocketHost APK signing and matched installation

PocketHost consists of two separately installable Android applications:

- `com.assayflux.pockethost` — the management/hosting app.
- `com.assayflux.pockethost.worker` — the runtime companion.

The host binds to the companion's exported Binder service, which is protected by Android's `signature` permission. **Both APKs must be signed by the same certificate.** Installing two debug APKs produced by unrelated GitHub-hosted jobs is not a valid matched installation: each fresh runner can generate a different Android debug keystore.

## CI artifacts

- The independent App APK and Runtime Worker APK workflows prove that each module builds and packages independently. Do not assume that artifacts from different workflow runs have matching signing certificates.
- Use the two APK files from the single `PocketHost Android Integration Acceptance` run's `pockethost-matched-app-and-runtime-worker-apks` artifact. Both are built on the same runner; CI compares their signer SHA-256 certificate digests, checks package IDs and all three supported ABIs, then installs them together during acceptance.
- These are debug artifacts for testing, not production releases.

## Production signing

For release distribution, provision one protected release keystore and use it to sign **both** Gradle modules. Configure the following environment variables in the release build environment:

- `POCKETHOST_KEYSTORE_FILE`
- `POCKETHOST_KEYSTORE_PASSWORD`
- `POCKETHOST_KEY_ALIAS`
- `POCKETHOST_KEY_PASSWORD`

Never commit the keystore or passwords. Both APKs must be signed by the same release certificate; verify with `apksigner verify --print-certs` before distributing them. Increment and coordinate the host/worker version and Binder protocol when making incompatible changes.
