# Pocket Host — Web Toolchain Boundaries

## Purpose

Keep the browser operator console, Android website authoring/build tools, static website serving and optional server-side runtimes independent.

## Technology ownership

| Technology | Repository | Function | Release |
|---|---|---|---|
| HTML/CSS/browser JavaScript | Main app + rust-engine | Static website serving | v0.1.0 |
| React 18 + TypeScript 5.7 + Vite 6 | web-console/ | Separate browser operator console | v0.2.0 scope |
| TypeScript compiler | Main Android app | Compile website TypeScript to browser JavaScript | v0.1.1 |
| Python 3.11 | runtime-worker/ companion | Optional WSGI backend runtime | v0.1.1 |
| Node.js mobile 24.22.1 | runtime-worker/ companion | Optional server-side JavaScript runtime | v0.1.1 |

## v0.1.0 static hosting contract

The main app serves already-built static assets. A React/Vite site is supported after a production build on another machine or CI; import the dist output. The main app does not install npm packages, resolve arbitrary dependencies or compile JSX/TypeScript in v0.1.0.

## v0.1.1 TypeScript compiler contract

The built-in compiler is a bounded authoring/build tool, not a server runtime. It must:
- Compile supported TypeScript source to ordinary browser JavaScript.
- Report file/line diagnostics.
- Reject unsafe paths and oversized projects.
- Bound CPU, memory, disk and execution time.
- Avoid arbitrary package scripts and unrestricted process execution.
- Produce deterministic staged outputs and content digests.
- Work without requiring the optional runtime companion for the basic TypeScript path.

## Operator console contract

web-console is a separately built browser app. Its dependencies do not become main Android app dependencies. It communicates with the control plane only under the v0.2.0 authenticated API contract.

## Optional server-side runtime contract

Python and Node exist in the separate runtime-worker APK. They currently ship together, share its application UID and are in progress. They are not independent download packages or hardened per-project sandboxes. Static hosting must work with the companion absent, disabled or crashed.

## Acceptance

Verify a clean main-app install serves static HTML/CSS/JS without companion runtimes; Vite dist output works; the v0.1.1 TypeScript compiler produces browser-valid output; operator console builds separately; Python/Node lifecycle and isolation tests pass before runtime support is claimed.
