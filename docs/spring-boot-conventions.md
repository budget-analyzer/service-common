# Spring Boot Conventions - Budget Analyzer Microservices

## Architecture Layers

**Pattern**: Clean layered architecture with clear separation of concerns

```
Controller Layer (HTTP concerns)
    ↓ calls
Service Layer (business logic)
    ↓ calls
Repository Layer (data access)
```

**Rules**:
- Controllers never call repositories directly
- Services contain all business logic
- Repositories are thin data access only
- No business logic in entities (domain models are anemic)

## Package Structure

**Standard package hierarchy**:
```
org.budgetanalyzer.{service-name}
├── api/               # REST controllers
│   ├── request/       # Request DTOs
│   └── response/      # Response DTOs
├── service/           # Business logic (concrete classes)
│   ├── dto/           # Service-layer DTOs
│   └── provider/      # External service provider interfaces & implementations
├── repository/        # JPA repositories
│   └── spec/          # JPA Specifications (optional)
├── domain/            # JPA entities
│   └── event/         # Domain events (optional)
├── client/            # External API clients
├── messaging/         # Messaging infrastructure (optional)
│   ├── listener/      # Message listeners
│   ├── publisher/     # Message publishers
│   ├── consumer/      # Message consumers
│   └── message/       # Message DTOs
├── scheduler/         # Scheduled tasks (optional)
└── config/            # Spring configuration classes
```

**Discovery**:
```bash
# Find all controllers in a service
grep -r "@RestController" src/

# Find all services
grep -r "@Service" src/

# Find all repositories
grep -r "@Repository" src/
```

## Naming Conventions

### Controllers
- **Pattern**: `*Controller`
- **Package**: `*.api`
- **Annotation**: `@RestController`
- **Example**: `TransactionController`, `BudgetController`
- **Request DTOs**: `*.api.request` package
- **Response DTOs**: `*.api.response` package

### Services
- **Pattern**: `*Service` (concrete classes, no interfaces)
- **Package**: `*.service`
- **Annotation**: `@Service`
- **Example**: `TransactionService`, `BudgetService`
- **Rule**: Internal business logic services are concrete classes only. See "When to Use Interfaces" below for the three patterns that DO require interfaces.

### When to Use Interfaces

Budget Analyzer uses interfaces in exactly THREE patterns:

#### 1. Provider Pattern (External Service Boundaries)
- **Rule**: 100% required for all external API/data source integrations
- **Pattern**: `*Provider` interface + concrete implementation
- **Package**: `*.service.provider`
- **Annotation**: `@Service` on implementation
- **Example**:
  - Interface: `ExchangeRateProvider`
  - Implementation: `FredExchangeRateProvider`, `EcbExchangeRateProvider`
