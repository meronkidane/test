package com.example.qts.maintenance;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(timeout = 5)
class WorkOrderService {
  enum Priority {
    ROUTINE,
    URGENT
  }

  enum Status {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
  }

  record Create(
      @NotNull UUID requestId,
      @NotNull UUID assetId,
      @NotBlank @Size(max = 500) String summary,
      @NotNull Priority priority) {}

  record Change(@NotNull Status status, @NotNull @Min(0) Long expectedVersion) {}

  record WorkOrder(
      UUID id, UUID assetId, String summary, String priority, String status, long version) {}

  private final JdbcClient jdbc;
  private static final String COLUMNS = "id,asset_id,summary,priority,status,version";

  WorkOrderService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  WorkOrder create(UUID tenant, Create request) {
    jdbc.sql(
            "INSERT INTO work_orders(tenant_id,id,request_id,asset_id,summary,priority,status) VALUES (:tenant,:id,:request,:asset,:summary,:priority,'OPEN') ON CONFLICT (tenant_id,request_id) DO NOTHING")
        .param("tenant", tenant)
        .param("id", UUID.randomUUID())
        .param("request", request.requestId())
        .param("asset", request.assetId())
        .param("summary", request.summary())
        .param("priority", request.priority().name())
        .update();
    var result =
        jdbc.sql(
                "SELECT "
                    + COLUMNS
                    + " FROM work_orders WHERE tenant_id=:tenant AND request_id=:request")
            .param("tenant", tenant)
            .param("request", request.requestId())
            .query(WorkOrder.class)
            .single();
    if (!result.assetId().equals(request.assetId())
        || !result.summary().equals(request.summary())
        || !result.priority().equals(request.priority().name()))
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    return result;
  }

  @Transactional(readOnly = true, timeout = 5)
  WorkOrder get(UUID tenant, UUID id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM work_orders WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", tenant)
        .param("id", id)
        .query(WorkOrder.class)
        .optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @Transactional(readOnly = true, timeout = 5)
  List<WorkOrder> list(UUID tenant, UUID after, int limit) {
    var q =
        jdbc.sql(
                "SELECT "
                    + COLUMNS
                    + " FROM work_orders WHERE tenant_id=:tenant"
                    + (after == null ? "" : " AND id>:after")
                    + " ORDER BY id LIMIT :limit")
            .param("tenant", tenant)
            .param("limit", limit);
    if (after != null) q.param("after", after);
    return q.query(WorkOrder.class).list();
  }

  WorkOrder change(UUID tenant, UUID id, Change request) {
    var current = get(tenant, id);
    boolean allowed =
        switch (Status.valueOf(current.status())) {
          case OPEN ->
              request.status() == Status.IN_PROGRESS || request.status() == Status.CANCELLED;
          case IN_PROGRESS ->
              request.status() == Status.COMPLETED || request.status() == Status.CANCELLED;
          default -> false;
        };
    if (!allowed || current.version() != request.expectedVersion())
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    int updated =
        jdbc.sql(
                "UPDATE work_orders SET status=:status,version=version+1 WHERE tenant_id=:tenant AND id=:id AND version=:version")
            .param("status", request.status().name())
            .param("tenant", tenant)
            .param("id", id)
            .param("version", request.expectedVersion())
            .update();
    if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT);
    return get(tenant, id);
  }
}
