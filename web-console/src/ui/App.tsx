import { useCallback, useEffect, useState } from "react";
import { RemoteControlPlane } from "./RemoteControlPlane";

type Runtime = { name: string; state: string; detail?: string };
type DeviceStatus = { state: string; apiVersion: string; runtimes: Runtime[] };
type Metrics = { sampledAt: string; sources: Array<{ source: string; requests?: number; bytesServed?: number; bytesSent?: number; bytes_sent?: number; errors?: number; active?: number; peak_active?: number; processPssKb?: number; rssBytes?: number; heapUsedBytes?: number; last_request_ms?: number; scopeNote?: string }>; deviceResources?: { processPssKb?: number; availableDeviceMemoryBytes?: number; totalDeviceMemoryBytes?: number; lowMemory?: boolean; telemetry?: { manufacturer?: string; model?: string; androidApi?: number; cpuCores?: number; cpuUsagePercent?: number | null; processPssKb?: number; availableMemoryBytes?: number; totalMemoryBytes?: number; storageAvailableBytes?: number; batteryPercent?: number | null; batteryTemperatureC?: number | null; thermalLabel?: string; networkTransport?: string } } };
type Site = { id: string; name: string; runtime: string; hostnames: string[]; state: string };
type LogEntry = { timestamp: string; level: string; event: string; message: string };
type ResourceLimits = { pythonMaxWorkers: number; nodeHeapMb: number; deviceTotalMemoryBytes: number; deviceAvailableMemoryBytes: number; note: string };
type CapacityState = { percent: number; safeConcurrency: number; effectiveConcurrencyLimit: number; activeConnections?: number; queuedConnections?: number; rateLimitedRequests?: number; siteLimitRejections?: number; rejectedConnections?: number; scheduled?: boolean; scheduleExpiresAt?: number; healthState?: string; semantics: string };
type CapacityBenchmark = { state: string; safeConcurrency?: number; workload?: string; error?: string; levels?: Array<{ concurrency: number; samples: number; errors: number; p95LatencyMs: number; withinSafeThreshold: boolean }> };
type DailyReport = { date: string; totalRequests: number; totalBytes: number; eventCount: number; errorCount: number; warningCount: number };

const defaultApi = "http://127.0.0.1:17841";
const storedToken = () => { try { return sessionStorage.getItem("pockethost_device_token") || ""; } catch { return ""; } };

