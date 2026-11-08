package com.backend.jobplatform.domain;

import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.domain.entity.JobEvent;
import com.backend.jobplatform.domain.exception.JobNotFoundException;
import com.backend.jobplatform.domain.repository.JobEventRepository;
import com.backend.jobplatform.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side. Kept separate from the write services so a query can never accidentally mutate, and
 * so the transaction is explicitly read-only.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobQueries {

  private final JobRepository jobRepository;
  private final JobEventRepository jobEventRepository;

  public Job get(UUID jobId) {
    return jobRepository.findById(jobId)
        .orElseThrow(() -> new JobNotFoundException(jobId));
  }

  public List<JobEvent> events(UUID jobId) {
    if (!jobRepository.existsById(jobId)) {
      throw new JobNotFoundException(jobId);
    }
    return jobEventRepository.findByJob_IdOrderByCreatedAtAsc(jobId);
  }
}
