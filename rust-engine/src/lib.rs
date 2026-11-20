use bytes::Bytes;
use futures_util::StreamExt;
use http_body_util::{combinators::UnsyncBoxBody, BodyExt, Full, StreamBody};
use hyper::body::{Frame, Incoming};
use hyper::service::service_fn;
use hyper::{Method, Request, Response, StatusCode};
use hyper_util::rt::{TokioExecutor, TokioIo};
use hyper_util::client::legacy::{Client, connect::HttpConnector};
use hyper_util::server::conn::auto::Builder;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use percent_encoding::percent_decode_str;
use std::path::{Component, Path, PathBuf};
use std::collections::{HashMap, HashSet};
use std::net::{IpAddr, SocketAddr};
use std::sync::{mpsc, Arc, Mutex, OnceLock};
use std::sync::atomic::{AtomicU64, Ordering};
use std::thread::JoinHandle;
use std::time::{Duration, Instant};
use tokio::io::{AsyncReadExt, AsyncSeekExt, AsyncWriteExt};
use tokio::net::TcpListener;
use tokio::sync::{oneshot, OwnedSemaphorePermit, Semaphore};
use tokio::task::JoinSet;
use tokio_util::io::ReaderStream;

const MAX_FILE_BYTES: u64 = 500_000_000;
const MAX_CONNECTIONS: usize = 16;
const MAX_QUEUED_CONNECTIONS: usize = 32;
const MAX_PER_SITE_CONNECTIONS: usize = 12;
const MAX_HTTP2_STREAMS_PER_CONNECTION: u32 = 8;
const MAX_HTTP2_PENDING_RESET_STREAMS: usize = 16;
const MAX_HTTP2_LOCAL_ERROR_RESETS: usize = 64;
const MAX_HTTP_HEADER_LIST_BYTES: u32 = 16 * 1024;
const MAX_HTTP1_HEADERS: usize = 64;
const MAX_HTTP1_BUFFER_BYTES: usize = 16 * 1024;
const HTTP1_HEADER_READ_TIMEOUT: Duration = Duration::from_secs(10);
const QUEUE_WAIT: Duration = Duration::from_millis(200);
const SHUTDOWN_DRAIN: Duration = Duration::from_secs(3);
const RATE_LIMIT_BURST: f64 = 120.0;
const RATE_LIMIT_REFILL_PER_SECOND: f64 = 10.0;
const MAX_RATE_LIMIT_BUCKETS: usize = 1_024;
type ResponseBody = UnsyncBoxBody<Bytes, std::io::Error>;

#[derive(Clone)]
enum SiteTarget { Static(PathBuf), Proxy { port: u16 } }

static REQUESTS: AtomicU64 = AtomicU64::new(0);
static BYTES_SERVED: AtomicU64 = AtomicU64::new(0);
static ACTIVE_CONNECTIONS: AtomicU64 = AtomicU64::new(0);
static CAPACITY_LIMIT: AtomicU64 = AtomicU64::new(MAX_CONNECTIONS as u64);
static REJECTED_CONNECTIONS: AtomicU64 = AtomicU64::new(0);
static QUEUED_CONNECTIONS: AtomicU64 = AtomicU64::new(0);
static RATE_LIMITED_REQUESTS: AtomicU64 = AtomicU64::new(0);
static SITE_LIMIT_REJECTIONS: AtomicU64 = AtomicU64::new(0);

#[derive(Clone, Debug, Default)]
struct SiteAccess { mode: String, credentials: HashMap<String, String> }

struct RateBucket {
    tokens: f64,
    last_refill: Instant,
    last_seen: Instant,
}

impl RateBucket {
    fn new(now: Instant) -> Self {
        Self { tokens: RATE_LIMIT_BURST, last_refill: now, last_seen: now }
    }

    fn take_token(&mut self, now: Instant) -> bool {
        let elapsed = now.duration_since(self.last_refill).as_secs_f64();
        self.tokens = (self.tokens + elapsed * RATE_LIMIT_REFILL_PER_SECOND).min(RATE_LIMIT_BURST);
        self.last_refill = now;
        self.last_seen = now;
        if self.tokens >= 1.0 {
            self.tokens -= 1.0;
            true
        } else {
            false
        }
    }
}

static RATE_LIMITS: OnceLock<Mutex<HashMap<IpAddr, RateBucket>>> = OnceLock::new();

const MAX_AUTH_FAILURE_BUCKETS: usize = 4_096;
const AUTH_FAILURE_WINDOW: Duration = Duration::from_secs(60);
const AUTH_FAILURE_THRESHOLD: u8 = 8;
const AUTH_FAILURE_LOCKOUT: Duration = Duration::from_secs(60);

struct AuthFailureBucket {
    window_started: Instant,
    failures: u8,
    blocked_until: Option<Instant>,
    last_seen: Instant,
}

static AUTH_FAILURES: OnceLock<Mutex<HashMap<(IpAddr, String), AuthFailureBucket>>> = OnceLock::new();

fn auth_attempt_allowed(client_ip: IpAddr, hostname: &str) -> bool {
    let now = Instant::now();
    let buckets = AUTH_FAILURES.get_or_init(|| Mutex::new(HashMap::new()));
    let Ok(mut buckets) = buckets.lock() else { return false };
    let key = (client_ip, hostname.to_string());
    let Some(bucket) = buckets.get_mut(&key) else { return true };
    bucket.last_seen = now;
    if bucket.blocked_until.map(|until| until > now).unwrap_or(false) { return false; }
    if now.duration_since(bucket.window_started) >= AUTH_FAILURE_WINDOW {
        bucket.window_started = now;
        bucket.failures = 0;
        bucket.blocked_until = None;
    }
    true
}

fn record_auth_failure(client_ip: IpAddr, hostname: &str) {
    let now = Instant::now();
    let buckets = AUTH_FAILURES.get_or_init(|| Mutex::new(HashMap::new()));
    let Ok(mut buckets) = buckets.lock() else { return };
    let key = (client_ip, hostname.to_string());
    if !buckets.contains_key(&key) && buckets.len() >= MAX_AUTH_FAILURE_BUCKETS {
        if let Some(oldest) = buckets.iter().min_by_key(|(_, bucket)| bucket.last_seen).map(|(key, _)| key.clone()) {
            buckets.remove(&oldest);
        }
    }
    let bucket = buckets.entry(key).or_insert_with(|| AuthFailureBucket {
        window_started: now, failures: 0, blocked_until: None, last_seen: now,
    });
    if now.duration_since(bucket.window_started) >= AUTH_FAILURE_WINDOW {
        bucket.window_started = now;
        bucket.failures = 0;
        bucket.blocked_until = None;
    }
    bucket.last_seen = now;
    bucket.failures = bucket.failures.saturating_add(1);
    if bucket.failures >= AUTH_FAILURE_THRESHOLD {
        bucket.blocked_until = Some(now + AUTH_FAILURE_LOCKOUT);
    }
}

fn clear_auth_failures(client_ip: IpAddr, hostname: &str) {
    if let Some(buckets) = AUTH_FAILURES.get() {
        if let Ok(mut buckets) = buckets.lock() {
            buckets.remove(&(client_ip, hostname.to_string()));
        }
    }
}

fn allow_request_rate(client_ip: IpAddr) -> bool {
    let now = Instant::now();
    let limits = RATE_LIMITS.get_or_init(|| Mutex::new(HashMap::new()));
    let Ok(mut buckets) = limits.lock() else { return false };
    if !buckets.contains_key(&client_ip) && buckets.len() >= MAX_RATE_LIMIT_BUCKETS {
        if let Some(oldest) = buckets.iter().min_by_key(|(_, bucket)| bucket.last_seen).map(|(ip, _)| *ip) {
            buckets.remove(&oldest);
        }
    }
    buckets.entry(client_ip).or_insert_with(|| RateBucket::new(now)).take_token(now)
}

fn effective_client_ip(peer_ip: IpAddr, _forwarded_ip: Option<&str>) -> IpAddr {
    // A loopback socket is not proof that the caller is Cloudflared. Any local
    // app can forge CF-Connecting-IP, so rate-limit identity stays bound to the
    // actual socket peer until an authenticated proxy contract is configured.
    peer_ip
}

