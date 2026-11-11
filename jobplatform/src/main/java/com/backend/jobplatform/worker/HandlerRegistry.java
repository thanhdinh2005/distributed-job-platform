package com.backend.jobplatform.worker;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class HandlerRegistry {
  private final Map<String, JobHandler> handlers;

  public HandlerRegistry(List<JobHandler> handlerList) {
    this.handlers = handlerList.stream()
      .collect(Collectors.toMap(
        JobHandler::jobType,
        Function.identity() // return the entry instant
      ));
  }

  public JobHandler getHandler(String jobType) {
    JobHandler handler = handlers.get(jobType);

    if (handler == null) {
      throw new IllegalArgumentException(
        "No handler registered for job type: " + jobType
      );
    }

    return handler;
  }
}
