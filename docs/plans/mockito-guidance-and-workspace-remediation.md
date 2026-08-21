# Mockito Guidance and Workspace Test Remediation Plan

Establish one direct testing rule across the Budget Analyzer Java workspace, remove contradictory
guidance, and migrate existing tests that mock or spy application-owned Spring beans. The agreed
rule is:

> Do not mock or spy application-owned Spring beans. Test them together using real application
> components with Testcontainers or WireMock where infrastructure or external HTTP is involved.
> In plain unit tests, use real objects or concrete test implementations for framework interfaces.

The baseline audit found Mockito imports in 33 Java test files: 16 in `transaction-service`, six in
`permission-service`, five in `service-common`, four in `session-gateway`, and two in
`currency-service`. Twenty-seven files mock or spy application-owned components. The five
`service-common` files mock framework HTTP/filter interfaces, and one `permission-service` file
mocks Spring's fluent HTTP client. Re-run local discovery in every phase because this inventory is
migration context, not a permanent source of truth.

`MockMvc`, Spring `Mock*` request/response implementations, WireMock, Testcontainers, and frontend
`vi.mock` usage are not Mockito violations by name. A `MockMvc` test is acceptable when it loads the
real application components needed by the endpoint; the prohibited pattern is replacing those
components with Mockito beans.

## Phase 1: Make Service-Common Testing Guidance Canonical

### Workspace

.

### Goal

Publish the agreed rule as the single unambiguous testing policy for Java services.

### Scope

Update `docs/testing-patterns.md`, the testing summary in `AGENTS.md`, and contradictory statements
in `docs/spring-boot-conventions.md`, `docs/advanced-patterns.md`, and `docs/common-patterns.md`.

### Non-goals

Do not change tests, dependencies, production code, coverage gates, or consumer repositories in
this phase.

### Required context

Read the affected documentation sections, `docs/code-quality-standards.md`,
`docs/versioning-and-compatibility.md`, and
`../orchestration/docs/agents-md-checkstyle.md`. Preserve unrelated instructions and the existing
user change in `AGENTS.md`.

### Execution steps

1. Replace the current mixture of a blanket `@MockBean` prohibition and Mockito-positive examples
   with the agreed application-bean rule, including a short distinction between `MockMvc` and
   Mockito bean replacement.
2. Define the preferred layers plainly: pure logic with real objects; application behavior with
   real Spring beans; persistence and brokers with Testcontainers; external HTTP with WireMock;
   framework callbacks with concrete test implementations.
3. Rewrite the controller, service, exception, and external-boundary examples so every recommended
   example follows the rule. Keep intentionally bad examples only when they are clearly labeled and
   cannot be mistaken for guidance.
4. Remove or rewrite claims that services are tested with mocked repositories, provider interfaces
   should be mocked, unit tests use mocks for dependencies, or Mockito is the standard default.
5. Add durable discovery commands to the testing documentation for finding Mockito imports and
   Spring Mockito bean overrides without maintaining a file inventory.
6. Update `AGENTS.md` to state the short rule and point agents to `docs/testing-patterns.md` before
   writing or modifying tests.

### Implementation notes

Do not describe this as a wholesale dependency ban. The rule targets application-owned Spring
beans and interaction-heavy tests. Prefer "test implementation" or "fake" for deterministic
framework collaborators, and reserve "mock" for Mockito behavior so agents do not confuse Spring
test request objects or WireMock with prohibited usage.

### Validation

Run focused searches for `Mockito`, `@MockBean`, `@MockitoBean`, `mocked repositories`, and `Uses
mocks` across `AGENTS.md` and `docs/`. Read every remaining match in context and confirm it is either
the rule, a clearly labeled anti-example, historical plan context, or external-link terminology.
Verify all changed relative links resolve and inspect the Markdown diff.

### Completion criteria

