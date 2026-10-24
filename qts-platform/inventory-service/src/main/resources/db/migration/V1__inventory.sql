CREATE TABLE sites (
 tenant_id UUID NOT NULL,
 id UUID NOT NULL,
 code VARCHAR(32) NOT NULL,
 name VARCHAR(160) NOT NULL,
 PRIMARY KEY (tenant_id,id),
 UNIQUE (tenant_id,code)
);
CREATE TABLE assets (
 tenant_id UUID NOT NULL,
 id UUID NOT NULL,
 site_id UUID NOT NULL,
 label VARCHAR(160) NOT NULL,
 kind VARCHAR(32) NOT NULL CHECK (kind IN ('RACK','SERVER','UPS','COOLING','NETWORK')),
 PRIMARY KEY (tenant_id,id),
 FOREIGN KEY (tenant_id,site_id) REFERENCES sites(tenant_id,id)
);
CREATE INDEX assets_site_cursor ON assets(tenant_id,site_id,id);
