package com.backend.jobplatform.domain.exception;

import com.backend.jobplatform.domain.entity.enums.JobStatus;
import lombok.Getter;

import java.util.UUID;

/**
 * Thrown when a transition is attempted that the state machine does not allow.
 *
 * <p>Runtime exception on purpose: callers are not expected to recover by branching on it — an
 * illegal transition is a bug in the caller, not a business outcome.
 */
@Getter
public class IllegalTransitionException extends RuntimeException {

  private final UUID jobId;
  private final JobStatus from;
  private final JobStatus to;

  public IllegalTransitionException(UUID jobId, JobStatus from, JobStatus to) {
    super("Illegal job status transition for job " + jobId + ": " + from + " -> " + to);
    this.jobId = jobId;
    this.from = from;
    this.to = to;
  }

}
