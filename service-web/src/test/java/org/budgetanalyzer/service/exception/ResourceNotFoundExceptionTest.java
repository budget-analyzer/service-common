package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ResourceNotFoundException}. */
@DisplayName("ResourceNotFoundException Tests")
class ResourceNotFoundExceptionTest {

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new ResourceNotFoundException("Not found");

    assertThat(exception).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ResourceNotFoundException("Not found");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }
}
