# Tasks — US-06 Publicación de Auditoría de Chat vía REST (`pub_aud_msg`)

> Ejecutar en orden de dependencia. Cada tarea se marca `[x]` solo después de completar su implementación y sus pruebas asociadas.  
> Exportar `PROFILE=local` y utilizar Java 17 en todos los comandos Maven.

---

## Fase 1 — DTOs y Servicio de Aplicación de Ingesta

- [x] **T1 — Crear DTOs para respuesta de admisión y errores de la API.**
  - Crear `dto/ConversationAuditSubmissionResponseDto.java` (record: `eventId`, `chatId`, `status`, `message`, `submittedAt`).
  - Crear `dto/ApiErrorResponseDto.java` (record: `timestamp`, `status`, `error`, `message`, `invalidFields`).
  - **Cubre:** R2, R5, R12, R13.

- [x] **T2 — Implementar `IConversationAuditSubmissionService` y `ConversationAuditSubmissionServiceImpl`.**
  - Crear interfaz `service/IConversationAuditSubmissionService.java`.
  - Crear implementación `service/impl/ConversationAuditSubmissionServiceImpl.java` con inyección por constructor de `IConversationAuditPublisher`, `ConversationAuditRequestValidator`, `Clock` y `@Value("${auditbot.messaging.producer:smart-chatbot-rest}") String producer`.
  - Construir envelope `ConversationAuditRequestDto` con `schemaVersion = "1.0"` y nuevo UUID `eventId`.
  - Validar contra `ConversationAuditRequestValidator` y publicar mediante `IConversationAuditPublisher.publish(requestDto)`.
  - Emitir log estructurado con `etapa=REST_AUDIT_SUBMISSION` sin volcar el cuerpo del chat.
  - **Cubre:** R3, R4, R10, R11, R16, R17, R19.

- [x] **T3 — Crear pruebas unitarias en `ConversationAuditSubmissionServiceImplTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/ConversationAuditSubmissionServiceImplTest.java` con Mockito.
  - Verificar construcción correcta de metadata del evento, validación del contrato, delegación exitosa al publicador de RabbitMQ, y propagación limpia de excepciones de validación o broker.
  - **Cubre:** R3, R4, R10, R11, R16, R17.

---

## Fase 2 — Seguridad, Token Bearer y Políticas de CORS

- [x] **T4 — Implementar validador de token y filtro de autenticación JWT.**
  - Crear `config/JwtTokenValidator.java` (o componente utilitario) utilizando `io.jsonwebtoken` para verificar firma y expiración del token con clave configurable `ec.com.technoloqie.auditbot.security.jwt.secret`.
  - Crear `config/JwtAuthenticationFilter.java` extendiendo `OncePerRequestFilter` para extraer y validar `Authorization: Bearer <token>`, estableciendo `SecurityContext` en peticiones válidas y permitiendo continuar la cadena.
  - **Cubre:** R7, R8, R9, R17.

- [x] **T5 — Configurar `SecurityConfiguration` con CORS, CSRF disabled y gestión stateless.**
  - Crear `config/SecurityConfiguration.java` con `@Configuration` y `@EnableWebSecurity`.
  - Definir `SecurityFilterChain` con CSRF desactivado, `SessionCreationPolicy.STATELESS`, CORS configurado hacia `${ec.com.technoloqie.chatbot.app.url}` para métodos `GET, POST, OPTIONS`.
  - Proteger ruta `POST /api/v1/audits/conversations` requiriendo autenticación, permitiendo `GET /api/v1/audits` público.
  - Configurar `AuthenticationEntryPoint` para retornar `HTTP 401 Unauthorized` ante intentos de acceso no autorizados.
  - **Cubre:** R7, R8, R9, R14, R15, R20, R21.

- [x] **T6 — Crear pruebas de seguridad y CORS en `SecurityConfigurationTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/config/SecurityConfigurationTest.java`.
  - Verificar que las peticiones sin token sean rechazadas con 401, las peticiones con token válido sean admitidas, y que las cabeceras CORS de preflight `OPTIONS` respondan correctamente.
  - **Cubre:** R7, R8, R9, R14, R15, R20, R21.

---

## Fase 3 — Controlador REST y Manejador Global de Errores

- [x] **T7 — Implementar manejador de errores `@RestControllerAdvice`.**
  - Crear `controller/advice/AuditChatControllerAdvice.java`.
  - Mapear `InvalidAuditRequestException` a `HTTP 400 Bad Request` con lista de `invalidFields`.
  - Mapear `AuditPublicationException` a `HTTP 502 Bad Gateway` con mensaje controlado sin filtrar datos sensibles.
  - Mapear `HttpMessageNotReadableException` y errores de serialización a `HTTP 400 Bad Request`.
  - **Cubre:** R5, R6, R13, R17.

- [x] **T8 — Actualizar `AuditChatRestController` con endpoint `POST /conversations`.**
  - Modificar `controller/AuditChatRestController.java` inyectando `IConversationAuditSubmissionService`.
  - Implementar método `@PostMapping("/conversations")` que reciba `@RequestBody AuditConversationDto conversation` y retorne `ResponseEntity<ConversationAuditSubmissionResponseDto>` con código `HttpStatus.ACCEPTED` (202).
  - Mantener el controlador delgado sin lógica de negocio ni importaciones de RabbitMQ.
  - **Cubre:** R1, R2, R12, R18, R19.

- [x] **T9 — Crear pruebas de integración en `AuditChatRestControllerTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/controller/AuditChatRestControllerTest.java` con `MockMvc`.
  - Verificar respuesta `HTTP 202 Accepted` con payload válido y token Bearer.
  - Verificar respuesta `HTTP 400 Bad Request` ante payload con campos inválidos o ausentes.
  - Verificar respuesta `HTTP 401 Unauthorized` ante omisión o invalidez del token.
  - Verificar respuesta `HTTP 502 Bad Gateway` cuando el servicio lanza `AuditPublicationException`.
  - Verificar cabeceras CORS devueltas.
  - **Cubre:** R1, R2, R5, R6, R7, R8, R9, R12, R13, R14, R15.

---

## Fase 4 — Reglas de Arquitectura y Verificación Integral

- [x] **T10 — Actualizar reglas de arquitectura en `ArchitectureRulesTest`.**
  - En `ArchitectureRulesTest`, ajustar la regla de controllers para asegurar que no dependan directamente de `IConversationAuditPublisher` ni de `integration.rabbitmq`, sino de `IConversationAuditSubmissionService`.
  - Verificar inyección por constructor y pureza de capas.
  - **Cubre:** R18, R19.

- [x] **T11 — Ejecución de verificación integral (`./mvnw clean verify`).**
  - Ejecutar `./mvnw clean verify` con Java 17.
  - Confirmar `BUILD SUCCESS` con 100% de tests unitarios, de repositorio, de integración y de arquitectura en verde.
  - **Cubre:** R22.
