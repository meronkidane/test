package com.example.accounts;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException error) {
    return ProblemDetail.forStatus(error.getStatusCode());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail badInput() {
    return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ProblemDetail databaseUnavailable() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Customer data is temporarily unavailable.");
  }
}
