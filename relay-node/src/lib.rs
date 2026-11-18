//! Native Android Relay HTTP gateway (first implementation milestone).
//!
//! The gateway is deliberately separate from the fleet control plane. It accepts
//! bounded visitor HTTP requests and forwards them over authenticated outbound
//! SSE host sessions. TLS termination, durable pairing/route storage and DNS
//! ownership are separate release-critical milestones and are not claimed here.

use axum::{
    body::{to_bytes, Body},
    extract::{DefaultBodyLimit, Path, State},
    http::{header, HeaderMap, HeaderValue, Method, Request, Response, StatusCode},
    response::sse::{Event, KeepAlive, Sse},
    routing::{any, get, post},
    Json, Router,
};
use axum_server::tls_rustls::RustlsConfig;
use axum_server::Handle;
use base64::{engine::general_purpose::STANDARD, Engine as _};
use futures_util::StreamExt;
use ring::signature::{UnparsedPublicKey, ECDSA_P256_SHA256_ASN1};
use rusqlite::{params, Connection, OptionalExtension};
use jni::{objects::{JClass, JString}, sys::jstring, JNIEnv};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    net::{IpAddr, SocketAddr, UdpSocket},
    sync::{Arc, Mutex, OnceLock, mpsc as std_mpsc},
    thread::JoinHandle,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::{net::TcpListener, sync::{mpsc, oneshot}};
use tokio_stream::wrappers::ReceiverStream;
use uuid::Uuid;

const MAX_BODY_BYTES: usize = 8 * 1024 * 1024;
const MAX_PENDING_GLOBAL: usize = 32;
const MAX_PENDING_PER_HOST: usize = 8;
const MAX_HOST_SESSIONS: usize = 64;
const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);
const ALLOWED_METHODS: &[Method] = &[Method::GET, Method::HEAD, Method::POST, Method::PUT, Method::DELETE, Method::OPTIONS];

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RelayConfig {
    #[serde(default = "default_bind")]
    bind_address: String,
    #[serde(default = "default_port")]
    http_port: u16,
    #[serde(default = "default_https_port")]
    https_port: u16,
    #[serde(default)]
    tls_certificate_pem: Option<String>,
    #[serde(default)]
    tls_private_key_pem: Option<String>,
    #[serde(default = "default_dns_resolver")]
    dns_resolver: String,
    hosts: Vec<HostConfig>,
    routes: Vec<RouteConfig>,
}

