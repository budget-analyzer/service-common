# Service-Web Reactive Support Refactoring Plan

**Status**: Draft - Not Implemented
**Created**: 2025-11-19
**Goal**: Add reactive (WebFlux) support to service-web alongside existing servlet support

## Executive Summary

This plan outlines the refactoring needed to support both servlet-based (Spring MVC) and reactive (Spring WebFlux) services from a single `service-web` module. Currently, service-web only supports servlet-based applications like transaction-service. This refactoring will enable reactive applications like session-gateway to use the same production-quality HTTP logging, exception handling, and security features.

## Background

### Current State

**service-web** provides:
- HTTP logging filters (CorrelationIdFilter, HttpLoggingFilter)
- Exception handling (DefaultApiExceptionHandler)
- API error response models (ApiErrorResponse, ApiErrorType, FieldError)
- OAuth2 security configuration
- All servlet-specific (Spring MVC)

**session-gateway** (reactive):
- Currently has basic debug logging using `System.err.println()`
- No structured HTTP logging
- No standardized exception handling
- No correlation ID management
- Missing production-quality features

### Problem Statement

1. **Exception handlers cannot be shared** - DefaultApiExceptionHandler uses servlet-specific types (WebRequest, MethodArgumentNotValidException) that don't work with reactive applications (needs ServerWebExchange, WebExchangeBindException)

2. **Transitive dependency conflicts** - service-web exposes `spring-boot-starter-web` as `api()` (transitive), which means reactive services would inherit conflicting servlet dependencies (Tomcat, servlet-api, spring-webmvc)

3. **Filter implementations are incompatible** - Servlet filters extend `OncePerRequestFilter`, reactive filters implement `WebFilter` with different APIs

### Key Decisions

- **Module naming**: Keep `service-web` name (less disruptive than renaming to service-http)
- **Dependency management**: Breaking change - convert `api()` to `compileOnly()` for web stack dependencies
- **Code sharing**: Use interface with default methods for shared exception handler logic

## Architecture

### Package Structure

```
service-core/
  └── logging/
      ├── SafeLogger.java                    (existing)
      ├── @Sensitive.java                    (existing)
      ├── SensitiveDataModule.java           (existing)
      ├── CorrelationIdGenerator.java        (NEW - shared UUID generation)
      ├── HttpLogFormatter.java              (NEW - shared log formatting)
      └── SensitiveHeaderMasker.java         (NEW - shared header masking)

service-web/
  ├── api/
  │   ├── ApiErrorResponse.java              (existing - shared)
  │   ├── ApiErrorType.java                  (existing - shared)
  │   ├── FieldError.java                    (existing - shared)
  │   └── ApiExceptionHandler.java           (NEW - interface with default methods)
  │
  ├── exception/
  │   ├── ServiceException.java              (existing - shared)
  │   ├── ResourceNotFoundException.java     (existing - shared)
  │   ├── InvalidRequestException.java       (existing - shared)
  │   ├── BusinessException.java             (existing - shared)
  │   ├── ClientException.java               (existing - shared)
  │   └── ServiceUnavailableException.java   (existing - shared)
  │
  ├── config/
  │   ├── HttpLoggingProperties.java         (existing - shared)
  │   └── ServiceWebAutoConfiguration.java   (MODIFIED - conditional registration)
  │
  ├── servlet/                                (NEW package - servlet-specific)
  │   ├── http/
  │   │   ├── CorrelationIdFilter.java       (moved from service.http)
  │   │   ├── HttpLoggingFilter.java         (moved from service.http)
  │   │   ├── ContentLoggingUtil.java        (moved from service.http)
  │   │   └── HttpLoggingConfig.java         (moved from service.http)
  │   ├── api/
  │   │   └── DefaultApiExceptionHandler.java (moved from service.api, implements ApiExceptionHandler)
  │   └── security/
  │       └── OAuth2ResourceServerSecurityConfig.java (moved if servlet-specific)
  │
  └── reactive/                               (NEW package - reactive-specific)
      ├── http/
      │   ├── ReactiveCorrelationIdFilter.java
      │   ├── ReactiveHttpLoggingFilter.java
      │   ├── CachedBodyServerHttpRequestDecorator.java
      │   ├── CachedBodyServerHttpResponseDecorator.java
      │   ├── ReactiveContentLoggingUtil.java
      │   └── ReactiveHttpLoggingConfig.java
      ├── api/
      │   └── ReactiveApiExceptionHandler.java (implements ApiExceptionHandler)
      └── security/
          └── ReactiveOAuth2ResourceServerSecurityConfig.java (if needed)
```

### Dependency Management

**Current (service-web/build.gradle.kts):**
```kotlin
// PROBLEM: These are transitive to consuming services
api(libs.spring.boot.starter.web)
api(libs.spring.boot.starter.data.jpa)
api(libs.spring.boot.starter.oauth2.resource.server)
```

