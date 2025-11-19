# Reactive Package Testing Plan

## Overview

**Current State**: The reactive package (`service-web/src/main/java/org/budgetanalyzer/service/reactive/`) has **zero test coverage**.

**Goal**: Achieve 80%+ test coverage by creating comprehensive tests based on equivalent servlet test patterns.

**Approach**: Adapt existing servlet tests to reactive patterns, accounting for key differences in async handling, context management, and framework APIs.

---

## Testing Gap Analysis

### Reactive Classes Requiring Tests

| Class | Location | Purpose | Lines |
|-------|----------|---------|-------|
| `ReactiveApiExceptionHandler` | `reactive/api/` | Global exception handler for WebFlux apps | ~200 |
| `ReactiveCorrelationIdFilter` | `reactive/http/` | Correlation ID management via Reactor Context | ~100 |
| `ReactiveHttpLoggingFilter` | `reactive/http/` | HTTP request/response logging | ~150 |
| `ReactiveHttpLoggingConfig` | `reactive/http/` | Autoconfiguration for reactive filters | ~80 |
| `CachedBodyServerHttpRequestDecorator` | `reactive/http/` | Caches request body for logging | ~80 |
| `CachedBodyServerHttpResponseDecorator` | `reactive/http/` | Caches response body for logging | ~60 |

**Total**: 6 classes, ~670 lines of untested code

---

## Test Files to Create

### 1. ReactiveApiExceptionHandlerTest

**Priority**: HIGH (most critical for API consistency)

**Template**: `ServletApiExceptionHandlerTest.java` (352 lines, 25+ tests)

**Location**: `service-web/src/test/java/org/budgetanalyzer/service/reactive/api/ReactiveApiExceptionHandlerTest.java`

#### Key Adaptations from Servlet Version

- **Return Type**: Test `Mono<ResponseEntity<ApiErrorResponse>>` instead of direct `ResponseEntity`
- **Async Verification**: Use `StepVerifier` from reactor-test to verify mono behavior
- **Validation Exception**: Test `WebExchangeBindException` instead of `MethodArgumentNotValidException`
- **Parameters**: No `WebRequest` parameter in reactive handler methods

#### Test Cases to Implement (~25 tests)

**Exception Type Tests**:
- `handleInvalidRequestException_returnsStatus400()`
- `handleInvalidRequestException_withNullMessage_returnsStatus400()`
- `handleInvalidRequestException_withEmptyMessage_returnsStatus400()`
- `handleResourceNotFoundException_returnsStatus404()`
- `handleResourceNotFoundException_withNullMessage_returnsStatus404()`
- `handleResourceNotFoundException_withEmptyMessage_returnsStatus404()`
- `handleNoHandlerFoundException_returnsStatus404()`
- `handleBusinessException_returnsStatus422()`
- `handleBusinessException_withCode_includesCode()`
- `handleBusinessException_withNullMessage_returnsStatus422()`
- `handleClientException_returnsStatus502()`
- `handleServiceUnavailableException_returnsStatus503()`
- `handleMethodArgumentTypeMismatchException_returnsStatus400()`
- `handleMissingRequestPartException_returnsStatus400()`
- `handleMissingRequestParameterException_returnsStatus400()`

**Validation Tests**:
- `handleWebExchangeBindException_returnsStatus400WithFieldErrors()`
- `handleWebExchangeBindException_multipleFieldErrors()`
- `handleWebExchangeBindException_withGlobalErrors()`

**Generic Exception Tests**:
- `handleGenericException_returnsStatus500()`
- `handleException_withNullMessage_usesExceptionClassName()`

**Edge Cases**:
- `handleException_withExceptionChain_includesRootCause()`
- `handleMultipleExceptionsOfSameType_eachHandledCorrectly()`

#### Example Test Structure

```java
@Test
@DisplayName("InvalidRequestException returns 400 with error details")
void handleInvalidRequestException_returnsStatus400() {
    // Arrange
    var handler = new ReactiveApiExceptionHandler();
    var exception = new InvalidRequestException("Invalid input data");

    // Act
    Mono<ResponseEntity<ApiErrorResponse>> result = handler.handleInvalidRequestException(exception);

    // Assert
    StepVerifier.create(result)
        .assertNext(response -> {
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(ApiErrorType.VALIDATION_ERROR, response.getBody().getType());
            assertEquals("Invalid input data", response.getBody().getMessage());
        })
        .verifyComplete();
}
```

---

### 2. ReactiveCorrelationIdFilterTest

**Priority**: HIGH (core tracing functionality)

