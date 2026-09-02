# Java Testing Convergence Plan

Converge the five Java repositories on the testing conventions already defined in
`docs/testing-patterns.md`. This plan implements five audit findings: correct test class suffixes,
replace the remaining JUnit assertions with AssertJ, normalize legacy test method names, replace
brittle message-text assertions with stable contracts, and enforce the naming convention through
the centralized Checkstyle configuration. All phases deliberately execute with
`/workspace/service-common` as their workspace and address consumer repositories through relative
paths, as requested.

The repositories in scope are `service-common`, `currency-service`, `permission-service`,
`session-gateway`, and `transaction-service`. H2 usage, coverage thresholds, test source-set
structure, and service-common dependency-version upgrades are not part of this plan.

## Phase 1: Correct Test Class Classification Names

### Workspace

.

### Goal

Make executable test class names consistently communicate whether a test is a unit test or an
integration test without changing test behavior.

### Scope

Rename the twelve known class/file inconsistencies across service-common and its consumer
repositories. Use `*Test` for plain unit tests and `*IntegrationTest` for tests that load Spring,
start Testcontainers, exercise a real database migration, or run an application HTTP client
against WireMock.

### Non-goals

Do not move tests, introduce a separate integration-test source set, change test setup, alter
assertions, or rename test methods in this phase.

### Required context

Read `AGENTS.md`, `docs/testing-patterns.md`, and the `AGENTS.md` at each consumer repository root
before editing that consumer. Preserve every repository's existing uncommitted work. The approved
rename map is:

- `CurrencyServiceApplicationTests` to `CurrencyServiceApplicationIntegrationTest`.
- `CurrencySeriesControllerTest` to `CurrencySeriesControllerIntegrationTest`.
- `ExchangeRateControllerTest` to `ExchangeRateControllerIntegrationTest`.
- `ExchangeRateImportCacheTest` to `ExchangeRateImportCacheIntegrationTest`.
- `InternalPermissionControllerTest` to `InternalPermissionControllerIntegrationTest`.
- `UserControllerTest` to `UserControllerIntegrationTest`.
- `SessionGatewayClientTest` to `SessionGatewayClientIntegrationTest`.
- `ExceptionHandlingIntegrationIntegrationTest` to `ExceptionHandlingIntegrationTest`.
- `HttpLoggingIntegrationIntegrationTest` to `HttpLoggingIntegrationTest`.
- `PermissionServiceClientTest` to `PermissionServiceClientIntegrationTest`.
- `TransactionServiceApplicationTests` to `TransactionServiceApplicationIntegrationTest`.
- `SavedViewSchemaMigrationTest` to `SavedViewSchemaMigrationIntegrationTest`.

### Execution steps

1. Verify each listed class still exists and still has the integration characteristics described
   above; stop and report material semantic drift rather than blindly renaming a changed test.
2. Rename each Java file and its outer class declaration, using patches for content edits and a
   filesystem rename for the path.
3. Search all five repositories for references to the old class names, including Gradle `--tests`
   filters, documentation, CI configuration, and Javadoc, and update valid references.
4. Run Spotless in each affected repository from this workspace with `./gradlew -p PATH
   spotlessApply`, using `.` for service-common and `../REPOSITORY` for consumers.
5. Run the renamed test classes with focused Gradle `--tests` filters from this workspace.

### Implementation notes

Test class renames are behavior-neutral. Do not use the rename as an opportunity to refactor test
fixtures or convert testing styles. WireMock-backed application client tests count as integration
tests because they exercise a real HTTP boundary even when they construct the client without a
Spring context.

### Validation

Use `rg` to prove that none of the old class names remain. Run every renamed class through the
appropriate project test task, for example `./gradlew -p ../currency-service test --tests
'org.budgetanalyzer.currency.api.CurrencySeriesControllerIntegrationTest'`. Confirm the generated
JUnit XML uses the new class names and no test was lost because of the file rename.

### Completion criteria

All twelve files and outer classes use the approved names, old-name references are absent, and all
twelve renamed test classes pass from the service-common working directory.