**New (BREAKING CHANGE):**
```kotlin
// Shared dependencies - transitive
api(project(":service-core"))
api(libs.commons.lang3)

// Stack-specific - compile-only (NOT transitive)
compileOnly(libs.spring.boot.starter.web)      // For servlet components
compileOnly(libs.spring.boot.starter.webflux)  // For reactive components
compileOnly(libs.spring.boot.starter.data.jpa)
compileOnly(libs.spring.boot.starter.oauth2.resource.server)

// Test dependencies
testImplementation(libs.spring.boot.starter.web)
testImplementation(libs.spring.boot.starter.webflux)
testImplementation(libs.spring.boot.starter.test)
```

**Impact on Consuming Services:**

Servlet services (transaction-service, currency-service, etc.) must add:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
```

Reactive services (session-gateway) add:
```kotlin
implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
implementation("org.springframework.boot:spring-boot-starter-webflux")
```

### Autoconfiguration Strategy

**ServiceWebAutoConfiguration.java:**
```java
package org.budgetanalyzer.service.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
public class ServiceWebAutoConfiguration {

  /**
   * Configuration for servlet-based (Spring MVC) web applications.
   * Activates when servlet dependencies are on classpath.
   */
  @Configuration
  @ConditionalOnWebApplication(type = Type.SERVLET)
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  @ComponentScan(basePackages = {
      "org.budgetanalyzer.service.servlet"
  })
  static class ServletWebConfiguration {}

  /**
   * Configuration for reactive (Spring WebFlux) web applications.
   * Activates when reactive dependencies are on classpath.
   */
  @Configuration
  @ConditionalOnWebApplication(type = Type.REACTIVE)
  @ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
  @ComponentScan(basePackages = {
      "org.budgetanalyzer.service.reactive"
  })
  static class ReactiveWebConfiguration {}
}
```

## Implementation Phases

### Phase 1: Extract Shared Utilities to service-core

**Goal**: Move framework-agnostic utilities to service-core for reuse

**New files in `service-core/src/main/java/org/budgetanalyzer/core/logging/`:**

#### 1. CorrelationIdGenerator.java
Static utility for generating correlation IDs.

```java
package org.budgetanalyzer.core.logging;

import java.util.UUID;

/**
 * Utility for generating correlation IDs for distributed tracing.
 */
public final class CorrelationIdGenerator {

  private static final String CORRELATION_ID_PREFIX = "req_";

  private CorrelationIdGenerator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Generates a new correlation ID.
   * Format: req_{16-hex-chars}
   *
   * @return correlation ID string
   */
  public static String generate() {
    var uuid = UUID.randomUUID().toString().replace("-", "");
    return CORRELATION_ID_PREFIX + uuid.substring(0, 16);
  }
}
```

#### 2. HttpLogFormatter.java
Static utility for formatting HTTP log messages.

```java
package org.budgetanalyzer.core.logging;

import java.util.Map;

/**
 * Utility for formatting HTTP request/response log messages.
 */
public final class HttpLogFormatter {

  private HttpLogFormatter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Formats a log message with prefix, details map, and optional body.
   *
   * @param prefix message prefix (e.g., "HTTP Request")
   * @param details structured details as key-value pairs
   * @param body optional body content
   * @return formatted log message
   */
  public static String formatLogMessage(String prefix, Map<String, Object> details, String body) {
    var sb = new StringBuilder();
    sb.append(prefix).append(" - ");
    sb.append(SafeLogger.toJson(details));

    if (body != null && !body.isEmpty()) {
      sb.append("\nBody: ").append(body);
    }

    return sb.toString();
  }
}
```

#### 3. SensitiveHeaderMasker.java
Utility for masking sensitive HTTP headers.

```java
package org.budgetanalyzer.core.logging;

import java.util.List;

/**
 * Utility for masking sensitive HTTP headers in logs.
 */
public final class SensitiveHeaderMasker {

  /**
   * Default list of sensitive header names.
   */
  public static final List<String> DEFAULT_SENSITIVE_HEADERS = List.of(
      "Authorization",
      "Cookie",
      "Set-Cookie",
      "X-API-Key",
      "X-Auth-Token",
      "Proxy-Authorization",
      "WWW-Authenticate"
  );

  private SensitiveHeaderMasker() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Checks if a header name is sensitive.
   *
   * @param headerName header name to check
   * @param sensitiveHeaders list of sensitive header names
   * @return true if sensitive
   */
  public static boolean isSensitive(String headerName, List<String> sensitiveHeaders) {
    return sensitiveHeaders.stream()
        .anyMatch(sensitive -> sensitive.equalsIgnoreCase(headerName));
  }

  /**
   * Masks a header value.
   *
   * @param value original value
   * @return masked value
   */
  public static String mask(String value) {
    return "***MASKED***";
  }
}
```

**Tests**: Add unit tests for each utility class.

---

### Phase 2: Create Shared Exception Handler Interface

**Goal**: Define common exception handling logic in interface with default methods

**New file: `service-web/src/main/java/org/budgetanalyzer/service/api/ApiExceptionHandler.java`**

```java
package org.budgetanalyzer.service.api;

import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceException;

