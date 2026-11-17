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
type RelaySite = { id: string; hostname: string; siteId: string; verified: boolean; enabled: boolean };
type RelayChallenge = { hostname: string; name: string; value: string };

function decodeBase64Url(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(base64 + "=".repeat((4 - base64.length % 4) % 4));
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
  return bytes.buffer;
}

function encodeBase64Url(value: ArrayBuffer): string {
  const bytes = new Uint8Array(value);
  let binary = "";
  bytes.forEach(byte => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function serializePasskeyCredential(credential: PublicKeyCredential): Record<string, unknown> {
  const response = credential.response;
  const common = {
    id: credential.id,
    rawId: encodeBase64Url(credential.rawId),
    type: credential.type,
    extensions: credential.getClientExtensionResults(),
  };
  if (response instanceof AuthenticatorAttestationResponse) {
    return {
      ...common,
      response: {
        clientDataJSON: encodeBase64Url(response.clientDataJSON),
        attestationObject: encodeBase64Url(response.attestationObject),
        transports: response.getTransports?.() || [],
      },
    };
  }
  if (response instanceof AuthenticatorAssertionResponse) {
    return {
      ...common,
      response: {
        clientDataJSON: encodeBase64Url(response.clientDataJSON),
        authenticatorData: encodeBase64Url(response.authenticatorData),
        signature: encodeBase64Url(response.signature),
        userHandle: response.userHandle ? encodeBase64Url(response.userHandle) : null,
      },
    };
  }
  throw new Error("The authenticator returned an unsupported credential response.");
}


const initialPayloads: Record<string, string> = {
  site_action: JSON.stringify({ siteId: "my-site", action: "stop" }, null, 2),
  tunnel_action: JSON.stringify({ action: "stop" }, null, 2),
  restart_runtime: JSON.stringify({ runtime: "python" }, null, 2),
};

export function RemoteControlPlane() {
  const [endpoint, setEndpoint] = useState(() => localStorage.getItem("pockethost_control_endpoint") || "");
  const [adminToken, setAdminToken] = useState("");
  const [reauthCredential, setReauthCredential] = useState("");
  const [sessionToken, setSessionToken] = useState("");
  const [sessionRole, setSessionRole] = useState<"owner" | "editor" | "viewer" | "">("");
  const [deviceName, setDeviceName] = useState("");
  const [devices, setDevices] = useState<Device[]>([]);
  const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
  const [users, setUsers] = useState<ControlUser[]>([]);
  const [newUserName, setNewUserName] = useState("");
  const [newUserRole, setNewUserRole] = useState<"editor" | "viewer">("viewer");
  const [newUserToken, setNewUserToken] = useState("");
  const [selected, setSelected] = useState("");
  const [issuedToken, setIssuedToken] = useState("");
  const [issuedRelayToken, setIssuedRelayToken] = useState("");
  const [configText, setConfigText] = useState('{"pythonMaxWorkers":8,"nodeHeapMb":192,"tunnelEnabled":false}');
  const [configVersion, setConfigVersion] = useState<number | null>(null);
  const [commandKind, setCommandKind] = useState("site_action");
  const [payloadText, setPayloadText] = useState(initialPayloads.site_action);
  const [message, setMessage] = useState("Connect to your independently deployed PocketHost Control Plane.");
  const [busy, setBusy] = useState(false);
  const [archive, setArchive] = useState<File | null>(null);
  const [relaySites, setRelaySites] = useState<RelaySite[]>([]);
  const [relayHostname, setRelayHostname] = useState("");
  const [relaySiteId, setRelaySiteId] = useState("static-default");
  const [relayChallenge, setRelayChallenge] = useState<RelayChallenge | null>(null);
  const [passkeyDisplayName, setPasskeyDisplayName] = useState("PocketHost Owner");
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [recoveryCodeInput, setRecoveryCodeInput] = useState("");

  async function exchangeAdminSession(credential: string): Promise<string> {
    const response = await fetch(endpoint.replace(/\/$/, "") + "/v1/admin/session", {
      method: "POST",
      headers: { Authorization: `Bearer ${credential}` },
      cache: "no-store",
    });
    const body = await response.json() as { accessToken?: string; role?: "owner" | "editor" | "viewer"; error?: string };
    if (!response.ok || !body.accessToken) {
      throw new Error(body.error || `Control Plane session exchange failed (HTTP ${response.status}).`);
    }
    const previousSession = sessionToken;
    setSessionToken(body.accessToken);
    setSessionRole(body.role || "");
    if (previousSession) {
      // Retire the old bearer as soon as its replacement exists. A failed best-effort
      // revoke does not discard the fresh session or block the requested operation.
      await fetch(endpoint.replace(/\/$/, "") + "/v1/admin/session", {
        method: "DELETE",
        headers: { Authorization: `Bearer ${previousSession}` },
        cache: "no-store",
      }).catch(() => undefined);
    }
    return body.accessToken;
  }

  async function authenticateOwnerPasskey(): Promise<string> {
    if (!endpoint.startsWith("https://")) throw new Error("Use an HTTPS Control Plane URL for passkey authentication.");
    if (!window.PublicKeyCredential || !navigator.credentials) throw new Error("This browser does not support WebAuthn passkeys.");
    const base = endpoint.replace(/\/$/, "");
    const startResponse = await fetch(base + "/v1/admin/passkeys/authentication/start", { method: "POST", cache: "no-store" });
    const start = await startResponse.json() as { challengeId?: string; publicKey?: Record<string, unknown>; error?: string };
    if (!startResponse.ok || !start.challengeId || !start.publicKey) throw new Error(start.error || "Could not start passkey authentication.");
    const raw = start.publicKey;
    const options = {
      ...raw,
      challenge: decodeBase64Url(String(raw.challenge)),
      allowCredentials: Array.isArray(raw.allowCredentials) ? raw.allowCredentials.map(item => {
        const descriptor = item as Record<string, unknown>;
        return { ...descriptor, id: decodeBase64Url(String(descriptor.id)) };
      }) : undefined,
    } as unknown as PublicKeyCredentialRequestOptions;
    const credential = await navigator.credentials.get({ publicKey: options }) as PublicKeyCredential | null;
    if (!credential) throw new Error("Passkey authentication was cancelled.");
    const finishResponse = await fetch(base + "/v1/admin/passkeys/authentication/finish", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ challenge_id: start.challengeId, credential: serializePasskeyCredential(credential) }),
      cache: "no-store",
    });
    const result = await finishResponse.json() as { accessToken?: string; role?: "owner"; error?: string };
    if (!finishResponse.ok || !result.accessToken) throw new Error(result.error || "Passkey authentication failed.");
    setSessionToken(result.accessToken);
    setSessionRole("owner");
    setReauthCredential("");
    return result.accessToken;
  }

  async function recoverOwnerSession() {
    setBusy(true);
    try {
      const response = await fetch(endpoint.replace(/\/$/, "") + "/v1/admin/passkeys/recovery", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code: recoveryCodeInput.trim() }),
        cache: "no-store",
      });
      const result = await response.json() as { accessToken?: string; role?: "owner"; error?: string };
      if (!response.ok || !result.accessToken) throw new Error(result.error || "Owner recovery failed.");
      setSessionToken(result.accessToken);
      setSessionRole("owner");
      setReauthCredential("");
      setRecoveryCodeInput("");
      setMessage("Owner recovery succeeded. Enroll a replacement passkey now; the recovery code has been consumed.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Owner recovery failed.");
    } finally { setBusy(false); }
  }

  async function enrollOwnerPasskey() {
    setBusy(true);
    try {
      if (!window.PublicKeyCredential || !navigator.credentials) throw new Error("This browser does not support WebAuthn passkeys.");
      const start = await request<{ challengeId: string; publicKey: Record<string, unknown> }>(
        "/v1/admin/passkeys/registration/start",
        { method: "POST", body: JSON.stringify({ display_name: passkeyDisplayName.trim() }) },
      );
      const raw = start.publicKey;
      const options = {
        ...raw,
        challenge: decodeBase64Url(String(raw.challenge)),
        user: {
          ...(raw.user as Record<string, unknown>),
          id: decodeBase64Url(String((raw.user as Record<string, unknown>).id)),
        },
        excludeCredentials: Array.isArray(raw.excludeCredentials) ? raw.excludeCredentials.map(item => {
          const descriptor = item as Record<string, unknown>;
          return { ...descriptor, id: decodeBase64Url(String(descriptor.id)) };
        }) : undefined,
      } as unknown as PublicKeyCredentialCreationOptions;
      const credential = await navigator.credentials.create({ publicKey: options }) as PublicKeyCredential | null;
      if (!credential) throw new Error("Passkey registration was cancelled.");
      const response = await fetch(endpoint.replace(/\/$/, "") + "/v1/admin/passkeys/registration/finish", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: "Bearer " + sessionToken },
        body: JSON.stringify({
          challenge_id: start.challengeId,
          credential: serializePasskeyCredential(credential),
          display_name: passkeyDisplayName.trim(),
        }),
        cache: "no-store",
      });
      const result = await response.json() as { error?: string; recoveryCodes?: string[] };
      if (!response.ok) throw new Error(result.error || "Passkey registration failed.");
      if (result.recoveryCodes?.length) setRecoveryCodes(result.recoveryCodes);
      await authenticateOwnerPasskey();
      setMessage("Owner passkey enrolled. Bootstrap sessions have been revoked; future Owner sign-ins use the passkey.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Passkey enrollment failed.");
    } finally { setBusy(false); }
  }

  async function request<T>(path: string, init: RequestInit = {}, bearerToken = sessionToken): Promise<T> {
    if (!bearerToken) throw new Error("Create an administrative session before using the Control Plane.");
    const send = (token: string) => fetch(endpoint.replace(/\/$/, "") + path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        ...init.headers,
      },
      cache: "no-store",
    });
    let response = await send(bearerToken);
    if (response.status === 428 && sessionRole === "owner") {
      // Prefer passkey verification. Before the first passkey is enrolled, a fresh
      // bootstrap exchange remains the supported initial Owner recovery path.
      let refreshed: string;
      try {
        refreshed = await authenticateOwnerPasskey();
      } catch (passkeyError) {
        if (!reauthCredential) throw passkeyError;
        refreshed = await exchangeAdminSession(reauthCredential);
      }
      response = await send(refreshed);
    } else if ((response.status === 428 || response.status === 401) && reauthCredential) {
      const refreshed = await exchangeAdminSession(reauthCredential);
      response = await send(refreshed);
    }
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
      if (!adminToken) throw new Error("Enter the long-lived administrative credential to connect.");
      const nextSession = await exchangeAdminSession(adminToken);
      setReauthCredential(adminToken);
      setAdminToken("");
      localStorage.setItem("pockethost_control_endpoint", endpoint);
      const next = await request<Device[]>("/v1/admin/devices", {}, nextSession);
      setDevices(next);
      setAuditEntries(await request<AuditEntry[]>("/v1/admin/audit", {}, nextSession));
      setMessage(`Connected with a 12-hour session; write actions reauthenticate every five minutes. ${next.length} registered device(s).`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not connect to the Control Plane.");
    } finally { setBusy(false); }
  }

  async function disconnect() {
    setBusy(true);
    try {
      await request<void>("/v1/admin/session", { method: "DELETE" });
      setSessionToken("");
      setReauthCredential("");
      setSessionRole("");
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

  async function rotateDeviceToken(device: Device) {
    if (!window.confirm("Rotate " + device.name + "'s device token? Its current token will stop working immediately. You must replace it in the target host's secure enrollment settings.")) return;
    setBusy(true);
    setIssuedToken("");
    try {
      const result = await request<{ device_token: string }>(
        "/v1/admin/devices/" + encodeURIComponent(device.id) + "/rotate-token",
        { method: "POST" },
      );
      setIssuedToken(result.device_token);
      setMessage("Token rotated for " + device.name + ". Copy the replacement and update the phone now; the old token is revoked.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Device token rotation failed.");
    } finally { setBusy(false); }
  }

  async function rotateRelayToken(device: Device) {
    if (sessionRole !== "owner") throw new Error("Only the owner can issue Relay host credentials.");
    if (!window.confirm("Issue a dedicated website-ingress credential for " + device.name + "? Any previous Relay credential for this host will stop working immediately.")) return;
    setBusy(true);
    setIssuedRelayToken("");
    try {
      const result = await request<{ relay_token: string }>(
        "/v1/admin/devices/" + encodeURIComponent(device.id) + "/relay-token",
        { method: "POST" },
      );
      setIssuedRelayToken(result.relay_token);
      setSelected(device.id);
      setMessage("Dedicated Relay credential issued. Copy it into Pocket Host Settings > Network > AssayFlux Relay. It is separate from the fleet device token.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Relay credential issuance failed.");
    } finally { setBusy(false); }
  }

  async function loadRelaySites() {
    if (!selected) throw new Error("Select a device first.");
    const next = await request<RelaySite[]>(`/v1/admin/devices/${selected}/relay-sites`);
    setRelaySites(next);
    setMessage(`Relay hostname inventory loaded · ${next.length} route(s).`);
  }

  async function createRelaySite() {
    if (!selected) throw new Error("Select a device first.");
    setBusy(true);
    setRelayChallenge(null);
    try {
      const result = await request<{
        id: string; hostname: string; verification: { name: string; value: string };
      }>(`/v1/admin/devices/${selected}/relay-sites`, {
        method: "POST",
        body: JSON.stringify({ hostname: relayHostname.trim().toLowerCase(), site_id: relaySiteId.trim() }),
      });
      setRelayChallenge({ hostname: result.hostname, name: result.verification.name, value: result.verification.value });
      setRelayHostname("");
      await loadRelaySites();
      setMessage("Relay hostname created. Publish the DNS TXT proof, then verify it.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not create Relay hostname.");
    } finally { setBusy(false); }
  }

  async function verifyRelaySite(site: RelaySite) {
    setBusy(true);
    try {
      await request(`/v1/admin/relay-sites/${site.id}/verify`, { method: "POST", body: "{}" });
      await loadRelaySites();
      setMessage(`DNS ownership verified for ${site.hostname}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Relay hostname verification failed.");
    } finally { setBusy(false); }
  }

  async function deleteRelaySite(site: RelaySite) {
    if (!window.confirm(`Remove Relay route for ${site.hostname}?`)) return;
    setBusy(true);
    try {
      await request(`/v1/admin/relay-sites/${site.id}`, { method: "DELETE" });
      setRelaySites(current => current.filter(item => item.id !== site.id));
      if (relayChallenge?.hostname === site.hostname) setRelayChallenge(null);
      setMessage(`Relay route removed for ${site.hostname}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not remove Relay route.");
    } finally { setBusy(false); }
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
      setMessage(`Command queued: ${result.commandId}. The host agent will poll and acknowledge it.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Command could not be queued."); }
    finally { setBusy(false); }
  }

  return <section className="panel" id="remote-control">
    <div className="panel-heading"><h3>Remote Control Plane</h3><span className="muted-tag">INDEPENDENT BACKEND</span></div>
    <p>Manage enrolled Pocket Host nodes while their host agents remain active. Administrative credentials exchange for revocable 12-hour sessions; device commands currently use outbound HTTPS polling.</p>
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
    <div className="section-heading"><div><span className="eyebrow">DEVICE REGISTRY</span><h2>Registered hosts</h2></div><span className="muted-tag">{devices.length} DEVICES</span></div>
    <div className="resource-panel">
      <label>New device name<input value={deviceName} onChange={event => setDeviceName(event.target.value)} placeholder="e.g. Pocket Host primary host" /></label>
      <button disabled={busy || !sessionToken || !deviceName.trim()} onClick={registerDevice}>Register device</button>
      {issuedToken && <div className="notice"><div><strong>One-time device token</strong><p>Copy this token into Advanced Mode on the target host. It will not be shown again.</p><code>{issuedToken}</code><button onClick={() => void navigator.clipboard.writeText(issuedToken)}>Copy token</button></div></div>}
    </div>
    {devices.map(device => <div className="status-line" key={device.id}>
      <span><strong>{device.name}</strong><small>{device.id} · Last seen: {device.last_seen ? new Date(device.last_seen).toLocaleString() : "Never"}</small></span>
      <span className="site-actions"><strong>{device.last_seen && Date.now() - Date.parse(device.last_seen) < 90_000 ? "Online" : "Offline"}</strong><button disabled={busy || !sessionToken} onClick={() => void loadConfig(device.id)}>Manage</button>{sessionRole === "owner" && <button disabled={busy || !sessionToken} onClick={() => void rotateDeviceToken(device)}>Rotate token</button>}<button className="danger-button" disabled={busy || !sessionToken} onClick={() => void revokeDevice(device)}>Revoke</button></span>
    </div>)}
    {selected && <div className="lower-grid">
      <article className="panel">
        <div className="panel-heading"><h3>AssayFlux Relay hostnames</h3><span className="muted-tag">OUTBOUND DEVICE STREAM</span></div>
        <p>Issue a dedicated visitor-ingress credential for this host. It is separate from the fleet-control device token and is shown only once.</p>
        <button disabled={busy || !sessionToken || sessionRole !== "owner"} onClick={() => {
          const device = devices.find(item => item.id === selected);
          if (device) void rotateRelayToken(device);
        }}>Issue / rotate Relay host credential</button>
        {issuedRelayToken && <div className="notice">
          <strong>One-time Relay host credential</strong>
          <p>Paste this into Pocket Host Settings → Network → AssayFlux Relay. The endpoint is this Relay gateway's HTTPS URL.</p>
          <code>{issuedRelayToken}</code>
          <button onClick={() => void navigator.clipboard.writeText(issuedRelayToken)}>Copy Relay credential</button>
        </div>}
        <p>Bind a verified hostname to this device and an existing Pocket Host site ID. The selected site ID must resolve to an enabled local site with at least one configured hostname. Visitor Private or Restricted access is enforced by the selected host's site policy.</p>
        <label>Hostname<input value={relayHostname} onChange={event => setRelayHostname(event.target.value)} placeholder="www.example.com" /></label>
        <label>Local site ID<input value={relaySiteId} onChange={event => setRelaySiteId(event.target.value)} placeholder="static-default" /></label>
        <div className="site-actions">
          <button disabled={busy || !sessionToken || !relayHostname.trim() || !relaySiteId.trim()} onClick={createRelaySite}>Create DNS challenge</button>
          <button disabled={busy || !sessionToken} onClick={() => void loadRelaySites()}>Refresh Relay routes</button>
        </div>
        {relayChallenge && <div className="notice">
          <strong>DNS TXT ownership proof · {relayChallenge.hostname}</strong>
          <p>Name: <code>{relayChallenge.name}</code></p>
          <p>Value: <code>{relayChallenge.value}</code></p>
          <button onClick={() => void navigator.clipboard.writeText(`${relayChallenge.name} TXT ${relayChallenge.value}`)}>Copy DNS record</button>
          <p>Add this TXT record at your DNS provider. The route stays inactive until verification succeeds.</p>
        </div>}
        {relaySites.map(site => <div className="status-line" key={site.id}>
          <span><strong>{site.hostname}</strong><small>Site: {site.siteId} · {site.verified ? "DNS verified" : "DNS verification pending"}</small></span>
          <span className="site-actions">
            {!site.verified && <button disabled={busy || !sessionToken} onClick={() => void verifyRelaySite(site)}>Verify DNS</button>}
            <button className="danger-button" disabled={busy || !sessionToken} onClick={() => void deleteRelaySite(site)}>Remove</button>
          </span>
        </div>)}
      </article>
      <article className="panel">
        <div className="panel-heading"><h3>Desired device configuration</h3><span className="muted-tag">VERSION {configVersion ?? "—"}</span></div>
        <p>Supported keys currently include pythonMaxWorkers, nodeHeapMb and tunnelEnabled. The host validates settings before applying them.</p>
        <textarea rows={9} value={configText} onChange={event => setConfigText(event.target.value)} spellCheck={false}/>
        <button disabled={busy || !sessionToken || configVersion === null} onClick={saveConfig}>Save and queue configuration</button>
      </article>
      <article className="panel">
        <div className="panel-heading"><h3>Deploy website archive</h3><span className="muted-tag">ZIP · 50 MiB MAX</span></div>
        <p>Upload a ZIP containing index.html at its root. The host verifies SHA-256 and atomically activates the release while existing services remain online.</p>
        <label>Website ZIP<input type="file" accept=".zip,application/zip" onChange={event => setArchive(event.target.files?.[0] || null)} /></label>
        <button disabled={busy || !sessionToken || !archive} onClick={deployArchive}>Upload and deploy</button>
      </article>
      <article className="panel">
        <div className="panel-heading"><h3>Remote actions</h3><span className="muted-tag">TYPED COMMANDS</span></div>
        <label>Command<select value={commandKind} onChange={event => { setCommandKind(event.target.value); setPayloadText(initialPayloads[event.target.value] || "{}"); }}>
          <option value="site_action">Start / stop site</option>
          <option value="tunnel_action">Start / stop tunnel</option>
          <option value="restart_runtime">Restart runtime</option>
        </select></label>
        <label>JSON payload<textarea rows={6} value={payloadText} onChange={event => setPayloadText(event.target.value)} spellCheck={false}/></label>
        <button disabled={busy || !sessionToken} onClick={queueCommand}>Queue command</button>
      </article>
    </div>}
    <p className="scope-note">The backend cannot browse a host filesystem or execute shell commands. Website archives are validated on-device and activated using the native release importer.</p>
    <div className="section-heading"><div><span className="eyebrow">OWNER IDENTITY</span><h2>Passkey security</h2></div><span className="muted-tag">WEBAUTHN</span></div>
    <div className="resource-panel">
      <p>Enroll a device passkey with user verification. After the first passkey is enrolled, bootstrap Owner sessions are revoked and future Owner sign-ins use the passkey challenge.</p>
      <label>Passkey name<input value={passkeyDisplayName} onChange={event => setPasskeyDisplayName(event.target.value)} maxLength={80} /></label>
      <div className="site-actions">
        <button disabled={busy || !sessionToken || sessionRole !== "owner" || !passkeyDisplayName.trim()} onClick={() => void enrollOwnerPasskey()}>Enroll owner passkey</button>
        <button disabled={busy || !endpoint.startsWith("https://")} onClick={() => void authenticateOwnerPasskey().then(() => setMessage("Signed in with Owner passkey.")).catch(error => setMessage(error instanceof Error ? error.message : "Passkey sign-in failed."))}>Sign in with passkey</button>
      </div>
      <label>One-time recovery code<input value={recoveryCodeInput} onChange={event => setRecoveryCodeInput(event.target.value)} autoComplete="off" spellCheck={false} placeholder="Paste one 64-character recovery code" /></label>
      <button disabled={busy || !endpoint.startsWith("https://") || recoveryCodeInput.trim().length !== 64} onClick={() => void recoverOwnerSession()}>Recover Owner session</button>
      {recoveryCodes.length > 0 && <div className="notice">
        <strong>Save these one-time recovery codes offline</strong>
        <p>Each code can be used once. Enrolling a replacement passkey after recovery rotates this set.</p>
        <code>{recoveryCodes.join(" · ")}</code>
        <button onClick={() => void navigator.clipboard.writeText(recoveryCodes.join("\n"))}>Copy recovery codes</button>
      </div>}
    </div>
    <div className="section-heading"><div><span className="eyebrow">ACCESS CONTROL</span><h2>Team permissions</h2></div><span className="muted-tag">OWNER MANAGED</span></div>
    <div className="resource-panel">
      <p>Owner credentials can create revocable Editor and Viewer accounts. Editors can manage devices and deployments; Viewers can inspect device state, configuration and audit history.</p>
      <label>Account name<input value={newUserName} onChange={event => setNewUserName(event.target.value)} placeholder="Team member" /></label>
      <label>Permission role<select value={newUserRole} onChange={event => setNewUserRole(event.target.value as "editor" | "viewer")}><option value="viewer">Viewer · read only</option><option value="editor">Editor · manage devices</option></select></label>
      <div className="site-actions"><button disabled={busy || !sessionToken || !newUserName.trim()} onClick={createUser}>Create account</button><button disabled={busy || !sessionToken} onClick={loadUsers}>Load team</button></div>
      {newUserToken && <div className="notice"><strong>One-time team token</strong><code>{newUserToken}</code><button onClick={() => void navigator.clipboard.writeText(newUserToken)}>Copy token</button></div>}
      {users.map(user => <div className="status-line" key={user.id}><span><strong>{user.name}</strong><small>{user.role} · {user.active ? "Active" : "Revoked"}</small></span><span className="site-actions"><button disabled={busy || !sessionToken || !user.active} onClick={() => void rotateUserToken(user)}>Rotate token</button><button className="danger-button" disabled={busy || !sessionToken || !user.active} onClick={() => void revokeUser(user)}>Revoke</button></span></div>)}
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
