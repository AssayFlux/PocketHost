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
use std::collections::HashMap;
use std::sync::{mpsc, Arc, Mutex, OnceLock};
use std::sync::atomic::{AtomicU64, Ordering};
use std::thread::JoinHandle;
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncSeekExt, AsyncWriteExt};
use tokio::net::TcpListener;
use tokio::sync::{oneshot, Semaphore};
use tokio::task::JoinSet;
use tokio_util::io::ReaderStream;

const MAX_FILE_BYTES: u64 = 50 * 1024 * 1024;
const MAX_CONNECTIONS: usize = 16;
const MAX_QUEUED_CONNECTIONS: usize = 32;
const QUEUE_WAIT: Duration = Duration::from_millis(200);
type ResponseBody = UnsyncBoxBody<Bytes, std::io::Error>;

#[derive(Clone)]
enum SiteTarget { Static(PathBuf), Proxy { port: u16 } }

static REQUESTS: AtomicU64 = AtomicU64::new(0);
static BYTES_SERVED: AtomicU64 = AtomicU64::new(0);
static ACTIVE_CONNECTIONS: AtomicU64 = AtomicU64::new(0);
static CAPACITY_LIMIT: AtomicU64 = AtomicU64::new(MAX_CONNECTIONS as u64);
static REJECTED_CONNECTIONS: AtomicU64 = AtomicU64::new(0);
static QUEUED_CONNECTIONS: AtomicU64 = AtomicU64::new(0);

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
    let original_host = req.headers().get(hyper::header::HOST).cloned();
    let forwarded_proto = if req.headers().get("cf-visitor").and_then(|v| v.to_str().ok())
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
    if let Some(host) = original_host {
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

async fn resolve_static_file(root: &Path, relative: &Path) -> Result<PathBuf, StatusCode> {
    let requested = tokio::fs::canonicalize(root.join(relative)).await;
    let candidate = match requested {
        Ok(path) if path.is_file() => path,
        Ok(path) if path.is_dir() && relative.extension().is_none() => {
            tokio::fs::canonicalize(root.join("index.html")).await.map_err(|_| StatusCode::NOT_FOUND)?
        }
        Err(_) if relative.extension().is_none() => {
            // History-based SPAs (React/Vue/Svelte/etc.) expect extensionless routes
            // to load the application shell. Missing asset paths with extensions remain 404s.
            tokio::fs::canonicalize(root.join("index.html")).await.map_err(|_| StatusCode::NOT_FOUND)?
        }
        Err(_) => return Err(StatusCode::NOT_FOUND),
        _ => return Err(StatusCode::NOT_FOUND),
    };
    if !candidate.starts_with(root) { return Err(StatusCode::FORBIDDEN); }
    if !candidate.is_file() { return Err(StatusCode::NOT_FOUND); }
    Ok(candidate)
}

async fn serve(root: PathBuf, host_roots: Arc<HashMap<String, SiteTarget>>, req: Request<Incoming>) -> Result<Response<ResponseBody>, std::convert::Infallible> {
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
    let selected_root = match selected_target {
        SiteTarget::Static(path) => path,
        SiteTarget::Proxy { .. } => unreachable!(),
    };
    REQUESTS.fetch_add(1, Ordering::Relaxed);
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
        return Ok(text_response(StatusCode::PAYLOAD_TOO_LARGE, "text/plain; charset=utf-8", "File exceeds the 50 MB serving limit"));
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

async fn accept_loop(listener: TcpListener, root: PathBuf, host_roots: Arc<HashMap<String, SiteTarget>>, mut stop: oneshot::Receiver<()>) {
    let mut connections = JoinSet::new();
    // At most 32 accepted sockets may wait up to 200 ms for an active slot.
    // Queue permits are acquired before spawning, so task creation stays bounded.
    let queue_slots = Arc::new(Semaphore::new(MAX_QUEUED_CONNECTIONS));
    loop {
        tokio::select! {
            _ = &mut stop => {
                connections.abort_all();
                while connections.join_next().await.is_some() {}
                break;
            },
            accepted = listener.accept() => {
                let Ok((stream, _peer)) = accepted else { continue };
                let queue_permit = match queue_slots.clone().try_acquire_owned() {
                    Ok(permit) => permit,
                    Err(_) => {
                        reject_overloaded(stream).await;
                        continue;
                    }
                };
                let site_root = root.clone();
                let site_roots = host_roots.clone();
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
                    let service = service_fn(move |req| serve(site_root.clone(), site_roots.clone(), req));
                    let builder = Builder::new(TokioExecutor::new());
                    let _ = builder.serve_connection(TokioIo::new(stream), service).await;
                });
            }
            Some(_) = connections.join_next(), if !connections.is_empty() => {}
        }
    }
}

fn start_engine(root: &str, port: u16, allow_lan: bool) -> Result<(), String> {
    start_engine_with_hosts(root, port, allow_lan, HashMap::new())
}

fn start_engine_with_hosts(root: &str, port: u16, allow_lan: bool, host_paths: HashMap<String, SiteTarget>) -> Result<(), String> {
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
    let canonical_hosts = Arc::new(canonical_hosts);
    let mut state = engine().lock().map_err(|_| "Engine state unavailable".to_string())?;
    if state.is_some() { return Err("Engine already running".into()); }
    REQUESTS.store(0, Ordering::Relaxed);
    BYTES_SERVED.store(0, Ordering::Relaxed);
    ACTIVE_CONNECTIONS.store(0, Ordering::Relaxed);
    QUEUED_CONNECTIONS.store(0, Ordering::Relaxed);
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
        runtime.block_on(accept_loop(listener, root, canonical_hosts, stop_rx));
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
            sites.insert(host, target);
        }
        start_engine_with_hosts(&root, port as u16, allow_lan == 1, sites)?;
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
#[cfg(test)]
mod tests {
    use super::*;
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
