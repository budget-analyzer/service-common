# Reactive Support Implementation - Integration Test Debugging

## Session Context

This document captures the state of the reactive support implementation for the next Claude session to continue debugging 8 failing integration tests.

## Current Status (95% Complete)

### ✅ Completed Work

1. **Core Implementation** (100%)
   - All reactive and servlet code implemented
   - Package structure reorganized (servlet/ and reactive/)
   - Conditional autoconfiguration working
   - All code compiles successfully

2. **Test Fixes** (87% complete)
   - Fixed circular import in ServiceWebAutoConfiguration
   - Deleted old package files (org.budgetanalyzer.service.http/, old DefaultApiExceptionHandler)
   - Moved all test files to new servlet package structure
   - Updated all test imports
   - Fixed test expectations for generic error messages (security improvement)
   - **service-core**: 99/99 tests passing ✅
   - **service-web**: 263/271 tests passing (97%)

3. **Documentation** (100%)
   - Updated CLAUDE.md with new architecture
   - Added Breaking Changes section
   - Updated Autoconfiguration section
   - Documented package reorganization

4. **Code Quality** (100%)
   - All code passes Spotless formatting
   - No compilation errors
   - Checkstyle compliance

### ⚠️ Remaining Issue: 8 Integration Test Failures

**File**: `/workspace/service-common/service-web/src/test/java/org/budgetanalyzer/service/integration/ExceptionHandlingIntegrationIntegrationTest.java`

**ALL 8 failing tests in this one file:**
1. Should return 404 with ApiErrorResponse for ResourceNotFoundException
2. Should return 400 with field errors for validation failures
3. Should return 500 with ApiErrorResponse for ServiceException
4. Should return 422 with ApiErrorResponse for BusinessException
5. Should return 503 with ApiErrorResponse for ServiceUnavailableException
6. Should include correlation ID in all error responses
7. Should return 500 with ApiErrorResponse for unexpected RuntimeException
8. Should return 400 with ApiErrorResponse for InvalidRequestException

**Note**: One test in the same class PASSES: "Should return 200 for valid request"

## Problem Description

### Symptom
All exception-throwing endpoints return HTTP 200 instead of the expected error status codes (404, 400, 422, 500, 503).

**Example:**
```
Expected: 404 (ResourceNotFoundException)
Actual: 200 OK
```

### What Works
- ✅ Unit tests for `DefaultApiExceptionHandler` (ALL PASSING)
- ✅ Bean registration test (confirms DefaultApiExceptionHandler bean IS created)
- ✅ Other integration tests (ServiceCommonAutoConfigurationIntegrationTest - ALL PASSING)
- ✅ Valid request test in the SAME test class (returns 200 as expected)

### What's Broken
- ❌ MockMvc is not routing exceptions through the `@RestControllerAdvice` handler
- ❌ Exceptions are thrown but not caught/handled (returning 200 instead of error codes)

## Key Files

### Test File (Failing)
**Location**: `service-web/src/test/java/org/budgetanalyzer/service/integration/ExceptionHandlingIntegrationIntegrationTest.java`

**Current Configuration:**
```java
@SpringBootTest(
    classes = {TestApplication.class, TestSecurityConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.example.com/",
      "AUTH0_AUDIENCE=https://test-api.example.com",
      "spring.main.web-application-type=servlet"
    })
@AutoConfigureMockMvc
```

### Test Application
**Location**: `service-web/src/test/java/org/budgetanalyzer/service/integration/TestApplication.java`

**Component Scanning:**
```java
@SpringBootApplication(
    scanBasePackages = {
      "org.budgetanalyzer.service",
      "org.budgetanalyzer.core",
      "org.budgetanalyzer.core.integration.fixture",
      "org.budgetanalyzer.service.integration.fixture"
    })
```

### Test Controller (Working)
**Location**: `service-web/src/test/java/org/budgetanalyzer/service/integration/fixture/TestController.java`

**Example endpoint:**
```java
@GetMapping("/not-found")
public ResponseEntity<String> throwNotFound() {
    throw new ResourceNotFoundException("Test resource not found");
}
```

### Exception Handler (Working in unit tests)
**Location**: `service-web/src/main/java/org/budgetanalyzer/service/servlet/api/DefaultApiExceptionHandler.java`

**Annotation:**
```java
@Component
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultApiExceptionHandler implements ApiExceptionHandler {
    // ... handlers ...
}
```

