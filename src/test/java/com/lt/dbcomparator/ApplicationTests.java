package com.lt.dbcomparator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke-тест: контекст приложения поднимается без ошибок.
 */
class ApplicationTests extends AbstractIntegrationTest {

	@Test
	@DisplayName("Контекст Spring Boot поднимается")
	void contextLoads() {
		// Если дошли сюда — контекст поднялся, Postgres доступен, JPA работает.
	}
}
