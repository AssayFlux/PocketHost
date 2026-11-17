#!/usr/bin/env python3
"""Validate Pocket Host JSON Schema documents and protocol version metadata."""
import json
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
VERSION = json.loads((ROOT / "protocol/protocol-version.json").read_text())
assert VERSION["protocol"] == "pockethost.device"
assert VERSION["version"] == "1.0"
assert VERSION["schema_dialect"] == "https://json-schema.org/draft/2020-12/schema"

schemas = sorted((ROOT / "protocol/schemas").glob("*.schema.json"))
expected = {
    "device-hello.schema.json",
    "role-grant.schema.json",
    "job-envelope.schema.json",
}
actual = {path.name for path in schemas}
missing = expected - actual
if missing:
    raise SystemExit(f"Missing protocol schemas: {sorted(missing)}")

for path in schemas:
    schema = json.loads(path.read_text())
    Draft202012Validator.check_schema(schema)
    if schema.get("properties", {}).get("protocol", {}).get("const") != "pockethost.device/1.0":
        raise SystemExit(f"{path.name}: protocol version constant is missing or inconsistent")
    print(f"VALID {path.relative_to(ROOT)}")

print(f"Validated {len(schemas)} Pocket Host protocol schemas.")