### Autoconfiguration
**Location**: `service-web/src/main/java/org/budgetanalyzer/service/config/ServiceWebAutoConfiguration.java`

**Servlet Configuration:**
```java
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ComponentScan(
    basePackages = {
      "org.budgetanalyzer.service.servlet.http",
      "org.budgetanalyzer.service.servlet.api"
    })
static class ServletWebConfiguration {
    // Servlet-specific beans registered via component scanning
}
```

**Registered via**: `service-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## Evidence & Observations

### What We Know
1. **Autoconfiguration IS working** - Other integration tests confirm beans are registered
2. **TestController IS working** - Valid request test passes (returns 200)
3. **DefaultApiExceptionHandler IS registered** - Bean registration test passes
4. **Unit tests work perfectly** - All 23 DefaultApiExceptionHandler unit tests pass
5. **Both web stacks on test classpath** - `spring-boot-starter-web` AND `spring-boot-starter-webflux` (may cause confusion)

### What We've Tried
1. ✅ Added `webEnvironment = SpringBootTest.WebEnvironment.MOCK`
2. ✅ Added `spring.main.web-application-type=servlet` property
3. ✅ Removed `addFilters = false` (no change)
4. ✅ Added explicit web application type configuration

### Test Dependencies
```kotlin
testImplementation(libs.spring.boot.starter.web)
testImplementation(libs.spring.boot.starter.webflux)  // BOTH on classpath!
testImplementation(libs.spring.boot.starter.data.jpa)
testImplementation(libs.spring.boot.starter.oauth2.resource.server)
testImplementation(libs.spring.boot.starter.test)
```

## Suggested Debugging Steps

### 1. Verify Bean Registration in Test Context

Add a test to `ExceptionHandlingIntegrationIntegrationTest` to verify beans:

```java
@Autowired(required = false)
private DefaultApiExceptionHandler exceptionHandler;

@Autowired
private ApplicationContext context;

@Test
void debugBeanRegistration() {
    // Check if handler bean exists
    assertNotNull(exceptionHandler, "DefaultApiExceptionHandler should be autowired");

    // Check all RestControllerAdvice beans
    Map<String, Object> advisors = context.getBeansWithAnnotation(RestControllerAdvice.class);
    System.out.println("RestControllerAdvice beans: " + advisors.keySet());

    // Check if our handler is in the list
    boolean found = advisors.values().stream()
        .anyMatch(bean -> bean instanceof DefaultApiExceptionHandler);
    assertTrue(found, "DefaultApiExceptionHandler should be in RestControllerAdvice beans");
}
```

### 2. Check MockMvc Configuration

Verify MockMvc is using the exception handler:

```java
@Autowired
private WebApplicationContext webApplicationContext;

@BeforeEach
void debugMockMvcSetup() {
    // Manually build MockMvc to ensure handler is included
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .build();

    // Or use standalone setup with explicit controller and handler
    mockMvc = MockMvcBuilders
        .standaloneSetup(new TestController())
        .setControllerAdvice(exceptionHandler)
        .build();
}
```

### 3. Test with Explicit Handler Registration

Try importing ServiceWebAutoConfiguration explicitly:

```java
@SpringBootTest(
    classes = {
        TestApplication.class,
        TestSecurityConfig.class,
        ServiceWebAutoConfiguration.class  // Explicit import
    },
    // ... rest of config
)
```

### 4. Investigate Servlet vs Reactive Conflict

The test has BOTH web starters on classpath. Try:

**Option A**: Create separate test source sets for servlet vs reactive
**Option B**: Use `@ConditionalOnMissingBean` in test config
**Option C**: Exclude reactive starter from test classpath:

```kotlin
testImplementation(libs.spring.boot.starter.web)
// Remove or comment out:
// testImplementation(libs.spring.boot.starter.webflux)
```

### 5. Check Exception Handler Order

Verify no other exception handler is taking precedence:

```java
@Test
void debugExceptionHandlerOrder() {
    Map<String, Object> handlers = context.getBeansWithAnnotation(ControllerAdvice.class);
    handlers.putAll(context.getBeansWithAnnotation(RestControllerAdvice.class));

    handlers.forEach((name, bean) -> {
        System.out.println("Handler: " + name);
        System.out.println("  Type: " + bean.getClass());
        Order order = bean.getClass().getAnnotation(Order.class);
        System.out.println("  Order: " + (order != null ? order.value() : "none"));
    });
}
```

### 6. Enable Debug Logging

Add to test properties:

```java
properties = {
    // ... existing properties ...
    "logging.level.org.springframework.web=DEBUG",
    "logging.level.org.budgetanalyzer=DEBUG"
}
```

Run test and check logs for:
- Exception handler registration
- Request mapping
- Exception handling flow

### 7. Compare with Working Integration Test

**File**: `ServiceCommonAutoConfigurationIntegrationTest.java` (ALL PASSING)

**Compare:**
- Test configuration
- MockMvc setup
- Bean registration approach
- Any differences that might explain why one works and the other doesn't

### 8. Simplify Test

Create a minimal reproduction:

```java
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class MinimalExceptionTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldHandle404() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
            .andDo(print())  // Print full request/response
            .andExpect(status().isNotFound());
    }
}
```

## Quick Wins to Try First

### Option 1: Manual MockMvc Setup
Replace `@AutoConfigureMockMvc` with manual setup in `@BeforeEach`:

```java
@Autowired
private WebApplicationContext wac;

