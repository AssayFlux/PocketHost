#!/usr/bin/env bash
set -euo pipefail

: "${CONSOLE_DOMAIN:?Set CONSOLE_DOMAIN, e.g. console.example.com}"
: "${API_DOMAIN:?Set API_DOMAIN, e.g. api.example.com}"
: "${VISITOR_DOMAIN:?Set VISITOR_DOMAIN to a DNS-verified and enabled site hostname}"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
resolve() {
  local host="$1"
  getent ahosts "$host" >/dev/null 2>&1 || fail "DNS does not resolve $host"
  printf 'PASS: DNS resolves %s\n' "$host"
}

resolve "$CONSOLE_DOMAIN"
resolve "$API_DOMAIN"
resolve "$VISITOR_DOMAIN"

check_address_families() {
  local host="$1" path="$2" family_flag="$3" family_label="$4" resolver="$5"
  if getent "$resolver" "$host" >/dev/null 2>&1; then
    curl "$family_flag" --fail --silent --show-error --output /dev/null "https://$host$path" ||
      fail "$host publishes $family DNS but HTTPS connectivity failed"
    printf 'PASS: %s HTTPS connectivity for %s\n' "$family_label" "$host"
  else
    printf 'SKIP: %s has no %s address record\n' "$host" "$family_label"
  fi
}

for host_path in "$CONSOLE_DOMAIN|/" "$API_DOMAIN|/health" "$VISITOR_DOMAIN|/"; do
  host="${host_path%%|*}"
  path="${host_path#*|}"
  check_address_families "$host" "$path" -4 IPv4 ahostsv4
  check_address_families "$host" "$path" -6 IPv6 ahostsv6
done

api_body="$(curl --fail --silent --show-error "https://$API_DOMAIN/health")" ||
  fail "Control-plane HTTPS health request failed"
grep -q '"status":"ok"' <<<"$api_body" ||
  fail "Control-plane health response did not contain status=ok"
printf 'PASS: API HTTPS health and certificate validation\n'

console_status="$(curl --location --silent --show-error --output /dev/null --write-out '%{http_code}' "https://$CONSOLE_DOMAIN/")" ||
  fail "Console HTTPS request failed"
[[ "$console_status" == "200" ]] || fail "Console returned HTTP $console_status"
printf 'PASS: Console HTTPS returns HTTP 200\n'

authorized_headers="$(curl --silent --show-error --dump-header - --output /dev/null \
  --request OPTIONS "https://$API_DOMAIN/v1/admin/session" \
  --header "Origin: https://$CONSOLE_DOMAIN" \
  --header "Access-Control-Request-Method: POST" \
  --header "Access-Control-Request-Headers: authorization,content-type")" ||
  fail "Authorized CORS preflight request failed"
grep -Fqi "access-control-allow-origin: https://$CONSOLE_DOMAIN" <<<"$authorized_headers" ||
  fail "Configured console origin was not authorized by CORS"
printf 'PASS: Authorized CORS preflight\n'

unauthorized_headers="$(curl --silent --show-error --dump-header - --output /dev/null \
  --request OPTIONS "https://$API_DOMAIN/v1/admin/session" \
  --header "Origin: https://not-authorized.invalid" \
  --header "Access-Control-Request-Method: POST" \
  --header "Access-Control-Request-Headers: authorization,content-type")" ||
  fail "Unauthorized CORS preflight transport failed"
if grep -qi '^access-control-allow-origin:' <<<"$unauthorized_headers"; then
  fail "An unlisted browser origin received Access-Control-Allow-Origin"
fi
printf 'PASS: Unlisted CORS origin receives no allow-origin header\n'

visitor_status="$(curl --location --silent --show-error --output /dev/null --write-out '%{http_code}' "https://$VISITOR_DOMAIN/")" ||
  fail "Visitor HTTPS request failed (DNS, TLS, relay or site may be unavailable)"
[[ "$visitor_status" =~ ^2[0-9][0-9]$ ]] ||
  fail "Visitor hostname returned HTTP $visitor_status rather than a successful 2xx response"
printf 'PASS: External visitor hostname returns HTTP %s\n' "$visitor_status"

printf '\nAll configured public deployment checks passed. This does not test Android reboot or mobile-network recovery.\n'
