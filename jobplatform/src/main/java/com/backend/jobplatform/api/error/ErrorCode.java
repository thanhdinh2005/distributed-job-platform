package com.backend.jobplatform.api.error;

import org.springframework.http.HttpStatus;

/**
 * The stable error contract. {@link #code()} is what a client branches on — never rename a
 * constant once it has shipped; change {@link #title()} instead.
 *
 * <p>Lives in {@code api/} on purpose: HTTP statuses are a transport concern, and the domain
 * packages must not depend on them.
 */
public enum ErrorCode {

  JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Job not found"),
  JOB_ILLEGAL_TRANSITION(HttpStatus.CONFLICT, "Illegal status transition"),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
  ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Route not found"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  public String code() {
    return name();
  }
}
