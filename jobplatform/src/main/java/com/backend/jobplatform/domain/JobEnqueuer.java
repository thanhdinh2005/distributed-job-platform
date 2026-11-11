package com.backend.jobplatform.domain;

import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Moves {@code CREATED} jobs to {@code QUEUED} — the in-process stand-in for "publish to the
 * queue". When the outbox lands (stage 3), the trigger changes from "scan for CREATED rows" to
 * "read unpublished outbox rows"; the transition itself stays the same.
 *
 * <p>{@code @Transactional} is on the public entry point, which is the only place it takes effect:
 * the select and the bulk update share one transaction, so the rows stay locked while they are
 * being moved and a crash cannot half-move the batch.
 *
 * <p>No {@code saveAll} is needed — the loaded entities are managed, so dirty checking flushes
 * them at commit.
 */
@Component
@RequiredArgsConstructor
public class JobEnqueuer {

  private static final int BATCH_SIZE = 100;

  private final JobRepository jobRepository;
  private final JobStatusUpdater jobStatusUpdater;

  @Scheduled(fixedDelay = 20000)
  @Transactional
  public void enqueueJobs() {
    List<Job> jobs = jobRepository.findJobsToQueue(BATCH_SIZE);
    jobs.forEach(job ->
        jobStatusUpdater.transition(job.getId(), JobStatus.QUEUED, "queued for processing"));
  }
}
