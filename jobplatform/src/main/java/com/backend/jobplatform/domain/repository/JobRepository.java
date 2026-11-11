package com.backend.jobplatform.domain.repository;

import com.backend.jobplatform.domain.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
  @Query(value = """
    SELECT *
    FROM jobs
    WHERE status = 'CREATED'
    ORDER BY priority DESC, created_at ASC
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
  List<Job> findJobsToQueue(@Param("batchSize") int batchSize);

  @Query(value = """
  SELECT *
    FROM jobs
    WHERE status = 'QUEUED'
    ORDER BY priority DESC, queued_at ASC
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
  """, nativeQuery = true)
  List<Job> findJobsToHandle(@Param("batchSize") int batchSize);
}
