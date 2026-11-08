package com.backend.jobplatform.domain.entity.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.backend.jobplatform.domain.entity.enums.JobStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the transition table. Every ordered pair is checked, so a new edge cannot be added to
 * {@link JobStatus#canTransitionTo} without this table being updated too — the two disagree and the
 * test names the exact edge.
 */
class JobStatusTest {

  private static final Map<JobStatus, Set<JobStatus>> LEGAL_EDGES = new EnumMap<>(JobStatus.class);

  static {
    LEGAL_EDGES.put(CREATED, EnumSet.of(QUEUED, CANCELLED));
    LEGAL_EDGES.put(QUEUED, EnumSet.of(PROCESSING, CANCELLED));
    LEGAL_EDGES.put(PROCESSING, EnumSet.of(SUCCEEDED, FAILED_RETRYABLE, DEAD_LETTER));
    LEGAL_EDGES.put(FAILED_RETRYABLE, EnumSet.of(QUEUED, DEAD_LETTER, CANCELLED));
    LEGAL_EDGES.put(SUCCEEDED, EnumSet.noneOf(JobStatus.class));
    LEGAL_EDGES.put(DEAD_LETTER, EnumSet.noneOf(JobStatus.class));
    LEGAL_EDGES.put(CANCELLED, EnumSet.noneOf(JobStatus.class));
  }

  @ParameterizedTest
  @EnumSource(JobStatus.class)
  void canTransitionToMatchesTheDeclaredTable(JobStatus from) {
    for (JobStatus to : JobStatus.values()) {
      assertEquals(
          LEGAL_EDGES.get(from).contains(to),
          from.canTransitionTo(to),
          "edge " + from + " -> " + to);
    }
  }

  @ParameterizedTest
  @EnumSource(JobStatus.class)
  void nullIsNeverALegalTarget(JobStatus from) {
    assertFalse(from.canTransitionTo(null));
  }

  @Test
  void terminalStatesAcceptNothing() {
    for (JobStatus status : JobStatus.values()) {
      if (status.isTerminal()) {
        for (JobStatus to : JobStatus.values()) {
          assertFalse(status.canTransitionTo(to), status + " -> " + to);
        }
      }
    }
  }

  @Test
  void cancellingMidProcessingIsNotAllowed() {
    assertFalse(PROCESSING.canTransitionTo(CANCELLED));
  }

  @Test
  void requeueingGoesThroughFailedRetryable() {
    assertFalse(PROCESSING.canTransitionTo(QUEUED));
    assertTrue(PROCESSING.canTransitionTo(FAILED_RETRYABLE));
    assertTrue(FAILED_RETRYABLE.canTransitionTo(QUEUED));
  }
}
