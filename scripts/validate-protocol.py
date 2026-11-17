#!/usr/bin/env python3
"""Validate Pocket Host JSON Schemas and protocol policy consistency."""
import json
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]


def load_contracts():
    version = json.loads((ROOT / "protocol/protocol-version.json").read_text())
    assert version["protocol"] == "pockethost.device"
    assert version["version"] == "1.0"
    assert version["schema_dialect"] == "https://json-schema.org/draft/2020-12/schema"
    schemas = {p.name: json.loads(p.read_text())
               for p in (ROOT / "protocol/schemas").glob("*.schema.json")}
    expected = {"device-hello.schema.json", "role-grant.schema.json",
                "job-envelope.schema.json", "enrollment-challenge.schema.json"}
    missing = expected - schemas.keys()
    if missing:
        raise SystemExit(f"Missing protocol schemas: {sorted(missing)}")
    policy = json.loads((ROOT / "protocol/ROLE_CAPABILITY_POLICY.json").read_text())
    if policy.get("default_policy") != "deny":
        raise SystemExit("Role policy must be default deny")
    for name, role in policy["roles"].items():
        if set(role["allow"]) & set(role["deny"]):
            raise SystemExit(f"Role {name} has overlapping allow/deny capabilities")
    for name, schema in schemas.items():
        Draft202012Validator.check_schema(schema)
        if schema.get("properties", {}).get("protocol", {}).get("const") != "pockethost.device/1.0":
            raise SystemExit(f"{name}: protocol version constant is missing or inconsistent")
    return schemas, policy


if __name__ == "__main__":
    schemas, _ = load_contracts()
    for name in sorted(schemas):
        print(f"VALID protocol/schemas/{name}")
    print(f"Validated {len(schemas)} Pocket Host protocol schemas and role policy.")
