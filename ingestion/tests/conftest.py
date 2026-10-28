import os
import pytest
from app.store import Store

@pytest.fixture
def store():
    url = os.environ.get("TEST_DATABASE_URL")
    if not url:
        pytest.skip("Set TEST_DATABASE_URL to an isolated PostgreSQL test database")
    store = Store(url)
    store.migrate()
    with store.connection() as db:
        db.execute("TRUNCATE ingestion_jobs")
    return store
