package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ClientException}. */
@DisplayName("ClientException Tests")
class ClientExceptionTest {

  @Test
  @DisplayName("Should create exception with cause")
  void shouldCreateExceptionWithCause() {
    var message = "Failed to connect to external API";
    var cause = new IOException("Connection timeout");

    var exception = new ClientException(message, cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new ClientException("Error");

    assertThat(exception).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new ClientException("Error");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Client error";

    var exception = new ClientException(message, null);

    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should preserve cause when wrapping HTTP client exceptions")
  void shouldPreserveCauseWhenWrappingHttpExceptions() {
    var httpException = new RuntimeException("HTTP 503 Service Unavailable");
    var exception = new ClientException("External service unavailable", httpException);

    assertThat(exception.getCause()).isSameAs(httpException);
  }
}
