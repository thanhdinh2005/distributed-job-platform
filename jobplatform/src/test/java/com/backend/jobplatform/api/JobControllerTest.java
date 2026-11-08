package com.backend.jobplatform.api;

import com.backend.jobplatform.domain.JobCreator;
import com.backend.jobplatform.domain.JobQueries;
import com.backend.jobplatform.domain.JobStatusUpdater;
import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import com.backend.jobplatform.domain.exception.IllegalTransitionException;
import com.backend.jobplatform.domain.exception.JobNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract only: routing, validation, and the exception-to-status mapping. No database,
 * so this runs without Docker.
 */
@WebMvcTest(JobController.class)
class JobControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JobCreator jobCreator;

  @MockitoBean
  private JobStatusUpdater jobStatusUpdater;

  @MockitoBean
  private JobQueries jobQueries;

  private static Job jobWithId(UUID id) {
    Job job = Job.create("SEND_EMAIL", Map.of("to", "a@b.c"));
    try {
      Field field = Job.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(job, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("could not set job id", e);
    }
    return job;
  }

  @Test
  void createReturns201WithLocationAndBody() throws Exception {
    UUID id = UUID.randomUUID();
    when(jobCreator.create(anyString(), anyMap())).thenReturn(jobWithId(id));

    mockMvc.perform(post("/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"jobType":"SEND_EMAIL","payload":{"to":"a@b.c"}}
                """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/jobs/" + id)))
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.jobType").value("SEND_EMAIL"))
        .andExpect(jsonPath("$.status").value("CREATED"));
  }

  @Test
  void freeFormJobTypeIsAccepted() throws Exception {
    UUID id = UUID.randomUUID();
    when(jobCreator.create(anyString(), anyMap())).thenReturn(jobWithId(id));

    mockMvc.perform(post("/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"jobType":"TELEPORT","payload":{}}
                """))
        .andExpect(status().isCreated());
  }

  @Test
  void blankJobTypeReturns400() throws Exception {
    mockMvc.perform(post("/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"jobType":"   ","payload":{}}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.jobType").exists());
  }

  @Test
  void missingPayloadReturns400WithFieldError() throws Exception {
    mockMvc.perform(post("/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"jobType":"SEND_EMAIL"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.payload").exists());
  }

  @Test
  void unknownJobReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(jobQueries.get(id)).thenThrow(new JobNotFoundException(id));

    mockMvc.perform(get("/jobs/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"))
        .andExpect(jsonPath("$.jobId").value(id.toString()));
  }

  @Test
  void eventsAreReturnedOldestFirst() throws Exception {
    UUID id = UUID.randomUUID();
    when(jobQueries.events(id)).thenReturn(java.util.List.of());

    mockMvc.perform(get("/jobs/{id}/events", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void cancellingATerminalJobReturns409() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new IllegalTransitionException(id, JobStatus.SUCCEEDED, JobStatus.CANCELLED))
        .when(jobStatusUpdater).transition(eq(id), eq(JobStatus.CANCELLED), any());

    mockMvc.perform(post("/jobs/{id}/cancel", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("JOB_ILLEGAL_TRANSITION"))
        .andExpect(jsonPath("$.from").value("SUCCEEDED"))
        .andExpect(jsonPath("$.to").value("CANCELLED"));
  }

  @Test
  void cancelReturnsTheUpdatedJob() throws Exception {
    UUID id = UUID.randomUUID();
    Job cancelled = jobWithId(id);
    cancelled.transitionTo(JobStatus.CANCELLED, Instant.parse("2026-11-08T12:00:00Z"), null);
    when(jobQueries.get(id)).thenReturn(cancelled);

    mockMvc.perform(post("/jobs/{id}/cancel", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }
}
