create table requests (
 id uuid primary key, tenant varchar(160) not null, title varchar(160) not null,
 description varchar(4000) not null, priority varchar(16) not null check (priority in ('LOW','NORMAL','HIGH')),
 status varchar(16) not null check (status in ('OPEN','IN_PROGRESS','RESOLVED')),
 requester varchar(255) not null, version bigint not null default 0,
 created_at timestamp with time zone not null, updated_at timestamp with time zone not null
);
create index requests_tenant_created on requests(tenant, created_at desc, id desc);
