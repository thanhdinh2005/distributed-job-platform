package com.backend.jobplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * No {@code @EnableJpaAuditing} on purpose: timestamps are set by the entities from the injected
 * {@link Clock}. Auditing would be a second clock that ignores this bean, so the object you build
 * and the row you persist could disagree — and neither {@code @CreatedDate} nor {@code @PrePersist}
 * runs outside a persistence context, which makes them invisible to unit tests.
 */
@Configuration
@EnableScheduling
public class AppConfig {

  /**
   * Injected into domain services instead of calling {@code Instant.now()} inline, so a test can
   * hand in a fixed clock. {@link Clock#systemUTC()} is fine here because only instants are stored
   * — the zone never matters.
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
