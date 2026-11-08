package com.backend.jobplatform.domain;

import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.JobEvent;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.exception.JobNotFoundException;
import com.backend.jobplatform.domain.repository.JobEventRepository;
import com.backend.jobplatform.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * The single entry point for changing a job's status.
 *
 * <p>Division of labour, deliberately:
 * <ul>
 *   <li>{@link Job#transitionTo} owns the <em>rules</em> — which edges are legal, which timestamps
 *       move. It is pure, needs no Spring, and is tested directly.</li>
 *   <li>This class owns the <em>transaction</em> and the <em>side effects</em> — loading the row,
 *       appending the audit event, and (later) publishing after commit. An entity cannot do this:
 *       Hibernate instantiates entities, so they can hold no collaborators.</li>
 * </ul>
 *
 * <p>Because this method is {@code @Transactional} and the entity is managed, no explicit
 * {@code save(job)} is needed — dirty checking flushes the update on commit. The event insert and
 * the job update therefore land in the same transaction, or neither does.
 */
@Service
@RequiredArgsConstructor
public class JobStatusUpdater {

  private final JobRepository jobRepository;
  private final JobEventRepository jobEventRepository;
  private final Clock clock;

  /**
   * Moves a job to {@code next} and records the edge.
   *
   * @param jobId  the job to move
   * @param next   target status; must be a legal edge from the current one
   * @param reason failure detail, kept on the job and the event; may be null
   * @throws JobNotFoundException      if no such job exists
   * @throws com.backend.jobplatform.domain.exception.IllegalTransitionException
   *                                   if the edge is not allowed; nothing is written in that case
   */
  @Transactional
  public void transition(UUID jobId, JobStatus next, String reason) {
    Objects.requireNonNull(jobId, "jobId must not be null");

    Job job = jobRepository.findById(jobId)
        .orElseThrow(() -> new JobNotFoundException(jobId));

    JobStatus from = job.getStatus();
    job.transitionTo(next, clock.instant(), reason);
    jobEventRepository.save(JobEvent.of(job, from, next, reason));
  }
}