- **Purpose**: Decouple from external services, allow multiple implementations
- **See**: [advanced-patterns.md](advanced-patterns.md#provider-abstraction-pattern) for detailed documentation

#### 2. Third-Party Library Abstraction (Avoiding Vendor Lock-in)
- **Rule**: Required when wrapping third-party libraries we don't control
- **Pattern**: Interface in `core.*` + implementation in `core.*.impl`
- **Annotation**: `@Component` on implementation
- **Example**:
  - Interface: `CsvParser` (in `org.budgetanalyzer.core.csv`)
  - Implementation: `OpenCsvParser` (in `org.budgetanalyzer.core.csv.impl`)
  - Third-party: `com.opencsv:opencsv`
- **Purpose**: Abstract third-party implementations to avoid lock-in, make switching libraries transparent to consuming code
- **Goal**: Make interfaces as generic as possible, independent of any specific library

#### 3. Repository Enhancement Pattern
- **Rule**: Used to extend Spring Data JPA with reusable query patterns
- **Pattern**: Interface with default methods extending `JpaSpecificationExecutor`
- **Example**: `SoftDeleteOperations<T extends SoftDeletableEntity>`
- **Purpose**: Add reusable query methods across all repositories (e.g., `findAllActive()`, `findByIdActive()`)

#### Anti-Pattern: Internal Service Interfaces
```java
// ❌ WRONG - Don't create interfaces for internal services
public interface TransactionService { }
public class TransactionServiceImpl implements TransactionService { }

// ✅ CORRECT - Internal services are concrete classes
@Service
public class TransactionService { }
```

### Repositories
- **Pattern**: `*Repository`
- **Package**: `*.repository`
- **Extends**: `JpaRepository<Entity, ID>`
- **Annotation**: `@Repository` (optional, inherited from JpaRepository)
- **Example**: `TransactionRepository`, `BudgetRepository`

### DTOs
- **Pattern**: `*Request`, `*Response` (API layer), `*DTO` (Service layer)
- **Packages**:
  - `*.api.request` - API request DTOs
  - `*.api.response` - API response DTOs
  - `*.service.dto` - Service-layer DTOs (internal)
- **Example**: `CreateTransactionRequest`, `TransactionResponse`, `TransactionDTO`

### Entities
- **Pattern**: Entity name (no suffix)
- **Package**: `*.domain`
- **Annotation**: `@Entity`
- **Example**: `Transaction`, `Budget`, `Category`

## Base Entity Classes

**Module**: service-core

**From the service-core module** (in `org.budgetanalyzer.core.domain`):

### AuditableEntity
Provides automatic timestamp tracking:
- `createdAt` - Timestamp when entity was created (immutable)
- `updatedAt` - Timestamp when entity was last modified (auto-updated)
- Managed by JPA lifecycle callbacks

**Location**: `service-core/src/main/java/org/budgetanalyzer/core/domain/AuditableEntity.java`

**Usage**:
```java
@Entity
public class Transaction extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ... other fields
}
```

### SoftDeletableEntity
Extends `AuditableEntity` with soft-delete support:
- `deleted` boolean field (default: false)
- `SoftDeleteListener` intercepts deletions
- Sets `deleted=true` instead of removing from database

**Location**: `service-core/src/main/java/org/budgetanalyzer/core/domain/SoftDeletableEntity.java`

**Usage**:
```java
@Entity
public class Transaction extends SoftDeletableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ... other fields
}

// In repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
                                               SoftDeleteOperations<Transaction, Long> {
    // Automatically gets findByIdActive(), findAllActive()
}
```

**Note**: `SoftDeleteOperations` interface is also in service-core (`org.budgetanalyzer.core.repository`).

## Persistence Layer: Pure JPA

**CRITICAL RULE**: Use pure JPA (Jakarta Persistence API) exclusively. NO Hibernate-specific features.

**Forbidden**:
```java
❌ import org.hibernate.*;
❌ import org.hibernate.annotations.*;
❌ import org.hibernate.criterion.*;
```

**Allowed**:
```java
✅ import jakarta.persistence.*;
```

**Why?**
- **Portability**: Allows switching JPA providers without code changes
- **Standard compliance**: JPA is a specification with multiple implementations
- **Architectural discipline**: Maintains flexibility at minimal cost

## Exception Handling

**Module**: service-web

**Pattern**: Centralized exception handling with `@ControllerAdvice`

### Exception Hierarchy (from service-web)

**Location**: `service-web/src/main/java/org/budgetanalyzer/service/exception/`

**Base Exceptions**:
- `ServiceException` - Base for all service exceptions (500)
- `ClientException` - Base for client errors (400)

**Specific Exceptions**:
- `ResourceNotFoundException` - Entity not found (404)
- `InvalidRequestException` - Bad request data (400)
- `BusinessException` - Business rule violation (422)
- `ServiceUnavailableException` - Service unavailable (503)

### Usage in Controllers
```java
@GetMapping("/{id}")
public TransactionResponse getById(@PathVariable Long id) {
    var transaction = repository.findByIdActive(id)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    return mapper.toResponse(transaction);
}
```

### Usage in Services
```java
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction createTransaction(Transaction transaction) {
        if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Transaction amount must be positive");
        }
        return transactionRepository.save(transaction);
    }
}
```

### Global Exception Handler
Service-common provides `DefaultApiExceptionHandler` that automatically converts exceptions to standardized API error responses. Just include the library - component scanning will auto-discover it.

## Dependency Injection

**Pattern**: Constructor injection (preferred)

```java
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    // Constructor injection - no @Autowired needed in modern Spring
    public TransactionService(TransactionRepository transactionRepository,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }
}
```

**Avoid**:
- Field injection (`@Autowired` on fields)
- Setter injection (except for optional dependencies)

## Configuration Classes

**Pattern**: `@Configuration` classes for Spring beans

```java
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        // configuration
    }
}
```

**File naming**: `*Config.java`
**Package**: `*.config`

## API Versioning

**Pattern**: URL-based versioning

```java
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    // endpoints
}
```

**Rationale**: Simple, explicit, compatible with API gateway routing

## HTTP Response Patterns

### 201 Created with Location Header

**Pattern**: POST endpoints that create resources MUST return `201 Created` with a `Location` header pointing to the newly created resource.

**HTTP Standard**: RFC 7231 - The Location header provides the URI of the created resource. This is expected behavior in RESTful APIs and followed by all major APIs (Google, GitHub, Stripe, PayPal).

**Implementation**:
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateRequest request) {
    var created = service.create(request.toEntity());

    var location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(created.getId())
        .toUri();

    return ResponseEntity.created(location).body(ResourceResponse.from(created));
}
```

**Key points**:
- Use `ResponseEntity.created(location)` to set both status and header
- Build Location URI with `ServletUriComponentsBuilder.fromCurrentRequest()`
- Always include response body (the created resource representation)
- Location must be an absolute URI (ServletUriComponentsBuilder handles this)

**Discovery**:
```bash
# Find all POST endpoints in a service
grep -r "@PostMapping" src/main/java/*/api/

# Find examples using ResponseEntity.created
grep -rA 10 "ResponseEntity.created" src/main/java/
```

**Example**: See `AdminCurrencySeriesController.create()` in currency-service for a complete implementation.

## Testing Patterns

See [testing-patterns.md](testing-patterns.md) for detailed testing guidelines.

**Quick reference**:
- Unit tests: `*Test.java` (no Spring context)
- Integration tests: `*IntegrationTest.java` (with `@SpringBootTest`)
- Use TestContainers for database/Redis/RabbitMQ
- Minimum 80% code coverage

## Documentation Standards

**Quick reference**:
- All public APIs need Javadoc
- First sentence of Javadoc must end with period
- REST endpoints documented with OpenAPI/Swagger
- Domain models documented in architecture documentation

---

## External Links (GitHub Web Viewing)

*The relative paths in this document are optimized for Claude Code. When viewing on GitHub, use this link to access the referenced controller:*

- [AdminCurrencySeriesController.java](https://github.com/budgetanalyzer/currency-service/blob/main/src/main/java/org/budgetanalyzer/currency/api/AdminCurrencySeriesController.java)
