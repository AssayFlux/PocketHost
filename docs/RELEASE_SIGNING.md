# PocketHost Android release signing

PocketHost keeps release credentials outside the repository. Never commit a keystore, passwords, signing properties, or a production APK containing embedded credentials.

## 1. Create and back up the signing key

Run this on a trusted computer with JDK 17 or newer:

```bash
keytool -genkeypair -v \
  -keystore pockethost-release.jks \
  -alias pockethost \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose strong, unique passwords. Keep at least two encrypted offline backups of the keystore. Losing the key can prevent updates to an installed app under the same application ID.

## 2. Add GitHub Actions secrets

In the PocketHost repository, open **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `POCKETHOST_KEYSTORE_BASE64` | Base64-encoded bytes of `pockethost-release.jks` |
| `POCKETHOST_KEYSTORE_PASSWORD` | Keystore password |
| `POCKETHOST_KEY_ALIAS` | `pockethost` (or the alias you selected) |
| `POCKETHOST_KEY_PASSWORD` | Key password |

On Linux, create the one-line Base64 value with:

```bash
base64 -w 0 pockethost-release.jks
```

On macOS, use:

```bash
base64 < pockethost-release.jks | tr -d '\\n'
```

Do not paste the key or passwords into issues, chat, commits, workflow logs, or README files.

## 3. Build and verify

After all four secrets are configured:

1. Open **Actions → PocketHost signed release**.
2. Choose **Run workflow** on `main`.
3. Wait for the build and APK signature verification steps.
4. Download the `pockethost-signed-release-apks` artifact from the successful run (it contains the ARM64, ARMv7 and x86_64 release APKs).

The workflow deliberately fails if required signing secrets are missing. The ordinary quality workflow continues to build a debug APK and never needs the release key.

## Release limitations

A signed artifact is not the same as a production-approved release. Before distribution, confirm the package installs and upgrades correctly, test the target devices, review permissions and network exposure, and perform an independent security review. Keep the signing key permanently backed up and restrict who can change Actions secrets or run release workflows.
