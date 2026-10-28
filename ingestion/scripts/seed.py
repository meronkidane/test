"""Idempotent local fixture setup. Uses Spark to create a real Parquet source."""
import json
import os
import tempfile
from pathlib import Path
import boto3
from botocore.exceptions import ClientError
from app.spark_job import session


def main():
    s3 = boto3.client("s3", endpoint_url=os.environ.get("S3_ENDPOINT"))
    for bucket in ("landing", "curated"):
        try:
            s3.create_bucket(Bucket=bucket)
        except ClientError as error:
            if error.response["Error"]["Code"] not in ("BucketAlreadyOwnedByYou", "BucketAlreadyExists"):
                raise
    rows = [
        ("file-1", "customer-1", "10.00", "2026-01-01T12:00:00Z"),
        ("file-1", "customer-1", "15.00", "2026-01-02T12:00:00Z"),
        ("file-2", "customer-2", "25.00", "2026-01-02T12:00:00Z"),
    ]
    columns = ["record_id", "customer_id", "amount", "event_time"]
    s3.put_object(Bucket="landing", Key="csv/orders.csv", Body=(",".join(columns)+"\n"+"\n".join(",".join(r) for r in rows)).encode())
    s3.put_object(Bucket="landing", Key="json/orders.json", Body="\n".join(json.dumps(dict(zip(columns, r))) for r in rows).encode())
    spark = session()
    try:
        with tempfile.TemporaryDirectory() as temp:
            spark.createDataFrame(rows, columns).coalesce(1).write.parquet(temp + "/parquet")
            for file in Path(temp, "parquet").glob("*.parquet"):
                s3.put_object(Bucket="landing", Key="parquet/orders.parquet", Body=file.read_bytes())
    finally:
        spark.stop()
    print("Seeded PostgreSQL companion fixtures plus S3 CSV/JSON/Parquet inputs")

if __name__ == "__main__":
    main()
