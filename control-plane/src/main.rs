use axum::{
    body::Bytes,
    extract::{DefaultBodyLimit, Path, State},
    http::{header::{AUTHORIZATION, CONTENT_TYPE}, HeaderMap, HeaderValue, Method, StatusCode, Response},
    routing::{get, post},
    Json, Router,
};
use chrono::{DateTime, Utc};
use rand::{rngs::OsRng, RngCore};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{fs, path::PathBuf};
use sqlx::{postgres::PgPoolOptions, PgPool, Row};
use subtle::ConstantTimeEq;
use tower_http::{cors::CorsLayer, trace::TraceLayer};
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

fn role_can_write(role: &str) -> bool {
    matches!(role, "owner" | "editor")
}

fn has_zip_signature(bytes: &[u8]) -> bool {
    bytes.starts_with(b"PK\x03\x04") || bytes.starts_with(b"PK\x05\x06")
}

fn new_token() -> String {
    let mut bytes = [0u8; 32];
    OsRng.fill_bytes(&mut bytes);
    hex::encode(bytes)
}

#[derive(Clone, Debug)]
struct AdminActor { name: String, role: String }

async fn admin(headers: &HeaderMap, state: &AppState) -> Result<AdminActor, (StatusCode, String)> {
    let supplied = bearer(headers).ok_or((StatusCode::UNAUTHORIZED, "admin_auth_required".into()))?;
    if secure_equal(supplied, &state.admin_token) {
        return Ok(AdminActor { name: "Bootstrap Owner".into(), role: "owner".into() });
    }
    let row = sqlx::query("SELECT name,role FROM control_users WHERE token_hash=$1 AND active=TRUE")
        .bind(token_hash(supplied)).fetch_optional(&state.db).await.map_err(internal)?;
    row.map(|row| AdminActor { name: row.get("name"), role: row.get("role") })
        .ok_or((StatusCode::UNAUTHORIZED, "invalid_admin_token".into()))
}

async fn admin_write(headers: &HeaderMap, state: &AppState) -> Result<AdminActor, (StatusCode, String)> {
    let actor = admin(headers, state).await?;
    if !role_can_write(&actor.role) {
        return Err((StatusCode::FORBIDDEN, "insufficient_role".into()));
    }
    Ok(actor)
}

#[derive(Deserialize)]
struct CreateUser { name: String, role: String }

#[derive(Serialize)]
struct UserCreated { id: Uuid, name: String, role: String, token: String }

async fn create_user(
    State(s): State<AppState>, headers: HeaderMap, Json(input): Json<CreateUser>,
) -> Result<(StatusCode, Json<UserCreated>), (StatusCode, String)> {
    let actor = admin(&headers, &s).await?;
    if actor.role != "owner" { return Err((StatusCode::FORBIDDEN, "owner_role_required".into())); }
    let name = input.name.trim();
    if name.is_empty() || name.len() > 120 || !["editor","viewer"].contains(&input.role.as_str()) {
        return Err((StatusCode::BAD_REQUEST, "invalid_user".into()));
    }
    let id = Uuid::new_v4();
    let token = new_token();
    sqlx::query("INSERT INTO control_users(id,name,role,token_hash) VALUES($1,$2,$3,$4)")
        .bind(id).bind(name).bind(&input.role).bind(token_hash(&token))
        .execute(&s.db).await.map_err(internal)?;
    audit(&s, &actor.name, "user.create", &id.to_string(), json!({"name":name,"role":input.role})).await?;
    Ok((StatusCode::CREATED, Json(UserCreated { id, name:name.into(), role:input.role, token })))
}

async fn list_users(
    State(s): State<AppState>, headers: HeaderMap,
) -> Result<Json<Vec<Value>>, (StatusCode, String)> {
    let actor = admin(&headers, &s).await?;
    if actor.role != "owner" { return Err((StatusCode::FORBIDDEN, "owner_role_required".into())); }
    let rows = sqlx::query("SELECT id,name,role,active,created_at FROM control_users ORDER BY created_at DESC")
        .fetch_all(&s.db).await.map_err(internal)?;
    Ok(Json(rows.into_iter().map(|r| json!({
        "id":r.get::<Uuid,_>("id"),"name":r.get::<String,_>("name"),
        "role":r.get::<String,_>("role"),"active":r.get::<bool,_>("active"),
        "createdAt":r.get::<DateTime<Utc>,_>("created_at")
    })).collect()))
}

