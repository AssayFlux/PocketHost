# WEBSITE DEPLOYMENT — STATIC AND APPLICATION SITES

Pocket Host serves the built website output. It does not run your desktop build toolchain.

## 1. Supported deployment formats

| Project | Import format | Position |
|---|---|---|
| HTML/CSS/browser JavaScript | Folder or ZIP with root index.html | Static hosting |
| TypeScript | Compiled browser bundle/output | Static hosting of generated output |
| React/Vue/Svelte/Angular | Framework production output | Static hosting |
| Vite | Usually dist/ | Static hosting |
| Next.js | Static export output | Static export only; no Next server |
| Python | Root wsgi.py exporting application | Embedded WSGI; trusted code only |
| Node.js | package.json and safe server entry; dependencies bundled | Embedded Node; trusted code only |
| PHP/Java/Go/Ruby/.NET | Not included | External service or future runtime required |

## 2. Static publish workflow

1. Build the site with its normal framework tools.
2. Locate the production output directory.
3. Confirm root `index.html` exists.
4. Open Pocket Host → Sites → import folder or ZIP.
5. Wait for validation; reject traversal, unsafe entries and size-limit violations.
6. Preview locally and test asset paths/client-side routes.
7. Register hostname and publish staged deployment.
8. Select Local Network or configured public connector.
9. For Cloudflare, reuse the existing named tunnel and point its Public Hostname to Rust only.
10. Verify public HTTPS from a different network and create a backup.

The planned AssayFlux Relay will be another outbound public-connectivity option. It is not implemented and should not be shown as operational.

## 3. SPA routes and assets

The static server supports SPA fallback for missing extensionless paths where configured. Missing asset files with extensions should return 404. Set the correct framework base/public path. Validate nested routes, encoded separators, MIME, HEAD, byte ranges and large streaming files.

## 4. Python WSGI

Provide root `wsgi.py` exporting callable `application(environ, start_response)`. Bundle compatible dependencies; Pocket Host does not automatically run pip. ASGI/WebSockets are not implied. Python is currently trusted-code-only because projects share companion UID and outbound egress is not OS-enforced.

## 5. Node.js

Provide `package.json`, a resolvable server entry and prebundled dependencies. On-device npm install, PM2/cluster and arbitrary child-process workflows are not supported. Node is currently trusted-code-only because projects share companion UID and outbound egress is not OS-enforced.

## 6. Reliability and security

- Static serving must continue when optional runtimes stop or crash.
- Import is staged; failed validation must not replace the active site.
- Use rollback for deployment updates.
- Never put management, tunnel or signing secrets in project directories.
- Do not expose management API as a website hostname.
- Do not run hostile or visitor-submitted application code.
- A compile pass or successful local preview is not external HTTPS acceptance.
