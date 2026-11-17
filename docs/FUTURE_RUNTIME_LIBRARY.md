# Future Runtime Library — 20 Optional Language Add-ons

**Status:** future expansion plan; catalog manifests are not runtime binaries.  
**Release placement:** Python/Node completion is v0.1.1; the wider independent Runtime Library follows the initial three-release pathway.

## Product rule

The main Pocket Host app remains the website-serving application. Static HTML/CSS/browser JavaScript does not require any server-side runtime. Each additional language is an optional, independently signed and versioned package. Users download only what they need.

## Initial 20-package catalog

| # | Runtime | Current repository status |
|---|---|---|
| 1 | Python / CPython | Existing integration in runtime-worker; in progress |
| 2 | Node.js | Existing integration in runtime-worker; in progress |
| 3 | PHP | Planned |
| 4 | Ruby | Planned |
| 5 | Java / OpenJDK | Planned |
| 6 | C# / .NET | Planned |
| 7 | Go | Planned |
| 8 | Rust | Planned |
| 9 | Deno | Planned |
| 10 | Bun | Planned |
| 11 | Lua | Planned |
| 12 | Perl | Planned |
| 13 | R | Planned |
| 14 | Elixir | Planned |
| 15 | Erlang | Planned |
| 16 | Kotlin/JVM | Planned |
| 17 | Swift | Planned |
| 18 | Dart | Planned |
| 19 | Zig | Planned |
| 20 | Crystal | Planned |

The catalog under runtime-library/catalog contains manifests. A manifest is not an installable runtime package.

## Current Python and Node placement

CPython 3.11 and Node.js mobile 24.22.1 are already integrated in the separate runtime-worker companion APK. They currently ship together, share the companion UID and remain in progress. v0.1.1 completes device acceptance, IPC/lifecycle and security hardening. Independent package splitting is a separate delivery task.

## Package boundaries

Every runtime package must declare:
- Stable package ID and semantic version.
- Runtime version and supported Android ABI/API.
- Artifact digest, signature and publisher identity.
- License and SBOM.
- Entrypoints and supported project format.
- Filesystem/network capabilities and resource budgets.
- Health checks, update, rollback and uninstall behavior.
- Compatibility with the host API and project storage contract.

## Security requirements

No runtime is advertised as sandboxed without OS-enforced filesystem/process/network boundaries and adversarial tests. Static hosting remains operational if any optional runtime is missing or crashes. Do not bundle all languages into the main app.

## Delivery phases

1. v0.1.1: complete existing Python and Node companion integration.
2. Later: split Python and Node into independent signed artifacts and deliver Runtime Manager.
3. Later: add languages according to demand, ABI support, size, license and security readiness.

## Acceptance

Each runtime has its own G0–G9 record, artifact digest, ABI/device matrix, install/upgrade/rollback tests, resource limits, security review and sample project. No language is "available" because a catalog JSON file exists.
