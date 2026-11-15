import { useState } from "react";

type Device = {
  id: string;
  name: string;
  state: Record<string, unknown>;
  last_seen: string | null;
  created_at: string;
};
type Config = { version: number; config: Record<string, unknown>; updated_at: string };

const initialPayloads: Record<string, string> = {
  site_action: JSON.stringify({ siteId: "my-site", action: "stop" }, null, 2),
  tunnel_action: JSON.stringify({ action: "stop" }, null, 2),
  restart_runtime: JSON.stringify({ runtime: "python" }, null, 2),
};

export function RemoteControlPlane() {
  const [endpoint, setEndpoint] = useState(() => localStorage.getItem("pockethost_control_endpoint") || "");
  const [adminToken, setAdminToken] = useState(() => sessionStorage.getItem("pockethost_control_admin") || "");
  const [deviceName, setDeviceName] = useState("");
  const [devices, setDevices] = useState<Device[]>([]);
  const [selected, setSelected] = useState("");
  const [issuedToken, setIssuedToken] = useState("");
  const [configText, setConfigText] = useState('{"pythonMaxWorkers":8,"nodeHeapMb":192,"tunnelEnabled":false}');
  const [configVersion, setConfigVersion] = useState<number | null>(null);
  const [commandKind, setCommandKind] = useState("site_action");
  const [payloadText, setPayloadText] = useState(initialPayloads.site_action);
  const [message, setMessage] = useState("Connect to your independently deployed PocketHost Control Plane.");
  const [busy, setBusy] = useState(false);

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(endpoint.replace(/\/$/, "") + path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${adminToken}`,
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
      localStorage.setItem("pockethost_control_endpoint", endpoint);
      sessionStorage.setItem("pockethost_control_admin", adminToken);
      const next = await request<Device[]>("/v1/admin/devices");
      setDevices(next);
      setMessage(`Connected. ${next.length} registered device(s).`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not connect to the Control Plane.");
    } finally { setBusy(false); }
  }

  async function refreshDevices() {
    setBusy(true);
    try {
      const next = await request<Device[]>("/v1/admin/devices");
      setDevices(next);
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
      <label>Control Plane HTTPS URL<input value={endpoint} onChange={event => setEndpoint(event.target.value)} placeholder="https://control.example.com" /></label>
      <label>Administrator token<input type="password" value={adminToken} onChange={event => setAdminToken(event.target.value)} placeholder="Administrator bearer token" /></label>
      <div className="site-actions">
        <button disabled={busy || !endpoint.startsWith("https://") || adminToken.length < 32} onClick={connect}>Connect</button>
        <button disabled={busy || !adminToken} onClick={refreshDevices}>Refresh devices</button>
      </div>
    </div>
    <p className="scope-note">{message}</p>
    <div className="section-heading"><div><span className="eyebrow">DEVICE REGISTRY</span><h2>Registered phones</h2></div><span className="muted-tag">{devices.length} DEVICES</span></div>
    <div className="resource-panel">
      <label>New device name<input value={deviceName} onChange={event => setDeviceName(event.target.value)} placeholder="e.g. PocketHost main phone" /></label>
      <button disabled={busy || !adminToken || !deviceName.trim()} onClick={registerDevice}>Register device</button>
      {issuedToken && <div className="notice"><div><strong>One-time device token</strong><p>Copy this token into Advanced Mode on the target phone. It will not be shown again.</p><code>{issuedToken}</code><button onClick={() => void navigator.clipboard.writeText(issuedToken)}>Copy token</button></div></div>}
    </div>
    {devices.map(device => <div className="status-line" key={device.id}>
      <span><strong>{device.name}</strong><small>{device.id} · Last seen: {device.last_seen ? new Date(device.last_seen).toLocaleString() : "Never"}</small></span>
      <span className="site-actions"><strong>{device.last_seen ? "Registered" : "Awaiting heartbeat"}</strong><button disabled={busy} onClick={() => void loadConfig(device.id)}>Manage</button></span>
    </div>)}
    {selected && <div className="lower-grid">
      <article className="panel">
        <div className="panel-heading"><h3>Desired device configuration</h3><span className="muted-tag">VERSION {configVersion ?? "—"}</span></div>
        <p>Supported keys currently include pythonMaxWorkers, nodeHeapMb and tunnelEnabled. The phone validates settings before applying them.</p>
        <textarea rows={9} value={configText} onChange={event => setConfigText(event.target.value)} spellCheck={false}/>
        <button disabled={busy || configVersion === null} onClick={saveConfig}>Save and queue configuration</button>
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
    <p className="scope-note">The backend does not access phone files directly and exposes no shell command endpoint. Site deployment still uses the native import workflow; remote archive deployment is not yet supported.</p>
  </section>;
}