export function App() {
  const [apiBase, setApiBase] = useState(() => localStorage.getItem("pockethost_api_base") || defaultApi);
  const [token, setToken] = useState(storedToken);
  const [challengeId, setChallengeId] = useState("");
  const [approvalCode, setApprovalCode] = useState("");
  const [loginUsername, setLoginUsername] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [ownerReauthRequired, setOwnerReauthRequired] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [accountUsername, setAccountUsername] = useState("");
  const [accountPassword, setAccountPassword] = useState("");
  const [recoveryPassword, setRecoveryPassword] = useState("");
  const [hostIdentity, setHostIdentity] = useState<{ username: string; role: string; deviceApprovedPairing?: boolean } | null>(null);
  const [hostAccounts, setHostAccounts] = useState<Array<{ username: string; role: string; createdAt: number }>>([]);
  const [needsOwnerSetup, setNeedsOwnerSetup] = useState(false);
  const [status, setStatus] = useState<DeviceStatus | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [sites, setSites] = useState<Site[]>([]);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [tunnel, setTunnel] = useState<{ state: string; configured: boolean } | null>(null);
  const [resources, setResources] = useState<ResourceLimits | null>(null);
  const [pythonWorkers, setPythonWorkers] = useState(8);
  const [nodeHeap, setNodeHeap] = useState(192);
  const [resourceDirty, setResourceDirty] = useState(false);
  const [capacity, setCapacity] = useState<CapacityState | null>(null);
  const [capacityPercent, setCapacityPercent] = useState(100);
  const [capacityDirty, setCapacityDirty] = useState(false);
  const [benchmark, setBenchmark] = useState<CapacityBenchmark | null>(null);
  const [dailyReports, setDailyReports] = useState<DailyReport[]>([]);
  const [message, setMessage] = useState("Connect to your PocketHost device on the same phone or local network.");
  const [busy, setBusy] = useState(false);

  const request = useCallback(async <T extends Record<string, unknown>>(path: string, init: RequestInit = {}): Promise<T> => {
    const response = await fetch(apiBase.replace(/\/$/, "") + path, {
      ...init,
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
      cache: "no-store",
    });
    const text = await response.text();
    let data: Record<string, unknown> = {};
    if (text) {
      try { data = JSON.parse(text) as { error?: string }; }
      catch { data = { error: text.slice(0, 240) }; }
    }
    if (!response.ok) {
      if (response.status === 428 && data.error === "owner_reauthentication_required") {
        setOwnerReauthRequired(true);
      }
      throw new Error(typeof data.error === "string" ? data.error : `Device API returned ${response.status}`);
    }
    return data as T;
  }, [apiBase, token]);

  const refresh = useCallback(async () => {
    if (!token) return;
    try {
      const [nextStatus, nextMetrics, nextSites, nextLogs, nextTunnel, nextResources, nextCapacity, nextBenchmark, nextReports] = await Promise.all([
        request<DeviceStatus>("/api/v1/status"), request<Metrics>("/api/v1/metrics"), request<{ items: Site[] }>("/api/v1/sites"),
        request<{ items: LogEntry[] }>("/api/v1/logs"), request<{ state: string; configured: boolean }>("/api/v1/tunnel"), request<ResourceLimits>("/api/v1/resources"),
        request<CapacityState>("/api/v1/capacity"), request<CapacityBenchmark>("/api/v1/capacity/benchmark"), request<{ items: DailyReport[] }>("/api/v1/reports"),
      ]);
      setStatus(nextStatus);
      setMetrics(nextMetrics);
      setSites(nextSites.items || []);
      setLogs(nextLogs.items || []);
      setTunnel(nextTunnel);
      setResources(nextResources);
      setCapacity(nextCapacity);
      if (!capacityDirty) setCapacityPercent(nextCapacity.percent);
      setBenchmark(nextBenchmark);
      setDailyReports(nextReports.items || []);
      if (!resourceDirty) {
        setPythonWorkers(nextResources.pythonMaxWorkers);
        setNodeHeap(nextResources.nodeHeapMb);
      }
      await loadHostIdentity();
      setMessage("Authenticated host session active. Metrics are labelled by their actual source.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not reach the device.");
      if (error instanceof Error && error.message === "unauthorized") {
        setToken("");
        sessionStorage.removeItem("pockethost_device_token");
      }
    }
  }, [request, token, resourceDirty, capacityDirty]);

  useEffect(() => {
    if (!token) return;
    void refresh();
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => window.clearInterval(timer);
  }, [refresh, token]);

  async function login() {
    setBusy(true);
    try {
      localStorage.setItem("pockethost_api_base", apiBase);
      const previousToken = token;
      const result = await request<{ accessToken: string }>("/api/v1/auth/login", {
        method: "POST", body: JSON.stringify({ username: loginUsername, password: loginPassword }),
      });
      if (previousToken) {
        // A successful credential re-check replaces the old local session.
        await fetch(apiBase.replace(/\/$/, "") + "/api/v1/session", {
          method: "DELETE", headers: { Authorization: `Bearer ${previousToken}` }, cache: "no-store",
        }).catch(() => undefined);
      }
      sessionStorage.setItem("pockethost_device_token", result.accessToken);
      setToken(result.accessToken);
      setOwnerReauthRequired(false);
      setLoginPassword("");
      setMessage("Host account authenticated; the previous session was retired.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Login failed."); }
    finally { setBusy(false); }
  }

  async function loadHostIdentity() {
    const me = await request<{ username: string; role: string; deviceApprovedPairing?: boolean }>("/api/v1/auth/me");
    setHostIdentity(me);
    if (me.role === "OWNER") {
      const result = await request<{ items: Array<{ username: string; role: string; createdAt: number }> }>("/api/v1/auth/accounts");
      setHostAccounts(result.items || []);
      setNeedsOwnerSetup(!(result.items || []).some(account => account.role === "OWNER"));
    } else { setHostAccounts([]); setNeedsOwnerSetup(false); }
  }

  async function recoverOwnerPassword() {
    setBusy(true);
    try {
      const result = await request<{ accessToken: string; username: string; role: string }>("/api/v1/auth/recover-owner", {
        method: "POST", body: JSON.stringify({ newPassword: recoveryPassword }),
      });
      sessionStorage.setItem("pockethost_device_token", result.accessToken);
      setToken(result.accessToken);
      setHostIdentity({ username: result.username, role: result.role, deviceApprovedPairing: false });
      setRecoveryPassword("");
      setMessage("Owner password recovered. All previous Owner and pairing sessions were revoked.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Owner recovery failed.");
    } finally {
      setBusy(false);
    }
  }

  async function createHostAccount() {
    setBusy(true);
    try {
      await request("/api/v1/auth/accounts", { method: "POST", body: JSON.stringify({
        username: accountUsername, password: accountPassword, role: needsOwnerSetup ? "OWNER" : "OPERATOR",
      }) });
      setAccountUsername(""); setAccountPassword("");
      await loadHostIdentity();
      setMessage(needsOwnerSetup ? "Owner account created. Use these credentials for future logins." : "Operator account created.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not create host account."); }
    finally { setBusy(false); }
  }

  async function changeHostPassword() {
    setBusy(true);
    try {
      const result = await request<{ accessToken: string }>("/api/v1/auth/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      sessionStorage.setItem("pockethost_device_token", result.accessToken);
      setToken(result.accessToken);
      setCurrentPassword("");
      setNewPassword("");
      setMessage("Password changed. Other sessions for this account were revoked.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not change password.");
    } finally {
      setBusy(false);
    }
  }

  async function revokeHostAccount(username: string) {
    if (!window.confirm("Revoke operator " + username + "?")) return;
    setBusy(true);
    try {
      await request("/api/v1/auth/accounts/" + encodeURIComponent(username), { method: "DELETE" });
      await loadHostIdentity();
      setMessage("Operator account revoked.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not revoke operator."); }
    finally { setBusy(false); }
  }

  async function beginPairing() {
    setBusy(true);
    try {
      localStorage.setItem("pockethost_api_base", apiBase);
      const result = await request<{ challengeId: string }>("/api/v1/pair/challenge", { method: "POST", body: "{}" });
      setChallengeId(result.challengeId);
      setMessage("A six-digit approval code is now displayed in the PocketHost Android app. Approve this browser only if you initiated this request.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Pairing request failed.");
    } finally { setBusy(false); }
  }

  async function completePairing() {
    setBusy(true);
    try {
      const result = await request<{ accessToken: string }>("/api/v1/pair/complete", {
        method: "POST",
        body: JSON.stringify({ challengeId, code: approvalCode }),
      });
      sessionStorage.setItem("pockethost_device_token", result.accessToken);
      setToken(result.accessToken);
      setApprovalCode("");
      setChallengeId("");
      setMessage("Device paired. Create the first Owner account to enable password login.");
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
      const result = await request<Partial<ResourceLimits>>("/api/v1/resources", {
        method: "POST", body: JSON.stringify({ pythonMaxWorkers: pythonWorkers, nodeHeapMb: nodeHeap }),
      });
      setResources(current => current ? { ...current, ...result } : current);
      setResourceDirty(false);
      setMessage("Runtime resource limits saved. Restart Python and Node runtimes to apply them.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Resource settings could not be saved."); }
    finally { setBusy(false); }
  }

  async function saveCapacity(percent: number, durationMinutes = 0) {
    setBusy(true);
    try {
      const result = await request<CapacityState>("/api/v1/capacity", {
        method: "POST", body: JSON.stringify({ percent, durationMinutes }),
      });
      setCapacity(result);
      setCapacityPercent(result.percent);
      setCapacityDirty(false);
      setMessage(durationMinutes > 0 ? `Capacity scheduled at ${percent}% for ${durationMinutes} minutes.` : `Capacity admission profile set to ${percent}%.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Capacity profile could not be applied."); }
    finally { setBusy(false); }
  }

  async function runCapacityBenchmark() {
    setBusy(true);
    try {
      const result = await request<CapacityBenchmark>("/api/v1/capacity/benchmark", { method: "POST", body: "{}" });
      setBenchmark(result);
      setMessage("Capacity Lab started on the connected Android device. Results will appear as the bounded workload sweep completes.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Capacity benchmark could not start."); }
    finally { setBusy(false); }
  }

  async function tunnelAction(action: "start" | "stop") {
    setBusy(true);
    try {
      const result = await request<{ state: string }>("/api/v1/tunnel/actions", { method: "POST", body: JSON.stringify({ action }) });
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
      sessionStorage.removeItem("pockethost_device_token");
      setToken("");
      setStatus(null);
      setHostIdentity(null); setHostAccounts([]); setNeedsOwnerSetup(false);
      setMetrics(null);
      setSites([]);
      setLogs([]);
      setTunnel(null);
      setResources(null);
      setBusy(false);
    }
  }

  const bytes = (value: number) => value < 1024 ? `${value} B` : value < 1048576 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1048576).toFixed(1)} MB`;

  const runtimeMetrics = metrics?.sources ?? [];

  return <main className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">P</span><div><strong>PocketHost</strong><small>BY ASSAYFLUX</small></div></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav aria-label="Console sections"><a className="nav-item active" href="#overview">◈ Overview</a><a className="nav-item" href="#runtimes">▣ Runtimes</a><a className="nav-item" href="#metrics">⌁ Metrics</a><a className="nav-item" href="#connection">⚙ Connection</a><a className="nav-item" href="#remote-control">⌘ Remote control</a></nav>
      <div className="sidebar-bottom"><span className="connection-dot" /> {token ? "Paired device session" : "Device not paired"}<small>Loopback management API · v1</small></div>
    </aside>
    <section className="main-panel" id="overview">
      <header className="topbar"><div><span className="eyebrow">ASSAYFLUX / CONTROL PLANE</span><h1>Device overview</h1><p>Live status from your PocketHost Android device.</p></div><span className="device-pill"><i /> {token ? "Session active" : "Not connected"}</span></header>
      <section className="notice"><div className="notice-icon">i</div><div><strong>Device connection</strong><p>{message}</p></div></section>
      {token && ownerReauthRequired && <section className="panel" aria-live="polite">
        <div className="panel-heading"><h3>Confirm Owner identity</h3><span className="muted-tag">FRESH AUTHORIZATION</span></div>
        <p>This sensitive change needs a fresh Owner credential check. Sign in again to authorize it; your existing session remains active for read-only access.</p>
        <label>Owner username<input autoComplete="username" value={loginUsername || hostIdentity?.username || ""} onChange={e => setLoginUsername(e.target.value)} /></label>
        <label>Owner password<input type="password" autoComplete="current-password" value={loginPassword} onChange={e => setLoginPassword(e.target.value)} /></label>
        <button disabled={busy || !loginUsername && !hostIdentity?.username || !loginPassword} onClick={login}>Reauthenticate Owner</button>
      </section>}
      {!token && <section className="lower-grid" id="connection">
        <article className="panel">
          <div className="panel-heading"><h3>Host account login</h3><span className="muted-tag">OWNER / OPERATOR</span></div>
          <label>Management API address<input value={apiBase} onChange={e => setApiBase(e.target.value)} spellCheck={false} /></label>
          <label>Username<input autoComplete="username" value={loginUsername} onChange={e => setLoginUsername(e.target.value)} /></label>
          <label>Password<input type="password" autoComplete="current-password" value={loginPassword} onChange={e => setLoginPassword(e.target.value)} /></label>
          <button disabled={busy || !loginUsername || !loginPassword} onClick={login}>Sign in</button>
        </article>
        <article className="panel">
          <div className="panel-heading"><h3>First-time device pairing</h3><span className="muted-tag">ON-DEVICE APPROVAL</span></div>
          <p>Pair only when you can approve the request on the Android phone. The first approved session creates the Owner account.</p>
          <button disabled={busy} onClick={beginPairing}>Request pairing code</button>
          {challengeId && <div className="pair-complete"><p>Enter the code shown in the Android app after approving this request.</p><input aria-label="Device approval code" value={approvalCode} onChange={e => setApprovalCode(e.target.value.replace(/\D/g, "").slice(0, 6))} inputMode="numeric" maxLength={6} placeholder="6-digit code" /><button disabled={busy || approvalCode.length !== 6} onClick={completePairing}>Complete pairing</button></div>}
        </article>
      </section>}
      {token && hostIdentity && <section className="panel">
        <div className="panel-heading"><h3>Host access</h3><span className="muted-tag">{hostIdentity.role}</span></div>
        <p>Signed in as <strong>{hostIdentity.username}</strong>. Operators can inspect device data, operate sites and adjust capacity. Sensitive configuration and account administration remain Owner-only.</p>
        <div className="section-heading"><div><span className="eyebrow">ACCOUNT SECURITY</span><h2>Change your password</h2></div></div>
        <div className="resource-panel">
          <label>Current password<input type="password" autoComplete="current-password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} /></label>
          <label>New password<input type="password" autoComplete="new-password" value={newPassword} onChange={event => setNewPassword(event.target.value)} placeholder="At least 12 characters" /></label>
          <button disabled={busy || currentPassword.length === 0 || newPassword.length < 12}
            onClick={() => void changeHostPassword()}>Change password and revoke other sessions</button>
        </div>
        {hostIdentity.deviceApprovedPairing && !needsOwnerSetup && <section className="panel">
          <div className="panel-heading"><h3>Owner account recovery</h3><span className="muted-tag">DEVICE APPROVAL</span></div>
          <p>This browser session was approved directly on the Android phone. Set a new Owner password to recover access. All existing Owner and pairing sessions will be revoked.</p>
          <div className="resource-panel">
            <label>New Owner password<input type="password" autoComplete="new-password" value={recoveryPassword} onChange={event => setRecoveryPassword(event.target.value)} placeholder="At least 12 characters" /></label>
            <button disabled={busy || recoveryPassword.length < 12} onClick={() => void recoverOwnerPassword()}>Recover Owner account</button>
          </div>
        </section>}
        {hostIdentity.role === "OWNER" && <>
          <div className="section-heading"><div><span className="eyebrow">TEAM ACCESS</span><h2>{needsOwnerSetup ? "Create the Owner account" : "Manage Operators"}</h2></div></div>
          {needsOwnerSetup && <p>Set a unique Owner username and password of at least 12 characters. Credentials are stored encrypted on the phone.</p>}
          <div className="resource-panel">
            <label>Username<input autoComplete="username" value={accountUsername} onChange={e => setAccountUsername(e.target.value)} /></label>
            <label>Password<input type="password" autoComplete="new-password" value={accountPassword} onChange={e => setAccountPassword(e.target.value)} placeholder="At least 12 characters" /></label>
            <button disabled={busy || accountUsername.length < 3 || accountPassword.length < 12} onClick={createHostAccount}>{needsOwnerSetup ? "Create Owner account" : "Create Operator"}</button>
          </div>
          {hostAccounts.filter(account => account.role === "OPERATOR").map(account => <div className="status-line" key={account.username}><span><strong>{account.username}</strong><small>Operator · {new Date(account.createdAt).toLocaleDateString()}</small></span><button className="danger-button" disabled={busy} onClick={() => void revokeHostAccount(account.username)}>Revoke</button></div>)}
        </>}
      </section>}
      <div className="section-heading"><div><span className="eyebrow">SITE REGISTRY</span><h2>Configured websites</h2></div><span className="muted-tag">{sites.length} REGISTERED</span></div>
      <section className="panel">
        {sites.length === 0 ? <p>No registered websites yet. Import a project and assign its hostname in the Android app.</p> : sites.map(site => <div className="status-line" key={site.id}><span><strong>{site.name}</strong><small className="site-hosts">{site.hostnames.join(", ") || "No hostname assigned"}</small></span><span className="site-actions"><strong>{site.runtime} · {site.state}</strong><button disabled={busy} onClick={() => siteAction(site, "start")}>Start</button><button disabled={busy} onClick={() => siteAction(site, "stop")}>Stop</button></span></div>)}
      </section>
      <section className="metric-grid" id="metrics">
        <article className="metric-card"><span>Rust static requests</span><strong>{metrics?.sources.find(x => x.source === "rust-static")?.requests?.toLocaleString() ?? "—"}</strong><small>Persisted device counter</small></article>
        <article className="metric-card"><span>Static data served</span><strong>{metrics ? bytes(metrics.sources.find(x => x.source === "rust-static")?.bytesServed ?? 0) : "—"}</strong><small>Rust static server only</small></article>
        <article className="metric-card"><span>Last sample</span><strong>{metrics ? new Date(metrics.sampledAt).toLocaleTimeString() : "—"}</strong><small>Device-reported timestamp</small></article>
      </section>
      <div className="section-heading"><div><span className="eyebrow">RUNTIME OBSERVABILITY</span><h2>Application traffic</h2></div><span className="muted-tag">SOURCE-ATTRIBUTED</span></div>
      <section className="metric-grid">
        {(["python-wsgi", "node"] as const).map(source => {
          const item = runtimeMetrics.find(metric => metric.source === source);
          const label = source === "python-wsgi" ? "Python WSGI" : "Node.js";
          return <article className="metric-card" key={source}>
            <span>{label} requests</span>
            <strong>{item?.requests?.toLocaleString() ?? "Not running"}</strong>
            <small>{item ? bytes(item.bytes_sent ?? item.bytesSent ?? 0) + " sent · " + (item.errors ?? 0) + " errors · " + (item.active ?? 0) + " active" : "Runtime-specific counters appear while the service is running."}</small>
            {item && <small>{item.processPssKb ? "Process memory " + bytes(item.processPssKb * 1024) : item.rssBytes ? "RSS " + bytes(item.rssBytes) : "Memory sample unavailable"}</small>}
          </article>;
        })}
      </section>
      <div className="section-heading"><div><span className="eyebrow">RESOURCE POLICY</span><h2>Runtime resource limits</h2></div><span className="muted-tag">DEVICE-AWARE</span></div>
      <section className="panel resource-panel">
        <div className="resource-control"><label>Python WSGI worker ceiling <strong>{pythonWorkers}</strong><input type="range" min="2" max="16" step="1" value={pythonWorkers} onChange={event => { setPythonWorkers(Number(event.target.value)); setResourceDirty(true); }} /></label></div>
        <div className="resource-control"><label>Node.js V8 heap ceiling <strong>{nodeHeap} MB</strong><input type="range" min="96" max="512" step="16" value={nodeHeap} onChange={event => { setNodeHeap(Number(event.target.value)); setResourceDirty(true); }} /></label></div>
        <p>{resources?.note ?? "Resource limits are device-managed."}</p>
        <p>Available device memory: {metrics?.deviceResources?.availableDeviceMemoryBytes ? bytes(metrics.deviceResources.availableDeviceMemoryBytes) : "—"} · App process PSS: {metrics?.deviceResources?.processPssKb ? bytes(metrics.deviceResources.processPssKb * 1024) : "—"}</p>
        <button disabled={busy || !resources} onClick={saveResources}>Save resource limits</button>
      </section>
      {benchmark?.levels && benchmark.levels.length > 0 && <section className="panel">
        <div className="panel-heading"><h3>Measured capacity curve</h3><span className="muted-tag">P95 · MILLISECONDS</span></div>
        <div className="capacity-chart" role="group" aria-label="Measured p95 latency by concurrency">
          {benchmark.levels.map(level => <button type="button" className="capacity-point" key={level.concurrency}
            aria-label={level.concurrency + " concurrent, p95 " + level.p95LatencyMs + " milliseconds"}
            title={level.concurrency + " concurrent · p95 " + level.p95LatencyMs + " ms · " + level.samples + " samples"}
            onClick={() => setMessage(level.concurrency + " concurrent: p95 " + level.p95LatencyMs + " ms; " + level.samples + " samples, " + level.errors + " errors.")}>
            <span className="capacity-bar" style={{ height: Math.max(5, Math.min(100, level.p95LatencyMs / Math.max(1, ...benchmark.levels!.map(point => point.p95LatencyMs)) * 100)) + "%" }} />
            <strong>{level.p95LatencyMs} ms</strong><small>{level.concurrency}x</small>
          </button>)}
        </div>
        <p>Each bar is a measured benchmark point, not live visitor traffic. Select a bar to inspect its sample and error counts in the status message.</p>
      </section>}
      <div className="section-heading" id="capacity"><div><span className="eyebrow">DEVICE PROTECTION</span><h2>Capacity & operations</h2></div><span className="muted-tag">{capacity?.scheduled ? "SCHEDULED PROFILE" : "ADMISSION CONTROL"}</span></div>
      <section className="panel resource-panel">
        <div className="resource-control"><label>Safe capacity profile <strong>{capacityPercent}%</strong><input type="range" min="1" max="100" step="1" value={capacityPercent} onChange={event => { setCapacityPercent(Number(event.target.value)); setCapacityDirty(true); }} /></label></div>
        <div className="resource-control"><label>Effective connection ceiling <strong>{capacity?.effectiveConcurrencyLimit ?? "—"}</strong><span className="scope-note">{capacity?.semantics ?? "Waiting for device telemetry."}</span></label></div>
        <p>Active connections: {capacity?.activeConnections ?? "—"} · Waiting queue: {capacity?.queuedConnections ?? "—"} · Rate-limited requests: {capacity?.rateLimitedRequests ?? "—"} · Site-cap rejections: {capacity?.siteLimitRejections ?? "—"} · Rejected with HTTP 503: {capacity?.rejectedConnections ?? "—"} · Measured safe ceiling: {capacity?.safeConcurrency ?? "—"} · Device health: {capacity?.healthState ?? "—"}</p>
        <p>Device: {metrics?.deviceResources?.telemetry?.manufacturer ?? "—"} {metrics?.deviceResources?.telemetry?.model ?? ""} · Android API {metrics?.deviceResources?.telemetry?.androidApi ?? "—"} · CPU cores {metrics?.deviceResources?.telemetry?.cpuCores ?? "—"} · CPU load {metrics?.deviceResources?.telemetry?.cpuUsagePercent == null ? "warming up / unavailable" : Math.round(metrics?.deviceResources?.telemetry?.cpuUsagePercent ?? 0) + "%"} · RAM available {metrics?.deviceResources?.telemetry?.availableMemoryBytes == null ? "—" : bytes(metrics?.deviceResources?.telemetry?.availableMemoryBytes ?? 0)} · App PSS {metrics?.deviceResources?.telemetry?.processPssKb == null ? "—" : bytes((metrics?.deviceResources?.telemetry?.processPssKb ?? 0) * 1024)} · Storage free {metrics?.deviceResources?.telemetry?.storageAvailableBytes == null ? "—" : bytes(metrics?.deviceResources?.telemetry?.storageAvailableBytes ?? 0)} · Battery {metrics?.deviceResources?.telemetry?.batteryPercent ?? "—"}% · Thermal {metrics?.deviceResources?.telemetry?.thermalLabel ?? "—"} · Network {metrics?.deviceResources?.telemetry?.networkTransport ?? "—"}</p>
        <div className="site-actions"><button disabled={busy || !capacity} onClick={() => void saveCapacity(capacityPercent)}>Apply profile</button><button disabled={busy || !capacity} onClick={() => void saveCapacity(30, 180)}>30% for 3 hours</button><button disabled={busy || !status || status.state !== "local"} onClick={() => void runCapacityBenchmark()}>Run Capacity Lab</button></div>
        <p>Benchmark state: {benchmark?.state ?? "NOT_RUN"} · {benchmark?.workload ?? "No completed workload measurement."}</p>
        {benchmark?.levels?.map(level => <div className="status-line" key={level.concurrency}><span>{level.concurrency} concurrent · {level.samples} samples · {level.errors} errors</span><strong>p95 {level.p95LatencyMs} ms</strong></div>)}
        <p>Daily operations reports retained: {dailyReports.length}. Latest report: {dailyReports[0]?.date ?? "Not yet generated"} · {dailyReports[0] ? dailyReports[0].totalRequests.toLocaleString() + " requests · " + bytes(dailyReports[0].totalBytes) : "The current-day snapshot refreshes hourly; the previous day is archived at local-day rollover."}</p>
      </section>
      <div className="section-heading" id="runtimes"><div><span className="eyebrow">DEVICE RUNTIMES</span><h2>Runtime health</h2></div><span className="muted-tag">{status?.state ?? "NOT CONNECTED"}</span></div>
      <section className="lower-grid">
        <article className="panel"><div className="panel-heading"><h3>Cloudflare Tunnel</h3><span className="muted-tag">{tunnel?.state ?? "UNKNOWN"}</span></div><p>{tunnel?.configured ? "Encrypted tunnel token is configured on the Android device." : "No tunnel token is configured."}</p><div className="site-actions"><button disabled={busy || !tunnel?.configured} onClick={() => tunnelAction("start")}>Start tunnel</button><button disabled={busy} onClick={() => tunnelAction("stop")}>Stop tunnel</button></div></article>
        <article className="panel"><div className="panel-heading"><h3>Recent operations</h3><span className="muted-tag">{logs.length} EVENTS</span></div>{logs.slice(0,8).map((entry,i)=><div className="log-line" key={entry.timestamp+i}><strong>{entry.level}</strong><span>{entry.event}</span><small>{entry.message}</small></div>)}</article>
      </section>
      <section className="lower-grid"><article className="panel"><div className="panel-heading"><h3>Runtime status</h3><span className="status-muted">{status ? "Live device data" : "Waiting for pairing"}</span></div>
        {(status?.runtimes ?? [{ name: "rust-static", state: "—" }, { name: "python", state: "—" }, { name: "node", state: "—" }]).map(runtime => <div className="status-line" key={runtime.name}><span>{runtime.name}</span><strong>{runtime.state}</strong></div>)}
      </article><article className="panel"><div className="panel-heading"><h3>Session controls</h3><span className="muted-tag">LOCAL DEVICE</span></div><p>Revoking this session invalidates its bearer credential on the device.</p><button className="danger-button" disabled={!token || busy} onClick={revoke}>Revoke this session</button><p className="scope-note">Python and Node metrics are process-local and include runtime-specific request counters and memory samples when those runtimes are active.</p></article></section>
      <RemoteControlPlane />
      <footer>Designed by AssayFlux <span>·</span> Your website. Your device. Your control.</footer>
    </section>
  </main>;
}
