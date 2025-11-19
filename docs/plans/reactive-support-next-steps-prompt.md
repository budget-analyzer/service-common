# Prompt for Next Claude Session: Complete Reactive Support Implementation

## Context

The reactive (WebFlux) support implementation for service-web is **95% complete**. All core functionality has been implemented and compiles successfully. However, **41 integration tests are failing** due to package reorganization, and documentation needs to be updated.

**Current Status:**
- ✅ All new code written and compiles
- ✅ service-core: 99/99 tests passing
- ⚠️ service-web: 271 tests total, 41 failing
- 📝 Documentation needs updates

## Your Task

Complete the reactive support implementation by:
1. Fixing the 41 failing integration tests
2. Deleting old package files (if duplicates exist)
3. Updating CLAUDE.md documentation
4. Verifying the build passes completely

## Background Reading

**IMPORTANT**: Read these files first to understand what was done:

1. `/workspace/service-common/docs/plans/service-web-reactive-support.md` - Original implementation plan
2. `/workspace/service-common/docs/plans/reactive-support-implementation-status.md` - What's been completed

## Step-by-Step Instructions

### Step 1: Understand the Package Reorganization

**Key Changes Made:**
```
OLD STRUCTURE:
org.budgetanalyzer.service.http.*
org.budgetanalyzer.service.api.DefaultApiExceptionHandler

NEW STRUCTURE:
org.budgetanalyzer.service.servlet.http.*          (servlet-specific)
org.budgetanalyzer.service.servlet.api.*           (servlet-specific)
org.budgetanalyzer.service.reactive.http.*         (reactive-specific - NEW)
org.budgetanalyzer.service.reactive.api.*          (reactive-specific - NEW)
org.budgetanalyzer.service.config.*                (shared config)
```

### Step 2: Fix Failing Integration Tests

**Command to see test failures:**
```bash
cd /workspace/service-common
./gradlew clean test --no-daemon 2>&1 | grep -A 20 "FAILED"
```

**What to do:**

1. **Identify failing tests** - Look for tests in `service-web/src/test/java/`

2. **Update imports** - Change old package imports to new ones:
   ```java
   // OLD
   import org.budgetanalyzer.service.http.HttpLoggingFilter;
   import org.budgetanalyzer.service.api.DefaultApiExceptionHandler;

   // NEW
   import org.budgetanalyzer.service.servlet.http.HttpLoggingFilter;
   import org.budgetanalyzer.service.servlet.api.DefaultApiExceptionHandler;
   ```

3. **Update test configurations** - Fix `@ComponentScan` or `@Import` annotations:
   ```java
   // Example fix
   @ComponentScan(basePackages = {
       "org.budgetanalyzer.service.servlet",  // Updated
       "org.budgetanalyzer.service.reactive"  // Updated
   })
   ```

4. **Fix Spring Boot test application configs** - Update any test application classes

5. **Verify each fix** - Run tests after each change:
   ```bash
   ./gradlew :service-web:test --tests "FailingTestClassName"
   ```

**Expected Result**: All 271 tests should pass

### Step 3: Delete Old Package Files (If They Exist)

**Check if old files still exist:**
```bash
find /workspace/service-common/service-web/src/main/java/org/budgetanalyzer/service/http -type f 2>/dev/null
find /workspace/service-common/service-web/src/main/java/org/budgetanalyzer/service/api -type f -name "Default*" 2>/dev/null
```

**If duplicates exist, DELETE the old files:**
- `service-web/src/main/java/org/budgetanalyzer/service/http/CorrelationIdFilter.java`
- `service-web/src/main/java/org/budgetanalyzer/service/http/HttpLoggingFilter.java`
- `service-web/src/main/java/org/budgetanalyzer/service/http/ContentLoggingUtil.java`
- `service-web/src/main/java/org/budgetanalyzer/service/http/HttpLoggingConfig.java`
- `service-web/src/main/java/org/budgetanalyzer/service/http/HttpLoggingProperties.java` (moved to config/)
- `service-web/src/main/java/org/budgetanalyzer/service/api/DefaultApiExceptionHandler.java` (moved to servlet.api/)

