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
use base64::{engine::general_purpose::STANDARD, Engine as _};
use futures_util::StreamExt;
use jni::{objects::{JClass, JString}, sys::jstring, JNIEnv};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    net::{IpAddr, SocketAddr},
    sync::{Arc, Mutex, OnceLock, mpsc as std_mpsc},
    thread::JoinHandle,
    time::Duration,
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
    hosts: Vec<HostConfig>,
    routes: Vec<RouteConfig>,
}

fn default_bind() -> String { "127.0.0.1".into() }
fn default_port() -> u16 { 8082 }

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

#[derive(Clone, Default)]
struct RelayState(Arc<Mutex<Hub>>);

impl RelayState {
    fn from_config(config: RelayConfig) -> Result<Self, String> {
        if !(1024..=65535).contains(&config.http_port) {
            return Err("Relay HTTP port must be between 1024 and 65535".into());
        }
        let bind_ip: IpAddr = config.bind_address.parse()
            .map_err(|_| "Relay bind address must be a numeric IP address")?;
        let _ = bind_ip;
        if config.hosts.is_empty() || config.routes.is_empty() {
            return Err("At least one paired host and one verified route are required".into());
        }
        let mut hub = Hub::default();
        for host in config.hosts {
            if host.credential_sha256.len() != 64
                || !host.credential_sha256.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err("Host credential digest must be a 64-character SHA-256 hex value".into());
            }
            if hub.hosts.insert(host.device_id, Host {
                credential_sha256: host.credential_sha256.to_ascii_lowercase(),
            }).is_some() {
                return Err("Duplicate Relay host identity".into());
            }
        }
        for route in config.routes {
            let hostname = normalize_hostname(&route.hostname)?;
            if !hub.hosts.contains_key(&route.device_id) {
                return Err("Route references an unpaired host".into());
            }
            if !valid_site_id(&route.site_id) {
                return Err("Invalid Relay site ID".into());
            }
            if hub.routes.insert(hostname, Route {
                device_id: route.device_id,
                site_id: route.site_id,
            }).is_some() {
                return Err("Duplicate Relay hostname".into());
            }
        }
        Ok(Self(Arc::new(Mutex::new(hub))))
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
    if path.len() > 8192 || path.starts_with("//") || path.bytes().any(|b| b == 0 || b < 0x20) {
        return Err(StatusCode::BAD_REQUEST);
    }
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

fn router(state: RelayState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/v1/device/relay/stream", get(host_stream))
        .route("/v1/device/relay/responses/:id", post(host_response))
        .route("/*path", any(visitor))
        .layer(DefaultBodyLimit::max(12 * 1024 * 1024))
        .with_state(state)
}

struct Running {
    stop: oneshot::Sender<()>,
    thread: JoinHandle<()>,
}
static ENGINE: OnceLock<Mutex<Option<Running>>> = OnceLock::new();
static LAST_STATUS: OnceLock<Mutex<String>> = OnceLock::new();

fn engine() -> &'static Mutex<Option<Running>> { ENGINE.get_or_init(|| Mutex::new(None)) }
fn set_status(value: &str) {
    if let Ok(mut status) = LAST_STATUS.get_or_init(|| Mutex::new("STOPPED".into())).lock() {
        *status = value.into();
    }
}

fn start(config_json: &str) -> Result<(), String> {
    let config: RelayConfig = serde_json::from_str(config_json)
        .map_err(|e| format!("Invalid Relay configuration: {e}"))?;
    let bind_ip: IpAddr = config.bind_address.parse()
        .map_err(|_| "Relay bind address must be a numeric IP address")?;
    if !bind_ip.is_loopback() {
        return Err("Plain HTTP Relay is restricted to loopback until the TLS listener is configured".into());
    }
    let bind = SocketAddr::new(bind_ip, config.http_port);
    let state = RelayState::from_config(config)?;
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
            let listener = match TcpListener::bind(&bind).await {
                Ok(listener) => listener,
                Err(error) => { let _ = ready_tx.send(Err(format!("Relay listener failed: {error}"))); return; }
            };
            let _ = ready_tx.send(Ok(()));
            set_status("RUNNING");
            let server = axum::serve(listener, router(state));
            let _ = server.with_graceful_shutdown(async { let _ = stop_rx.await; }).await;
            set_status("STOPPED");
        });
    }).map_err(|e| format!("Unable to create Relay thread: {e}"))?;
    match ready_rx.recv_timeout(Duration::from_secs(10)) {
        Ok(Ok(())) => {
            *slot = Some(Running { stop: stop_tx, thread });
            Ok(())
        }
        Ok(Err(error)) => { let _ = thread.join(); Err(error) }
        Err(_) => { let _ = thread.join(); Err("Timed out starting Relay listener".into()) }
    }
}

fn stop() -> Result<(), String> {
    let running = engine().lock().map_err(|_| "Relay lifecycle lock poisoned")?.take();
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
    mut env: JNIEnv, _class: JClass, config: JString,
) -> jstring {
    let result = (|| {
        let config: String = env.get_string(&config).map_err(|e| e.to_string())?.into();
        start(&config)?;
        Ok::<_, String>("OK".into())
    })();
    to_jstring(&mut env, result.unwrap_or_else(|e| e))
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
    fn hostname_validation_rejects_ip_and_malformed_labels() {
        assert_eq!(normalize_hostname("WWW.Example.com.").unwrap(), "www.example.com");
        assert!(normalize_hostname("127.0.0.1").is_err());
        assert!(normalize_hostname("-bad.example").is_err());
        assert!(normalize_hostname("bad..example").is_err());
    }

    #[test]
    fn constant_time_digest_comparison_and_sha256_vector() {
        assert_eq!(hex_sha256(b"secret"),
            "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b");
        assert!(constant_time_equal("abc", "abc"));
        assert!(!constant_time_equal("abc", "abd"));
    }

    #[test]
    fn route_config_rejects_unpaired_hosts_and_duplicate_hostnames() {
        let device = Uuid::new_v4();
        let config = RelayConfig {
            bind_address: "127.0.0.1".into(), http_port: 8082,
            hosts: vec![HostConfig { device_id: device, credential_sha256: hex_sha256(b"secret") }],
            routes: vec![
                RouteConfig { hostname: "example.com".into(), site_id: "site".into(), device_id: device },
                RouteConfig { hostname: "EXAMPLE.COM.".into(), site_id: "site2".into(), device_id: device },
            ],
        };
        assert!(RelayState::from_config(config).is_err());
    }
}
