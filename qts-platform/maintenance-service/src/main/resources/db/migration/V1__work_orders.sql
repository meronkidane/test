CREATE TABLE work_orders (
 tenant_id UUID NOT NULL,
 id UUID NOT NULL,
 request_id UUID NOT NULL,
 asset_id UUID NOT NULL,
 summary VARCHAR(500) NOT NULL CHECK (length(trim(summary))>0),
 priority VARCHAR(16) NOT NULL CHECK (priority IN ('ROUTINE','URGENT')),
 status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (tenant_id,id),
 UNIQUE (tenant_id,request_id)
);
