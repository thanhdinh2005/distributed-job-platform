package com.backend.jobplatform.worker;

import com.backend.jobplatform.domain.entity.Job;

public interface JobHandler {
  String jobType();
  void handle(Job job) throws Exception;
}
