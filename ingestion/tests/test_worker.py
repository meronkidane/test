import subprocess
import sys
import threading
import uuid
from types import SimpleNamespace
import pytest
from app import worker

@pytest.mark.parametrize("reason", ["claim_lost", "shutdown", "deadline"])
def test_supervisor_terminates_child_on_lost_claim_shutdown_or_deadline(monkeypatch, reason):
    original = subprocess.Popen
    children = []
    def start(*args, **kwargs):
        child = original([sys.executable, "-c", "import time; time.sleep(60)"], **kwargs)
        children.append(child)
        return child
    monkeypatch.setattr(worker.subprocess, "Popen", start)
    stopping = threading.Event()
    if reason == "shutdown":
        stopping.set()
    store = SimpleNamespace(heartbeat=lambda *_: reason != "claim_lost")
    job = {"id": uuid.uuid4(), "claim": uuid.uuid4(), "spec": {}}
    with pytest.raises(RuntimeError):
        worker.execute(store, job, stopping, deadline_seconds=0 if reason == "deadline" else 30)
    assert len(children) == 1 and children[0].poll() is not None
