"""Durable local assignment and replay state."""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Any


class AgentStore:
    def __init__(self, path: Path):
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.db = sqlite3.connect(path)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA synchronous=FULL")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS agent_state (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS jobs (
                job_id TEXT PRIMARY KEY, idempotency_key TEXT NOT NULL UNIQUE,
                state TEXT NOT NULL, envelope TEXT NOT NULL, result TEXT
            );
        """)
        self.db.commit()

    def get_generation(self) -> int:
        row = self.db.execute("SELECT value FROM agent_state WHERE key='generation'").fetchone()
        return int(row[0]) if row else 0

    def save_grant(self, grant: dict[str, Any], owner_public_key: str) -> None:
        with self.db:
            self.db.execute(
                "INSERT INTO agent_state(key,value) VALUES('generation',?) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (str(grant["generation"]),),
            )
            self.db.execute(
                "INSERT INTO agent_state(key,value) VALUES('grant',?) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (json.dumps(grant, separators=(",", ":")),),
            )
            self.db.execute(
                "INSERT INTO agent_state(key,value) VALUES('owner_public_key',?) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value", (owner_public_key,)
            )

    def enqueue_once(self, job_id: str, idempotency_key: str, envelope: dict[str, Any]) -> bool:
        try:
            with self.db:
                self.db.execute(
                    "INSERT INTO jobs(job_id,idempotency_key,state,envelope) VALUES(?,?,?,?)",
                    (job_id, idempotency_key, "QUEUED", json.dumps(envelope, separators=(",", ":"))),
                )
            return True
        except sqlite3.IntegrityError:
            return False

    def claim_job(self, job_id: str) -> bool:
        with self.db:
            cursor = self.db.execute(
                "UPDATE jobs SET state='RUNNING' WHERE job_id=? AND state='QUEUED'", (job_id,)
            )
            return cursor.rowcount == 1

    def complete_job(self, job_id: str, result: dict[str, Any]) -> None:
        with self.db:
            self.db.execute(
                "UPDATE jobs SET state='SUCCEEDED', result=? WHERE job_id=? AND state='RUNNING'",
                (json.dumps(result, separators=(",", ":")), job_id),
            )

    def fail_job(self, job_id: str, reason: str) -> None:
        with self.db:
            self.db.execute(
                "UPDATE jobs SET state='FAILED', result=? WHERE job_id=? AND state='RUNNING'",
                (json.dumps({"error": reason[:256]}, separators=(",", ":")), job_id),
            )

    def close(self) -> None:
        self.db.close()
