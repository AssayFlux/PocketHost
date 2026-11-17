# Future Runtime Library — 20 Independent Language Add-ons

**Status: future plan, not a Step 4 deliverable.** Pocket Host main app remains the primary server application. It includes HTML/CSS static hosting and the built-in TypeScript-to-JavaScript web build toolchain. None of the 20 packages below is required to install, start or use the main app.

## Product rule

Each language is an independent downloadable runtime package. The catalog is not a monolithic bundle and is not a second main app. A user selects a language, downloads that one package, verifies it, installs it into the main app's Runtime Library manager and can later update or remove it without affecting the main app or other runtimes. Package files/manifests are separate per language under `runtime-library/catalog/`.

## Initial 20-package catalog

| # | Package ID | Language/runtime | Intended use | Separate manifest file |
|---:|---|---|---|
| 1 | `python` | Python (CPython) | Backend services, automation and scripting | `runtime-library/catalog/python.json` |
| 2 | `nodejs` | Node.js | JavaScript server applications and APIs | `runtime-library/catalog/nodejs.json` |
| 3 | `php` | PHP | Traditional web applications and CMS backends | `runtime-library/catalog/php.json` |
| 4 | `ruby` | Ruby (CRuby) | Web applications and automation | `runtime-library/catalog/ruby.json` |
| 5 | `java` | Java (OpenJDK) | Enterprise services and JVM applications | `runtime-library/catalog/java.json` |
| 6 | `dotnet` | C# / .NET | Cross-platform APIs and services | `runtime-library/catalog/dotnet.json` |
| 7 | `go` | Go | Efficient network services and command-line backends | `runtime-library/catalog/go.json` |
| 8 | `rust` | Rust | High-performance services and native components | `runtime-library/catalog/rust.json` |
| 9 | `deno` | Deno | Secure-by-default TypeScript and JavaScript services | `runtime-library/catalog/deno.json` |
| 10 | `bun` | Bun | JavaScript and TypeScript server applications | `runtime-library/catalog/bun.json` |
| 11 | `lua` | Lua | Embedded scripting and lightweight services | `runtime-library/catalog/lua.json` |
| 12 | `perl` | Perl | Text processing and system automation | `runtime-library/catalog/perl.json` |
| 13 | `r` | R | Statistics, analytics and data services | `runtime-library/catalog/r.json` |
| 14 | `julia` | Julia | Scientific and numerical computing | `runtime-library/catalog/julia.json` |
| 15 | `elixir` | Elixir (BEAM) | Concurrent, fault-tolerant services | `runtime-library/catalog/elixir.json` |
| 16 | `erlang` | Erlang (BEAM) | Distributed and highly available systems | `runtime-library/catalog/erlang.json` |
| 17 | `kotlin-jvm` | Kotlin/JVM | Kotlin server applications | `runtime-library/catalog/kotlin-jvm.json` |
| 18 | `swift` | Swift | Swift server-side applications | `runtime-library/catalog/swift.json` |
| 19 | `dart` | Dart | Dart server applications and tooling | `runtime-library/catalog/dart.json` |
| 20 | `haskell` | Haskell (GHC) | Typed functional services and applications | `runtime-library/catalog/haskell.json` |

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

- **Catalog phase:** manifests and user-facing catalog; all entries remain `planned` until real artifacts exist.
- **Package pipeline:** reproducible builds per target ABI, SBOM, provenance, signatures, vulnerability scanning and artifact publication.
- **Runtime Manager:** per-package download/install/update/remove and independent health reporting in the main app.
- **Isolation phase:** per-project identities, filesystem namespaces, network egress controls, secrets, CPU/memory/storage quotas and adversarial tests.
- **Compatibility phase:** runtime/API compatibility matrix, migration and rollback acceptance across supported Android devices.

## Important scope limits

The catalog is a planned selection, not a claim that all 20 runtimes are already implemented, downloadable or compatible with every Android device. Some ecosystems may require substantial storage, native toolchains or background execution and may be marked unsupported on constrained devices. Publish a package only after a named device/ABI passes installation, execution, resource, upgrade and removal acceptance. The main app's Step 4 gate must not wait for this future catalog.