fn default_bind() -> String { "127.0.0.1".into() }
fn default_port() -> u16 { 8082 }
fn default_https_port() -> u16 { 8444 }
fn default_dns_resolver() -> String { "1.1.1.1:53".into() }

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct HostConfig {
    device_id: Uuid,
    /// SHA-256 hex digest of the high-entropy credential held by Android Keystore.
    credential_sha256: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RouteConfig {
    hostname: String,
    site_id: String,
    device_id: Uuid,
    dns_txt_name: String,
    dns_txt_value: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct RelayRequest {
    id: Uuid,
    method: String,
    path: String,
    hostname: String,
    site_id: String,
    headers: HashMap<String, String>,
    body_base64: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RelayResponse {
    id: Uuid,
    site_id: String,
    status: u16,
    #[serde(default)]
    headers: HashMap<String, String>,
    #[serde(default)]
    set_cookies: Vec<String>,
    #[serde(default)]
    body_base64: String,
}

#[derive(Clone)]
struct Host {
    credential_sha256: String,
}

#[derive(Clone)]
struct Route {
    device_id: Uuid,
    site_id: String,
}

struct Pending {
    device_id: Uuid,
    site_id: String,
    sender: oneshot::Sender<RelayResponse>,
}

#[derive(Default)]
struct Hub {
    hosts: HashMap<Uuid, Host>,
    routes: HashMap<String, Route>,
    sessions: HashMap<Uuid, (Uuid, mpsc::Sender<RelayRequest>)>,
    pending: HashMap<Uuid, Pending>,
}

#[derive(Clone)]
struct RelayState(Arc<Mutex<Hub>>, Arc<Mutex<Connection>>);

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct PairingRequest {
    invite_code: String,
    device_id: String,
    credential_token: String,
    public_key_base64: String,
    signature_base64: String,
}

#[derive(Debug, Serialize)]
struct PairingResponse {
    device_id: String,
    status: &'static str,
}

impl RelayState {
    async fn from_config(config: RelayConfig, database_path: &str) -> Result<Self, String> {
        if !(1024..=65535).contains(&config.http_port)
            || !(1024..=65535).contains(&config.https_port)
            || config.http_port == config.https_port {
            return Err("Relay HTTP and HTTPS ports must be distinct and between 1024 and 65535".into());
        }
        let bind_ip: IpAddr = config.bind_address.parse()
            .map_err(|_| "Relay bind address must be a numeric IP address")?;
        let _ = bind_ip;

        let connection = Connection::open(database_path)
            .map_err(|error| format!("Could not open durable Relay state database: {error}"))?;
        connection.busy_timeout(Duration::from_secs(5))
            .map_err(|error| format!("Could not configure Relay database timeout: {error}"))?;
        connection.execute_batch(
            "CREATE TABLE IF NOT EXISTS relay_revocations (
                device_id TEXT PRIMARY KEY NOT NULL,
                revoked_at INTEGER NOT NULL,
                reason TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS relay_host_credentials (
                device_id TEXT PRIMARY KEY NOT NULL,
                credential_sha256 TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS relay_pairing_invites (
                invite_hash TEXT PRIMARY KEY NOT NULL,
                device_id TEXT NOT NULL,
                expires_at INTEGER NOT NULL
            );"
        ).map_err(|error| format!("Could not initialize Relay state database: {error}"))?;

        // Imported engineering config seeds missing credential rows. The durable
        // revocation ledger always wins and prevents stale config from resurrecting a host.
        {
            let mut statement = connection.prepare(
                "SELECT device_id, credential_sha256 FROM relay_host_credentials"
            ).map_err(|error| format!("Could not read Relay host credentials: {error}"))?;
            let rows = statement.query_map([], |row| {
                Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?))
            }).map_err(|error| format!("Could not enumerate Relay host credentials: {error}"))?;
            let mut existing = HashMap::new();
            for row in rows {
                let (device_id, digest) = row.map_err(|error| error.to_string())?;
                existing.insert(device_id, digest);
            }
            drop(statement);
            for host in config.hosts {
                let id = host.device_id.to_string();
                if host.credential_sha256.len() != 64
                    || !host.credential_sha256.bytes().all(|b| b.is_ascii_hexdigit()) {
                    return Err("Host credential digest must be a 64-character SHA-256 hex value".into());
                }
                let revoked: bool = connection.query_row(
                    "SELECT EXISTS(SELECT 1 FROM relay_revocations WHERE device_id = ?1)",
                    params![id], |row| row.get(0)
                ).map_err(|error| format!("Could not check Relay host revocation: {error}"))?;
                if revoked {
                    return Err("Relay configuration references a previously revoked host identity".into());
                }
                if !existing.contains_key(&id) {
                    connection.execute(
                        "INSERT INTO relay_host_credentials(device_id, credential_sha256, created_at) VALUES (?1, ?2, ?3)",
                        params![id, host.credential_sha256.to_ascii_lowercase(), unix_now()]
                    ).map_err(|error| format!("Could not seed Relay host credential: {error}"))?;
                }
            }
        }

        let mut hub = Hub::default();
        {
            let mut statement = connection.prepare(
                "SELECT h.device_id, h.credential_sha256
                 FROM relay_host_credentials h
                 LEFT JOIN relay_revocations r ON r.device_id = h.device_id
                 WHERE r.device_id IS NULL"
            ).map_err(|error| format!("Could not load active Relay hosts: {error}"))?;
            let rows = statement.query_map([], |row| {
                Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?))
            }).map_err(|error| format!("Could not enumerate active Relay hosts: {error}"))?;
            for row in rows {
                let (raw_id, digest) = row.map_err(|error| error.to_string())?;
                let id = Uuid::parse_str(&raw_id)
                    .map_err(|_| "Stored Relay host identity is malformed")?;
                if digest.len() != 64 || !digest.bytes().all(|b| b.is_ascii_hexdigit()) {
                    return Err("Stored Relay host credential verifier is malformed".into());
                }
                hub.hosts.insert(id, Host { credential_sha256: digest.to_ascii_lowercase() });
            }
        }

        let dns_resolver = config.dns_resolver.clone();
        let mut dns_proofs = Vec::new();
        for route in config.routes {
            let hostname = normalize_hostname(&route.hostname)?;
            if !valid_site_id(&route.site_id) {
                return Err("Invalid Relay site ID".into());
            }
            let txt_name = normalize_dns_name(&route.dns_txt_name)?;
            if route.dns_txt_value.len() < 24 || route.dns_txt_value.len() > 255 {
                return Err("DNS TXT ownership value must be between 24 and 255 characters".into());
            }
            dns_proofs.push((txt_name, route.dns_txt_value));
            if hub.routes.insert(hostname, Route {
                device_id: route.device_id,
                site_id: route.site_id,
            }).is_some() {
                return Err("Duplicate Relay hostname".into());
            }
        }
        for (name, expected_value) in dns_proofs {
            let records = dns_txt_records(name.clone(), dns_resolver.clone()).await?;
            if !records.iter().any(|value| constant_time_equal(value, &expected_value)) {
                return Err(format!("DNS TXT ownership proof is missing for {name}"));
            }
        }
        Ok(Self(Arc::new(Mutex::new(hub)), Arc::new(Mutex::new(connection))))
    }

    fn create_pairing_invite(&self, device_id: Uuid) -> Result<String, String> {
        let hub = self.0.lock().map_err(|_| "Relay state lock poisoned")?;
        if !hub.routes.values().any(|route| route.device_id == device_id) {
            return Err("No configured hostname route references this host identity.".into());
        }
        if hub.hosts.contains_key(&device_id) {
            return Err("This host is already paired; rotate its credential instead.".into());
        }
        drop(hub);

        let invite_code = Uuid::new_v4().simple().to_string();
        let invite_hash = hex_sha256(invite_code.as_bytes());
        let expires_at = unix_now() + 120;
        let connection = self.1.lock().map_err(|_| "Relay database lock poisoned")?;
        let transaction = connection.unchecked_transaction()
            .map_err(|error| format!("Could not begin pairing invite transaction: {error}"))?;
        transaction.execute(
            "DELETE FROM relay_pairing_invites WHERE expires_at <= ?1 OR device_id = ?2",
            params![unix_now(), device_id.to_string()]
        ).map_err(|error| format!("Could not clear stale pairing invites: {error}"))?;
        transaction.execute(
            "INSERT INTO relay_pairing_invites(invite_hash, device_id, expires_at) VALUES (?1, ?2, ?3)",
            params![invite_hash, device_id.to_string(), expires_at]
        ).map_err(|error| format!("Could not persist pairing invite: {error}"))?;
        transaction.commit().map_err(|error| format!("Could not commit pairing invite: {error}"))?;
        Ok(invite_code)
    }

    fn complete_pairing(&self, request: PairingRequest) -> Result<PairingResponse, StatusCode> {
        if request.invite_code.len() != 32
            || !request.invite_code.bytes().all(|byte| byte.is_ascii_hexdigit())
            || request.credential_token.len() < 32
            || request.credential_token.len() > 256
            || request.credential_token.bytes().any(|byte| byte.is_ascii_whitespace()) {
            return Err(StatusCode::UNAUTHORIZED);
        }
        let device_id = Uuid::parse_str(&request.device_id).map_err(|_| StatusCode::BAD_REQUEST)?;
        let public_key = STANDARD.decode(&request.public_key_base64).map_err(|_| StatusCode::UNAUTHORIZED)?;
        let signature = STANDARD.decode(&request.signature_base64).map_err(|_| StatusCode::UNAUTHORIZED)?;
        if public_key.len() != 65 || public_key[0] != 0x04 || signature.len() > 128 {
            return Err(StatusCode::UNAUTHORIZED);
        }
        let proof_message = format!(
            "AssayFlux Pocket Host Relay Pairing v1:{}:{}:{}",
            request.invite_code, device_id, hex_sha256(&public_key)
        );
        UnparsedPublicKey::new(&ECDSA_P256_SHA256_ASN1, &public_key)
            .verify(proof_message.as_bytes(), &signature)
            .map_err(|_| StatusCode::UNAUTHORIZED)?;

        let invite_hash = hex_sha256(request.invite_code.as_bytes());
        let credential_sha256 = hex_sha256(request.credential_token.as_bytes());
        let connection = self.1.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        let transaction = connection.unchecked_transaction().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        let invite: Option<i64> = transaction.query_row(
            "SELECT expires_at FROM relay_pairing_invites WHERE invite_hash = ?1 AND device_id = ?2",
            params![invite_hash, device_id.to_string()],
            |row| row.get(0)
        ).optional().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        if invite.map(|expires| expires > unix_now()) != Some(true) {
            return Err(StatusCode::UNAUTHORIZED);
        }
        let route_exists = self.0.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?
            .routes.values().any(|route| route.device_id == device_id);
        if !route_exists { return Err(StatusCode::FORBIDDEN); }
        let revoked: bool = transaction.query_row(
            "SELECT EXISTS(SELECT 1 FROM relay_revocations WHERE device_id = ?1)",
            params![device_id.to_string()], |row| row.get(0)
        ).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        if revoked { return Err(StatusCode::FORBIDDEN); }
        let already_paired: bool = transaction.query_row(
            "SELECT EXISTS(SELECT 1 FROM relay_host_credentials WHERE device_id = ?1)",
            params![device_id.to_string()], |row| row.get(0)
        ).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        if already_paired { return Err(StatusCode::CONFLICT); }

        transaction.execute(
            "INSERT INTO relay_host_credentials(device_id, credential_sha256, created_at) VALUES (?1, ?2, ?3)",
            params![device_id.to_string(), credential_sha256, unix_now()]
        ).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        transaction.execute(
            "DELETE FROM relay_pairing_invites WHERE invite_hash = ?1",
            params![invite_hash]
        ).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        transaction.commit().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

        self.0.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?
            .hosts.insert(device_id, Host { credential_sha256 });
        Ok(PairingResponse { device_id: device_id.to_string(), status: "paired" })
    }

    fn authenticate(&self, token: &str) -> Option<Uuid> {
        let digest = hex_sha256(token.as_bytes());
        let hub = self.0.lock().ok()?;
        hub.hosts.iter().find_map(|(id, host)|
            constant_time_equal(&digest, &host.credential_sha256).then_some(*id))
    }

    fn connect(&self, device_id: Uuid) -> Result<(Uuid, mpsc::Receiver<RelayRequest>), StatusCode> {
        let mut hub = self.0.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        if !hub.hosts.contains_key(&device_id) { return Err(StatusCode::UNAUTHORIZED); }
        if hub.sessions.len() >= MAX_HOST_SESSIONS && !hub.sessions.contains_key(&device_id) {
            return Err(StatusCode::SERVICE_UNAVAILABLE);
        }
        let session_id = Uuid::new_v4();
        let (tx, rx) = mpsc::channel(64);
        hub.sessions.insert(device_id, (session_id, tx));
        hub.pending.retain(|_, p| p.device_id != device_id);
        Ok((session_id, rx))
    }

    fn disconnect(&self, device_id: Uuid, session_id: Uuid) {
        if let Ok(mut hub) = self.0.lock() {
            if hub.sessions.get(&device_id).map(|s| s.0) == Some(session_id) {
                hub.sessions.remove(&device_id);
                hub.pending.retain(|_, p| p.device_id != device_id);
            }
        }
    }

    async fn dispatch(&self, device_id: Uuid, request: RelayRequest) -> Result<RelayResponse, StatusCode> {
        let request_id = request.id;
        let (sender, receiver) = {
            let mut hub = self.0.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
            let sender = hub.sessions.get(&device_id).map(|s| s.1.clone())
                .ok_or(StatusCode::SERVICE_UNAVAILABLE)?;
            let global = hub.pending.len();
            let per_host = hub.pending.values().filter(|p| p.device_id == device_id).count();
            if global >= MAX_PENDING_GLOBAL || per_host >= MAX_PENDING_PER_HOST
                || hub.pending.contains_key(&request_id) {
                return Err(StatusCode::TOO_MANY_REQUESTS);
            }
            let (tx, rx) = oneshot::channel();
            hub.pending.insert(request_id, Pending {
                device_id, site_id: request.site_id.clone(), sender: tx,
            });
            (sender, rx)
        };
        if sender.send(request).await.is_err() {
            if let Ok(mut hub) = self.0.lock() { hub.pending.remove(&request_id); }
            return Err(StatusCode::SERVICE_UNAVAILABLE);
        }
        match tokio::time::timeout(REQUEST_TIMEOUT, receiver).await {
            Ok(Ok(response)) => Ok(response),
            _ => {
                if let Ok(mut hub) = self.0.lock() { hub.pending.remove(&request_id); }
                Err(StatusCode::GATEWAY_TIMEOUT)
            }
        }
    }

    fn complete(&self, device_id: Uuid, response: RelayResponse) -> bool {
        let pending = {
            let Ok(mut hub) = self.0.lock() else { return false };
            let valid = hub.pending.get(&response.id)
                .map(|p| p.device_id == device_id && p.site_id == response.site_id)
                .unwrap_or(false);
            if !valid { return false; }
            hub.pending.remove(&response.id)
        };
        pending.map(|p| p.sender.send(response).is_ok()).unwrap_or(false)
    }
}

