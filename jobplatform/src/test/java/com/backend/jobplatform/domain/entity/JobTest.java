package com.backend.jobplatform.domain.entity;

import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.exception.IllegalTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Entity-level tests: no Spring, no database. This is the payoff of keeping the state machine in
 * {@link Job} instead of a service — every rule is checkable in milliseconds.
 *
 * <p>Lives in this package so it can use the protected no-arg constructor.
 */
class JobTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2026-01-01T00:01:00Z");

  private Job newJob() {
    return new Job();
  }

  @Test
  void newJobIsCreated() {
    assertEquals(JobStatus.CREATED, newJob().getStatus());
  }

  @Test
  void queuedTransitionStampsQueuedAt() {
    Job job = newJob();

    job.transitionTo(JobStatus.QUEUED, T0, null);

    assertEquals(JobStatus.QUEUED, job.getStatus());
    assertEquals(T0, job.getQueuedAt());
    assertEquals(T0, job.getUpdatedAt());
    assertNull(job.getStartedAt());
  }

  @Test
  void happyPathStampsEachTimestampInTurn() {
    Job job = newJob();

    job.transitionTo(JobStatus.QUEUED, T0, null);
    job.transitionTo(JobStatus.PROCESSING, T0, null);
    job.transitionTo(JobStatus.SUCCEEDED, T1, null);

    assertEquals(JobStatus.SUCCEEDED, job.getStatus());
    assertEquals(T0, job.getQueuedAt());
    assertEquals(T0, job.getStartedAt());
    assertEquals(T1, job.getCompletedAt());
  }

  @Test
  void failureStoresTheReasonOnTheJob() {
    Job job = newJob();
    job.transitionTo(JobStatus.QUEUED, T0, null);
    job.transitionTo(JobStatus.PROCESSING, T0, null);

    job.transitionTo(JobStatus.FAILED_RETRYABLE, T0, "downstream timeout");

    assertEquals("downstream timeout", job.getErrorMessage());
    assertNull(job.getCompletedAt());
  }

  @Test
  void illegalTransitionThrowsAndLeavesStateUntouched() {
    Job job = newJob();

    IllegalTransitionException thrown = assertThrows(
        IllegalTransitionException.class,
        () -> job.transitionTo(JobStatus.SUCCEEDED, T0, null));

    assertEquals(JobStatus.CREATED, thrown.getFrom());
    assertEquals(JobStatus.SUCCEEDED, thrown.getTo());
    assertEquals(JobStatus.CREATED, job.getStatus());
    assertNull(job.getCompletedAt());
    assertNull(job.getUpdatedAt());
  }

  @Test
  void terminalStateRejectsFurtherTransitions() {
    Job job = newJob();
    job.transitionTo(JobStatus.QUEUED, T0, null);
    job.transitionTo(JobStatus.PROCESSING, T0, null);
    job.transitionTo(JobStatus.SUCCEEDED, T1, null);

    assertThrows(IllegalTransitionException.class,
        () -> job.transitionTo(JobStatus.QUEUED, T1, null));
    assertEquals(JobStatus.SUCCEEDED, job.getStatus());
    assertEquals(T1, job.getCompletedAt());
  }
}
