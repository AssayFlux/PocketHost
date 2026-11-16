# PocketHost Android release signing

Release credentials stay outside the repository. Never commit a keystore, passwords, signing properties or production APKs containing embedded credentials.

## 1. Create and back up the signing key

Run on a trusted computer with JDK 17 or newer:

```bash
keytool -genkeypair -v \
  -keystore pockethost-release.jks \
  -alias pockethost \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose strong unique passwords and keep at least two encrypted offline backups. Losing the key can prevent upgrades under the same package IDs.

## 2. Configure GitHub Actions secrets

Open **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `POCKETHOST_KEYSTORE_BASE64` | Base64-encoded keystore bytes |
| `POCKETHOST_KEYSTORE_PASSWORD` | Keystore password |
| `POCKETHOST_KEY_ALIAS` | Key alias (for example `pockethost`) |
| `POCKETHOST_KEY_PASSWORD` | Key password |

Linux: `base64 -w 0 pockethost-release.jks`

macOS: `base64 < pockethost-release.jks | tr -d '\\n'`

Never paste signing material into chat, issues, commits or workflow logs.

## 3. Build and verify the release

After all four secrets are configured:

1. Open **Actions → PocketHost signed release** and run it on `main`.
2. The workflow builds the `:runtime-worker` companion release APK, then the host app for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
3. The workflow verifies all four APK signatures and requires the same signing certificate across host and companion packages.
4. Download the `pockethost-signed-release-apks` artifact. It contains four APKs plus `SHA256SUMS`.
5. Run `sha256sum -c SHA256SUMS` in the downloaded directory.

### APK inventory

| APK | Package | ABI |
|---|---|---|
| `app-arm64-v8a-release.apk` | `com.assayflux.pockethost` | ARM64 |
| `app-armeabi-v7a-release.apk` | `com.assayflux.pockethost` | ARMv7 |
| `app-x86_64-release.apk` | `com.assayflux.pockethost` | x86_64 emulator |
| `pockethost-runtime-worker-release.apk` | `com.assayflux.pockethost.worker` | Companion package |

The companion APK is currently a capability prototype only; it does not yet execute Python or Node. Its inclusion in the release workflow is not evidence of completed runtime isolation. The host and companion must be signed by the same certificate for signature-protected Binder access.

The ordinary quality workflow and debug build do not require release secrets. If any required secret is missing, the signed-release workflow must fail rather than produce an unsigned artifact.

## Release limitations

A signed artifact is not a production-approved release. Before distribution, finish companion runtime integration, API 26/33/35 emulator acceptance, physical-device upgrade/lifecycle tests, external Cloudflare HTTPS verification, capacity and backup acceptance, dependency/security review and independent security review. Restrict who can edit Actions secrets or trigger release workflows.