package com.example.accounts;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me")
@Transactional(readOnly = true, timeout = 5)
class AccountController {
  private final AccountRepository repository;

  AccountController(AccountRepository repository) {
    this.repository = repository;
  }

  record AccountPage(List<AccountRepository.Account> items, UUID nextCursor) {}

  private String subject(Jwt jwt) {
    if (jwt.getSubject() == null || jwt.getSubject().isBlank())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return jwt.getSubject();
  }

  @GetMapping
  AccountRepository.Customer customer(@AuthenticationPrincipal Jwt jwt) {
    return repository
        .customer(subject(jwt))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @GetMapping("/accounts")
  AccountPage accounts(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) UUID after) {
    if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var rows = repository.accounts(subject(jwt), after, limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? List.copyOf(rows.subList(0, limit)) : rows;
    return new AccountPage(items, more ? items.getLast().id() : null);
  }

  @GetMapping("/accounts/{id}")
  AccountRepository.Account account(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return repository
        .account(subject(jwt), id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