fn is_sensitive_request_path(relative: &Path) -> bool {
    let blocked_directories = [".git", ".ssh", ".aws", ".azure", ".docker", ".kube", ".pki", ".pockethost", ".gnupg", ".terraform", ".pulumi", "secrets"];
    let blocked_names = [
        ".env", ".netrc", ".npmrc", ".pypirc", ".git-credentials", ".gitconfig",
        "credentials", "credentials.json", "application_default_credentials.json", "service_account.json",
        "id_rsa", "id_dsa", "id_ed25519", "id_ecdsa", "id_ed25519_sk", "identity",
        "authorized_keys", "known_hosts", "kubeconfig", "service-account.json",
        "serviceaccount.json", "secrets.json",
    ];
    if relative.components().any(|component| !matches!(component, Component::Normal(_))) {
        return true;
    }
    let portable_path = relative.to_string_lossy().replace('\\', "/").to_ascii_lowercase();
    if portable_path == ".config/gcloud" || portable_path.starts_with(".config/gcloud/") { return true; }
    portable_path.split('/').filter(|segment| !segment.is_empty()).any(|name| {
        let extension = name.rsplit_once('.').map(|(_, ext)| ext).unwrap_or("");
        blocked_directories.contains(&name) ||
            blocked_names.contains(&name) ||
            name.starts_with(".pockethost-") ||
            name.ends_with(".pockethost-tmp") ||
            name.ends_with(".pockethost-bak") ||
            name.ends_with(".upload") ||
            name == ".env" || name.starts_with(".env.") ||
            matches!(extension, "pem" | "key" | "p12" | "pfx" | "jks" | "keystore" | "mobileprovision") ||
            ((name.starts_with("service-account-") || name.starts_with("serviceaccount-") || name.starts_with("firebase-adminsdk")) && extension == "json") ||
            name.ends_with("-service-account.json") || name.ends_with("-serviceaccount.json")
    })
}
fn try_reserve_connection(counter: &AtomicU64, limit: u64) -> bool {
    let limit = limit.clamp(1, MAX_CONNECTIONS as u64);
    loop {
        let current = counter.load(Ordering::Acquire);
        if current >= limit { return false; }
        if counter.compare_exchange_weak(current, current + 1, Ordering::AcqRel, Ordering::Relaxed).is_ok() {
            return true;
        }
    }
}

struct ActiveConnection;
impl Drop for ActiveConnection {
    fn drop(&mut self) { ACTIVE_CONNECTIONS.fetch_sub(1, Ordering::Release); }
}

struct QueuedConnection;
impl QueuedConnection {
    fn new() -> Self {
        QUEUED_CONNECTIONS.fetch_add(1, Ordering::Relaxed);
        Self
    }
}
impl Drop for QueuedConnection {
    fn drop(&mut self) { QUEUED_CONNECTIONS.fetch_sub(1, Ordering::Relaxed); }
}

async fn reject_overloaded(mut stream: tokio::net::TcpStream) {
    REJECTED_CONNECTIONS.fetch_add(1, Ordering::Relaxed);
    let _ = stream.write_all(b"HTTP/1.1 503 Service Unavailable\r\nRetry-After: 2\r\nCache-Control: no-store\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").await;
    let _ = stream.shutdown().await;
}

struct RunningEngine { stop: oneshot::Sender<()>, thread: JoinHandle<()> }
static ENGINE: OnceLock<Mutex<Option<RunningEngine>>> = OnceLock::new();
fn engine() -> &'static Mutex<Option<RunningEngine>> { ENGINE.get_or_init(|| Mutex::new(None)) }

fn site_target_key(target: &SiteTarget) -> String {
    match target {
        SiteTarget::Static(path) => format!("static:{}", path.display()),
        SiteTarget::Proxy { port } => format!("proxy:{port}"),
    }
}

fn per_site_connection_limit(site_count: usize) -> usize {
    if site_count <= 1 { return MAX_CONNECTIONS; }
    let reserved_for_other_sites = site_count.saturating_sub(1).saturating_mul(2);
    MAX_CONNECTIONS.saturating_sub(reserved_for_other_sites).clamp(2, MAX_PER_SITE_CONNECTIONS)
}

/// Tokio's semaphore is FIFO-fair: requests for a busy site wait in arrival order,
/// but only for the same bounded interval as global admission. The global socket
/// queue is capped, and each HTTP/2 socket admits at most eight concurrent streams.
async fn acquire_site_permit(
    limiter: Arc<Semaphore>,
    wait: Duration,
) -> Option<OwnedSemaphorePermit> {
    tokio::time::timeout(wait, limiter.acquire_owned())
        .await
        .ok()?
        .ok()
}

fn normalize_hostname(value: &str) -> Result<String, String> {
    let host = value.strip_suffix('.').unwrap_or(value).to_ascii_lowercase();
    if host.is_empty() || host.len() > 253 || !host.is_ascii() {
        return Err(format!("Invalid hostname: {value}"));
    }
    for label in host.split('.') {
        if label.is_empty() || label.len() > 63 ||
            label.starts_with('-') || label.ends_with('-') ||
            !label.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-') {
            return Err(format!("Invalid hostname: {value}"));
        }
    }
    Ok(host)
}

fn full_body(bytes: Bytes) -> ResponseBody {
    Full::new(bytes).map_err(|never| match never {}).boxed_unsync()
}
fn text_response(status: StatusCode, mime: &'static str, body: &'static str) -> Response<ResponseBody> {
    Response::builder().status(status).header("content-type", mime)
        .header("x-content-type-options", "nosniff")
        .body(full_body(Bytes::from_static(body.as_bytes()))).expect("response")
}
fn mime_type(path: &Path) -> &'static str {
    match path.extension().and_then(|v| v.to_str()).unwrap_or("").to_ascii_lowercase().as_str() {
        "html" | "htm" => "text/html; charset=utf-8",
        "css" => "text/css; charset=utf-8",
        "js" | "mjs" => "text/javascript; charset=utf-8",
        "json" | "webmanifest" | "map" => "application/json",
        "svg" => "image/svg+xml",
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "webp" => "image/webp",
        "gif" => "image/gif",
        "ico" => "image/x-icon",
        "txt" => "text/plain; charset=utf-8",
        "xml" => "application/xml",
        "pdf" => "application/pdf",
        "wasm" => "application/wasm",
        "mp4" | "m4v" => "video/mp4",
        "webm" => "video/webm",
        "mp3" => "audio/mpeg",
        "woff" => "font/woff",
        "woff2" => "font/woff2",
        "ttf" => "font/ttf",
        "otf" => "font/otf",
        _ => "application/octet-stream",
    }
}
fn request_relative_path(raw_path: &str) -> Result<PathBuf, StatusCode> {
    let decoded = percent_decode_str(raw_path).decode_utf8()
        .map_err(|_| StatusCode::BAD_REQUEST)?;
    if decoded.contains('\0') || decoded.contains('\\') {
        return Err(StatusCode::FORBIDDEN);
    }
    let mut relative = PathBuf::new();
    for part in Path::new(decoded.trim_start_matches('/')).components() {
        match part {
            Component::Normal(value) => relative.push(value),
            Component::CurDir => {},
            _ => return Err(StatusCode::FORBIDDEN),
        }
    }
    if relative.as_os_str().is_empty() { relative.push("index.html"); }
    Ok(relative)
}
fn parse_byte_range(value: &str, size: u64) -> Result<(u64, u64), ()> {
    if size == 0 { return Err(()); }
    let spec = value.strip_prefix("bytes=").ok_or(())?;
    if spec.contains(',') { return Err(()); } // Multi-range responses are deliberately unsupported.
    let (start, end) = spec.split_once('-').ok_or(())?;
    if start.is_empty() {
        let suffix = end.parse::<u64>().map_err(|_| ())?;
        if suffix == 0 { return Err(()); }
        let length = suffix.min(size);
        return Ok((size - length, size - 1));
    }
    let first = start.parse::<u64>().map_err(|_| ())?;
    if first >= size { return Err(()); }
    let last = if end.is_empty() { size - 1 } else {
        end.parse::<u64>().map_err(|_| ())?.min(size - 1)
    };
    if last < first { return Err(()); }
    Ok((first, last))
}


