package com.backend.jobplatform.worker;

import com.backend.jobplatform.domain.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The execution loop: claim a batch, run one handler per job, settle the outcome.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The transaction boundaries live in
 * {@link JobClaimer} and {@link JobSettler}, which keeps handlers outside any transaction and
 * makes each boundary explicit. Annotating this method would hold a connection for the duration of
 * the batch, and would roll back settled jobs when a later one fails.
 *
 * <p>Each job is isolated: a failure settles that job and the loop continues. One bad job must not
 * stop the batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

  private static final int BATCH_SIZE = 10;

  private final JobClaimer jobClaimer;
  private final JobSettler jobSettler;
  private final HandlerRegistry handlerRegistry;

  @Scheduled(fixedDelay = 15000)
  public void pollJobs() {
    List<Job> jobs = jobClaimer.claim(BATCH_SIZE);

    for (Job job : jobs) {
      try {
        JobHandler handler = handlerRegistry.getHandler(job.getJobType());
        handler.handle(job);
        jobSettler.succeed(job.getId());
      } catch (Exception e) {
        log.error("Job {} ({}) failed: {}", job.getId(), job.getJobType(), e.getMessage(), e);
        failQuietly(job, e);
      }
    }
  }

  /**
   * Settling can itself fail — the job may already be terminal, or the row may be gone. Swallowing
   * that keeps one broken job from aborting the rest of the batch; the log is the record.
   */
  private void failQuietly(Job job, Exception cause) {
    try {
      jobSettler.fail(job.getId(), cause.getMessage());
    } catch (RuntimeException settleFailure) {
      log.error("Could not mark job {} as FAILED_RETRYABLE", job.getId(), settleFailure);
    }
  }
}
