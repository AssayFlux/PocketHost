use bytes::Bytes;
use http_body_util::Full;
use hyper::body::Incoming;
use hyper::service::service_fn;
use hyper::{Method, Request, Response, StatusCode};
use hyper_util::rt::{TokioExecutor, TokioIo};
use hyper_util::server::conn::auto::Builder;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use percent_encoding::percent_decode_str;
use std::path::{Component, Path, PathBuf};
use std::sync::{mpsc, Mutex, OnceLock};
use std::thread::JoinHandle;
use tokio::net::TcpListener;
use tokio::sync::oneshot;
use tokio::task::JoinSet;

struct RunningEngine { stop: oneshot::Sender<()>, thread: JoinHandle<()> }
static ENGINE: OnceLock<Mutex<Option<RunningEngine>>> = OnceLock::new();
fn engine() -> &'static Mutex<Option<RunningEngine>> { ENGINE.get_or_init(|| Mutex::new(None)) }

fn text_response(status: StatusCode, mime: &'static str, body: &'static str) -> Response<Full<Bytes>> {
    Response::builder().status(status).header("content-type", mime)
        .header("x-content-type-options", "nosniff")
        .body(Full::new(Bytes::from_static(body.as_bytes()))).expect("response")
}
fn mime_type(path: &Path) -> &'static str {
    match path.extension().and_then(|v| v.to_str()).unwrap_or("").to_ascii_lowercase().as_str() {
        "html" | "htm" => "text/html; charset=utf-8", "css" => "text/css; charset=utf-8",
        "js" | "mjs" => "text/javascript; charset=utf-8", "json" => "application/json",
        "svg" => "image/svg+xml", "png" => "image/png", "jpg" | "jpeg" => "image/jpeg",
        "webp" => "image/webp", "gif" => "image/gif", "ico" => "image/x-icon",
        "txt" => "text/plain; charset=utf-8", "pdf" => "application/pdf",
        "woff" => "font/woff", "woff2" => "font/woff2", _ => "application/octet-stream",
    }
}
async fn serve(root: PathBuf, req: Request<Incoming>) -> Result<Response<Full<Bytes>>, std::convert::Infallible> {
    if req.method() != Method::GET && req.method() != Method::HEAD {
        return Ok(text_response(StatusCode::METHOD_NOT_ALLOWED, "text/plain; charset=utf-8", "Method not allowed"));
    }
    let decoded = match percent_decode_str(req.uri().path()).decode_utf8() {
        Ok(value) => value.into_owned(),
        Err(_) => return Ok(text_response(StatusCode::BAD_REQUEST, "text/plain; charset=utf-8", "Invalid URL encoding")),
    };
    let mut relative = PathBuf::new();
    for part in Path::new(decoded.trim_start_matches('/')).components() {
        match part {
            Component::Normal(value) => relative.push(value),
            Component::CurDir => {},
            _ => return Ok(text_response(StatusCode::FORBIDDEN, "text/plain; charset=utf-8", "Forbidden")),
        }
    }
    if relative.as_os_str().is_empty() { relative.push("index.html"); }
    let canonical = match tokio::fs::canonicalize(root.join(relative)).await {
        Ok(path) => path,
        Err(_) => return Ok(text_response(StatusCode::NOT_FOUND, "text/plain; charset=utf-8", "Not found")),
    };
    if !canonical.starts_with(&root) || !canonical.is_file() {
        return Ok(text_response(StatusCode::FORBIDDEN, "text/plain; charset=utf-8", "Forbidden"));
    }
    let data = match tokio::fs::read(&canonical).await {
        Ok(data) => data,
        Err(_) => return Ok(text_response(StatusCode::INTERNAL_SERVER_ERROR, "text/plain; charset=utf-8", "Read error")),
    };
    let body = if req.method() == Method::HEAD { Bytes::new() } else { Bytes::from(data) };
    Ok(Response::builder().status(StatusCode::OK).header("content-type", mime_type(&canonical))
        .header("x-content-type-options", "nosniff").header("cache-control", "no-cache")
        .body(Full::new(body)).expect("file response"))
}

async fn accept_loop(listener: TcpListener, root: PathBuf, mut stop: oneshot::Receiver<()>) {
    let mut connections = JoinSet::new();
    loop {
        tokio::select! {
            _ = &mut stop => {
                connections.abort_all();
                while connections.join_next().await.is_some() {}
                break;
            },
            accepted = listener.accept() => {
                let Ok((stream, _peer)) = accepted else { continue };
                let site_root = root.clone();
                connections.spawn(async move {
                    let service = service_fn(move |req| serve(site_root.clone(), req));
                    let builder = Builder::new(TokioExecutor::new());
                    let _ = builder.serve_connection(TokioIo::new(stream), service).await;
                });
            }
            Some(_) = connections.join_next(), if !connections.is_empty() => {}
        }
    }
}

fn start_engine(root: &str, port: u16) -> Result<(), String> {
    let root = std::fs::canonicalize(root).map_err(|e| format!("Invalid site directory: {e}"))?;
    if !root.is_dir() { return Err("Site path is not a directory".into()); }
    let mut state = engine().lock().map_err(|_| "Engine state unavailable".to_string())?;
    if state.is_some() { return Err("Engine already running".into()); }

    // Phase 0 deliberately binds to loopback. LAN/public exposure is not enabled.
    let listener = std::net::TcpListener::bind(("127.0.0.1", port))
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
pub extern "system" fn Java_com_assayflux_pockethost_engine_NativeEngine_start(mut env: JNIEnv, _class: JClass, root: JString, port: jint) -> jstring {
    let result = (|| {
        let root: String = env.get_string(&root).map_err(|e| e.to_string())?.into();
        if !(1024..=65535).contains(&port) { return Err("Port must be 1024–65535".to_string()); }
        start_engine(&root, port as u16)?;
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
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn maps_common_web_mime_types() {
        assert_eq!(mime_type(Path::new("index.html")), "text/html; charset=utf-8");
        assert_eq!(mime_type(Path::new("site.css")), "text/css; charset=utf-8");
        assert_eq!(mime_type(Path::new("app.js")), "text/javascript; charset=utf-8");
        assert_eq!(mime_type(Path::new("font.woff2")), "font/woff2");
    }
}
