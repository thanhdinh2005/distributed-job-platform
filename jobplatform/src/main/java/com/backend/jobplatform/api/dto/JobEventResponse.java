package com.backend.jobplatform.api.dto;

import com.backend.jobplatform.domain.entity.JobEvent;
import com.backend.jobplatform.domain.entity.enums.JobStatus;

import java.time.Instant;

/**
 * One row of the audit trail.
 *
 * <p>Reads only scalar columns — never {@code event.getJob()}, which is a lazy proxy that would
 * fail outside the transaction.
 */
public record JobEventResponse(
    JobStatus fromStatus,
    JobStatus toStatus,
    String message,
    Instant createdAt
) {

  public static JobEventResponse from(JobEvent event) {
    return new JobEventResponse(
        event.getFromStatus(),
        event.getToStatus(),
        event.getMessage(),
        event.getCreatedAt());
  }
}
