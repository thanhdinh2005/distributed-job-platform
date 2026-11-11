package com.backend.jobplatform.domain.entity;

import com.backend.jobplatform.domain.entity.enums.JobStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only audit trail of job status transitions (V2).
 *
 * <p>Rows are inserted by {@code JobStatusUpdater} in the same transaction as the {@code jobs}
 * update, so the history can never describe a change that was rolled back.
 */
@Getter
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
   *
   * <p>{@code occurredAt} is passed in rather than read from a framework callback, so the event
   * timestamp is exactly the instant the transition happened — the same one written to the job row
   * — and unit tests can assert it.
   */
  public static JobEvent of(Job job, JobStatus from, JobStatus to, String message, Instant occurredAt) {
    JobEvent event = new JobEvent();
    event.job = job;
    event.fromStatus = from;
    event.toStatus = to;
    event.message = message;
    event.createdAt = occurredAt;
    return event;
  }

  /**
   * Safety net only. {@code @PrePersist} runs when Hibernate persists the entity — never for a
   * plain {@code new JobEvent()} in a unit test — so it fills the timestamp only if the factory did
   * not already provide one.
   */
  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
