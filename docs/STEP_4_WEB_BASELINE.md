# Step 4 — Pocket Host Built-in Web Server Baseline

**Scope: the main Pocket Host mobile application only.** Step 4 covers the built-in website-serving experience. Optional language runtime add-ons are explicitly outside Step 4 and are tracked in the future Runtime Library plan (`FUTURE_RUNTIME_LIBRARY.md`).

## 1. Main-app capabilities

Every Pocket Host mobile server installation must provide these capabilities without a companion APK, runtime download or add-on installation:

| Technology | Main-app behavior | Execution model |
|---|---|---|
| HTML | Serve pages, entry documents and fragments | Bundled Rust static server |
| CSS | Serve stylesheets, fonts and related assets | Bundled Rust static server |
| TypeScript | Built-in authoring/build workflow compiles TypeScript to JavaScript before publish | Compiler/transpiler bundled with the main app; browser executes emitted JavaScript |
| JavaScript | Serve generated and authored browser-side JavaScript | Static asset delivery |

The built-in baseline is present in both mobile modes: **Server only** and **Command Center + Server**. Website serving remains enabled in both modes.

## 2. TypeScript behavior

TypeScript is not browser-native and must not be served as if a browser could execute raw TypeScript. The main app must bundle the compiler/transpiler and perform a bounded build step before activation:

1. Import site source into a staging area.
2. Identify TypeScript entry points and project configuration.
3. Compile TypeScript to JavaScript and emit diagnostics.
4. Reject failed builds without replacing the active site.
5. Validate generated paths and file sizes, then stage immutable output.
6. Let the owner preview/approve the artifact and atomically activate it.
7. Serve generated JavaScript as a normal static asset.

The initial built-in workflow must not silently promise full npm package installation, arbitrary native addons, server-side Node execution or a general-purpose build farm. Those are separate product decisions.

## 3. Technology separation

The accepted technology ownership and dependency boundaries are normative in [Web Toolchain Boundaries](WEB_TOOLCHAIN_BOUNDARIES.md):

- React, React DOM, TypeScript and Vite dependencies used by the browser operator console stay inside `web-console/`; none are bundled into the Android app.
- The main app receives its own bundled TypeScript compiler/transpiler for website authoring. This is a compiler tool, not Node.js or a server-side runtime.
- The main app's TypeScript workflow must work without npm, React, Vite, Node.js or the runtime companion. Full npm package resolution and arbitrary package scripts are explicitly out of scope for the initial baseline.
- Python and Node.js remain separate optional server-side runtimes in `runtime-worker/`.

## 4. Main-app boundaries

- Basic HTML/CSS/JavaScript static hosting works without installing any extra runtime.
- A failed TypeScript build leaves the previous active website unchanged.
- Static serving remains independent of future optional language runtime packages.
- Worker devices may build immutable artifacts, but cannot publish directly to the live site or bypass owner approval.
- The main app does not bundle every server-side language runtime.
- Optional runtime package management is a future, separately scoped Runtime Library feature, not a Step 4 exit requirement.

## 5. Current implementation evidence

Already present in the repository:
- Android server app packages the Rust website engine through its native build.
- Rust engine serves HTML and CSS and includes static routing, path validation, bounded connections and common asset MIME handling.
- Site import and static hosting flows exist in the Android application.

Still required before the main-app baseline can be called verified:
- Bundle the TypeScript compiler/transpiler into the main Pocket Host app.
- Add deterministic tests for TypeScript success, diagnostics/failure, path traversal, output bounds and atomic publish behavior.
- Verify generated JavaScript assets are served with the JavaScript MIME type.
- Verify a fresh main-app installation serves HTML/CSS/JS without installing the runtime companion or any optional runtime.
- Verify on supported Android ABIs and physical devices.

## 6. Workbench — required Step 4 workstream

Workbench is a first-class in-app website development workspace, available in both operating modes. It is GitHub-inspired but does not require a GitHub account. It extends the existing website registry and file-management foundations rather than introducing a second site system.

Required areas: multi-site Explorer, Editor with unsaved changes, isolated Preview, Changes/diffs, durable local Commits, Deployments, release history and rollback. Its editable workspace must be separate from the immutable production release. Editing, saving, previewing and committing cannot alter the version currently served to visitors.

Publishing validates paths and content, performs bounded build steps, creates an immutable staged release and digest, previews/health-checks it, presents a diff, requires owner approval, atomically activates it and preserves a last-known-good release. Failure or process death must not leave a partially deployed site. TypeScript is supported only after the main-app compiler is bundled and verified. See [Workbench specification](WORKBENCH.md) for the full contract and acceptance checklist.

Current `WorkbenchWorkspaceManager` provides a separate per-site private draft workspace and baseline, bounded nested file listing, text read/write/create, folder creation, file rename/delete, SHA-256 change detection and immutable local commit snapshots. `PocketHostWorkbenchScreen` is connected to the hamburger navigation in both operating modes and provides site selection, nested folder navigation, a basic text editor, save-draft, create/rename/delete file actions, change summary and commit history. Unsaved editor text is protected from accidental navigation/discard. Android integration run [44050320129](https://github.com/AssayFlux/PocketHost/actions/runs/44050320129) successfully compiled app and instrumentation sources; emulator tests and APK packaging were skipped. This is an initial foundation, not a production-ready Workbench: preview, per-file diffs, TypeScript build, deployment UI, owner-approved atomic release activation, rollback, complete acceptance and physical-device testing remain open.

## 7. Step 4 partial-close definition

**Partially closed:** the architecture and existing implementation establish the always-included HTML/CSS static-serving baseline. TypeScript is frozen as a built-in main-app toolchain capability, not an optional language runtime.

**Still open within Step 4:** TypeScript compiler bundling and tests; main-app website lifecycle; secure staging, preview and atomic publishing; and required physical-device acceptance.

**Not part of Step 4:** Python, Node.js, PHP or any other separately downloadable server-side language runtime. Those are future optional add-ons described in `FUTURE_RUNTIME_LIBRARY.md`.

Do not claim TypeScript support is implemented until the compiler is actually bundled and tested. Do not hold Step 4 open for the future language add-on catalog.
