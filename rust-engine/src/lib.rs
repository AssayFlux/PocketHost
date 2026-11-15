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
use tokio::io::{AsyncReadExt, AsyncSeekExt};
use tokio::net::TcpListener;
use tokio::sync::{oneshot, Semaphore};
use tokio::task::JoinSet;
use tokio_util::io::ReaderStream;

const MAX_FILE_BYTES: u64 = 50 * 1024 * 1024;
const MAX_CONNECTIONS: usize = 16;
type ResponseBody = UnsyncBoxBody<Bytes, std::io::Error>;

#[derive(Clone)]
enum SiteTarget { Static(PathBuf), Proxy { port: u16 } }

static REQUESTS: AtomicU64 = AtomicU64::new(0);
static BYTES_SERVED: AtomicU64 = AtomicU64::new(0);
static ACTIVE_CONNECTIONS: AtomicU64 = AtomicU64::new(0);

struct ActiveConnection;
impl Drop for ActiveConnection {
    fn drop(&mut self) { ACTIVE_CONNECTIONS.fetch_sub(1, Ordering::Relaxed); }
}

struct RunningEngine { stop: oneshot::Sender<()>, thread: JoinHandle<()> }
static ENGINE: OnceLock<Mutex<Option<RunningEngine>>> = OnceLock::new();
fn engine() -> &'static Mutex<Option<RunningEngine>> { ENGINE.get_or_init(|| Mutex::new(None)) }

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
    if let Some(host) = original_host {
        headers.insert("x-forwarded-host", host);
    }
    headers.insert("x-forwarded-proto", forwarded_proto.parse().unwrap());
    for name in ["connection", "proxy-connection", "keep-alive", "transfer-encoding", "trailer", "te"] {
        headers.remove(name);
    }
    let connector = HttpConnector::new();
    let client: Client<_, Incoming> = Client::builder(TokioExecutor::new()).build(connector);
    match client.request(req).await {
        Ok(upstream) => {
            let (mut parts, body) = upstream.into_parts();
            parts.headers.remove("connection");
            parts.headers.remove("transfer-encoding");
            if !parts.headers.contains_key("x-content-type-options") {
                parts.headers.insert("x-content-type-options", "nosniff".parse().unwrap());
            }
            let body = body.map_err(|error| std::io::Error::new(std::io::ErrorKind::Other, error)).boxed_unsync();
            Ok(Response::from_parts(parts, body))
        }
        Err(_) => Ok(text_response(StatusCode::BAD_GATEWAY, "text/plain; charset=utf-8", "Upstream runtime is unavailable")),
    }
}

async fn serve(root: PathBuf, host_roots: Arc<HashMap<String, SiteTarget>>, req: Request<Incoming>) -> Result<Response<ResponseBody>, std::convert::Infallible> {
    let requested_host = req.headers().get(hyper::header::HOST)
        .and_then(|value| value.to_str().ok())
        .unwrap_or("")
        .trim()
        .to_ascii_lowercase()
        .split(':').next().unwrap_or("").to_string();
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
    let canonical = match tokio::fs::canonicalize(selected_root.join(relative)).await {
        Ok(path) => path,
        Err(_) => return Ok(text_response(StatusCode::NOT_FOUND, "text/plain; charset=utf-8", "Not found")),
    };
    if !canonical.starts_with(&selected_root) || !canonical.is_file() {
        return Ok(text_response(StatusCode::FORBIDDEN, "text/plain; charset=utf-8", "Forbidden"));
    }
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
    let permits = Arc::new(Semaphore::new(MAX_CONNECTIONS));
    loop {
        tokio::select! {
            _ = &mut stop => {
                connections.abort_all();
                while connections.join_next().await.is_some() {}
                break;
            },
            accepted = listener.accept() => {
                let Ok((stream, _peer)) = accepted else { continue };
                let Ok(permit) = permits.clone().try_acquire_owned() else {
                    drop(stream);
                    continue;
                };
                let site_root = root.clone();
                let site_roots = host_roots.clone();
                ACTIVE_CONNECTIONS.fetch_add(1, Ordering::Relaxed);
                let active = ActiveConnection;
                connections.spawn(async move {
                    let _active = active;
                    let _permit = permit;
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
        let normalized = host.trim().trim_end_matches('.').to_ascii_lowercase();
        if normalized.is_empty() || normalized.contains('/') || normalized.contains(':') || normalized.contains(' ') {
            return Err(format!("Invalid hostname: {host}"));
        }
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
                    "python-wsgi" | "node" => SiteTarget::Proxy {
                        port: value.get("port").and_then(|v| v.as_u64()).ok_or("Proxy route requires port")? as u16
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
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_activeConnections(_env: JNIEnv, _class: JClass) -> jlong {
    ACTIVE_CONNECTIONS.load(Ordering::Relaxed) as jlong
}
#[cfg(test)]
mod tests {
    use super::*;
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
