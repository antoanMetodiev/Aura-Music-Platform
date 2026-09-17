plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.aura"
version = "0.0.1-SNAPSHOT"
description = "Aura Worker Service — every background provider job, with its own credentials and its own rate limits"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.1.3"

dependencies {
	// Web (its own status API) + the clients it drives the other services with
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-restclient")

	// No database on purpose: every row this service produces belongs to another service's schema,
	// and that service writes it (Project-Info.md §6). This one owns credentials and pacing, nothing else.

	// Resilience for external providers and for the services it calls
	implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")

	// Observability
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")

	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
