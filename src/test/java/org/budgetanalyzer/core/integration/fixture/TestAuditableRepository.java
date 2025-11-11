package org.budgetanalyzer.core.integration.fixture;

import org.springframework.data.jpa.repository.JpaRepository;

/** Test repository for TestAuditableEntity. */
public interface TestAuditableRepository extends JpaRepository<TestAuditableEntity, Long> {}
