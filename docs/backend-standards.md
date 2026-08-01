---
description: Backend development standards, architecture guidelines, coding conventions, and best practices for the AuditBot AI Java Spring Boot microservice.
globs:
  - "src/main/java/**/*.java"
  - "src/main/resources/**"
  - "pom.xml"
alwaysApply: true
---

# Backend Project Standards and Best Practices

## Table of Contents

- Overview
- Technology Stack
- Architecture Overview
- Project Structure
- Design Patterns
- Coding Standards
- API Standards
- RabbitMQ Standards
- AI Integration Standards
- Database Patterns
- Testing Standards
- Performance Best Practices
- Security Standards
- Development Workflow

---

# Overview

AuditBot AI is a Java Spring Boot microservice responsible for auditing conversations using AI analyzers.

The backend consumes events from RabbitMQ, executes multiple AI analysis strategies through Ollama Cloud, persists audit results in MySQL, and exposes REST APIs consumed by the dashboard.

The architecture prioritizes:

- Separation of responsibilities
- Event-driven communication
- Resilience against external failures
- Extensibility of AI analyzers
- Maintainable and testable code

Main principles:

- Clean Code
- SOLID
- DRY
- KISS
- YAGNI
- Dependency Injection
- Separation of concerns

---

# Technology Stack

## Core Technologies

- Java 21
- Spring Boot 3
- Spring Web
- Spring AMQP
- Spring Data JPA
- Hibernate
- Maven

## Database

- MySQL 8
- Hibernate ORM
- JPA repositories

## Messaging

- RabbitMQ

