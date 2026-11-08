package com.backend.jobplatform.domain.entity.enums;

/**
 * Job lifecycle states (must match the {@code chk_jobs_status} constraint in V1).
 *
 * <p>The transition table lives here and nowhere else. Do not add a second copy of these rules in a
 * service or controller — one table means one place to reason about, and one place to test.
 *
 * <p>The {@code switch} below is exhaustive on purpose: no {@code default} branch. Adding a status
 * to the enum breaks compilation until its legal outgoing edges are written down.
 */
public enum JobStatus {
  CREATED,
  QUEUED,
  PROCESSING,
  SUCCEEDED,
  FAILED_RETRYABLE,
  DEAD_LETTER,
  CANCELLED;

  /**
   * Whether {@code this -> next} is a legal transition.
   *
   * <p>Deliberately not allowed:
   * <ul>
   *   <li>{@code PROCESSING -> CANCELLED} — cancelling mid-execution is a coordination problem,
   *       not a status update (the handler may already be running).</li>
   *   <li>{@code PROCESSING -> QUEUED} — re-queueing goes through {@code FAILED_RETRYABLE} first,
   *       so every retry is recorded as a failure.</li>
   * </ul>
   */
  public boolean canTransitionTo(JobStatus next) {
    if (next == null) {
      return false;
    }
    return switch (this) {
      case CREATED -> next == QUEUED || next == CANCELLED;
      case QUEUED -> next == PROCESSING || next == CANCELLED;
      case PROCESSING -> next == SUCCEEDED || next == FAILED_RETRYABLE || next == DEAD_LETTER;
      case FAILED_RETRYABLE -> next == QUEUED || next == DEAD_LETTER || next == CANCELLED;
      case SUCCEEDED, DEAD_LETTER, CANCELLED -> false;
    };
  }

  /** Terminal states accept no further transition. */
  public boolean isTerminal() {
    return this == SUCCEEDED || this == DEAD_LETTER || this == CANCELLED;
  }
}
