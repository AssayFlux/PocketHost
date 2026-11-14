use bytes::Bytes;
use futures_util::StreamExt;
use http_body_util::{combinators::UnsyncBoxBody, BodyExt, Full, StreamBody};
use hyper::body::{Frame, Incoming};
use hyper::service::service_fn;
use hyper::{Method, Request, Response, StatusCode};
use hyper_util::rt::{TokioExecutor, TokioIo};
use hyper_util::server::conn::auto::Builder;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use percent_encoding::percent_decode_str;
use std::path::{Component, Path, PathBuf};
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

async fn serve(root: PathBuf, req: Request<Incoming>) -> Result<Response<ResponseBody>, std::convert::Infallible> {
    REQUESTS.fetch_add(1, Ordering::Relaxed);
    if req.method() != Method::GET && req.method() != Method::HEAD {
        return Ok(text_response(StatusCode::METHOD_NOT_ALLOWED, "text/plain; charset=utf-8", "Method not allowed"));
    }
    let relative = match request_relative_path(req.uri().path()) {
        Ok(path) => path,
        Err(status) => return Ok(text_response(status, "text/plain; charset=utf-8", "Invalid or forbidden path")),
    };
    let canonical = match tokio::fs::canonicalize(root.join(relative)).await {
        Ok(path) => path,
        Err(_) => return Ok(text_response(StatusCode::NOT_FOUND, "text/plain; charset=utf-8", "Not found")),
    };
    if !canonical.starts_with(&root) || !canonical.is_file() {
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
        BYTES_SERVED.fetch_add(response_length, Ordering::Relaxed);
        let chunks = ReaderStream::new(file.take(response_length)).map(|chunk| chunk.map(|bytes| Frame::data(bytes)));
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

async fn accept_loop(listener: TcpListener, root: PathBuf, mut stop: oneshot::Receiver<()>) {
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
                ACTIVE_CONNECTIONS.fetch_add(1, Ordering::Relaxed);
                let active = ActiveConnection;
                connections.spawn(async move {
                    let _active = active;
                    let _permit = permit;
                    let service = service_fn(move |req| serve(site_root.clone(), req));
                    let builder = Builder::new(TokioExecutor::new());
                    let _ = builder.serve_connection(TokioIo::new(stream), service).await;
                });
            }
            Some(_) = connections.join_next(), if !connections.is_empty() => {}
        }
    }
}

fn start_engine(root: &str, port: u16, allow_lan: bool) -> Result<(), String> {
    let root = std::fs::canonicalize(root).map_err(|e| format!("Invalid site directory: {e}"))?;
    if !root.is_dir() { return Err("Site path is not a directory".into()); }
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
        let listener = match TcpListener::from_std(listener) {
            Ok(listener) => listener,
            Err(error) => {
                let _ = ready_tx.send(Err(format!("Cannot initialize listener: {error}")));
                return;
            }
        };
        if ready_tx.send(Ok(())).is_err() { return; }
        runtime.block_on(accept_loop(listener, root, stop_rx));
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
