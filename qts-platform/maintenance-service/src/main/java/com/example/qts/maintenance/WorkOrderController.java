package com.example.qts.maintenance;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/work-orders")
class WorkOrderController {
  private final WorkOrderService service;

  WorkOrderController(WorkOrderService service) {
    this.service = service;
  }

  record Page(List<WorkOrderService.WorkOrder> items, UUID nextCursor) {}

  @PostMapping
  WorkOrderService.WorkOrder create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkOrderService.Create body) {
    return service.create(Access.tenant(jwt), body);
  }

  @GetMapping("/{id}")
  WorkOrderService.WorkOrder get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.get(Access.tenant(jwt), id);
  }

  @GetMapping
  Page list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) UUID after) {
    if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var rows = service.list(Access.tenant(jwt), after, limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? List.copyOf(rows.subList(0, limit)) : rows;
    return new Page(items, more ? items.getLast().id() : null);
  }

  @PatchMapping("/{id}")
  WorkOrderService.WorkOrder change(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody WorkOrderService.Change body) {
    return service.change(Access.tenant(jwt), id, body);
  }
}
