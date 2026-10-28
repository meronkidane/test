"""Initialize a disposable PostgreSQL cluster with a quoted injected password."""
import os
import secrets
import subprocess
import time
import uuid
from pathlib import Path
import pytest

pytestmark = pytest.mark.skipif(os.environ.get("RUN_DOCKER_TESTS") != "1", reason="Set RUN_DOCKER_TESTS=1 for fresh PostgreSQL bootstrap")


def test_injected_source_password_authenticates_and_remains_read_only():
    name = "ingestion-credential-test-" + uuid.uuid4().hex[:12]
    password = "quote'and\\slash;" + secrets.token_hex(20)
    env = os.environ | {"POSTGRES_PASSWORD": secrets.token_hex(24), "SOURCE_PASSWORD": password}
    fixture = Path(__file__).resolve().parents[1] / "fixtures/source.sql"
    def run(args, environment=env):
        return subprocess.run(args, env=environment, capture_output=True, text=True, timeout=30)
    try:
        started = run(["docker", "run", "-d", "--name", name, "--tmpfs", "/tmp:rw,size=256m",
            "-e", "PGDATA=/tmp/pgdata", "-e", "POSTGRES_USER=ingestion", "-e", "POSTGRES_DB=ingestion",
            "-e", "POSTGRES_PASSWORD", "-e", "SOURCE_PASSWORD",
            "-e", "POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256", "--mount",
            f"type=bind,source={fixture},target=/docker-entrypoint-initdb.d/001-source.sql,readonly",
            "postgres:17.4-alpine"])
        assert started.returncode == 0, "Disposable PostgreSQL startup failed"
        for _ in range(60):
            # TCP is opened by the final server only, after all init scripts complete.
            ready = run(["docker", "exec", name, "pg_isready", "-h", "127.0.0.1", "-U", "ingestion"])
            if ready.returncode == 0:
                break
            time.sleep(0.25)
        else:
            pytest.fail("PostgreSQL bootstrap did not complete")
        client = ["docker", "exec", "-e", "PGPASSWORD", name, "psql", "-h", "127.0.0.1",
                  "-U", "ingestion_reader", "-d", "ingestion", "-At", "-v", "ON_ERROR_STOP=1", "-c"]
        reader_env = env | {"PGPASSWORD": password}
        selected = run(client + ["SELECT count(*) FROM source_orders"], reader_env)
        assert selected.returncode == 0 and selected.stdout.strip() == "2", "Injected credential cannot read fixtures"
        assert run(client + ["DELETE FROM source_orders"], reader_env).returncode != 0
        assert run(client + ["CREATE ROLE unexpected_role"], reader_env).returncode != 0
        denied = run(client + ["SELECT 1"], env | {"PGPASSWORD": "incorrect-password"})
        assert denied.returncode != 0, "Incorrect credential was accepted"
    finally:
        run(["docker", "rm", "-f", name])
