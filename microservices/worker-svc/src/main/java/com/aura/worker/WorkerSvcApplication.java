package com.aura.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Every background provider job in Aura, in one process that owns its own credentials.
 *
 * <p>It has no database. Each row it causes to exist belongs to another service's schema and is
 * written by that service (Project-Info.md §6): the worker claims a unit of work over that service's
 * API, calls the provider, and posts the result back. What it does own is the thing that made the
 * split worth doing — the provider credentials and the rate-limit budget that goes with them.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkerSvcApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkerSvcApplication.class, args);
	}

}
