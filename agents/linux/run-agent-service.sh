#!/usr/bin/env sh
set -eu

if [ -n "${POCKETHOST_CONTROL_ENDPOINT:-}" ] && [ -n "${POCKETHOST_CONTROL_TOKEN:-}" ]; then
  exec /opt/pockethost-agent/bin/pockethost-agent-remote --endpoint "$POCKETHOST_CONTROL_ENDPOINT"
fi

exec /opt/pockethost-agent/bin/pockethost-agent serve