## Phase 2: Standardize Remaining Assertions on AssertJ

### Workspace

.

### Goal

Remove the remaining direct JUnit assertion API usage so application assertions consistently use
AssertJ.

### Scope

Update `EventListenerIntegrationTest`, `MessageConsumerIntegrationTest`,
`RedirectUrlValidatorTest`, and `BatchValidationExceptionTest` in their respective consumer
repositories.

### Non-goals

Do not rewrite MockMvc result matchers, WireMock verification, Reactor `StepVerifier`, exception
message contracts, or unrelated assertions. Hamcrest matchers embedded in MockMvc `jsonPath`
expectations remain allowed.

### Required context

Read the affected consumer `AGENTS.md` files and the assertion guidance in
`docs/testing-patterns.md`. Phase 1 must be complete so focused filters use any renamed class
names. JUnit annotations and lifecycle APIs remain in use; only imports and calls from
`org.junit.jupiter.api.Assertions` are being replaced.

### Execution steps

1. Replace `assertEquals`, `assertTrue`, `assertFalse`, and `assertInstanceOf` imports and calls
   with equivalent AssertJ assertions.
2. Preserve useful assertion descriptions by translating JUnit message arguments to AssertJ
   `.as(...)` descriptions.
3. Keep assertion strength equal to or stronger than the existing checks, including exact counts,
   types, collection contents, and boolean results.
4. Search all five repositories for remaining static imports from
   `org.junit.jupiter.api.Assertions` and inspect every match.
5. Apply Spotless and run the four focused test classes from the service-common workspace.

### Implementation notes

Use `assertThat(actual).isEqualTo(expected)`, `assertThat(condition).isTrue()` or `.isFalse()`, and
`assertThat(value).isInstanceOf(Type.class)` as appropriate. Do not change message assertions in
`BatchValidationExceptionTest` in this phase; the later message-contract phases replace them with
stable assertions after the assertion framework has been standardized.

### Validation

Run:

- `rg -n 'import static org\.junit\.jupiter\.api\.Assertions\.' ../currency-service
  ../permission-service . ../session-gateway ../transaction-service -g '**/src/test/**/*.java'`
- Focused tests for the two currency messaging classes, `RedirectUrlValidatorTest`, and
  `BatchValidationExceptionTest` using `./gradlew -p REPOSITORY test --tests CLASS`.

The search must return no application test imports from JUnit Assertions.

### Completion criteria

The four classes use AssertJ exclusively for direct assertions, retain their original behavioral
coverage, pass focused tests, and the workspace-wide JUnit Assertions import search is empty.

## Phase 3: Normalize Service-Common Test Method Names

### Workspace

.

### Goal

Bring service-common's legacy test method names into the documented lower-camel-case convention.

### Scope

Rename test methods containing underscores or beginning with a `test` prefix in the seven affected
service-common test files. Preserve already-compliant direct and `should...` names.

### Non-goals

Do not alter test bodies, assertions, `@DisplayName` annotations, nested test organization,
production methods, or test class names.

### Required context

Read `AGENTS.md` and `docs/testing-patterns.md`, especially the rule allowing either
`shouldBehaviorWhenCondition` or clear direct camelCase. The audit identified affected files under
`service-core` logging tests and `service-web` security tests, but discovery commands are the
source of truth.

### Execution steps

1. Discover current violations with searches for test declarations whose method name contains `_`
   or starts with `test`.
2. Rename each violating method to a clear lowerCamelCase behavioral name, removing redundant
   `test` prefixes and expressing the condition and expected outcome.
3. Keep style consistent within each affected class; do not force compliant direct-style classes
   to adopt `should...`.
4. Search documentation, build scripts, and test filters for references to renamed methods and
   update valid selectors.
5. Apply Spotless and run the complete `service-core` and `service-web` test tasks.

### Implementation notes

This is a mechanical symbol-only change, but names should remain readable. For example,
`testMask_withNull` should become a behavioral name such as `shouldReturnNullWhenMaskingNull`, not
merely `testMaskWithNull`. Do not remove `@DisplayName` in this phase because that would mix a
separate presentation cleanup into the naming checkpoint.

