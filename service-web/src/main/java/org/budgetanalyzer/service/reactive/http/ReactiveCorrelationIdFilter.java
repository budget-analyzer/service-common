package org.budgetanalyzer.service.reactive.http;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.budgetanalyzer.core.logging.CorrelationIdGenerator;

/**
 * Reactive filter for managing correlation IDs in distributed tracing.
 *
 * <p>Unlike servlet-based filters that use MDC (thread-local), reactive filters store the
 * correlation ID in Reactor Context which propagates through the reactive chain.
 *
 * <p>Order: 100 (runs before all other filters)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ReactiveCorrelationIdFilter implements WebFilter {

  /** Header name for correlation ID. */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

  /** Key for storing correlation ID in Reactor Context. */
  public static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";

  /**
   * Processes the request by extracting or generating a correlation ID.
   *
   * <p>The correlation ID is:
   *
   * <ul>
   *   <li>Extracted from request header or generated if not present
   *   <li>Added to response headers for client-side tracing
   *   <li>Stored in Reactor Context for use in downstream reactive operations
   * </ul>
   *
   * @param exchange the server web exchange
   * @param chain the filter chain to continue processing
   * @return Mono that completes when the request is processed
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    var correlationId = extractOrGenerateCorrelationId(exchange.getRequest());

    // Add to response headers
    exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

    // Store in Reactor Context for logging
    return chain
        .filter(exchange)
        .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId));
  }

  /**
   * Extracts correlation ID from request header, or generates a new one if not present.
   *
   * @param request the HTTP request
   * @return correlation ID
   */
  private String extractOrGenerateCorrelationId(ServerHttpRequest request) {
    var correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

    if (correlationId == null || correlationId.trim().isEmpty()) {
      correlationId = CorrelationIdGenerator.generate();
    }

    return correlationId;
  }
}
