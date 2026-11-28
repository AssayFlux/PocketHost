# AssayFlux Host Runtime Worker compatibility

The Runtime Worker remains a separately installed APK. Its Binder service accepts exactly the existing PocketHost host package and the dedicated private AssayFlux Host package `com.assayflux.host`, and only when the caller's signing certificate matches the Worker.

The Worker build includes Flask 3.1.1 in its embedded CPython environment so the existing AssayFlux CMS WSGI entrypoint can run without a separate Termux/Python installation.

## Persistent CMS data

AssayFlux CMS stores its SQLite database, session key, one-time setup code and uploaded files under `instance/`. A host project refresh replaces the transferred source tree. The Worker preserves the previous Worker-owned `instance/` directory when it installs an updated project archive, with symlink/path checks, an entry-count limit and a 512 MiB data-copy limit. This prevents a host-side source refresh from silently resetting the CMS database and uploads.

The Worker exposes an additive Binder query for the active Python project ID. AssayFlux Host uses it to distinguish its own CMS from another Python WSGI project already running in the shared Worker. The current Python runtime supports one active WSGI listener; the new app refuses a conflicting start instead of treating a global RUNNING status as proof that its CMS is active.

## Release signing

Both APKs must be signed with the same certificate. Independently generated debug APKs normally use different debug keystores and therefore cannot pair. Do not weaken the signature permission or caller certificate check to make mismatched APKs work.

## Trust boundary

The Worker is a separate Android UID from the host app, but Python code within the Worker still shares the Worker's UID. This is not per-project OS isolation. Only trusted code should be run until a stronger sandbox is implemented and verified.

The standalone Worker APK workflow accepts the same four repository Actions secrets in both repositories: `ASSAYFLUX_SHARED_KEYSTORE_BASE64`, `ASSAYFLUX_SHARED_KEYSTORE_PASSWORD`, `ASSAYFLUX_SHARED_KEY_ALIAS` and `ASSAYFLUX_SHARED_KEY_PASSWORD`. When all are configured, it builds and publishes both debug and release Worker APKs; the release APK uses the shared certificate expected by AssayFlux Host.