#[derive(Clone)]
struct SessionGuard {
    state: RelayState,
    device_id: Uuid,
    session_id: Uuid,
}
impl Drop for SessionGuard {
    fn drop(&mut self) { self.state.disconnect(self.device_id, self.session_id); }
}

fn normalize_hostname(input: &str) -> Result<String, String> {
    let value = input.trim().trim_end_matches('.').to_ascii_lowercase();
    if value.is_empty() || value.len() > 253 || value.parse::<IpAddr>().is_ok()
        || value.split('.').any(|label| label.is_empty() || label.len() > 63
            || label.starts_with('-') || label.ends_with('-')
            || !label.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-')) {
        return Err("Invalid Relay hostname".into());
    }
    Ok(value)
}

fn valid_site_id(value: &str) -> bool {
    !value.is_empty() && value.len() <= 64
        && value.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
}

/// Reject paths that could escape the website namespace or collide with Relay
/// control surfaces. The visitor router is deliberately website-only.
fn validate_visitor_path(path: &str) -> Result<(), StatusCode> {
    if path.is_empty() || path.len() > 8192 || !path.starts_with('/')
        || path.starts_with("//")
        || path.bytes().any(|b| b == 0 || b < 0x20 || b == 0x7f)
        || path.contains('\\')
    {
        return Err(StatusCode::BAD_REQUEST);
    }

    // Validate escapes before forwarding. Encoded dot/separator/percent bytes are
    // rejected so neither this edge nor a downstream server can reinterpret a
    // traversal or double-encoded control path differently.
    let path_only = path.split('?').next().unwrap_or("/");
    let bytes = path_only.as_bytes();
    let mut index = 0usize;
    while index < bytes.len() {
        if bytes[index] == b'%' {
            if index + 2 >= bytes.len()
                || !bytes[index + 1].is_ascii_hexdigit()
                || !bytes[index + 2].is_ascii_hexdigit()
            {
                return Err(StatusCode::BAD_REQUEST);
            }
            let hex = |value: u8| -> u8 {
                match value {
                    b'0'..=b'9' => value - b'0',
                    b'a'..=b'f' => value - b'a' + 10,
                    b'A'..=b'F' => value - b'A' + 10,
                    _ => 0,
                }
            };
            let decoded = (hex(bytes[index + 1]) << 4) | hex(bytes[index + 2]);
            if matches!(decoded, b'.' | b'/' | b'\\' | b'%' | 0..=0x1f | 0x7f) {
                return Err(StatusCode::NOT_FOUND);
            }
            index += 3;
        } else {
            index += 1;
        }
    }

    let lower = path_only.to_ascii_lowercase();
    if path_only.contains("//")
        || lower == "/v1" || lower.starts_with("/v1/")
        || lower == "/health"
        || lower == "/_pockethost" || lower.starts_with("/_pockethost/")
        || lower == "/.well-known/pockethost"
        || lower.starts_with("/.well-known/pockethost/")
        || path_only.split('/').any(|segment| segment == "." || segment == "..")
    {
        return Err(StatusCode::NOT_FOUND);
    }
    Ok(())
}
fn normalize_dns_name(input: &str) -> Result<String, String> {
    let value = input.trim().trim_end_matches('.').to_ascii_lowercase();
    if value.is_empty() || value.len() > 253
        || value.split('.').any(|label| label.is_empty() || label.len() > 63
            || !label.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')) {
        return Err("Invalid DNS TXT challenge name".into());
    }
    Ok(value)
}

