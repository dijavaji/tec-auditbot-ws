# Tasks — US-04 Detectar frustración del usuario (`aud_sentiment_analisys`)

> Ejecutar en orden de dependencia. Cada tarea se marca `[x]` solo después de completar su implementación y sus pruebas asociadas.
> Exportar `PROFILE=local` y utilizar Java 17 en todos los comandos Maven.

---

## Fase 1 — Modelo de datos y persistencia

- [x] **T1 — Crear la migración Flyway `V3__create_audit_sentiment.sql`.**
  - Crear `src/main/resources/db/migration/V3__create_audit_sentiment.sql` definiendo la tabla `audit_sentiment`, primary key `sentiment_id`, foreign key `fk_audit_sentiment_audit_result` hacia `audit_result(audit_id)` con `ON DELETE CASCADE` e índice `idx_audit_sentiment_audit_id`.
  - **Cubre:** R20.

- [x] **T2 — Crear entidad `AuditSentiment`, enums de dominio y repositorio `IAuditSentimentRepository`.**
  - Crear enum `model/SentimentLabel.java` (`POSITIVE`, `NEUTRAL`, `NEGATIVE`, `FRUSTRATED`).
  - Crear enum `model/SentimentTrend.java` (`IMPROVING`, `WORSENING`, `STABLE`).
  - Crear entidad `model/AuditSentiment.java` con mapeo JPA (`@Entity`, `@Table`, `@Id`), campos `sentimentId`, `auditResult` (`@ManyToOne`), `messageId`, `label` (`@Enumerated(EnumType.STRING)`), `score` (`BigDecimal`), `sequenceOrder` (`int`), `createdDate` (`Instant`).
  - Crear interfaz `repository/IAuditSentimentRepository.java` extendiendo `JpaRepository<AuditSentiment, String>` con métodos `List<AuditSentiment> findByAuditResult_AuditId(String auditId)` y `void deleteByAuditResult_AuditId(String auditId)`.
  - Mapear en `AuditResult.java` la relación opcional `@OneToMany(mappedBy = "auditResult", cascade = CascadeType.ALL) private List<AuditSentiment> sentiments = new ArrayList<>();`.
  - **Cubre:** R21, R25.

- [x] **T3 — Crear pruebas de integración para el repositorio `AuditSentimentRepositoryTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/repository/AuditSentimentRepositoryTest.java` con `@DataJpaTest` y perfil H2.
  - Verificar inserción correcta, restricción de clave foránea hacia `audit_result`, búsqueda por `auditId`, borrado por `auditId` y eliminación en cascada.
  - **Cubre:** R20, R21, R25.

---

## Fase 2 — DTOs, Enums y Analizador de Negocio Puro

- [x] **T4 — Crear DTOs para clasificación y evaluación de sentimientos.**
  - Crear `dto/SentimentItemClassificationDto.java` (record: `messageId`, `label`, `score`).
  - Crear `dto/SentimentAnalysisResponseDto.java` (record: `List<SentimentItemClassificationDto> classifications`).
  - Crear `dto/ProcessedSentimentDto.java` (record: `messageId`, `label`, `score`, `sequenceOrder`).
  - Crear `dto/SentimentEvaluationResultDto.java` (record: `items`, `frustrationDetected`, `trend`).
  - **Cubre:** R8, R9.

- [x] **T5 — Implementar `SentimentAnalyzer` (lógica de dominio pura).**
  - Crear `analyzer/SentimentAnalyzer.java` libre de dependencias de Spring, JPA, AMQP o Spring AI.
  - Implementar mapeo de polaridad escalar para cada `SentimentLabel`.
  - Implementar cálculo de tendencia emocional comparando la polaridad promedio de dos mitades cronológicas ($\Delta P \ge +0.20 \implies \text{IMPROVING}$, $\le -0.20 \implies \text{WORSENING}$, caso contrario $\text{STABLE}$).
  - Implementar reglas de detección de frustración: pico individual ($\text{score} \ge \theta_{\text{pico}}$), ratio de negatividad ($\ge \theta_{\text{ratio}}$), y secuencia de negativos consecutivos ($\ge K$).
  - **Cubre:** R9, R10, R11, R12, R13, R14, R15, R16, R17, R18, R19, R34.

- [x] **T6 — Crear pruebas unitarias exhaustivas en `SentimentAnalyzerTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/analyzer/SentimentAnalyzerTest.java` (JUnit 5 puro sin contexto Spring).
  - Cubrir: validación de scores fuera de rango, tendencia en conversación con 1 solo mensaje, detección de tendencias `IMPROVING`, `WORSENING` y `STABLE`, activación de frustración por pico, por ratio superado, por consecutivos negativos, y caso de conversación sin frustración.
  - **Cubre:** R9, R10, R11, R12, R13, R14, R15, R16, R17, R18, R19, R34.

---

