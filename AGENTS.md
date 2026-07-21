# Agent Guide for tec-auditbot-ws

This guide provides essential context for agents working in this Spring Boot Hexagonal Architecture project.

## Project Overview
- **Type**: Spring Boot application with Hexagonal Architecture principles.
- **Build Tool**: Maven.
- **Java Version**: 17.
- **Main Entry Point**: `src/main/java/ec/com/technoloqie/auditbot/api/TecAuditbotWsApplication.java`

## Key Developer Commands

### Build
- **Clean and Build**: `./mvnw clean install`
- **Run Application**: `./mvnw spring-boot:run`

### Testing
- **Run all tests**: `./mvnw test`
- **Run a single test class**: `./mvnw test -Dtest=TecAuditbotWsApplicationTests` (replace `TecAuditbotWsApplicationTests` with the actual test class name).

## Architecture Notes
- The project follows a Hexagonal Architecture, separating core domain logic from external concerns (like web or database).
- Domain models are located under `src/main/java/ec/com/technoloqie/auditbot/api/domain/`.
- Configuration for the Spring Boot application is in `src/main/resources/application.properties`.

## Conventions
- Lombok is used for reducing boilerplate code (e.g., getters, setters, constructors). Ensure Lombok annotations are handled correctly during refactoring or code generation.