package com.backend.jobplatform.worker.handlers;

import com.backend.jobplatform.domain.entity.Job;
import com.backend.jobplatform.worker.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class SendEmailHandler implements JobHandler {
  @Override
  public String jobType() {
    return "SEND_EMAIL";
  }

  @Override
  public void handle(Job job) throws Exception {
    Map<String, Object> payload = job.getPayload();

    String sender = (String) payload.get("sender");
    String receiver = (String) payload.get("reicever");
    String content = (String) payload.get("content");

    log.info("Sending email to {}", receiver);
  }
}