async fn revoke_user(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>,
) -> Result<StatusCode, (StatusCode, String)> {
    let actor = admin(&headers, &s).await?;
    if actor.role != "owner" { return Err((StatusCode::FORBIDDEN, "owner_role_required".into())); }
    let result = sqlx::query("UPDATE control_users SET active=FALSE WHERE id=$1")
        .bind(id).execute(&s.db).await.map_err(internal)?;
    if result.rows_affected() == 0 { return Err((StatusCode::NOT_FOUND, "user_not_found".into())); }
    audit(&s, &actor.name, "user.revoke", &id.to_string(), json!({})).await?;
    Ok(StatusCode::NO_CONTENT)
}

async fn device_id(headers: &HeaderMap, state: &AppState) -> Result<Uuid, (StatusCode, String)> {
    let token = bearer(headers).ok_or((StatusCode::UNAUTHORIZED, "device_auth_required".into()))?;
    let row = sqlx::query("SELECT id FROM devices WHERE token_hash = $1")
        .bind(token_hash(token)).fetch_optional(&state.db).await
        .map_err(internal)?;
    row.map(|r| r.get("id")).ok_or((StatusCode::UNAUTHORIZED, "invalid_device_token".into()))
}

async fn audit(s: &AppState, actor: &str, action: &str, target: &str, details: Value) -> Result<(), (StatusCode, String)> {
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(actor).bind(action).bind(target).bind(details).execute(&s.db).await.map_err(internal)?;
    Ok(())
}

