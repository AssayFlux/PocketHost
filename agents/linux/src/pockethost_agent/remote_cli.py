"""Environment-safe entry point for the Linux remote worker."""
from __future__ import annotations

import argparse
import os
from pathlib import Path

from .remote import run_remote

ROOT = Path(os.environ.get(
    "POCKETHOST_AGENT_HOME",
    Path.home() / ".local" / "state" / "pockethost-agent",
))


def main() -> None:
    parser = argparse.ArgumentParser(prog="pockethost-agent-remote")
    parser.add_argument("--endpoint", default=os.environ.get("POCKETHOST_CONTROL_ENDPOINT"))
    parser.add_argument("--poll-interval", type=float, default=15.0)
    parser.add_argument(
        "--token-env", default="POCKETHOST_CONTROL_TOKEN",
        help="Environment variable containing the one-time-issued device bearer token.",
    )
    args = parser.parse_args()
    if not args.endpoint:
        parser.error("--endpoint or POCKETHOST_CONTROL_ENDPOINT is required")
    token = os.environ.get(args.token_env, "")
    if not token:
        parser.error("The device token environment variable is not set")
    run_remote(args.endpoint, token, ROOT, args.poll_interval)


if __name__ == "__main__":
    main()
