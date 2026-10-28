import pytest
from pyspark.sql import SparkSession, functions as F
from app.pipeline import normalize, deduplicate, valid_condition, ingest

pytestmark = pytest.mark.spark

@pytest.fixture(scope="module")
def spark():
    session = (SparkSession.builder.master("local[2]").appName("ingestion-regression")
               .config("spark.ui.enabled", "false").config("spark.sql.session.timeZone", "UTC")
               .config("spark.sql.shuffle.partitions", "2").getOrCreate())
    yield session
    session.stop()


def test_normalization_quarantine_and_deterministic_dedup(spark):
    rows = [("one", "customer", "10.00", "2026-01-01T00:00:00Z"),
            ("one", "customer", "15.00", "2026-01-02T00:00:00Z"),
            ("bad", "customer", "not-money", "2026-01-01T00:00:00Z"),
            ("negative", "customer", "-1.00", "2026-01-01T00:00:00Z"),
            ("bad-time", "customer", "1.00", "not-a-time"),
            (" ", "customer", "1.00", "2026-01-01T00:00:00Z")]
    frame = normalize(spark.createDataFrame(rows, ["record_id", "customer_id", "amount", "event_time"]), "csv")
    good = frame.filter(F.coalesce(valid_condition(), F.lit(False)))
    assert good.count() == 2
    result = deduplicate(good).collect()
    assert len(result) == 1 and str(result[0].amount) == "15.00"
    assert str(result[0].event_date) == "2026-01-02"
    other = normalize(spark.createDataFrame(rows[:1], ["record_id", "customer_id", "amount", "event_time"]), "jdbc")
    assert deduplicate(good.unionByName(other)).count() == 2


def test_real_json_ingestion_writes_partitioned_parquet_and_quarantine(spark, tmp_path):
    source = tmp_path / "source.json"
    source.write_text('{"record_id":"a","customer_id":"c","amount":"10.00","event_time":"2026-01-01T00:00:00Z"}\n{"record_id":"bad","customer_id":"c","amount":"oops","event_time":"2026-01-01T00:00:00Z"}\n')
    spec = {"sources": {"json": {"kind": "json", "uri": str(source), "max_rows": 10}},
            "max_invalid_fraction": 0.5, "output_partitions": 1}
    output = str(tmp_path / "output")
    result = ingest(spark, spec, output)
    assert result["invalid_rows"] == 1 and result["output_rows"] == 1
    assert spark.read.parquet(output+"/data").count() == 1
    assert spark.read.parquet(output+"/quarantine").count() == 1
    with pytest.raises(ValueError, match="DATA_QUALITY"):
        ingest(spark, {**spec, "max_invalid_fraction": 0}, str(tmp_path / "failed"))
    assert not (tmp_path / "failed/data").exists()
