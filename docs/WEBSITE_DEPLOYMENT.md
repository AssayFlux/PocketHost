# Website deployment guide

Pocket Host hosts the **built website output**, not your development workstation or source tree's build tools. Build the site with the framework's normal toolchain, then import the generated deployment directory or a ZIP containing its contents.

## Choose a deployment type

| Website stack | Deployment method | Current Pocket Host support |
|---|---|---|
| HTML + CSS + JavaScript | Import the folder containing `index.html` | Native static hosting |
| TypeScript | Compile/bundle to browser JavaScript, then import the output | Native static hosting of build output |
| React, Vue, Svelte, Angular | Run the framework production build and import its static output | Native static hosting |
| Vite | Usually import `dist/` | Native static hosting |
| Next.js | Configure static export and import `out/` | Static-export features only; no Next.js server runtime |
| Python | Import a project containing root `wsgi.py` exporting callable `application` | Embedded WSGI runtime; trusted code only |
| Node.js | Import a self-contained project with `package.json` and a resolvable server entry point | Embedded Node runtime; dependencies must be prebundled; trusted code only |
| PHP, Java, Go, Ruby, .NET, other server runtimes | Requires an additional compatible runtime or external API | Not built in |

TypeScript is a development language for browser code; browsers execute the generated JavaScript. Pocket Host does not run a TypeScript compiler on the phone.

## Static website: recommended first deployment

1. Develop your website using any editor and framework.
2. Run the framework's production build on your computer or build environment.
3. Locate the generated output directory (for example, Vite `dist/`, Angular `dist/<project>/browser/`, or Next.js static export `out/`).
4. Confirm that the directory contains a root `index.html`.
5. Import that directory using **Sites → Choose website folder**, or ZIP its contents and use **Import website ZIP**.
6. Configure the hostname in Pocket Host and reuse your existing Cloudflare Tunnel Public Hostname, pointing it to the Rust website listener.
7. Start hosting and test the public HTTPS domain from a network outside the phone.

You can also add one file at a time in the website file manager. Individual uploads are limited to 50 MiB; the Rust server has its own 50 MiB per-file serving limit.

### Single-page application routes

The static server falls back to the root `index.html` for missing extensionless paths, allowing common client-side routes such as `/account/profile` to load the SPA shell. Missing paths with file extensions (for example, `/assets/missing.js`) remain 404 responses. Your application must still include the correct asset base/public path in its build configuration.

## Python backend

The current Python runtime expects a root-level `wsgi.py` with a callable named `application`. It is an embedded WSGI runtime, not a general-purpose Linux server. It does not provide ASGI/WebSocket support or automatic on-device package installation. Prepare dependencies for the supported embedded environment before importing.

## Node.js backend

The current Node runtime needs a self-contained project with `package.json` and a safe server entry file (for example, `server.js` or the file referenced by `main`). Dependencies must already be bundled in the imported project. On-device `npm install`, arbitrary child-process build workflows, PM2/cluster and multiple independent Node application processes are not currently supported.

## Important security and reliability boundaries

- Keep Python and Node limited to code you own or explicitly trust. They now execute under the separate-UID companion package, not the host app UID, but share the companion UID with one another and are **not complete OS sandboxes**. Outbound network egress is not yet restricted.
- Static hosting works without enabling either optional runtime.
- Cloudflare Tunnel provides connectivity, not visitor authentication. For private sites, configure an appropriate Cloudflare Access policy.
- Android may stop background processes or restrict networking; Pocket Host cannot guarantee cloud-server-style 24/7 uptime.
- Never upload private keys, `.env` files, credentials, payment card data or other secrets into a public website directory.
- A successful local preview does not prove that DNS, Cloudflare ingress, TLS, or external reachability is configured correctly.

## Troubleshooting

- **Blank page:** verify the imported directory contains the generated root `index.html` and that the build asset paths are correct.
- **CSS/JS 404:** upload the complete build output, not only `index.html`.
- **Deep link returns 404:** ensure the route has no file extension and that root `index.html` exists.
- **API route fails:** a static frontend does not provide a backend; configure a supported Python/Node app and map its hostname, or use an external API.
- **Works on phone but not publicly:** inspect the existing Cloudflare Tunnel connection and Public Hostname origin; never point the public hostname at management port 17841.
