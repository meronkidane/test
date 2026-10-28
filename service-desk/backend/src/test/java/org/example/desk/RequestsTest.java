package org.example.desk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:desk;DB_CLOSE_DELAY=-1", "spring.datasource.password=test"})
class RequestsTest {
 @Autowired WebApplicationContext context;
 @MockitoBean JwtDecoder decoder;
 MockMvc mvc;
 @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
 RequestPostProcessor user(String tenant, String role) { return jwt().jwt(j -> j.subject("alice").claim("tenant", tenant)).authorities(new SimpleGrantedAuthority("ROLE_" + role)); }
 String create() throws Exception {
  var body = mvc.perform(post("/api/requests").with(user("demo","AUTHOR")).contentType("application/json")
    .content("{\"title\":\"Restore VPN access\",\"description\":\"VPN login fails after enrollment\",\"priority\":\"HIGH\"}"))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0)).andReturn().getResponse().getContentAsString();
  return JsonMapper.builder().build().readTree(body).path("id").asText();
 }
 @Test void onlyGetHealthIsPublicAndDoesNotExposeDetails() throws Exception {
  mvc.perform(get("/actuator/health")).andExpect(status().isOk())
    .andExpect(jsonPath("$.status").value("UP"))
    .andExpect(jsonPath("$.components").doesNotExist())
    .andExpect(jsonPath("$.details").doesNotExist());
  mvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
  mvc.perform(get("/actuator/health/private")).andExpect(status().isUnauthorized());
  mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/requests").contentType("application/json").content("{}"))
    .andExpect(status().isUnauthorized());
  mvc.perform(patch("/api/requests/unknown/status").contentType("application/json").content("{}"))
    .andExpect(status().isUnauthorized());
 }
 @Test void authenticationAndValidation() throws Exception {
  mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/requests").with(jwt())).andExpect(status().isForbidden());
  mvc.perform(get("/api/requests?size=101").with(user("demo","AUTHOR"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/requests").with(user("demo","AUTHOR")).contentType("application/json")
    .content("{\"title\":\" \",\"description\":\"x\",\"priority\":\"NORMAL\"}")).andExpect(status().isBadRequest());
 }
 @Test void tenantIsolationAndRoleDenial() throws Exception {
  String id = create();
  mvc.perform(get("/api/requests/"+id).with(user("other","ADMIN"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/requests").with(user("other","ADMIN"))).andExpect(jsonPath("$.items").isEmpty());
  mvc.perform(patch("/api/requests/"+id+"/status").with(user("demo","AUTHOR")).contentType("application/json")
    .content("{\"status\":\"IN_PROGRESS\",\"version\":0}")).andExpect(status().isForbidden());
  mvc.perform(patch("/api/requests/"+id+"/status").with(user("other","ADMIN")).contentType("application/json")
    .content("{\"status\":\"IN_PROGRESS\",\"version\":0}")).andExpect(status().isNotFound());
 }
 @Test void transitionsAndOptimisticLocking() throws Exception {
  String id = create(); String path = "/api/requests/"+id+"/status";
  mvc.perform(patch(path).with(user("demo","LEAD")).contentType("application/json")
    .content("{\"status\":\"RESOLVED\",\"version\":0}")).andExpect(status().isConflict());
  mvc.perform(patch(path).with(user("demo","LEAD")).contentType("application/json")
    .content("{\"status\":\"IN_PROGRESS\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(patch(path).with(user("demo","LEAD")).contentType("application/json")
    .content("{\"status\":\"RESOLVED\",\"version\":0}")).andExpect(status().isConflict());
  mvc.perform(patch(path).with(user("demo","LEAD")).contentType("application/json")
    .content("{\"status\":\"RESOLVED\",\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
 }
}