import java.util.List;

/**
 * Interface for exception handlers with shared response building logic.
 * Both servlet and reactive exception handlers implement this interface
 * to reuse common error response construction.
 */
public interface ApiExceptionHandler {

  /**
   * Builds a validation error response.
   *
   * @param fieldErrors list of field validation errors
   * @return API error response
   */
  default ApiErrorResponse buildValidationError(List<FieldError> fieldErrors) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.VALIDATION_ERROR)
        .message("Validation failed for " + fieldErrors.size() + " field(s)")
        .fieldErrors(fieldErrors)
        .build();
  }

  /**
   * Builds a not found error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildNotFoundError(ResourceNotFoundException exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.NOT_FOUND)
        .message(exception.getMessage())
        .build();
  }

  /**
   * Builds an invalid request error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildInvalidRequestError(InvalidRequestException exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.INVALID_REQUEST)
        .message(exception.getMessage())
        .build();
  }

  /**
   * Builds a business error response with error code.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildBusinessError(BusinessException exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.APPLICATION_ERROR)
        .code(exception.getCode())
        .message(exception.getMessage())
        .build();
  }

  /**
   * Builds a service unavailable error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildServiceUnavailableError(ServiceException exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.SERVICE_UNAVAILABLE)
        .message(exception.getMessage())
        .build();
  }

  /**
   * Builds a generic internal error response.
   *
   * @param exception the exception
   * @return API error response
   */
  default ApiErrorResponse buildInternalError(Exception exception) {
    return ApiErrorResponse.builder()
        .type(ApiErrorType.INTERNAL_ERROR)
        .message("An unexpected error occurred")
        .build();
  }
}
```

**No tests needed** - This is just an interface with default methods (shared logic).

---

### Phase 3: Reorganize service-web Package Structure

**Goal**: Move servlet-specific code to `service.servlet.*` packages

#### 3.1 Move HTTP Filters

**From** `org.budgetanalyzer.service.http.*`
**To** `org.budgetanalyzer.service.servlet.http.*`

**Files to move:**
- `CorrelationIdFilter.java` - Update to use `CorrelationIdGenerator` from service-core
- `HttpLoggingFilter.java` - Update to use `HttpLogFormatter` from service-core
- `ContentLoggingUtil.java` - Update to use `SensitiveHeaderMasker` from service-core
- `HttpLoggingConfig.java` - No changes to logic

**Example refactoring in CorrelationIdFilter:**
```java
// OLD
private String generateCorrelationId() {
  var uuid = UUID.randomUUID().toString().replace("-", "");
  return CORRELATION_ID_PREFIX + uuid.substring(0, 16);
}

// NEW
import org.budgetanalyzer.core.logging.CorrelationIdGenerator;

private String generateCorrelationId() {
  return CorrelationIdGenerator.generate();
}
```

#### 3.2 Move Exception Handler

**From** `org.budgetanalyzer.service.api.DefaultApiExceptionHandler`
**To** `org.budgetanalyzer.service.servlet.api.DefaultApiExceptionHandler`

**Changes:**
1. Implement `ApiExceptionHandler` interface
2. Replace inline response building with interface default methods
3. Keep servlet-specific exception handling (MethodArgumentNotValidException, etc.)

**Example refactoring:**
```java
package org.budgetanalyzer.service.servlet.api;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiExceptionHandler;
import org.budgetanalyzer.service.api.FieldError;
import org.budgetanalyzer.service.exception.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for servlet-based (Spring MVC) applications.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultApiExceptionHandler implements ApiExceptionHandler {

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiErrorResponse handle(ResourceNotFoundException exception, WebRequest request) {
    return buildNotFoundError(exception);  // Uses interface default method
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(InvalidRequestException exception, WebRequest request) {
    return buildInvalidRequestError(exception);  // Uses interface default method
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiErrorResponse handle(BusinessException exception, WebRequest request) {
    return buildBusinessError(exception);  // Uses interface default method
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handle(MethodArgumentNotValidException exception, WebRequest request) {
    var fieldErrors = exception.getBindingResult().getAllErrors().stream()
        .filter(error -> error instanceof org.springframework.validation.FieldError)
        .map(error -> {
          var springFieldError = (org.springframework.validation.FieldError) error;
          return FieldError.of(
              springFieldError.getField(),
              error.getDefaultMessage(),
              springFieldError.getRejectedValue());
        })
        .toList();

    return buildValidationError(fieldErrors);  // Uses interface default method
  }

  // ... other servlet-specific exception handlers
}
```

#### 3.3 Update Package References

**Update imports in:**
- All files that reference moved classes
- Test files
- Documentation

---

### Phase 4: Implement Reactive Components

**Goal**: Create reactive equivalents of servlet filters and exception handler

#### 4.1 ReactiveCorrelationIdFilter

**File**: `service-web/src/main/java/org/budgetanalyzer/service/reactive/http/ReactiveCorrelationIdFilter.java`

```java
package org.budgetanalyzer.service.reactive.http;

import org.budgetanalyzer.core.logging.CorrelationIdGenerator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Reactive filter for managing correlation IDs in distributed tracing.
 * Runs before all other filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ReactiveCorrelationIdFilter implements WebFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String correlationId = extractOrGenerateCorrelationId(exchange.getRequest());

    // Add to response headers
    exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

    // Store in Reactor Context for logging
    return chain.filter(exchange)
        .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId));
  }

  private String extractOrGenerateCorrelationId(ServerHttpRequest request) {
    String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

    if (correlationId == null || correlationId.trim().isEmpty()) {
      correlationId = CorrelationIdGenerator.generate();
    }

    return correlationId;
  }
}
```

#### 4.2 Reactive Body Caching Decorators

**Problem**: Reactive request/response bodies are `Flux<DataBuffer>` that can only be consumed once. To log the body and still pass it to downstream handlers, we need custom decorators.

**File**: `service-web/src/main/java/org/budgetanalyzer/service/reactive/http/CachedBodyServerHttpRequestDecorator.java`

```java
package org.budgetanalyzer.service.reactive.http;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorator that caches the request body for logging while allowing downstream
 * handlers to still read it.
 */
