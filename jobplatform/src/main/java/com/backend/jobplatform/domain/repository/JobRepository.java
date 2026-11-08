package com.backend.jobplatform.domain.repository;

import com.backend.jobplatform.domain.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
}
