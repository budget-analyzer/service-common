plugins {
    alias(libs.plugins.spring.dependency.management)
}

description = "Core utilities for microservices - minimal dependencies"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    // API dependencies (transitive to consumers)
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.slf4j.api)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.opencsv)

    // Test dependencies
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}