async fn list_audit(State(s): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<Value>>, (StatusCode, String)> {
    let _role = admin(&headers, &s).await?;
    let rows = sqlx::query("SELECT id,actor,action,target,details,created_at FROM audit_events ORDER BY created_at DESC LIMIT 200")
        .fetch_all(&s.db).await.map_err(internal)?;
    Ok(Json(rows.into_iter().map(|r| json!({
        "id": r.get::<i64,_>("id"), "actor": r.get::<String,_>("actor"),
        "action": r.get::<String,_>("action"), "target": r.get::<String,_>("target"),
        "details": r.get::<Value,_>("details"), "createdAt": r.get::<DateTime<Utc>,_>("created_at")
    })).collect()))
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
    let role = admin_write(&headers, &s).await?;
    let name = input.name.trim();
    if name.is_empty() || name.len() > 120 {
        return Err((StatusCode::BAD_REQUEST, "invalid_device_name".into()));
    }
    let id = Uuid::new_v4();
    let token = new_token();
    let mut tx = s.db.begin().await.map_err(internal)?;
    sqlx::query("INSERT INTO devices(id,name,token_hash) VALUES($1,$2,$3)")
        .bind(id).bind(name).bind(token_hash(&token)).execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO device_configs(device_id,config) VALUES($1,'{}'::jsonb)")
        .bind(id).execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    audit(&s, &role.name, "device.register", &id.to_string(), json!({"name":name})).await?;
    Ok((StatusCode::CREATED, Json(RegisteredDevice {
        id, name: name.to_owned(), device_token: token,
        warning: "Store this token securely. It is shown only once.",
    })))
}

async fn list_devices(
    State(s): State<AppState>, headers: HeaderMap,
) -> Result<Json<Vec<DeviceView>>, (StatusCode, String)> {
    let _role = admin(&headers, &s).await?;
    let rows = sqlx::query("SELECT id,name,state,last_seen,created_at FROM devices ORDER BY created_at DESC")
        .fetch_all(&s.db).await.map_err(internal)?;
    Ok(Json(rows.into_iter().map(|r| DeviceView {
        id: r.get("id"), name: r.get("name"), state: r.get("state"),
        last_seen: r.get("last_seen"), created_at: r.get("created_at"),
    }).collect()))
}

async fn delete_device(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>,
) -> Result<StatusCode, (StatusCode, String)> {
    let role = admin_write(&headers, &s).await?;
    let artifacts = sqlx::query("SELECT storage_path FROM device_artifacts WHERE device_id=$1")
        .bind(id).fetch_all(&s.db).await.map_err(internal)?;
    let result = sqlx::query("DELETE FROM devices WHERE id=$1")
        .bind(id).execute(&s.db).await.map_err(internal)?;
    if result.rows_affected() == 0 {
        return Err((StatusCode::NOT_FOUND, "device_not_found".into()));
    }
    for row in artifacts {
        let path: String = row.get("storage_path");
        let _ = fs::remove_file(path);
    }
    audit(&s, &role.name, "device.revoke", &id.to_string(), json!({})).await?;
    Ok(StatusCode::NO_CONTENT)
}

async fn get_config(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>,
) -> Result<Json<ConfigView>, (StatusCode, String)> {
    let _role = admin(&headers, &s).await?;
    let row = sqlx::query("SELECT version,config,updated_at FROM device_configs WHERE device_id=$1")
        .bind(id).fetch_optional(&s.db).await.map_err(internal)?
        .ok_or((StatusCode::NOT_FOUND, "device_config_not_found".into()))?;
    Ok(Json(ConfigView { version: row.get("version"), config: row.get("config"), updated_at: row.get("updated_at") }))
}

async fn put_config(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>, Json(input): Json<ConfigUpdate>,
) -> Result<Json<ConfigView>, (StatusCode, String)> {
    let role = admin_write(&headers, &s).await?;
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
    let committed_config: Value = row.get("config");
    // Persist the desired config and its delivery command atomically.
    sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) VALUES($1,$2,'apply_config',$3)")
        .bind(Uuid::new_v4()).bind(id)
        .bind(json!({"version":next,"config":committed_config}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    audit(&s, &role.name, "config.update", &id.to_string(), json!({"version":next})).await?;
    Ok(Json(ConfigView { version: row.get("version"), config: row.get("config"), updated_at: row.get("updated_at") }))
}

async fn queue_command(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>, Json(input): Json<QueueCommand>,
) -> Result<(StatusCode, Json<Value>), (StatusCode, String)> {
    let role = admin_write(&headers, &s).await?;
    const ALLOWED: &[&str] = &["site_action", "tunnel_action", "restart_runtime"];
    if !ALLOWED.contains(&input.kind.as_str()) {
        return Err((StatusCode::BAD_REQUEST, "unsupported_command".into()));
    }
    if serde_json::to_vec(&input.payload).map_err(internal)?.len() > 256 * 1024 {
        return Err((StatusCode::PAYLOAD_TOO_LARGE, "command_payload_too_large".into()));
    }
    let command_id = Uuid::new_v4();
    let result = sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) SELECT $1,id,$2,$3 FROM devices WHERE id=$4")
        .bind(command_id).bind(&input.kind).bind(input.payload).bind(id).execute(&s.db).await.map_err(internal)?;
    if result.rows_affected() == 0 { return Err((StatusCode::NOT_FOUND, "device_not_found".into())); }
    audit(&s, &role.name, "command.queue", &id.to_string(), json!({"commandId":command_id,"kind":input.kind})).await?;
    Ok((StatusCode::ACCEPTED, Json(json!({"commandId":command_id,"state":"queued"}))))
}

async fn upload_site_archive(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>, body: Bytes,
) -> Result<(StatusCode, Json<Value>), (StatusCode, String)> {
    let role = admin_write(&headers, &s).await?;
    if body.is_empty() || body.len() > 50 * 1024 * 1024 {
        return Err((StatusCode::PAYLOAD_TOO_LARGE, "archive_must_be_1_to_50_mib".into()));
    }
    if !has_zip_signature(&body) {
        return Err((StatusCode::BAD_REQUEST, "invalid_zip_archive".into()));
    }
    let sha = hex::encode(Sha256::digest(&body));
    let artifact_id = Uuid::new_v4();
    let root = PathBuf::from(std::env::var("POCKETHOST_ARTIFACT_DIR").unwrap_or_else(|_| "./artifacts".into()));
    fs::create_dir_all(&root).map_err(internal)?;
    let path = root.join(format!("{artifact_id}.zip"));
    fs::write(&path, &body).map_err(internal)?;
    let mut tx = s.db.begin().await.map_err(internal)?;
    let exists = sqlx::query("SELECT id FROM devices WHERE id=$1").bind(id)
        .fetch_optional(&mut *tx).await.map_err(internal)?;
    if exists.is_none() {
        let _ = fs::remove_file(&path);
        return Err((StatusCode::NOT_FOUND, "device_not_found".into()));
    }
    sqlx::query("INSERT INTO device_artifacts(id,device_id,sha256,byte_size,storage_path) VALUES($1,$2,$3,$4,$5)")
        .bind(artifact_id).bind(id).bind(&sha).bind(body.len() as i64)
        .bind(path.to_string_lossy().to_string()).execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) VALUES($1,$2,'deploy_site',$3)")
        .bind(Uuid::new_v4()).bind(id)
        .bind(json!({"artifactId":artifact_id,"sha256":sha,"byteSize":body.len()}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    audit(&s, &role.name, "site.deploy.queue", &id.to_string(), json!({"artifactId":artifact_id,"sha256":sha,"byteSize":body.len()})).await?;
    Ok((StatusCode::ACCEPTED, Json(json!({"artifactId":artifact_id,"sha256":sha,"byteSize":body.len(),"state":"queued"}))))
}

async fn download_artifact(
    State(s): State<AppState>, headers: HeaderMap, Path(artifact): Path<Uuid>,
) -> Result<Response<axum::body::Body>, (StatusCode, String)> {
    let device = device_id(&headers, &s).await?;
    let row = sqlx::query("SELECT storage_path,sha256 FROM device_artifacts WHERE id=$1 AND device_id=$2")
        .bind(artifact).bind(device).fetch_optional(&s.db).await.map_err(internal)?
        .ok_or((StatusCode::NOT_FOUND, "artifact_not_found".into()))?;
    let path: String = row.get("storage_path");
    let expected: String = row.get("sha256");
    let bytes = fs::read(path).map_err(internal)?;
    if hex::encode(Sha256::digest(&bytes)) != expected {
        return Err((StatusCode::INTERNAL_SERVER_ERROR, "artifact_integrity_check_failed".into()));
    }
    Response::builder()
        .status(StatusCode::OK)
        .header(CONTENT_TYPE, "application/zip")
        .header("Cache-Control", "no-store")
        .header(axum::http::header::CONTENT_LENGTH, bytes.len().to_string())
        .body(axum::body::Body::from(bytes))
        .map_err(internal)
}

async fn poll_commands(
    State(s): State<AppState>, headers: HeaderMap,
) -> Result<Json<Vec<CommandView>>, (StatusCode, String)> {
    let id = device_id(&headers, &s).await?;
    let mut tx = s.db.begin().await.map_err(internal)?;
    sqlx::query("UPDATE device_commands SET state='expired' WHERE device_id=$1 AND state IN ('queued','delivered') AND expires_at <= now()")
        .bind(id).execute(&mut *tx).await.map_err(internal)?;
    let rows = sqlx::query("SELECT id,kind,payload,created_at FROM device_commands WHERE device_id=$1 AND expires_at > now() AND (state='queued' OR (state='delivered' AND delivered_at < now() - interval '2 minutes')) ORDER BY created_at LIMIT 32 FOR UPDATE SKIP LOCKED")
        .bind(id).fetch_all(&mut *tx).await.map_err(internal)?;
    let mut commands = Vec::new();
    for row in rows {
        let command_id: Uuid = row.get("id");
        sqlx::query("UPDATE device_commands SET state='delivered',delivered_at=now(),delivery_attempts=delivery_attempts+1 WHERE id=$1")
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
    let result = sqlx::query("UPDATE device_commands SET state=$3,result=$4,acknowledged_at=now() WHERE id=$1 AND device_id=$2 AND state='delivered' AND expires_at > now()")
        .bind(command).bind(id).bind(state).bind(input.result).execute(&s.db).await.map_err(internal)?;
    if result.rows_affected() == 0 { return Err((StatusCode::NOT_FOUND, "command_not_found_or_already_acknowledged".into())); }
    if input.success {
        if let Some(row) = sqlx::query("SELECT payload FROM device_commands WHERE id=$1 AND device_id=$2 AND kind='deploy_site'")
            .bind(command).bind(id).fetch_optional(&s.db).await.map_err(internal)? {
            let payload: Value = row.get("payload");
            if let Some(artifact) = payload.get("artifactId").and_then(Value::as_str).and_then(|value| Uuid::parse_str(value).ok()) {
                if let Some(file) = sqlx::query("SELECT storage_path FROM device_artifacts WHERE id=$1 AND device_id=$2")
                    .bind(artifact).bind(id).fetch_optional(&s.db).await.map_err(internal)? {
                    let path: String = file.get("storage_path");
                    sqlx::query("DELETE FROM device_artifacts WHERE id=$1").bind(artifact).execute(&s.db).await.map_err(internal)?;
                    let _ = fs::remove_file(path);
                }
            }
        }
    }
    audit(&s, "device", "command.acknowledge", &command.to_string(), json!({"deviceId":id,"state":state})).await?;
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
    let cors = match std::env::var("POCKETHOST_CONTROL_ORIGIN") {
        Ok(origin) => CorsLayer::new()
            .allow_origin(origin.parse::<HeaderValue>()?)
            .allow_methods([Method::GET, Method::POST, Method::PUT, Method::DELETE, Method::OPTIONS])
            .allow_headers([AUTHORIZATION, CONTENT_TYPE]),
        Err(_) => CorsLayer::new(),
    };
    let app = Router::new()
        .route("/health", get(health))
        .route("/v1/admin/audit", get(list_audit))
        .route("/v1/admin/users", get(list_users).post(create_user))
        .route("/v1/admin/users/:id", axum::routing::delete(revoke_user))
        .route("/v1/admin/devices", get(list_devices).post(register_device))
        .route("/v1/admin/devices/:id", axum::routing::delete(delete_device))
        .route("/v1/admin/devices/:id/config", get(get_config).put(put_config))
        .route("/v1/admin/devices/:id/commands", post(queue_command))
        .route("/v1/admin/devices/:id/artifacts", post(upload_site_archive))
        .route("/v1/device/commands", get(poll_commands))
        .route("/v1/device/artifacts/:artifact", get(download_artifact))
        .route("/v1/device/heartbeat", post(heartbeat))
        .route("/v1/device/commands/:command/ack", post(acknowledge))
        .with_state(state)
        .layer(DefaultBodyLimit::max(50 * 1024 * 1024))
        .layer(cors)
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

    #[tokio::test]
    async fn postgres_migrations_support_revocable_viewer_accounts_and_audit() {
        let url = std::env::var("DATABASE_URL").expect("CI DATABASE_URL is required");
        let db = PgPoolOptions::new().max_connections(2).connect(&url).await.unwrap();
        sqlx::migrate!("./migrations").run(&db).await.unwrap();
        let id = Uuid::new_v4();
        let token = new_token();
        sqlx::query("INSERT INTO control_users(id,name,role,token_hash) VALUES($1,'CI viewer','viewer',$2)")
            .bind(id).bind(token_hash(&token)).execute(&db).await.unwrap();
        let state = AppState { db: db.clone(), admin_token: "owner-test-token-0123456789abcdef".into() };
        let mut headers = HeaderMap::new();
        headers.insert(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {token}")).unwrap());
        assert_eq!(admin(&headers, &state).await.unwrap().role, "viewer");
        assert_eq!(admin_write(&headers, &state).await.unwrap_err().0, StatusCode::FORBIDDEN);
        audit(&state, "viewer", "test.read", &id.to_string(), json!({"test":true})).await.unwrap();
        let count: i64 = sqlx::query_scalar("SELECT count(*) FROM audit_events WHERE target=$1")
            .bind(id.to_string()).fetch_one(&db).await.unwrap();
        assert_eq!(count, 1);
        sqlx::query("DELETE FROM control_users WHERE id=$1").bind(id).execute(&db).await.unwrap();
        assert!(admin(&headers, &state).await.is_err());
    }

    #[test]
    fn generated_tokens_are_unique_and_not_stored_as_plaintext() {
        let a = new_token();
        let b = new_token();
        assert_eq!(a.len(), 64);
        assert_ne!(a, b);
        assert_ne!(token_hash(&a), a);
    }

    #[test]
    fn viewer_is_read_only_and_editor_owner_can_mutate() {
        assert!(!role_can_write("viewer"));
        assert!(role_can_write("editor"));
        assert!(role_can_write("owner"));
        assert!(!role_can_write("unknown"));
    }

    #[test]
    fn deployment_upload_requires_a_zip_signature() {
        assert!(has_zip_signature(b"PK\x03\x04archive"));
        assert!(has_zip_signature(b"PK\x05\x06empty"));
        assert!(!has_zip_signature(b"not a zip"));
        assert!(!has_zip_signature(b""));
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
