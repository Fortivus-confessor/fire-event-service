package br.arthconf.fortivus.fire_event_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FireEventServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FireEventServiceApplication.class, args);
	}

}