**Template**: `CorrelationIdFilterTest.java` (176 lines, 10 tests)

**Location**: `service-web/src/test/java/org/budgetanalyzer/service/reactive/http/ReactiveCorrelationIdFilterTest.java`

#### Key Adaptations from Servlet Version

- **Context Management**: Test Reactor Context instead of MDC (thread-local)
- **Filter Interface**: Mock `WebFilterChain` returning `Mono.empty()` instead of servlet `FilterChain`
- **Exchange Object**: Use `MockServerWebExchange` instead of `MockHttpServletRequest/Response`
- **Async Verification**: Use `StepVerifier` to verify filter execution

#### Test Cases to Implement (~10 tests)

- `shouldGenerateCorrelationIdWhenNotProvided()`
- `shouldUseExistingCorrelationIdFromHeader()`
- `shouldStoreCorrelationIdInReactorContext()`
- `shouldAddCorrelationIdToResponseHeader()`
- `shouldClearContextAfterExecution()`
- `shouldClearContextOnException()`
- `shouldGenerateUniqueIds()`
- `shouldHandleEmptyCorrelationIdHeader()`
- `shouldValidateCorrelationIdFormat()`
- `shouldPropagateContextThroughFilterChain()`

#### Example Test Structure

```java
@Test
@DisplayName("Should store correlation ID in Reactor Context")
void shouldStoreCorrelationIdInReactorContext() {
    // Arrange
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
    var chain = mock(WebFilterChain.class);
    when(chain.filter(any())).thenReturn(Mono.empty());

    // Act
    Mono<Void> result = filter.filter(exchange, chain);

    // Assert
    StepVerifier.create(result)
        .expectAccessibleContext()
        .assertThat(context -> {
            assertTrue(context.hasKey(ReactiveCorrelationIdFilter.CORRELATION_ID_KEY));
            assertNotNull(context.get(ReactiveCorrelationIdFilter.CORRELATION_ID_KEY));
        })
        .then()
        .verifyComplete();
}
```

---

### 3. ReactiveHttpLoggingFilterTest

**Priority**: MEDIUM (logging functionality)

**Template**: `HttpLoggingFilterTest.java` (261 lines, 12 tests)

**Location**: `service-web/src/test/java/org/budgetanalyzer/service/reactive/http/ReactiveHttpLoggingFilterTest.java`

#### Key Adaptations from Servlet Version

- **Exchange Mocking**: Use `MockServerWebExchange` with `MockServerHttpRequest/Response`
- **Body Decorators**: Verify `CachedBodyServerHttpRequest/ResponseDecorator` wrapping
- **Async Logging**: Test that logging completes without blocking
- **Flux Handling**: Test body logging with `Flux<DataBuffer>` instead of byte arrays

#### Test Cases to Implement (~12 tests)

- `shouldBypassFilterWhenDisabled()`
- `shouldLogRequestAndResponse()`
- `shouldExcludePathsMatchingExcludePatterns()`
- `shouldIncludeOnlyPathsMatchingIncludePatterns()`
- `shouldLogOnlyErrorsWhenEnabled()`
- `shouldWrapRequestAndResponseForCaching()`
- `shouldNotLogBodyForGetRequests()`
- `shouldLogBodyForPostRequests()`
- `shouldHandleExceptionsDuringLogging()`
- `shouldCopyResponseBodyToActualResponse()`
- `shouldRespectExcludePatternPrecedence()`
- `shouldHandleEmptyRequestBody()`

#### Example Test Structure

```java
@Test
@DisplayName("Should log request and response details")
void shouldLogRequestAndResponse() {
    // Arrange
    var properties = new HttpLoggingProperties();
    properties.setEnabled(true);
    properties.setIncludePayload(true);

    var filter = new ReactiveHttpLoggingFilter(properties);
    var exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/test")
            .header("Content-Type", "application/json")
            .body("{\"test\":\"data\"}")
    );

    var chain = mock(WebFilterChain.class);
    when(chain.filter(any())).thenReturn(Mono.empty());

    // Act
    Mono<Void> result = filter.filter(exchange, chain);

    // Assert
    StepVerifier.create(result)
        .verifyComplete();

    // Verify logging occurred (check logs or use log capture)
}
```

---

### 4. ReactiveHttpLoggingConfigTest

**Priority**: MEDIUM (autoconfiguration validation)

**Template**: `HttpLoggingConfigTest.java` (335 lines, 15+ tests)

**Location**: `service-web/src/test/java/org/budgetanalyzer/service/reactive/http/ReactiveHttpLoggingConfigTest.java`

#### Key Adaptations from Servlet Version

