#!/usr/bin/env sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this installer as root (for example: sudo $0)." >&2
  exit 1
fi

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 -m venv /opt/pockethost-agent
/opt/pockethost-agent/bin/pip install --upgrade "$ROOT_DIR"
install -D -m 0644 "$ROOT_DIR/pockethost-agent.service" /etc/systemd/system/pockethost-agent.service
systemctl daemon-reload
systemctl enable --now pockethost-agent.service
printf '%s\n' "Pocket Host Linux agent service installed."
printf '%s\n' "Agent state: /var/lib/pockethost-agent"
printf '%s\n' "This preview service uses a local inbox; authenticated network transport is not yet implemented."
