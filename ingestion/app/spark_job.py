"""One Spark application per claimed attempt; never overwrite another attempt."""
import json
import os
import sys
from pathlib import Path
from urllib.parse import urlsplit
import boto3
from pyspark.sql import SparkSession
from .pipeline import ingest


def session():
    builder = (SparkSession.builder.appName("multi-source-ingestion")
               .master(os.environ.get("SPARK_MASTER", "local[2]"))
               .config("spark.sql.session.timeZone", "UTC")
               .config("spark.sql.shuffle.partitions", "4")
               .config("spark.ui.enabled", "false")
               .config("spark.hadoop.fs.s3a.endpoint.region", os.environ.get("AWS_REGION", "us-east-1")))
    endpoint = os.environ.get("S3_ENDPOINT")
    if endpoint:
        builder = (builder.config("spark.hadoop.fs.s3a.endpoint", endpoint)
                   .config("spark.hadoop.fs.s3a.path.style.access", "true")
                   .config("spark.hadoop.fs.s3a.connection.ssl.enabled", str(endpoint.startswith("https:")).lower()))
    # S3A and boto3 use their environment / workload-identity credential chains.
    jars = os.environ.get("SPARK_CONNECTOR_JARS")
    if jars:
        builder = builder.config("spark.jars", jars)
    return builder.getOrCreate()


def main():
    payload = json.loads(Path(sys.argv[1]).read_text())
    spec = payload["spec"]
    output = f"{spec['output']}/runs/{payload['id']}/attempts/{payload['claim']}"
    spark = session()
    try:
        result = ingest(spark, spec, output)
        result.update(run_id=payload["id"], claim=payload["claim"], schema_version=1)
        manifest_uri = output + "/manifest.json"
        parsed = urlsplit(manifest_uri)
        boto3.client("s3", endpoint_url=os.environ.get("S3_ENDPOINT")).put_object(
            Bucket=parsed.netloc, Key=parsed.path.lstrip("/"),
            Body=json.dumps(result, sort_keys=True).encode(), ContentType="application/json")
        result["manifest_uri"] = manifest_uri
        Path(sys.argv[2]).write_text(json.dumps(result))
    finally:
        spark.stop()

if __name__ == "__main__":
    main()
