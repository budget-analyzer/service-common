package org.budgetanalyzer.core.integration.fixture;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

/** Test entity extending SoftDeletableEntity for integration testing. */
@Entity
@Table(name = "test_soft_deletable")
public class TestSoftDeletableEntity extends SoftDeletableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  /** Default constructor. */
  public TestSoftDeletableEntity() {}

  /**
   * Constructor with name.
   *
   * @param name the name
   */
  public TestSoftDeletableEntity(String name) {
    this.name = name;
  }

  /**
   * Gets the ID.
   *
   * @return the ID
   */
  public Long getId() {
    return id;
  }

  /**
   * Sets the ID.
   *
   * @param id the ID to set
   */
  public void setId(Long id) {
    this.id = id;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }
}
