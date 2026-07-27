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

- Java 17
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

## Coding Standards

## Estilo
*   **Indentación:** Se utiliza una indentación de 4 espacios. No se deben usar tabulaciones.
*   **Llaves:** Se sigue el estilo K&R, donde la llave de apertura de un bloque (`{`) se coloca en la misma línea que la declaración y la llave de cierre (`}`) en una nueva línea.
*   **Longitud de Línea:** Las líneas de código deben ser de una longitud razonable para facilitar la lectura, preferiblemente no excediendo los 120 caracteres.
*   **Espacios en Blanco:**
    *   Se deben usar espacios alrededor de los operadores (`=`, `+`, `-`, `*`, `/`, `==`, `!=`, `>`, `<`, `>=`, `<=`, `&&`, `||`).
    *   Se debe usar un espacio después de las comas en las listas de parámetros, declaraciones de variables, etc.
    *   Se debe usar un espacio después de las palabras clave como `if`, `for`, `while`, `catch`.
## Nombres
### Clases e Interfaces
*   **Clases:** Deben utilizar `PascalCase` (cada palabra comienza con mayúscula, sin espacios) y ser sustantivos o frases sustantivas.
    *   Ejemplo: `ChatDto`, `ChatbotServiceImpl`, `ChatBotRestController`.
*   **Interfaces:** Deben utilizar `PascalCase` y comenzar con la letra `I` seguida del nombre de la interfaz.
    *   Ejemplo: `IChatBotService`, `IAIGestor`.
*   **DTOs (Data Transfer Objects):** Deben terminar con el sufijo `Dto`.
    *   Ejemplo: `ChatDto`, `ChatMessageDto`.
*   **Implementaciones:** Las clases que implementan una interfaz a menudo terminan con el sufijo `Impl`.
    *   Ejemplo: `ChatbotServiceImpl`, `ChatBotGestorImpl`.
*   **Excepciones:** Deben terminar con el sufijo `Exception`.
    *   Ejemplo: `ChatBotException`.
### Funciones / Métodos
*   Deben utilizar `camelCase` (la primera palabra en minúscula, las siguientes palabras comienzan con mayúscula) y ser verbos o frases verbales que describan la acción que realiza el método.
    *   Ejemplo: `multiMessage()`, `getChatGpt()`, `send()`.
*   Los parámetros de los métodos también deben seguir `camelCase`.
    *   Ejemplo: `ChatDto chat`, `String id`.
### Variables
*   Deben utilizar `camelCase`.
    *   Ejemplo: `senderId`, `text`, `chatBotGestor`.
### Constantes
*   Deben utilizar `UPPER_SNAKE_CASE` (todas las letras en mayúscula, palabras separadas por guiones bajos).
*   Se deben declarar como `public static final`.
*   Preferiblemente, agrupar las constantes en clases `final` con un constructor privado para evitar instanciación.
    *   Ejemplo: `STATUS_STRING_ENABLE`, `AUDIT_APP_CREATED_BY`, `DOCUMENT_ASSISTANT_TYPE_ID` en `ChatBotConstants`.
## Estructura de Archivo
*   El proyecto sigue la estructura estándar de Maven/Spring Boot (`src/main/java`, `src/main/resources`).
*   Los paquetes deben organizarse de forma lógica por dominio y tipo de componente.
    
## Tests
*   **Framework:** Se utiliza JUnit 5 para la escritura de pruebas unitarias e de integración.
*   **Ejecutor de Pruebas:** Se utilizan `SpringRunner` para pruebas que requieren el contexto de Spring (ej `ConvertGalacticServiceTest`) y `BlockJUnit4ClassRunner` para pruebas unitarias más aisladas (`ConvertGalacticCurrencyTest`, `ConvertCurrencyTest`).
*   **Ubicación:** Los archivos de prueba se encuentran en la estructura de directorios estándar de Maven: `src/test/java/ec/com/technoloqie/galactic/currency/test/`.
*   **Nomenclatura:** Los nombres de las clases de prueba terminan con el sufijo `Test` (ej. `ConvertGalacticServiceTest`).
*   **Métodos de Prueba:** Los métodos de prueba están anotados con `@Test`.
*   **Asertiones:** Se utilizan las aserciones estáticas de JUnit (ej. `assertEquals`, `assertTrue`, `fail`).
*   **Contexto de Spring:** Para pruebas de integración, se configura el contexto de Spring utilizando `@ContextConfiguration` para cargar las clases de servicio necesarias.
*   **Logging en Tests:** Se utiliza `GalacticCurrencyLog.getLog().info()` y `GalacticCurrencyLog.getLog().error()` para registrar información durante la ejecución de las pruebas.
*   **Manejo de Excepciones en Tests:** Los bloques `try-catch` se utilizan dentro de los métodos de prueba para capturar excepciones y, en caso de error, llamar a `fail()` o `assertTrue(..., Boolean.TRUE)` para indicar el fallo de la prueba.
*   **Configuración Previa (Setup):** Los métodos anotados con `@Before` se utilizan para realizar configuraciones antes de la ejecución de cada prueba.

## Manejo de Errores
*   **Excepciones Personalizadas:** Se utilizan excepciones personalizadas (ej. `ChatBotException` que extiende `RuntimeException`) para encapsular errores específicos de la aplicación.
*   **Bloques `try-catch`:** Se utilizan para manejar excepciones de forma controlada, especialmente en capas de servicio, gestores y controladores donde se interactúa con componentes que pueden lanzar excepciones.
*   **Logging de Errores:** Se utiliza el framework de logging (Lombok `@Slf4j` y SLF4J/Logback) para registrar mensajes de error, incluyendo la traza de la pila (`e`) cuando sea apropiado.
    *   Ejemplo: `log.error("Error al momento de consultar con modelo ia ", e);`.
*   **Respuestas Estandarizadas en Controladores:** Los controladores deben devolver `ResponseEntity` con los códigos de estado HTTP apropiados (ej. `HttpStatus.CREATED`, `HttpStatus.NOT_FOUND`, `HttpStatus.INTERNAL_SERVER_ERROR`).
*   **DTOs de Respuesta de Error:** Se utiliza un DTO de respuesta estandarizado (ej. `ResponseFormatOut`) para comunicar mensajes de error, códigos de error y trazas cuando ocurre una excepción en la API.
## Comentarios
*   **Encabezados de Archivo:** Muchos archivos incluyen un bloque de comentarios inicial con información de derechos de autor y licencia.
*   **Comentarios de Documentación (Javadoc):**
    *   Se utilizan para documentar clases, interfaces y métodos públicos, explicando su propósito, parámetros, valores de retorno y excepciones lanzadas.
    *   En los controladores, se utilizan anotaciones de Swagger (`@Operation`, `@ApiResponses`, `@Tag`) para documentar la API REST.
    *   Las clases también pueden incluir bloques Javadoc para describir su autor y cambios.
*   **Comentarios en Línea:** Se deben usar de forma esporádica para explicar lógica compleja o decisiones no obvias en el código.
    *   Ejemplo: `//TODO tomar de cabecera http` para indicar tareas pendientes.
*   **Evitar Código Comentado:** Se debe evitar dejar grandes bloques de código comentado que no estén en uso. Si el código no es necesario, debe eliminarse; el control de versiones (Git) puede recuperarlo si es necesario.
