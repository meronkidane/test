import hmac
import os
import uuid
from contextlib import asynccontextmanager
from typing import Annotated
from fastapi import Depends, FastAPI, Header, HTTPException, Response
from pydantic import BaseModel, ConfigDict, Field
from .catalog import Catalog, NAME
from .store import Conflict, Store

class RunRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    dataset: str = Field(pattern=NAME)
    sources: list[str] = Field(min_length=1, max_length=16)


def create_app(store=None, catalog=None):
    store = store or Store(os.environ["DATABASE_URL"])
    catalog = catalog or Catalog.load(os.environ.get("CATALOG_PATH", "config/catalog.json"))
    # Resolve credentials once; fail startup if any configured token is missing/weak.
    tokens = {tenant: os.environ[p.token_env] for tenant, p in catalog.principals.items()}
    if any(len(token) < 24 for token in tokens.values()) or len(set(tokens.values())) != len(tokens):
        raise ValueError("Use distinct API tokens of at least 24 characters")

    @asynccontextmanager
    async def lifespan(app):
        store.migrate()
        yield

    app = FastAPI(title="Multi-source Spark ingestion", version="1.0.0", lifespan=lifespan)

    def tenant(authorization: Annotated[str | None, Header()] = None):
        if authorization and authorization.startswith("Bearer "):
            for name, token in tokens.items():
                if hmac.compare_digest(authorization[7:].encode(), token.encode()):
                    return name
        raise HTTPException(401, "Invalid service credential")

    def public(row):
        return {k: row[k] for k in ("id", "status", "attempts", "created_at", "updated_at", "result", "error_code")}

    @app.get("/health")
    def health():
        with store.connection() as db:
            db.execute("SELECT 1")
        return {"status": "UP"}

    @app.post("/v1/runs", status_code=202)
    def submit(request: RunRequest, response: Response, owner=Depends(tenant),
               idempotency_key: Annotated[str, Header(min_length=1, max_length=128)] = ""):
        if not idempotency_key or not idempotency_key.isascii() or not idempotency_key.isprintable():
            raise HTTPException(422, "Provide an ASCII Idempotency-Key")
        try:
            spec = catalog.snapshot(owner, request.dataset, request.sources)
            row, created = store.submit(owner, idempotency_key, spec)
        except PermissionError:
            raise HTTPException(403, "Dataset not authorized") from None
        except ValueError:
            raise HTTPException(422, "Unknown or duplicate sources") from None
        except Conflict:
            raise HTTPException(409, "Idempotency key conflict") from None
        response.headers["Location"] = f"/v1/runs/{row['id']}"
        return {**public(row), "duplicate": not created}

    @app.get("/v1/runs/{job_id}")
    def get(job_id: uuid.UUID, owner=Depends(tenant)):
        row = store.get(owner, job_id)
        if not row:
            raise HTTPException(404, "Run not found")
        return public(row)

    @app.post("/v1/runs/{job_id}/cancel", status_code=202)
    def cancel(job_id: uuid.UUID, owner=Depends(tenant)):
        if not store.get(owner, job_id):
            raise HTTPException(404, "Run not found")
        if not store.cancel(owner, job_id):
            raise HTTPException(409, "Run already terminal")
        return {"status": "CANCELLED"}

    return app