### Validation

Run a source search that identifies JUnit `@Test`, `@ParameterizedTest`, and `@RepeatedTest`
methods, and verify none of their declared names contains `_` or begins with `test` followed by an
uppercase character or underscore. Then run `./gradlew spotlessCheck :service-core:test
:service-web:test`.

### Completion criteria

All service-common test method names satisfy the documented convention, method-selector references
are current, formatting passes, and both library modules' tests pass.

## Phase 4: Normalize Session-Gateway Test Method Names

### Workspace

.

### Goal

Bring session-gateway's legacy test method names into the documented lower-camel-case convention.

### Scope

Rename test methods containing underscores or beginning with a `test` prefix in the currently
affected session-gateway tests, including validator, OAuth callback, client, controller, startup,
and application integration coverage.

### Non-goals

Do not change reactive behavior, WireMock setup, Redis Testcontainers setup, assertion semantics,
or test class organization.

### Required context

Read `../session-gateway/AGENTS.md` and `docs/testing-patterns.md`. Phase 1 may have renamed
`PermissionServiceClientTest`; use the current filename and class name discovered from source.

### Execution steps

1. Discover all current session-gateway test method names containing `_` or starting with `test`.
2. Rename each violation to clear lowerCamelCase while retaining the behavior and condition in the
   name.
3. Preserve compliant direct-style method names; a `should...` prefix is recommended but not
   mandatory.
4. Update any Gradle filters, documentation, or scripts that reference renamed methods.
5. Apply Spotless and run the complete session-gateway test task from service-common.

### Implementation notes

Keep reactive tests reactive and retain `StepVerifier` where it is already used. Names such as
`fetchPermissions_returnsParsedResponse` should become either
`shouldReturnParsedResponseWhenFetchingPermissions` or an equally clear direct camelCase name.

### Validation

Run the workspace naming search against `../session-gateway/src/test/java`, then run
`./gradlew -p ../session-gateway spotlessCheck test`. Docker must be available for its Redis-backed
integration tests.

### Completion criteria

Session-gateway has no underscore or `test`-prefix test method violations, all selector references
are current, formatting passes, and its complete test task passes.

## Phase 5: Normalize Transaction-Service Test Method Names

### Workspace

.

### Goal

Bring transaction-service's legacy test method names into the documented lower-camel-case
convention.

### Scope

Rename the underscore-style methods in the currently affected repository, service, extractor,
parser, matcher, token, import, and integration test classes.

### Non-goals

Do not rewrite extractor fixtures, alter PDF/CSV inputs, change persistence setup, modify exception
contracts, or convert already-compliant direct-style names to `should...` solely for uniformity.

### Required context

Read `../transaction-service/AGENTS.md` and `docs/testing-patterns.md`. This is the largest naming
batch, with roughly 141 audited violations across thirteen files, so rediscover the exact current
set rather than relying on the audit count.

### Execution steps

1. Discover all current transaction-service test method names containing `_` or starting with
   `test`.
2. Rename violations in coherent class-sized batches, preserving behavior and making each name
   state the operation, condition, and expected result.
3. After each class-sized batch, inspect the diff to ensure only method declarations and genuine
   selector references changed.
4. Update any build, documentation, or script references to renamed methods.
5. Apply Spotless and run the complete transaction-service test task from service-common.

### Implementation notes

Many names already contain good behavioral information separated by underscores. Preserve that
information in lowerCamelCase rather than rewriting test intent. Avoid bulk text transforms that
produce awkward capitalization or accidentally rename production helpers.

### Validation

Run the workspace naming search against `../transaction-service/src/test/java`, then run
`./gradlew -p ../transaction-service spotlessCheck test`. Docker must be available for PostgreSQL
Testcontainers tests, and the PDF/CSV fixture tests must remain unchanged except for method names.

### Completion criteria

Transaction-service has no underscore or `test`-prefix test method violations, all selector
references are current, formatting passes, and its complete test task passes.

## Phase 6: Replace Service-Common Model and Exception Message Assertions

### Workspace

.

### Goal

