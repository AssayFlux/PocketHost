# Native Relay Node configuration (Alpha engineering format)

This document describes the temporary import format used by the native Android Relay service while the in-app pairing and hostname setup flows are being implemented. It is not the final owner onboarding experience.

Import the JSON through **Settings → Network → Native AssayFlux Relay Node → Import node config**. Pocket Host encrypts the complete JSON using an Android Keystore AES-GCM key before storing it. The Rust engine keeps only host credential SHA-256 verifiers in memory.

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

Put the raw `TOKEN` into the host phone's existing **Dedicated Relay host credential** field and configure its Relay HTTPS endpoint as `https://your-relay-hostname`. Put only the SHA-256 digest (64 hex characters, without spaces or filename) in the Relay Node's `credential_sha256` field. Do not paste the raw token into the node configuration.

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

This import flow is an engineering bridge, not final pairing. The app does not yet issue credentials, generate node identity, automate ACME, persist route/revocation state in a database, rotate credentials, or renew certificates. DNS TXT proof is rechecked at node startup, not continuously. Do not claim v0.1.0 acceptance until those gaps and the physical/public-network tests are closed.
