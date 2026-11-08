package com.backend.jobplatform.domain.exception;

import lombok.Getter;

import java.util.UUID;

/** Thrown when a job id does not exist. Explicit rather than returning {@code null}. */
@Getter
public class JobNotFoundException extends RuntimeException {

  private final UUID jobId;

  public JobNotFoundException(UUID jobId) {
    super("Job not found: " + jobId);
    this.jobId = jobId;
  }

}
