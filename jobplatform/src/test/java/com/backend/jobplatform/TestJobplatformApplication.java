package com.backend.jobplatform;

import org.springframework.boot.SpringApplication;

public class TestJobplatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobplatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
