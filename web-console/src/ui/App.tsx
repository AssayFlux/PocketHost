import { useCallback, useEffect, useState } from "react";

type Runtime = { name: string; state: string; detail?: string };
type DeviceStatus = { state: string; apiVersion: string; runtimes: Runtime[] };
type Metrics = { sampledAt: string; sources: Array<{ source: string; requests: number; bytesServed: number; scopeNote?: string }> };
type Site = { id: string; name: string; runtime: string; hostnames: string[]; state: string };
type LogEntry = { timestamp: string; level: string; event: string; message: string };
type ResourceLimits = { pythonMaxWorkers: number; nodeHeapMb: number; deviceTotalMemoryBytes: number; deviceAvailableMemoryBytes: number; note: string };

const defaultApi = "http://127.0.0.1:17841";
const storedToken = () => { try { return sessionStorage.getItem("pockethost_device_token") || ""; } catch { return ""; } };

export function App() {
  const [apiBase, setApiBase] = useState(() => localStorage.getItem("pockethost_api_base") || defaultApi);
  const [token, setToken] = useState(storedToken);
  const [challengeId, setChallengeId] = useState("");
  const [approvalCode, setApprovalCode] = useState("");
  const [status, setStatus] = useState<DeviceStatus | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [sites, setSites] = useState<Site[]>([]);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [tunnel, setTunnel] = useState<{ state: string; configured: boolean } | null>(null);
  const [resources, setResources] = useState<ResourceLimits | null>(null);
  const [pythonWorkers, setPythonWorkers] = useState(8);
  const [nodeHeap, setNodeHeap] = useState(192);
  const [resourceDirty, setResourceDirty] = useState(false);
  const [message, setMessage] = useState("Connect to your PocketHost device on the same phone or local network.");
  const [busy, setBusy] = useState(false);

  const request = useCallback(async (path: string, init: RequestInit = {}) => {
    const response = await fetch(apiBase.replace(/\/$/, "") + path, {
      ...init,
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
      cache: "no-store",
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : {};
    if (!response.ok) throw new Error(data.error || `Device API returned ${response.status}`);
    return data;
  }, [apiBase, token]);

  const refresh = useCallback(async () => {
    if (!token) return;
    try {
      const [nextStatus, nextMetrics, nextSites, nextLogs, nextTunnel, nextResources] = await Promise.all([
        request("/api/v1/status"), request("/api/v1/metrics"), request("/api/v1/sites"),
        request("/api/v1/logs"), request("/api/v1/tunnel"), request("/api/v1/resources"),
      ]);
      setStatus(nextStatus);
      setMetrics(nextMetrics);
      setSites(nextSites.items || []);
      setLogs(nextLogs.items || []);
      setTunnel(nextTunnel);
      setResources(nextResources);
      if (!resourceDirty) {
        setPythonWorkers(nextResources.pythonMaxWorkers);
        setNodeHeap(nextResources.nodeHeapMb);
      }
      setMessage("Secure device session active. Metrics are labelled by their actual source.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not reach the device.");
      if (error instanceof Error && error.message === "unauthorized") {
        setToken("");
        sessionStorage.removeItem("pockethost_device_token");
      }
    }
  }, [request, token, resourceDirty]);

  useEffect(() => {
    if (!token) return;
    void refresh();
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => window.clearInterval(timer);
  }, [refresh, token]);

  async function beginPairing() {
    setBusy(true);
    try {
      localStorage.setItem("pockethost_api_base", apiBase);
      const result = await request("/api/v1/pair/challenge", { method: "POST", body: "{}" });
      setChallengeId(result.challengeId);
      setMessage("A six-digit approval code is now displayed in the PocketHost Android app. Approve this browser only if you initiated this request.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Pairing request failed.");
    } finally { setBusy(false); }
  }

  async function completePairing() {
    setBusy(true);
    try {
      const result = await request("/api/v1/pair/complete", {
        method: "POST",
        body: JSON.stringify({ challengeId, code: approvalCode }),
      });
      sessionStorage.setItem("pockethost_device_token", result.accessToken);
      setToken(result.accessToken);
      setApprovalCode("");
      setChallengeId("");
      setMessage("Device paired. Your bearer token is stored in this browser profile.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Pairing failed.");
    } finally { setBusy(false); }
  }

  async function siteAction(site: Site, action: "start" | "stop") {
    setBusy(true);
    try {
      await request("/api/v1/sites/" + encodeURIComponent(site.id) + "/actions", {
        method: "POST", body: JSON.stringify({ action }),
      });
      await refresh();
      setMessage(site.name + ": " + action + " request completed.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Site action failed."); }
    finally { setBusy(false); }
  }

  async function saveResources() {
    setBusy(true);
    try {
      const result = await request("/api/v1/resources", {
        method: "POST", body: JSON.stringify({ pythonMaxWorkers: pythonWorkers, nodeHeapMb: nodeHeap }),
      });
      setResources(current => current ? { ...current, ...result } : current);
      setResourceDirty(false);
      setMessage("Runtime resource limits saved. Restart Python and Node runtimes to apply them.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Resource settings could not be saved."); }
    finally { setBusy(false); }
  }

  async function tunnelAction(action: "start" | "stop") {
    setBusy(true);
    try {
      const result = await request("/api/v1/tunnel/actions", { method: "POST", body: JSON.stringify({ action }) });
      setTunnel(current => ({ configured: current?.configured ?? false, state: result.state }));
      setMessage("Cloudflare Tunnel: " + result.state);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Tunnel action failed."); }
    finally { setBusy(false); }
  }

  async function revoke() {
    setBusy(true);
    try {
      await request("/api/v1/session", { method: "DELETE" });
      setMessage("This browser session has been revoked on the device.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Session revocation failed.");
    } finally {
      localStorage.removeItem("pockethost_device_token");
      setToken("");
      setStatus(null);
      setMetrics(null);
      setSites([]);
      setLogs([]);
      setTunnel(null);
      setResources(null);
      setBusy(false);
    }
  }

  const bytes = (value: number) => value < 1024 ? `${value} B` : value < 1048576 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1048576).toFixed(1)} MB`;

  return <main className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">P</span><div><strong>PocketHost</strong><small>BY ASSAYFLUX</small></div></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav aria-label="Console sections"><a className="nav-item active" href="#overview">◈ Overview</a><a className="nav-item" href="#runtimes">▣ Runtimes</a><a className="nav-item" href="#metrics">⌁ Metrics</a><a className="nav-item" href="#connection">⚙ Connection</a></nav>
      <div className="sidebar-bottom"><span className="connection-dot" /> {token ? "Paired device session" : "Device not paired"}<small>Loopback management API · v1</small></div>
    </aside>
    <section className="main-panel" id="overview">
      <header className="topbar"><div><span className="eyebrow">ASSAYFLUX / CONTROL PLANE</span><h1>Device overview</h1><p>Live status from your PocketHost Android device.</p></div><span className="device-pill"><i /> {token ? "Session active" : "Not connected"}</span></header>
      <section className="notice"><div className="notice-icon">i</div><div><strong>Device connection</strong><p>{message}</p></div></section>
      {!token && <section className="panel" id="connection">
        <div className="panel-heading"><h3>Secure device pairing</h3><span className="muted-tag">DEVICE APPROVAL REQUIRED</span></div>
        <label>Management API address<input value={apiBase} onChange={e => setApiBase(e.target.value)} spellCheck={false} /></label>
        <button disabled={busy} onClick={beginPairing}>Request pairing code</button>
        {challengeId && <div className="pair-complete"><p>Enter the code shown in the Android app after approving this request.</p><input aria-label="Device approval code" value={approvalCode} onChange={e => setApprovalCode(e.target.value.replace(/\D/g, "").slice(0, 6))} inputMode="numeric" maxLength={6} placeholder="6-digit code" /><button disabled={busy || approvalCode.length !== 6} onClick={completePairing}>Complete pairing</button></div>}
      </section>}
      <div className="section-heading"><div><span className="eyebrow">SITE REGISTRY</span><h2>Configured websites</h2></div><span className="muted-tag">{sites.length} REGISTERED</span></div>
      <section className="panel">
        {sites.length === 0 ? <p>No registered websites yet. Import a project and assign its hostname in the Android app.</p> : sites.map(site => <div className="status-line" key={site.id}><span><strong>{site.name}</strong><small className="site-hosts">{site.hostnames.join(", ") || "No hostname assigned"}</small></span><span className="site-actions"><strong>{site.runtime} · {site.state}</strong><button disabled={busy} onClick={() => siteAction(site, "start")}>Start</button><button disabled={busy} onClick={() => siteAction(site, "stop")}>Stop</button></span></div>)}
      </section>
      <section className="metric-grid" id="metrics">
        <article className="metric-card"><span>Rust static requests</span><strong>{metrics?.sources.find(x => x.source === "rust-static")?.requests.toLocaleString() ?? "—"}</strong><small>Persisted device counter</small></article>
        <article className="metric-card"><span>Static data served</span><strong>{metrics ? bytes(metrics.sources.find(x => x.source === "rust-static")?.bytesServed ?? 0) : "—"}</strong><small>Rust static server only</small></article>
        <article className="metric-card"><span>Last sample</span><strong>{metrics ? new Date(metrics.sampledAt).toLocaleTimeString() : "—"}</strong><small>Device-reported timestamp</small></article>
      </section>
      <div className="section-heading"><div><span className="eyebrow">RESOURCE POLICY</span><h2>Runtime resource limits</h2></div><span className="muted-tag">DEVICE-AWARE</span></div>
      <section className="panel resource-panel">
        <div className="resource-control"><label>Python WSGI worker ceiling <strong>{pythonWorkers}</strong><input type="range" min="2" max="16" step="1" value={pythonWorkers} onChange={event => { setPythonWorkers(Number(event.target.value)); setResourceDirty(true); }} /></label></div>
        <div className="resource-control"><label>Node.js V8 heap ceiling <strong>{nodeHeap} MB</strong><input type="range" min="96" max="512" step="16" value={nodeHeap} onChange={event => { setNodeHeap(Number(event.target.value)); setResourceDirty(true); }} /></label></div>
        <p>{resources?.note ?? "Resource limits are device-managed."}</p>
        <button disabled={busy || !resources} onClick={saveResources}>Save resource limits</button>
      </section>
      <div className="section-heading" id="runtimes"><div><span className="eyebrow">DEVICE RUNTIMES</span><h2>Runtime health</h2></div><span className="muted-tag">{status?.state ?? "NOT CONNECTED"}</span></div>
      <section className="lower-grid">
        <article className="panel"><div className="panel-heading"><h3>Cloudflare Tunnel</h3><span className="muted-tag">{tunnel?.state ?? "UNKNOWN"}</span></div><p>{tunnel?.configured ? "Encrypted tunnel token is configured on the Android device." : "No tunnel token is configured."}</p><div className="site-actions"><button disabled={busy || !tunnel?.configured} onClick={() => tunnelAction("start")}>Start tunnel</button><button disabled={busy} onClick={() => tunnelAction("stop")}>Stop tunnel</button></div></article>
        <article className="panel"><div className="panel-heading"><h3>Recent operations</h3><span className="muted-tag">{logs.length} EVENTS</span></div>{logs.slice(0,8).map((entry,i)=><div className="log-line" key={entry.timestamp+i}><strong>{entry.level}</strong><span>{entry.event}</span><small>{entry.message}</small></div>)}</article>
      </section>
      <section className="lower-grid"><article className="panel"><div className="panel-heading"><h3>Runtime status</h3><span className="status-muted">{status ? "Live device data" : "Waiting for pairing"}</span></div>
        {(status?.runtimes ?? [{ name: "rust-static", state: "—" }, { name: "python", state: "—" }, { name: "node", state: "—" }]).map(runtime => <div className="status-line" key={runtime.name}><span>{runtime.name}</span><strong>{runtime.state}</strong></div>)}
      </article><article className="panel"><div className="panel-heading"><h3>Session controls</h3><span className="muted-tag">LOCAL DEVICE</span></div><p>Revoking this session invalidates its bearer credential on the device.</p><button className="danger-button" disabled={!token || busy} onClick={revoke}>Revoke this session</button><p className="scope-note">Python and Node metrics are process-local and include runtime-specific request counters and memory samples when those runtimes are active.</p></article></section>
      <footer>Designed by AssayFlux <span>·</span> Your website. Your device. Your control.</footer>
    </section>
  </main>;
}
