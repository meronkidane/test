package com.example.accounts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {
  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/me",
                        "/api/v1/me/accounts",
                        "/api/v1/me/accounts/*")
                    .hasAuthority("SCOPE_accounts:read")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
        .build();
  }
}
