package com.example.qts.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

  @BeforeEach
  void seed() {
    db.execute("TRUNCATE assets,sites");
    db.update(
        "INSERT INTO sites VALUES (?::uuid,?::uuid,'TEST-1','Example site'), (?::uuid,?::uuid,'OTHER','Other tenant site')",
        TENANT,
        SITE,
        OTHER,
        SITE);
    db.update(
        "INSERT INTO assets VALUES (?::uuid,?::uuid,?::uuid,'Rack A','RACK'), (?::uuid,?::uuid,?::uuid,'UPS B','UPS')",
        TENANT,
        ASSET,
        SITE,
        TENANT,
        "20000000-0000-0000-0000-000000000002",
        SITE);
  }

  @Test
  void sitesAreTenantScoped() throws Exception {
    mvc.perform(get("/api/v1/sites").with(access(TENANT, "inventory:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].code").value("TEST-1"));
  }

  @Test
  void assetsUseBoundedCursor() throws Exception {
    mvc.perform(
            get("/api/v1/sites/" + SITE + "/assets?limit=1").with(access(TENANT, "inventory:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextCursor").value(ASSET));
    mvc.perform(
            get("/api/v1/sites/" + SITE + "/assets?limit=1&after=" + ASSET)
                .with(access(TENANT, "inventory:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].label").value("UPS B"))
        .andExpect(jsonPath("$.nextCursor").isEmpty());
  }

  @Test
  void crossTenantAssetIsNotFound() throws Exception {
    mvc.perform(get("/api/v1/assets/" + ASSET).with(access(OTHER, "inventory:read")))
        .andExpect(status().isNotFound());
  }

  @Test
  void authenticationScopeAndTenantRequired() throws Exception {
    mvc.perform(get("/api/v1/sites")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/sites").with(jwt())).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/sites").with(access("invalid", "inventory:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  void badInputsRejected() throws Exception {
    for (String q : List.of("limit=101", "limit=0", "after=invalid"))
      mvc.perform(get("/api/v1/sites?" + q).with(access(TENANT, "inventory:read")))
          .andExpect(status().isBadRequest());
  }

  @Test
  void databaseFailureSanitized() throws Exception {
    db.execute("ALTER TABLE sites RENAME TO hidden_sites");
    try {
      mvc.perform(get("/api/v1/sites").with(access(TENANT, "inventory:read")))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value("Service data is temporarily unavailable."));
    } finally {
      db.execute("ALTER TABLE hidden_sites RENAME TO sites");
    }
  }
}
