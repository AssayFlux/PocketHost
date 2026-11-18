use axum::{
    extract::{DefaultBodyLimit, Path, State},
    routing::get,
    Router,
};
use instant_acme::{
    Account, AccountCredentials, AuthorizationStatus, ChallengeType, Identifier,
    NewAccount, NewOrder, OrderStatus, RetryPolicy,
};
use serde::Serialize;
use serde_json::Value;
use std::{
    collections::HashMap,
    net::{IpAddr, SocketAddr},
    sync::Arc,
    time::Duration,
};
use tokio::net::TcpListener;

pub struct AcmeRequest {
    pub bind_address: String,
    pub http_port: u16,
    pub domains: Vec<String>,
    pub contact_email: String,
    pub terms_accepted: bool,
    pub directory_url: Option<String>,
    pub account_credentials: Option<Value>,
}

#[derive(Debug, Serialize)]
pub struct AcmeCertificateBundle {
    pub tls_certificate_pem: String,
    pub tls_private_key_pem: String,
    pub acme_account_credentials: Value,
}

async fn challenge_response(
    State(challenges): State<Arc<HashMap<String, String>>>,
    Path(token): Path<String>,
) -> Result<String, axum::http::StatusCode> {
    challenges.get(&token).cloned().ok_or(axum::http::StatusCode::NOT_FOUND)
}

/// Obtain or renew a certificate with HTTP-01 validation.
///
/// The caller must ensure the configured public HTTP port is reachable from the
/// Internet as TCP/80 (normally by forwarding public port 80 to the Android
/// listener port). Account credentials are returned to the caller so Android can
/// persist them inside its Keystore-encrypted node configuration.
pub async fn issue(request: AcmeRequest) -> Result<AcmeCertificateBundle, String> {
    if !request.terms_accepted {
        return Err("Let's Encrypt Terms of Service must be accepted by the owner before certificate issuance.".into());
    }
    if !request.contact_email.contains('@') || request.contact_email.len() > 254 {
        return Err("A valid ACME contact email is required.".into());
    }
    if request.domains.is_empty() || request.domains.len() > 100 {
        return Err("ACME requires between 1 and 100 configured hostnames.".into());
    }
    if request.domains.iter().any(|domain| {
        domain.is_empty() || domain.len() > 253 || domain.contains('*')
            || !domain.is_ascii()
    }) {
        return Err("ACME HTTP-01 supports valid non-wildcard DNS hostnames only.".into());
    }

    let bind_ip: IpAddr = request.bind_address.parse()
        .map_err(|_| "ACME bind address must be a numeric IP address")?;
    if bind_ip.is_loopback() {
        return Err("ACME HTTP-01 requires a non-loopback bind address and public port-80 forwarding.".into());
    }

    let (account, account_credentials) = if let Some(value) = request.account_credentials {
        let credentials: AccountCredentials = serde_json::from_value(value)
            .map_err(|error| format!("Stored ACME account credentials are invalid: {error}"))?;
        let serialized = serde_json::to_value(&credentials)
            .map_err(|error| format!("Could not serialize ACME account credentials: {error}"))?;
        let account = Account::builder()
            .map_err(|error| format!("Could not initialize ACME client: {error}"))?
            .from_credentials(credentials)
            .await
            .map_err(|error| format!("Could not restore ACME account: {error}"))?;
        (account, serialized)
    } else {
        let directory_url = request.directory_url.unwrap_or_else(|| {
            "https://acme-v02.api.letsencrypt.org/directory".to_string()
        });
        let contact = format!("mailto:{}", request.contact_email);
        let contacts = [contact.as_str()];
        let new_account = NewAccount {
            contact: &contacts,
            terms_of_service_agreed: true,
            only_return_existing: false,
        };
        let (account, credentials) = Account::builder()
            .map_err(|error| format!("Could not initialize ACME client: {error}"))?
            .create(&new_account, directory_url, None)
            .await
            .map_err(|error| format!("Could not create Let's Encrypt account: {error}"))?;
        let serialized = serde_json::to_value(credentials)
            .map_err(|error| format!("Could not serialize ACME account credentials: {error}"))?;
        (account, serialized)
    };

    let identifiers: Vec<Identifier> = request.domains.iter()
        .cloned()
        .map(Identifier::Dns)
        .collect();
    let mut order = account.new_order(&NewOrder::new(&identifiers)).await
        .map_err(|error| format!("Could not create ACME certificate order: {error}"))?;

    let mut challenges = HashMap::new();
    {
        let mut authorizations = order.authorizations();
        while let Some(authorization) = authorizations.next().await {
            let mut authorization = authorization
                .map_err(|error| format!("Could not read ACME authorization: {error}"))?;
            match authorization.status {
                AuthorizationStatus::Valid => continue,
                AuthorizationStatus::Pending => {}
                _ => return Err(format!("ACME authorization is not pending or valid: {:?}", authorization.status)),
            }
            let challenge = authorization.challenge(ChallengeType::Http01)
                .ok_or_else(|| "The ACME server did not offer an HTTP-01 challenge.".to_string())?;
            if challenge.token.is_empty() {
                return Err("The ACME server returned an empty HTTP-01 challenge token.".into());
            }
            challenges.insert(
                challenge.token.clone(),
                challenge.key_authorization().as_str().to_string(),
            );
        }
    }

    let challenge_server = if challenges.is_empty() {
        None
    } else {
        let address = SocketAddr::new(bind_ip, request.http_port);
        let listener = TcpListener::bind(address).await
            .map_err(|error| format!("Could not bind temporary ACME HTTP-01 listener at {address}: {error}"))?;
        let state = Arc::new(challenges);
        let app = Router::new()
            .route(
                "/.well-known/acme-challenge/:token",
                get(challenge_response).layer(DefaultBodyLimit::max(1024)),
            )
            .with_state(state);
        Some(tokio::spawn(async move {
            let _ = axum::serve(listener, app).await;
        }))
    };

    let readiness = async {
        let mut authorizations = order.authorizations();
        while let Some(authorization) = futures_util::StreamExt::next(&mut authorizations).await {
            let mut authorization = authorization
                .map_err(|error| format!("Could not refresh ACME authorization: {error}"))?;
            if authorization.status == AuthorizationStatus::Valid {
                continue;
            }
            let mut challenge = authorization.challenge(ChallengeType::Http01)
                .ok_or_else(|| "The ACME server did not offer an HTTP-01 challenge.".to_string())?;
            challenge.set_ready().await
                .map_err(|error| format!("Could not activate HTTP-01 challenge: {error}"))?;
        }
        let status = order.poll_ready(
            &RetryPolicy::new()
                .initial_delay(Duration::from_millis(500))
                .timeout(Duration::from_secs(180))
        ).await.map_err(|error| format!("ACME authorization validation failed: {error}"))?;
        match status {
            OrderStatus::Ready | OrderStatus::Valid => Ok(()),
            other => Err(format!("ACME order did not become ready: {other:?}")),
        }
    }.await;

    if let Some(server) = challenge_server {
        server.abort();
        let _ = server.await;
    }
    readiness?;

    let private_key_pem = order.finalize().await
        .map_err(|error| format!("Could not finalize ACME certificate order: {error}"))?;
    let certificate_pem = order.poll_certificate(
        &RetryPolicy::new()
            .initial_delay(Duration::from_millis(500))
            .timeout(Duration::from_secs(180))
    ).await.map_err(|error| format!("Could not retrieve issued ACME certificate: {error}"))?;

    Ok(AcmeCertificateBundle {
        tls_certificate_pem: certificate_pem,
        tls_private_key_pem: private_key_pem,
        acme_account_credentials: account_credentials,
    })
}