Remove brittle message wording checks from service-common's core utilities, API models, security
types, and exception classes while retaining meaningful coverage of stable behavior.

### Scope

Audit and update service-common tests that assert exception messages, causes' messages, API model
message values, or validation message wording. This includes the currently identified
`service-core` CSV and soft-delete tests and `service-web` API model, security token, and exception
tests. Discovery is authoritative because the earlier quick audit did not include every direct
`getMessage()` assertion.

### Non-goals

Do not change production message wording, remove message fields from public response models,
introduce a new `FieldError` API field, or modify servlet/reactive handler tests in this phase.

### Required context

Read `AGENTS.md`, the stable-contract guidance in `docs/testing-patterns.md`, and each affected
production type. Stable assertions include exception type, `BusinessException.getCode()`, field
name/index/rejected value, cause type or identity, immutable collection behavior, and other
structured state. Human-readable message content and cause message content are unstable.

### Execution steps

1. Inventory affected service-common tests using searches for AssertJ `.hasMessage*`, assertions
   on `getMessage()` or a cause's message, and assertions on API/field-error message accessors.
2. Classify each assertion as brittle wording, stable structured behavior, security behavior, or
   simple Java exception/message plumbing before changing it.
3. Replace wording checks with stable type, code, field/index/rejected-value, cause identity/type,
   or collection assertions. Keep security checks by asserting that serialized/logged output does
   not expose a supplied secret rather than expecting a replacement sentence.
4. Remove or consolidate a test only when its sole purpose is proving that Java's
   `RuntimeException` stores the constructor message and the same constructor remains exercised by
   meaningful tests; do not reduce JaCoCo below the enforced module gates.
5. Apply Spotless and run the affected `service-core` and `service-web` focused tests, followed by
   both complete module test tasks.

### Implementation notes

Do not replace one literal message with a different literal or with `.isNotNull()` merely to keep
an assertion. `FieldError.message` and `ApiErrorResponse.message` remain valid response data, but
their wording is not a programmatic discriminator. Tests of builders, copying, equality, or JSON
shape should focus on stable fields and may use an arbitrary message only as setup data without
asserting its content.

### Validation

Repeat the message-assertion searches over `service-core/src/test/java` and
`service-web/src/test/java`, inspecting any remaining match in context. Non-wording properties
such as an intentional maximum message length may remain only when documented as a safety
requirement. Run `./gradlew spotlessCheck :service-core:test :service-web:test
:service-core:jacocoTestCoverageVerification :service-web:jacocoTestCoverageVerification`.

### Completion criteria

Service-common model, security, utility, and exception tests no longer depend on human-readable
message wording; meaningful structured and safety coverage remains; and both modules pass their
tests and JaCoCo gates.

## Phase 7: Replace Service-Common Handler and HTTP Message Assertions

### Workspace

.

### Goal

Make servlet, reactive, resolver, and HTTP integration tests assert stable error contracts instead
of human-readable response messages.

### Scope

Update service-common handler and integration tests that inspect `ApiErrorResponse.message`,
`FieldError.message`, `$.message`, or `$.fieldErrors[].message`, including servlet and reactive
exception handlers, shared error resolution, security responses, and full HTTP error handling.

### Non-goals

Do not change production handler behavior, HTTP status mapping, error wording, OpenAPI schemas, or
the existence of message fields in responses.

### Required context

Phase 1 has corrected the duplicated integration-test class names, and Phase 6 has cleaned the
underlying model/exception tests. Read the stable API contract list in `docs/testing-patterns.md`:
HTTP status, error type, error code, and field error field/code are stable; descriptive message
text is not. Where the current `FieldError` model has no code, assert its stable field/index and
the containing error code without introducing a new API in this plan.

### Execution steps

1. Discover all service-web handler and HTTP tests that assert response or field-error message
   content, including direct Java accessors, MockMvc JSON paths, and WebTestClient JSON paths.
2. Replace each wording assertion with the strongest available status, type, code, field,
   index, rejected-value, body-shape, or cause classification assertion.
