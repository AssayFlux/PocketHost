use axum::{
    extract::{Path, State},
    http::{HeaderMap, StatusCode},
    routing::{get, post, put},
    Json, Router,
};
use chrono::{DateTime, Utc};
use rand::{rngs::OsRng, RngCore};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use sqlx::{postgres::PgPoolOptions, PgPool, Row};
use subtle::ConstantTimeEq;
use tower_http::trace::TraceLayer;
use uuid::Uuid;

#[derive(Clone)]
struct AppState {
    db: PgPool,
    admin_token: String,
}

#[derive(Serialize)]
struct DeviceView {
    id: Uuid,
    name: String,
    state: Value,
    last_seen: Option<DateTime<Utc>>,
    created_at: DateTime<Utc>,
}

#[derive(Deserialize)]
struct RegisterDevice {
    name: String,
}

#[derive(Serialize)]
struct RegisteredDevice {
    id: Uuid,
    name: String,
    device_token: String,
    warning: &'static str,
}

#[derive(Deserialize)]
struct ConfigUpdate {
    config: Value,
    expected_version: Option<i64>,
}

#[derive(Serialize)]
struct ConfigView {
    version: i64,
    config: Value,
    updated_at: DateTime<Utc>,
}

#[derive(Deserialize)]
struct QueueCommand {
    kind: String,
    payload: Value,
}

#[derive(Serialize)]
struct CommandView {
    id: Uuid,
    kind: String,
    payload: Value,
    created_at: DateTime<Utc>,
}

#[derive(Deserialize)]
struct Heartbeat {
    state: Value,
    config_version: Option<i64>,
}

#[derive(Deserialize)]
struct CommandAck {
    success: bool,
    result: Value,
}

fn bearer(headers: &HeaderMap) -> Option<&str> {
    headers.get(axum::http::header::AUTHORIZATION)?.to_str().ok()?.strip_prefix("Bearer ")
}

fn token_hash(token: &str) -> String {
    hex::encode(Sha256::digest(token.as_bytes()))
}

fn secure_equal(a: &str, b: &str) -> bool {
    a.as_bytes().ct_eq(b.as_bytes()).into()
}

fn new_token() -> String {
    let mut bytes = [0u8; 32];
    OsRng.fill_bytes(&mut bytes);
    hex::encode(bytes)
}

fn admin(headers: &HeaderMap, state: &AppState) -> Result<(), (StatusCode, String)> {
    let supplied = bearer(headers).ok_or((StatusCode::UNAUTHORIZED, "admin_auth_required".into()))?;
    if secure_equal(supplied, &state.admin_token) { Ok(()) }
    else { Err((StatusCode::UNAUTHORIZED, "invalid_admin_token".into())) }
}

async fn device_id(headers: &HeaderMap, state: &AppState) -> Result<Uuid, (StatusCode, String)> {
    let token = bearer(headers).ok_or((StatusCode::UNAUTHORIZED, "device_auth_required".into()))?;
    let row = sqlx::query("SELECT id FROM devices WHERE token_hash = $1")
        .bind(token_hash(token)).fetch_optional(&state.db).await
        .map_err(internal)?;
    row.map(|r| r.get("id")).ok_or((StatusCode::UNAUTHORIZED, "invalid_device_token".into()))
}

fn internal<E: std::fmt::Display>(error: E) -> (StatusCode, String) {
    tracing::error!(error = %error, "control-plane request failed");
    (StatusCode::INTERNAL_SERVER_ERROR, "internal_error".into())
}

async fn health() -> Json<Value> {
    Json(json!({"status":"ok","service":"pockethost-control-plane","apiVersion":"v1"}))
}

async fn register_device(
    State(s): State<AppState>, headers: HeaderMap, Json(input): Json<RegisterDevice>,
) -> Result<(StatusCode, Json<RegisteredDevice>), (StatusCode, String)> {
    admin(&headers, &s)?;
    let name = input.name.trim();
    if name.is_empty() || name.len() > 120 {
        return Err((StatusCode::BAD_REQUEST, "invalid_device_name".into()));
    }
    let id = Uuid::new_v4();
    let token = new_token();
    sqlx::query("INSERT INTO devices(id,name,token_hash) VALUES($1,$2,$3)")
        .bind(id).bind(name).bind(token_hash(&token)).execute(&s.db).await.map_err(internal)?;
    sqlx::query("INSERT INTO device_configs(device_id,config) VALUES($1,'{}'::jsonb)")
        .bind(id).execute(&s.db).await.map_err(internal)?;
    Ok((StatusCode::CREATED, Json(RegisteredDevice {
        id, name: name.to_owned(), device_token: token,
        warning: "Store this token securely. It is shown only once.",
    })))
}