fn read_dns_name(packet: &[u8], offset: &mut usize) -> Option<String> {
    let mut cursor = *offset;
    let mut jumped = false;
    let mut jumps = 0usize;
    let mut labels = Vec::new();
    loop {
        let length = *packet.get(cursor)?;
        if length & 0xc0 == 0xc0 {
            let low = *packet.get(cursor + 1)?;
            let pointer = (((length & 0x3f) as usize) << 8) | low as usize;
            if !jumped { *offset = cursor + 2; }
            cursor = pointer;
            jumped = true;
            jumps += 1;
            if jumps > 16 { return None; }
            continue;
        }
        if length == 0 {
            if !jumped { *offset = cursor + 1; }
            break;
        }
        if length & 0xc0 != 0 || length > 63 { return None; }
        let begin = cursor + 1;
        let finish = begin.checked_add(length as usize)?;
        let label = std::str::from_utf8(packet.get(begin..finish)?).ok()?;
        if !label.bytes().all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_') {
            return None;
        }
        labels.push(label.to_ascii_lowercase());
        cursor = finish;
        if !jumped { *offset = cursor; }
        if labels.iter().map(|label: &String| label.len() + 1).sum::<usize>() > 255 { return None; }
    }
    Some(labels.join("."))
}

fn parse_dns_txt_answers(packet: &[u8], expected_name: &str) -> Vec<String> {
    if packet.len() < 12 { return Vec::new(); }
    let flags = u16::from_be_bytes([packet[2], packet[3]]);
    if flags & 0x8000 == 0 || flags & 0x7800 != 0 || flags & 0x0200 != 0 || flags & 0x000f != 0 {
        return Vec::new();
    }
    let questions = u16::from_be_bytes([packet[4], packet[5]]) as usize;
    let answers = u16::from_be_bytes([packet[6], packet[7]]) as usize;
    if questions != 1 || answers > 256 { return Vec::new(); }
    let mut offset = 12usize;
    let Some(question_name) = read_dns_name(packet, &mut offset) else { return Vec::new(); };
    if question_name != expected_name.trim_end_matches('.').to_ascii_lowercase()
        || packet.get(offset..offset + 4).is_none()
        || u16::from_be_bytes([packet[offset], packet[offset + 1]]) != 16
        || u16::from_be_bytes([packet[offset + 2], packet[offset + 3]]) != 1 {
        return Vec::new();
    }
    offset += 4;
    let mut values = Vec::new();
    for _ in 0..answers {
        let Some(owner) = read_dns_name(packet, &mut offset) else { break };
        if packet.get(offset..offset + 10).is_none() { break; }
        let record_type = u16::from_be_bytes([packet[offset], packet[offset + 1]]);
        let record_class = u16::from_be_bytes([packet[offset + 2], packet[offset + 3]]);
        let length = u16::from_be_bytes([packet[offset + 8], packet[offset + 9]]) as usize;
        offset += 10;
        let Some(data) = packet.get(offset..offset.saturating_add(length)) else { break };
        if record_type == 16 && record_class == 1 && owner == question_name {
            let mut part = 0usize;
            let mut text = Vec::new();
            let mut valid = true;
            while part < data.len() {
                let size = data[part] as usize;
                part += 1;
                if part + size > data.len() { valid = false; break; }
                text.extend_from_slice(&data[part..part + size]);
                part += size;
            }
            if valid {
                if let Ok(value) = String::from_utf8(text) { values.push(value); }
            }
        }
        offset += length;
    }
    values
}