One concise rule is stated consistently, all recommended examples obey it, the allowed integration
tools are explicit, and no service-common guidance recommends mocking application-owned beans.

## Phase 2: Replace Mockito in Service-Common Framework Tests

### Workspace

.

### Goal

Demonstrate the guidance in service-common by testing its servlet and reactive filters with Spring
test objects, lambdas, or small concrete test implementations instead of Mockito.

### Scope

Cover the five Mockito-using tests under `service-web/src/test/java`: servlet
`CorrelationIdFilterTest`, `HttpLoggingFilterTest`, and `ContentLoggingUtilTest`, plus reactive
`ReactiveCorrelationIdFilterTest` and `ReactiveHttpLoggingFilterTest`. Remove the unused explicit
`mockito-core` version-catalog alias if discovery confirms it has no consumer.

### Non-goals

Do not remove Mockito transitively from `spring-boot-starter-test`, change production filter
behavior, weaken assertions, or rewrite unrelated service-core tests.

### Required context

Read the final `docs/testing-patterns.md`, all five tests, their production classes, and existing
Spring servlet/reactive test utilities on the test classpath.

### Execution steps

1. Replace servlet request, response, wrapper, and chain mocks with `MockHttpServletRequest`,
   `MockHttpServletResponse`, `MockFilterChain`, real content-caching wrappers, or minimal recording
   implementations where the Spring objects do not expose the required observation.
2. Replace reactive `WebFilterChain` mocks with lambdas or recording implementations and continue
   using `MockServerHttpRequest` and `MockServerWebExchange`.
3. Preserve success, error propagation, body capture, redaction, correlation ID, MDC/Reactor
   context, and cleanup coverage through observable state rather than call verification.
4. Remove obsolete Mockito imports/extensions and delete the unused catalog alias only after a
   repository-wide reference search.
5. Update the nearest testing documentation if implementation exposes a missing concrete example
   or qualification in the new rule.

### Implementation notes

Small test-only implementations should model only the framework interface contract needed by the
test. Do not recreate Mockito as a generic in-house mocking utility.

### Validation

Run the five focused test classes, then run `./gradlew clean spotlessApply` followed by
`./gradlew clean build`. Confirm `rg -l 'org\.mockito|override\.mockito' service-*/src/test/java`
returns no files and review the coverage report for regressions.

### Completion criteria

Service-common contains no Mockito-using Java tests, filter behavior remains covered through
observable results, formatting and the full build pass, and documentation matches the examples.

## Phase 3: Establish Transaction API Integration Coverage

### Workspace

../transaction-service

### Goal

Align transaction-service guidance and replace mocked authorization controller slices with real
application integration tests.

### Scope

Update the testing section of `AGENTS.md`; reuse or extend the full-context setup demonstrated by
`TransactionOpenApiIntegrationTest`; migrate `SavedViewControllerAuthorizationTest`,
`StatementFormatControllerAuthorizationTest`, and `TransactionControllerAuthorizationTest`.

### Non-goals

Do not migrate the two functional controller suites or service tests in this phase, and do not
change endpoint authorization or response contracts.

### Required context

Read the repository `AGENTS.md`, the final service-common testing guidance, the three authorization
tests, security test support, existing integration tests, migrations, and test data builders.

### Execution steps

1. Replace the Mockito-positive local instruction with the canonical short rule and consultation
   trigger while preserving repository-specific test commands and constraints.
2. Create or consolidate a reusable full-context controller integration setup using real security,
   services, repositories, PostgreSQL, and `MockMvc`; avoid duplicating container lifecycle code
   when an existing fixture can own it.
3. Migrate the three authorization suites to seed the minimum real state required for each route and
   assert allowed and denied HTTP behavior without `@MockitoBean`.
4. Retain stable API assertions and remove interaction verification that only checks service method
   calls.
5. Document any new reusable test fixture in the nearest testing guidance without adding a static
   inventory to `AGENTS.md`.

### Implementation notes

