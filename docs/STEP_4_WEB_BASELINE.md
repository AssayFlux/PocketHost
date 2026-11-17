# Step 4 — Built-in Web Baseline and Optional Runtime Packs

**Status: partial close only.** The mobile server app already bundles the Rust static website engine and serves HTML/CSS assets. This document freezes the requested baseline product boundary. TypeScript compiler integration, optional runtime lifecycle/isolation and publishing acceptance are not claimed complete.

## 1. Default server-app baseline

Every Pocket Host mobile server installation must provide these capabilities without a separate runtime download:

| Technology | Default behavior | Execution model |
|---|---|---|
| HTML | Serve pages, entry documents and fragments | Rust static server |
| CSS | Serve stylesheets, fonts and related assets | Rust static server |
| TypeScript | Accept TypeScript source in the built-in website build workflow and compile/transpile it to JavaScript before publish | Bundled TypeScript build toolchain; browser executes emitted JavaScript |

The built-in web baseline is present in both mobile modes: **Server only** and **Command Center + Server**. Website hosting never becomes optional when the user changes fleet-administration mode.

## 2. TypeScript boundary

TypeScript is not a browser-native language and must not be served as if a browser could execute raw TypeScript source. The app must bundle the TypeScript compiler/transpiler and perform a bounded build step before activation:

1. Import the site source into a staging area.
2. Identify TypeScript entry points and project configuration.
3. Compile TypeScript to JavaScript and emit diagnostics.
4. Reject failed builds; do not replace the active site.
5. Validate generated paths and file sizes, then stage the immutable output.
6. Let the owner preview/approve the artifact and atomically activate it.
7. Serve the generated JavaScript as a normal static asset.

The first baseline should support a simple TypeScript-to-JavaScript workflow without silently promising full npm package installation, arbitrary native addons, server-side Node execution or a general-purpose build farm. Dependency installation and advanced bundling need their own explicit compatibility and security policy.

## 3. Additional language runtimes stay separate

Python, Node/server-side JavaScript and future language runtimes remain separately selectable, signed, versioned and installable packs. They are installed only on the mobile website-serving node in this product baseline. Installing or removing one pack must not alter the built-in HTML/CSS/TypeScript capability or sibling packs.

The base app must not bundle every future language runtime. Users choose which additional languages they need and install them independently.

## 4. Isolation and failure boundaries

- Static HTML/CSS/JS serving must work when no optional language runtime is installed.
- A failed TypeScript build must leave the previous active website unchanged.
- A runtime crash must not stop static ingress or unrelated runtimes.
- Optional runtime execution must use project-scoped filesystem access, network policy, secrets, quotas and process supervision.
- Until adversarial tests prove the controls, do not advertise optional runtime execution as a hostile-code sandbox.
- Worker devices may build immutable artifacts, but cannot publish directly to the live site or bypass owner approval.

## 5. Current implementation evidence

Already present in the repository:
- Android server app packages the Rust website engine through its native build.
- Rust engine serves HTML and CSS and includes static routing, path validation, bounded connections and common asset MIME handling.
- Site import and static hosting flows exist in the Android application.

Still required before the baseline can be called verified:
- Bundle the TypeScript compiler/transpiler into the server app.
- Add deterministic tests for TypeScript success, diagnostics/failure, path traversal, output bounds and atomic publish behavior.
- Verify generated JavaScript assets are served with the JavaScript MIME type.
- Verify the server app installs and uses this baseline without installing the separate Python/Node runtime companion.
- Verify on supported Android ABIs and physical devices.

## 6. Step 4 partial-close definition

**Partially closed:** the architecture and existing implementation establish the always-included HTML/CSS static-serving baseline, and this document fixes TypeScript as part of the built-in app toolchain rather than an optional language pack.

**Still open:** implementation and verification of the bundled TypeScript compiler; separately installable additional language runtimes; per-project OS isolation; runtime lifecycle/fault containment; signed artifact verification, preview and atomic publishing; storage/traffic integration and physical-device acceptance.

Do not mark all of Step 4 complete until the remaining exit criteria in the Master Plan pass. Do not claim TypeScript support is implemented until the compiler is actually bundled and tested.
