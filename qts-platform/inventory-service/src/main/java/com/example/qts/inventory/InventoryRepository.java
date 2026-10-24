package com.example.qts.inventory;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryRepository {
  private final JdbcClient jdbc;

  InventoryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  record Site(UUID id, String code, String name) {}

  record Asset(UUID id, UUID siteId, String label, String kind) {}

  List<Site> sites(UUID tenant, UUID after, int limit) {
    var q =
        jdbc.sql(
                "SELECT id,code,name FROM sites WHERE tenant_id=:tenant"
                    + (after == null ? "" : " AND id>:after")
                    + " ORDER BY id LIMIT :limit")
            .param("tenant", tenant)
            .param("limit", limit);
    if (after != null) q.param("after", after);
    return q.query(Site.class).list();
  }

  List<Asset> assets(UUID tenant, UUID site, UUID after, int limit) {
    var q =
        jdbc.sql(
                "SELECT id,site_id,label,kind FROM assets WHERE tenant_id=:tenant AND site_id=:site"
                    + (after == null ? "" : " AND id>:after")
                    + " ORDER BY id LIMIT :limit")
            .param("tenant", tenant)
            .param("site", site)
            .param("limit", limit);
    if (after != null) q.param("after", after);
    return q.query(Asset.class).list();
  }

  Optional<Asset> asset(UUID tenant, UUID id) {
    return jdbc.sql("SELECT id,site_id,label,kind FROM assets WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", tenant)
        .param("id", id)
        .query(Asset.class)
        .optional();
  }
}
