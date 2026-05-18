package com.aidea.aidea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class AideaApplication {

	public static void main(String[] args) {
		SpringApplication.run(AideaApplication.class, args);
	}

}
