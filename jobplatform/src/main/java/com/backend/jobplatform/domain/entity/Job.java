package com.backend.jobplatform.domain.entity;

import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.exception.IllegalTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Job {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "job_type", nullable = false, length = 100)
  //@Enumerated(EnumType.STRING)
  private String jobType;

  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private JobStatus status = JobStatus.CREATED;

  @Column(name = "priority", nullable = false)
  private int priority = 0;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount = 0;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts = 3;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "queued_at")
  private Instant queuedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  /**
   * Timestamps are set by the entity, not by {@code @CreatedDate} / {@code @LastModifiedDate}.
   *
   * <p>Those are framework callbacks: auditing runs on persist inside a persistence context, so a
   * plain {@code new Job()} in a unit test has a null timestamp, and an injected {@link
   * java.time.Clock} would be ignored in favour of the framework's own. One clock, one source of
   * truth.
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static Job create(String type, Map<String, Object> payload, Instant now) {
    Job job = new Job();

    job.jobType = type;
    job.status = JobStatus.CREATED;
    job.payload = payload;
    job.createdAt = now;
    job.updatedAt = now;

    return job;
  }

  /**
   * The only way to change this job's status.
   *
   * <p>There is no {@code setStatus}: a status change goes through the state machine, which rejects
   * illegal edges before anything is mutated. A rejected transition leaves the entity untouched, so
   * a caller that wraps this in a transaction rolls back to a consistent row.
   *
   * <p>Calling this directly from a controller or worker is a mistake even though it compiles — the
   * transaction and the {@code job_events} row belong to {@code JobStatusUpdater}.
   *
   * @param next    the target status
   * @param at      the instant of the transition (injected, not {@code Instant.now()}, so tests are
   *                deterministic)
   * @param reason  failure detail, stored on the job when entering a failure state; may be null
   * @throws IllegalTransitionException if {@code status -> next} is not a legal edge
   */
  public void transitionTo(JobStatus next, Instant at, String reason) {
    Objects.requireNonNull(next, "next status must not be null");
    Objects.requireNonNull(at, "transition instant must not be null");
    if (!status.canTransitionTo(next)) {
      throw new IllegalTransitionException(id, status, next);
    }

    this.status = next;

    switch (next) {
      case QUEUED -> this.queuedAt = at;
      case PROCESSING -> this.startedAt = at;
      case SUCCEEDED, DEAD_LETTER, CANCELLED -> this.completedAt = at;
      case CREATED, FAILED_RETRYABLE -> {
        // No timestamp of their own: CREATED is the initial state, FAILED_RETRYABLE is a pause.
        // The retry timestamp is derived from the previous attempt, not stored on the job.
      }
    }

    if (next == JobStatus.FAILED_RETRYABLE || next == JobStatus.DEAD_LETTER) {
      this.errorMessage = reason;
    }

    this.updatedAt = at;
  }
}
