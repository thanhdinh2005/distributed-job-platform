package com.backend.jobplatform.worker;

import com.backend.jobplatform.domain.JobStatusUpdater;
import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Claims a batch of queued jobs: select and transition in <em>one</em> transaction.
 *
 * <p>That matters for two reasons:
 * <ul>
 *   <li>{@code FOR UPDATE SKIP LOCKED} is a transaction-scoped lock. Outside a transaction that
 *       spans the select and the update, it protects nothing — the lock is released the moment the
 *       repository method returns.</li>
 *   <li>Claiming and marking {@code PROCESSING} must be atomic, or two workers can claim the same
 *       job.</li>
 * </ul>
 *
 * <p>This is a separate bean on purpose: {@code @Transactional} is applied by a proxy, so a
 * self-invocation from {@link JobWorker} would silently run without a transaction.
 *
 * <p>The returned entities are detached once the transaction commits, and their {@code status}
 * still reads {@code QUEUED}. Handlers should treat them as read-only input.
 */
@Component
@RequiredArgsConstructor
public class JobClaimer {

  private final JobRepository jobRepository;
  private final JobStatusUpdater jobStatusUpdater;

  @Transactional
  public List<Job> claim(int batchSize) {
    List<Job> jobs = jobRepository.findJobsToHandle(batchSize);
    jobs.forEach(job ->
        jobStatusUpdater.transition(job.getId(), JobStatus.PROCESSING, null));
    return jobs;
  }
}
