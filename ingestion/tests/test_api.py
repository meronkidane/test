import pytest
from fastapi.testclient import TestClient
from app.api import create_app
from app.catalog import Catalog

pytestmark = pytest.mark.integration

def test_authenticated_idempotent_submission_and_tenant_isolation(store, monkeypatch):
    monkeypatch.setenv("DEMO_API_TOKEN", "demo-token-with-at-least-24-characters")
    monkeypatch.setenv("OTHER_API_TOKEN", "other-token-with-at-least-24-characters")
    headers = {"Authorization": "Bearer demo-token-with-at-least-24-characters", "Idempotency-Key": "api-test"}
    with TestClient(create_app(store, Catalog.load("config/catalog.json"))) as client:
        body = {"dataset": "orders", "sources": ["csv"]}
        assert client.post("/v1/runs", json=body).status_code == 401
        response = client.post("/v1/runs", json=body, headers=headers)
        assert response.status_code == 202
        job_id = response.json()["id"]
        assert client.post("/v1/runs", json=body, headers=headers).json()["duplicate"]
        assert client.post("/v1/runs", json={**body, "sources": ["json"]}, headers=headers).status_code == 409
        assert client.post("/v1/runs", json={**body, "sources": ["file:///etc/passwd"]}, headers=headers).status_code == 422
        assert client.post("/v1/runs", json={**body, "tenant": "other"}, headers=headers).status_code == 422
        assert client.get("/v1/runs/"+job_id, headers={"Authorization": "Bearer other-token-with-at-least-24-characters"}).status_code == 404
        assert client.post("/v1/runs/"+job_id+"/cancel", headers=headers).status_code == 202
        assert client.post("/v1/runs/"+job_id+"/cancel", headers=headers).status_code == 409
        assert client.get("/v1/runs/"+job_id, headers=headers).json()["status"] == "CANCELLED"


@pytest.mark.parametrize("key", ["", "\t", "x" * 129])
def test_invalid_idempotency_keys_do_not_create_jobs(store, monkeypatch, key):
    monkeypatch.setenv("DEMO_API_TOKEN", "demo-token-with-at-least-24-characters")
    monkeypatch.setenv("OTHER_API_TOKEN", "other-token-with-at-least-24-characters")
    with TestClient(create_app(store, Catalog.load("config/catalog.json"))) as client:
        response = client.post("/v1/runs", json={"dataset": "orders", "sources": ["csv"]},
            headers={"Authorization": "Bearer demo-token-with-at-least-24-characters", "Idempotency-Key": key})
        assert response.status_code == 422
    with store.connection() as db:
        assert db.execute("SELECT count(*) AS n FROM ingestion_jobs").fetchone()["n"] == 0
