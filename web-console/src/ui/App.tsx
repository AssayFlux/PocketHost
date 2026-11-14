const metrics = [
  { label: "Published sites", value: "—", note: "Connect a device to load sites" },
  { label: "Requests today", value: "—", note: "Waiting for device metrics" },
  { label: "Data served", value: "—", note: "No telemetry received" },
];
const nav = ["Overview", "Websites", "Deployments", "Traffic", "Logs", "Settings"];
export function App() {
  return <main className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">P</span><div><strong>PocketHost</strong><small>BY ASSAYFLUX</small></div></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav aria-label="Console sections">{nav.map((item, i) => <a className={i === 0 ? "nav-item active" : "nav-item"} href="#preview" key={item}><span className="nav-dot">{["◈","▣","↗","⌁","≡","⚙"][i]}</span>{item}</a>)}</nav>
      <div className="sidebar-bottom"><span className="connection-dot" /> Device not connected <small>Connect from PocketHost Android</small></div>
    </aside>
    <section className="main-panel" id="preview">
      <header className="topbar"><div><span className="eyebrow">ASSAYFLUX / CONTROL PLANE</span><h1>Overview</h1><p>Manage your websites and hosting environment.</p></div><span className="device-pill"><i /> No device connected</span></header>
      <section className="notice"><div className="notice-icon">i</div><div><strong>Console preview</strong><p>This interface is a visual foundation. It is not connected to an Android device or management API yet. Live controls are intentionally unavailable.</p></div></section>
      <section className="metric-grid">{metrics.map(m => <article className="metric-card" key={m.label}><span>{m.label}</span><strong>{m.value}</strong><small>{m.note}</small></article>)}</section>
      <div className="section-heading"><div><span className="eyebrow">HOSTING</span><h2>Your websites</h2></div><span className="muted-tag">DEVICE DATA UNAVAILABLE</span></div>
      <section className="empty-state"><div className="empty-icon">⌘</div><h3>Connect PocketHost to get started</h3><p>Your deployed sites, domains, deployment history and traffic will appear here after a secure management connection is implemented.</p><span className="outline-chip">Static hosting foundation</span></section>
      <section className="lower-grid"><article className="panel"><div className="panel-heading"><h3>Hosting status</h3><span className="status-muted">Not connected</span></div><div className="status-line"><span>Rust engine</span><strong>—</strong></div><div className="status-line"><span>Cloudflare Tunnel</span><strong>Not available</strong></div><div className="status-line"><span>Python / Node.js</span><strong>Not available</strong></div></article><article className="panel"><div className="panel-heading"><h3>System design</h3><span className="muted-tag">POCKETHOST 0.1.0</span></div><div className="stack-row"><b>Android</b><span>Kotlin · Jetpack Compose</span></div><div className="stack-row"><b>Engine</b><span>Rust · Tokio · Hyper</span></div><div className="stack-row"><b>Console</b><span>React · TypeScript</span></div></article></section>
      <footer>Designed by AssayFlux <span>·</span> Your website. Your device. Your control.</footer>
    </section>
  </main>;
}
