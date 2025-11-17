package org.budgetanalyzer.core.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auto-configuration for service-core.
 *
 * <p>Always enables:
 *
 * <ul>
 *   <li>Component scanning for core utilities (logging, CSV parsing)
 * </ul>
 *
 * <p>Conditionally enables (only if JPA is on classpath):
 *
 * <ul>
 *   <li>Entity scanning for JPA base entities (AuditableEntity, SoftDeletableEntity)
 *   <li>JPA auditing for automatic createdAt/updatedAt timestamps
 * </ul>
 *
 * <p>This auto-configuration is automatically discovered by Spring Boot via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 */
@AutoConfiguration
@ComponentScan(basePackages = {"org.budgetanalyzer.core.logging", "org.budgetanalyzer.core.csv"})
public class ServiceCoreAutoConfiguration {

  /**
   * JPA-specific configuration.
   *
   * <p>Only loaded if DataSource bean exists (i.e., service added spring-boot-starter-data-jpa
   * dependency and DataSource auto-configuration is enabled).
   *
   * <p>Enables:
   *
   * <ul>
   *   *
   *   <li>Entity scanning for org.budgetanalyzer.core.domain package
   *   <li>JPA auditing for AuditableEntity timestamps
   * </ul>
   */
  @AutoConfiguration
  @ConditionalOnClass(DataSource.class)
  @ConditionalOnBean(DataSource.class)
  @EntityScan(basePackages = "org.budgetanalyzer.core.domain")
  @EnableJpaAuditing
  public static class JpaConfiguration {}
}