fn decode_basic_credentials(input: &str) -> Option<(String, String)> {
    let mut bytes = Vec::with_capacity(input.len() * 3 / 4);
    let mut accumulator = 0u32;
    let mut bits = 0u8;
    for byte in input.bytes() {
        let value = match byte {
            b'A'..=b'Z' => byte - b'A',
            b'a'..=b'z' => byte - b'a' + 26,
            b'0'..=b'9' => byte - b'0' + 52,
            b'+' => 62,
            b'/' => 63,
            b'=' => break,
            _ => return None,
        };
        accumulator = (accumulator << 6) | value as u32;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            bytes.push(((accumulator >> bits) & 0xff) as u8);
        }
    }
    let pair = std::str::from_utf8(&bytes).ok()?;
    let (username, password) = pair.split_once(':')?;
    if username.is_empty() || password.is_empty() { return None; }
    Some((username.to_string(), password.to_string()))
}

fn sha256_hex(input: &[u8]) -> String {
    const K: [u32; 64] = [
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2,
    ];
    let mut state = [0x6a09e667u32,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
    let bit_len = (input.len() as u64).wrapping_mul(8);
    let mut padded = input.to_vec();
    padded.push(0x80);
    while padded.len() % 64 != 56 { padded.push(0); }
    padded.extend_from_slice(&bit_len.to_be_bytes());
    for chunk in padded.chunks_exact(64) {
        let mut words = [0u32; 64];
        for (index, bytes) in chunk.chunks_exact(4).take(16).enumerate() {
            words[index] = u32::from_be_bytes(bytes.try_into().unwrap());
        }
        for index in 16..64 {
            let a = words[index-15].rotate_right(7) ^ words[index-15].rotate_right(18) ^ (words[index-15] >> 3);
            let b = words[index-2].rotate_right(17) ^ words[index-2].rotate_right(19) ^ (words[index-2] >> 10);
            words[index] = words[index-16].wrapping_add(a).wrapping_add(words[index-7]).wrapping_add(b);
        }
        let [mut a,mut b,mut c,mut d,mut e,mut f,mut g,mut h] = state;
        for index in 0..64 {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let choose = (e & f) ^ ((!e) & g);
            let t1 = h.wrapping_add(s1).wrapping_add(choose).wrapping_add(K[index]).wrapping_add(words[index]);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let majority = (a & b) ^ (a & c) ^ (b & c);
            let t2 = s0.wrapping_add(majority);
            h=g; g=f; f=e; e=d.wrapping_add(t1); d=c; c=b; b=a; a=t1.wrapping_add(t2);
        }
        for (slot, value) in state.iter_mut().zip([a,b,c,d,e,f,g,h]) {
            *slot = slot.wrapping_add(value);
        }
    }
    state.iter().map(|word| format!("{word:08x}")).collect()
}

fn constant_time_hex_eq(left: &str, right: &str) -> bool {
    if left.len() != right.len() { return false; }
    left.bytes().zip(right.bytes()).fold(0u8, |diff, (a,b)| diff | (a ^ b)) == 0
}

fn authorize_site_request(headers: &hyper::HeaderMap, policy: &SiteAccess) -> bool {
    let Some(value) = headers.get(hyper::header::AUTHORIZATION).and_then(|v| v.to_str().ok()) else { return false; };
    let Some(encoded) = value.strip_prefix("Basic ") else { return false; };
    let Some((username, password)) = decode_basic_credentials(encoded) else { return false; };
    let Some(expected_hash) = policy.credentials.get(&username) else { return false; };
    constant_time_hex_eq(&sha256_hex(password.as_bytes()), expected_hash)
}

fn strip_hop_by_hop(headers: &mut hyper::HeaderMap) {
    let mut nominated = Vec::new();
    for value in headers.get_all(hyper::header::CONNECTION).iter() {
        if let Ok(value) = value.to_str() {
            for token in value.split(',').map(str::trim).filter(|token| !token.is_empty()) {
                if let Ok(name) = hyper::header::HeaderName::from_bytes(token.as_bytes()) {
                    nominated.push(name);
                }
            }
        }
    }
    for name in nominated {
        headers.remove(name);
    }
    for name in [
        "connection", "proxy-connection", "keep-alive", "transfer-encoding",
        "trailer", "te", "upgrade", "proxy-authenticate", "proxy-authorization",
    ] {
        headers.remove(name);
    }
}

async fn proxy_request(mut req: Request<Incoming>, port: u16) -> Result<Response<ResponseBody>, std::convert::Infallible> {
    let original_host = req.headers().get("x-pockethost-original-host").cloned()
        .or_else(|| req.headers().get(hyper::header::HOST).cloned());
    let forwarded_proto = if req.headers().get("x-forwarded-proto").and_then(|v| v.to_str().ok())
        .map(|v| v.eq_ignore_ascii_case("https")).unwrap_or(false) ||
        req.headers().get("cf-visitor").and_then(|v| v.to_str().ok())
            .map(|v| v.contains("\"scheme\":\"https\"")).unwrap_or(false) { "https" } else { "http" };
    let path_and_query = req.uri().path_and_query().map(|v| v.as_str()).unwrap_or("/");
    let uri = match format!("http://127.0.0.1:{port}{path_and_query}").parse() {
        Ok(uri) => uri,
        Err(_) => return Ok(text_response(StatusCode::BAD_GATEWAY, "text/plain; charset=utf-8", "Invalid upstream URI")),
    };
    *req.uri_mut() = uri;
    let headers = req.headers_mut();
    // RFC 9110: Connection may nominate additional hop-by-hop headers.
    // Remove those dynamically as well as the standard hop-by-hop set before
    // forwarding to an application runtime.
    strip_hop_by_hop(headers);
    headers.remove("x-pockethost-original-host");
    if let Some(host) = original_host {
        headers.insert(hyper::header::HOST, host.clone());
        headers.insert("x-forwarded-host", host);
    }
    headers.insert("x-forwarded-proto", forwarded_proto.parse().unwrap());
    let mut connector = HttpConnector::new();
    connector.set_connect_timeout(Some(Duration::from_secs(5)));
    let client: Client<_, Incoming> = Client::builder(TokioExecutor::new()).build(connector);
    match tokio::time::timeout(Duration::from_secs(15), client.request(req)).await {
        Ok(Ok(upstream)) => {
            let (mut parts, body) = upstream.into_parts();
            strip_hop_by_hop(&mut parts.headers);
            if !parts.headers.contains_key("x-content-type-options") {
                parts.headers.insert("x-content-type-options", "nosniff".parse().unwrap());
            }
            let body = body.map_err(|error| std::io::Error::new(std::io::ErrorKind::Other, error)).boxed_unsync();
            Ok(Response::from_parts(parts, body))
        }
        Ok(Err(_)) => Ok(text_response(StatusCode::BAD_GATEWAY, "text/plain; charset=utf-8", "Upstream runtime is unavailable")),
        Err(_) => Ok(text_response(StatusCode::GATEWAY_TIMEOUT, "text/plain; charset=utf-8", "Upstream runtime did not respond within 15 seconds")),
    }
}

async fn safe_index_file(root: &Path) -> Result<PathBuf, StatusCode> {
    let index = root.join("index.html");
    let metadata = tokio::fs::symlink_metadata(&index).await.map_err(|_| StatusCode::NOT_FOUND)?;
    if metadata.file_type().is_symlink() || !metadata.is_file() { return Err(StatusCode::NOT_FOUND); }
    let canonical = tokio::fs::canonicalize(&index).await.map_err(|_| StatusCode::NOT_FOUND)?;
    if !canonical.starts_with(root) { return Err(StatusCode::FORBIDDEN); }
    Ok(canonical)
}

async fn resolve_static_file(root: &Path, relative: &Path) -> Result<PathBuf, StatusCode> {
    if is_sensitive_request_path(relative) { return Err(StatusCode::NOT_FOUND); }
    // Do not follow symbolic links anywhere in the requested path, even when the
    // target is an ordinary in-root asset. Importers and file management reject them.
    let mut cursor = root.to_path_buf();
    for component in relative.components() {
        let Component::Normal(name) = component else { return Err(StatusCode::FORBIDDEN); };
        cursor.push(name);
        match tokio::fs::symlink_metadata(&cursor).await {
            Ok(metadata) if metadata.file_type().is_symlink() => return Err(StatusCode::NOT_FOUND),
            Ok(_) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => break,
            Err(_) => return Err(StatusCode::NOT_FOUND),
        }
    }
    let requested = tokio::fs::canonicalize(root.join(relative)).await;
    let candidate = match requested {
        Ok(path) if path.is_file() => path,
        Ok(path) if path.is_dir() && relative.extension().is_none() => safe_index_file(root).await?,
        Err(_) if relative.extension().is_none() => {
            // History-based SPAs expect extensionless routes to load the app shell.
            // Missing asset paths with extensions remain 404s.
            safe_index_file(root).await?
        }
        Err(_) => return Err(StatusCode::NOT_FOUND),
        _ => return Err(StatusCode::NOT_FOUND),
    };
    if !candidate.starts_with(root) { return Err(StatusCode::FORBIDDEN); }
    if !candidate.is_file() { return Err(StatusCode::NOT_FOUND); }
    let resolved_relative = candidate.strip_prefix(root).map_err(|_| StatusCode::FORBIDDEN)?;
    if is_sensitive_request_path(resolved_relative) { return Err(StatusCode::NOT_FOUND); }
    Ok(candidate)
}

fn hold_site_permit(response: Response<ResponseBody>, permit: OwnedSemaphorePermit) -> Response<ResponseBody> {
    let (parts, body) = response.into_parts();
    // Keep the permit alive until the response stream is fully consumed or dropped.
    let body = body.map_frame(move |frame| {
        let _permit = &permit;
        frame
    }).boxed_unsync();
    Response::from_parts(parts, body)
}

async fn serve(
    root: PathBuf,
    host_roots: Arc<HashMap<String, SiteTarget>>,
    host_access: Arc<HashMap<String, SiteAccess>>,
    site_limits: Arc<HashMap<String, Arc<Semaphore>>>,
    mut req: Request<Incoming>,
    peer: SocketAddr,
) -> Result<Response<ResponseBody>, std::convert::Infallible> {
    let close_http1 = req.version() != hyper::Version::HTTP_2;
    let client_ip = effective_client_ip(
        peer.ip(),
        req.headers().get("cf-connecting-ip").and_then(|value| value.to_str().ok())
    );
    if !allow_request_rate(client_ip) {
        RATE_LIMITED_REQUESTS.fetch_add(1, Ordering::Relaxed);
        let mut response = text_response(StatusCode::TOO_MANY_REQUESTS, "text/plain; charset=utf-8", "Request rate limit exceeded");
        response.headers_mut().insert("retry-after", "1".parse().unwrap());
        response.headers_mut().insert("cache-control", "no-store".parse().unwrap());
        if close_http1 {
            response.headers_mut().insert(hyper::header::CONNECTION, "close".parse().unwrap());
        }
        return Ok(response);
    }

    if !peer.ip().is_loopback() {
        req.headers_mut().remove("x-pockethost-original-host");
        req.headers_mut().remove("x-forwarded-proto");
    }
    let requested_host = req.headers().get(hyper::header::HOST)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.parse::<hyper::http::uri::Authority>().ok())
        .and_then(|authority| normalize_hostname(authority.host()).ok())
        .unwrap_or_default();
    if let Some(policy) = host_access.get(&requested_host) {
        if policy.mode != "public" {
            let secure_proxy = peer.ip().is_loopback() && (
                req.headers().get("x-forwarded-proto").and_then(|v| v.to_str().ok())
                    .map(|v| v.eq_ignore_ascii_case("https")).unwrap_or(false) ||
                req.headers().get("cf-visitor").and_then(|v| v.to_str().ok())
                    .map(|v| v.contains(r#""scheme":"https""#)).unwrap_or(false)
            );
            if !secure_proxy {
                let mut response = text_response(StatusCode::FORBIDDEN, "text/plain; charset=utf-8", "Private site access requires an HTTPS proxy.");
                response.headers_mut().insert("cache-control", "no-store".parse().unwrap());
                return Ok(response);
            }
            if !auth_attempt_allowed(client_ip, &requested_host) {
                let mut response = text_response(StatusCode::TOO_MANY_REQUESTS, "text/plain; charset=utf-8", "Visitor authentication temporarily rate limited.");
                response.headers_mut().insert("retry-after", "60".parse().unwrap());
                response.headers_mut().insert("cache-control", "no-store".parse().unwrap());
                return Ok(response);
            }
            if !authorize_site_request(req.headers(), policy) {
                record_auth_failure(client_ip, &requested_host);
                let mut response = text_response(StatusCode::UNAUTHORIZED, "text/plain; charset=utf-8", "Visitor authentication required.");
                response.headers_mut().insert("www-authenticate", r#"Basic realm="AssayFlux Pocket Host", charset="UTF-8""#.parse().unwrap());
                response.headers_mut().insert("cache-control", "no-store".parse().unwrap());
                return Ok(response);
            }
            clear_auth_failures(client_ip, &requested_host);
            req.headers_mut().remove(hyper::header::AUTHORIZATION);
        }
    }
    let target = if host_roots.is_empty() {
        Some(SiteTarget::Static(root.clone()))
    } else {
        host_roots.get(&requested_host).cloned()
    };
    let Some(target) = target else {
        return serve_inner(root, host_roots, req).await;
    };
    let key = site_target_key(&target);
    let permit = match site_limits.get(&key).cloned() {
        Some(limiter) => acquire_site_permit(limiter, QUEUE_WAIT).await,
        None => None,
    };
    let Some(permit) = permit else {
        SITE_LIMIT_REJECTIONS.fetch_add(1, Ordering::Relaxed);
        let mut response = text_response(StatusCode::SERVICE_UNAVAILABLE, "text/plain; charset=utf-8", "Site concurrency limit reached");
        response.headers_mut().insert("retry-after", "1".parse().unwrap());
        response.headers_mut().insert("cache-control", "no-store".parse().unwrap());
        if close_http1 {
            response.headers_mut().insert(hyper::header::CONNECTION, "close".parse().unwrap());
        }
        return Ok(response);
    };
    let response = serve_inner(root, host_roots, req).await?;
    Ok(hold_site_permit(response, permit))
}

async fn serve_inner(root: PathBuf, host_roots: Arc<HashMap<String, SiteTarget>>, req: Request<Incoming>) -> Result<Response<ResponseBody>, std::convert::Infallible> {
    let requested_host = req.headers().get(hyper::header::HOST)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.parse::<hyper::http::uri::Authority>().ok())
        .and_then(|authority| normalize_hostname(authority.host()).ok())
        .unwrap_or_default();
    let selected_target = if host_roots.is_empty() {
        SiteTarget::Static(root)
    } else if let Some(target) = host_roots.get(&requested_host) {
        target.clone()
    } else {
        return Ok(text_response(StatusCode::NOT_FOUND, "text/plain; charset=utf-8", "Unknown host"));
    };
    if let SiteTarget::Proxy { port } = &selected_target {
        return proxy_request(req, *port).await;
    }
    REQUESTS.fetch_add(1, Ordering::Relaxed);
    let selected_root = match selected_target {
        SiteTarget::Static(path) => path,
        SiteTarget::Proxy { .. } => unreachable!(),
    };
    if req.method() != Method::GET && req.method() != Method::HEAD {
        return Ok(text_response(StatusCode::METHOD_NOT_ALLOWED, "text/plain; charset=utf-8", "Method not allowed"));
    }
    let relative = match request_relative_path(req.uri().path()) {
        Ok(path) => path,
        Err(status) => return Ok(text_response(status, "text/plain; charset=utf-8", "Invalid or forbidden path")),
    };
    let canonical = match resolve_static_file(&selected_root, &relative).await {
        Ok(path) => path,
        Err(status) if status == StatusCode::FORBIDDEN => return Ok(text_response(StatusCode::FORBIDDEN, "text/plain; charset=utf-8", "Forbidden")),
        Err(_) => return Ok(text_response(StatusCode::NOT_FOUND, "text/plain; charset=utf-8", "Not found")),
    };
    let metadata = match tokio::fs::metadata(&canonical).await {
        Ok(metadata) => metadata,
        Err(_) => return Ok(text_response(StatusCode::NOT_FOUND, "text/plain; charset=utf-8", "Not found")),
    };
    if metadata.len() > MAX_FILE_BYTES {
        return Ok(text_response(StatusCode::PAYLOAD_TOO_LARGE, "text/plain; charset=utf-8", "File exceeds the 500 MB serving limit"));
    }
    let selected = match req.headers().get(hyper::header::RANGE) {
        Some(value) => match value.to_str().ok().and_then(|v| parse_byte_range(v, metadata.len()).ok()) {
            Some(range) => Some(range),
            None => {
                return Ok(Response::builder().status(StatusCode::RANGE_NOT_SATISFIABLE)
                    .header("content-range", format!("bytes */{}", metadata.len()))
                    .header("accept-ranges", "bytes")
                    .header("content-length", "0")
                    .body(full_body(Bytes::new())).expect("range error"));
            }
        },
        None => None,
    };
    let (start, end) = selected.unwrap_or((0, metadata.len().saturating_sub(1)));
    let response_length = if metadata.len() == 0 { 0 } else { end - start + 1 };
    let status = if selected.is_some() { StatusCode::PARTIAL_CONTENT } else { StatusCode::OK };
    let body = if req.method() == Method::HEAD || response_length == 0 {
        full_body(Bytes::new())
    } else {
        let mut file = match tokio::fs::File::open(&canonical).await {
            Ok(file) => file,
            Err(_) => return Ok(text_response(StatusCode::INTERNAL_SERVER_ERROR, "text/plain; charset=utf-8", "Read error")),
        };
        if file.seek(std::io::SeekFrom::Start(start)).await.is_err() {
            return Ok(text_response(StatusCode::INTERNAL_SERVER_ERROR, "text/plain; charset=utf-8", "Read error"));
        }
        let chunks = ReaderStream::new(file.take(response_length)).map(|chunk| chunk.map(|bytes| {
            // Count bytes as the file stream yields them to Hyper, not when the
            // response is merely constructed. Aborted/short reads therefore
            // do not count the complete Content-Length in advance.
            BYTES_SERVED.fetch_add(bytes.len() as u64, Ordering::Relaxed);
            Frame::data(bytes)
        }));
        StreamBody::new(chunks).boxed_unsync()
    };
    let mut response = Response::builder().status(status)
        .header("content-type", mime_type(&canonical))
        .header("content-length", response_length)
        .header("accept-ranges", "bytes")
        .header("x-content-type-options", "nosniff")
        .header("cache-control", "no-cache");
    if selected.is_some() {
        response = response.header("content-range", format!("bytes {start}-{end}/{}", metadata.len()));
    }
    Ok(response.body(body).expect("file response"))
}

async fn accept_loop(listener: TcpListener, root: PathBuf, host_roots: Arc<HashMap<String, SiteTarget>>, host_access: Arc<HashMap<String, SiteAccess>>, site_limits: Arc<HashMap<String, Arc<Semaphore>>>, mut stop: oneshot::Receiver<()>) {
    let mut connections = JoinSet::new();
    // At most 32 accepted sockets may wait up to 200 ms for an active slot.
    // Queue permits are acquired before spawning, so task creation stays bounded.
    let queue_slots = Arc::new(Semaphore::new(MAX_QUEUED_CONNECTIONS));
    loop {
        tokio::select! {
            _ = &mut stop => {
                // Stop accepting new sockets and let active responses finish for
                // up to three seconds before force-closing lingering keep-alive work.
                let deadline = tokio::time::Instant::now() + SHUTDOWN_DRAIN;
                while !connections.is_empty() {
                    match tokio::time::timeout_at(deadline, connections.join_next()).await {
                        Ok(Some(_)) => {},
                        Ok(None) => break,
                        Err(_) => {
                            connections.abort_all();
                            while connections.join_next().await.is_some() {}
                            break;
                        }
                    }
                }
                break;
            },
            accepted = listener.accept() => {
                let Ok((stream, peer)) = accepted else { continue };
                let queue_permit = match queue_slots.clone().try_acquire_owned() {
                    Ok(permit) => permit,
                    Err(_) => {
                        reject_overloaded(stream).await;
                        continue;
                    }
                };
                let site_root = root.clone();
                let site_roots = host_roots.clone();
                let site_access = host_access.clone();
                let site_limiters = site_limits.clone();
                connections.spawn(async move {
                    // Try the active slot first. If this future is cancelled before
                    // its first poll, no active count has been reserved or leaked.
                    let immediately_acquired = try_reserve_connection(
                        &ACTIVE_CONNECTIONS,
                        CAPACITY_LIMIT.load(Ordering::Acquire)
                    );
                    let queued_guard = if immediately_acquired { None } else { Some(QueuedConnection::new()) };
                    let acquired = if immediately_acquired {
                        true
                    } else {
                        tokio::time::timeout(QUEUE_WAIT, async {
                            loop {
                                let current_limit = CAPACITY_LIMIT.load(Ordering::Acquire);
                                if try_reserve_connection(&ACTIVE_CONNECTIONS, current_limit) {
                                    break true;
                                }
                                tokio::time::sleep(Duration::from_millis(10)).await;
                            }
                        }).await.unwrap_or(false)
                    };
                    if !acquired {
                        drop(queued_guard);
                        drop(queue_permit);
                        reject_overloaded(stream).await;
                        return;
                    }
                    // No await occurs between reserving the active count and
                    // constructing its RAII guard, so cancellation cannot leak a slot.
                    let _active = ActiveConnection;
                    drop(queued_guard);
                    drop(queue_permit);
                    let service = service_fn(move |req| serve(site_root.clone(), site_roots.clone(), site_access.clone(), site_limiters.clone(), req, peer));
                    let mut builder = Builder::new(TokioExecutor::new());
                    // Bound multiplexed request fan-out independently of the
                    // TCP connection cap. Hyper's default is 200 streams/socket.
                    builder.http1()
                        .timer(hyper_util::rt::TokioTimer::new())
                        .header_read_timeout(HTTP1_HEADER_READ_TIMEOUT)
                        .max_headers(MAX_HTTP1_HEADERS)
                        .max_buf_size(MAX_HTTP1_BUFFER_BYTES);
                    builder.http2()
                        .max_concurrent_streams(MAX_HTTP2_STREAMS_PER_CONNECTION)
                        .max_pending_accept_reset_streams(MAX_HTTP2_PENDING_RESET_STREAMS)
                        .max_local_error_reset_streams(MAX_HTTP2_LOCAL_ERROR_RESETS)
                        .max_header_list_size(MAX_HTTP_HEADER_LIST_BYTES);
                    let _ = builder.serve_connection(TokioIo::new(stream), service).await;
                });
            }
            Some(_) = connections.join_next(), if !connections.is_empty() => {}
        }
    }
}

fn start_engine(root: &str, port: u16, allow_lan: bool) -> Result<(), String> {
    start_engine_with_hosts(root, port, allow_lan, HashMap::new(), HashMap::new())
}

fn start_engine_with_hosts(root: &str, port: u16, allow_lan: bool, host_paths: HashMap<String, SiteTarget>, host_access: HashMap<String, SiteAccess>) -> Result<(), String> {
    let root = std::fs::canonicalize(root).map_err(|e| format!("Invalid site directory: {e}"))?;
    if !root.is_dir() { return Err("Site path is not a directory".into()); }
    let mut canonical_hosts = HashMap::new();
    for (host, path) in host_paths {
        let normalized = normalize_hostname(&host)?;
        let target = match path {
            SiteTarget::Static(site_path) => {
                let canonical = std::fs::canonicalize(&site_path).map_err(|e| format!("Invalid root for {normalized}: {e}"))?;
                if !canonical.is_dir() { return Err(format!("Site root for {normalized} is not a directory")); }
                SiteTarget::Static(canonical)
            }
            SiteTarget::Proxy { port } => {
                if !(1024..=65535).contains(&port) { return Err(format!("Invalid upstream port for {normalized}")); }
                SiteTarget::Proxy { port }
            }
        };
        if canonical_hosts.insert(normalized.clone(), target).is_some() {
            return Err(format!("Duplicate hostname: {normalized}"));
        }
    }
    let site_keys: HashSet<String> = if canonical_hosts.is_empty() {
        [site_target_key(&SiteTarget::Static(root.clone()))].into_iter().collect()
    } else {
        canonical_hosts.values().map(site_target_key).collect()
    };
    let per_site_limit = per_site_connection_limit(site_keys.len());
    let site_limits = Arc::new(site_keys.into_iter()
        .map(|key| (key, Arc::new(Semaphore::new(per_site_limit))))
        .collect::<HashMap<_, _>>());
    let mut canonical_access = HashMap::new();
    for (host, policy) in host_access {
        let normalized = normalize_hostname(&host)?;
        if !matches!(policy.mode.as_str(), "public" | "private" | "restricted") {
            return Err(format!("Invalid visitor access mode for {normalized}"));
        }
        if policy.credentials.len() > 100 || policy.credentials.iter().any(|(name, hash)| {
            name.is_empty() || name.len() > 64 ||
            !name.bytes().all(|b| b.is_ascii_alphanumeric() || b"_.@-".contains(&b)) ||
            hash.len() != 64 || !hash.bytes().all(|b| b.is_ascii_hexdigit())
        }) { return Err(format!("Invalid visitor credentials for {normalized}")); }
        if policy.mode == "private" && policy.credentials.len() > 1 {
            return Err(format!("Private site {normalized} may have only one shared visitor credential"));
        }
        if !canonical_hosts.contains_key(&normalized) {
            return Err(format!("Visitor policy has no matching hostname route: {normalized}"));
        }
        canonical_access.insert(normalized, policy);
    }
    let canonical_hosts = Arc::new(canonical_hosts);
    let canonical_access = Arc::new(canonical_access);
    let mut state = engine().lock().map_err(|_| "Engine state unavailable".to_string())?;
    if state.is_some() { return Err("Engine already running".into()); }
    REQUESTS.store(0, Ordering::Relaxed);
    BYTES_SERVED.store(0, Ordering::Relaxed);
    ACTIVE_CONNECTIONS.store(0, Ordering::Relaxed);
    QUEUED_CONNECTIONS.store(0, Ordering::Relaxed);
    RATE_LIMITED_REQUESTS.store(0, Ordering::Relaxed);
    SITE_LIMIT_REJECTIONS.store(0, Ordering::Relaxed);
    if let Some(limits) = RATE_LIMITS.get() {
        if let Ok(mut buckets) = limits.lock() { buckets.clear(); }
    }
    REJECTED_CONNECTIONS.store(0, Ordering::Relaxed);

    // LAN exposure is opt-in from the Android dashboard. Public tunnel exposure is separate.
    let bind_host = if allow_lan { "0.0.0.0" } else { "127.0.0.1" };
    let listener = std::net::TcpListener::bind((bind_host, port))
        .map_err(|e| format!("Cannot bind port {port}: {e}"))?;
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    let (stop_tx, stop_rx) = oneshot::channel();
    let (ready_tx, ready_rx) = mpsc::sync_channel::<Result<(), String>>(1);

    let thread = std::thread::Builder::new().name("pockethost-engine".into()).spawn(move || {
        let runtime = match tokio::runtime::Builder::new_multi_thread().worker_threads(2).enable_all().build() {
            Ok(runtime) => runtime,
            Err(error) => {
                let _ = ready_tx.send(Err(format!("Cannot create async runtime: {error}")));
                return;
            }
        };
        // Tokio I/O resources must be created while the runtime is entered.
        // Constructing TcpListener::from_std outside an entered runtime panics
        // on Android (and can terminate the native engine thread before JNI
        // receives its readiness signal).
        let listener = match runtime.block_on(async { TcpListener::from_std(listener) }) {
            Ok(listener) => listener,
            Err(error) => {
                let _ = ready_tx.send(Err(format!("Cannot initialize listener: {error}")));
                return;
            }
        };
        if ready_tx.send(Ok(())).is_err() { return; }
        runtime.block_on(accept_loop(listener, root, canonical_hosts, canonical_access, site_limits, stop_rx));
    }).map_err(|e| format!("Cannot start engine thread: {e}"))?;

    match ready_rx.recv() {
        Ok(Ok(())) => {
            *state = Some(RunningEngine { stop: stop_tx, thread });
            Ok(())
        }
        Ok(Err(error)) => {
            let _ = thread.join();
            Err(error)
        }
        Err(_) => {
            let _ = thread.join();
            Err("Engine thread exited before startup completed".into())
        }
    }
}
fn stop_engine() -> Result<(), String> {
    let running = engine().lock().map_err(|_| "Engine state unavailable".to_string())?.take();
    if let Some(running) = running {
        let _ = running.stop.send(());
        running.thread.join().map_err(|_| "Engine thread panicked".to_string())?;
    }
    Ok(())
}
fn to_jstring(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_start(mut env: JNIEnv, _class: JClass, root: JString, port: jint, allow_lan: jboolean) -> jstring {
    let result = (|| {
        let root: String = env.get_string(&root).map_err(|e| e.to_string())?.into();
        if !(1024..=65535).contains(&port) { return Err("Port must be 1024–65535".to_string()); }
        start_engine(&root, port as u16, allow_lan == 1)?;
        Ok("OK".to_string())
    })();
    to_jstring(&mut env, result.unwrap_or_else(|e| e))
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_startMultiSite(mut env: JNIEnv, _class: JClass, root: JString, sites_json: JString, port: jint, allow_lan: jboolean) -> jstring {
    let result = (|| {
        let root: String = env.get_string(&root).map_err(|e| e.to_string())?.into();
        let json: String = env.get_string(&sites_json).map_err(|e| e.to_string())?.into();
        if !(1024..=65535).contains(&port) { return Err("Port must be 1024–65535".to_string()); }
        let raw: HashMap<String, serde_json::Value> = serde_json::from_str(&json).map_err(|e| format!("Invalid hostname registry: {e}"))?;
        let mut sites = HashMap::new();
        let mut access = HashMap::new();
        for (host, value) in raw {
            let target = if let Some(path) = value.as_str() {
                SiteTarget::Static(PathBuf::from(path))
            } else {
                match value.get("runtime").and_then(|v| v.as_str()).unwrap_or("static") {
                    "static" => SiteTarget::Static(PathBuf::from(value.get("root").and_then(|v| v.as_str()).ok_or("Static route requires root")?)),
                    "python-wsgi" | "node" => {
                        let raw_port = value.get("port").and_then(|v| v.as_u64()).ok_or("Proxy route requires port")?;
                        if !(1024..=65535).contains(&raw_port) {
                            return Err(format!("Invalid upstream port for {host}"));
                        }
                        SiteTarget::Proxy { port: raw_port as u16 }
                    },
                    runtime => return Err(format!("Unsupported runtime route: {runtime}")),
                }
            };
            if let Some(policy) = value.get("access") {
                let mode = policy.get("mode").and_then(|v| v.as_str()).unwrap_or("public").to_string();
                let credentials = policy.get("credentials").and_then(|v| v.as_object()).map(|values| {
                    values.iter().filter_map(|(name, value)| value.as_str().map(|hash| (name.clone(), hash.to_string()))).collect::<HashMap<_,_>>()
                }).unwrap_or_default();
                access.insert(host.clone(), SiteAccess { mode, credentials });
            }
            sites.insert(host, target);
        }
        start_engine_with_hosts(&root, port as u16, allow_lan == 1, sites, access)?;
        Ok("OK".to_string())
    })();
    to_jstring(&mut env, result.unwrap_or_else(|e| e))
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_stop(mut env: JNIEnv, _class: JClass) -> jstring {
    to_jstring(&mut env, stop_engine().map(|_| "OK".to_string()).unwrap_or_else(|e| e))
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_status(mut env: JNIEnv, _class: JClass) -> jstring {
    let running = engine().lock().map(|s| s.is_some()).unwrap_or(false);
    to_jstring(&mut env, if running { "RUNNING".into() } else { "STOPPED".into() })
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_requestCount(_env: JNIEnv, _class: JClass) -> jlong {
    REQUESTS.load(Ordering::Relaxed) as jlong
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_bytesServed(_env: JNIEnv, _class: JClass) -> jlong {
    BYTES_SERVED.load(Ordering::Relaxed) as jlong
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_setCapacityLimit(mut env: JNIEnv, _class: JClass, limit: jint) -> jstring {
    if !(1..=MAX_CONNECTIONS as jint).contains(&limit) {
        return to_jstring(&mut env, "Capacity limit must be between 1 and 16".to_string());
    }
    CAPACITY_LIMIT.store(limit as u64, Ordering::Relaxed);
    to_jstring(&mut env, "OK".to_string())
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_rejectedConnections(_env: JNIEnv, _class: JClass) -> jlong {
    REJECTED_CONNECTIONS.load(Ordering::Relaxed) as jlong
}
#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_activeConnections(_env: JNIEnv, _class: JClass) -> jlong {
    ACTIVE_CONNECTIONS.load(Ordering::Relaxed) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_queuedConnections(_env: JNIEnv, _class: JClass) -> jlong {
    QUEUED_CONNECTIONS.load(Ordering::Relaxed) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_rateLimitedRequests(_env: JNIEnv, _class: JClass) -> jlong {
    RATE_LIMITED_REQUESTS.load(Ordering::Relaxed) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_siteLimitRejections(_env: JNIEnv, _class: JClass) -> jlong {
    SITE_LIMIT_REJECTIONS.load(Ordering::Relaxed) as jlong
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn site_visitor_sha256_matches_standard_vectors() {
        assert_eq!(sha256_hex(b""), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assert_eq!(sha256_hex(b"secret"), "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b");
    }

    #[test]
    fn site_visitor_basic_auth_is_site_scoped_and_constant_time_compared() {
        let mut policy = SiteAccess { mode: "restricted".into(), credentials: HashMap::new() };
        policy.credentials.insert("alice".into(), sha256_hex(b"visitor-secret"));
        let mut headers = hyper::HeaderMap::new();
        headers.insert(hyper::header::AUTHORIZATION, "Basic YWxpY2U6dmlzaXRvci1zZWNyZXQ=".parse().unwrap());
        assert!(authorize_site_request(&headers, &policy));
        headers.insert(hyper::header::AUTHORIZATION, "Basic Ym9iOnZpc2l0b3Itc2VjcmV0".parse().unwrap());
        assert!(!authorize_site_request(&headers, &policy));
        headers.insert(hyper::header::AUTHORIZATION, "Bearer visitor-secret".parse().unwrap());
        assert!(!authorize_site_request(&headers, &policy));
    }

    #[test]
    fn private_site_policy_rejects_multiple_shared_credentials() {
        let mut policy = SiteAccess { mode: "private".into(), credentials: HashMap::new() };
        policy.credentials.insert("visitor".into(), sha256_hex(b"one"));
        policy.credentials.insert("second".into(), sha256_hex(b"two"));
        assert!(policy.credentials.len() > 1);
    }

    #[test]
    fn per_site_semaphore_releases_after_response_slot_is_dropped() {
        let limiter = Arc::new(Semaphore::new(1));
        let permit = limiter.clone().try_acquire_owned().unwrap();
        assert!(limiter.clone().try_acquire_owned().is_err());
        drop(permit);
        assert!(limiter.clone().try_acquire_owned().is_ok());
    }

    #[test]
    fn http_protocol_fanout_and_header_bounds_are_explicitly_bounded() {
        assert_eq!(MAX_FILE_BYTES, 500_000_000);
        assert_eq!(MAX_HTTP2_STREAMS_PER_CONNECTION, 8);
        assert_eq!(MAX_HTTP2_PENDING_RESET_STREAMS, 16);
        assert_eq!(MAX_HTTP2_LOCAL_ERROR_RESETS, 64);
        assert_eq!(MAX_HTTP_HEADER_LIST_BYTES, 16 * 1024);
        assert_eq!(MAX_HTTP1_HEADERS, 64);
        assert_eq!(MAX_HTTP1_BUFFER_BYTES, 16 * 1024);
        assert_eq!(HTTP1_HEADER_READ_TIMEOUT, Duration::from_secs(10));
    }

    #[test]
    fn per_site_limit_preserves_capacity_for_other_sites() {
        assert_eq!(per_site_connection_limit(0), MAX_CONNECTIONS);
        assert_eq!(per_site_connection_limit(1), MAX_CONNECTIONS);
        assert_eq!(per_site_connection_limit(2), 12);
        assert_eq!(per_site_connection_limit(4), 10);
        assert_eq!(per_site_connection_limit(9), 2);
        assert_eq!(per_site_connection_limit(100), 2);
    }

    #[tokio::test]
    async fn saturated_site_does_not_consume_another_sites_response_permit() {
        let busy_site = Arc::new(Semaphore::new(1));
        let other_site = Arc::new(Semaphore::new(1));
        let held = busy_site.clone().try_acquire_owned().unwrap();

        assert!(acquire_site_permit(other_site, Duration::from_millis(20)).await.is_some());
        assert!(acquire_site_permit(busy_site.clone(), Duration::from_millis(5)).await.is_none());

        drop(held);
        assert!(acquire_site_permit(busy_site, Duration::from_millis(20)).await.is_some());
    }

    #[tokio::test]
    async fn site_permit_wait_is_bounded_and_recovers_when_capacity_returns() {
        let limiter = Arc::new(Semaphore::new(0));
        assert!(acquire_site_permit(limiter.clone(), Duration::from_millis(5)).await.is_none());
        limiter.add_permits(1);
        assert!(acquire_site_permit(limiter, Duration::from_millis(50)).await.is_some());
    }

    #[test]
    fn site_target_keys_group_same_runtime_target() {
        assert_eq!(site_target_key(&SiteTarget::Proxy { port: 9000 }), "proxy:9000");
        assert_eq!(site_target_key(&SiteTarget::Static(PathBuf::from("/tmp/site"))), "static:/tmp/site");
    }

    #[test]
    fn visitor_authentication_lockout_is_scoped_to_site_and_client_ip() {
        let ip: IpAddr = "203.0.113.42".parse().unwrap();
        let site = format!("auth-throttle-{}", std::process::id());
        assert!(auth_attempt_allowed(ip, &site));
        for _ in 0..AUTH_FAILURE_THRESHOLD {
            record_auth_failure(ip, &site);
        }
        assert!(!auth_attempt_allowed(ip, &site));
        assert!(auth_attempt_allowed("203.0.113.43".parse().unwrap(), &site));
        assert!(auth_attempt_allowed(ip, "another-site.example"));
        clear_auth_failures(ip, &site);
        assert!(auth_attempt_allowed(ip, &site));
    }

    #[test]
    fn rate_bucket_enforces_burst_and_refills_over_time() {
        let start = Instant::now();
        let mut bucket = RateBucket::new(start);
        for _ in 0..(RATE_LIMIT_BURST as usize) {
            assert!(bucket.take_token(start));
        }
        assert!(!bucket.take_token(start));
        assert!(bucket.take_token(start + Duration::from_millis(100)));
    }

    #[test]
    fn forwarded_client_ip_is_not_trusted_without_authenticated_proxy_identity() {
        let forwarded = Some("203.0.113.7");
        assert_eq!(effective_client_ip("127.0.0.1".parse().unwrap(), forwarded), "127.0.0.1".parse::<IpAddr>().unwrap());
        assert_eq!(effective_client_ip("192.0.2.9".parse().unwrap(), forwarded), "192.0.2.9".parse::<IpAddr>().unwrap());
    }

    #[test]
    fn static_server_never_exposes_credential_paths() {
        for path in [
            ".aws/credentials", r".aws\credentials", ".npmrc", ".git-credentials", ".docker/config.json",
            ".kube/config", "secrets/service-account-prod.json", "tls/server.key",
            ".env.production", ".index.html.pockethost-tmp", ".index.html.pockethost-bak", ".pending.upload",
            ".index.html.pockethost-tmp",
            ".index.html.pockethost-bak",
            ".pending.upload",
            "credentials.json",
            ".config/gcloud/application_default_credentials.json",
            "service_account.json",
            "firebase-adminsdk-prod.json",
            ".azure/accessTokens.json",
            ".terraform/terraform.tfstate",
            ".pki/private/client.key",
            ".pulumi/credentials.json",
            "secrets/credentials.json",
            "identity",
            "profile.mobileprovision",
        ] {
            assert!(is_sensitive_request_path(Path::new(path)), "must block {path}");
        }
        assert!(!is_sensitive_request_path(Path::new(".well-known/acme-challenge/token")));
        assert!(!is_sensitive_request_path(Path::new("assets/app.js")));
    }

    #[test]
    fn reserves_connection_slots_atomically_and_respects_limit() {
        let active = AtomicU64::new(0);
        assert!(try_reserve_connection(&active, 2));
        assert!(try_reserve_connection(&active, 2));
        assert!(!try_reserve_connection(&active, 2));
        assert_eq!(active.load(Ordering::Relaxed), 2);
        active.fetch_sub(1, Ordering::Release);
        assert!(try_reserve_connection(&active, 2));
        assert_eq!(active.load(Ordering::Relaxed), 2);
    }

    #[test]
    fn extensionless_routes_fall_back_to_spa_index_but_missing_assets_stay_404() {
        let root = std::env::temp_dir().join(format!("pockethost-spa-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&root);
        std::fs::create_dir_all(root.join("assets")).unwrap();
        std::fs::write(root.join("index.html"), "<div id=app></div>").unwrap();
        std::fs::write(root.join("assets/app.js"), "console.log('ok')").unwrap();
        let runtime = tokio::runtime::Builder::new_multi_thread().enable_all().build().unwrap();
        runtime.block_on(async {
            let route = resolve_static_file(&root, Path::new("account/profile")).await.unwrap();
            assert_eq!(route, std::fs::canonicalize(root.join("index.html")).unwrap());
            let asset = resolve_static_file(&root, Path::new("assets/app.js")).await.unwrap();
            assert_eq!(asset, std::fs::canonicalize(root.join("assets/app.js")).unwrap());
            assert_eq!(resolve_static_file(&root, Path::new("assets/missing.js")).await.unwrap_err(), StatusCode::NOT_FOUND);
        });
        std::fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn loopback_server_routes_multiple_hostnames_to_isolated_static_roots() {
        use std::io::{Read, Write};
        use std::net::{TcpListener as StdListener, TcpStream};
        use std::time::Duration;

        let base = std::env::temp_dir().join(format!("pockethost-multisite-{}", std::process::id()));
        let site_a = base.join("site-a");
        let site_b = base.join("site-b");
        let _ = std::fs::remove_dir_all(&base);
        std::fs::create_dir_all(&site_a).unwrap();
        std::fs::create_dir_all(&site_b).unwrap();
        std::fs::write(site_a.join("index.html"), "SITE-A").unwrap();
        std::fs::write(site_a.join("only-a.txt"), "A-PRIVATE-CONTENT").unwrap();
        std::fs::write(site_b.join("index.html"), "SITE-B").unwrap();
        std::fs::write(site_b.join("only-b.txt"), "B-PRIVATE-CONTENT").unwrap();

        let probe = StdListener::bind(("127.0.0.1", 0)).unwrap();
        let port = probe.local_addr().unwrap().port();
        drop(probe);
        let routes = [
            ("a.example.test".to_string(), SiteTarget::Static(site_a.clone())),
            ("b.example.test".to_string(), SiteTarget::Static(site_b.clone())),
        ].into_iter().collect();
        start_engine_with_hosts(base.to_str().unwrap(), port, false, routes, HashMap::new()).unwrap();

        let request = |host: &str, path: &str| {
            let mut stream = TcpStream::connect(("127.0.0.1", port)).unwrap();
            stream.set_read_timeout(Some(Duration::from_secs(2))).unwrap();
            write!(stream, "GET {path} HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n\r\n").unwrap();
            let mut response = String::new();
            stream.read_to_string(&mut response).unwrap();
            response
        };

        let a_index = request("a.example.test", "/");
        let b_index = request("b.example.test", "/");
        let a_cannot_read_b = request("a.example.test", "/only-b.txt");
        assert!(a_index.contains("SITE-A"), "unexpected response: {a_index}");
        assert!(b_index.contains("SITE-B"), "unexpected response: {b_index}");
        assert!(a_cannot_read_b.starts_with("HTTP/1.1 404"), "cross-site path leaked: {a_cannot_read_b}");

        stop_engine().unwrap();
        std::fs::remove_dir_all(base).unwrap();
    }

    #[cfg(unix)]
    #[tokio::test]
    async fn static_server_blocks_sensitive_files_reached_through_in_root_symlinks() {
        let root = std::env::temp_dir().join(format!("pockethost-symlink-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&root);
        std::fs::create_dir_all(&root).unwrap();
        std::fs::write(root.join("index.html"), "ok").unwrap();
        std::fs::write(root.join(".env"), "PRIVATE=never-serve").unwrap();
        std::fs::write(root.join("visible.txt"), "visible").unwrap();
        std::os::unix::fs::symlink(".env", root.join("public-config.txt")).unwrap();
        std::os::unix::fs::symlink("visible.txt", root.join("visible-alias.txt")).unwrap();
        let result = resolve_static_file(&root, Path::new("public-config.txt")).await;
        assert_eq!(result.unwrap_err(), StatusCode::NOT_FOUND);
        let alias = resolve_static_file(&root, Path::new("visible-alias.txt")).await;
        assert_eq!(alias.unwrap_err(), StatusCode::NOT_FOUND);
        std::fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn strips_standard_and_connection_nominated_hop_by_hop_headers() {
        let mut headers = hyper::HeaderMap::new();
        headers.insert("connection", "keep-alive, x-private-hop".parse().unwrap());
        headers.insert("keep-alive", "timeout=5".parse().unwrap());
        headers.insert("x-private-hop", "must-not-cross-proxy".parse().unwrap());
        headers.insert("upgrade", "websocket".parse().unwrap());
        headers.insert("content-type", "application/json".parse().unwrap());

        strip_hop_by_hop(&mut headers);

        assert!(!headers.contains_key("connection"));
        assert!(!headers.contains_key("keep-alive"));
        assert!(!headers.contains_key("x-private-hop"));
        assert!(!headers.contains_key("upgrade"));
        assert_eq!(headers.get("content-type").unwrap(), "application/json");
    }

    #[test]
    fn normalizes_and_validates_dns_hostnames() {
        assert_eq!(normalize_hostname("Example.COM").unwrap(), "example.com");
        assert_eq!(normalize_hostname("www.example.com.").unwrap(), "www.example.com");
        assert!(normalize_hostname("").is_err());
        assert!(normalize_hostname("-bad.example").is_err());
        assert!(normalize_hostname("bad-.example").is_err());
        assert!(normalize_hostname("bad..example").is_err());
        assert!(normalize_hostname("bad/name").is_err());
        assert!(normalize_hostname("bad:8080").is_err());
        assert!(normalize_hostname("éxample.com").is_err());
    }

    #[test]
    fn parses_single_byte_ranges_and_suffix_ranges() {
        assert_eq!(parse_byte_range("bytes=0-9", 100).unwrap(), (0, 9));
        assert_eq!(parse_byte_range("bytes=10-", 100).unwrap(), (10, 99));
        assert_eq!(parse_byte_range("bytes=-10", 100).unwrap(), (90, 99));
        assert_eq!(parse_byte_range("bytes=90-200", 100).unwrap(), (90, 99));
        assert_eq!(parse_byte_range("bytes=-200", 100).unwrap(), (0, 99));
    }
    #[test]
    fn rejects_invalid_and_multiple_byte_ranges() {
        assert!(parse_byte_range("bytes=100-", 100).is_err());
        assert!(parse_byte_range("bytes=10-2", 100).is_err());
        assert!(parse_byte_range("bytes=-0", 100).is_err());
        assert!(parse_byte_range("bytes=0-1,3-4", 100).is_err());
        assert!(parse_byte_range("items=0-1", 100).is_err());
        assert!(parse_byte_range("bytes=0-", 0).is_err());
    }
    #[test]
    fn maps_common_web_mime_types() {
        assert_eq!(mime_type(Path::new("index.html")), "text/html; charset=utf-8");
        assert_eq!(mime_type(Path::new("site.css")), "text/css; charset=utf-8");
        assert_eq!(mime_type(Path::new("app.js")), "text/javascript; charset=utf-8");
        assert_eq!(mime_type(Path::new("font.woff2")), "font/woff2");
        assert_eq!(mime_type(Path::new("app.wasm")), "application/wasm");
        assert_eq!(mime_type(Path::new("video.mp4")), "video/mp4");
    }
    #[test]
    fn resolves_safe_request_paths() {
        assert_eq!(request_relative_path("/").unwrap(), PathBuf::from("index.html"));
        assert_eq!(request_relative_path("/assets/app.js").unwrap(), PathBuf::from("assets/app.js"));
    }
    #[test]
    fn rejects_traversal_and_invalid_utf8_paths() {
        assert_eq!(request_relative_path("/%2e%2e/private").unwrap_err(), StatusCode::FORBIDDEN);
        assert_eq!(request_relative_path("/%FF").unwrap_err(), StatusCode::BAD_REQUEST);
        assert_eq!(request_relative_path("/%00secret").unwrap_err(), StatusCode::FORBIDDEN);
        assert_eq!(request_relative_path("/..%5cprivate").unwrap_err(), StatusCode::FORBIDDEN);
    }
}