public class CachedBodyServerHttpRequestDecorator extends ServerHttpRequestDecorator {

  private final Flux<DataBuffer> cachedBody;

  public CachedBodyServerHttpRequestDecorator(ServerHttpRequest delegate) {
    super(delegate);
    this.cachedBody = DataBufferUtils.join(delegate.getBody())
        .cache(); // Cache for multiple subscribers
  }

  @Override
  public Flux<DataBuffer> getBody() {
    return cachedBody;
  }

  /**
   * Reads the cached body as a string.
   *
   * @param maxBytes maximum bytes to read
   * @return Mono with body string
   */
  public Mono<String> getCachedBodyAsString(int maxBytes) {
    return cachedBody
        .next()
        .map(dataBuffer -> {
          int readableBytes = Math.min(dataBuffer.readableByteCount(), maxBytes);
          byte[] bytes = new byte[readableBytes];
          dataBuffer.read(bytes);
          return new String(bytes, getDelegate().getHeaders().getContentType().getCharset());
        })
        .defaultIfEmpty("");
  }
}
```

**File**: `service-web/src/main/java/org/budgetanalyzer/service/reactive/http/CachedBodyServerHttpResponseDecorator.java`

```java
package org.budgetanalyzer.service.reactive.http;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Decorator that caches the response body for logging.
 */
public class CachedBodyServerHttpResponseDecorator extends ServerHttpResponseDecorator {

  private StringBuilder cachedBody = new StringBuilder();

  public CachedBodyServerHttpResponseDecorator(ServerHttpResponse delegate) {
    super(delegate);
  }

  @Override
  public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
    return super.writeWith(
        Flux.from(body)
            .doOnNext(dataBuffer -> {
              // Cache body content
              byte[] bytes = new byte[dataBuffer.readableByteCount()];
              dataBuffer.read(bytes);
              cachedBody.append(new String(bytes, StandardCharsets.UTF_8));
            })
    );
  }

  /**
   * Gets the cached response body.
   *
   * @return response body string
   */
  public String getCachedBody() {
    return cachedBody.toString();
  }
}
```

#### 4.3 ReactiveHttpLoggingFilter

**File**: `service-web/src/main/java/org/budgetanalyzer/service/reactive/http/ReactiveHttpLoggingFilter.java`

```java
package org.budgetanalyzer.service.reactive.http;

import org.budgetanalyzer.core.logging.HttpLogFormatter;
import org.budgetanalyzer.service.config.HttpLoggingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Reactive filter for HTTP request/response logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 150)
public class ReactiveHttpLoggingFilter implements WebFilter {

  private static final Logger log = LoggerFactory.getLogger(ReactiveHttpLoggingFilter.class);
  private final HttpLoggingProperties properties;

  public ReactiveHttpLoggingFilter(HttpLoggingProperties properties) {
    this.properties = properties;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    long startTime = System.currentTimeMillis();

    // Decorate request/response to cache bodies
    var decoratedRequest = new CachedBodyServerHttpRequestDecorator(exchange.getRequest());
    var decoratedResponse = new CachedBodyServerHttpResponseDecorator(exchange.getResponse());

    var decoratedExchange = exchange.mutate()
        .request(decoratedRequest)
        .response(decoratedResponse)
        .build();

    // Log request
    logRequest(decoratedExchange);

    return chain.filter(decoratedExchange)
        .doFinally(signalType -> {
          long duration = System.currentTimeMillis() - startTime;
          logResponse(decoratedExchange, decoratedResponse, duration);
        });
  }

  private void logRequest(ServerWebExchange exchange) {
    Map<String, Object> details = new HashMap<>();
    details.put("method", exchange.getRequest().getMethod());
    details.put("uri", exchange.getRequest().getURI().toString());

    if (properties.isIncludeClientInfo()) {
      details.put("clientIp", exchange.getRequest().getRemoteAddress());
    }

    String message = HttpLogFormatter.formatLogMessage("HTTP Request", details, null);
    log.info(message);
  }

