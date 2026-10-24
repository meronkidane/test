package com.example.accounts;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepository {
  private final JdbcClient jdbc;

  AccountRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  record Customer(UUID id, String displayName) {}

  record Account(UUID id, String currency, BigDecimal balance, String status) {}

  Optional<Customer> customer(String subject) {
    return jdbc.sql("SELECT id, display_name FROM customers WHERE auth_subject = :subject")
        .param("subject", subject)
        .query(Customer.class)
        .optional();
  }

  List<Account> accounts(String subject, UUID after, int limit) {
    String cursor = after == null ? "" : " AND a.id > :after";
    var query =
        jdbc.sql(
                "SELECT a.id, a.currency, a.balance, a.status FROM accounts a JOIN customers c ON c.id = a.customer_id WHERE c.auth_subject = :subject"
                    + cursor
                    + " ORDER BY a.id LIMIT :limit")
            .param("subject", subject)
            .param("limit", limit);
    if (after != null) query.param("after", after);
    return query.query(Account.class).list();
  }

  Optional<Account> account(String subject, UUID id) {
    return jdbc.sql(
            "SELECT a.id, a.currency, a.balance, a.status FROM accounts a JOIN customers c ON c.id = a.customer_id WHERE c.auth_subject = :subject AND a.id = :id")
        .param("subject", subject)
        .param("id", id)
        .query(Account.class)
        .optional();
  }
}