Use `MockMvc` as an HTTP driver, not as a reason to load a web slice. Keep authorization tests
focused: they need enough valid persisted state to reach the authorization decision, not redundant
business-behavior coverage.

### Validation

Run the migrated authorization suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Confirm those files contain no Mockito imports or bean overrides.

### Completion criteria

All three authorization suites exercise real application beans and PostgreSQL, local guidance
matches service-common, and the full transaction-service build passes.

## Phase 4: Migrate Transaction Functional Controller Tests

### Workspace

../transaction-service

### Goal

Replace the remaining mocked controller slices with endpoint integration coverage, repair the
self-scoped transaction count behavior exposed by real persistence, and make the controller
integration fixture enforce the production pagination contract.

### Scope

Migrate `TransactionControllerTest` and `StatementFormatControllerTest`, reusing the integration
foundation from Phase 3. Permit the smallest transaction-service production change needed for
`GET /v1/transactions/count` to ignore a caller-supplied `ownerId` and count the authenticated
owner's matching active transactions. Extend the full-context test configuration so
`GET /v1/transactions/search` uses the production default page size of 50 and maximum page size of
100.

### Non-goals

Do not duplicate tests already proven by authorization, service, repository, or OpenAPI suites; do
not change the documented API contract beyond correcting implementation that contradicts it. Do
not make `ownerId` effective on self-scoped endpoints, change `ownerId` behavior on the cross-user
search or count endpoints, add a controller-side pagination cap when Spring's configured resolver
owns that rule, or make any other public API behavior change to make tests pass.

### Required context

Read both controller suites, their controllers and request/response types, the Phase 3 fixture,
existing service integration tests, and the stable error-response rules. For the two exposed
contract gaps, also read `TransactionService`, the internal transaction criteria conversion,
`src/main/resources/application.yml`, `src/test/resources/application.yml`, the transaction API
documentation, and the repository's authorization and ownership instructions. Before modifying
Java or tests, read the shared code-quality and testing standards required by the transaction
repository instructions.

### Execution steps

1. Classify each existing test as a stable endpoint contract, duplicated service behavior, or
   interaction-only assertion. Distinguish production behavior defects from differences caused by
   the full-context test configuration.
2. Preserve stable endpoint contracts using full-context `MockMvc`, real services, real
   repositories, and transaction-safe test data cleanup. Configure the shared controller
   integration context with the production pagination values, retain the assertion that
   `size=500` is capped to 100, and do not add redundant production pagination logic.
3. Retain a persisted-state regression case that sends another user's `ownerId` to
   `GET /v1/transactions/count`. Make the smallest service-layer criteria change required to
   replace or ignore that value while preserving every other supplied filter and enforcing the
   authenticated owner. Keep `ownerId` effective for `GET /v1/transactions/search` and
   `GET /v1/transactions/search/count`.
4. Move unique business scenarios to the appropriate service integration suite when HTTP adds no
   distinct assertion, remove duplicated interaction-only cases, and replace stubbed exception
   paths with realistic persisted state, validation input, or external boundary conditions.
5. Remove all `@MockitoBean`, captors, stubbing, and service-call verification from the two suites.
   Confirm the active API and configuration documentation still describes the resulting behavior;
   update the nearest owner document only if the implementation reveals another mismatch.

### Implementation notes

Keep the suite smaller than the mocked version when multiple tests prove the same mapping. Coverage
percentage is not a reason to retain implementation-coupled cases. The self-scoped count failure
is a production correctness defect: combining the requested owner with the authenticated owner can
create mutually exclusive predicates. It is not a known data-exposure path. The page-size failure
is test-configuration drift: `src/test/resources/application.yml` shadows the production resource
and omits its Spring Data web paging values. Correct the shared integration configuration or
fixture instead of changing the controller. Do not remove either stable contract assertion.

### Validation

