#!/usr/bin/env python3
"""Fail CI if the Android trust authority drifts from the shared role allow-list."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/assayflux/pockethost/management/DeviceTrustStore.kt"
POLICY = ROOT / "protocol/ROLE_CAPABILITY_POLICY.json"


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    policy = json.loads(POLICY.read_text(encoding="utf-8"))
    match = re.search(
        r"private val ROLE_CAPABILITIES = mapOf\((.*?)\n    \)",
        source,
        flags=re.DOTALL,
    )
    if not match:
        raise SystemExit("Could not locate DeviceTrustStore ROLE_CAPABILITIES map")
    actual: dict[str, set[str]] = {}
    for role, body in re.findall(r'"([a-z_]+)"\s+to\s+setOf\((.*?)\)', match.group(1), re.DOTALL):
        actual[role] = set(re.findall(r'"([^"]+)"', body))
    expected = {
        role: set(config["allow"])
        for role, config in policy["roles"].items()
    }
    if actual != expected:
        missing_roles = sorted(set(expected) - set(actual))
        extra_roles = sorted(set(actual) - set(expected))
        mismatched = {
            role: {
                "missing": sorted(expected[role] - actual.get(role, set())),
                "unexpected": sorted(actual.get(role, set()) - expected[role]),
            }
            for role in set(expected) & set(actual)
            if expected[role] != actual.get(role, set())
        }
        raise SystemExit(
            "Android role allow-list differs from protocol policy: "
            + json.dumps({"missing_roles": missing_roles, "extra_roles": extra_roles, "mismatched": mismatched}, sort_keys=True)
        )
    print(f"Android role allow-list matches all {len(expected)} protocol roles.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
