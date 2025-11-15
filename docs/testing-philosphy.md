# Testing Philosophy

## Core Principle: Test Behavior, Not Implementation

This reference implementation uses a layered testing strategy that prioritizes real integration over mocked abstractions. Tests should give us confidence to refactor and upgrade dependencies independently.

## Testing Layers

### 1. API Contract Tests (Primary Confidence Layer)
- Black-box tests against the actual HTTP API
- Run in CI/CD against deployed services
- Test the contract, not the framework
- **Benefit**: Swap Spring Boot for anything else - if API tests pass, you're good

### 2. Infrastructure Integration Tests (Testcontainers)
- Test each infrastructure boundary: database, messaging, caching, external APIs
- Use real dependencies via testcontainers (Postgres, RabbitMQ, Redis, etc.)
- Verify YOUR configuration works, not that Spring works
- **Example**: Test that your retry logic + DLQ routing works, not that RabbitMQ has a DLQ

### 3. Unit Tests (Business Logic Only)
- Pure functions and domain logic
- No framework dependencies
- If it's simple CRUD with no business rules, skip the test

## What We Don't Test

### No Mocks of Internal Components
**Hard rule**: No `@MockBean`, no Mockito on repositories/services/controllers.

**Why**: Mocks drift from reality, create false confidence, and couple tests to implementation details. When you upgrade Spring Boot, you shouldn't have to rewrite test infrastructure.

**Exception**: External system boundaries (third-party APIs via WireMock, time via `Clock` injection). Mock things you don't control, not things you wrote.

### No Framework Verification Tests
Don't test that Spring does what Spring says it does:
- Don't test that `@Scheduled` annotations work
- Don't test that `@Transactional` creates transactions
- Don't test that validation annotations validate

**Test YOUR code**: Does your scheduler configuration use the right cron? Does your transaction boundary make sense for your use case?

## Development Workflow

1. Write API test for the contract
2. Implement feature
3. Add testcontainers integration test if touching infrastructure
4. Add unit test if complex business logic exists
5. Run tests - they should fail for the right reasons (your bug, not Spring's)

## Upgrade Strategy

When upgrading Spring Boot:
- API tests remain unchanged (framework agnostic)
- Integration tests may need configuration updates
- Unit tests remain unchanged (no framework dependencies)

This separation lets you validate the upgrade without rewriting the entire test suite.

## Philosophy

Simple code doesn't need tests proving it's simple. Complex infrastructure needs real integration tests, not mocked fantasies. The API contract is the only promise that matters.