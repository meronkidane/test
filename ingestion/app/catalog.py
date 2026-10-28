"""Only operators define network locations, SQL tables, credentials and output paths."""
import json
import re
from pathlib import Path
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field, model_validator

NAME = r"^[a-z][a-z0-9_-]{0,62}$"
class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

class Source(Strict):
    kind: Literal["jdbc", "csv", "json", "parquet"]
    uri: str
    table: str | None = None
    user_env: str | None = None
    password_env: str | None = None
    max_rows: int = Field(default=1_000_000, ge=1, le=100_000_000)

    @model_validator(mode="after")
    def location(self):
        if self.kind == "jdbc":
            if not re.fullmatch(r"jdbc:postgresql://[a-zA-Z0-9.-]+:[0-9]+/[a-zA-Z0-9_]+", self.uri):
                raise ValueError("Only configured PostgreSQL URLs without query credentials are allowed")
            if not self.table or not re.fullmatch(r"[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)?", self.table):
                raise ValueError("A table identifier is required, not SQL")
            for name in (self.user_env, self.password_env):
                if not name or not re.fullmatch(r"[A-Z][A-Z0-9_]*", name):
                    raise ValueError("Credential environment reference required")
        elif not re.fullmatch(r"s3a://[a-z0-9.-]+/[a-zA-Z0-9_./=-]+", self.uri) or ".." in self.uri:
            raise ValueError("File sources must use an explicit S3 prefix")
        return self

class Dataset(Strict):
    sources: dict[str, Source]
    output: str
    max_invalid_fraction: float = Field(default=0.1, ge=0, le=1)
    output_partitions: int = Field(default=2, ge=1, le=128)

    @model_validator(mode="after")
    def names(self):
        if not self.sources or not all(re.fullmatch(NAME, key) for key in self.sources):
            raise ValueError("Invalid source identifiers")
        if not re.fullmatch(r"s3a://[a-z0-9.-]+/[a-zA-Z0-9_/-]+", self.output) or ".." in self.output:
            raise ValueError("Invalid output prefix")
        return self

class Principal(Strict):
    token_env: str
    datasets: list[str]

class Catalog(Strict):
    datasets: dict[str, Dataset]
    principals: dict[str, Principal]

    @model_validator(mode="after")
    def identifiers(self):
        if not all(re.fullmatch(NAME, k) for k in [*self.datasets, *self.principals]):
            raise ValueError("Invalid dataset or tenant identifier")
        if any(set(p.datasets) - self.datasets.keys() for p in self.principals.values()):
            raise ValueError("Unknown dataset grant")
        return self

    @classmethod
    def load(cls, path: str):
        return cls.model_validate(json.loads(Path(path).read_text()))

    def snapshot(self, tenant: str, dataset: str, sources: list[str]):
        if tenant not in self.principals or dataset not in self.principals[tenant].datasets:
            raise PermissionError("Dataset not authorized")
        config = self.datasets[dataset]
        if not sources or len(sources) != len(set(sources)) or set(sources) - config.sources.keys():
            raise ValueError("Unknown or duplicate source")
        return dict(dataset=dataset, sources={s: config.sources[s].model_dump() for s in sorted(sources)},
                    output=f"{config.output.rstrip('/')}/{tenant}/{dataset}",
                    max_invalid_fraction=config.max_invalid_fraction,
                    output_partitions=config.output_partitions)
