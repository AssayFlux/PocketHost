#!/usr/bin/env bash
set -euo pipefail

network="pockethost-proxy-test-$RANDOM-$$"
echo_name="pockethost-proxy-echo-$RANDOM-$$"
caddy_name="pockethost-proxy-caddy-$RANDOM-$$"
tmp_dir="$(mktemp -d)"
cleanup() {
  docker rm -f "$caddy_name" "$echo_name" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

docker network create --subnet 172.31.240.0/24 "$network" >/dev/null
cat > "$tmp_dir/Caddyfile" <<'CADDY'
{
    import /etc/caddy/trusted-proxy.caddy
}
:8080 {
    reverse_proxy echo:8081 {
        header_up X-Verified-Client-IP {http.request.client_ip}
    }
}
CADDY

docker run -d --name "$echo_name" --network "$network" --network-alias echo \
  --ip 172.31.240.13 \
  -v "$PWD/tests/proxy/echo_server.py:/echo_server.py:ro" \
  python:3.12-alpine python /echo_server.py >/dev/null

docker run -d --name "$caddy_name" --network "$network" --network-alias caddy --ip 172.31.240.10 \
  -e POCKETHOST_CLOUDFLARED_IP=172.31.240.11 \
  -v "$tmp_dir/Caddyfile:/etc/caddy/Caddyfile:ro" \
  -v "$PWD/control-plane/trusted-proxy.caddy:/etc/caddy/trusted-proxy.caddy:ro" \
  caddy:2 caddy run --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null

ready=false
for _ in $(seq 1 30); do
  if docker run --rm --network "$network" --ip 172.31.240.11 curlimages/curl:8.12.1 \
      -fsS http://caddy:8080/ >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  docker logs "$caddy_name"
  echo "Caddy did not become ready" >&2
  exit 1
fi

trusted="$(docker run --rm --network "$network" --ip 172.31.240.11 curlimages/curl:8.12.1 \
  -fsS -H 'CF-Connecting-IP: 203.0.113.77' http://caddy:8080/)"
untrusted="$(docker run --rm --network "$network" --ip 172.31.240.12 curlimages/curl:8.12.1 \
  -fsS -H 'CF-Connecting-IP: 198.51.100.99' http://caddy:8080/)"

[[ "$trusted" == "203.0.113.77" ]] || {
  echo "Trusted Cloudflared peer was not mapped to its verified visitor IP: $trusted" >&2
  exit 1
}
[[ "$untrusted" == "172.31.240.12" ]] || {
  echo "Untrusted peer spoofed visitor IP; expected socket peer, got: $untrusted" >&2
  exit 1
}

echo "PASS: trusted Cloudflared metadata is accepted; untrusted forwarding headers are ignored."
