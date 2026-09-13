package com.aura.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Real datasource connection details come from Testcontainers via {@code @ServiceConnection}
 * ({@link TestcontainersConfiguration}) — everything else in {@code application.yml} (Flyway's
 * `catalog` schema, resilience4j, actuator, ...) stays in effect, so this genuinely exercises the
 * production config. TIDAL is disabled here so the context loads without real credentials or
 * network access; {@code NoopMusicMetadataProvider}/{@code NoopMusicSearchProvider} take over.
 *
 * NOTE: don't add a {@code src/test/resources/application.yml} — Boot loads exactly one
 * {@code classpath:/application.yml}, so a test-resources copy would silently replace this one
 * instead of layering on top of it. Use {@code properties} here (or {@code @TestPropertySource})
 * for test-only overrides.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"music.providers.tidal.enabled=false",
		"music.providers.tidal.client-id=test-client-id",
		"music.providers.tidal.client-secret=test-client-secret"
})
class CatalogSvcApplicationTests {

	@Test
	void contextLoads() {
	}

}