Run focused tests for the authenticated-owner count regression and the configured page-size cap.
Then run both controller suites plus authorization and OpenAPI integration tests, followed by
`./gradlew clean spotlessApply` and `./gradlew clean build`. Inspect the full build output and fix
Checkstyle warnings even if Gradle exits successfully. Search the transaction API test package for
Mockito imports and bean overrides, inspect any remaining match, and confirm the self-scoped count
documentation excludes `ownerId` while cross-user search continues to expose it.

### Completion criteria

No transaction API test replaces application beans with Mockito, stable HTTP contracts remain
covered, self-scoped count ignores a supplied owner filter while applying all other filters to the
authenticated owner, cross-user owner filtering is unchanged, requests for `size=500` resolve to a
page size of 100 in the full-context suite without redundant controller logic, documentation
matches behavior, and the complete build passes.

## Phase 5: Consolidate Transaction and Import Service Tests

### Workspace

../transaction-service

### Goal

Move unique core transaction/import behavior from repository-mocked unit tests into real-component
integration coverage.

### Scope

Cover `TransactionServiceTest`, `TransactionImportServiceTest`,
`TransactionDuplicateMatcherTest`, and `FileImportTrackingServiceTest`, consolidating with existing
`TransactionServiceIntegrationTest` and `TransactionImportServiceIntegrationTest` where possible.

### Non-goals

Do not preserve tests whose only assertion is a repository method call, alter production behavior,
or migrate statement-format tests in this phase.

### Required context

Read the four Mockito suites, existing integration counterparts, production services,
repositories/specifications, migrations, fixtures, and external-boundary configuration.

### Execution steps

1. Map unique business invariants, ownership checks, duplicate decisions, import state transitions,
   and failure outcomes to observable database or returned results.
2. Extend existing integration suites rather than creating parallel classes for the same service.
3. Use real repositories and PostgreSQL; use real extractors or boundary tools appropriate to each
   scenario instead of Mockito application collaborators.
4. Delete superseded mocked suites or reduce them to pure tests that construct only real objects.
5. Preserve coverage of realistic failures without asserting exception message wording or internal
   call counts.

### Implementation notes

Criteria API deep stubs are a priority for removal because they reproduce JPA behavior least
reliably. Exercise specifications through repository queries instead.

### Validation

Run the affected service and repository integration suites, then `./gradlew clean spotlessApply`
and `./gradlew clean build`. Confirm the four targeted files are removed or contain no Mockito.

### Completion criteria

Core transaction and import behavior is covered through real persistence and observable outcomes,
all targeted Mockito usage is gone, and the full build passes.

## Phase 6: Migrate Saved-View and Statement-Format Services

### Workspace

../transaction-service

### Goal

Replace mocked persistence in saved-view and statement-format service tests with real database
coverage.

### Scope

Cover `SavedViewServiceTest`, `StatementFormatServiceTest`,
`CsvStatementFormatWizardServiceTest`, and `PdfStatementFormatWizardServiceTest`; reuse
`SavedViewServiceIntegrationTest` and repository integration suites.

### Non-goals

Do not migrate extractor registry tests or change parsing/product behavior.

### Required context

Read the four target suites, production services, existing saved-view and statement-format
integration coverage, parser revision persistence, fixtures, and migrations.

### Execution steps

1. Preserve unique validation, ownership, revision, save, and preview behavior as observable
   service results and persisted state.
2. Consolidate duplicate saved-view coverage into the existing integration class.
3. Add focused integration coverage for statement-format persistence and wizard save paths using
   real repositories and PostgreSQL.
4. Keep analysis-only parsing tests fast by constructing real parser/extraction collaborators;
   split persistence cases into integration tests if that keeps boundaries clear.
5. Delete superseded mocks, captors, and interaction assertions.

### Implementation notes

Generated PDF/CSV fixtures are legitimate deterministic inputs. Preserve them when they test parser
behavior; only replace their mocked application collaborators.

### Validation

