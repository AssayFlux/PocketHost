import { useState } from "react";

type Device = {
  id: string;
  name: string;
  state: Record<string, unknown>;
  last_seen: string | null;
  created_at: string;
};
type Config = { version: number; config: Record<string, unknown>; updated_at: string };
type AuditEntry = { id: number; actor: string; action: string; target: string; details: Record<string, unknown>; createdAt: string };
type ControlUser = { id: string; name: string; role: "editor" | "viewer"; active: boolean };

const initialPayloads: Record<string, string> = {
  site_action: JSON.stringify({ siteId: "my-site", action: "stop" }, null, 2),
  tunnel_action: JSON.stringify({ action: "stop" }, null, 2),
  restart_runtime: JSON.stringify({ runtime: "python" }, null, 2),
};

export function RemoteControlPlane() {
  const [endpoint, setEndpoint] = useState(() => localStorage.getItem("pockethost_control_endpoint") || "");
  const [adminToken, setAdminToken] = useState(() => sessionStorage.getItem("pockethost_control_admin") || "");
  const [sessionToken, setSessionToken] = useState("");
  const [deviceName, setDeviceName] = useState("");
  const [devices, setDevices] = useState<Device[]>([]);
  const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
  const [users, setUsers] = useState<ControlUser[]>([]);
  const [newUserName, setNewUserName] = useState("");
  const [newUserRole, setNewUserRole] = useState<"editor" | "viewer">("viewer");
  const [newUserToken, setNewUserToken] = useState("");
  const [selected, setSelected] = useState("");
  const [issuedToken, setIssuedToken] = useState("");
  const [configText, setConfigText] = useState('{"pythonMaxWorkers":8,"nodeHeapMb":192,"tunnelEnabled":false}');
  const [configVersion, setConfigVersion] = useState<number | null>(null);
  const [commandKind, setCommandKind] = useState("site_action");
  const [payloadText, setPayloadText] = useState(initialPayloads.site_action);
  const [message, setMessage] = useState("Connect to your independently deployed PocketHost Control Plane.");
  const [busy, setBusy] = useState(false);
  const [archive, setArchive] = useState<File | null>(null);

  async function request<T>(path: string, init: RequestInit = {}, bearerToken = sessionToken): Promise<T> {
    if (!bearerToken) throw new Error("Create an administrative session before using the Control Plane.");
    const response = await fetch(endpoint.replace(/\/$/, "") + path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${bearerToken}`,
        ...init.headers,
      },
      cache: "no-store",
    });
    const body = await response.text();
    let data: Record<string, unknown> = {};
    if (body) {
      try { data = JSON.parse(body) as Record<string, unknown>; }
      catch { data = { error: body.slice(0, 200) }; }
    }
    if (!response.ok) throw new Error(typeof data.error === "string" ? data.error : `Control Plane returned HTTP ${response.status}`);
    return data as T;
  }

  async function connect() {
    setBusy(true);
    try {
      if (!endpoint.startsWith("https://")) throw new Error("Use an HTTPS Control Plane URL.");
      const sessionResponse = await fetch(endpoint.replace(/\/$/, "") + "/v1/admin/session", {
        method: "POST",
        headers: { Authorization: `Bearer ${adminToken}` },
        cache: "no-store",
      });
      const sessionBody = await sessionResponse.json() as { accessToken?: string; error?: string };
      if (!sessionResponse.ok || !sessionBody.accessToken) {
        throw new Error(sessionBody.error || `Control Plane session exchange failed (HTTP ${sessionResponse.status}).`);
      }
      const nextSession = sessionBody.accessToken;
      setSessionToken(nextSession);
      localStorage.setItem("pockethost_control_endpoint", endpoint);
      sessionStorage.setItem("pockethost_control_admin", adminToken);
      const next = await request<Device[]>("/v1/admin/devices", {}, nextSession);
      setDevices(next);
      setAuditEntries(await request<AuditEntry[]>("/v1/admin/audit", {}, nextSession));
      setMessage(`Connected with a 12-hour administrative session. ${next.length} registered device(s).`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not connect to the Control Plane.");
    } finally { setBusy(false); }
  }

  async function disconnect() {
    setBusy(true);
    try {
      await request<void>("/v1/admin/session", { method: "DELETE" });
      setSessionToken("");
      setDevices([]);
      setUsers([]);
      setSelected("");
      setMessage("Administrative session revoked.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not revoke the session."); }
    finally { setBusy(false); }
  }

  async function refreshDevices() {
    setBusy(true);
    try {
      const next = await request<Device[]>("/v1/admin/devices");
      setDevices(next);
      setAuditEntries(await request<AuditEntry[]>("/v1/admin/audit"));
      setMessage(`Device inventory refreshed · ${next.length} device(s).`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not refresh devices."); }
    finally { setBusy(false); }
  }

  async function registerDevice() {
    setBusy(true);
    setIssuedToken("");
    try {
      const result = await request<{ id: string; name: string; device_token: string }>("/v1/admin/devices", {
        method: "POST", body: JSON.stringify({ name: deviceName }),
      });
      setIssuedToken(result.device_token);
      setSelected(result.id);
      setDeviceName("");
      await refreshDevices();
      setMessage("Device registered. Copy its token now; it is only returned once.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Device registration failed."); }
    finally { setBusy(false); }
  }

  async function loadUsers() {
    setBusy(true);
    try { setUsers(await request<ControlUser[]>("/v1/admin/users")); setMessage("Team accounts loaded."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Only the owner can manage team accounts."); }
    finally { setBusy(false); }
  }

  async function createUser() {
    setBusy(true); setNewUserToken("");
    try {
      const created = await request<{ id: string; name: string; role: string; token: string }>("/v1/admin/users", { method: "POST", body: JSON.stringify({ name: newUserName, role: newUserRole }) });
      setNewUserToken(created.token); setNewUserName(""); await loadUsers();
      setMessage("Team account created. Copy its token; it is displayed only once.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not create team account."); }
    finally { setBusy(false); }
  }

  async function rotateUserToken(user: ControlUser) {
    if (!window.confirm("Rotate " + user.name + "'s token? Their current token will stop working immediately.")) return;
    setBusy(true); setNewUserToken("");
    try {
      const result = await request<{ token: string }>(`/v1/admin/users/${encodeURIComponent(user.id)}/rotate-token`, { method: "POST" });
      setNewUserToken(result.token);
      setMessage("User token rotated. Copy the replacement now; the previous token is already revoked.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not rotate user token."); }
    finally { setBusy(false); }
  }

  async function revokeUser(user: ControlUser) {
    if (!window.confirm("Revoke " + user.name + "? Their API token will stop working immediately.")) return;
    setBusy(true);
    try { await request<void>("/v1/admin/users/" + encodeURIComponent(user.id), { method: "DELETE" }); setUsers(items => items.filter(item => item.id !== user.id)); setMessage("Team account revoked."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Could not revoke account."); }
    finally { setBusy(false); }
  }
  async function revokeDevice(device: Device) {
    if (!window.confirm(`Revoke ${device.name}? Its device token and queued commands will be invalidated.`)) return;
    setBusy(true);
    try {
      await request<void>(`/v1/admin/devices/${encodeURIComponent(device.id)}`, { method: "DELETE" });
      setDevices(current => current.filter(item => item.id !== device.id));
      if (selected === device.id) setSelected("");
      setMessage(`${device.name} was revoked.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Device revocation failed."); }
    finally { setBusy(false); }
  }

  async function loadConfig(deviceId: string) {
    setSelected(deviceId);
    setBusy(true);
    try {
      const result = await request<Config>(`/v1/admin/devices/${encodeURIComponent(deviceId)}/config`);
      setConfigVersion(result.version);
      setConfigText(JSON.stringify(result.config, null, 2));
      setMessage(`Loaded desired configuration version ${result.version}.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not load device configuration."); }
    finally { setBusy(false); }
  }

  async function saveConfig() {
    if (!selected) return;
    setBusy(true);
    try {
      const config = JSON.parse(configText) as Record<string, unknown>;
      const result = await request<Config>(`/v1/admin/devices/${encodeURIComponent(selected)}/config`, {
        method: "PUT",
        body: JSON.stringify({ config, expected_version: configVersion }),
      });
      setConfigVersion(result.version);
      setMessage(`Configuration version ${result.version} saved and queued for the device.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Configuration is invalid or could not be saved."); }
    finally { setBusy(false); }
  }

  async function deployArchive() {
    if (!selected || !archive) return;
    if (!archive.name.toLowerCase().endsWith('.zip') || archive.size < 1 || archive.size > 50 * 1024 * 1024) { setMessage('Choose a ZIP no larger than 50 MiB.'); return; }
    setBusy(true);
    try {
      const result = await request<{ artifactId: string; sha256: string; state: string }>( '/v1/admin/devices/' + encodeURIComponent(selected) + '/artifacts', { method: 'POST', headers: { 'Content-Type': 'application/zip' }, body: await archive.arrayBuffer() });
      setMessage('Website archive queued: ' + result.artifactId + '.');
      setArchive(null);
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Website deployment failed.'); }
    finally { setBusy(false); }
  }
  async function queueCommand() {
    if (!selected) return;
    setBusy(true);
    try {
      const payload = JSON.parse(payloadText) as Record<string, unknown>;
      const result = await request<{ commandId: string }>(`/v1/admin/devices/${encodeURIComponent(selected)}/commands`, {
        method: "POST", body: JSON.stringify({ kind: commandKind, payload }),
      });
      setMessage(`Command queued: ${result.commandId}. The phone will poll and acknowledge it.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Command could not be queued."); }
    finally { setBusy(false); }
  }

  return <section className="panel" id="remote-control">
    <div className="panel-heading"><h3>Remote Control Plane</h3><span className="muted-tag">INDEPENDENT BACKEND</span></div>
    <p>Manage registered PocketHost phones while their hosting services remain active. The backend queues typed commands; the phone initiates outbound HTTPS polling.</p>
    <div className="resource-panel">
      <label>Control Plane HTTPS URL<input value={endpoint} onChange={event => { setEndpoint(event.target.value); setSessionToken(""); }} placeholder="https://control.example.com" /></label>
      <label>API credential<input type="password" value={adminToken} onChange={event => { setAdminToken(event.target.value); setSessionToken(""); }} placeholder="Owner, Editor or Viewer credential" /><small>Used only to create a short-lived administrative session; it is not accepted by management endpoints.</small></label>
      <div className="site-actions">
        <button disabled={busy || !endpoint.startsWith("https://") || adminToken.length < 32} onClick={connect}>Create session &amp; connect</button>
        <button disabled={busy || !sessionToken} onClick={refreshDevices}>Refresh devices</button>
        <button disabled={busy || !sessionToken} onClick={disconnect}>Disconnect</button>
      </div>
    </div>
    <p className="scope-note">{message}</p>
    <div className="section-heading"><div><span className="eyebrow">DEVICE REGISTRY</span><h2>Registered phones</h2></div><span className="muted-tag">{devices.length} DEVICES</span></div>
    <div className="resource-panel">
      <label>New device name<input value={deviceName} onChange={event => setDeviceName(event.target.value)} placeholder="e.g. PocketHost main phone" /></label>
      <button disabled={busy || !sessionToken || !deviceName.trim()} onClick={registerDevice}>Register device</button>
      {issuedToken && <div className="notice"><div><strong>One-time device token</strong><p>Copy this token into Advanced Mode on the target phone. It will not be shown again.</p><code>{issuedToken}</code><button onClick={() => void navigator.clipboard.writeText(issuedToken)}>Copy token</button></div></div>}
    </div>
    {devices.map(device => <div className="status-line" key={device.id}>
      <span><strong>{device.name}</strong><small>{device.id} · Last seen: {device.last_seen ? new Date(device.last_seen).toLocaleString() : "Never"}</small></span>
      <span className="site-actions"><strong>{device.last_seen && Date.now() - Date.parse(device.last_seen) < 90_000 ? "Online" : "Offline"}</strong><button disabled={busy} onClick={() => void loadConfig(device.id)}>Manage</button><button className="danger-button" disabled={busy} onClick={() => void revokeDevice(device)}>Revoke</button></span>
    </div>)}
    {selected && <div className="lower-grid">
      <article className="panel">
        <div className="panel-heading"><h3>Desired device configuration</h3><span className="muted-tag">VERSION {configVersion ?? "—"}</span></div>
        <p>Supported keys currently include pythonMaxWorkers, nodeHeapMb and tunnelEnabled. The phone validates settings before applying them.</p>
        <textarea rows={9} value={configText} onChange={event => setConfigText(event.target.value)} spellCheck={false}/>
        <button disabled={busy || configVersion === null} onClick={saveConfig}>Save and queue configuration</button>
      </article>
      <article className="panel">
        <div className="panel-heading"><h3>Deploy website archive</h3><span className="muted-tag">ZIP · 50 MiB MAX</span></div>
        <p>Upload a ZIP containing index.html at its root. The phone verifies SHA-256 and atomically activates the release while hosting stays online.</p>
        <label>Website ZIP<input type="file" accept=".zip,application/zip" onChange={event => setArchive(event.target.files?.[0] || null)} /></label>
        <button disabled={busy || !archive} onClick={deployArchive}>Upload and deploy</button>
      </article>
      <article className="panel">
        <div className="panel-heading"><h3>Remote actions</h3><span className="muted-tag">TYPED COMMANDS</span></div>
        <label>Command<select value={commandKind} onChange={event => { setCommandKind(event.target.value); setPayloadText(initialPayloads[event.target.value] || "{}"); }}>
          <option value="site_action">Start / stop site</option>
          <option value="tunnel_action">Start / stop tunnel</option>
          <option value="restart_runtime">Restart runtime</option>
        </select></label>
        <label>JSON payload<textarea rows={6} value={payloadText} onChange={event => setPayloadText(event.target.value)} spellCheck={false}/></label>
        <button disabled={busy} onClick={queueCommand}>Queue command</button>
      </article>
    </div>}
    <p className="scope-note">The backend cannot browse the phone filesystem or execute shell commands. Website archives are validated on-device and activated using the native release importer.</p>
    <div className="section-heading"><div><span className="eyebrow">ACCESS CONTROL</span><h2>Team permissions</h2></div><span className="muted-tag">OWNER MANAGED</span></div>
    <div className="resource-panel">
      <p>Owner credentials can create revocable Editor and Viewer accounts. Editors can manage devices and deployments; Viewers can inspect device state, configuration and audit history.</p>
      <label>Account name<input value={newUserName} onChange={event => setNewUserName(event.target.value)} placeholder="Team member" /></label>
      <label>Permission role<select value={newUserRole} onChange={event => setNewUserRole(event.target.value as "editor" | "viewer")}><option value="viewer">Viewer · read only</option><option value="editor">Editor · manage devices</option></select></label>
      <div className="site-actions"><button disabled={busy || !newUserName.trim()} onClick={createUser}>Create account</button><button disabled={busy} onClick={loadUsers}>Load team</button></div>
      {newUserToken && <div className="notice"><strong>One-time team token</strong><code>{newUserToken}</code><button onClick={() => void navigator.clipboard.writeText(newUserToken)}>Copy token</button></div>}
      {users.map(user => <div className="status-line" key={user.id}><span><strong>{user.name}</strong><small>{user.role} · {user.active ? "Active" : "Revoked"}</small></span><span className="site-actions"><button disabled={busy || !user.active} onClick={() => void rotateUserToken(user)}>Rotate token</button><button className="danger-button" disabled={busy || !user.active} onClick={() => void revokeUser(user)}>Revoke</button></span></div>)}
    </div>
    <div className="section-heading"><div><span className="eyebrow">SECURITY & GOVERNANCE</span><h2>Audit history</h2></div><span className="muted-tag">LATEST 200 EVENTS</span></div>
    <div className="resource-panel">
      {auditEntries.length === 0 ? <p>No audit events recorded yet.</p> : auditEntries.map(entry => <div className="status-line" key={entry.id}>
        <span><strong>{entry.action}</strong><small>{entry.actor} · {entry.target}</small></span>
        <span>{new Date(entry.createdAt).toLocaleString()}</span>
      </div>)}
    </div>
    <p className="scope-note">Audit events record device registration/revocation, configuration changes, command queueing/acknowledgement and website deployment. Owner, Editor and Viewer roles are enforced by the backend. Audit history is a latest-200 view; configure secure database backups for long-term retention.</p>
  </section>;
}