async fn dns_txt_records(name: String, resolver: String) -> Result<Vec<String>, String> {
    tokio::task::spawn_blocking(move || {
        let resolver: SocketAddr = resolver.parse()
            .map_err(|e| format!("Invalid DNS resolver address: {e}"))?;
        let mut query = Vec::with_capacity(512);
        let random_id = Uuid::new_v4();
        let id = u16::from_be_bytes([random_id.as_bytes()[0], random_id.as_bytes()[1]]);
        query.extend_from_slice(&id.to_be_bytes());
        query.extend_from_slice(&[0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);
        if name.len() > 253 { return Err("DNS TXT name is too long".into()); }
        for label in name.trim_end_matches('.').split('.') {
            if label.is_empty() || label.len() > 63 { return Err("Invalid DNS TXT label".into()); }
            query.push(label.len() as u8);
            query.extend_from_slice(label.as_bytes());
        }
        query.push(0);
        query.extend_from_slice(&16u16.to_be_bytes());
        query.extend_from_slice(&1u16.to_be_bytes());
        let bind_address = if resolver.is_ipv4() { "0.0.0.0:0" } else { "[::]:0" };
        let socket = UdpSocket::bind(bind_address).map_err(|e| format!("DNS socket bind failed: {e}"))?;
        socket.set_read_timeout(Some(Duration::from_secs(3))).map_err(|e| e.to_string())?;
        socket.set_write_timeout(Some(Duration::from_secs(3))).map_err(|e| e.to_string())?;
        socket.send_to(&query, resolver).map_err(|e| format!("DNS query failed: {e}"))?;
        let mut response = [0u8; 4096];
        let (length, source) = socket.recv_from(&mut response).map_err(|e| format!("DNS TXT lookup failed: {e}"))?;
        if source != resolver || length < 12
            || u16::from_be_bytes([response[0], response[1]]) != id
            || response[2] & 0x80 == 0 || response[2] & 0x02 != 0 {
            return Err("DNS TXT response failed validation".into());
        }
        Ok(parse_dns_txt_answers(&response[..length], &name))
    }).await.map_err(|e| format!("DNS TXT worker failed: {e}"))?
}

fn unix_now() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|duration| duration.as_secs() as i64).unwrap_or(0)
}

fn hex_sha256(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    digest.iter().map(|b| format!("{b:02x}")).collect()
}

fn constant_time_equal(a: &str, b: &str) -> bool {
    if a.len() != b.len() { return false; }
    let mut diff = 0u8;
    for (x, y) in a.bytes().zip(b.bytes()) { diff |= x ^ y; }
    diff == 0
}

fn bearer(headers: &HeaderMap) -> Option<&str> {
    headers.get(header::AUTHORIZATION)?.to_str().ok()?.strip_prefix("Bearer ")
}

async fn health() -> &'static str { "ok" }

async fn http_redirect(
    State(state): State<RelayState>,
    request: Request<Body>,
) -> Result<Response<Body>, StatusCode> {
    let authority = request.headers().get(header::HOST)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse::<axum::http::uri::Authority>().ok())
        .ok_or(StatusCode::BAD_REQUEST)?;
    let hostname = normalize_hostname(authority.host())
        .map_err(|_| StatusCode::MISDIRECTED_REQUEST)?;
    let configured = state.0.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?
        .routes.contains_key(&hostname);
    if !configured { return Err(StatusCode::NOT_FOUND); }
    let path = request.uri().path_and_query().map(|v| v.as_str()).unwrap_or("/");
    let location = format!("https://{hostname}{path}");
    Response::builder().status(StatusCode::PERMANENT_REDIRECT)
        .header(header::LOCATION, location)
        .body(Body::empty())
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)
}

async fn host_stream(
    State(state): State<RelayState>,
    headers: HeaderMap,
) -> Result<Sse<impl futures_util::Stream<Item = Result<Event, std::convert::Infallible>>>, StatusCode> {
    let token = bearer(&headers).ok_or(StatusCode::UNAUTHORIZED)?;
    let device_id = state.authenticate(token).ok_or(StatusCode::UNAUTHORIZED)?;
    let (session_id, receiver) = state.connect(device_id)?;
    let guard = SessionGuard { state: state.clone(), device_id, session_id };
    let stream = ReceiverStream::new(receiver).map(move |request| {
        let data = serde_json::to_string(&request).unwrap_or_else(|_| "{}".into());
        let _keep_guard_alive = &guard;
        Ok(Event::default().event("request").data(data))
    });
    Ok(Sse::new(stream).keep_alive(KeepAlive::new().interval(Duration::from_secs(15))))
}

async fn host_response(
    State(state): State<RelayState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(response): Json<RelayResponse>,
) -> Result<StatusCode, StatusCode> {
    let token = bearer(&headers).ok_or(StatusCode::UNAUTHORIZED)?;
    let device_id = state.authenticate(token).ok_or(StatusCode::UNAUTHORIZED)?;
    if response.id != id || !(200..=599).contains(&response.status) {
        return Err(StatusCode::BAD_REQUEST);
    }
    let body = STANDARD.decode(&response.body_base64).map_err(|_| StatusCode::BAD_REQUEST)?;
    if body.len() > MAX_BODY_BYTES { return Err(StatusCode::PAYLOAD_TOO_LARGE); }
    if !state.complete(device_id, response) { return Err(StatusCode::CONFLICT); }
    Ok(StatusCode::NO_CONTENT)
}

async fn visitor(
    State(state): State<RelayState>,
    request: Request<Body>,
) -> Result<Response<Body>, StatusCode> {
    if !ALLOWED_METHODS.contains(request.method()) { return Err(StatusCode::METHOD_NOT_ALLOWED); }
    let hostname = request.headers().get(header::HOST)
        .and_then(|v| v.to_str().ok()).and_then(|v| v.parse::<axum::http::uri::Authority>().ok())
        .map(|a| a.host().to_ascii_lowercase()).ok_or(StatusCode::BAD_REQUEST)?;
    let hostname = normalize_hostname(&hostname).map_err(|_| StatusCode::MISDIRECTED_REQUEST)?;
    let route = {
        let hub = state.0.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        hub.routes.get(&hostname).cloned().ok_or(StatusCode::NOT_FOUND)?
    };
    let method = request.method().as_str().to_string();
    let path = request.uri().path_and_query().map(|v| v.as_str()).unwrap_or("/").to_string();
    validate_visitor_path(&path)?;
    let is_head = request.method() == Method::HEAD;
    let mut forwarded_headers = HashMap::new();
    for name in ["accept", "accept-encoding", "content-type", "cookie", "if-modified-since",
        "if-none-match", "range", "user-agent"] {
        if let Some(value) = request.headers().get(name).and_then(|v| v.to_str().ok()) {
            if value.len() <= 8192 { forwarded_headers.insert(name.to_string(), value.to_string()); }
        }
    }
    let body = to_bytes(request.into_body(), MAX_BODY_BYTES).await
        .map_err(|_| StatusCode::PAYLOAD_TOO_LARGE)?;
    let id = Uuid::new_v4();
    let relay_request = RelayRequest {
        id, method, path, hostname, site_id: route.site_id.clone(),
        headers: forwarded_headers, body_base64: STANDARD.encode(&body),
    };
    let response = state.dispatch(route.device_id, relay_request).await?;
    let response_body = STANDARD.decode(&response.body_base64).map_err(|_| StatusCode::BAD_GATEWAY)?;
    if response_body.len() > MAX_BODY_BYTES { return Err(StatusCode::BAD_GATEWAY); }
    let status = StatusCode::from_u16(response.status).map_err(|_| StatusCode::BAD_GATEWAY)?;
    let mut output = Response::builder().status(status);
    for (name, value) in response.headers {
        if !["content-type", "cache-control", "etag", "last-modified", "expires",
            "content-encoding", "accept-ranges", "content-range", "location"].contains(&name.to_ascii_lowercase().as_str()) {
            continue;
        }
        if let (Ok(name), Ok(value)) = (
            axum::http::header::HeaderName::from_bytes(name.to_ascii_lowercase().as_bytes()),
            HeaderValue::from_str(&value),
        ) {
            output = output.header(name, value);
        }
    }
    for cookie in response.set_cookies.into_iter().take(16) {
        if cookie.len() <= 4096 {
            if let Ok(value) = HeaderValue::from_str(&cookie) {
                output = output.header(header::SET_COOKIE, value);
            }
        }
    }
    let bytes = if is_head || status == StatusCode::NO_CONTENT
        || status == StatusCode::NOT_MODIFIED { Vec::new() } else { response_body };
    output.body(Body::from(bytes)).map_err(|_| StatusCode::BAD_GATEWAY)
}

