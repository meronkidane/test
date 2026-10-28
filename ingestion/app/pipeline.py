"""Distributed normalization, quality checks and deterministic per-source deduplication."""
import os
from functools import reduce
from pyspark.sql import DataFrame, SparkSession, Window, functions as F
from pyspark.sql.types import StructType, StructField, StringType

COLUMNS = ["record_id", "customer_id", "amount", "event_time"]
INPUT_SCHEMA = StructType([StructField(name, StringType(), True) for name in COLUMNS])


def read_source(spark: SparkSession, config: dict) -> DataFrame:
    kind = config["kind"]
    if kind == "jdbc":
        return (spark.read.format("jdbc").option("url", config["uri"])
                .option("dbtable", config["table"]).option("driver", "org.postgresql.Driver")
                .option("user", os.environ[config["user_env"]])
                .option("password", os.environ[config["password_env"]])
                .option("fetchsize", "1000").option("queryTimeout", "120").load())
    reader = spark.read.format(kind)
    if kind in ("csv", "json"):
        reader = reader.schema(INPUT_SCHEMA).option("mode", "FAILFAST")
    if kind == "csv":
        reader = reader.option("header", "true").option("enforceSchema", "false")
    return reader.load(config["uri"])


def normalize(frame: DataFrame, source: str) -> DataFrame:
    if set(COLUMNS) - set(frame.columns):
        raise ValueError("Source schema is missing required columns")
    return frame.select(
        F.trim(F.col("record_id").cast("string")).alias("record_id"),
        F.trim(F.col("customer_id").cast("string")).alias("customer_id"),
        F.expr("try_cast(amount as decimal(18,2))").alias("amount"),
        F.expr("try_cast(event_time as timestamp)").alias("event_time"),
        F.lit(source).alias("source_id"),
        F.to_json(F.struct(*[F.col(c).cast("string").alias(c) for c in COLUMNS])).alias("raw_record"))


def valid_condition():
    return (F.col("record_id").isNotNull() & (F.length("record_id") > 0)
            & F.col("customer_id").isNotNull() & (F.length("customer_id") > 0)
            & F.col("amount").isNotNull() & (F.col("amount") >= 0)
            & F.col("event_time").isNotNull())


def deduplicate(frame: DataFrame) -> DataFrame:
    # Deterministic tie breaking; IDs are namespaced by source, not globally merged.
    window = Window.partitionBy("source_id", "record_id").orderBy(
        F.col("event_time").desc(), F.sha2("raw_record", 256).desc())
    return (frame.withColumn("_rank", F.row_number().over(window)).filter("_rank = 1")
            .drop("_rank", "raw_record").withColumn("event_date", F.to_date("event_time")))


def ingest(spark: SparkSession, spec: dict, output: str):
    frames = []
    counts = {}
    try:
        for name, config in spec["sources"].items():
            frame = normalize(read_source(spark, config), name).persist()
            frames.append(frame)
            count = frame.limit(config["max_rows"] + 1).count()
            if count > config["max_rows"]:
                raise ValueError("SOURCE_ROW_BUDGET_EXCEEDED")
            counts[name] = count
        combined = reduce(DataFrame.unionByName, frames)
        valid = F.coalesce(valid_condition(), F.lit(False))
        invalid = combined.filter(~valid)
        invalid_count = invalid.count()
        total = sum(counts.values())
        if invalid_count:
            invalid.withColumn("reason", F.lit("INVALID_REQUIRED_FIELD"))                .write.mode("errorifexists").parquet(output + "/quarantine")
        if total == 0 or invalid_count / total > spec["max_invalid_fraction"]:
            raise ValueError("DATA_QUALITY_BUDGET_EXCEEDED")
        curated = deduplicate(combined.filter(valid)).persist()
        try:
            written = curated.count()
            if written == 0:
                raise ValueError("NO_VALID_ROWS")
            curated.repartition(spec["output_partitions"], "event_date").write                .mode("errorifexists").partitionBy("event_date").parquet(output + "/data")
            return {"input_rows": total, "invalid_rows": invalid_count, "output_rows": written,
                    "duplicate_rows": total - invalid_count - written, "sources": counts,
                    "data_uri": output + "/data", "quarantine_uri": output + "/quarantine" if invalid_count else None}
        finally:
            curated.unpersist()
    finally:
        for frame in frames:
            frame.unpersist()