@Autowired
private DefaultApiExceptionHandler exceptionHandler;

private MockMvc mockMvc;

@BeforeEach
void setup() {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(wac)
        .build();
}
```

### Option 2: Standalone Setup
```java
@Autowired
private TestController testController;

@Autowired
private DefaultApiExceptionHandler exceptionHandler;

@BeforeEach
void setup() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(testController)
        .setControllerAdvice(exceptionHandler)
        .build();
}
```

### Option 3: Remove Reactive Starter
Edit `service-web/build.gradle.kts`:

```kotlin
// testImplementation(libs.spring.boot.starter.webflux)  // Comment out
```

## Expected Outcome

Once fixed, all 8 tests should pass, resulting in:
- **service-core**: 99/99 passing
- **service-web**: 271/271 passing
- **Total**: 370/370 passing ✅

## Commands to Run

```bash
# Run failing tests
./gradlew :service-web:test --tests "*ExceptionHandlingIntegrationIntegrationTest*"

# Run with debug logging
./gradlew :service-web:test --tests "*ExceptionHandlingIntegrationIntegrationTest*" --info

# Run all tests
./gradlew clean test

# Final build
./gradlew clean spotlessApply build

# Publish
./gradlew publishToMavenLocal
```

## Success Criteria

✅ All 271 service-web tests passing
✅ Build succeeds without errors
✅ All code formatted with Spotless
✅ Ready to publish to Maven Local

## Additional Context

### Generic Error Message Change
We updated tests to expect generic "An unexpected error occurred" for internal errors instead of specific exception messages. This is a **security improvement** to prevent leaking internal implementation details.

**Changed in:**
- `DefaultApiExceptionHandlerTest` (7 tests updated)
- `ExceptionHandlingIntegrationIntegrationTest` (2 tests updated for ServiceException and RuntimeException)

### Files Modified in Last Session
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/api/DefaultApiExceptionHandlerTest.java`
- `service-web/src/test/java/org/budgetanalyzer/service/servlet/http/ContentLoggingUtilTest.java`
- `service-web/src/test/java/org/budgetanalyzer/service/integration/ExceptionHandlingIntegrationIntegrationTest.java`
- `CLAUDE.md` (breaking changes and architecture updates)

### Git Status
```
M CLAUDE.md
M service-web/src/test/java/org/budgetanalyzer/service/servlet/api/DefaultApiExceptionHandlerTest.java
M service-web/src/test/java/org/budgetanalyzer/service/servlet/http/ContentLoggingUtilTest.java
M service-web/src/test/java/org/budgetanalyzer/service/integration/ExceptionHandlingIntegrationIntegrationTest.java
? docs/plans/reactive-support-integration-test-debugging.md
```

## Next Session Action Plan

1. **Start here**: Run the failing tests to confirm current state
2. **Try Quick Win**: Option 3 (remove reactive starter from test classpath)
3. **If that fails**: Try Quick Win Option 1 (manual MockMvc setup)
4. **If still failing**: Proceed through debugging steps 1-8 systematically
5. **Document findings**: Update this file with what worked/didn't work
6. **Final steps**: Once tests pass, run full build and publish

Good luck! 🚀
