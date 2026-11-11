package com.backend.jobplatform.domain;

import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.JobEvent;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.exception.IllegalTransitionException;
import com.backend.jobplatform.domain.exception.JobNotFoundException;
import com.backend.jobplatform.domain.repository.JobEventRepository;
import com.backend.jobplatform.domain.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-level rules without a database: what gets written, and — more importantly — what does
 * not. The database-level proof (both rows in one transaction) belongs in an integration test.
 */
@ExtendWith(MockitoExtension.class)
class JobStatusUpdaterTest {

  private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

  @Mock
  private JobRepository jobRepository;

  @Mock
  private JobEventRepository jobEventRepository;

  private JobStatusUpdater updater;

  @BeforeEach
  void setUp() {
    updater = new JobStatusUpdater(jobRepository, jobEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private Job existingJob() {
    return Job.create("SEND_EMAIL", Map.of("to", "a@b.c"), NOW);
  }

  @Test
  void transitionUpdatesStatusAndWritesTheEdge() {
    Job job = existingJob();
    UUID id = UUID.randomUUID();
    when(jobRepository.findById(id)).thenReturn(Optional.of(job));

    updater.transition(id, JobStatus.QUEUED, null);

    assertEquals(JobStatus.QUEUED, job.getStatus());
    assertEquals(NOW, job.getQueuedAt());

    ArgumentCaptor<JobEvent> captor = ArgumentCaptor.forClass(JobEvent.class);
    verify(jobEventRepository).save(captor.capture());
    JobEvent event = captor.getValue();
    assertEquals(JobStatus.CREATED, event.getFromStatus());
    assertEquals(JobStatus.QUEUED, event.getToStatus());
    assertEquals(NOW, event.getCreatedAt());
  }

  @Test
  void illegalTransitionPropagatesAndWritesNothing() {
    Job job = existingJob();
    UUID id = UUID.randomUUID();
    when(jobRepository.findById(id)).thenReturn(Optional.of(job));

    assertThrows(IllegalTransitionException.class,
        () -> updater.transition(id, JobStatus.SUCCEEDED, null));

    assertEquals(JobStatus.CREATED, job.getStatus());
    verify(jobEventRepository, never()).save(any());
  }

  @Test
  void unknownJobThrowsAndWritesNothing() {
    UUID id = UUID.randomUUID();
    when(jobRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(JobNotFoundException.class,
        () -> updater.transition(id, JobStatus.QUEUED, null));

    verify(jobEventRepository, never()).save(any());
  }
}
