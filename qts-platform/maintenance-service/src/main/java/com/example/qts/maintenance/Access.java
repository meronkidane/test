package com.example.qts.maintenance;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

final class Access {
  private Access() {}

  static UUID tenant(Jwt jwt) {
    try {
      String value = jwt.getClaimAsString("tenant_id");
      UUID id = UUID.fromString(value);
      if (!id.toString().equals(value)) throw new IllegalArgumentException();
      return id;
    } catch (IllegalArgumentException | NullPointerException error) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }
}
