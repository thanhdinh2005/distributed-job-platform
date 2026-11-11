package com.backend.jobplatform.worker;

import com.backend.jobplatform.domain.entity.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The loop's failure semantics, without a database: one bad job must not stop the batch, and a
 * failure has to be recorded on the job rather than thrown away.
 */
@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

  @Mock
  private JobClaimer jobClaimer;

  @Mock
  private JobSettler jobSettler;

  @Mock
  private HandlerRegistry handlerRegistry;

  @Mock
  private JobHandler handler;

  private JobWorker worker;

  @BeforeEach
  void setUp() {
    worker = new JobWorker(jobClaimer, jobSettler, handlerRegistry);
  }

  private static Job job(String type) {
    Job job = Job.create(type, Map.of("to", "a@b.c"), Instant.parse("2026-11-09T12:00:00Z"));
    try {
      Field field = Job.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(job, UUID.randomUUID());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return job;
  }

  @Test
  void successfulHandlerSettlesSucceeded() throws Exception {
    Job job = job("SEND_EMAIL");
    when(jobClaimer.claim(anyInt())).thenReturn(List.of(job));
    when(handlerRegistry.getHandler("SEND_EMAIL")).thenReturn(handler);

    worker.pollJobs();

    verify(handler).handle(job);
    verify(jobSettler).succeed(job.getId());
  }

  @Test
  void throwingHandlerSettlesRetryable() throws Exception {
    Job job = job("SEND_EMAIL");
    when(jobClaimer.claim(anyInt())).thenReturn(List.of(job));
    when(handlerRegistry.getHandler("SEND_EMAIL")).thenReturn(handler);
    doThrow(new IllegalStateException("smtp down")).when(handler).handle(job);

    worker.pollJobs();

    verify(jobSettler).fail(eq(job.getId()), contains("smtp down"));
  }

  @Test
  void unknownJobTypeSettlesRetryableInsteadOfThrowing() {
    Job job = job("TELEPORT");
    when(jobClaimer.claim(anyInt())).thenReturn(List.of(job));
    when(handlerRegistry.getHandler("TELEPORT"))
        .thenThrow(new IllegalArgumentException("No handler registered for job type: TELEPORT"));

    worker.pollJobs();

    verify(jobSettler).fail(eq(job.getId()), contains("No handler registered"));
  }

  @Test
  void oneFailingJobDoesNotStopTheBatch() throws Exception {
    Job failing = job("SEND_EMAIL");
    Job healthy = job("SEND_EMAIL");
    when(jobClaimer.claim(anyInt())).thenReturn(List.of(failing, healthy));

    when(handlerRegistry.getHandler("SEND_EMAIL"))
        .thenReturn(handler, handler);
    doThrow(new IllegalStateException("boom")).doNothing().when(handler).handle(any());

    worker.pollJobs();

    verify(jobSettler).fail(eq(failing.getId()), contains("boom"));
    verify(jobSettler).succeed(healthy.getId());
  }
}