  private void logResponse(ServerWebExchange exchange,
                           CachedBodyServerHttpResponseDecorator response,
                           long duration) {
    Map<String, Object> details = new HashMap<>();
    details.put("status", response.getStatusCode());
    details.put("duration", duration + "ms");

    String message = HttpLogFormatter.formatLogMessage("HTTP Response", details, null);
    log.info(message);
  }
}
```

#### 4.4 ReactiveApiExceptionHandler

**File**: `service-web/src/main/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandler.java`

```java
package org.budgetanalyzer.service.reactive.api;

import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiExceptionHandler;
import org.budgetanalyzer.service.api.FieldError;
import org.budgetanalyzer.service.exception.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

/**
 * Global exception handler for reactive (Spring WebFlux) applications.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ReactiveApiExceptionHandler implements ApiExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleNotFound(ResourceNotFoundException ex) {
    return Mono.just(ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(buildNotFoundError(ex)));
  }

  @ExceptionHandler(InvalidRequestException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleInvalidRequest(InvalidRequestException ex) {
    return Mono.just(ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(buildInvalidRequestError(ex)));
  }

  @ExceptionHandler(BusinessException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleBusiness(BusinessException ex) {
    return Mono.just(ResponseEntity
        .status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(buildBusinessError(ex)));
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleValidation(WebExchangeBindException ex) {
    var fieldErrors = ex.getBindingResult().getAllErrors().stream()
        .filter(error -> error instanceof org.springframework.validation.FieldError)
        .map(error -> {
          var springFieldError = (org.springframework.validation.FieldError) error;
          return FieldError.of(
              springFieldError.getField(),
              error.getDefaultMessage(),
              springFieldError.getRejectedValue());
        })
        .toList();

    return Mono.just(ResponseEntity
        .badRequest()
        .body(buildValidationError(fieldErrors)));
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleServiceUnavailable(
      ServiceUnavailableException ex) {
    return Mono.just(ResponseEntity
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(buildServiceUnavailableError(ex)));
  }

  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<ApiErrorResponse>> handleGenericException(Exception ex) {
    return Mono.just(ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(buildInternalError(ex)));
  }
}
```

#### 4.5 ReactiveHttpLoggingConfig

**File**: `service-web/src/main/java/org/budgetanalyzer/service/reactive/config/ReactiveHttpLoggingConfig.java`

```java
package org.budgetanalyzer.service.reactive.config;

import org.budgetanalyzer.service.config.HttpLoggingProperties;
import org.budgetanalyzer.service.reactive.http.ReactiveCorrelationIdFilter;
import org.budgetanalyzer.service.reactive.http.ReactiveHttpLoggingFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for reactive HTTP logging filters.
 */
@Configuration
@EnableConfigurationProperties(HttpLoggingProperties.class)
public class ReactiveHttpLoggingConfig {

  /**
   * Correlation ID filter - always enabled.
   */
  @Bean
  public ReactiveCorrelationIdFilter reactiveCorrelationIdFilter() {
    return new ReactiveCorrelationIdFilter();
  }