**DO NOT DELETE:**
- `service-web/src/main/java/org/budgetanalyzer/service/api/ApiErrorResponse.java` (shared)
- `service-web/src/main/java/org/budgetanalyzer/service/api/ApiErrorType.java` (shared)
- `service-web/src/main/java/org/budgetanalyzer/service/api/FieldError.java` (shared)
- `service-web/src/main/java/org/budgetanalyzer/service/api/ApiExceptionHandler.java` (NEW shared interface)

### Step 4: Update CLAUDE.md Documentation

**File**: `/workspace/service-common/CLAUDE.md`

**Sections to update:**

#### A. Module Architecture Section

Add reactive vs servlet package information:

```markdown
### service-web
**Location**: `service-web/src/main/java/org/budgetanalyzer/service/`

**Purpose**: Spring Boot web components (both servlet and reactive)

**Contains**:
- `api/` - Error response models (ApiErrorResponse, ApiErrorType, FieldError, ApiExceptionHandler)
- `exception/` - Exception hierarchy (shared by both stacks)
- `config/` - Shared configuration (HttpLoggingProperties, ServiceWebAutoConfiguration)
- `servlet/` - Servlet-specific implementations (Spring MVC)
  - `http/` - HTTP filters (CorrelationIdFilter, HttpLoggingFilter, ContentLoggingUtil, HttpLoggingConfig)
  - `api/` - Exception handler (DefaultApiExceptionHandler)
- `reactive/` - Reactive-specific implementations (Spring WebFlux)
  - `http/` - Reactive filters (ReactiveCorrelationIdFilter, ReactiveHttpLoggingFilter, body caching decorators, ReactiveHttpLoggingConfig)
  - `api/` - Exception handler (ReactiveApiExceptionHandler)

**Dependencies**: service-core (transitive), Spring Boot Starter Web (compileOnly), Spring Boot Starter WebFlux (compileOnly)

**Note**: service-web now supports BOTH servlet and reactive stacks through conditional autoconfiguration.
```

#### B. Autoconfiguration Section

Update to document conditional registration:

```markdown
### service-web: Automatic Configuration

**Registered via**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**ServiceWebAutoConfiguration** - Conditionally registers servlet OR reactive components:

**For Servlet Applications** (Spring MVC):
- Activates when `@ConditionalOnWebApplication(type = SERVLET)`
- **DefaultApiExceptionHandler** - Global exception handling
- **CorrelationIdFilter** - Always enabled
- **HttpLoggingFilter** - Opt-in via configuration
- **HttpLoggingConfig** - Configuration for servlet filters

**For Reactive Applications** (Spring WebFlux):
- Activates when `@ConditionalOnWebApplication(type = REACTIVE)`
- **ReactiveApiExceptionHandler** - Global exception handling
- **ReactiveCorrelationIdFilter** - Always enabled
- **ReactiveHttpLoggingFilter** - Opt-in via configuration
- **ReactiveHttpLoggingConfig** - Configuration for reactive filters

**Shared Configuration**:
- **HttpLoggingProperties** - Shared between servlet and reactive
```

#### C. Add Breaking Changes Section

Add a new section before "Architectural Principles":

```markdown
## Breaking Changes (Version 0.0.2-SNAPSHOT)

### Dependency Management Change

**BREAKING**: As of version 0.0.2-SNAPSHOT, consuming services must explicitly declare their web stack dependency.

**Before (0.0.1-SNAPSHOT):**
```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
    // spring-boot-starter-web was transitive
}
```

**After (0.0.2-SNAPSHOT and later):**

**Servlet services:**
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")  // NOW REQUIRED
    implementation("org.budgetanalyzer:service-web:0.0.2-SNAPSHOT")
}
```

