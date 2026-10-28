import os
import subprocess
import sys
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/configure.py"

def test_setup_generates_distinct_private_credentials_and_never_overwrites(tmp_path):
    run = subprocess.run([sys.executable, str(SCRIPT)], cwd=tmp_path, capture_output=True, text=True)
    assert run.returncode == 0
    path = tmp_path / ".env"
    original = path.read_bytes()
    values = dict(line.split("=", 1) for line in path.read_text().splitlines())
    assert set(values) == {"DB_PASSWORD", "SOURCE_PASSWORD", "DEMO_API_TOKEN", "OTHER_API_TOKEN"}
    assert len(set(values.values())) == 4
    assert all(len(v) == 48 and v not in run.stdout and v not in run.stderr for v in values.values())
    assert path.stat().st_mode & 0o777 == 0o600
    retry = subprocess.run([sys.executable, str(SCRIPT)], cwd=tmp_path, capture_output=True, text=True)
    assert retry.returncode != 0
    assert path.read_bytes() == original
