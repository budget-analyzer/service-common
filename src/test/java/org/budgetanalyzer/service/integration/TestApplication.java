package org.budgetanalyzer.service.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot application for integration testing.
 *
 * <p>This application simulates how a consuming microservice would configure service-common by
 * including the service-common package in component scanning.
 */
@SpringBootApplication(
    scanBasePackages = {
      "org.budgetanalyzer.service",
      "org.budgetanalyzer.core",
      "org.budgetanalyzer.core.integration.fixture",
      "org.budgetanalyzer.service.integration.fixture"
    })
@EntityScan(
    basePackages = {
      "org.budgetanalyzer.core",
      "org.budgetanalyzer.core.integration.fixture",
      "org.budgetanalyzer.service.integration.fixture"
    })
@EnableJpaRepositories(
    basePackages = {
      "org.budgetanalyzer.core",
      "org.budgetanalyzer.core.integration.fixture",
      "org.budgetanalyzer.service.integration.fixture"
    })
public class TestApplication {
  // No main method needed - used only for @SpringBootTest
}