  /**
   * HTTP logging filter - enabled via property.
   */
  @Bean
  @ConditionalOnProperty(prefix = "budgetanalyzer.service.http-logging", name = "enabled", havingValue = "true")
  public ReactiveHttpLoggingFilter reactiveHttpLoggingFilter(HttpLoggingProperties properties) {
    return new ReactiveHttpLoggingFilter(properties);
  }
}
```

---

### Phase 5: Fix Transitive Dependencies (BREAKING CHANGE)

**Goal**: Prevent servlet dependencies from leaking to reactive services (and vice versa)

#### 5.1 Update service-web/build.gradle.kts

**Current:**
```kotlin
dependencies {
    // Transitive - all consuming services get these
    api(project(":service-core"))
    api(libs.spring.boot.starter.web)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.boot.starter.oauth2.resource.server)
    api(libs.spring.boot.starter.validation)
    api(libs.commons.lang3)

    // SpringDoc OpenAPI
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
}
```

**New:**
```kotlin
dependencies {
    // Common - transitive (services get these)
    api(project(":service-core"))
    api(libs.commons.lang3)
    api(libs.spring.boot.starter.validation)

    // Stack-specific - compile-only (NOT transitive)
    // Services must explicitly add the stack they need
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.webflux)
    compileOnly(libs.spring.boot.starter.data.jpa)
    compileOnly(libs.spring.boot.starter.oauth2.resource.server)

    // SpringDoc - compile-only (services choose servlet or reactive version)
    compileOnly(libs.springdoc.openapi.starter.webmvc.ui)
    compileOnly("org.springdoc:springdoc-openapi-starter-webflux-ui:2.3.0")

    // Test - need both stacks for testing
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}
```

#### 5.2 Update Consuming Services

**All servlet services** (transaction-service, currency-service, etc.):

```kotlin
// build.gradle.kts
dependencies {
    // Explicitly add servlet stack
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // service-web provides exception handling, logging, security
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

**Reactive services** (session-gateway):

```kotlin
// build.gradle.kts
dependencies {
    // Explicitly add reactive stack
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // service-web provides exception handling, logging
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

---

### Phase 6: Update Autoconfiguration

**Goal**: Conditionally register servlet or reactive beans based on classpath

**File**: `service-web/src/main/java/org/budgetanalyzer/service/config/ServiceWebAutoConfiguration.java`

```java
package org.budgetanalyzer.service.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for service-web supporting both servlet and reactive stacks.
 * Conditionally registers components based on which web stack is on the classpath.
 */
@AutoConfiguration
public class ServiceWebAutoConfiguration {

  /**
   * Configuration for servlet-based (Spring MVC) web applications.
   * Activates when:
   * - Application type is SERVLET
   * - jakarta.servlet.Filter is on classpath
   */
  @Configuration
  @ConditionalOnWebApplication(type = Type.SERVLET)
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  @ComponentScan(basePackages = {
      "org.budgetanalyzer.service.servlet.http",
      "org.budgetanalyzer.service.servlet.api",
      "org.budgetanalyzer.service.servlet.config"
  })
  static class ServletWebConfiguration {
    // Servlet-specific beans registered via component scanning
  }

  /**
   * Configuration for reactive (Spring WebFlux) web applications.
   * Activates when:
   * - Application type is REACTIVE
   * - org.springframework.web.server.WebFilter is on classpath
   */
  @Configuration
  @ConditionalOnWebApplication(type = Type.REACTIVE)
  @ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
  @ComponentScan(basePackages = {
      "org.budgetanalyzer.service.reactive.http",
      "org.budgetanalyzer.service.reactive.api",
      "org.budgetanalyzer.service.reactive.config"
  })
  static class ReactiveWebConfiguration {
    // Reactive-specific beans registered via component scanning
  }
}
```

**Update**: `service-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
org.budgetanalyzer.service.config.ServiceWebAutoConfiguration
```

**Note**: OAuth2ResourceServerSecurityConfig may need to be split if it's servlet-specific.

---

### Phase 7: Update session-gateway

**Goal**: Replace debug logging with production HTTP logging from service-web

#### 7.1 Add Dependency

**File**: `session-gateway/build.gradle.kts`

```kotlin
dependencies {
    // Add service-web for HTTP logging and exception handling
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")

    // Existing dependencies...
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

#### 7.2 Remove Debug Logging Filters

**Delete files:**
- `session-gateway/src/main/java/org/budgetanalyzer/sessiongateway/config/RequestLoggingFilter.java`
- `session-gateway/src/main/java/org/budgetanalyzer/sessiongateway/config/RequestLoggingWebFilter.java`

#### 7.3 Add HTTP Logging Configuration

**File**: `session-gateway/src/main/resources/application.yml`

```yaml
budgetanalyzer:
  service:
    http-logging:
      enabled: true
      log-level: DEBUG
      include-request-body: true
      include-response-body: true
      include-request-headers: true
      include-response-headers: true
      include-query-params: true
      include-client-ip: true
      max-body-size: 10000
      log-errors-only: false
      sensitive-headers:
        - Authorization
        - Cookie
        - Set-Cookie
        - X-API-Key
```

#### 7.4 Verify Reactive Filters Work

**Test:**
1. Start session-gateway
2. Make requests through the gateway
3. Verify correlation IDs appear in logs
4. Verify HTTP request/response logging works
5. Verify exception handling returns standardized ApiErrorResponse format

---

### Phase 8: Testing & Documentation

#### 8.1 Unit Tests

**New test files:**

1. `service-core/src/test/java/org/budgetanalyzer/core/logging/CorrelationIdGeneratorTest.java`
2. `service-core/src/test/java/org/budgetanalyzer/core/logging/HttpLogFormatterTest.java`
3. `service-core/src/test/java/org/budgetanalyzer/core/logging/SensitiveHeaderMaskerTest.java`
4. `service-web/src/test/java/org/budgetanalyzer/service/reactive/http/ReactiveCorrelationIdFilterTest.java`
5. `service-web/src/test/java/org/budgetanalyzer/service/reactive/http/ReactiveHttpLoggingFilterTest.java`
6. `service-web/src/test/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandlerTest.java`

**Update existing tests:**
- Update package references for moved classes
- Ensure servlet tests still pass

#### 8.2 Integration Tests

**Servlet Integration Test:**

Create test that verifies servlet autoconfiguration works:
- `@SpringBootTest` with servlet web application type
- Verify `DefaultApiExceptionHandler` is registered
- Verify `HttpLoggingFilter` is registered
- Verify reactive beans are NOT registered

**Reactive Integration Test:**

Create test that verifies reactive autoconfiguration works:
- `@SpringBootTest` with reactive web application type
- Verify `ReactiveApiExceptionHandler` is registered
- Verify `ReactiveHttpLoggingFilter` is registered
- Verify servlet beans are NOT registered

#### 8.3 End-to-End Testing

1. **Test with transaction-service** (servlet):
   - Build and publish service-web: `./gradlew publishToMavenLocal`
   - Update transaction-service dependencies (add spring-boot-starter-web)
   - Rebuild transaction-service: `./gradlew clean build`
   - Run transaction-service and verify HTTP logging works
   - Test exception handling (trigger 404, 400, 500 errors)
   - Verify correlation IDs in logs

2. **Test with session-gateway** (reactive):
   - Update session-gateway dependencies (add service-web)
   - Remove debug logging filters
   - Add HTTP logging configuration
   - Rebuild session-gateway: `./gradlew clean build`
   - Run session-gateway and verify reactive HTTP logging works
   - Test exception handling (trigger various errors)
   - Verify correlation IDs in logs

#### 8.4 Documentation Updates

**Update `/workspace/service-common/CLAUDE.md`:**

1. **Architecture section** - Document servlet vs reactive packages:
   ```markdown
   ### service-web
   **Location**: `service-web/src/main/java/org/budgetanalyzer/service/`

   **Purpose**: Spring Boot web components (both servlet and reactive)

   **Contains**:
   - `api/` - Error response models (ApiErrorResponse, ApiErrorType, FieldError, ApiExceptionHandler)
   - `exception/` - Exception hierarchy (shared by both stacks)
   - `config/` - Shared configuration (HttpLoggingProperties, ServiceWebAutoConfiguration)
   - `servlet/` - Servlet-specific implementations (Spring MVC)
     - `http/` - HTTP filters (CorrelationIdFilter, HttpLoggingFilter)
     - `api/` - Exception handler (DefaultApiExceptionHandler)
   - `reactive/` - Reactive-specific implementations (Spring WebFlux)
     - `http/` - Reactive filters (ReactiveCorrelationIdFilter, ReactiveHttpLoggingFilter)
     - `api/` - Exception handler (ReactiveApiExceptionHandler)
   ```

2. **Autoconfiguration section** - Document conditional registration:
   ```markdown
   **service-web** (supports both servlet and reactive):
   - Servlet configuration activates with `@ConditionalOnWebApplication(type = SERVLET)`
   - Reactive configuration activates with `@ConditionalOnWebApplication(type = REACTIVE)`
   - Common components (ApiErrorResponse, exceptions) shared by both
   ```

3. **Publishing and Consumption section** - Document breaking change:
   ```markdown
   ### BREAKING CHANGE: Explicit Web Stack Dependency

   **As of version 0.0.2-SNAPSHOT**, consuming services must explicitly declare their web stack dependency.

   **Servlet services:**
   ```kotlin
   dependencies {
       implementation("org.springframework.boot:spring-boot-starter-web")
       implementation("org.budgetanalyzer:service-web:0.0.2-SNAPSHOT")
   }
   ```

   **Reactive services:**
   ```kotlin
   dependencies {
       implementation("org.springframework.boot:spring-boot-starter-webflux")
       implementation("org.budgetanalyzer:service-web:0.0.2-SNAPSHOT")
   }
   ```

   **Why?** This prevents classpath conflicts where reactive services would inherit servlet dependencies (and vice versa).
   ```

4. **Migration guide**:
   ```markdown
   ## Migration Guide: 0.0.1-SNAPSHOT → 0.0.2-SNAPSHOT

   ### For Servlet Services (transaction-service, currency-service, etc.)

   **Before:**
   ```kotlin
   dependencies {
       implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
       // spring-boot-starter-web was transitive
   }
   ```

   **After:**
   ```kotlin
   dependencies {
       implementation("org.springframework.boot:spring-boot-starter-web")  // NOW REQUIRED
       implementation("org.budgetanalyzer:service-web:0.0.2-SNAPSHOT")
   }
   ```

   ### For Reactive Services (session-gateway)

   **Before:**
   ```kotlin
   dependencies {
       implementation("org.springframework.cloud:spring-cloud-starter-gateway")
       // Did not use service-web
   }
   ```

   **After:**
   ```kotlin
   dependencies {
       implementation("org.springframework.cloud:spring-cloud-starter-gateway")
       implementation("org.budgetanalyzer:service-web:0.0.2-SNAPSHOT")  // NOW AVAILABLE
   }
   ```

   **Remove debug logging:**
   - Delete `RequestLoggingFilter.java` and `RequestLoggingWebFilter.java`
   - Add HTTP logging configuration to `application.yml`
   ```

**Update `/workspace/service-common/docs/error-handling.md`:**

Add section on reactive exception handling with examples.

**Create new doc**: `/workspace/service-common/docs/http-logging-reactive.md`

Document reactive-specific HTTP logging patterns, body caching, and Reactor Context usage.

#### 8.5 Build and Publish

```bash
# From /workspace/service-common
./gradlew clean spotlessApply
./gradlew clean build
./gradlew publishToMavenLocal
```

**Verify:**
- All tests pass
- JaCoCo coverage meets 80% threshold
- Checkstyle passes
- Spotless formatting applied

---

## Breaking Changes Summary

### For Consuming Services

**All servlet services** (transaction-service, currency-service, etc.) must add:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
```

**Reactive services** (session-gateway) can now use service-web:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-webflux")
implementation("org.budgetanalyzer:service-web:0.0.2-SNAPSHOT")
```

### Gradle Dependency Changes

**service-web/build.gradle.kts:**
- Changed: `api(libs.spring.boot.starter.web)` → `compileOnly(libs.spring.boot.starter.web)`
- Changed: `api(libs.spring.boot.starter.data.jpa)` → `compileOnly(libs.spring.boot.starter.data.jpa)`
- Added: `compileOnly(libs.spring.boot.starter.webflux)`

### Package Reorganization

**Moved classes:**
- `org.budgetanalyzer.service.http.*` → `org.budgetanalyzer.service.servlet.http.*`
- `org.budgetanalyzer.service.api.DefaultApiExceptionHandler` → `org.budgetanalyzer.service.servlet.api.DefaultApiExceptionHandler`

**Impact**: Any code that imports these classes needs updated imports (unlikely since these are internal to service-web).

---

## Testing Strategy

### Unit Tests
- Test new utilities in service-core (CorrelationIdGenerator, HttpLogFormatter, SensitiveHeaderMasker)
- Test reactive filters (ReactiveCorrelationIdFilter, ReactiveHttpLoggingFilter)
- Test reactive exception handler (ReactiveApiExceptionHandler)
- Test interface default methods (ApiExceptionHandler)

### Integration Tests
- Test servlet autoconfiguration (verify servlet beans registered, reactive beans NOT registered)
- Test reactive autoconfiguration (verify reactive beans registered, servlet beans NOT registered)
- Test both stacks in isolation

### End-to-End Tests
- Test with real servlet service (transaction-service)
- Test with real reactive service (session-gateway)
- Verify HTTP logging works in both contexts
- Verify exception handling produces standardized responses
- Verify correlation IDs propagate correctly

---

## Rollout Plan

### Version Bump
- **Current**: 0.0.1-SNAPSHOT
- **New**: 0.0.2-SNAPSHOT (breaking changes)

### Phase 1: Implement in service-common
1. Create feature branch: `feature/reactive-web-support`
2. Implement all phases (1-6)
3. Run full test suite
4. Code review
5. Merge to main
6. Publish to Maven local

### Phase 2: Update servlet services
1. Update transaction-service
2. Update currency-service
3. Update any other servlet services
4. Test each service individually
5. Deploy to dev environment

### Phase 3: Update reactive services
1. Update session-gateway
2. Remove debug logging
3. Add HTTP logging configuration
4. Test thoroughly
5. Deploy to dev environment

### Phase 4: Documentation and Communication
1. Update all documentation
2. Communicate breaking changes to team
3. Update migration guides
4. Create release notes

---

## Risks and Mitigations

### Risk 1: Breaking Change Impact
**Mitigation**: Clear documentation, migration guide, coordinated rollout across all services

### Risk 2: Reactive Body Caching Performance
**Concern**: Caching request/response bodies in reactive apps may impact performance
**Mitigation**:
- Make HTTP logging opt-in via configuration
- Implement max body size limits
- Test with realistic load

### Risk 3: Autoconfiguration Conflicts
**Concern**: Conditional bean registration might not work as expected
**Mitigation**:
- Comprehensive integration tests
- Test with both servlet and reactive apps
- Use `@ConditionalOnClass` with class names (not types) to avoid compile-time dependencies

### Risk 4: Reactor Context vs MDC
**Concern**: Reactive correlation ID storage in Reactor Context (not MDC like servlet)
**Mitigation**:
- Document difference in reactive logging patterns
- Consider using reactor-core's `Hooks.onEachOperator()` to propagate context to MDC
- Test log correlation in reactive scenarios

---

## Success Criteria

- ✅ service-web supports both servlet and reactive stacks
- ✅ Servlet services continue to work without regression
- ✅ session-gateway gets production-quality HTTP logging and exception handling
- ✅ No classpath conflicts between servlet and reactive dependencies
- ✅ Autoconfiguration correctly activates servlet or reactive beans
- ✅ All tests pass with 80%+ coverage
- ✅ Documentation updated and accurate
- ✅ Breaking changes communicated and services migrated

---

## Future Enhancements

### Potential Improvements
1. **Reactive OAuth2 Security** - Create reactive equivalent of OAuth2ResourceServerSecurityConfig
2. **Reactive Logging Correlation** - Improve MDC integration with Reactor Context
3. **Performance Metrics** - Add metrics for HTTP logging overhead
4. **Response Caching** - Implement HTTP response caching for both stacks
5. **GraphQL Support** - Add GraphQL-specific logging and exception handling

---

## References

- Spring Boot Autoconfiguration: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration
- Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Reactor Context: https://projectreactor.io/docs/core/release/reference/#context
- Spring Cloud Gateway: https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/

---

**END OF PLAN**
