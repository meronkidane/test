"""PostgreSQL queue with idempotency, expiring claims and fenced completion."""
import hashlib
import json
import uuid
from pathlib import Path
import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

class Conflict(Exception):
    pass

class Store:
    def __init__(self, dsn: str):
        self.dsn = dsn

    def connection(self):
        return psycopg.connect(self.dsn, row_factory=dict_row, connect_timeout=5)

    def migrate(self):
        with self.connection() as db:
            db.execute("SELECT pg_advisory_xact_lock(724113)")
            db.execute((Path(__file__).parent.parent / "sql/001_jobs.sql").read_text())

    def submit(self, tenant, key, spec):
        digest = hashlib.sha256(json.dumps(spec, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        with self.connection() as db:
            row = db.execute("""INSERT INTO ingestion_jobs(id,tenant,idempotency_key,request_hash,spec,status)
                VALUES(%s,%s,%s,%s,%s,'QUEUED') ON CONFLICT(tenant,idempotency_key) DO NOTHING RETURNING *""",
                (uuid.uuid4(), tenant, key, digest, Jsonb(spec))).fetchone()
            if row:
                return row, True
            row = db.execute("SELECT * FROM ingestion_jobs WHERE tenant=%s AND idempotency_key=%s", (tenant, key)).fetchone()
            if row["request_hash"] != digest:
                raise Conflict("Idempotency key belongs to a different request/configuration")
            return row, False

    def get(self, tenant, job_id):
        with self.connection() as db:
            return db.execute("SELECT * FROM ingestion_jobs WHERE tenant=%s AND id=%s", (tenant, job_id)).fetchone()

    def claim(self, lease_seconds=60, max_attempts=3):
        with self.connection() as db:
            db.execute("""UPDATE ingestion_jobs SET status='FAILED',error_code='ATTEMPTS_EXHAUSTED',
                claim=NULL,lease_until=NULL,updated_at=now() WHERE status='RUNNING'
                AND lease_until < now() AND attempts >= %s""", (max_attempts,))
            row = db.execute("""SELECT id FROM ingestion_jobs WHERE attempts < %s AND
                ((status='QUEUED' AND available_at <= now()) OR (status='RUNNING' AND lease_until < now()))
                ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1""", (max_attempts,)).fetchone()
            if not row:
                return None
            return db.execute("""UPDATE ingestion_jobs SET status='RUNNING',attempts=attempts+1,claim=%s,
                lease_until=now()+make_interval(secs => %s),updated_at=now() WHERE id=%s RETURNING *""",
                (uuid.uuid4(), lease_seconds, row["id"])).fetchone()

    def heartbeat(self, job_id, claim, lease_seconds=60):
        with self.connection() as db:
            return db.execute("""UPDATE ingestion_jobs SET lease_until=now()+make_interval(secs => %s),updated_at=now()
                WHERE id=%s AND claim=%s AND status='RUNNING' AND lease_until>now()""",
                (lease_seconds, job_id, claim)).rowcount == 1

    def complete(self, job_id, claim, result):
        with self.connection() as db:
            return db.execute("""UPDATE ingestion_jobs SET status='SUCCEEDED',result=%s,error_code=NULL,claim=NULL,
                lease_until=NULL,updated_at=now() WHERE id=%s AND claim=%s AND status='RUNNING' AND lease_until>now()""",
                (Jsonb(result), job_id, claim)).rowcount == 1

    def fail(self, job_id, claim, code, max_attempts=3):
        with self.connection() as db:
            return db.execute("""UPDATE ingestion_jobs SET
                status=CASE WHEN attempts >= %s THEN 'FAILED' ELSE 'QUEUED' END,
                available_at=now()+make_interval(secs => attempts*5),error_code=%s,
                claim=NULL,lease_until=NULL,updated_at=now() WHERE id=%s AND claim=%s
                AND status='RUNNING' AND lease_until>now()""", (max_attempts, code, job_id, claim)).rowcount == 1

    def cancel(self, tenant, job_id):
        with self.connection() as db:
            return db.execute("""UPDATE ingestion_jobs SET status='CANCELLED',claim=NULL,lease_until=NULL,
                updated_at=now() WHERE tenant=%s AND id=%s AND status IN ('QUEUED','RUNNING') RETURNING id""",
                (tenant, job_id)).fetchone() is not None
