package com.backend.jobplatform.domain.repository;

import com.backend.jobplatform.domain.entity.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobEventRepository extends JpaRepository<JobEvent, Long> {

  /** Derived query: {@code job} is the association, {@code id} its identifier column. */
  List<JobEvent> findByJob_IdOrderByCreatedAtAsc(UUID jobId);
}
