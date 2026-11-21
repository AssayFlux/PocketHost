# Pocket Host v0.1.0 Alpha — Engineering audit and remediation

**Audit date:** 2026-11-21  
**Branch:** `Alpha`  
**Source snapshot inspected:** `218d25793b7b7104e4c5583c716bb53586cdf156`  
**Remediation:** committed directly to `Alpha`; see commit history for the complete repair series.  
**APK policy:** APK packaging was not invoked. The APK workflow is manual-dispatch only.  
**API/emulator policy:** no API emulator, live API, or external-network workflow was run.

## Executive result

The repository has substantial v0.1.0 implementation, but prior status documents were stale and one Android packaging dependency was not connected to CI. The concrete defect was Cloudflared: runtime code expected an executable named `libcloudflared.so` in Android's native library directory, but the three ABI binaries were absent from the repository and neither Android engineering nor APK packaging workflow generated them.

The remediation wires the pinned Cloudflared build into Android engineering CI and the separately manual APK workflow, verifies all supported ABI outputs, transports generated binaries and license notices between CI jobs, and checks for the executable inside each selected APK. This closes the pipeline configuration gap. It is not yet a successful build result: the first post-remediation CI run must prove the Go cross-build, ELF checks, artifact handoff and Android source compilation.

## Findings and disposition

| Finding | Disposition | Evidence / remaining boundary |
|---|---|---|
| Cloudflared executable absent for arm64-v8a, armeabi-v7a and x86_64 | Remediated in build pipeline | Build script is now invoked by Android native CI and manual APK workflow. First CI run pending. |
| Cloudflared binary was not verified in APK payload | Remediated in workflow configuration | Manual APK workflow checks the selected ABI's `libcloudflared.so` entry and ELF type. APK workflow was not run. |
| Android integration did not build Cloudflared | Remediated in workflow configuration | Android native job builds and verifies it, uploads a dedicated artifact; source-compile job restores it before Gradle compile. |
| APK workflow could run on a push to its own workflow file | Remediated | Push trigger removed; only explicit `workflow_dispatch` remains. No APK build was triggered. |
| Generated Cloudflared work directory could pollute Git status | Remediated | Root `.build/` added to `.gitignore`. |
| README and release/baseline reports referenced older commits and workflow design | Documentation remediation in this series | Current README, baseline and release report updated. Historical records are retained as historical evidence, not current status. |
| Current-head workflow status | Pending external CI evidence | GitHub workflow execution must complete; source inspection is not a pass. |
| Physical-device, emulator and independent-network acceptance | Open / owner verification | Not performed. API emulator tests were not run. |
| APK build, signatures, artifact and installation | Deliberately not run | Owner explicitly requested no APK build in this pass. |

## Cloudflared implementation details

- Upstream Cloudflared version remains pinned to `2026.11.0`.
- Android NDK toolchains are required for all three declared ABIs.
- Go toolchain in CI is pinned to `1.27.4`, matching the inspected upstream `go.mod` toolchain directive.
- The script checks required tools and NDK compiler presence, removes stale ABI outputs before rebuilding, and fails if any output is absent or non-executable.
- CI checks ELF headers and uploads Cloudflared binaries and license notices as a dedicated artifact.
- The dependent source-compilation job restores those generated files before invoking Gradle.
- The manual APK workflow builds the same pinned executable and requires the selected ABI's executable in the APK. It also checks ELF type and license notice presence.

The generated Go executable is named `libcloudflared.so` because Android extracts packaged native-library entries into `ApplicationInfo.nativeLibraryDir`; the app then executes it as a child process. This is a deliberate packaging convention, not a JNI library loaded by the app.

## Workflow boundaries

- `.github/workflows/quality.yml`: general Rust, control-plane/PostgreSQL, Python/protocol, web-console, Node and shell/script checks.
- `.github/workflows/android-debug-apk.yml`: Android lint, JVM unit tests and runtime asset prerequisite validation; no APK assembly.
- `.github/workflows/android-integration.yml`: Android native ABI builds, Cloudflared build/ELF verification, and Android app/instrumentation-source/runtime-worker compilation; no APK assembly and no emulator execution.
- `.github/workflows/android-alpha-apk.yml`: manual-only APK packaging and artifact verification. It is not automatically triggered by pushes.

No API emulator tests, live API workflows, APK assembly, APK signing, APK artifact upload or device installation were performed during this remediation.

## Verification required before calling this repair complete

1. General quality workflow passes on the new Alpha head.
2. Android native workflow builds Cloudflared for all three ABIs, verifies ELF output and uploads both native artifacts.
3. Android source-compile job downloads/restores those artifacts and completes Kotlin/Gradle compilation.
4. Android lint/JVM test workflow passes on the new source head.
5. Only after explicit owner authorization: run the manual APK workflow and inspect resulting APK contents.
6. Owner performs physical-device acceptance. API and external-network tests remain separately authorized.

**Verdict:** implementation and CI wiring have been corrected in source. Automated confirmation is pending. v0.1.0 is not certified production-ready or device-accepted by this audit.