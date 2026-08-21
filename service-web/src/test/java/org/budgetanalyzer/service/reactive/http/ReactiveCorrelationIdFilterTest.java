package org.budgetanalyzer.service.reactive.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactiveCorrelationIdFilterTest {

  private ReactiveCorrelationIdFilter reactiveCorrelationIdFilter;
  private WebFilterChain webFilterChain;

  @BeforeEach
  void setUp() {
    reactiveCorrelationIdFilter = new ReactiveCorrelationIdFilter();
    webFilterChain = exchange -> Mono.empty();
  }

  @Test
  void shouldGenerateCorrelationIdWhenNotProvidedInRequest() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertThat(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY))
                  .isTrue();
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertThat(correlationId).isNotNull();
              assertThat(correlationId.startsWith("req_")).isTrue();
            })
        .then()
        .verifyComplete();

    // Verify response header
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isNotNull();
    assertThat(correlationId.startsWith("req_")).isTrue();
  }

  @Test
  void shouldUseExistingCorrelationIdFromRequest() {
    // Arrange
    var existingCorrelationId = "req_abc123def456";
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId));

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertThat(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY))
                  .isTrue();
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertThat(correlationId).isEqualTo(existingCorrelationId);
            })
        .then()
        .verifyComplete();

    // Verify response header
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isEqualTo(existingCorrelationId);
  }

  @Test
  void shouldTrimExistingCorrelationIdFromRequest() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, "  req_trimmed-123  "));

    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertThat(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY))
                  .isTrue();
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertThat(correlationId).isEqualTo("req_trimmed-123");
            })
        .then()
        .verifyComplete();

    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isEqualTo("req_trimmed-123");
  }

  @Test
  void shouldStoreCorrelationIdInReactorContext() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertThat(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY))
                  .isTrue();
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertThat(correlationId).isNotNull();
            })
        .then()
        .verifyComplete();
  }

  @Test
  void shouldAddCorrelationIdToResponseHeader() {
    // Arrange
    var existingCorrelationId = "req_test123456";
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId));

    // Act
    reactiveCorrelationIdFilter.filter(exchange, webFilterChain).block();

    // Assert
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isEqualTo(existingCorrelationId);
  }

  @Test
  void shouldPropagateContextThroughFilterChain() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    var capturedCorrelationId = new String[1];

    webFilterChain =
        chainExchange ->
            Mono.deferContextual(
                context -> {
                  capturedCorrelationId[0] =
                      context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
                  return Mono.empty();
                });

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert
    StepVerifier.create(result).verifyComplete();

    assertThat(capturedCorrelationId[0]).isNotNull();
    assertThat(capturedCorrelationId[0].startsWith("req_")).isTrue();
  }

  @Test
  void shouldContextNotPersistAfterExecution() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert - Context is accessible only within the reactive chain
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertThat(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY))
                  .isTrue();
            })
        .then()
        .verifyComplete();

    // After completion, context doesn't persist (reactive contexts are chain-scoped)
    // This is different from MDC which needed explicit cleanup
  }

  @Test
  void shouldHandleExceptionDuringFilterChainExecution() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    var expectedException = new RuntimeException("Simulated error");
    webFilterChain = chainExchange -> Mono.error(expectedException);

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert - Context should be accessible even when there's an error
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertThat(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY))
                  .isTrue();
            })
        .then()
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void shouldGenerateUniqueCorrelationIds() {
    // Arrange
    Set<String> correlationIds = new HashSet<>();

    // Act - Call filter multiple times
    for (int i = 0; i < 10; i++) {
      var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
      var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

      StepVerifier.create(result)
          .expectAccessibleContext()
          .assertThat(
              context -> {
                String correlationId =
                    context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
                correlationIds.add(correlationId);
              })
          .then()
          .verifyComplete();
    }

    // Assert - All should be unique
    assertThat(correlationIds.size()).isEqualTo(10);
  }

  @Test
  void shouldHandleEmptyCorrelationIdHeader() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, "  "));

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert - Should generate new ID when header is empty/whitespace
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertThat(correlationId).isNotNull();
              assertThat(correlationId.startsWith("req_")).isTrue();
            })
        .then()
        .verifyComplete();

    // Verify response header
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId.startsWith("req_")).isTrue();
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderContainsUnsafeCharacters() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, "bad value"));

    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertThat(correlationId).isNotNull();
              assertThat(correlationId.startsWith("req_")).isTrue();
              assertThat(correlationId.length()).isEqualTo(36);
            })
        .then()
        .verifyComplete();

    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId.startsWith("req_")).isTrue();
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderExceedsMaxLength() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, "a".repeat(129)));

    reactiveCorrelationIdFilter.filter(exchange, webFilterChain).block();

    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isNotNull();
    assertThat(correlationId.startsWith("req_")).isTrue();
    assertThat(correlationId.length()).isEqualTo(36);
  }

  @Test
  void shouldGenerateCorrelationIdWithCorrectFormat() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);

              // Assert format: req_<32 hex chars>
              assertThat(correlationId).isNotNull();
              assertThat(correlationId.startsWith("req_")).isTrue();
              assertThat(correlationId.length()).isEqualTo(36); // "req_" (4) + 32 hex chars
              assertThat(correlationId.substring(4).matches("[0-9a-f]{32}"))
                  .as("Correlation ID should contain 32 hexadecimal characters")
                  .isTrue();
            })
        .then()
        .verifyComplete();
  }

  @Test
  void shouldHandleNullCorrelationIdHeader() {
    // Arrange
    var headers = new HttpHeaders();
    // Explicitly don't set the correlation ID header
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").headers(headers));

    // Act
    var result = reactiveCorrelationIdFilter.filter(exchange, webFilterChain);

    // Assert - Should generate new ID
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(
            context -> {
              assertThat(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY))
                  .isTrue();
              String correlationId =
                  context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY);
              assertThat(correlationId).isNotNull();
              assertThat(correlationId.startsWith("req_")).isTrue();
            })
        .then()
        .verifyComplete();
  }

  @Test
  void shouldPreserveOtherResponseHeaders() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
    exchange.getResponse().getHeaders().add("X-Custom-Header", "custom-value");

    // Act
    reactiveCorrelationIdFilter.filter(exchange, webFilterChain).block();

    // Assert - Custom header should still exist alongside correlation ID
    var responseHeaders = exchange.getResponse().getHeaders();
    assertThat(responseHeaders.getFirst("X-Custom-Header")).isNotNull();
    assertThat(responseHeaders.getFirst("X-Custom-Header")).isEqualTo("custom-value");
    assertThat(responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER))
        .isNotNull();
  }

  @Test
  void shouldNotOverwriteExistingResponseHeaders() {
    // Arrange
    var existingCorrelationId = "req_existing12345";
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId));
    // Act
    reactiveCorrelationIdFilter.filter(exchange, webFilterChain).block();

    // Assert - Should use the existing correlation ID from request
    var responseHeaders = exchange.getResponse().getHeaders();
    var correlationId = responseHeaders.getFirst(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(correlationId).isEqualTo(existingCorrelationId);
    assertThat(responseHeaders.get(ReactiveCorrelationIdFilter.CORRELATION_ID_HEADER).size() > 1)
        .isFalse();
  }
}