## Fase 3 — Integración OpenAI con Spring AI (Ollama compatible)

- [ ] **T7 — Crear puerto `ISentimentAiClient` y adaptador `OpenAiSentimentAdapter`.**
  - Crear `integration/openai/ISentimentAiClient.java`.
  - Crear `integration/openai/OpenAiSentimentAdapter.java` inyectando `ChatClient` y timeout configurable (`auditbot.ai.openai.timeout-seconds`).
  - Implementar formulación de prompt estricto del sistema y batching de mensajes de usuario en una sola llamada structured output hacia `SentimentAnalysisResponseDto`.
  - Confinar el uso de Spring AI exclusivamente al paquete `integration.openai`.
  - **Cubre:** R6, R7, R8, R29, R35.

- [ ] **T8 — Crear pruebas unitarias para el adaptador en `OpenAiSentimentAdapterTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/integration/openai/OpenAiSentimentAdapterTest.java` con Mockito.
  - Mockear el cliente `ChatClient` y verificar la invocación correcta del prompt, deserialización del JSON de salida y captura de timeouts.
  - **Cubre:** R6, R7, R8, R29.

---

## Fase 4 — Servicio de Aplicación y Orquestación del Pipeline

- [ ] **T9 — Crear interfaz `ISentimentAnalysisService` e implementar `SentimentAnalysisServiceImpl`.**
  - Crear `service/ISentimentAnalysisService.java`.
  - Crear `service/impl/SentimentAnalysisServiceImpl.java` anotado con `@Service` y `@Transactional`.
  - Implementar extracción cronológica de mensajes con rol `USER`, exclusión de `SYSTEM`/`ASSISTANT`, y numeración secuencial (`sequenceOrder`).
  - Implementar manejo de conversación sin mensajes de usuario (`frustrationDetected = false`, `status = PROCESSING`).
  - Implementar invocación a `ISentimentAiClient`, pase al analizador puro `SentimentAnalyzer`, limpieza de registros previos (`deleteByAuditResult_AuditId`) y persistencia en lote de `AuditSentiment`.
  - Implementar actualización de `frustrationDetected`, `updatedDate` en `AuditResult` manteniendo `status = PROCESSING`.
  - Implementar manejo de fallos: actualización de `AuditResult` a `status = FAILED`, error truncado a 500 chars con prefijo `[AI_ERROR]`, y log estructurado de etapa.
  - **Cubre:** R1, R2, R3, R4, R5, R22, R23, R24, R25, R26, R30, R31, R32, R33, R36.

- [ ] **T10 — Crear pruebas unitarias en `SentimentAnalysisServiceImplTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/SentimentAnalysisServiceImplTest.java` con Mockito.
  - Verificar: filtrado y ordenación de mensajes de usuario, asignación correlativa de `sequenceOrder`, conversación vacía, persistencia de entidades, actualización de campos en `AuditResult`, idempotencia en reintentos, marcado en `FAILED` ante excepción del cliente LLM y ausencia de texto confidencial en logs.
  - **Cubre:** R1, R2, R3, R4, R5, R22, R23, R24, R25, R26, R30, R31, R32, R33.

- [ ] **T11 — Conectar la etapa en `AuditPipelineOrchestratorImpl` y crear pruebas de orquestación.**
  - Modificar `service/impl/AuditPipelineOrchestratorImpl.java` para inyectar `ISentimentAnalysisService` e invocarlo tras `knowledgeGapService.analyzeAndPersistGaps` validando que la auditoría no se encuentre en `FAILED`.
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/AuditPipelineOrchestratorImplTest.java` para verificar la ejecución secuencial gaps $\rightarrow$ sentimientos y la detención si gaps falla.
  - **Cubre:** R27, R28.

---

## Fase 5 — Configuración, Arquitectura y Verificación End-to-End

- [ ] **T12 — Incorporar propiedades de configuración en `application.properties`.**
  - Agregar en `src/main/resources/application.properties` las claves: `auditbot.ai.sentiment.frustration-score-threshold=0.70`, `auditbot.ai.sentiment.negative-ratio-threshold=0.40`, `auditbot.ai.sentiment.consecutive-negative-threshold=2`.
  - **Cubre:** R16, R17, R18, R29.

- [ ] **T13 — Actualizar y verificar reglas de arquitectura en `ArchitectureRulesTest`.**
  - Asegurar que `shouldKeepKnowledgeGapAnalyzerPure` se generalice a `shouldKeepAnalyzersPure` cubriendo a `SentimentAnalyzer`.
  - Ejecutar `ArchitectureRulesTest` para corroborar límites arquitectónicos y pureza de analizadores.
  - **Cubre:** R34, R35, R36.

- [ ] **T14 — Verificación completa del build y test suite.**
  - Ejecutar `./mvnw clean verify` asegurando código de salida 0, 100% pruebas unitarias, de integración y de arquitectura en verde.
  - **Cubre:** R37.