3. Preserve generic-error redaction coverage by supplying a recognizable internal secret and
   proving the serialized response does not contain it, instead of expecting the current generic
   sentence.
4. Remove message-presence checks such as `isString()` or `isNotNull()` when they do not establish
   a stable behavior; do not substitute weak assertions.
5. Apply Spotless, run the focused servlet/reactive handler and integration classes, and then run
   the complete `service-web` test and coverage verification tasks.

### Implementation notes

Tests may continue putting realistic messages into exceptions as input. The forbidden coupling is
using those strings as the expected programmatic result. Authentication, authorization,
validation, not-found, business, client, service, and generic failures should each retain a stable
mapping assertion even after wording checks are removed.

### Validation

Search service-web tests for `.hasMessage*`, assertions whose subject is an exception/API
`getMessage()`, and `$.message` or field-message JSON-path expectations. Inspect any match that
remains. Run `./gradlew spotlessCheck :service-web:test
:service-web:jacocoTestCoverageVerification`.

### Completion criteria

Servlet, reactive, resolver, security, and HTTP integration tests cover stable status/type/code
and field contracts without depending on descriptive message wording, redaction remains tested,
and service-web passes its test and coverage gates.

## Phase 8: Replace Currency-Service Message Assertions

### Workspace

.

### Goal

Remove message wording coupling from currency-service client, provider, and service integration
tests without weakening external-boundary or business-error coverage.

### Scope

Update the currently affected `FredClientIntegrationTest`,
`FredExchangeRateProviderIntegrationTest`, `ExchangeRateImportServiceIntegrationTest`,
`CurrencyServiceIntegrationTest`, and `ExchangeRateServiceIntegrationTest`, plus any additional
matches found by discovery.

### Non-goals

Do not change FRED client/provider production behavior, exception messages, retry policies,
business rules, database behavior, or API contracts.

### Required context

Read `../currency-service/AGENTS.md`, its error enums, and the external HTTP guidance in
`docs/testing-patterns.md`. Currency business failures generally expose stable
`CurrencyServiceError` codes. `ClientException`, `ResourceNotFoundException`, and
`ServiceUnavailableException` may only offer a stable type/cause, so the arranged WireMock
response, request verification, elapsed-time bound, or surrounding observable state must carry the
rest of the scenario.

### Execution steps

1. Inventory `.hasMessage*` and direct `getMessage()` assertions in currency tests, including
   timeout classification and oversized upstream error cases.
2. For `BusinessException`, assert the exact stable error code in addition to the exception type.
   For not-found, client, and availability failures, assert type plus any stable cause,
   WireMock request, retry count, timing bound, rollback, cache, or persistence outcome already
   relevant to the scenario.
3. Remove dependencies on upstream reason phrases, status numbers embedded in messages, entity IDs
   embedded in messages, and JDK-generated duplicate-key wording.
4. Preserve the oversized-error truncation bound and timeout-duration checks because they test
   safety/performance properties rather than wording; rewrite any nearby comments that imply the
   message sentence itself is the contract.
5. Apply Spotless and run the five affected integration classes, followed by the complete currency
   test and coverage verification tasks.

### Implementation notes

Do not add new production exception subtypes or codes solely to keep every old assertion count.
Each WireMock test already establishes its input scenario; a precise exception type and observable
client behavior can be sufficient. Conversely, do not delete rollback, retry, cache, or request
verification just because the message assertion is removed.

### Validation

Search all currency test sources for literal/fragment exception message checks and inspect direct
`getMessage()` uses. Run `./gradlew -p ../currency-service spotlessCheck test
jacocoTestCoverageVerification` from service-common with Docker available.

### Completion criteria

Currency-service tests no longer depend on exception wording or upstream reason phrases, stable
business codes and external-boundary behavior remain covered, and the complete test and coverage
tasks pass.

## Phase 9: Replace Session and Transaction Message Assertions

### Workspace

.

### Goal

Remove the remaining message wording coupling from session-gateway and transaction-service tests.

### Scope

