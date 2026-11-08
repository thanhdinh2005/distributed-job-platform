package com.backend.jobplatform.api.dto;

import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.enums.JobStatus;

import java.time.Instant;
import java.util.UUID;

/** Read projection of a job. Never accepts writes — creation goes through {@code JobCreator}. */
public record JobResponse(
    UUID id,
    String jobType,
    JobStatus status,
    int priority,
    int attemptCount,
    int maxAttempts,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt,
    Instant queuedAt,
    Instant startedAt,
    Instant completedAt
) {

  public static JobResponse from(Job job) {
    return new JobResponse(
        job.getId(),
        job.getJobType(),
        job.getStatus(),
        job.getPriority(),
        job.getAttemptCount(),
        job.getMaxAttempts(),
        job.getErrorMessage(),
        job.getCreatedAt(),
        job.getUpdatedAt(),
        job.getQueuedAt(),
        job.getStartedAt(),
        job.getCompletedAt());
  }
}
