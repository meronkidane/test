package com.example.qts.inventory;

import java.util.*;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@Transactional(readOnly = true, timeout = 5)
class InventoryController {
  private final InventoryRepository repository;

  InventoryController(InventoryRepository repository) {
    this.repository = repository;
  }

  record Page<T>(List<T> items, UUID nextCursor) {}

  private int bound(int limit) {
    if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return limit;
  }

  private <T> Page<T> page(List<T> rows, int limit, Function<T, UUID> id) {
    boolean more = rows.size() > limit;
    var items = more ? List.copyOf(rows.subList(0, limit)) : rows;
    return new Page<>(items, more ? id.apply(items.getLast()) : null);
  }

  @GetMapping("/sites")
  Page<InventoryRepository.Site> sites(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) UUID after) {
    return page(
        repository.sites(Access.tenant(jwt), after, bound(limit) + 1),
        limit,
        InventoryRepository.Site::id);
  }

  @GetMapping("/sites/{site}/assets")
  Page<InventoryRepository.Asset> assets(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID site,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) UUID after) {
    return page(
        repository.assets(Access.tenant(jwt), site, after, bound(limit) + 1),
        limit,
        InventoryRepository.Asset::id);
  }

  @GetMapping("/assets/{id}")
  InventoryRepository.Asset asset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return repository
        .asset(Access.tenant(jwt), id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