async fn pair_complete(
    State(state): State<RelayState>,
    Json(request): Json<PairingRequest>,
) -> Result<Json<PairingResponse>, StatusCode> {
    state.complete_pairing(request).map(Json)
}

fn router(state: RelayState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/v1/pair/complete", post(pair_complete).layer(DefaultBodyLimit::max(4096)))
        .route("/v1/device/relay/stream", get(host_stream))
        .route("/v1/device/relay/responses/:id", post(host_response))
        .route("/*path", any(visitor))
        .layer(DefaultBodyLimit::max(12 * 1024 * 1024))
        .with_state(state)
}

fn redirect_router(state: RelayState) -> Router {
    Router::new()
        .route("/*path", any(http_redirect))
        .with_state(state)
}

struct Running {
    stop: oneshot::Sender<()>,
    thread: JoinHandle<()>,
}
static ENGINE: OnceLock<Mutex<Option<Running>>> = OnceLock::new();
static LAST_STATUS: OnceLock<Mutex<String>> = OnceLock::new();
static LIVE_STATE: OnceLock<Mutex<Option<RelayState>>> = OnceLock::new();

fn engine() -> &'static Mutex<Option<Running>> { ENGINE.get_or_init(|| Mutex::new(None)) }
fn live_state() -> &'static Mutex<Option<RelayState>> { LIVE_STATE.get_or_init(|| Mutex::new(None)) }
fn set_status(value: &str) {
    if let Ok(mut status) = LAST_STATUS.get_or_init(|| Mutex::new("STOPPED".into())).lock() {
        *status = value.into();
    }
}

fn start(config_json: &str, database_path: &str) -> Result<(), String> {
    let mut config: RelayConfig = serde_json::from_str(config_json)
        .map_err(|e| format!("Invalid Relay configuration: {e}"))?;
    let bind_ip: IpAddr = config.bind_address.parse()
        .map_err(|_| "Relay bind address must be a numeric IP address")?;
    let tls_material = match (config.tls_certificate_pem.take(), config.tls_private_key_pem.take()) {
        (Some(cert), Some(key)) if !cert.trim().is_empty() && !key.trim().is_empty() => Some((cert, key)),
        (None, None) => None,
        _ => return Err("Both TLS certificate and private key PEM values are required".into()),
    };
    if tls_material.is_none() && !bind_ip.is_loopback() {
        return Err("Plain HTTP Relay is restricted to loopback; configure a TLS certificate and key for public binding".into());
    }
    let database_path = database_path.to_string();
    let http_bind = SocketAddr::new(bind_ip, config.http_port);
    let https_bind = SocketAddr::new(bind_ip, config.https_port);
    let mut slot = engine().lock().map_err(|_| "Relay lifecycle lock poisoned")?;
    if slot.is_some() { return Err("Relay is already running".into()); }
    let (stop_tx, stop_rx) = oneshot::channel();
    let (ready_tx, ready_rx) = std_mpsc::channel();
    let thread = std::thread::Builder::new().name("AssayFlux-Relay".into()).spawn(move || {
        let runtime = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
            Ok(runtime) => runtime,
            Err(error) => { let _ = ready_tx.send(Err(format!("Relay runtime failed: {error}"))); return; }
        };
        runtime.block_on(async move {
            let state = match RelayState::from_config(config, &database_path).await {
                Ok(state) => state,
                Err(error) => { let _ = ready_tx.send(Err(error)); return; }
            };
            let http_listener = match TcpListener::bind(http_bind).await {
                Ok(listener) => listener,
                Err(error) => { let _ = ready_tx.send(Err(format!("Relay HTTP listener failed: {error}"))); return; }
            };
            if let Some((cert_pem, key_pem)) = tls_material {
                let _ = rustls::crypto::ring::default_provider().install_default();
                let tls_config = match RustlsConfig::from_pem(cert_pem.into_bytes(), key_pem.into_bytes()).await {
                    Ok(config) => config,
                    Err(error) => { let _ = ready_tx.send(Err(format!("Relay TLS configuration failed: {error}"))); return; }
                };
                if let Ok(mut live) = live_state().lock() { *live = Some(state.clone()); }
                let _ = ready_tx.send(Ok(()));
                set_status("RUNNING");
                let handle = Handle::new();
                let task_handle = handle.clone();
                let https_state = state.clone();
                let mut https_task = tokio::spawn(async move {
                    axum_server::bind_rustls(https_bind, tls_config)
                        .handle(task_handle)
                        .serve(router(https_state).into_make_service())
                        .await
                });
                let http_server = axum::serve(http_listener, redirect_router(state))
                    .with_graceful_shutdown(async { let _ = stop_rx.await; });
                tokio::select! {
                    _ = http_server => {
                        handle.graceful_shutdown(Some(Duration::from_secs(5)));
                        let _ = https_task.await;
                    }
                    _ = &mut https_task => {}
                }
            } else {
                if let Ok(mut live) = live_state().lock() { *live = Some(state.clone()); }
                let _ = ready_tx.send(Ok(()));
                set_status("RUNNING");
                let server = axum::serve(http_listener, router(state));
                let _ = server.with_graceful_shutdown(async { let _ = stop_rx.await; }).await;
            }
            if let Ok(mut live) = live_state().lock() { *live = None; }
            set_status("STOPPED");
        });
    }).map_err(|e| format!("Unable to create Relay thread: {e}"))?;
    match ready_rx.recv_timeout(Duration::from_secs(10)) {
        Ok(Ok(())) => {
            *slot = Some(Running { stop: stop_tx, thread });
            Ok(())
        }
        Ok(Err(error)) => { let _ = thread.join(); Err(error) }
        Err(_) => { let _ = stop_tx.send(()); let _ = thread.join(); Err("Timed out starting Relay listener".into()) }
    }
}

