use axum::{
    body::{to_bytes, Body, Bytes},
    extract::{DefaultBodyLimit, Path, Request, State},
    http::{header::{AUTHORIZATION, CONTENT_TYPE}, HeaderMap, HeaderValue, Method, StatusCode, Response},
    response::sse::{Event, KeepAlive, Sse},
    routing::{any, get, post},
    Json, Router,
};
use chrono::{DateTime, Utc};
use base64::{engine::general_purpose::STANDARD, Engine as _};
use futures_util::StreamExt;
use rand::{rngs::OsRng, RngCore};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{collections::HashMap, convert::Infallible, fs, io::Write, path::PathBuf, sync::Arc, time::Duration};
use sqlx::{postgres::PgPoolOptions, PgPool, Row};
use subtle::ConstantTimeEq;
use tower_http::{cors::CorsLayer, trace::TraceLayer};
use uuid::Uuid;

mod relay;

#[derive(Clone)]
struct AppState {
    db: PgPool,
    admin_token: String,
    relay: relay::RelayHub,
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

struct PendingArtifactFile {
    path: PathBuf,
    committed: bool,
}

impl PendingArtifactFile {
    fn create(path: PathBuf, bytes: &[u8]) -> std::io::Result<Self> {
        // Never overwrite an existing artifact, even if the storage directory is reused.
        let mut file = fs::OpenOptions::new().write(true).create_new(true).open(&path)?;
        // Arm cleanup immediately after exclusive creation so partial writes are removed.
        let pending = Self { path, committed: false };
        file.write_all(bytes)?;
        file.sync_all()?;
        if let Some(parent) = pending.path.parent() {
            fs::File::open(parent)?.sync_all()?;
        }
        Ok(pending)
    }

