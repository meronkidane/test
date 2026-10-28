CREATE TABLE IF NOT EXISTS ingestion_jobs (
 id uuid PRIMARY KEY, tenant text NOT NULL, idempotency_key text NOT NULL,
 request_hash text NOT NULL, spec jsonb NOT NULL,
 status text NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
 attempts integer NOT NULL DEFAULT 0, claim uuid, lease_until timestamptz,
 available_at timestamptz NOT NULL DEFAULT now(),
 result jsonb, error_code text,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant,idempotency_key)
);
CREATE INDEX IF NOT EXISTS ingestion_jobs_poll ON ingestion_jobs(status,available_at,lease_until);
