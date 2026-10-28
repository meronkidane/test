"""Claim, heartbeat, supervise Spark subprocess, and fence successful publication."""
import json
import logging
import os
import signal
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path
from .store import Store

log = logging.getLogger("ingestion.worker")


def stop_process(process):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()


def execute(store, job, stopping, deadline_seconds=600, heartbeat_seconds=10):
    with tempfile.TemporaryDirectory(prefix="spark-run-") as folder:
        path = Path(folder)
        request, result = path / "request.json", path / "result.json"
        request.write_text(json.dumps({"id": str(job["id"]), "claim": str(job["claim"]), "spec": job["spec"]}))
        # Never shell-expand arguments. Driver stderr can include source data/credentials:
        # discard it here; expose bounded error codes instead of raw external exceptions.
        process = subprocess.Popen([sys.executable, "-m", "app.spark_job", str(request), str(result)],
                                   start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        started = time.monotonic()
        next_heartbeat = started
        try:
            while process.poll() is None:
                if stopping.is_set():
                    raise RuntimeError("WORKER_SHUTDOWN")
                if time.monotonic() - started > deadline_seconds:
                    raise RuntimeError("SPARK_DEADLINE_EXCEEDED")
                if time.monotonic() >= next_heartbeat:
                    if not store.heartbeat(job["id"], job["claim"]):
                        raise RuntimeError("CLAIM_LOST_OR_CANCELLED")
                    next_heartbeat = time.monotonic() + heartbeat_seconds
                stopping.wait(0.25)
            if process.returncode != 0 or not result.exists():
                raise RuntimeError("SPARK_JOB_FAILED")
            manifest = json.loads(result.read_text())
            if manifest["run_id"] != str(job["id"]) or manifest["claim"] != str(job["claim"]):
                raise RuntimeError("INVALID_MANIFEST")
            published = store.complete(job["id"], job["claim"], manifest)
            log.info("run=%s outcome=%s", job["id"], "SUCCEEDED" if published else "STALE_ATTEMPT")
        finally:
            stop_process(process)


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    store = Store(os.environ["DATABASE_URL"])
    store.migrate()
    stopping = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: stopping.set())
    while not stopping.is_set():
        job = None
        try:
            job = store.claim()
            if job:
                execute(store, job, stopping, int(os.environ.get("JOB_TIMEOUT_SECONDS", "600")))
            else:
                stopping.wait(1)
        except Exception as error:
            # Do not serialize database, source or credential-bearing exception text.
            code = str(error) if isinstance(error, RuntimeError) and str(error) in {
                "WORKER_SHUTDOWN", "SPARK_DEADLINE_EXCEEDED", "CLAIM_LOST_OR_CANCELLED",
                "SPARK_JOB_FAILED", "INVALID_MANIFEST"} else "WORKER_DEPENDENCY_FAILURE"
            log.warning("run=%s code=%s", job["id"] if job else "none", code)
            if job:
                try:
                    store.fail(job["id"], job["claim"], code)
                except Exception:
                    log.warning("failure_recording_unavailable; claim will expire")
            stopping.wait(2)

if __name__ == "__main__":
    main()
