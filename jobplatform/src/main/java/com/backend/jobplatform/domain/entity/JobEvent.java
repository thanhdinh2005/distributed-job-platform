package com.backend.jobplatform.domain.entity;

import com.backend.jobplatform.domain.entity.enums.JobStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only audit trail of job status transitions (V2).
 *
 * <p>Rows are inserted by {@code JobStatusUpdater} in the same transaction as the {@code jobs}
 * update, so the history can never describe a change that was rolled back.
 */
@Entity
@Table(name = "job_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_id", nullable = false)
  private Job job;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 30)
  private JobStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 30)
  private JobStatus toStatus;

  @Column(columnDefinition = "TEXT")
  private String message;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /**
   * Factory instead of a public constructor: an event cannot exist without the job and the edge it
   * records.
   */
  public static JobEvent of(Job job, JobStatus from, JobStatus to, String message) {
    JobEvent event = new JobEvent();
    event.job = job;
    event.fromStatus = from;
    event.toStatus = to;
    event.message = message;
    return event;
  }

  public Long getId() {
    return id;
  }

  public Job getJob() {
    return job;
  }

  public JobStatus getFromStatus() {
    return fromStatus;
  }

  public JobStatus getToStatus() {
    return toStatus;
  }

  public String getMessage() {
    return message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }
}
