# Future Runtime Library — 20 Independent Language Add-ons

**Status: staged implementation plan, not a Step 4 deliverable.** Pocket Host main app remains the primary server application. It includes HTML/CSS static hosting and the built-in TypeScript-to-JavaScript web build toolchain. Python and Node.js already have substantial runtime integrations in the separate `runtime-worker/` companion APK; these are in progress, not greenfield future runtimes. The remaining 18 language runtimes are future additions. None is required to install, start or use the main app.

## Product rule

The target architecture is one independent downloadable runtime package per language. The catalog is not a monolithic bundle and is not a second main app. A user selects a language, downloads that one package, verifies it, installs it into the main app's Runtime Library manager and can later update or remove it without affecting the main app or other runtimes. Package files/manifests are separate per language under `runtime-library/catalog/`. Current exception: Python and Node.js code already exists, but both are currently delivered together inside `runtime-worker` companion APK; splitting them into independent package artifacts is still open.

## Initial 20-package catalog

Status key: **in progress** = implementation already exists in the repo but is not fully accepted or independently packaged; **planned** = implementation has not yet been delivered.

| # | Package ID | Language/runtime | Intended use | Separate manifest file | Status |
|---:|---|---|---|
| 1 | `python` | Python (CPython) | Backend services, automation and scripting | `runtime-library/catalog/python.json` | **in progress** |
| 2 | `nodejs` | Node.js | JavaScript server applications and APIs | `runtime-library/catalog/nodejs.json` | **in progress** |
| 3 | `php` | PHP | Traditional web applications and CMS backends | `runtime-library/catalog/php.json` | **planned** |
| 4 | `ruby` | Ruby (CRuby) | Web applications and automation | `runtime-library/catalog/ruby.json` | **planned** |
| 5 | `java` | Java (OpenJDK) | Enterprise services and JVM applications | `runtime-library/catalog/java.json` | **planned** |
| 6 | `dotnet` | C# / .NET | Cross-platform APIs and services | `runtime-library/catalog/dotnet.json` | **planned** |
| 7 | `go` | Go | Efficient network services and command-line backends | `runtime-library/catalog/go.json` | **planned** |
| 8 | `rust` | Rust | High-performance services and native components | `runtime-library/catalog/rust.json` | **planned** |
| 9 | `deno` | Deno | Secure-by-default TypeScript and JavaScript services | `runtime-library/catalog/deno.json` | **planned** |
| 10 | `bun` | Bun | JavaScript and TypeScript server applications | `runtime-library/catalog/bun.json` | **planned** |
| 11 | `lua` | Lua | Embedded scripting and lightweight services | `runtime-library/catalog/lua.json` | **planned** |
| 12 | `perl` | Perl | Text processing and system automation | `runtime-library/catalog/perl.json` | **planned** |
| 13 | `r` | R | Statistics, analytics and data services | `runtime-library/catalog/r.json` | **planned** |
| 14 | `julia` | Julia | Scientific and numerical computing | `runtime-library/catalog/julia.json` | **planned** |
| 15 | `elixir` | Elixir (BEAM) | Concurrent, fault-tolerant services | `runtime-library/catalog/elixir.json` | **planned** |
| 16 | `erlang` | Erlang (BEAM) | Distributed and highly available systems | `runtime-library/catalog/erlang.json` | **planned** |
| 17 | `kotlin-jvm` | Kotlin/JVM | Kotlin server applications | `runtime-library/catalog/kotlin-jvm.json` | **planned** |
| 18 | `swift` | Swift | Swift server-side applications | `runtime-library/catalog/swift.json` | **planned** |
| 19 | `dart` | Dart | Dart server applications and tooling | `runtime-library/catalog/dart.json` | **planned** |
| 20 | `haskell` | Haskell (GHC) | Typed functional services and applications | `runtime-library/catalog/haskell.json` | **planned** |

## Package boundaries

- One language package per downloadable artifact; never require downloading all 20.
- Each package has its own package ID, semantic version, target ABI/OS, artifact digest, signing identity, SBOM/license inventory, dependencies, resource profile and update/rollback metadata.
- Runtime packages are installed only when the user requests them. The main app must function with zero optional runtimes installed.
- Installation, update, health, start/stop and removal are independent per package.
- Verify signature, digest, ABI, license and compatibility before installation. Reject unsigned, tampered, incompatible or revoked packages.
- Runtime data and project configuration must survive supported package upgrades; uninstall behavior must clearly distinguish removing code from deleting user data.
- Runtime crashes must not stop HTML/CSS/JS static serving or sibling runtimes.
- No runtime is advertised as an untrusted-code sandbox until OS-enforced isolation and adversarial egress/resource tests pass.
- TypeScript in the main app is a compile-to-browser-JavaScript web toolchain, not one of these optional server-side runtime add-ons. Deno/Bun/Node are optional server-side execution environments and remain distinct from the built-in browser-side TypeScript workflow.

## Package lifecycle

1. Browse catalog and inspect language/version, size, ABI, permissions, license and publisher.
2. Download only the selected package over authenticated transport.
3. Verify signature, digest, manifest, ABI and policy before extraction.
4. Stage into a versioned immutable package directory; reject unsafe paths and resource-limit violations.
5. Run package self-test and health probe; activate only after success.
6. Start projects with explicit per-project permissions, storage, network and resource limits.
7. Update by side-by-side staging and health check; retain rollback version.
8. Remove package code independently, with explicit user choice about retaining or deleting project data.

## Delivery phases

- **Existing Python/Node completion:** finish Android device acceptance, lifecycle/crash recovery, security hardening and independent signed package split for the two existing implementations.
- **Catalog phase:** manifests and user-facing catalog; the Python/Node entries are `in-progress`; the remaining 18 entries stay `planned` until implementation begins.
- **Package pipeline:** reproducible builds per target ABI, SBOM, provenance, signatures, vulnerability scanning and artifact publication.
- **Runtime Manager:** per-package download/install/update/remove and independent health reporting in the main app.
- **Isolation phase:** per-project identities, filesystem namespaces, network egress controls, secrets, CPU/memory/storage quotas and adversarial tests.
- **Compatibility phase:** runtime/API compatibility matrix, migration and rollback acceptance across supported Android devices.

## Important scope limits

The catalog is a staged selection, not a claim that all 20 runtimes are already implemented, independently downloadable or compatible with every Android device. Python and Node.js have substantial existing code but are not yet independently packaged and fully accepted; the other 18 remain planned. Some ecosystems may require substantial storage, native toolchains or background execution and may be marked unsupported on constrained devices. Publish a package only after a named device/ABI passes installation, execution, resource, upgrade and removal acceptance. The main app's Step 4 gate must not wait for this future catalog.