Run the affected service and repository suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Confirm the four target suites no longer import Mockito.

### Completion criteria

Saved-view and statement-format behavior uses real owned components, persistence paths use
PostgreSQL, parsing-only tests remain deterministic, and the full build passes.

## Phase 7: Remove Mockito from Transaction Extractor Tests

### Workspace

../transaction-service

### Goal

Finish the transaction-service audit by replacing mocked or spied extractor collaborators.

### Scope

Cover `ConfigurableCsvStatementExtractorTest`,
`ConfigurablePdfTextTableStatementExtractorTest`, and `StatementExtractorRegistryTest`.

### Non-goals

Do not remove useful pure unit tests, require containers for algorithms with no infrastructure, or
retain one-call verification as a performance proxy.

### Required context

Read the target tests, extractor implementations, real `CsvParser` implementation, PDF extraction
service, registry persistence behavior, and completed Phase 6 fixtures.

### Execution steps

1. Construct real CSV and PDF parsing collaborators for algorithmic tests and assert parsed results,
   rejection reasons, and statuses.
2. Replace the PDF extraction spy's one-call assertion with an observable result or a dedicated
   performance benchmark only if a documented performance requirement exists.
3. Exercise registry/revision selection with real repository state in an integration test.
4. Remove all remaining Mockito imports from transaction-service Java tests and inspect every
   residual text match in documentation or historical plans.
5. Update local testing documentation only if the migration reveals a reusable pattern not already
   owned by service-common.

### Implementation notes

Do not introduce hand-written mocks for application types merely to satisfy a string-based audit.
Real domain objects and parsers are the intended unit-test collaborators.

### Validation

Run the three affected suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Require `rg -l 'org\.mockito|override\.mockito' src/test/java` to return no
files.

### Completion criteria

Transaction-service has no Mockito-using Java tests, all meaningful behavior remains covered, and
the full build passes.

## Phase 8: Migrate Permission Persistence Services

### Workspace

../permission-service

### Goal

Align permission-service guidance and replace repository-mocked service tests with PostgreSQL
integration coverage.

### Scope

Update `AGENTS.md`; migrate `PermissionServiceTest`, `UserServiceTest`, and `UserSyncServiceTest`,
reusing existing repository integration infrastructure.

### Non-goals

Do not migrate controllers or `SessionGatewayClientTest` in this phase, and do not change permission
or identity semantics.

### Required context

Read local instructions, canonical testing guidance, the three service suites, repository
integration tests, migrations, seed data, transaction behavior, and session-revocation boundary.

### Execution steps

1. Replace Mockito-positive `AGENTS.md` guidance with the canonical short rule and consultation
   trigger while preserving local test commands.
2. Establish a reusable service integration fixture around PostgreSQL and existing seed data.
3. Migrate effective-permission, user lifecycle, synchronization, role, and transaction outcomes to
   real repositories and observable state.
4. Represent session revocation as an external boundary using WireMock or defer only that boundary
   to Phase 9; do not mock repositories or transaction infrastructure.
5. Remove superseded mocked suites or rename converted suites with the integration-test suffix.

### Implementation notes

Repository queries are part of permission behavior. Tests that return the same sets they stubbed
should be replaced by tests that insert roles/permissions and prove the query-to-response path.

### Validation

Run the affected service and repository suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Confirm no repository Mockito fields remain.

### Completion criteria

Permission service logic runs against real PostgreSQL, local guidance is aligned, and the full build
passes.

## Phase 9: Migrate Permission Controllers and Session-Gateway Client

### Workspace

../permission-service

### Goal

Remove the remaining permission-service Mockito usage through full endpoint tests and WireMock HTTP
boundary tests.

### Scope

Migrate `InternalPermissionControllerTest`, `UserControllerTest`, and
`SessionGatewayClientTest`.

### Non-goals

Do not call a live session-gateway, duplicate service scenarios at the controller layer, or weaken
retry/error assertions.