Update `IdpReactiveJwtDecoderFactoryTest`, `BatchValidationExceptionTest`,
`PdfTextTableParserConfigValidatorTest`, and `PdfTextExtractionServiceTest`, plus additional
matches found after Phase 2 converts JUnit assertions to AssertJ.

### Non-goals

Do not change OAuth2 behavior, parser/extractor production messages, PDF fixtures, batch validation
semantics, or shared error model APIs.

### Required context

Read both consumer `AGENTS.md` files and their stable error types. The OAuth2 failure exposes a
stable `OAuth2Error.errorCode`. Transaction business failures expose `BudgetAnalyzerError` codes
and structured field errors; field-error wording and aggregate exception wording are not stable.

### Execution steps

1. Rediscover message assertions in session-gateway and transaction-service after earlier phases.
2. Replace the missing-JWK message fragment check with the stable OAuth2 error code.
3. Replace transaction exception wording checks with `BudgetAnalyzerError` codes, exception type,
   field/index/rejected-value structure, and cause type where applicable.
4. Ensure the batch validation tests still prove error count, indexes, fields, and immutable
   structured errors without asserting aggregate or field message wording.
5. Apply Spotless and run the affected focused classes, then run both repositories' complete test
   and coverage verification tasks from service-common.

### Implementation notes

The PDF blank/unsupported-file scenarios should both retain the stable
`PDF_PARSING_ERROR` code. The parser configuration failure should retain
`STATEMENT_FORMAT_VALIDATION_FAILED` and its structured field errors. Phase 2 may have renamed
assertion calls but must not have removed the message assertions before this phase evaluates their
stable replacements.

### Validation

Repeat the message-assertion searches over both consumer test trees. Run
`./gradlew -p ../session-gateway spotlessCheck test jacocoTestCoverageVerification` and
`./gradlew -p ../transaction-service spotlessCheck test jacocoTestCoverageVerification` with
Docker available where required.

### Completion criteria

Session-gateway and transaction-service tests use stable OAuth2/business codes and structured
fields instead of descriptive wording, and both complete test and coverage tasks pass.

## Phase 10: Enforce Method Naming Through Shared Checkstyle

### Workspace

.

### Goal

Prevent the cleaned test naming conventions from drifting by enforcing lowerCamelCase through the
shared Checkstyle configuration, then prove the five repositories remain green as a coordinated
workspace.

### Scope

Update `/workspace/checkstyle-config/checkstyle.xml`, which all five builds already consume, so the
existing `MethodName` check enforces lowerCamelCase without underscores and rejects the redundant
`test` method prefix. Remove the JUnit-specific suppression that currently permits underscore
components. Update the canonical testing and code-quality documentation, validate the pending
shared rule locally against all five repositories, and run every repository's required clean build
from service-common.

### Non-goals

Do not add repository-local naming tasks, inline Checkstyle configurations, or duplicated naming
regular expressions. Do not add a new testing framework, split unit and integration source sets,
standardize coverage gates, or remove H2. Do not change production behavior while tightening the
naming rule.

### Required context

Read `AGENTS.md`, `docs/testing-patterns.md`, `docs/code-quality-standards.md`, all four consumer
`AGENTS.md` files, the five build configurations, and `/workspace/checkstyle-config/checkstyle.xml`.
Phases 3 through 5 must be complete before tightening the shared rule. The current configuration
uses a Google-style `MethodName` expression that permits numeric underscore suffixes and suppresses
violations for JUnit-annotated methods when their names contain underscore-separated components.
That exception is valid Java and JUnit style, but Budget Analyzer deliberately standardizes on
lowerCamelCase because it is already the dominant workspace convention. All five builds load the
configuration from `budgetanalyzer/checkstyle-config` on `main`, so validate the local pending
configuration before publishing it and publish the shared rule only after the naming-cleanup
changes are ready across the affected repositories.

### Execution steps

1. Inspect `/workspace/checkstyle-config` status and preserve any existing uncommitted work. Update
   its `MethodName` format so all methods use strict lowerCamelCase without underscore suffixes and
   names beginning with the redundant `test` prefix are rejected. Use
   `^(?!test(?:[A-Z0-9_]|$))(?![a-z]$)(?![a-z][A-Z])[a-z][a-z0-9]*(?:[A-Z][a-z0-9]*)*$`
   as the single method-name expression. Remove the JUnit-specific
   `SuppressionXpathSingleFilter` for `MethodName`; do not add a replacement exception for test
   methods.
