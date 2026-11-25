# Tasks — US-07 Generar recomendaciones de mejora (`aud_automatic_upturn`)

> Ejecutar en orden de dependencia. Cada tarea se marca `[x]` solo después de completar su implementación y sus pruebas asociadas.  
> Exportar `PROFILE=local` y utilizar Java 17 (`sdk use java 17.0.11-amzn`) en todos los comandos Maven.

---

## Fase 1 — Modelo de datos y persistencia

- [x] **T1 — Crear la migración Flyway `V5__create_audit_recommendation.sql`.**
  - Crear `src/main/resources/db/migration/V5__create_audit_recommendation.sql` con la definición de la tabla `audit_recommendation`, primary key, foreign key en cascada con `audit_result`, e índice sobre `audit_id`.
  - **Cubre:** R14.

- [x] **T2 — Crear enum `RecommendationType`, entidad `AuditRecommendation` y actualizar `AuditResult`.**
  - Crear `model/RecommendationType.java` con los valores `NEW_FAQ`, `ALTERNATIVE_FLOW`.
  - Crear `model/AuditRecommendation.java` mapeando la entidad JPA con relación `@ManyToOne` hacia `AuditResult`.
  - Modificar `model/AuditResult.java` para añadir la relación `@OneToMany(mappedBy = "auditResult", cascade = CascadeType.ALL, orphanRemoval = true)` hacia `AuditRecommendation`.
  - **Cubre:** R15, R16.

- [x] **T3 — Crear repositorio `IAuditRecommendationRepository` y pruebas de integración JPA.**
  - Crear `repository/IAuditRecommendationRepository.java` con métodos `findByAuditResultAuditId` y `deleteByAuditResultAuditId`.
  - Crear prueba de persistencia `src/test/java/ec/com/technoloqie/auditbot/api/repository/AuditRecommendationRepositoryTest.java` verificando la migración V5, inserción, consulta y eliminación por `auditId`.
  - **Cubre:** R14, R15, R16.

---

## Fase 2 — DTOs y Analizador de Negocio Puro

- [x] **T4 — Crear DTOs de recomendaciones y contexto de hallazgos.**
  - Crear `dto/AuditFindingsContextDto.java` (record: `gapsCount`, `gapQuestions`, `frustrationDetected`, `lostLeadDetected`, `leadScore`, `lostLeadReason`).
  - Crear `dto/RecommendationCandidateDto.java` (record: `type`, `suggestedText`, `originMessageId`).
  - Crear `dto/RecommendationAnalysisResponseDto.java` (record: `recommendations`).
  - Crear `dto/RecommendationItemDto.java` (record: `type`, `suggestedText`, `originMessageId`).
  - Crear `dto/RecommendationEvaluationResultDto.java` (record: `items`).
  - **Cubre:** R8.

- [x] **T5 — Implementar `RecommendationAnalyzer` (lógica de dominio pura).**
  - Crear `analyzer/RecommendationAnalyzer.java` libre de dependencias de Spring, JPA o librerías de IA.
  - Implementar normalización case-insensitive y filtrado al catálogo `RecommendationType` (R10).
  - Implementar saneamiento, trim y descarte de textos vacíos o nulos (R11).
  - Implementar validación referencial lógica de `originMessageId` contra los mensajes de la conversación (R12).
  - Implementar deduplicación por tipo y texto sugerido (R13).
  - **Cubre:** R10, R11, R12, R13, R22.

- [x] **T6 — Crear pruebas unitarias exhaustivas en `RecommendationAnalyzerTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/analyzer/RecommendationAnalyzerTest.java` (JUnit 5 puro sin contexto Spring).
  - Verificar normalización de enums, descarte de tipos inválidos, trim de textos, descarte de textos en blanco, mensajes origen inexistentes seteados a null, deduplicación y respuesta vacía.
  - **Cubre:** R10, R11, R12, R13, R22.

---

## Fase 3 — Integración OpenAI con Spring AI (Ollama compatible)

