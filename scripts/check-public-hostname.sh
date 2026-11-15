#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <public-hostname> [expected-content-substring]" >&2
  exit 2
fi

host="$1"
expected="${2:-}"
if [[ ! "$host" =~ ^[A-Za-z0-9.-]+$ || "$host" == .* || "$host" == *..* ]]; then
  echo "Invalid hostname" >&2
  exit 2
fi

echo "DNS:"
if command -v dig >/dev/null 2>&1; then
  dig +short "$host"
else
  getent ahosts "$host"
fi

echo "TLS certificate:"
echo | openssl s_client -servername "$host" -connect "$host:443" -verify_return_error 2>&1 \
  | grep -E "Verify return code|Verification:|subject=|issuer=" || true

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
status="$(curl --silent --show-error --location --max-redirs 3 \
  --connect-timeout 10 --max-time 30 --output "$tmp" --write-out '%{http_code}' "https://$host/")"
echo "HTTP status: $status"
[[ "$status" == "200" ]] || { echo "Expected HTTP 200 from public hostname" >&2; exit 1; }
if [[ -n "$expected" ]]; then
  grep -Fq -- "$expected" "$tmp" || { echo "Expected content was not found" >&2; exit 1; }
fi
echo "Public hostname TLS and content check passed."
