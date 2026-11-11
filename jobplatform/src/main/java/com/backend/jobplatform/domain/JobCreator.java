package com.backend.jobplatform.domain;

import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.JobEvent;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.repository.JobEventRepository;
import com.backend.jobplatform.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Creation is not a transition: there is no legal edge <em>into</em> {@code CREATED}, so this
 * cannot go through {@link JobStatusUpdater}. What it does share is the rule that the job row and
 * its first audit row commit together.
 *
 * <p>The outbox row is deliberately absent: it belongs with the poller that will consume it
 * (stage 3). Writing rows nobody reads would be misleading.
 */
@Service
@RequiredArgsConstructor
public class JobCreator {

  private final JobRepository jobRepository;
  private final JobEventRepository jobEventRepository;
  private final Clock clock;

  @Transactional
  public Job create(String jobType, Map<String, Object> payload) {
    Instant now = clock.instant();
    Job job = Job.create(jobType, payload, now);
    jobRepository.save(job);

    // from_status is null: the job had no previous state. That is a fact, not a missing value.
    jobEventRepository.save(JobEvent.of(job, null, JobStatus.CREATED, "job created", now));

    return job;
  }
}