- **Context Runner**: Use `ReactiveWebApplicationContextRunner` instead of `WebApplicationContextRunner`
- **Conditional Check**: Verify `@ConditionalOnWebApplication(type = REACTIVE)` behavior
- **Bean Types**: Assert `WebFilter` beans instead of servlet `Filter` beans

#### Test Cases to Implement (~15 tests)

- `shouldRegisterCorrelationIdFilterInReactiveWebApp()`
- `shouldNotRegisterInNonWebApplication()`
- `shouldNotRegisterInServletWebApplication()`
- `shouldRegisterHttpLoggingFilterWhenEnabled()`
- `shouldNotRegisterHttpLoggingFilterWhenDisabled()`
- `shouldNotRegisterHttpLoggingFilterWhenPropertyNotSet()`
- `shouldRegisterBothFiltersWhenEnabled()`
- `shouldRegisterOnlyCorrelationIdFilterWhenLoggingDisabled()`
- `shouldRegisterHttpLoggingPropertiesBean()`
- `shouldPassPropertiesToHttpLoggingFilter()`
- `shouldHandleExcludePatterns()`
- `shouldHandleIncludePatterns()`
- `shouldHandleCustomSensitiveHeaders()`
- `shouldHandleLogErrorsOnlyFlag()`
- `shouldHandleAllBooleanPropertyCombinations()`

#### Example Test Structure

```java
@Test
@DisplayName("Should register correlation ID filter in reactive web application")
void shouldRegisterCorrelationIdFilterInReactiveWebApp() {
    new ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ReactiveHttpLoggingConfig.class))
        .run(context -> {
            assertThat(context).hasSingleBean(ReactiveCorrelationIdFilter.class);
            assertThat(context).getBean(ReactiveCorrelationIdFilter.class).isNotNull();
        });
}

@Test
@DisplayName("Should not register filters in servlet web application")
void shouldNotRegisterInServletWebApplication() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ReactiveHttpLoggingConfig.class))
        .run(context -> {
            assertThat(context).doesNotHaveBean(ReactiveCorrelationIdFilter.class);
            assertThat(context).doesNotHaveBean(ReactiveHttpLoggingFilter.class);
        });
}
```

---

### 5. CachedBodyServerHttpRequestDecoratorTest

**Priority**: LOW (supporting utility)

**Template**: None (servlet uses different approach with `ContentLoggingUtil`)

**Location**: `service-web/src/test/java/org/budgetanalyzer/service/reactive/http/CachedBodyServerHttpRequestDecoratorTest.java`

#### Test Cases to Create (~10 tests)

**Basic Functionality**:
- `shouldCacheRequestBodyForMultipleReads()`
- `shouldReturnCachedBodyAsString()`
- `shouldHandleEmptyBody()`
- `shouldPreserveOriginalHeaders()`

**Encoding & Charset**:
- `shouldHandleUtf8Encoding()`
- `shouldHandleCustomCharset()`
- `shouldHandleNullCharset()`

**Size Limits**:
- `shouldTruncateBodyAtMaxBytes()`
- `shouldHandleLargeBody()`

**Error Handling**:
- `shouldHandleDataBufferErrors()`

#### Example Test Structure

```java
@Test
@DisplayName("Should cache request body for multiple reads")
void shouldCacheRequestBodyForMultipleReads() {
    // Arrange
    var requestBody = "test body content";
    var bodyFlux = Flux.just(DefaultDataBufferFactory.sharedInstance
        .wrap(requestBody.getBytes(StandardCharsets.UTF_8)));

    var originalRequest = MockServerHttpRequest.post("/test")
        .body(bodyFlux);

    var decorator = new CachedBodyServerHttpRequestDecorator(
        originalRequest.build(),
        bodyFlux
    );

    // Act
    String firstRead = decorator.getCachedBodyAsString(1000).block();
    String secondRead = decorator.getCachedBodyAsString(1000).block();

    // Assert
    assertEquals(requestBody, firstRead);
    assertEquals(requestBody, secondRead);
}
```

---

### 6. CachedBodyServerHttpResponseDecoratorTest

**Priority**: LOW (supporting utility)

**Template**: None (different from servlet approach)

**Location**: `service-web/src/test/java/org/budgetanalyzer/service/reactive/http/CachedBodyServerHttpResponseDecoratorTest.java`

#### Test Cases to Create (~8 tests)

**Basic Functionality**:
- `shouldCaptureResponseBody()`
- `shouldReturnCachedBody()`
- `shouldHandleEmptyResponse()`

