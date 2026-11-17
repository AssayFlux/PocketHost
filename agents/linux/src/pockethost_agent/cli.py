"""Linux agent command-line entry point."""
from __future__ import annotations

import argparse
import base64
import json
import os
import time
from pathlib import Path

from .trust import load_or_create_identity, sign_challenge, verify_role_grant, verify_job_signature
from .store import AgentStore
from .runner import execute_job

ROOT = Path(os.environ.get("POCKETHOST_AGENT_HOME", Path.home() / ".local" / "state" / "pockethost-agent"))


def main() -> None:
    parser = argparse.ArgumentParser(prog="pockethost-agent")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("identity", help="Create or display this Linux agent's public identity")
    challenge = sub.add_parser("answer-challenge", help="Sign a mobile-owner enrollment challenge")
    challenge.add_argument("challenge_json")
    grant = sub.add_parser("install-grant", help="Verify and persist an owner-signed role grant")
    grant.add_argument("grant_json")
    grant.add_argument("--owner-public-key", required=True, help="Base64 DER SubjectPublicKeyInfo")
    daemon = sub.add_parser("serve", help="Run the local durable inbox worker")
    daemon.add_argument("--inbox", default=str(ROOT / "inbox"))
    daemon.add_argument("--results", default=str(ROOT / "results"))
    daemon.add_argument("--poll-interval", type=float, default=2.0)
    job = sub.add_parser("run-job", help="Run one supported typed job envelope")
    job.add_argument("job_json")
    args = parser.parse_args()

    key, device_id, public_key = load_or_create_identity(ROOT)
    store = AgentStore(ROOT / "agent-state.sqlite3")
    try:
        if args.command == "identity":
            print(json.dumps({"protocol": "pockethost.device/1.0", "device_id": device_id,
                              "public_key_spki_base64": public_key, "os": "linux"}, indent=2))
        elif args.command == "answer-challenge":
            challenge_data = json.loads(Path(args.challenge_json).read_text())
            print(json.dumps({"device_id": device_id, "proof_signature": sign_challenge(key, challenge_data)}, indent=2))
        elif args.command == "install-grant":
            grant_data = json.loads(Path(args.grant_json).read_text())
            policy = json.loads((Path(__file__).resolve().parents[4] / "protocol" / "ROLE_CAPABILITY_POLICY.json").read_text())
            verify_role_grant(grant_data, args.owner_public_key, device_id, store.get_generation() + 1, policy)
            store.save_grant(grant_data, args.owner_public_key)
            print("Verified role grant installed.")
        elif args.command == "serve":
            inbox = Path(args.inbox)
            results = Path(args.results)
            inbox.mkdir(mode=0o700, parents=True, exist_ok=True)
            results.mkdir(mode=0o700, parents=True, exist_ok=True)
            if not 0.25 <= args.poll_interval <= 60:
                raise SystemExit("Poll interval must be between 0.25 and 60 seconds.")
            while True:
                grant_row = store.db.execute("SELECT value FROM agent_state WHERE key='grant'").fetchone()
                owner_row = store.db.execute("SELECT value FROM agent_state WHERE key='owner_public_key'").fetchone()
                if not grant_row or not owner_row:
                    time.sleep(args.poll_interval)
                    continue
                grant_data = json.loads(grant_row[0])
                for source in sorted(inbox.glob("*.json")):
                    job_id = source.stem
                    try:
                        job_data = json.loads(source.read_text())
                        job_id = job_data["job_id"]
                        idempotency_key = job_data["idempotency_key"]
                        store.enqueue_once(job_id, idempotency_key, job_data)
                        if not store.claim_job(job_id):
                            source.unlink(missing_ok=True)
                            continue
                        verify_job_signature(job_data, owner_row[0])
                        result = execute_job(job_data, grant_data)
                        store.complete_job(job_id, result)
                        output = results / f"{job_id}.json"
                        temporary = output.with_suffix(".tmp")
                        temporary.write_text(json.dumps({"job_id": job_id, "success": True, "result": result}, indent=2))
                        os.chmod(temporary, 0o600)
                        temporary.replace(output)
                    except Exception as error:
                        store.fail_job(job_id, str(error))
                        failure = results / f"{source.stem}.json"
                        failure.write_text(json.dumps({"job_id": source.stem, "success": False, "error": str(error)[:256]}, indent=2))
                        os.chmod(failure, 0o600)
                    finally:
                        source.unlink(missing_ok=True)
                time.sleep(args.poll_interval)
        elif args.command == "run-job":
            row = store.db.execute("SELECT value FROM agent_state WHERE key='grant'").fetchone()
            if not row:
                raise SystemExit("No verified role grant is installed.")
            grant_data = json.loads(row[0])
            job_data = json.loads(Path(args.job_json).read_text())
            owner_key = store.db.execute("SELECT value FROM agent_state WHERE key='owner_public_key'").fetchone()
            if not owner_key:
                raise SystemExit("No owner verification key is stored.")
            verify_job_signature(job_data, owner_key[0])
            result = execute_job(job_data, grant_data)
            print(json.dumps({"result": result, "sha256": __import__("hashlib").sha256(
                json.dumps(result, sort_keys=True, separators=(",", ":")).encode()).hexdigest()}, indent=2))
    finally:
        store.close()


if __name__ == "__main__":
    main()
