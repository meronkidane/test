package org.example.desk;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretConfigurationTest {
 @TempDir Path secrets;
 @Configuration(proxyBeanMethods = false) static class ConfigurationProbe {}

 @Test void mountedSecretResolvesDatasourcePasswordWithoutEnvironmentVariable() throws Exception {
  Files.writeString(secrets.resolve("DB_PASSWORD"), "fixture-only-password");
  try (var context = new SpringApplicationBuilder(ConfigurationProbe.class)
      .web(WebApplicationType.NONE)
      .run("--spring.config.import=configtree:" + secrets + "/")) {
   assertEquals("fixture-only-password", context.getEnvironment().getProperty("spring.datasource.password"));
  }
 }
}
