# Native Relay Node configuration (Alpha engineering format)

This document describes the temporary import format used by the native Android Relay service while the in-app pairing and hostname setup flows are being implemented. It is not the final owner onboarding experience.

Import the initial node/route JSON through **Settings → Network → Native AssayFlux Relay Node → Import node config**. Pocket Host encrypts the complete configuration with Android Keystore AES-GCM and stores it in transactional SQLite. The Rust engine keeps only host credential SHA-256 verifiers in memory. After import, use the owner credential controls to issue or rotate a 256-bit host token for a configured host UUID; the raw token is shown once for transfer to the host phone. Revoking a host removes its routes and writes a durable revocation tombstone.

## Required shape

```json
{
  "bind_address": "0.0.0.0",
  "http_port": 8082,
  "https_port": 8444,
  "dns_resolver": "1.1.1.1:53",
  "tls_certificate_pem": "-----BEGIN CERTIFICATE-----\n...full chain...\n-----END CERTIFICATE-----",
  "tls_private_key_pem": "-----BEGIN PRIVATE KEY-----\n...PKCS#8 key...\n-----END PRIVATE KEY-----",
  "hosts": [
    {
      "device_id": "00000000-0000-4000-8000-000000000001",
      "credential_sha256": "<64 lowercase hex characters>"
    }
  ],
  "routes": [
    {
      "hostname": "www.example.com",
      "site_id": "your-site-id",
      "device_id": "00000000-0000-4000-8000-000000000001",
      "dns_txt_name": "_pockethost-verify.www.example.com",
      "dns_txt_value": "pockethost-verification-<at-least-24-random-characters>"
    }
  ]
}
```

The UUID is the Relay's local host identity reference; it is not a fleet device ID. Use the same UUID in the host entry and its routes. The `site_id` must match the site's ID in the host phone's Pocket Host site registry.

## Credential preparation

Generate a high-entropy host credential on a trusted computer:

```sh
TOKEN="$(openssl rand -hex 32)"
printf '%s\n' "$TOKEN"
printf '%s' "$TOKEN" | sha256sum
```

For an existing engineering config, the owner UI can generate/rotate the credential and updates the host's `credential_sha256` verifier automatically. Transfer the raw token to the host phone and save it in the **Dedicated Relay host credential** field with endpoint `https://your-relay-hostname`. Never put the raw token in the node configuration. The manual OpenSSL procedure remains available only for externally provisioned test credentials.

## DNS ownership proof

For every route, generate a distinct random value of at least 24 characters and publish it as a TXT record at the exact `dns_txt_name`, with the exact `dns_txt_value` as its TXT content. Wait for the record to become visible through the configured DNS resolver before starting the node. The native engine queries the TXT record during startup and refuses to activate any route without an exact matching answer. It does not follow CNAME indirection for the challenge name.

## TLS and network

- The certificate must cover every configured hostname and include the full certificate chain.
- The private key must be an unencrypted PKCS#8 PEM key matching the certificate.
- This Alpha engineering format accepts an already-issued certificate; automatic ACME issuance, renewal and expiry monitoring are not yet implemented.
- When TLS certificate and key are present, the engine can bind the configured interface. Without both values, HTTP is loopback-only.
- Forward public TCP 80 to Android port 8082 and public TCP 443 to Android port 8444. The HTTP listener redirects configured hostnames to HTTPS.
- CGNAT without a public IPv6 path or inbound edge forwarding cannot accept unsolicited public visitor traffic. Cloudflare Tunnel remains a separate fallback.

Never publish the JSON configuration. It contains the TLS private key and DNS challenge material. Do not include the raw host token. If a token or private key is exposed, rotate/revoke it and replace the certificate/key as appropriate.

## Current limitations

This import flow is still an engineering provisioning bridge, not the final pairing ceremony. Node identity, owner-issued credential creation/rotation/revocation, encrypted SQLite configuration persistence and revocation tombstones are implemented. Short-lived single-use pairing invites, automatic ACME issuance/renewal, certificate expiry monitoring and continuous DNS TXT proof lifecycle remain incomplete. DNS TXT proof is rechecked at node startup, not continuously. Do not claim v0.1.0 acceptance until those gaps and the physical/public-network tests are closed.