### Required context

Read the target tests and production classes, Phase 8 fixtures, security test support, HTTP client
configuration, and existing workspace WireMock patterns.

### Execution steps

1. Convert controller slices to full-context HTTP tests with real services and PostgreSQL, seeding
   only the data required for stable response/security contracts.
2. Replace the mocked fluent `RestClient` chain with a real client pointed at WireMock.
3. Verify outbound method/path, retryable and non-retryable statuses, connection failures where
   practical, retry exhaustion, and returned domain results through the HTTP boundary.
4. Remove interaction assertions that merely mirror controller-to-service delegation.
5. Remove all remaining Mockito imports and extensions from permission-service tests.

### Implementation notes

Use zero or minimal retry delays under test without changing production defaults. Prefer a real
HTTP response over constructing mocked Spring exception types.

### Validation

Run the three migrated suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Require `rg -l 'org\.mockito|override\.mockito' src/test/java` to return no
files.

### Completion criteria

Permission-service has no Mockito-using Java tests, endpoints use real owned beans, the outbound
client is tested through HTTP, and the full build passes.

## Phase 10: Consolidate Session-Gateway Controller Tests

### Workspace

../session-gateway

### Goal

Replace mocked logout/user controller unit tests with the repository's existing end-to-end reactive
integration coverage.

### Scope

Cover `LogoutControllerTest` and `UserControllerTest`, consolidating unique scenarios into
`LogoutControllerIntegrationTest`, `UserControllerIntegrationTest`, or the shared
`AbstractIntegrationTest` harness.

### Non-goals

Do not change redirect, cookie, Redis session, or user response behavior; do not rewrite unrelated
OAuth tests.

### Required context

Read local instructions, canonical testing guidance, both Mockito suites, their integration
counterparts, `AbstractIntegrationTest`, Redis container support, and WireMock configuration.

### Execution steps

1. Compare mocked and integration suites and identify unique stable HTTP/cookie/session contracts.
2. Add only missing scenarios to the full application tests using real session reader/writer,
   cookie helper, Redis, and WebTestClient.
3. Remove duplicated interaction assertions and delete superseded Mockito controller suites.
4. Update local testing guidance if it still recommends mocked controller collaborators.

### Implementation notes

Prefer externally observable status, redirect, cookie, response body, and Redis state. Do not assert
the order in which controller collaborators were invoked.

### Validation

Run the logout and user integration suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Confirm the API test package contains no Mockito imports.

### Completion criteria

Logout and user behavior is covered through real reactive integration paths, redundant mocked
suites are removed, and the full build passes.

## Phase 11: Consolidate Session-Gateway Security Tests

### Workspace

../session-gateway

### Goal

Remove application-component mocks from security configuration and Redis security-context tests.

### Scope

Cover `SecurityConfigTest` and `RedisSessionSecurityContextRepositoryTest`, reusing existing security
configuration, session reader/writer, WireMock, and Redis integration suites.

### Non-goals

Do not change OAuth, authorization, cookie, or security-context production behavior.

### Required context

Read both Mockito suites, `SecurityConfigIntegrationTest`, related configuration integration tests,
session integration tests, and the production security-context repository.

### Execution steps

1. Move unique security-chain behavior into existing full-context configuration tests with real
   application beans and WireMock external endpoints.
2. Test Redis security-context load/save outcomes with real session components and Redis state.
3. Use Spring reactive test objects or concrete framework implementations for exchange objects;
   do not replace application session components with mocks.
4. Remove superseded suites and all remaining Mockito imports from session-gateway Java tests.

### Implementation notes

Avoid re-testing Spring Security mechanics. Preserve only configuration choices and application
session behavior owned by session-gateway.

### Validation

Run the affected security/configuration/session integration suites, then
`./gradlew clean spotlessApply` and `./gradlew clean build`. Require
`rg -l 'org\.mockito|override\.mockito' src/test/java` to return no files.