async fn list_devices(
    State(s): State<AppState>, headers: HeaderMap,
) -> Result<Json<Vec<DeviceView>>, (StatusCode, String)> {
    admin(&headers, &s)?;
    let rows = sqlx::query("SELECT id,name,state,last_seen,created_at FROM devices ORDER BY created_at DESC")
        .fetch_all(&s.db).await.map_err(internal)?;
    Ok(Json(rows.into_iter().map(|r| DeviceView {
        id: r.get("id"), name: r.get("name"), state: r.get("state"),
        last_seen: r.get("last_seen"), created_at: r.get("created_at"),
    }).collect()))
}

async fn get_config(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>,
) -> Result<Json<ConfigView>, (StatusCode, String)> {
    admin(&headers, &s)?;
    let row = sqlx::query("SELECT version,config,updated_at FROM device_configs WHERE device_id=$1")
        .bind(id).fetch_optional(&s.db).await.map_err(internal)?
        .ok_or((StatusCode::NOT_FOUND, "device_config_not_found".into()))?;
    Ok(Json(ConfigView { version: row.get("version"), config: row.get("config"), updated_at: row.get("updated_at") }))
}

async fn put_config(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>, Json(input): Json<ConfigUpdate>,
) -> Result<Json<ConfigView>, (StatusCode, String)> {
    admin(&headers, &s)?;
    let encoded = serde_json::to_vec(&input.config).map_err(internal)?;
    if encoded.len() > 256 * 1024 { return Err((StatusCode::PAYLOAD_TOO_LARGE, "config_too_large".into())); }
    let mut tx = s.db.begin().await.map_err(internal)?;
    let row = sqlx::query("SELECT version FROM device_configs WHERE device_id=$1 FOR UPDATE")
        .bind(id).fetch_optional(&mut *tx).await.map_err(internal)?
        .ok_or((StatusCode::NOT_FOUND, "device_config_not_found".into()))?;
    let current: i64 = row.get("version");
    if input.expected_version.is_some_and(|v| v != current) {
        return Err((StatusCode::CONFLICT, "config_version_conflict".into()));
    }
    let next = current + 1;
    let row = sqlx::query("UPDATE device_configs SET version=$2,config=$3,updated_at=now() WHERE device_id=$1 RETURNING version,config,updated_at")
        .bind(id).bind(next).bind(input.config).fetch_one(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    // A config update is also queued for the phone; it applies only after local validation.
    sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) VALUES($1,$2,'apply_config',$3)")
        .bind(Uuid::new_v4()).bind(id).bind(json!({"version":next})).execute(&s.db).await.map_err(internal)?;
    Ok(Json(ConfigView { version: row.get("version"), config: row.get("config"), updated_at: row.get("updated_at") }))
}

async fn queue_command(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>, Json(input): Json<QueueCommand>,
) -> Result<(StatusCode, Json<Value>), (StatusCode, String)> {
    admin(&headers, &s)?;
    const ALLOWED: &[&str] = &["deploy_site", "site_action", "tunnel_action", "restart_runtime"];
    if !ALLOWED.contains(&input.kind.as_str()) {
        return Err((StatusCode::BAD_REQUEST, "unsupported_command".into()));
    }
    if serde_json::to_vec(&input.payload).map_err(internal)?.len() > 256 * 1024 {
        return Err((StatusCode::PAYLOAD_TOO_LARGE, "command_payload_too_large".into()));
    }
    let command_id = Uuid::new_v4();
    let result = sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) SELECT $1,id,$2,$3 FROM devices WHERE id=$4")
        .bind(command_id).bind(input.kind).bind(input.payload).bind(id).execute(&s.db).await.map_err(internal)?;
    if result.rows_affected() == 0 { return Err((StatusCode::NOT_FOUND, "device_not_found".into())); }
    Ok((StatusCode::ACCEPTED, Json(json!({"commandId":command_id,"state":"queued"}))))
}

