# Testing Patterns - Budget Analyzer Microservices

## Testing Philosophy

**CRITICAL PRINCIPLE**: Tests must always be written for how components **should** behave according to their specification and real-world requirements, NOT around defective implementations.

### When Tests Fail Due to Implementation Issues

1. **STOP immediately** - Do not attempt to work around the implementation deficiency
2. **Analyze the failure** - Determine if the test is correct or if the implementation is deficient
3. **Explain the situation** to the team
4. **Fix the implementation** or update requirements - never write tests around bugs

## Test Types

### Unit Tests

**File Pattern**: `*Test.java`
**Location**: `src/test/java` (mirror of `src/main/java`)
**Framework**: JUnit 5 (Jupiter)
**Spring Context**: No

**Characteristics**:
- Fast execution (milliseconds)
- No external dependencies
- No Spring context loading
- Uses mocks for dependencies

**Example**:
```java
class TransactionServiceTest {

    @Test
    void shouldCalculateTotalAmount() {
        var service = new TransactionService();
        var result = service.calculateTotal(transactions);
        assertEquals(new BigDecimal("150.00"), result);
    }
}
```

### Integration Tests

**File Pattern**: `*IntegrationTest.java`
**Location**: `src/test/java` (mirror of `src/main/java`)
**Framework**: JUnit 5 + Spring Boot Test
**Spring Context**: Yes

**Characteristics**:
- Slower execution (seconds)
- Tests multiple components together
- Loads Spring context
- Uses TestContainers for real database/Redis/RabbitMQ

**Example**:
```java
@SpringBootTest
@Testcontainers
class TransactionRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TransactionRepository repository;

    @Test
    void shouldSaveAndRetrieveTransaction() {
        var transaction = new Transaction();
        transaction.setAmount(new BigDecimal("100.00"));

        var saved = repository.save(transaction);
        var retrieved = repository.findByIdActive(saved.getId());

        assertTrue(retrieved.isPresent());
        assertEquals(saved.getId(), retrieved.get().getId());
    }
}
```

### Controller Tests

**File Pattern**: `*ControllerTest.java`
**Location**: `src/test/java` (mirror of `src/main/java`)
**Framework**: JUnit 5 + MockMvc
**Spring Context**: Partial (Web Layer only)

**Characteristics**:
- Tests HTTP layer only
- Uses `@WebMvcTest` for faster execution
- Mocks service dependencies
- Validates request/response format

**Example**:
```java
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    void shouldReturnTransactionById() throws Exception {
        var transaction = new Transaction();
        transaction.setId(1L);
        transaction.setAmount(new BigDecimal("100.00"));

        when(transactionService.findById(1L)).thenReturn(Optional.of(transaction));

        mockMvc.perform(get("/api/v1/transactions/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    void shouldReturn404WhenTransactionNotFound() throws Exception {
        when(transactionService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/transactions/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }
}
```

## TestContainers

**Purpose**: Provide real infrastructure (database, Redis, RabbitMQ) for integration tests

### PostgreSQL Container

```java
@Testcontainers
class DatabaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

### Redis Container

```java
@Container
static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
    .withExposedPorts(6379);

@DynamicPropertySource
static void configureRedis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
}
```

## Mocking Strategies

### Use Mockito for Dependencies

```java
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository repository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Test
    void shouldCreateTransaction() {
        var transaction = new Transaction();
        when(repository.save(any())).thenReturn(transaction);

        var result = transactionService.create(transaction);

        verify(repository).save(transaction);
        verify(auditService).logCreation(transaction);
    }
}
```

### ArgumentCaptor for Complex Verification

```java
@Test
void shouldSaveTransactionWithCorrectTimestamp() {
    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

    transactionService.create(transaction);

    verify(repository).save(captor.capture());
    assertNotNull(captor.getValue().getCreatedAt());
}
```

## Test Coverage Goals

### Minimum Coverage
- **Overall**: 80% code coverage
- **Critical paths**: 100% coverage
- **Utilities**: 100% coverage
- **Controllers**: 90% coverage
- **Services**: 85% coverage

### Coverage Tools
```bash
# Run tests with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## Testing Best Practices

### 1. Test Naming Convention

```java
// Pattern: should[ExpectedBehavior]When[Condition]
@Test
void shouldThrowExceptionWhenAmountIsNegative() { }

@Test
void shouldReturnEmptyListWhenNoTransactionsExist() { }

@Test
void shouldCalculateTotalCorrectly() { }
```

### 2. Arrange-Act-Assert Pattern

