package com.example.qts.maintenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example.test",
      "spring.security.oauth2.resourceserver.jwt.audiences=qts-test"
    })
@AutoConfigureMockMvc
@Testcontainers
class ApiTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.4");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @MockitoBean JwtDecoder decoder;
  static final String TENANT = "00000000-0000-0000-0000-000000000001";
  static final String OTHER = "00000000-0000-0000-0000-000000000002";
  static final String SITE = "10000000-0000-0000-0000-000000000001";
  static final String ASSET = "20000000-0000-0000-0000-000000000001";

  org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .JwtRequestPostProcessor
      access(String tenant, String scope) {
    return jwt()
        .jwt(j -> j.claim("tenant_id", tenant))
        .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
  }

  @Autowired WorkOrderService service;

  @BeforeEach
  void reset() {
    db.execute("TRUNCATE work_orders");
  }

  String payload(String request) {
    return "{\"requestId\":\""
        + request
        + "\",\"assetId\":\""
        + ASSET
        + "\",\"summary\":\"Inspect UPS\",\"priority\":\"ROUTINE\"}";
  }

  String create(String request) throws Exception {
    return mvc.perform(
            post("/api/v1/work-orders")
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(request)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void retriesAreIdempotentAndChangedPayloadConflicts() throws Exception {
    String request = UUID.randomUUID().toString();
    String first = create(request);
    assertEquals(first, create(request));
    mvc.perform(
            post("/api/v1/work-orders")
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(request).replace("Inspect UPS", "Different")))
        .andExpect(status().isConflict());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM work_orders", Integer.class));
  }

  @Test
  void concurrentRetriesCreateOneOrder() throws Exception {
    var request =
        new WorkOrderService.Create(
            UUID.randomUUID(),
            UUID.fromString(ASSET),
            "Concurrent inspection",
            WorkOrderService.Priority.ROUTINE);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.create(UUID.fromString(TENANT), request));
      var second = executor.submit(() -> service.create(UUID.fromString(TENANT), request));
      assertEquals(first.get().id(), second.get().id());
    }
    assertEquals(1, db.queryForObject("SELECT count(*) FROM work_orders", Integer.class));
  }

  @Test
  void transitionsEnforceVersionAndTerminalStates() throws Exception {
    var order =
        service.create(
            UUID.fromString(TENANT),
            new WorkOrderService.Create(
                UUID.randomUUID(),
                UUID.fromString(ASSET),
                "Inspect",
                WorkOrderService.Priority.URGENT));
    String path = "/api/v1/work-orders/" + order.id();
    mvc.perform(
            patch(path)
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\",\"expectedVersion\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch(path)
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\",\"expectedVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            patch(path)
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\",\"expectedVersion\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch(path)
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\",\"expectedVersion\":1}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch(path)
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\",\"expectedVersion\":2}"))
        .andExpect(status().isConflict());
  }

  @Test
  void tenantsCannotReadOrChangeOthersOrders() throws Exception {
    var order =
        service.create(
            UUID.fromString(TENANT),
            new WorkOrderService.Create(
                UUID.randomUUID(),
                UUID.fromString(ASSET),
                "Inspect",
                WorkOrderService.Priority.ROUTINE));
    mvc.perform(get("/api/v1/work-orders/" + order.id()).with(access(OTHER, "maintenance:read")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/work-orders").with(access(OTHER, "maintenance:read")))
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(
            patch("/api/v1/work-orders/" + order.id())
                .with(access(OTHER, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\",\"expectedVersion\":0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void authenticationValidationAndScopes() throws Exception {
    mvc.perform(get("/api/v1/work-orders")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/work-orders")
                .with(access(TENANT, "maintenance:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(UUID.randomUUID().toString())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/work-orders")
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/work-orders?limit=101").with(access(TENANT, "maintenance:read")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/work-orders").with(access("invalid", "maintenance:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingVersionIsRejected() throws Exception {
    mvc.perform(
            patch("/api/v1/work-orders/" + UUID.randomUUID())
                .with(access(TENANT, "maintenance:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void databaseFailureSanitized() throws Exception {
    db.execute("ALTER TABLE work_orders RENAME TO hidden_work_orders");
    try {
      mvc.perform(get("/api/v1/work-orders").with(access(TENANT, "maintenance:read")))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value("Service data is temporarily unavailable."));
    } finally {
      db.execute("ALTER TABLE hidden_work_orders RENAME TO work_orders");
    }
  }
}
