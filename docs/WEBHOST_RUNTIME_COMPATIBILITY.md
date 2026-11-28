# WebHost Runtime Worker compatibility

PocketHost's separately installed Runtime Worker supports the private AssayFlux WebHost Android application with application ID `com.assayflux.webhost`.

## Trust boundary

- The Worker accepts only `com.assayflux.pockethost` and `com.assayflux.webhost` as host packages.
- The calling package must share the Worker's signing certificate.
- The signature-protected Binder permission remains required; do not weaken it to make independently signed APKs pair.
- The Worker remains a separate Android application UID. Python and Node project code still shares the Worker UID and is not isolated per project.

## Flask CMS support

- Embedded CPython version: 3.11.
- Flask dependency: 3.1.1.
- WebHost uses the additive project-status Binder transaction `FIRST_CALL_TRANSACTION + 17` to distinguish its CMS project from another active Python project.
- The Worker preserves the runtime-owned `instance/` directory when the same project bundle is refreshed. The copy rejects symlinks and unsafe paths and is bounded to 10,000 entries and 512 MiB.
- This preserves the CMS database, session key, setup state and uploads from stale host-side asset copies.

## Signing and builds

WebHost and Worker APKs must use the same certificate. Configure the same `ASSAYFLUX_SHARED_KEYSTORE_BASE64`, `ASSAYFLUX_SHARED_KEYSTORE_PASSWORD`, `ASSAYFLUX_SHARED_KEY_ALIAS`, and `ASSAYFLUX_SHARED_KEY_PASSWORD` GitHub Actions secrets in both repositories to produce pairable release APKs. Debug APKs from different GitHub runners do not automatically share a certificate.

CI includes source-level compatibility tests and separate Worker build/quality workflows. These checks do not replace physical-device installation, CMS setup, persistent-data refresh, process recovery, or workload acceptance tests.