2. Validate the pending local Checkstyle configuration through the existing Checkstyle tasks in
   all five builds, using a temporary Gradle init script or equivalent non-committed override that
   points those tasks at `/workspace/checkstyle-config/checkstyle.xml`. Do not change the checked-in
   consumer build files, which must continue consuming the centralized remote configuration.
3. Prove the rule detects both a temporary underscore-named test and a temporary
   `testBehaviorWhenCondition` method, then remove the probes completely. Prove compliant direct
   camelCase and `should...` methods pass, and confirm no temporary source or init-script file
   remains in any repository.
4. Update the naming section of `docs/testing-patterns.md` to state that lowerCamelCase without
   underscores is a deliberate Budget Analyzer convention enforced by Checkstyle, not a Java or
   JUnit restriction. Retain both clear direct camelCase and `should...` as allowed behavioral
   styles, and keep `@DisplayName` limited to cases where the identifier alone is insufficient.
   Update `docs/code-quality-standards.md` so its Checkstyle naming guidance describes the same
   centrally enforced rule.
5. From `/workspace/service-common`, run `clean spotlessApply` and then `clean build` for
   service-common and each consumer using `./gradlew -p PATH`; run the two commands separately for
   every repository.
6. Repeat the workspace-wide searches for old class names, JUnit Assertions imports, forbidden
   test method names, brittle message-text assertions, Mockito imports/bean overrides, and
   `@Disabled` tests, and record the final zero-result checks or reviewed safety-only exceptions in
   the execution summary.

### Implementation notes

Use the existing central `MethodName` rule rather than introducing a test-source parser or a second
source of naming policy. The rule applies to production, test, lifecycle, and helper methods; all
must use lowerCamelCase without underscores. The `test` prefix prohibition applies globally as the
simplest unambiguous mechanical rule; names such as `verifyConnection` or
`shouldConnectWhenAvailable` communicate intent more precisely than `testConnection` in any source
set. Keep behavioral naming guidance in `docs/testing-patterns.md` rather than duplicating it across
consumer documentation.

The shared configuration is consumed from its remote `main` branch, so rollout order matters:
merge or otherwise make the method-renaming changes available in the affected repositories before
publishing the stricter Checkstyle configuration. The local override validation must pass before
the central change is published. After publication, rerun at least one isolated build without the
override to prove the normal remote configuration path enforces the rule.

### Validation

Run these command pairs from `/workspace/service-common`, one repository at a time:

- `./gradlew clean spotlessApply`, then `./gradlew clean build`.
- `./gradlew -p ../currency-service clean spotlessApply`, then `./gradlew -p
  ../currency-service clean build`.
- `./gradlew -p ../permission-service clean spotlessApply`, then `./gradlew -p
  ../permission-service clean build`.
- `./gradlew -p ../session-gateway clean spotlessApply`, then `./gradlew -p
  ../session-gateway clean build`.
- `./gradlew -p ../transaction-service clean spotlessApply`, then `./gradlew -p
  ../transaction-service clean build`.

Before publishing the shared rule, run each repository's `checkstyleTest` task against the pending
local configuration and confirm the negative probes fail for the expected `MethodName` violation.
After publication, confirm each normal `check` task receives the updated central configuration,
each JaCoCo verification still runs, all JUnit XML is generated, and no build relies on another
repository being present beyond its normal published service-common dependency.

### Completion criteria

The centralized Checkstyle configuration contains one strict lowerCamelCase method rule with no
JUnit exception, both negative naming probes are rejected, and all five standalone builds consume
and pass the same published rule through their normal `check` lifecycle. The twelve class names,
four assertion files, all legacy method names, and brittle message-text assertions are remediated;
the canonical testing and code-quality documents describe the enforced convention; and the final
audit searches show no regression in the existing no-Mockito, no-disabled-test policy.