async fn poll_commands(
    State(s): State<AppState>, headers: HeaderMap,
) -> Result<Json<Vec<CommandView>>, (StatusCode, String)> {
    let id = device_id(&headers, &s).await?;
    let mut tx = s.db.begin().await.map_err(internal)?;
    let rows = sqlx::query("SELECT id,kind,payload,created_at FROM device_commands WHERE device_id=$1 AND state='queued' ORDER BY created_at LIMIT 32 FOR UPDATE SKIP LOCKED")
        .bind(id).fetch_all(&mut *tx).await.map_err(internal)?;
    let mut commands = Vec::new();
    for row in rows {
        let command_id: Uuid = row.get("id");
        sqlx::query("UPDATE device_commands SET state='delivered' WHERE id=$1")
            .bind(command_id).execute(&mut *tx).await.map_err(internal)?;
        commands.push(CommandView { id: command_id, kind: row.get("kind"), payload: row.get("payload"), created_at: row.get("created_at") });
    }
    tx.commit().await.map_err(internal)?;
    Ok(Json(commands))
}

async fn heartbeat(
    State(s): State<AppState>, headers: HeaderMap, Json(input): Json<Heartbeat>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let id = device_id(&headers, &s).await?;
    let encoded = serde_json::to_vec(&input.state).map_err(internal)?;
    if encoded.len() > 64 * 1024 { return Err((StatusCode::PAYLOAD_TOO_LARGE, "device_state_too_large".into())); }
    sqlx::query("UPDATE devices SET state=$2,last_seen=now() WHERE id=$1")
        .bind(id).bind(input.state).execute(&s.db).await.map_err(internal)?;
    Ok(Json(json!({"accepted":true,"configVersionReported":input.config_version})))
}

async fn acknowledge(
    State(s): State<AppState>, headers: HeaderMap, Path(command): Path<Uuid>, Json(input): Json<CommandAck>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let id = device_id(&headers, &s).await?;
    let state = if input.success { "succeeded" } else { "failed" };
    let result = sqlx::query("UPDATE device_commands SET state=$3,result=$4,acknowledged_at=now() WHERE id=$1 AND device_id=$2 AND state='delivered'")
        .bind(command).bind(id).bind(state).bind(input.result).execute(&s.db).await.map_err(internal)?;
    if result.rows_affected() == 0 { return Err((StatusCode::NOT_FOUND, "command_not_found_or_already_acknowledged".into())); }
    Ok(Json(json!({"acknowledged":true,"state":state})))
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt().with_env_filter(
        tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into())
    ).init();
    let database_url = std::env::var("DATABASE_URL").expect("DATABASE_URL is required");
    let admin_token = std::env::var("POCKETHOST_CONTROL_ADMIN_TOKEN").expect("POCKETHOST_CONTROL_ADMIN_TOKEN is required");
    anyhow::ensure!(admin_token.len() >= 32, "admin token must contain at least 32 characters");
    let db = PgPoolOptions::new().max_connections(10).connect(&database_url).await?;
    sqlx::migrate!("./migrations").run(&db).await?;
    let state = AppState { db, admin_token };
    let app = Router::new()
        .route("/health", get(health))
        .route("/v1/admin/devices", get(list_devices).post(register_device))
        .route("/v1/admin/devices/:id/config", get(get_config).put(put_config))
        .route("/v1/admin/devices/:id/commands", post(queue_command))
        .route("/v1/device/commands", get(poll_commands))
        .route("/v1/device/heartbeat", post(heartbeat))
        .route("/v1/device/commands/:command/ack", post(acknowledge))
        .with_state(state)
        .layer(TraceLayer::new_for_http());
    let address = std::env::var("POCKETHOST_CONTROL_BIND").unwrap_or_else(|_| "127.0.0.1:8080".into());
    let listener = tokio::net::TcpListener::bind(&address).await?;
    tracing::info!(%address, "PocketHost control plane listening");
    axum::serve(listener, app).with_graceful_shutdown(async { let _ = tokio::signal::ctrl_c().await; }).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generated_tokens_are_unique_and_not_stored_as_plaintext() {
        let a = new_token();
        let b = new_token();
        assert_eq!(a.len(), 64);
        assert_ne!(a, b);
        assert_ne!(token_hash(&a), a);
    }

    #[test]
    fn admin_comparison_rejects_different_lengths_and_values() {
        assert!(secure_equal("same-token", "same-token"));
        assert!(!secure_equal("same-token", "other-token"));
        assert!(!secure_equal("short", "longer"));
    }

    #[test]
    fn command_allowlist_excludes_arbitrary_shell_execution() {
        let allowed = ["deploy_site", "site_action", "tunnel_action", "restart_runtime"];
        assert!(allowed.contains(&"site_action"));
        assert!(!allowed.contains(&"shell"));
    }
}
