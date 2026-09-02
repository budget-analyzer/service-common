package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ServiceException}. */
@DisplayName("ServiceException Tests")
class ServiceExceptionTest {

  @Test
  @DisplayName("Should create exception with cause")
  void shouldCreateExceptionWithCause() {
    var message = "Service error occurred";
    var cause = new IOException("Database connection failed");

    var exception = new ServiceException(message, cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should handle null message with cause")
  void shouldHandleNullMessageWithCause() {
    var cause = new IOException("Network error");

    var exception = new ServiceException(null, cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Service error occurred";

    var exception = new ServiceException(message, null);

    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ServiceException("Error");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("Should preserve stack trace")
  void shouldPreserveStackTrace() {
    var exception = new ServiceException("Error");

    assertThat(exception.getStackTrace()).isNotNull();
    assertThat(exception.getStackTrace().length > 0).isTrue();
  }

  @Test
  @DisplayName("Should preserve nested cause chain")
  void shouldPreserveNestedCauseChain() {
    var rootCause = new IllegalArgumentException("Invalid argument");
    var intermediateCause = new IOException("IO error", rootCause);
    var exception = new ServiceException("Service error", intermediateCause);

    assertThat(exception.getCause()).isSameAs(intermediateCause);
    assertThat(exception.getCause().getCause()).isSameAs(rootCause);
  }
}
