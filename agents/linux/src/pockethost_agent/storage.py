"""Confined, bounded local namespace snapshots for Linux storage/backup roles."""
from __future__ import annotations
import hashlib
import os
import tempfile
import zipfile
from pathlib import Path

def _roots():
    namespace_root = Path(os.environ.get("POCKETHOST_STORAGE_ROOT", "/var/lib/pockethost-agent/namespaces")).resolve()
    snapshot_root = Path(os.environ.get("POCKETHOST_SNAPSHOT_ROOT", "/var/lib/pockethost-agent/snapshots")).resolve()
    namespace_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    snapshot_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    return namespace_root, snapshot_root

def create_snapshot(namespace_id: str, snapshot_id: str, maximum_bytes: int) -> dict:
    for value in (namespace_id, snapshot_id):
        if not value or not value.replace("-", "").replace("_", "").isalnum():
            raise ValueError("Namespace or snapshot identifier is invalid.")
    if maximum_bytes < 1:
        raise ValueError("Snapshot requires a positive storage budget.")
    namespace_root, snapshot_root = _roots()
    source = (namespace_root / namespace_id).resolve()
    if source.parent != namespace_root or not source.is_dir() or source.is_symlink():
        raise ValueError("Assigned namespace does not exist or is not a safe directory.")
    destination = snapshot_root / (snapshot_id + ".zip")
    if destination.exists():
        raise FileExistsError("Snapshot IDs are immutable and cannot be overwritten.")
    entries, total = [], 0
    for root, dirs, files in os.walk(source, followlinks=False):
        root_path = Path(root)
        dirs[:] = sorted(d for d in dirs if not (root_path / d).is_symlink())
        for name in sorted(files):
            path = root_path / name
            if path.is_symlink() or not path.is_file():
                continue
            total += path.stat().st_size
            if total > maximum_bytes:
                raise ValueError("Namespace exceeds the granted snapshot storage budget.")
            entries.append(path)
            if len(entries) > 10000:
                raise ValueError("Namespace contains too many files for one snapshot.")
    fd, temporary_name = tempfile.mkstemp(prefix=".snapshot-", suffix=".tmp", dir=snapshot_root)
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for path in entries:
                archive.write(path, path.relative_to(source).as_posix())
        with temporary.open("rb") as stream:
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        os.link(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    return {"snapshot_id": snapshot_id, "sha256": digest, "file_count": len(entries),
            "source_bytes": total, "archive_bytes": destination.stat().st_size}

def verify_snapshot(snapshot_id: str) -> dict:
    if not snapshot_id or not snapshot_id.replace("-", "").replace("_", "").isalnum():
        raise ValueError("Snapshot identifier is invalid.")
    _, snapshot_root = _roots()
    path = snapshot_root / (snapshot_id + ".zip")
    if path.is_symlink() or not path.is_file():
        raise FileNotFoundError("Snapshot does not exist.")
    with zipfile.ZipFile(path, "r") as archive:
        bad = archive.testzip()
        if bad is not None:
            raise ValueError("Snapshot archive failed CRC verification.")
        names = archive.namelist()
        if any(name.startswith("/") or ".." in Path(name).parts for name in names):
            raise ValueError("Snapshot contains an unsafe archive path.")
    return {"snapshot_id": snapshot_id, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "file_count": len(names), "verified": True}