**Reactive services:**
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // NOW REQUIRED
    implementation("org.budgetanalyzer:service-web:0.0.2-SNAPSHOT")
}
```

**Why?** This prevents classpath conflicts where reactive services would inherit servlet dependencies (and vice versa).

### Package Reorganization

Components have been reorganized to separate servlet and reactive implementations:

| Old Package | New Package | Type |
|-------------|-------------|------|
| `org.budgetanalyzer.service.http.*` | `org.budgetanalyzer.service.servlet.http.*` | Servlet |
| `org.budgetanalyzer.service.api.DefaultApiExceptionHandler` | `org.budgetanalyzer.service.servlet.api.DefaultApiExceptionHandler` | Servlet |
| N/A | `org.budgetanalyzer.service.reactive.http.*` | Reactive (NEW) |
| N/A | `org.budgetanalyzer.service.reactive.api.*` | Reactive (NEW) |

**Impact**: Internal to service-web only. Consuming services don't import these classes directly.
```

#### D. Update Discovery Commands

Update the discovery section to show new package structure:

```bash
# View servlet package structure
find service-web/src/main/java/org/budgetanalyzer/service/servlet -type d

# View reactive package structure
find service-web/src/main/java/org/budgetanalyzer/service/reactive -type d

# Find servlet filters
grep -r "@Component" service-web/src/main/java/org/budgetanalyzer/service/servlet/

# Find reactive filters
grep -r "@Component" service-web/src/main/java/org/budgetanalyzer/service/reactive/
```

### Step 5: Verify Build Passes

**Run full build:**
```bash
cd /workspace/service-common
./gradlew clean spotlessApply
./gradlew clean build
```

**Expected output:**
```
BUILD SUCCESSFUL
```

**Verify test counts:**
- service-core: 99/99 tests passing
- service-web: 271/271 tests passing (was 230 passed, 41 failed - now all pass)

### Step 6: Final Checklist

Before completing, verify:

- [ ] All 271 service-web tests pass
- [ ] Build completes successfully (`./gradlew clean build`)
- [ ] No duplicate files in old package locations
- [ ] CLAUDE.md updated with new package structure
- [ ] CLAUDE.md includes breaking changes section
- [ ] Spotless formatting applied (`./gradlew spotlessApply`)

## Success Criteria

When you're done, the following should all be true:

1. ✅ `./gradlew clean build` completes with `BUILD SUCCESSFUL`
2. ✅ All tests pass (99 in service-core + 271 in service-web = 370 total)
3. ✅ CLAUDE.md documents new architecture
4. ✅ No old package files remain (if they were duplicates)

## Troubleshooting

### If tests fail with "Bean not found" errors:

Check that the test's `@ComponentScan` includes the new packages:
```java
@ComponentScan(basePackages = {
    "org.budgetanalyzer.service.servlet",
    "org.budgetanalyzer.service.reactive",
    "org.budgetanalyzer.service.config"
})
```

### If tests fail with import errors:

Update imports from old packages to new ones. Use IDE refactoring or:
```bash
# Find files with old imports
grep -r "import org.budgetanalyzer.service.http" service-web/src/test/
```

### If autoconfiguration tests fail:

Make sure `ServiceWebAutoConfiguration` is being loaded. Check:
```
service-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Should contain:
```
org.budgetanalyzer.service.config.ServiceWebAutoConfiguration
```

## Additional Context

**Why this refactoring was done:**
- Enable reactive (WebFlux) services to use service-web
- Prevent classpath conflicts between servlet and reactive dependencies
- Share common code (exceptions, error responses, configuration) between stacks
- Maintain backward compatibility via autoconfiguration

**What was NOT changed:**
- Shared components (ApiErrorResponse, ApiErrorType, FieldError, exceptions)
- service-core functionality
- Version number (still 0.0.1-SNAPSHOT until this is complete)

## Questions?

If you encounter issues not covered here:
1. Read the original plan: `docs/plans/service-web-reactive-support.md`
2. Check implementation status: `docs/plans/reactive-support-implementation-status.md`
3. Review Spring Boot autoconfiguration docs
4. Check existing servlet tests for patterns to follow

Good luck! 🚀
