package com.backend.jobplatform.worker;

import com.backend.jobplatform.domain.JobStatusUpdater;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes the outcome of an execution: one job, one short transaction, one audit row.
 *
 * <p>Separate from {@link JobWorker} for the same reason as {@link JobClaimer} — the proxy — and
 * separate from the handler because a handler must never run inside a database transaction: it can
 * take seconds, and holding a connection for that long is how a pool dies.
 */
@Component
@RequiredArgsConstructor
public class JobSettler {

  private final JobStatusUpdater jobStatusUpdater;

  @Transactional
  public void succeed(UUID jobId) {
    jobStatusUpdater.transition(jobId, JobStatus.SUCCEEDED, null);
  }

  /**
   * Failure is not yet a policy: the job goes to {@code FAILED_RETRYABLE} and stays there until the
   * retry scheduler exists. Dead-lettering on exhausted attempts belongs to that step.
   */
  @Transactional
  public void fail(UUID jobId, String reason) {
    jobStatusUpdater.transition(jobId, JobStatus.FAILED_RETRYABLE, reason);
  }
}
