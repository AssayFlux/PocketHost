# Web Toolchain Boundaries

**Status:** accepted architecture decision for Pocket Host 0.1.0.

## Purpose

Keep the browser operator experience, the main mobile website-authoring toolchain and optional server-side runtimes independent. They solve different problems and must not be bundled together merely because they use related languages.

## Technology ownership

| Technology | Product location | Purpose | Included in main Android APK? |
|---|---|---|---|
| React + React DOM | `web-console/` | UI framework for the separate browser operator console | No |
| TypeScript + Vite + React plugin | `web-console/` | Type-checking, transpilation and bundling of the operator console | No |
| Built-in TypeScript compiler/transpiler | `app/` | Compile a user's website TypeScript source into browser JavaScript before publish | Yes; implementation and acceptance remain open |
| HTML + CSS + browser JavaScript | Main Pocket Host app + bundled Rust engine | Default website authoring and static delivery | Yes |
| Python 3.11 | `runtime-worker/` companion APK | Optional server-side WSGI runtime | No; separate companion |
| Node.js 24.22.1 | `runtime-worker/` companion APK | Optional server-side JavaScript runtime | No; separate companion |

## Main-app TypeScript contract

The main app must compile user TypeScript source to ordinary browser JavaScript before a site is activated. The compiler is a bounded authoring/build tool, not a server-side runtime.

- Do not serve raw `.ts` or `.tsx` as executable browser scripts.
- Do not require React, Vite, npm, Node.js or the runtime companion to build a basic TypeScript website.
- Do not bundle the web-console dependencies into the Android application.
- The initial built-in compiler scope is standalone TypeScript compilation with explicit diagnostics and bounded input/output. Full npm dependency resolution, arbitrary package scripts, native add-ons and general-purpose Node execution are out of scope.
- Build into an app-private staging directory. A failed build must leave the current published site untouched.
- Validate generated paths, file count and byte limits before preview and owner-approved activation.
- Browser output is served through the existing Rust static server and its JavaScript MIME mapping.

## Operator console contract

The React/TypeScript/Vite application remains a separate secondary operator client under `web-console/`. Its npm lockfile and build pipeline belong to that component. It may communicate with the Pocket Host control plane through documented APIs, but its frontend toolchain is not a dependency of the Android server.

## Optional server-side runtime contract

Python and Node.js remain separate optional server-side runtimes in the existing `runtime-worker/` companion APK. Their current integrations are in progress and are not independent downloadable packages yet. They are not prerequisites for static sites or the built-in TypeScript-to-JavaScript workflow.

The future Runtime Library may split language runtimes into individual downloadable packages after the five-step baseline. It must not change the main-app defaults.

## Acceptance gates

1. A clean main-app installation serves HTML, CSS and JavaScript without the runtime companion.
2. A clean main-app installation compiles a TypeScript fixture without Node.js, npm, React or Vite installed.
3. Successful compilation emits browser-compatible JavaScript and diagnostics identify source errors.
4. A failed or over-budget build does not alter the active site.
5. Traversal, symlink, output-size and file-count attacks are rejected.
6. The React/Vite console builds independently using only `web-console/package-lock.json`.
7. Python and Node.js companion acceptance is tested separately from the main-app web baseline.

Until these checks pass on CI and supported Android devices, TypeScript support remains specified but not implemented/verified.
