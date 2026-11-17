# Future Runtime Library — 20 Independent Language Add-ons

**Status: future plan, not a Step 4 deliverable.** Pocket Host main app remains the primary server application. It includes HTML/CSS static hosting and the built-in TypeScript-to-JavaScript web build toolchain. None of the 20 packages below is required to install, start or use the main app.

## Product rule

Each language is an independent downloadable runtime package. The catalog is not a monolithic bundle and is not a second main app. A user selects a language, downloads that one package, verifies it, installs it into the main app's Runtime Library manager and can later update or remove it without affecting the main app or other runtimes. Package files/manifests are separate per language under `runtime-library/catalog/`.

## Initial 20-package catalog

| # | Package ID | Language/runtime | Intended use |
|---:|---|---|---|
| 1 | `python` | Python (CPython) | Backend services, automation and scripting |
| 2 | `nodejs` | Node.js (Node.js) | JavaScript server applications and APIs |
| 3 | `php` | PHP (PHP) | Traditional web applications and CMS backends |
| 4 | `ruby` | Ruby (CRuby) | Web applications and automation |
| 5 | `java` | Java (OpenJDK) | Enterprise services and JVM applications |
| 6 | `dotnet` | C# / .NET (.NET) | Cross-platform APIs and services |
| 7 | `go` | Go (Go) | Efficient network services and command-line backends |
| 8 | `rust` | Rust (Rust toolchain) | High-performance services and native components |
| 9 | `deno` | Deno (Deno) | Secure-by-default TypeScript and JavaScript services |
| 10 | `bun` | Bun (Bun) | JavaScript and TypeScript server applications |
| 11 | `lua` | Lua (Lua) | Embedded scripting and lightweight services |
| 12 | `perl` | Perl (Perl) | Text processing and system automation |
| 13 | `r` | R (R) | Statistics, analytics and data services |
| 14 | `julia` | Julia (Julia) | Scientific and numerical computing |
| 15 | `elixir` | Elixir (BEAM / Elixir) | Concurrent, fault-tolerant services |
| 16 | `erlang` | Erlang (BEAM / Erlang) | Distributed and highly available systems |
| 17 | `kotlin-jvm` | Kotlin (JVM) (Kotlin/JVM) | Kotlin server applications |
| 18 | `swift` | Swift (Swift) | Swift server-side applications |
| 19 | `dart` | Dart (Dart) | Dart server applications and tooling |
| 20 | `haskell` | Haskell (GHC) | Typed functional services and applications |

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
