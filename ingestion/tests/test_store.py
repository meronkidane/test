from concurrent.futures import ThreadPoolExecutor
import pytest
from app.store import Conflict

pytestmark = pytest.mark.integration

def test_concurrent_idempotency_and_conflict(store):
    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(lambda _: store.submit("demo", "same", {"v": 1}), range(4)))
    assert len({r[0]["id"] for r in rows}) == 1
    assert sum(created for _, created in rows) == 1
    with pytest.raises(Conflict):
        store.submit("demo", "same", {"v": 2})
    assert store.submit("other", "same", {"v": 2})[0]["id"] != rows[0][0]["id"]


def test_claims_are_exclusive_and_stale_worker_cannot_publish(store):
    submitted, _ = store.submit("demo", "one", {})
    with ThreadPoolExecutor(max_workers=4) as pool:
        claims = list(pool.map(lambda _: store.claim(), range(4)))
    old = next(c for c in claims if c)
    assert sum(c is not None for c in claims) == 1
    with store.connection() as db:
        db.execute("UPDATE ingestion_jobs SET lease_until=now()-interval '1 second' WHERE id=%s", (old["id"],))
    assert not store.heartbeat(old["id"], old["claim"])
    assert not store.complete(old["id"], old["claim"], {"bad": True})
    current = store.claim()
    assert current["attempts"] == 2 and current["claim"] != old["claim"]
    assert not store.complete(old["id"], old["claim"], {"bad": True})
    assert store.complete(current["id"], current["claim"], {"manifest": "ok"})
    assert store.get("demo", submitted["id"])["result"] == {"manifest": "ok"}


def test_cancel_fences_worker_and_hides_cross_tenant_runs(store):
    submitted, _ = store.submit("demo", "one", {})
    claim = store.claim()
    assert store.get("other", submitted["id"]) is None
    assert not store.cancel("other", submitted["id"])
    assert store.cancel("demo", submitted["id"])
    assert not store.heartbeat(claim["id"], claim["claim"])
    assert not store.complete(claim["id"], claim["claim"], {})
    assert store.claim() is None


def test_retry_budget_is_terminal_even_after_worker_crashes(store):
    store.submit("demo", "one", {})
    for expected in [1, 2, 3]:
        claim = store.claim()
        assert claim["attempts"] == expected
        with store.connection() as db:
            db.execute("UPDATE ingestion_jobs SET lease_until=now()-interval '1 second' WHERE id=%s", (claim["id"],))
    assert store.claim() is None
    assert store.get("demo", claim["id"])["status"] == "FAILED"


def test_failure_backoff_is_not_immediately_claimed(store):
    store.submit("demo", "one", {})
    claim = store.claim()
    assert store.fail(claim["id"], claim["claim"], "SPARK_JOB_FAILED")
    assert store.claim() is None
    assert store.get("demo", claim["id"])["status"] == "QUEUED"


def test_successful_retry_clears_previous_error(store):
    store.submit("demo", "retry", {})
    first = store.claim()
    store.fail(first["id"], first["claim"], "SPARK_JOB_FAILED")
    with store.connection() as db:
        db.execute("UPDATE ingestion_jobs SET available_at=now() WHERE id=%s", (first["id"],))
    retry = store.claim()
    assert store.complete(retry["id"], retry["claim"], {"manifest": "accepted"})
    assert store.get("demo", retry["id"])["error_code"] is None
