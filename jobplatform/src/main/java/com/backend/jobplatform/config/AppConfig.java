package com.backend.jobplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
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