#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn http01_handler_serves_only_registered_challenge_values() {
        let challenges = Arc::new(HashMap::from([
            ("token-123".to_string(), "token-123.account-thumbprint".to_string())
        ]));
        let response = challenge_response(
            State(challenges.clone()),
            Path("token-123".to_string())
        ).await.unwrap();
        assert_eq!(response, "token-123.account-thumbprint");
        assert_eq!(
            challenge_response(State(challenges), Path("unknown".to_string())).await,
            Err(axum::http::StatusCode::NOT_FOUND)
        );
    }

    #[tokio::test]
    async fn acme_http01_refuses_loopback_bind_before_network_requests() {
        let result = issue(AcmeRequest {
            bind_address: "127.0.0.1".into(),
            http_port: 8082,
            domains: vec!["example.test".into()],
            contact_email: "owner@example.test".into(),
            terms_accepted: true,
            directory_url: None,
            account_credentials: None,
        }).await;
        assert!(result.unwrap_err().contains("non-loopback"));
    }

    #[tokio::test]
    async fn acme_requires_explicit_terms_acceptance() {
        let result = issue(AcmeRequest {
            bind_address: "0.0.0.0".into(),
            http_port: 8082,
            domains: vec!["example.test".into()],
            contact_email: "owner@example.test".into(),
            terms_accepted: false,
            directory_url: None,
            account_credentials: None,
        }).await;
        assert!(result.unwrap_err().contains("Terms of Service"));
    }
}
