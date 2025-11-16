# Service Common

> **⚠️ Work in Progress**: This project is under active development. Features and documentation are subject to change.

Shared libraries for Budget Analyzer microservices - a personal finance management application.

## Overview

Service Common is a **multi-module Gradle project** that provides common functionality shared across all Budget Analyzer backend microservices. It consists of two modules:

### service-core
**Purpose**: Minimal-dependency core utilities for microservices

**Features**:
- Base JPA entity classes (AuditableEntity, SoftDeletableEntity)
- JPA entity listeners and repository utilities
- CSV parsing capabilities (OpenCSV integration)
- Safe logging utilities with sensitive data masking

**Dependencies**: Spring Data JPA, Jackson, SLF4J, OpenCSV

### service-web
**Purpose**: Spring Boot web service components with auto-configuration

**Features**:
- Standardized exception handling (@RestControllerAdvice)
- API error response models (ApiErrorResponse)
- HTTP request/response logging filters
- Correlation ID support
- OpenAPI/Swagger base configuration

**Dependencies**: service-core (transitive), Spring Boot Starter Web, Spring Boot Starter Actuator, SpringDoc OpenAPI

**Note**: service-web includes service-core transitively, so most microservices only need to depend on service-web.

## Purpose

Service Common promotes code reuse and consistency across microservices by:
- Reducing code duplication
- Ensuring consistent data models
- Providing shared utilities
- Standardizing error handling and validation

## Technology Stack

- **Java 24**
- **Spring Boot 3.x** (Starter Web, Data JPA)
- **SpringDoc OpenAPI** for API documentation
- **OpenCSV** for CSV file processing
- **JUnit 5** for testing

## Usage

These libraries are published to Maven Local and consumed by other Budget Analyzer services.

### Which Module Should I Use?

**Most microservices should use `service-web`** (which transitively includes `service-core`):

```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}
```

**Use `service-core` alone** only if you need minimal dependencies without Spring Web features:

```kotlin
dependencies {
    implementation("org.budgetanalyzer:service-core:0.0.1-SNAPSHOT")
}
```

### Adding as a Dependency

In your service's `build.gradle.kts`:

```kotlin
dependencies {
    // Recommended: Use service-web (includes service-core transitively)
    implementation("org.budgetanalyzer:service-web:0.0.1-SNAPSHOT")
}

repositories {
    mavenLocal()
    mavenCentral()
}
```

### Spring Component Scanning

Enable auto-configuration by including the service-common packages in your component scan:

```java
@SpringBootApplication(scanBasePackages = {
    "org.budgetanalyzer.yourservice",  // Your service
    "org.budgetanalyzer.service"       // service-web (exception handlers, filters)
})
```

**Note**: If you only use service-core, no component scanning is required (it contains only utilities and base classes).

### Building and Publishing

```bash
# Build all modules
./gradlew clean build

# Publish both modules to Maven Local
./gradlew publishToMavenLocal
```

This publishes both artifacts:
- `org.budgetanalyzer:service-core:0.0.1-SNAPSHOT`
- `org.budgetanalyzer:service-web:0.0.1-SNAPSHOT`

## Development

### Prerequisites

- JDK 24
- Gradle (wrapper included)

### Building

```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Check code style
./gradlew spotlessCheck

# Apply code formatting
./gradlew spotlessApply
```

## Code Quality

This project enforces code quality through:
- **Google Java Format** for consistent code style
- **Checkstyle** for code standards
- **Spotless** for automated formatting

## Project Structure

```
service-common/
├── service-core/                    # Core utilities module
│   ├── src/main/java/
│   │   └── org/budgetanalyzer/core/
│   │       ├── domain/              # Base JPA entities
│   │       ├── repository/          # Repository utilities
│   │       ├── csv/                 # CSV parsing
│   │       └── logging/             # Safe logging
│   ├── src/test/java/
│   └── build.gradle.kts
├── service-web/                     # Spring Boot web module
│   ├── src/main/java/
│   │   └── org/budgetanalyzer/service/
│   │       ├── exception/           # Exception hierarchy
│   │       ├── api/                 # Error response models
│   │       ├── http/                # HTTP filters/logging
│   │       └── config/              # OpenAPI config
│   ├── src/test/java/
│   └── build.gradle.kts
├── build.gradle.kts                 # Root build configuration
├── settings.gradle.kts              # Multi-module setup
└── config/                          # Checkstyle configuration
```

## Related Repositories

- **Orchestration**: https://github.com/budgetanalyzer/orchestration
- **Transaction Service**: https://github.com/budgetanalyzer/transaction-service
- **Currency Service**: https://github.com/budgetanalyzer/currency-service
- **Web Frontend**: https://github.com/budgetanalyzer/budget-analyzer-web

## License

MIT

## Contributing

This project is currently in early development. Contributions, issues, and feature requests are welcome as we build toward a stable release.