### Completion criteria

Session-gateway has no Mockito-using Java tests, owned security behavior is proven through real
components, and the full build passes.

## Phase 12: Align Currency Guidance and Remove the Integration Spy

### Workspace

../currency-service

### Goal

Align currency-service documentation and remove redundant Mockito verification from messaging
integration coverage.

### Scope

Update local testing guidance and `docs/advanced-patterns-usage.md`; remove
`@MockitoSpyBean` from `EventListenerIntegrationTest` while preserving observable event, RabbitMQ,
import, and database assertions.

### Non-goals

Do not migrate the scheduler test in this phase or change messaging behavior.

### Required context

Read local instructions, canonical testing guidance, the advanced-patterns examples,
`EventListenerIntegrationTest`, end-to-end messaging tests, container configuration, and WireMock
fixtures.

### Execution steps

1. Replace Mockito-positive documentation examples with real Testcontainers/WireMock/application
   examples and add the canonical consultation trigger where needed.
2. Remove the publisher spy, reset calls, and call-count assertions from the event listener suite.
3. Retain or strengthen observable assertions on completed events, broker-driven consumption,
   imported rates, and disabled-currency behavior.
4. Remove cases duplicated by stronger end-to-end messaging coverage rather than retaining them for
   method verification.

### Implementation notes

The test already proves the full flow through persisted state. A publisher invocation count is an
implementation detail unless it corresponds to a documented externally observable delivery
guarantee.

### Validation

Run messaging integration suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Confirm no Mockito remains in `EventListenerIntegrationTest` and inspect all
documentation matches in context.

### Completion criteria

Currency guidance matches service-common, messaging integration tests use observable results
without spies, and the full build passes.

## Phase 13: Replace Currency Scheduler Mocks

### Workspace

../currency-service

### Goal

Finish the workspace remediation by testing scheduler retry behavior without mocking the scheduler's
application-owned import service.

### Scope

Replace Mockito usage in `ExchangeRateImportSchedulerTest` using real application behavior,
WireMock/Testcontainers where needed, and a deterministic concrete `TaskScheduler` test
implementation for capturing and triggering retries.

### Non-goals

Do not wait for real retry delays, test ShedLock's implementation, call live FRED, or change retry
semantics solely for test convenience.

### Required context

Read the scheduler and its test, exchange-rate import integration tests, retry properties,
`AbstractWireMockTest`, PostgreSQL/RabbitMQ configuration, metrics assertions, and the final
service-common rule.

### Execution steps

1. Run the scheduler with the real exchange-rate import service and use WireMock responses plus
   persisted currency state to drive success, transient failure, and exhaustion paths.
2. Provide a small recording `TaskScheduler` test implementation that exposes scheduled task/time
   and lets tests invoke retries immediately and deterministically.
3. Preserve observable retry timing policy, attempt counts, metrics, exhaustion, and successful
   import outcomes without mocking application components.
4. Remove all Mockito imports, captors, extensions, and call verification from the suite.
5. Audit the repository's remaining Mockito text matches and update nearby documentation for any
   newly established reusable scheduler-test pattern.

### Implementation notes

The recording scheduler is a concrete implementation of a Spring framework interface, which the
rule permits. Keep it test-scoped and purpose-specific; do not create a general mocking framework.

### Validation

Run scheduler and exchange-rate import integration suites, then `./gradlew clean spotlessApply` and
`./gradlew clean build`. Require `rg -l 'org\.mockito|override\.mockito' src/test/java` to return no
files. Confirm the repository-local scans completed in Phases 2, 7, 9, 11, and 13 collectively cover
all five Java repositories from the baseline audit.

### Completion criteria

Currency-service has no Mockito-using Java tests, retry behavior remains deterministic and covered,
all affected repositories have passed their local full builds, and the agreed guidance is reflected
in canonical and consumer documentation.