**Size Management**:
- `shouldRespectSizeLimit()`
- `shouldTruncateLargeResponses()`

**DataBuffer Management**:
- `shouldProperlyReleaseDataBuffers()`
- `shouldHandleMultipleWrites()`

**Error Handling**:
- `shouldHandleWriteErrors()`

#### Example Test Structure

```java
@Test
@DisplayName("Should capture response body")
void shouldCaptureResponseBody() {
    // Arrange
    var originalResponse = new MockServerHttpResponse();
    var decorator = new CachedBodyServerHttpResponseDecorator(originalResponse, 1000);
    var responseBody = "response content";

    // Act
    var writeResult = decorator.writeWith(
        Flux.just(DefaultDataBufferFactory.sharedInstance
            .wrap(responseBody.getBytes(StandardCharsets.UTF_8)))
    ).block();

    String cachedBody = decorator.getCachedBody();

    // Assert
    assertEquals(responseBody, cachedBody);
}
```

---

## Key Testing Differences: Servlet vs Reactive

### 1. Mocking Strategy

| Aspect | Servlet | Reactive |
|--------|---------|----------|
| **Request** | `MockHttpServletRequest` | `MockServerHttpRequest` |
| **Response** | `MockHttpServletResponse` | `MockServerHttpResponse` |
| **Exchange** | N/A | `MockServerWebExchange` |
| **Filter Chain** | `FilterChain` (mock) | `WebFilterChain` returning `Mono.empty()` |

### 2. Context Management

| Aspect | Servlet | Reactive |
|--------|---------|----------|
| **Correlation ID Storage** | MDC (thread-local) | Reactor Context |
| **Testing** | `MDC.get(key)` | `context.get(key)` in StepVerifier |
| **Cleanup** | `MDC.clear()` | Context cleared automatically |

### 3. Async Handling

| Aspect | Servlet | Reactive |
|--------|---------|----------|
| **Execution** | Synchronous | Asynchronous (Mono/Flux) |
| **Assertions** | Direct assertions | `StepVerifier.create().assertNext()` |
| **Completion** | Method returns | `.verifyComplete()` |

### 4. Body Caching

| Aspect | Servlet | Reactive |
|--------|---------|----------|
| **Request Wrapper** | `ContentCachingRequestWrapper` | `CachedBodyServerHttpRequestDecorator` |
| **Response Wrapper** | `ContentCachingResponseWrapper` | `CachedBodyServerHttpResponseDecorator` |
| **Body Type** | `byte[]` | `Flux<DataBuffer>` |
| **Reading** | `getContentAsByteArray()` | `getCachedBodyAsString().block()` |

### 5. Validation Exceptions

| Aspect | Servlet | Reactive |
|--------|---------|----------|
| **Exception Type** | `MethodArgumentNotValidException` | `WebExchangeBindException` |
| **Binding Result** | `BindingResult` | `BindingResult` (same) |
| **Field Errors** | `getFieldErrors()` | `getFieldErrors()` (same) |

---

## Dependencies & Test Infrastructure

### Required Dependencies

Check `service-web/build.gradle` includes:

```groovy
dependencies {
    // Core testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'

    // Reactive testing (CRITICAL)
    testImplementation 'io.projectreactor:reactor-test'

    // Spring reactive test support
    testImplementation 'org.springframework:spring-webflux' // test scope
}
```

### Testing Utilities to Use

1. **JUnit 5**: `@Test`, `@DisplayName`, `@ExtendWith`
2. **Mockito**: `@Mock`, `@ExtendWith(MockitoExtension.class)`, `when()`, `verify()`
3. **Reactor Test**: `StepVerifier` for async verification
4. **Spring Test**: `MockServerWebExchange`, `MockServerHttpRequest`, `MockServerHttpResponse`
5. **AssertJ**: Boot Test includes it for fluent assertions
6. **ApplicationContextRunner**: For autoconfiguration tests

---

## Implementation Plan

### Phase 1: Exception Handler (Week 1)
**Priority**: HIGH - Most critical for API consistency

1. Create `ReactiveApiExceptionHandlerTest.java`
2. Implement all 25+ test cases
3. Verify with `./gradlew :service-web:test --tests ReactiveApiExceptionHandlerTest`
4. Target: 100% coverage of `ReactiveApiExceptionHandler`

### Phase 2: Filters (Week 1-2)
**Priority**: HIGH - Core tracing/logging functionality

1. Create `ReactiveCorrelationIdFilterTest.java` (10 tests)
2. Create `ReactiveHttpLoggingFilterTest.java` (12 tests)
3. Verify with `./gradlew :service-web:test --tests "*.reactive.http.*"`
4. Target: 100% coverage of both filters

