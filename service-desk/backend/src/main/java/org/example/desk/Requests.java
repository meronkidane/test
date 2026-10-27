package org.example.desk;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class Requests {
 private final JdbcClient db;
 public Requests(JdbcClient db) { this.db = db; }
 public enum Priority { LOW, NORMAL, HIGH }
 public enum Status { OPEN, IN_PROGRESS, RESOLVED }
 public record Create(@NotBlank @Size(max=160) String title, @NotBlank @Size(max=4000) String description, @NotNull Priority priority) {}
 public record Transition(@NotNull Status status, @NotNull @PositiveOrZero Long version) {}
 public record Item(UUID id, String title, String description, Priority priority, Status status, String requester, long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
 public record Page(List<Item> items, int page, int size, boolean hasNext) {}

 private String tenant(Jwt jwt) {
  Object value = jwt.getClaims().get("tenant");
  if (!(value instanceof String name) || name.isBlank()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant claim required");
  return name;
 }
 @GetMapping
 public Page list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="0") int page,
                  @RequestParam(defaultValue="20") int size, @RequestParam(required=false) Status status) {
  if (page < 0 || page > 10000 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
  var query = db.sql("select * from requests where tenant=:tenant" + (status == null ? "" : " and status=:status") + " order by created_at desc, id desc limit :limit offset :offset")
    .param("tenant", tenant(jwt)).param("limit", size + 1).param("offset", page * size);
  if (status != null) query = query.param("status", status.name());
  var items = query.query(Item.class).list();
  return new Page(items.stream().limit(size).toList(), page, size, items.size() > size);
 }
 @GetMapping("/{id}")
 public Item get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return find(tenant(jwt), id); }
 private Item find(String tenant, UUID id) {
  return db.sql("select * from requests where tenant=:tenant and id=:id").param("tenant", tenant).param("id", id)
    .query(Item.class).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
 }
 @PostMapping
 @ResponseStatus(HttpStatus.CREATED)
 @Transactional
 public Item create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create input) {
  String tenant = tenant(jwt); var id = UUID.randomUUID();
  db.sql("insert into requests(id,tenant,title,description,priority,status,requester,version,created_at,updated_at) values(:id,:tenant,:title,:description,:priority,'OPEN',:requester,0,current_timestamp,current_timestamp)")
    .param("id", id).param("tenant", tenant).param("title", input.title().strip()).param("description", input.description().strip())
    .param("priority", input.priority().name()).param("requester", jwt.getSubject()).update();
  return find(tenant, id);
 }
 @PatchMapping("/{id}/status")
 @PreAuthorize("hasAnyRole('LEAD','ADMIN')")
 @Transactional
 public Item transition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Transition input) {
  String tenant = tenant(jwt); var item = find(tenant, id);
  if (item.version() != input.version()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request changed; refresh before updating");
  Status next = switch (item.status()) { case OPEN -> Status.IN_PROGRESS; case IN_PROGRESS -> Status.RESOLVED; case RESOLVED -> Status.OPEN; };
  if (input.status() != next) throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid workflow transition");
  int updated = db.sql("update requests set status=:status, version=version+1, updated_at=current_timestamp where id=:id and tenant=:tenant and version=:version")
    .param("status", next.name()).param("id", id).param("tenant", tenant).param("version", input.version()).update();
  if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request changed; refresh before updating");
  return find(tenant, id);
 }
}