fn stop() -> Result<(), String> {
    let running = engine().lock().map_err(|_| "Relay lifecycle lock poisoned")?.take();
    if let Ok(mut live) = live_state().lock() { *live = None; }
    if let Some(running) = running {
        let _ = running.stop.send(());
        running.thread.join().map_err(|_| "Relay thread terminated unexpectedly")?;
    }
    set_status("STOPPED");
    Ok(())
}

fn to_jstring(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_relay_NativeRelay_start(
    mut env: JNIEnv, _class: JClass, config: JString, database_path: JString,
) -> jstring {
    let result = (|| {
        let config: String = env.get_string(&config).map_err(|e| e.to_string())?.into();
        let database_path: String = env.get_string(&database_path).map_err(|e| e.to_string())?.into();
        start(&config, &database_path)?;
        Ok::<_, String>("OK".into())
    })();
    to_jstring(&mut env, result.unwrap_or_else(|e| e))
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_relay_NativeRelay_createPairingInvite(
    mut env: JNIEnv, _class: JClass, device_id: JString,
) -> jstring {
    let result = (|| {
        let device_id: String = env.get_string(&device_id).map_err(|error| error.to_string())?.into();
        let device_id = Uuid::parse_str(&device_id).map_err(|_| "Host ID must be a UUID".to_string())?;
        let state = live_state().lock().map_err(|_| "Relay state lock poisoned".to_string())?
            .clone().ok_or_else(|| "Native Relay is not running".to_string())?;
        state.create_pairing_invite(device_id)
    })();
    to_jstring(&mut env, result.unwrap_or_else(|error| format!("ERROR: {error}")))
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_relay_NativeRelay_stop(
    mut env: JNIEnv, _class: JClass,
) -> jstring {
    to_jstring(&mut env, stop().map(|_| "OK".into()).unwrap_or_else(|e| e))
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_relay_NativeRelay_status(
    mut env: JNIEnv, _class: JClass,
) -> jstring {
    let status = LAST_STATUS.get_or_init(|| Mutex::new("STOPPED".into()))
        .lock().map(|s| s.clone()).unwrap_or_else(|_| "ERROR".into());
    to_jstring(&mut env, status)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn owner_pairing_invite_is_short_lived_single_use_and_proves_host_key_possession() {
        use ring::rand::SystemRandom;
        use ring::signature::{EcdsaKeyPair, KeyPair, ECDSA_P256_SHA256_ASN1_SIGNING};

        let database_path = std::env::temp_dir()
            .join(format!("pockethost-pairing-test-{}.db", Uuid::new_v4()));
        let connection = Connection::open(&database_path).unwrap();
        connection.execute_batch(
            "CREATE TABLE relay_revocations (device_id TEXT PRIMARY KEY NOT NULL, revoked_at INTEGER NOT NULL, reason TEXT NOT NULL);
             CREATE TABLE relay_host_credentials (device_id TEXT PRIMARY KEY NOT NULL, credential_sha256 TEXT NOT NULL, created_at INTEGER NOT NULL);
             CREATE TABLE relay_pairing_invites (invite_hash TEXT PRIMARY KEY NOT NULL, device_id TEXT NOT NULL, expires_at INTEGER NOT NULL);"
        ).unwrap();

        let device_id = Uuid::new_v4();
        let mut hub = Hub::default();
        hub.routes.insert("pair.example.test".into(), Route {
            device_id,
            site_id: "site-a".into(),
        });
        let state = RelayState(Arc::new(Mutex::new(hub)), Arc::new(Mutex::new(connection)));
        let invite = state.create_pairing_invite(device_id).unwrap();

        let rng = SystemRandom::new();
        let pkcs8 = EcdsaKeyPair::generate_pkcs8(&ECDSA_P256_SHA256_ASN1_SIGNING, &rng).unwrap();
        let key_pair = EcdsaKeyPair::from_pkcs8(&ECDSA_P256_SHA256_ASN1_SIGNING, pkcs8.as_ref(), &rng).unwrap();
        let public_key = key_pair.public_key().as_ref().to_vec();
        let message = format!(
            "AssayFlux Pocket Host Relay Pairing v1:{}:{}:{}",
            invite, device_id, hex_sha256(&public_key)
        );
        let signature = key_pair.sign(&rng, message.as_bytes()).unwrap();

        let token = "A".repeat(43);
        let request = PairingRequest {
            invite_code: invite,
            device_id: device_id.to_string(),
            credential_token: token.clone(),
            public_key_base64: STANDARD.encode(&public_key),
            signature_base64: STANDARD.encode(signature.as_ref()),
        };
        let response = state.complete_pairing(request.clone()).unwrap();
        assert_eq!(response.status, "paired");
        assert_eq!(response.device_id, device_id.to_string());
        assert_eq!(state.authenticate(&token), Some(device_id));

        assert_eq!(state.complete_pairing(request).unwrap_err(), StatusCode::UNAUTHORIZED);
        let _ = std::fs::remove_file(database_path);
    }

    #[test]
    fn pairing_rejects_invalid_proof_and_revoked_host_identity() {
        use ring::rand::SystemRandom;
        use ring::signature::{EcdsaKeyPair, KeyPair, ECDSA_P256_SHA256_ASN1_SIGNING};

        let database_path = std::env::temp_dir()
            .join(format!("pockethost-pairing-negative-{}.db", Uuid::new_v4()));
        let connection = Connection::open(&database_path).unwrap();
        connection.execute_batch(
            "CREATE TABLE relay_revocations (device_id TEXT PRIMARY KEY NOT NULL, revoked_at INTEGER NOT NULL, reason TEXT NOT NULL);
             CREATE TABLE relay_host_credentials (device_id TEXT PRIMARY KEY NOT NULL, credential_sha256 TEXT NOT NULL, created_at INTEGER NOT NULL);
             CREATE TABLE relay_pairing_invites (invite_hash TEXT PRIMARY KEY NOT NULL, device_id TEXT NOT NULL, expires_at INTEGER NOT NULL);"
        ).unwrap();

        let device_id = Uuid::new_v4();
        let mut hub = Hub::default();
        hub.routes.insert("pair.example.test".into(), Route {
            device_id,
            site_id: "site-a".into(),
        });
        let state = RelayState(Arc::new(Mutex::new(hub)), Arc::new(Mutex::new(connection)));
        let invite = state.create_pairing_invite(device_id).unwrap();

        let rng = SystemRandom::new();
        let pkcs8 = EcdsaKeyPair::generate_pkcs8(&ECDSA_P256_SHA256_ASN1_SIGNING, &rng).unwrap();
        let key_pair = EcdsaKeyPair::from_pkcs8(&ECDSA_P256_SHA256_ASN1_SIGNING, pkcs8.as_ref(), &rng).unwrap();
        let public_key = key_pair.public_key().as_ref().to_vec();
        let wrong_message = b"not the Relay pairing challenge";
        let signature = key_pair.sign(&rng, wrong_message).unwrap();
        let request = PairingRequest {
            invite_code: invite,
            device_id: device_id.to_string(),
            credential_token: "B".repeat(43),
            public_key_base64: STANDARD.encode(&public_key),
            signature_base64: STANDARD.encode(signature.as_ref()),
        };
        assert_eq!(state.complete_pairing(request).unwrap_err(), StatusCode::UNAUTHORIZED);
        let _ = std::fs::remove_file(database_path);
    }

    #[test]
    fn hostname_validation_rejects_ip_and_malformed_labels() {
        assert_eq!(normalize_hostname("WWW.Example.com.").unwrap(), "www.example.com");
        assert!(normalize_hostname("127.0.0.1").is_err());
        assert!(normalize_hostname("-bad.example").is_err());
        assert!(normalize_hostname("bad..example").is_err());
    }

    #[test]
    fn visitor_paths_cannot_reach_relay_control_surfaces_or_traverse() {
        for path in ["/v1", "/v1/device/relay/stream", "/v1/owner/pair", "/_pockethost",
            "/_pockethost/admin", "/.well-known/pockethost",
            "/.well-known/pockethost/internal", "/%2e%2e/secrets", "/%2Fetc/passwd",
            "/%252e%252e/secrets", "/%252fadmin", "/bad%zz", "/folder//private",
            "/folder/../private", "/folder\\\\..\\\\private"] {
            assert!(validate_visitor_path(path).is_err(), "path should be rejected: {path}");
        }
        for path in ["/", "/index.html", "/assets/app.css?version=2", "/products/item",
            "/search?q=%2Fsafe%2Fquery"] {
            assert!(validate_visitor_path(path).is_ok(), "path should be accepted: {path}");
        }
    }
    #[test]
    fn constant_time_digest_comparison_and_sha256_vector() {
        assert_eq!(hex_sha256(b"secret"),
            "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b");
        assert!(constant_time_equal("abc", "abc"));
        assert!(!constant_time_equal("abc", "abd"));
    }

    #[test]
    fn dns_txt_parser_requires_matching_owner_and_extracts_txt_chunks() {
        let name = "_pockethost-verify.example.com";
        let value = "pockethost-verification-unit-test-random-value";
        let mut packet = vec![0x12, 0x34, 0x81, 0x80, 0, 1, 0, 1, 0, 0, 0, 0];
        for label in name.split('.') {
            packet.push(label.len() as u8);
            packet.extend_from_slice(label.as_bytes());
        }
        packet.push(0);
        packet.extend_from_slice(&[0, 16, 0, 1]);
        packet.extend_from_slice(&[0xc0, 0x0c]);
        packet.extend_from_slice(&[0, 16, 0, 1, 0, 0, 0, 60]);
        let txt_length = 1 + value.len();
        packet.extend_from_slice(&(txt_length as u16).to_be_bytes());
        packet.push(value.len() as u8);
        packet.extend_from_slice(value.as_bytes());

        assert_eq!(parse_dns_txt_answers(&packet, name), vec![value.to_string()]);
        assert!(parse_dns_txt_answers(&packet, "wrong.example.com").is_empty());
        packet[2] |= 0x02;
        assert!(parse_dns_txt_answers(&packet, name).is_empty());
    }

    #[test]
    fn dns_challenge_names_allow_underscores_but_reject_malformed_labels() {
        assert_eq!(
            normalize_dns_name("_pockethost-verify.Example.com.").unwrap(),
            "_pockethost-verify.example.com"
        );
        assert!(normalize_dns_name("_bad..example.com").is_err());
        assert!(normalize_dns_name(&"x".repeat(254)).is_err());
    }

    #[tokio::test]
    async fn route_config_rejects_unpaired_hosts_and_duplicate_hostnames() {
        let device = Uuid::new_v4();
        let config = RelayConfig {
            bind_address: "127.0.0.1".into(), http_port: 8082, https_port: 8444,
            tls_certificate_pem: None, tls_private_key_pem: None, dns_resolver: "127.0.0.1:53".into(),
            hosts: vec![HostConfig { device_id: device, credential_sha256: hex_sha256(b"secret") }],
            routes: vec![
                RouteConfig { hostname: "example.com".into(), site_id: "site".into(), device_id: device, dns_txt_name: "_pockethost-verify.example.com".into(), dns_txt_value: "pockethost-verification-token-000000000000".into() },
                RouteConfig { hostname: "EXAMPLE.COM.".into(), site_id: "site2".into(), device_id: device, dns_txt_name: "_pockethost-verify.example.com".into(), dns_txt_value: "pockethost-verification-token-000000000000".into() },
            ],
        };
        let database_path = std::env::temp_dir()
            .join(format!("pockethost-relay-test-{}.db", Uuid::new_v4()));
        assert!(RelayState::from_config(config, database_path.to_str().unwrap()).await.is_err());
        let _ = std::fs::remove_file(database_path);
    }
}
