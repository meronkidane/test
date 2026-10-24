package com.example.accounts;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example.test",
      "spring.security.oauth2.resourceserver.jwt.audiences=accounts"
    })
@AutoConfigureMockMvc
@Testcontainers
class AccountApiTest {
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
  static final String FIRST = "10000000-0000-0000-0000-000000000001";
  static final String SECOND = "10000000-0000-0000-0000-000000000002";
  static final String OTHER = "10000000-0000-0000-0000-000000000003";

  @BeforeEach
  void seed() {
    db.execute("TRUNCATE accounts, customers");
    db.update(
        "INSERT INTO customers VALUES ('00000000-0000-0000-0000-000000000001','alice','Alice'), ('00000000-0000-0000-0000-000000000002','bob','Bob')");
    db.update(
        "INSERT INTO accounts VALUES (?::uuid,'00000000-0000-0000-0000-000000000001','USD',123.4567,'ACTIVE'), (?::uuid,'00000000-0000-0000-0000-000000000001','EUR',0,'FROZEN'), (?::uuid,'00000000-0000-0000-0000-000000000002','USD',900,'ACTIVE')",
        FIRST,
        SECOND,
        OTHER);
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .JwtRequestPostProcessor
      alice() {
    return jwt()
        .jwt(j -> j.subject("alice"))
        .authorities(new SimpleGrantedAuthority("SCOPE_accounts:read"));
  }

  @Test
  void customerProfile() throws Exception {
    mvc.perform(get("/api/v1/me").with(alice()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Alice"))
        .andExpect(jsonPath("$.authSubject").doesNotExist());
  }

  @Test
  void exactBalanceAndOwnership() throws Exception {
    mvc.perform(get("/api/v1/me/accounts/" + FIRST).with(alice()))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("123.4567")));
    mvc.perform(get("/api/v1/me/accounts/" + OTHER).with(alice())).andExpect(status().isNotFound());
  }

  @Test
  void keysetPaginationAndIsolation() throws Exception {
    mvc.perform(get("/api/v1/me/accounts?limit=1").with(alice()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(FIRST))
        .andExpect(jsonPath("$.nextCursor").value(FIRST));
    mvc.perform(get("/api/v1/me/accounts?limit=1&after=" + FIRST).with(alice()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(SECOND))
        .andExpect(jsonPath("$.nextCursor").isEmpty());
  }

  @Test
  void rejectInvalidInputs() throws Exception {
    for (String query : new String[] {"limit=0", "limit=101", "limit=no", "after=bad"})
      mvc.perform(get("/api/v1/me/accounts?" + query).with(alice()))
          .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/me/accounts/not-a-uuid").with(alice()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authenticationAndScopeRequired() throws Exception {
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").with(jwt())).andExpect(status().isForbidden());
    when(decoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownSubjectAndInjectionCannotReadOtherCustomer() throws Exception {
    var unknown =
        jwt()
            .jwt(j -> j.subject("alice' OR 1=1 --"))
            .authorities(new SimpleGrantedAuthority("SCOPE_accounts:read"));
    mvc.perform(get("/api/v1/me").with(unknown)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/me/accounts").with(unknown))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void missingAccount() throws Exception {
    mvc.perform(get("/api/v1/me/accounts/20000000-0000-0000-0000-000000000001").with(alice()))
        .andExpect(status().isNotFound());
  }

  @Test
  void databaseFailureIsSanitized() throws Exception {
    db.execute("ALTER TABLE accounts RENAME TO temporarily_unavailable_accounts");
    try {
      mvc.perform(get("/api/v1/me/accounts").with(alice()))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value("Customer data is temporarily unavailable."))
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
    } finally {
      db.execute("ALTER TABLE temporarily_unavailable_accounts RENAME TO accounts");
    }
  }
}