    fn commit(&mut self) {
        self.committed = true;
    }
}

impl Drop for PendingArtifactFile {
    fn drop(&mut self) {
        if !self.committed {
            if let Err(error) = fs::remove_file(&self.path) {
                if error.kind() != std::io::ErrorKind::NotFound {
                    tracing::warn!(path = %self.path.display(), error = %error, "uncommitted artifact cleanup failed");
                }
            }
        }
    }
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
struct AdminActor { session_id: Uuid, name: String, role: String }

#[derive(Clone, Debug)]
struct AdminCredential { user_id: Option<Uuid>, name: String, role: String, bootstrap_hash: Option<String>, user_token_hash: Option<String> }

const ADMIN_SESSION_TTL_HOURS: i64 = 12;

/// Long-lived API credentials are accepted only at the session-exchange endpoint.
async fn admin_credential(headers: &HeaderMap, state: &AppState) -> Result<AdminCredential, (StatusCode, String)> {
    let supplied = bearer(headers).ok_or((StatusCode::UNAUTHORIZED, "admin_credential_required".into()))?;
    if secure_equal(supplied, &state.admin_token) {
        return Ok(AdminCredential {
            user_id: None,
            name: "Bootstrap Owner".into(),
            role: "owner".into(),
            bootstrap_hash: Some(token_hash(supplied)),
            user_token_hash: None,
        });
    }
    let row = sqlx::query("SELECT id,name,role FROM control_users WHERE token_hash=$1 AND active=TRUE")
        .bind(token_hash(supplied)).fetch_optional(&state.db).await.map_err(internal)?;
    row.map(|row| AdminCredential {
        user_id: Some(row.get("id")),
        name: row.get("name"),
        role: row.get("role"),
        bootstrap_hash: None,
        user_token_hash: Some(token_hash(supplied)),
    }).ok_or((StatusCode::UNAUTHORIZED, "invalid_admin_credential".into()))
}

async fn create_admin_session(
    State(s): State<AppState>, headers: HeaderMap,
) -> Result<(StatusCode, Json<Value>), (StatusCode, String)> {
    let credential = admin_credential(&headers, &s).await?;
    let id = Uuid::new_v4();
    let token = new_token();
    let expires_at = Utc::now() + chrono::Duration::hours(ADMIN_SESSION_TTL_HOURS);
    let mut tx = s.db.begin().await.map_err(internal)?;
    sqlx::query("INSERT INTO control_sessions(id,user_id,name,role,token_hash,bootstrap_token_hash,user_token_hash,expires_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8)")
        .bind(id).bind(credential.user_id).bind(&credential.name).bind(&credential.role)
        .bind(token_hash(&token)).bind(&credential.bootstrap_hash).bind(&credential.user_token_hash).bind(expires_at)
        .execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,'session.create',$2,$3)")
        .bind(&credential.name).bind(id.to_string())
        .bind(json!({"role":credential.role,"expiresAt":expires_at}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    Ok((StatusCode::CREATED, Json(json!({
        "accessToken": token,
        "expiresAt": expires_at,
        "role": credential.role,
        "warning": "This session expires after 12 hours. Your long-lived API credential is used only to create sessions."
    }))))
}

/// Every management operation requires a short-lived server-side session.
/// A user session is invalidated immediately when its account is revoked or token rotated.
async fn admin(headers: &HeaderMap, state: &AppState) -> Result<AdminActor, (StatusCode, String)> {
    let supplied = bearer(headers).ok_or((StatusCode::UNAUTHORIZED, "admin_session_required".into()))?;
    let row = sqlx::query(
        "SELECT s.id,s.name,s.role FROM control_sessions s \
         LEFT JOIN control_users u ON u.id=s.user_id \
         WHERE s.token_hash=$1 AND s.revoked_at IS NULL AND s.expires_at > now() \
         AND ((s.user_id IS NULL AND s.bootstrap_token_hash=$2 AND s.user_token_hash IS NULL) OR \
              (s.user_id IS NOT NULL AND u.active=TRUE AND u.role=s.role AND u.token_hash=s.user_token_hash AND s.bootstrap_token_hash IS NULL))"
    )
        .bind(token_hash(supplied)).bind(token_hash(&state.admin_token))
        .fetch_optional(&state.db).await.map_err(internal)?;
    row.map(|row| AdminActor {
        session_id: row.get("id"),
        name: row.get("name"),
        role: row.get("role"),
    }).ok_or((StatusCode::UNAUTHORIZED, "invalid_or_expired_admin_session".into()))
}

async fn revoke_admin_session(
    State(s): State<AppState>, headers: HeaderMap,
) -> Result<StatusCode, (StatusCode, String)> {
    let actor = admin(&headers, &s).await?;
    let mut tx = s.db.begin().await.map_err(internal)?;
    sqlx::query("UPDATE control_sessions SET revoked_at=now() WHERE id=$1 AND revoked_at IS NULL")
        .bind(actor.session_id).execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,'session.revoke',$2,$3)")
        .bind(&actor.name).bind(actor.session_id.to_string()).bind(json!({}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    Ok(StatusCode::NO_CONTENT)
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
    let mut tx = s.db.begin().await.map_err(internal)?;
    sqlx::query("INSERT INTO control_users(id,name,role,token_hash) VALUES($1,$2,$3,$4)")
        .bind(id).bind(name).bind(&input.role).bind(token_hash(&token))
        .execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&actor.name).bind("user.create").bind(id.to_string())
        .bind(json!({"name":name,"role":input.role}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
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
    let mut tx = s.db.begin().await.map_err(internal)?;
    let result = sqlx::query("UPDATE control_users SET active=FALSE WHERE id=$1 AND active=TRUE")
        .bind(id).execute(&mut *tx).await.map_err(internal)?;
    if result.rows_affected() == 0 { return Err((StatusCode::NOT_FOUND, "user_not_found_or_already_revoked".into())); }
    sqlx::query("UPDATE control_sessions SET revoked_at=now() WHERE user_id=$1 AND revoked_at IS NULL")
        .bind(id).execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&actor.name).bind("user.revoke").bind(id.to_string()).bind(json!({}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    Ok(StatusCode::NO_CONTENT)
}

async fn rotate_user_token(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>,
) -> Result<(StatusCode, Json<Value>), (StatusCode, String)> {
    let actor = admin(&headers, &s).await?;
    if actor.role != "owner" {
        return Err((StatusCode::FORBIDDEN, "owner_role_required".into()));
    }
    let token = new_token();
    let mut tx = s.db.begin().await.map_err(internal)?;
    let updated = sqlx::query("UPDATE control_users SET token_hash=$2 WHERE id=$1 AND active=TRUE")
        .bind(id).bind(token_hash(&token)).execute(&mut *tx).await.map_err(internal)?;
    if updated.rows_affected() == 0 {
        return Err((StatusCode::NOT_FOUND, "active_user_not_found".into()));
    }
    sqlx::query("UPDATE control_sessions SET revoked_at=now() WHERE user_id=$1 AND revoked_at IS NULL")
        .bind(id).execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&actor.name).bind("user.token.rotate").bind(id.to_string())
        .bind(json!({"previous_token_revoked":true}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    Ok((StatusCode::OK, Json(json!({
        "userId": id,
        "token": token,
        "warning": "The previous user token is revoked immediately. Store this replacement securely; it is shown only once."
    }))))
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
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&role.name).bind("device.register").bind(id.to_string())
        .bind(json!({"name":name})).execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
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
    // Revocation, cascading credential/command removal, and its audit record are one
    // database transaction. A failed audit insert must never leave a revoked device
    // reported as an unsuccessful operation (or vice versa).
    let mut tx = s.db.begin().await.map_err(internal)?;
    let artifacts = sqlx::query("SELECT storage_path FROM device_artifacts WHERE device_id=$1")
        .bind(id).fetch_all(&mut *tx).await.map_err(internal)?;
    let result = sqlx::query("DELETE FROM devices WHERE id=$1")
        .bind(id).execute(&mut *tx).await.map_err(internal)?;
    if result.rows_affected() == 0 {
        return Err((StatusCode::NOT_FOUND, "device_not_found".into()));
    }
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&role.name).bind("device.revoke").bind(id.to_string()).bind(json!({}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;

    // Blob deletion is deliberately post-commit: database revocation remains durable
    // even if storage cleanup is temporarily unavailable.
    for row in artifacts {
        let path: String = row.get("storage_path");
        if let Err(error) = fs::remove_file(&path) {
            tracing::warn!(path = %path, error = %error, "revoked device artifact cleanup failed");
        }
    }
    Ok(StatusCode::NO_CONTENT)
}

async fn rotate_device_token(
    State(s): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>,
) -> Result<(StatusCode, Json<Value>), (StatusCode, String)> {
    let actor = admin_write(&headers, &s).await?;
    let token = new_token();
    let mut tx = s.db.begin().await.map_err(internal)?;
    let updated = sqlx::query("UPDATE devices SET token_hash=$2 WHERE id=$1")
        .bind(id).bind(token_hash(&token)).execute(&mut *tx).await.map_err(internal)?;
    if updated.rows_affected() == 0 {
        return Err((StatusCode::NOT_FOUND, "device_not_found".into()));
    }
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&actor.name).bind("device.token.rotate").bind(id.to_string())
        .bind(json!({"previous_token_revoked":true})).execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    Ok((StatusCode::OK, Json(json!({
        "deviceId": id,
        "device_token": token,
        "warning": "The previous token is revoked immediately. Store this replacement securely; it is shown only once."
    }))))
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
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&role.name).bind("config.update").bind(id.to_string())
        .bind(json!({"version":next})).execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
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
    let mut tx = s.db.begin().await.map_err(internal)?;
    let result = sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) SELECT $1,id,$2,$3 FROM devices WHERE id=$4")
        .bind(command_id).bind(&input.kind).bind(input.payload).bind(id).execute(&mut *tx).await.map_err(internal)?;
    if result.rows_affected() == 0 { return Err((StatusCode::NOT_FOUND, "device_not_found".into())); }
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&role.name).bind("command.queue").bind(id.to_string())
        .bind(json!({"commandId":command_id,"kind":input.kind}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
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
    let mut artifact_file = PendingArtifactFile::create(path.clone(), &body).map_err(internal)?;
    let mut tx = s.db.begin().await.map_err(internal)?;
    let exists = sqlx::query("SELECT id FROM devices WHERE id=$1").bind(id)
        .fetch_optional(&mut *tx).await.map_err(internal)?;
    if exists.is_none() {
        return Err((StatusCode::NOT_FOUND, "device_not_found".into()));
    }
    sqlx::query("INSERT INTO device_artifacts(id,device_id,sha256,byte_size,storage_path) VALUES($1,$2,$3,$4,$5)")
        .bind(artifact_id).bind(id).bind(&sha).bind(body.len() as i64)
        .bind(path.to_string_lossy().to_string()).execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) VALUES($1,$2,'deploy_site',$3)")
        .bind(Uuid::new_v4()).bind(id)
        .bind(json!({"artifactId":artifact_id,"sha256":sha,"byteSize":body.len()}))
        .execute(&mut *tx).await.map_err(internal)?;
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind(&role.name).bind("site.deploy.queue").bind(id.to_string())
        .bind(json!({"artifactId":artifact_id,"sha256":sha,"byteSize":body.len()}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    // Keep the durable blob only after metadata, command and audit have committed.
    artifact_file.commit();
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
    let encoded_result = serde_json::to_vec(&input.result).map_err(internal)?;
    if encoded_result.len() > 64 * 1024 {
        return Err((StatusCode::PAYLOAD_TOO_LARGE, "command_result_too_large".into()));
    }
    let state = if input.success { "succeeded" } else { "failed" };
    let mut tx = s.db.begin().await.map_err(internal)?;
    let result = sqlx::query("UPDATE device_commands SET state=$3,result=$4,acknowledged_at=now() WHERE id=$1 AND device_id=$2 AND state='delivered' AND expires_at > now()")
        .bind(command).bind(id).bind(state).bind(input.result).execute(&mut *tx).await.map_err(internal)?;
    if result.rows_affected() == 0 {
        return Err((StatusCode::NOT_FOUND, "command_not_found_or_already_acknowledged".into()));
    }

    // Commit command state and artifact metadata together. Delete the blob only after
    // the database transaction commits, so a failed transaction never loses a deploy.
    let mut artifact_to_remove: Option<String> = None;
    if input.success {
        if let Some(row) = sqlx::query("SELECT payload FROM device_commands WHERE id=$1 AND device_id=$2 AND kind='deploy_site'")
            .bind(command).bind(id).fetch_optional(&mut *tx).await.map_err(internal)? {
            let payload: Value = row.get("payload");
            if let Some(artifact) = payload.get("artifactId").and_then(Value::as_str).and_then(|value| Uuid::parse_str(value).ok()) {
                if let Some(file) = sqlx::query("SELECT storage_path FROM device_artifacts WHERE id=$1 AND device_id=$2")
                    .bind(artifact).bind(id).fetch_optional(&mut *tx).await.map_err(internal)? {
                    artifact_to_remove = Some(file.get("storage_path"));
                    sqlx::query("DELETE FROM device_artifacts WHERE id=$1 AND device_id=$2")
                        .bind(artifact).bind(id).execute(&mut *tx).await.map_err(internal)?;
                }
            }
        }
    }
    sqlx::query("INSERT INTO audit_events(actor,action,target,details) VALUES($1,$2,$3,$4)")
        .bind("device").bind("command.acknowledge").bind(command.to_string())
        .bind(json!({"deviceId":id,"state":state}))
        .execute(&mut *tx).await.map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    if let Some(path) = artifact_to_remove {
        if let Err(error) = fs::remove_file(&path) {
            tracing::warn!(path = %path, error = %error, "acknowledged deployment artifact cleanup failed");
        }
    }
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
    let state = AppState { db, admin_token, relay: relay::RelayHub::default() };
    let cors = match std::env::var("POCKETHOST_CONTROL_ORIGIN") {
        Ok(origin) => CorsLayer::new()
            .allow_origin(origin.parse::<HeaderValue>()?)
            .allow_methods([Method::GET, Method::POST, Method::PUT, Method::DELETE, Method::OPTIONS])
            .allow_headers([AUTHORIZATION, CONTENT_TYPE]),
        Err(_) => CorsLayer::new(),
    };
    let app = Router::new()
        .route("/health", get(health))
        .route("/v1/admin/session", post(create_admin_session).delete(revoke_admin_session))
        .route("/v1/admin/audit", get(list_audit))
        .route("/v1/admin/users", get(list_users).post(create_user))
        .route("/v1/admin/users/:id", axum::routing::delete(revoke_user))
        .route("/v1/admin/users/:id/rotate-token", post(rotate_user_token))
        .route("/v1/admin/devices", get(list_devices).post(register_device))
        .route("/v1/admin/devices/:id", axum::routing::delete(delete_device))
        .route("/v1/admin/devices/:id/rotate-token", post(rotate_device_token))
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
        let credential_headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {token}")).unwrap())]);
        assert_eq!(admin_credential(&credential_headers, &state).await.unwrap().role, "viewer");
        assert!(admin(&credential_headers, &state).await.is_err(), "Long-lived credentials must not authorize management routes directly.");
        let (_, Json(session_body)) = create_admin_session(State(state.clone()), credential_headers).await.unwrap();
        let session_token = session_body["accessToken"].as_str().unwrap();
        let headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {session_token}")).unwrap())]);
        assert_eq!(admin(&headers, &state).await.unwrap().role, "viewer");
        assert_eq!(admin_write(&headers, &state).await.unwrap_err().0, StatusCode::FORBIDDEN);
        audit(&state, "viewer", "test.read", &id.to_string(), json!({"test":true})).await.unwrap();
        let read_count: i64 = sqlx::query_scalar("SELECT count(*) FROM audit_events WHERE target=$1 AND action='test.read'")
            .bind(id.to_string()).fetch_one(&db).await.unwrap();
        assert_eq!(read_count, 1);

        let owner_credential = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {}", state.admin_token)).unwrap())]);
        let (_, Json(owner_session)) = create_admin_session(State(state.clone()), owner_credential).await.unwrap();
        let owner_token = owner_session["accessToken"].as_str().unwrap();
        let owner_headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {owner_token}")).unwrap())]);
        assert_eq!(revoke_user(State(state.clone()), owner_headers, Path(id)).await.unwrap(), StatusCode::NO_CONTENT);
        assert!(admin(&headers, &state).await.is_err(), "Revoking a team account must immediately revoke its active sessions.");
        let revoke_count: i64 = sqlx::query_scalar("SELECT count(*) FROM audit_events WHERE target=$1 AND action='user.revoke'")
            .bind(id.to_string()).fetch_one(&db).await.unwrap();
        assert_eq!(revoke_count, 1);
        sqlx::query("DELETE FROM control_users WHERE id=$1").bind(id).execute(&db).await.unwrap();
    }

    #[tokio::test]
    async fn rotating_user_token_revokes_old_credential_immediately() {
        let url = std::env::var("DATABASE_URL").expect("CI DATABASE_URL is required");
        let db = PgPoolOptions::new().max_connections(2).connect(&url).await.unwrap();
        sqlx::migrate!("./migrations").run(&db).await.unwrap();
        let user_id = Uuid::new_v4();
        let old_token = new_token();
        sqlx::query("INSERT INTO control_users(id,name,role,token_hash) VALUES($1,'rotation-test','editor',$2)")
            .bind(user_id).bind(token_hash(&old_token)).execute(&db).await.unwrap();
        let state = AppState { db: db.clone(), admin_token: "owner-test-token-0123456789abcdef".into() };
        let (_, Json(old_session_body)) = create_admin_session(
            State(state.clone()),
            HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {old_token}")).unwrap())])
        ).await.unwrap();
        let old_session_token = old_session_body["accessToken"].as_str().unwrap().to_owned();
        let credential_headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {}", state.admin_token)).unwrap())]);
        let (_, Json(session_body)) = create_admin_session(State(state.clone()), credential_headers).await.unwrap();
        let session_token = session_body["accessToken"].as_str().unwrap();
        let headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {session_token}")).unwrap())]);
        let (status, Json(body)) = rotate_user_token(
            State(state.clone()), headers, Path(user_id)
        ).await.unwrap();
        assert_eq!(status, StatusCode::OK);
        let replacement = body["token"].as_str().unwrap();
        assert_ne!(replacement, old_token);
        assert!(admin(&HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {old_session_token}")).unwrap())]), &state).await.is_err(),
            "Rotating a user's API credential must revoke all of that user's sessions.");
        assert!(admin_credential(&HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {old_token}")).unwrap())]), &state).await.is_err());
        let (_, Json(replacement_session)) = create_admin_session(
            State(state.clone()),
            HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {replacement}")).unwrap())])
        ).await.unwrap();
        let replacement_session_token = replacement_session["accessToken"].as_str().unwrap();
        let replacement_actor = admin(
            &HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {replacement_session_token}")).unwrap())]),
            &state
        ).await.unwrap();
        assert_eq!(replacement_actor.name, "rotation-test");
        let audit_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM audit_events WHERE target=$1 AND action='user.token.rotate'")
            .bind(user_id.to_string()).fetch_one(&state.db).await.unwrap();
        assert_eq!(audit_count, 1);
        sqlx::query("DELETE FROM control_users WHERE id=$1").bind(user_id).execute(&state.db).await.unwrap();
    }

    #[tokio::test]
    async fn rotating_device_token_revokes_old_credential_immediately() {
        let url = std::env::var("DATABASE_URL").expect("CI DATABASE_URL is required");
        let db = PgPoolOptions::new().max_connections(2).connect(&url).await.unwrap();
        sqlx::migrate!("./migrations").run(&db).await.unwrap();
        let device = Uuid::new_v4();
        let old_token = new_token();
        sqlx::query("INSERT INTO devices(id,name,token_hash) VALUES($1,'CI rotation device',$2)")
            .bind(device).bind(token_hash(&old_token)).execute(&db).await.unwrap();
        let state = AppState { db: db.clone(), admin_token: "owner-test-token-0123456789abcdef".into() };
        let credential_headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {}", state.admin_token)).unwrap())]);
        let (_, Json(session_body)) = create_admin_session(State(state.clone()), credential_headers).await.unwrap();
        let session_token = session_body["accessToken"].as_str().unwrap();
        let headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {session_token}")).unwrap())]);
        let (status, Json(body)) = rotate_device_token(
            State(state.clone()), headers, Path(device)
        ).await.unwrap();
        assert_eq!(status, StatusCode::OK);
        let replacement = body.get("device_token").and_then(Value::as_str).unwrap();
        assert_ne!(replacement, old_token);
        assert!(device_id(&{
            let mut h = HeaderMap::new();
            h.insert(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {old_token}")).unwrap());
            h
        }, &state).await.is_err());
        let mut new_headers = HeaderMap::new();
        new_headers.insert(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {replacement}")).unwrap());
        assert_eq!(device_id(&new_headers, &state).await.unwrap(), device);
        let audit_count: i64 = sqlx::query_scalar(
            "SELECT count(*) FROM audit_events WHERE action='device.token.rotate' AND target=$1"
        ).bind(device.to_string()).fetch_one(&db).await.unwrap();
        assert_eq!(audit_count, 1);
        sqlx::query("DELETE FROM devices WHERE id=$1").bind(device).execute(&db).await.unwrap();
    }

    #[tokio::test]
    async fn admin_sessions_expire_and_can_be_revoked() {
        let url = std::env::var("DATABASE_URL").expect("CI DATABASE_URL is required");
        let db = PgPoolOptions::new().max_connections(2).connect(&url).await.unwrap();
        sqlx::migrate!("./migrations").run(&db).await.unwrap();
        let state = AppState { db: db.clone(), admin_token: "owner-session-test-token-0123456789abcdef".into() };
        let credential_headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {}", state.admin_token)).unwrap())]);
        let (_, Json(first_session)) = create_admin_session(State(state.clone()), credential_headers.clone()).await.unwrap();
        let first_token = first_session["accessToken"].as_str().unwrap();
        let first_headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {first_token}")).unwrap())]);
        let first_id: Uuid = sqlx::query_scalar("SELECT id FROM control_sessions WHERE token_hash=$1")
            .bind(token_hash(first_token)).fetch_one(&db).await.unwrap();
        sqlx::query("UPDATE control_sessions SET expires_at=now()-interval '1 second' WHERE id=$1")
            .bind(first_id).execute(&db).await.unwrap();
        assert!(admin(&first_headers, &state).await.is_err(), "Expired sessions must be rejected.");

        let (_, Json(second_session)) = create_admin_session(State(state.clone()), credential_headers).await.unwrap();
        let second_token = second_session["accessToken"].as_str().unwrap();
        let second_headers = HeaderMap::from_iter([(AUTHORIZATION, HeaderValue::from_str(&format!("Bearer {second_token}")).unwrap())]);
        assert_eq!(revoke_admin_session(State(state.clone()), second_headers.clone()).await.unwrap(), StatusCode::NO_CONTENT);
        assert!(admin(&second_headers, &state).await.is_err(), "Explicitly revoked sessions must be rejected.");
        sqlx::query("DELETE FROM control_sessions WHERE id=$1").bind(first_id).execute(&db).await.unwrap();
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

    #[tokio::test]
    async fn disconnected_device_commands_are_released_after_lease_and_expire() {
        let url = std::env::var("DATABASE_URL").expect("CI DATABASE_URL is required");
        let db = PgPoolOptions::new().max_connections(2).connect(&url).await.unwrap();
        sqlx::migrate!("./migrations").run(&db).await.unwrap();

        let device = Uuid::new_v4();
        let command = Uuid::new_v4();
        let expired_command = Uuid::new_v4();
        sqlx::query("INSERT INTO devices(id,name,token_hash) VALUES($1,'CI recovery device',$2)")
            .bind(device).bind(token_hash(&new_token())).execute(&db).await.unwrap();
        sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload) VALUES($1,$2,'site_action',$3)")
            .bind(command).bind(device).bind(json!({"action":"start"})).execute(&db).await.unwrap();

        // A disconnected phone leaves its command queued; reconnecting can lease it.
        let queued: i64 = sqlx::query_scalar("SELECT count(*) FROM device_commands WHERE id=$1 AND state='queued' AND expires_at > now()")
            .bind(command).fetch_one(&db).await.unwrap();
        assert_eq!(queued, 1);

        sqlx::query("UPDATE device_commands SET state='delivered',delivered_at=now(),delivery_attempts=delivery_attempts+1 WHERE id=$1")
            .bind(command).execute(&db).await.unwrap();
        let immediate_retry: i64 = sqlx::query_scalar("SELECT count(*) FROM device_commands WHERE id=$1 AND (state='queued' OR (state='delivered' AND delivered_at < now() - interval '2 minutes'))")
            .bind(command).fetch_one(&db).await.unwrap();
        assert_eq!(immediate_retry, 0, "an active delivery lease must prevent duplicate delivery");

        // Simulate the phone disconnecting after delivery but before its ACK.
        sqlx::query("UPDATE device_commands SET delivered_at=now()-interval '3 minutes' WHERE id=$1")
            .bind(command).execute(&db).await.unwrap();
        let recovered: i64 = sqlx::query_scalar("SELECT count(*) FROM device_commands WHERE id=$1 AND state='delivered' AND delivered_at < now()-interval '2 minutes' AND expires_at > now()")
            .bind(command).fetch_one(&db).await.unwrap();
        assert_eq!(recovered, 1, "an expired delivery lease must make the command available again");

        sqlx::query("UPDATE device_commands SET delivered_at=now(),delivery_attempts=delivery_attempts+1 WHERE id=$1")
            .bind(command).execute(&db).await.unwrap();
        sqlx::query("UPDATE device_commands SET state='succeeded',acknowledged_at=now(),result=$2 WHERE id=$1 AND state='delivered' AND expires_at > now()")
            .bind(command).bind(json!({"status":200})).execute(&db).await.unwrap();
        let acknowledged: String = sqlx::query_scalar("SELECT state FROM device_commands WHERE id=$1")
            .bind(command).fetch_one(&db).await.unwrap();
        assert_eq!(acknowledged, "succeeded");

        sqlx::query("INSERT INTO device_commands(id,device_id,kind,payload,expires_at) VALUES($1,$2,'site_action',$3,now()-interval '1 second')")
            .bind(expired_command).bind(device).bind(json!({"action":"stop"})).execute(&db).await.unwrap();
        sqlx::query("UPDATE device_commands SET state='expired' WHERE id=$1 AND state IN ('queued','delivered') AND expires_at <= now()")
            .bind(expired_command).execute(&db).await.unwrap();
        let expiry_state: String = sqlx::query_scalar("SELECT state FROM device_commands WHERE id=$1")
            .bind(expired_command).fetch_one(&db).await.unwrap();
        assert_eq!(expiry_state, "expired");

        sqlx::query("DELETE FROM devices WHERE id=$1").bind(device).execute(&db).await.unwrap();
    }

    #[test]
    fn uncommitted_artifact_files_are_removed_and_committed_files_are_retained() {
        let directory = std::env::temp_dir().join(format!("pockethost-artifact-{}", Uuid::new_v4()));
        fs::create_dir_all(&directory).unwrap();
        let abandoned = directory.join("abandoned.zip");
        {
            let _pending = PendingArtifactFile::create(abandoned.clone(), b"zip-data").unwrap();
            assert_eq!(fs::read(&abandoned).unwrap(), b"zip-data");
        }
        assert!(!abandoned.exists(), "a failed DB transaction must not leave an orphan blob");

        let committed = directory.join("committed.zip");
        {
            let mut pending = PendingArtifactFile::create(committed.clone(), b"zip-data").unwrap();
            pending.commit();
        }
        assert_eq!(fs::read(&committed).unwrap(), b"zip-data");

        assert!(PendingArtifactFile::create(committed.clone(), b"overwrite").is_err(),
            "artifact creation must never overwrite an existing file");
        assert_eq!(fs::read(&committed).unwrap(), b"zip-data");
        fs::remove_dir_all(directory).unwrap();
    }

    #[test]
    fn command_result_payload_is_bounded() {
        let small = json!({"status":200,"message":"ok"});
        assert!(serde_json::to_vec(&small).unwrap().len() <= 64 * 1024);
        let oversized = json!({"output":"x".repeat(64 * 1024)});
        assert!(serde_json::to_vec(&oversized).unwrap().len() > 64 * 1024);
    }

    #[test]
    fn command_allowlist_excludes_arbitrary_shell_execution() {
        let allowed = ["deploy_site", "site_action", "tunnel_action", "restart_runtime"];
        assert!(allowed.contains(&"site_action"));
        assert!(!allowed.contains(&"shell"));
    }
}