### Phase 3: Configuration (Week 2)
**Priority**: MEDIUM - Autoconfiguration validation

1. Create `ReactiveHttpLoggingConfigTest.java` (15+ tests)
2. Test all conditional logic and property binding
3. Verify with `./gradlew :service-web:test --tests ReactiveHttpLoggingConfigTest`
4. Target: 100% coverage of `ReactiveHttpLoggingConfig`

### Phase 4: Body Decorators (Week 2-3)
**Priority**: LOW - Supporting utilities

1. Create `CachedBodyServerHttpRequestDecoratorTest.java` (10 tests)
2. Create `CachedBodyServerHttpResponseDecoratorTest.java` (8 tests)
3. Verify with `./gradlew :service-web:test --tests "*.CachedBody*"`
4. Target: 80%+ coverage of both decorators

---

## Validation & Quality Gates

### After Each Test File

```bash
# Run specific test file
./gradlew :service-web:test --tests "ReactiveApiExceptionHandlerTest"

# Check for failures
echo $?  # Should be 0
```

### After Each Phase

```bash
# Run all reactive tests
./gradlew :service-web:test --tests "*.reactive.*"

# Generate coverage report
./gradlew :service-web:jacocoTestReport

# View coverage
open service-web/build/reports/jacoco/test/html/index.html
```

### Final Validation

```bash
# Full clean build
./gradlew clean spotlessApply
./gradlew clean build

# Verify no test failures
./gradlew :service-web:test

# Check coverage meets 80% minimum
./gradlew :service-web:jacocoTestCoverageVerification
```

---

## Expected Outcomes

### Coverage Metrics

| Metric | Before | Target | Expected |
|--------|--------|--------|----------|
| **Reactive Package Coverage** | 0% | 80% | 85%+ |
| **ReactiveApiExceptionHandler** | 0% | 100% | 100% |
| **ReactiveCorrelationIdFilter** | 0% | 100% | 100% |
| **ReactiveHttpLoggingFilter** | 0% | 80% | 90% |
| **ReactiveHttpLoggingConfig** | 0% | 100% | 100% |
| **Body Decorators** | 0% | 80% | 80% |

### Test Metrics

- **Total Test Files**: 6 new files
- **Total Test Methods**: 80+ tests
- **Estimated Lines**: ~1,500 lines of test code
- **Time Estimate**: 2-3 weeks for complete implementation

### Quality Goals

- ✅ All tests follow existing servlet test patterns
- ✅ Clear, descriptive test names with `@DisplayName`
- ✅ Comprehensive edge case coverage
- ✅ No flaky tests (deterministic behavior)
- ✅ Fast execution (< 5 seconds total for reactive tests)
- ✅ Proper cleanup (no resource leaks)

---

## Risks & Mitigation

### Risk 1: Reactor Context Propagation Complexity
**Mitigation**: Study existing reactive examples in Spring documentation, use `StepVerifier.expectAccessibleContext()`

### Risk 2: DataBuffer Memory Leaks
**Mitigation**: Ensure proper `DataBuffer.release()` in decorators, verify with leak detection in tests

### Risk 3: Async Timing Issues
**Mitigation**: Use `StepVerifier` with explicit timeouts, avoid `block()` in production code

### Risk 4: Missing reactor-test Dependency
**Mitigation**: Verify dependency exists before starting, add if missing

---

## Success Criteria

1. ✅ All 6 test files created and passing
2. ✅ 80%+ code coverage for reactive package
3. ✅ Zero test failures in `./gradlew clean build`
4. ✅ Coverage report shows green for all reactive classes
5. ✅ Tests follow established patterns from servlet tests
6. ✅ No Checkstyle/Spotless violations in test code

---

## References

### Servlet Test Templates
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/api/ServletApiExceptionHandlerTest.java`
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/http/CorrelationIdFilterTest.java`
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/http/HttpLoggingFilterTest.java`
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/http/HttpLoggingConfigTest.java`

### Reactive Implementation
- `service-web/src/main/java/org/budgetanalyzer/service/reactive/`

### Documentation
- [Project Reactor Testing](https://projectreactor.io/docs/core/release/reference/#testing)
- [Spring WebFlux Testing](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html)
- [StepVerifier Documentation](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html)

---

## Notes

- Keep test structure consistent with servlet tests for maintainability
- Focus on behavior testing, not implementation details
- Use descriptive assertion messages for easier debugging
- Consider adding integration tests later if unit tests insufficient
- Document any reactive-specific testing patterns discovered during implementation