- [x] **T7 — Crear puerto `IRecommendationAiClient` y adaptador `OpenAiRecommendationAdapter`.**
  - Crear `integration/openai/IRecommendationAiClient.java`.
  - Crear `integration/openai/OpenAiRecommendationAdapter.java` inyectando `ChatClient` y timeout configurable (`auditbot.ai.openai.timeout-seconds`).
  - Formular prompt de sistema especializado para sugerir `NEW_FAQ` o `ALTERNATIVE_FLOW` basado en hallazgos previos y diálogo.
  - Confinar el adaptador y puerto exclusivamente a `integration.openai`.
  - **Cubre:** R6, R7, R8, R9, R23.

- [x] **T8 — Crear pruebas unitarias para `OpenAiRecommendationAdapterTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/integration/openai/OpenAiRecommendationAdapterTest.java`.
  - Probar llamada estructurada exitosa con mock de `ChatClient`.
  - Probar manejo de timeout y fallos de comunicación con propagación controlada de excepción.
  - **Cubre:** R6, R7, R8, R9, R23.

---

## Fase 4 — Servicio de Aplicación, Orquestación y Arquitectura

- [x] **T9 — Implementar `IRecommendationService` y `RecommendationServiceImpl`.**
  - Crear interfaz `service/IRecommendationService.java`.
  - Crear implementación `service/impl/RecommendationServiceImpl.java` con `@Service` y `@Transactional`.
  - Implementar consolidación de hallazgos previos y conversación descartando mensajes `SYSTEM`.
  - Implementar caso de conversación limpia (sin gaps, frustración ni lead perdido) promoviendo directamente a `COMPLETED` sin llamar a la IA.
  - Implementar invocación de IA protegida ante fallos, pasando respuesta al analizador puro.
  - Implementar idempotencia: eliminación previa de recomendaciones del mismo `audit_id`.
  - Implementar persistencia de nuevas recomendaciones y actualización atómica de `AuditResult`: `status = COMPLETED`, `auditedDate = Instant.now()`, `updatedDate = Instant.now()`.
  - Manejar excepciones de IA marcando `status = FAILED`, `errorMessage` con prefijo `[AI_ERROR]` y emitir log estructurado.
  - **Cubre:** R3, R4, R5, R17, R18, R19, R20, R21.

- [x] **T10 — Extender `AuditPipelineOrchestratorImpl` para incorporar la etapa de recomendaciones.**
  - Modificar `AuditPipelineOrchestratorImpl` inyectando `IRecommendationService`.
  - Invocar `recommendationService.generateAndPersistRecommendations(chatId, conversation)` inmediatamente después de la etapa de leads, verificando que no esté en estado `FAILED`.
  - **Cubre:** R1, R2.

- [x] **T11 — Crear pruebas unitarias de servicio y orquestación.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/RecommendationServiceImplTest.java` verificando flujo exitoso a `COMPLETED`, caso limpio sin hallazgos, idempotencia en reintentos y captura de error de IA con estado `FAILED`.
  - Actualizar pruebas de orquestación en `AuditPipelineOrchestratorTest` verificando invocación secuencial de la etapa de recomendaciones y omisión si una etapa previa falló.
  - **Cubre:** R1, R2, R3, R4, R5, R17, R18, R19, R20, R21.

- [x] **T12 — Actualizar y verificar reglas de arquitectura en `ArchitectureRulesTest`.**
  - Actualizar `ArchitectureRulesTest` asegurando que `RecommendationAnalyzer` sea lógica pura (sin dependencias Spring/JPA/AI) y que los clientes de IA residan en `integration.openai`.
  - **Cubre:** R22, R23.

---

## Fase 5 — Verificación Integral

- [x] **T13 — Ejecutar verificación completa `./mvnw clean verify`.**
  - Ejecutar `./mvnw clean verify` con Java 17 y comprobar que el 100% de las pruebas pasen en verde sin regresiones en las historias US-01 a US-06.
  - **Cubre:** R1–R23.
