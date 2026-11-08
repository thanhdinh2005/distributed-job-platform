package com.backend.jobplatform.api;

import com.backend.jobplatform.api.dto.CreateJobRequest;
import com.backend.jobplatform.api.dto.JobEventResponse;
import com.backend.jobplatform.api.dto.JobResponse;
import com.backend.jobplatform.domain.JobCreator;
import com.backend.jobplatform.domain.JobQueries;
import com.backend.jobplatform.domain.JobStatusUpdater;
import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.enums.JobStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * HTTP boundary only: validate, call one service, map to a DTO. No branching on state, no
 * transactions, no exception handling — those live in the services and in
 * {@code GlobalExceptionHandler}.
 *
 * <p>Context path is {@code /api}, so the real URLs are {@code /api/jobs/...}.
 */
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

  private final JobCreator jobCreator;
  private final JobStatusUpdater jobStatusUpdater;
  private final JobQueries jobQueries;

  @PostMapping
  public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
    Job job = jobCreator.create(request.jobType(), request.payload());

    // Built from the current request so the context path (/api) is included.
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(job.getId())
        .toUri();

    return ResponseEntity.created(location).body(JobResponse.from(job));
  }

  @GetMapping("/{id}")
  public JobResponse get(@PathVariable UUID id) {
    return JobResponse.from(jobQueries.get(id));
  }

  @GetMapping("/{id}/events")
  public List<JobEventResponse> events(@PathVariable UUID id) {
    return jobQueries.events(id).stream()
        .map(JobEventResponse::from)
        .toList();
  }

  @PostMapping("/{id}/cancel")
  public JobResponse cancel(@PathVariable UUID id) {
    // Cancelling is a transition, so it goes through the one funnel. An illegal cancel (already
    // PROCESSING or terminal) throws and is mapped to 409 by the advice.
    jobStatusUpdater.transition(id, JobStatus.CANCELLED, "cancelled by user");
    return JobResponse.from(jobQueries.get(id));
  }
}
