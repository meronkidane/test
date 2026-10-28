"""Exercise HTTP -> durable queue -> Spark -> actual S3 objects and cancellation."""
import json
import os
import time
import uuid
import urllib.request
import boto3


def main():
    base = os.environ.get("API_URL", "http://127.0.0.1:8099")
    token = os.environ["DEMO_API_TOKEN"]
    def request(method, path, data=None, key=None):
        headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
        if key:
            headers["Idempotency-Key"] = key
        req = urllib.request.Request(base+path, method=method, headers=headers,
                                     data=json.dumps(data).encode() if data is not None else None)
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.load(response)
    key = str(uuid.uuid4())
    body = {"dataset": "orders", "sources": ["postgres", "csv", "json", "parquet"]}
    first = request("POST", "/v1/runs", body, key)
    duplicate = request("POST", "/v1/runs", body, key)
    assert first["id"] == duplicate["id"] and duplicate["duplicate"]
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        run = request("GET", "/v1/runs/" + first["id"])
        if run["status"] in ("FAILED", "CANCELLED"):
            raise AssertionError(run)
        if run["status"] == "SUCCEEDED":
            break
        time.sleep(1)
    else:
        raise AssertionError("Ingestion deadline exceeded")
    result = run["result"]
    assert result["input_rows"] == 11 and result["output_rows"] == 8, result
    assert result["duplicate_rows"] == 3 and result["invalid_rows"] == 0, result
    from urllib.parse import urlsplit
    uri = urlsplit(result["manifest_uri"])
    s3 = boto3.client("s3", endpoint_url=os.environ.get("S3_ENDPOINT"))
    manifest = json.loads(s3.get_object(Bucket=uri.netloc, Key=uri.path.lstrip("/"))["Body"].read())
    assert manifest["run_id"] == first["id"]
    objects = s3.list_objects_v2(Bucket=uri.netloc, Prefix=uri.path.lstrip("/").rsplit("/", 1)[0]+"/data/")
    assert any(o["Key"].endswith(".parquet") and "event_date=" in o["Key"] for o in objects["Contents"])
    from app.spark_job import session
    spark = session()
    try:
        rows = spark.read.parquet(result["data_uri"]).collect()
        # Fixture is deliberately tiny; production ingestion itself never collects rows.
        assert len(rows) == 8
        assert sum(row.amount for row in rows) == __import__("decimal").Decimal("162.50")
        assert {row.source_id for row in rows} == {"postgres", "csv", "json", "parquet"}
        assert all(str(row.amount) == "15.00" for row in rows if row.record_id == "file-1")
    finally:
        spark.stop()
    cancelled = request("POST", "/v1/runs", body, str(uuid.uuid4()))
    request("POST", "/v1/runs/" + cancelled["id"] + "/cancel")
    assert request("GET", "/v1/runs/" + cancelled["id"])["status"] == "CANCELLED"
    print(json.dumps({"status": "PASS", "run_id": first["id"], "input_rows": 11, "output_rows": 8,
                      "sources": list(result["sources"]), "manifest_uri": result["manifest_uri"],
                      "idempotency": "PASS", "cancel": "PASS"}, indent=2))

if __name__ == "__main__":
    main()
