import pytest
from pydantic import ValidationError
from app.catalog import Catalog, Source

def test_no_arbitrary_sources_or_tenant_path():
    catalog = Catalog.load("config/catalog.json")
    with pytest.raises(PermissionError):
        catalog.snapshot("../demo", "orders", ["csv"])
    with pytest.raises(ValueError):
        catalog.snapshot("demo", "orders", ["https://evil.invalid"])
    with pytest.raises(ValueError):
        catalog.snapshot("demo", "orders", ["csv", "csv"])
    result = catalog.snapshot("demo", "orders", ["csv"])
    assert result["output"] == "s3a://curated/orders/demo/orders"
    assert result["sources"]["csv"]["kind"] == "csv"

@pytest.mark.parametrize("uri", ["file:///etc/passwd", "https://127.0.0.1", "s3a://bucket/../private", "s3a://bucket/*"])
def test_rejects_non_catalog_file_locations(uri):
    with pytest.raises(ValidationError):
        Source(kind="json", uri=uri)

def test_jdbc_table_cannot_be_query_or_contain_credentials():
    with pytest.raises(ValidationError):
        Source(kind="jdbc", uri="jdbc:postgresql://db:5432/demo", table="(select * from secrets) x", user_env="USER", password_env="PASS")
    with pytest.raises(ValidationError):
        Source(kind="jdbc", uri="jdbc:postgresql://db:5432/demo?password=secret", table="orders", user_env="USER", password_env="PASS")