```java
@Test
void shouldCalculateTotal() {
    // Arrange - Set up test data
    var transactions = List.of(
        createTransaction(100.00),
        createTransaction(50.00)
    );

    // Act - Execute the method
    var total = service.calculateTotal(transactions);

    // Assert - Verify the result
    assertEquals(new BigDecimal("150.00"), total);
}
```

### 3. Test One Thing Per Test

```java
// ❌ BAD - Testing multiple scenarios
@Test
void testTransactionCreation() {
    // Tests both creation AND validation
}

// ✅ GOOD - Separate tests
@Test
void shouldCreateTransactionSuccessfully() { }

@Test
void shouldThrowExceptionWhenAmountIsInvalid() { }
```

### 4. Use Test Fixtures

```java
class TransactionTestFixtures {

    static Transaction createTransaction() {
        return createTransaction("100.00", "Test");
    }

    static Transaction createTransaction(String amount, String description) {
        var transaction = new Transaction();
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDescription(description);
        transaction.setDate(LocalDate.now());
        return transaction;
    }
}
```

### 5. Test Edge Cases

```java
@Test
void shouldHandleNullValues() { }

@Test
void shouldHandleEmptyList() { }

@Test
void shouldHandleVeryLargeNumbers() { }

@Test
void shouldHandleSpecialCharacters() { }
```

### 6. Don't Test Framework Code

```java
// ❌ BAD - Testing JPA's save method
@Test
void shouldSaveToDatabase() {
    repository.save(transaction);
    // This just tests that JPA works
}

// ✅ GOOD - Test business logic
@Test
void shouldValidateAmountBeforeSaving() {
    assertThrows(BusinessException.class,
        () -> service.create(transactionWithNegativeAmount));
}
```

## Testing Soft-Delete Entities

```java
@Test
void shouldSoftDeleteTransaction() {
    var transaction = repository.save(new Transaction());

    repository.delete(transaction);

    // Should not be in active records
    assertFalse(repository.findByIdActive(transaction.getId()).isPresent());

    // But should still exist in database
    assertTrue(repository.findById(transaction.getId()).isPresent());
    assertTrue(repository.findById(transaction.getId()).get().isDeleted());
}
```

## Testing Exception Handling

```java
@Test
void shouldThrowResourceNotFoundExceptionWhenIdDoesNotExist() {
    when(repository.findByIdActive(999L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class,
        () -> service.getById(999L));
}

@Test
void shouldReturnErrorResponseForInvalidRequest() throws Exception {
    mockMvc.perform(post("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": -100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
}
```

## Performance Testing

### Testing with Large Datasets

```java
@Test
void shouldHandleLargeDataset() {
    var transactions = IntStream.range(0, 10000)
        .mapToObj(i -> createTransaction("100.00", "Transaction " + i))
        .toList();

    var startTime = System.currentTimeMillis();
    service.processTransactions(transactions);
    var duration = System.currentTimeMillis() - startTime;

    // Should process 10k transactions in under 5 seconds
    assertTrue(duration < 5000, "Processing took too long: " + duration + "ms");
}
```

## Test Organization

### Directory Structure
```
src/test/java/
└── org/budgetanalyzer/{service}/
    ├── controller/
    │   └── TransactionControllerTest.java
    ├── service/
    │   └── TransactionServiceTest.java
    │   └── impl/
    │       └── TransactionServiceImplIntegrationTest.java
    ├── repository/
    │   └── TransactionRepositoryIntegrationTest.java
    └── util/
        └── TransactionTestFixtures.java
```

## Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests TransactionServiceTest
```

### Run Specific Test Method
```bash
./gradlew test --tests TransactionServiceTest.shouldCreateTransaction
```

### Run Tests with Coverage
```bash
./gradlew test jacocoTestReport
```

### Skip Tests (for quick builds)
```bash
./gradlew build -x test
```

## Common Testing Pitfalls

### 1. Brittle Tests
```java
// ❌ BAD - Will break if order changes
assertEquals("John", users.get(0).getName());

// ✅ GOOD - Tests what matters
assertTrue(users.stream().anyMatch(u -> u.getName().equals("John")));
```

### 2. Testing Implementation Details
```java
// ❌ BAD - Testing private method behavior
@Test
void shouldCallPrivateMethod() { }

// ✅ GOOD - Test public API behavior
@Test
void shouldReturnCorrectResult() { }
```

### 3. Not Cleaning Up After Tests
```java
@AfterEach
void tearDown() {
    repository.deleteAll();
    MDC.clear();
}
```

## Resources

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Spring Boot Testing Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [TestContainers Documentation](https://testcontainers.com/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
