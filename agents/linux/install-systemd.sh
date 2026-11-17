#!/usr/bin/env sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this installer as root (for example: sudo $0)." >&2
  exit 1
fi

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 -m venv /opt/pockethost-agent
/opt/pockethost-agent/bin/pip install --upgrade "$ROOT_DIR"
if [ ! -e /etc/pockethost-agent.env ]; then
  cat > /etc/pockethost-agent.env <<'EOF'
# HTTPS control-plane endpoint. Leave blank for local inbox preview.
POCKETHOST_CONTROL_ENDPOINT=
# One-time-issued device token. Never commit or share this file.
POCKETHOST_CONTROL_TOKEN=
# Comma-separated namespace IDs explicitly assigned to this agent.
POCKETHOST_ALLOWED_NAMESPACES=
EOF
  chmod 0600 /etc/pockethost-agent.env
fi
install -D -m 0755 "$ROOT_DIR/run-agent-service.sh" /opt/pockethost-agent/bin/pockethost-agent-service
install -D -m 0644 "$ROOT_DIR/pockethost-agent.service" /etc/systemd/system/pockethost-agent.service
systemctl daemon-reload
systemctl enable --now pockethost-agent.service
printf '%s\n' "Pocket Host Linux agent service installed."
printf '%s\n' "Agent state: /var/lib/pockethost-agent"
printf '%s\n' "Configure /etc/pockethost-agent.env with an HTTPS endpoint and device token to enable remote jobs."
printf '%s\n' "Without those values, the service remains in local-inbox preview mode."
