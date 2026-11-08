package com.backend.jobplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class JobplatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobplatformApplication.class, args);
	}

}
