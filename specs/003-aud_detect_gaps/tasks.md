# Tasks — US-03 Detectar preguntas sin respuesta (`aud_detect_gaps`)

> Ejecutar en orden de dependencia. Cada tarea se marca `[x]` solo después de completar su implementación y sus pruebas asociadas.
> Exportar `PROFILE=local` en todos los comandos Maven.

## Fase 1 — Modelo de datos y persistencia

- [x] **T1 — Crear la migración Flyway `V2__create_audit_gap.sql`.**
  - Crear `src/main/resources/db/migration/V2__create_audit_gap.sql` con la definición de la tabla `audit_gap`, primary key `gap_id`, foreign key `fk_audit_gap_audit_result` con `ON DELETE CASCADE` hacia `audit_result(audit_id)` e índice `idx_audit_gap_audit_id`.
  - **Cubre:** R19.

- [x] **T2 — Crear entidad `AuditGap`, mapeo relacional y repositorio `IAuditGapRepository`.**
  - Crear `model/AuditGap.java` con anotaciones JPA (`@Entity`, `@Table`, `@Id`), campos `gapId`, `auditResult` (`@ManyToOne`), `messageId`, `questionText`, `confidenceScore` (`BigDecimal`), `createdDate` (`Instant`).
  - Crear `repository/IAuditGapRepository.java` extendiendo `JpaRepository<AuditGap, String>` con `List<AuditGap> findByAuditResult_AuditId(String auditId)` y `void deleteByAuditResult_AuditId(String auditId)`.
  - Opcionalmente en `AuditResult.java` mapear `@OneToMany(mappedBy = "auditResult", cascade = CascadeType.ALL)` defensivo.
  - **Cubre:** R20, R21.

- [x] **T3 — Crear pruebas de integración para el repositorio `AuditGapRepositoryTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/repository/AuditGapRepositoryTest.java` con `@DataJpaTest` y H2 en modo MySQL.
  - Verificar inserción, clave foránea hacia `audit_result`, búsqueda por `auditId`, cascada de borrado y eliminación por `auditId`.
  - **Cubre:** R19, R20, R21.

---

## Fase 2 — DTOs y Analizador de Negocio Puro

- [x] **T4 — Crear DTOs para turnos de conversación y gaps.**
  - Crear `dto/ConversationTurnDto.java` (record: `userMessageId`, `userQuestion`, `assistantMessageId`, `assistantResponse`).
  - Crear `dto/KnowledgeGapCandidateDto.java` (record: `messageId`, `questionText`, `isGap`, `confidenceScore`, `reason`).
  - Crear `dto/GapAnalysisResponseDto.java` (record: `List<KnowledgeGapCandidateDto> classifications`).
  - Crear `dto/KnowledgeGapItemDto.java` (record: `messageId`, `questionText`, `confidenceScore`, `occurrencesCount`).
  - **Cubre:** R3, R7, R16, R17.

- [x] **T5 — Implementar `KnowledgeGapAnalyzer` (lógica de dominio pura).**
  - Crear `analyzer/KnowledgeGapAnalyzer.java` libre de dependencias de Spring, JPA, AMQP y AI.
  - Implementar normalización léxica (minúsculas, diacríticos, puntuación).
  - Implementar tokenización y remoción de stopwords en español.
  - Implementar cálculo de similitud Jaccard $\frac{|A \cap B|}{|A \cup B|}$.
  - Implementar agrupación greedy determinista, selección de primer `messageId`, pregunta canónica más informativa y score máximo.
  - **Cubre:** R9, R10, R11, R12, R13, R14, R15, R16, R17, R18, R34.

- [x] **T6 — Crear pruebas unitarias exhaustivas en `KnowledgeGapAnalyzerTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/analyzer/KnowledgeGapAnalyzerTest.java` (JUnit 5 puro sin contexto Spring).
  - Cubrir: normalización, stopwords, cálculo exacto de Jaccard, descarte por confianza inferior a `min-confidence`, agrupación por similitud $\ge 0.70$, selección de pregunta canónica, preservación del primer `messageId`, y listas vacías.
  - **Cubre:** R9, R10, R11, R12, R13, R14, R15, R16, R17, R18, R34.

---

## Fase 3 — Integración OpenAI con Spring AI (Ollama compatible)

- [x] **T7 — Crear puerto `IKnowledgeGapAiClient` y adaptador `OpenAiKnowledgeGapAdapter`.**
  - Crear `integration/openai/IKnowledgeGapAiClient.java`.
  - Crear `integration/openai/OpenAiKnowledgeGapAdapter.java` inyectando `ChatClient.Builder` y timeout configurable (`auditbot.ai.openai.timeout-seconds`).
  - Configurar system prompt estricto y llamada con structured output a `GapAnalysisResponseDto`.
  - Confinar los componentes de Spring AI exclusivamente a `ec.com.technoloqie.auditbot.api.integration.openai`.
  - **Cubre:** R5, R6, R7, R8, R28, R35, R36.

- [x] **T8 — Crear pruebas unitarias para el adaptador en `OpenAiKnowledgeGapAdapterTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/integration/openai/OpenAiKnowledgeGapAdapterTest.java` con Mockito.
  - Mockear el comportamiento de `ChatClient` y `ChatClient.Builder`.
  - Verificar: inclusión del system prompt, mapeo de turnos de diálogo, deserialización correcta de JSON estructurado, descarte de turnos `isGap = false`, y manejo de timeout.
  - **Cubre:** R5, R6, R7, R8, R28.

---

## Fase 4 — Orquestación, Servicio y Refinamiento del Trigger

- [ ] **T9 — Refinar la interfaz y fallback de `IAuditPipelineTrigger`.**
  - Actualizar `service/IAuditPipelineTrigger.java` a la firma: `void trigger(String chatId, AuditConversationDto conversation)`.
  - Actualizar `service/impl/NoOpAuditPipelineTrigger.java` con la nueva firma y marcarlo con `@ConditionalOnMissingBean(IAuditPipelineTrigger.class)` o prioridad inferior.
  - Actualizar `service/impl/ConversationAuditIntakeServiceImpl.java` para pasar `request.conversation()` en la invocación de `trigger`.
  - Actualizar `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/ConversationAuditIntakeServiceImplTest.java` y `NoOpAuditPipelineTriggerTest.java`.
  - **Cubre:** R1, R3, R4.

- [ ] **T10 — Crear servicio `IKnowledgeGapService` e implementación transaccional `KnowledgeGapServiceImpl`.**
  - Crear `service/IKnowledgeGapService.java` con `void analyzeAndPersistGaps(String chatId, AuditConversationDto conversation)`.
  - Crear `service/impl/KnowledgeGapServiceImpl.java` con `@Service` y `@Transactional`.
  - Implementar reconstrucción de turnos cronológicos emparejando `USER` con `ASSISTANT`, descartando `SYSTEM` y soportando turnos sin respuesta final.
  - Invocar `IKnowledgeGapAiClient` y luego `KnowledgeGapAnalyzer`.
  - Manejar reintentos (`x-audit-retry = true`): borrar gaps previos del `auditId`.
  - Persistir gaps consolidados en `IAuditGapRepository`.
  - Actualizar `gapsCount`, `updatedDate` en `AuditResult` manteniendo `status = PROCESSING`.
  - Manejo de caso con 0 gaps detectados (`gapsCount = 0`).
  - Manejo de fallo del LLM: abortar transacción de gaps, marcar `status = FAILED`, registrar `errorMessage` truncado a 500 caracteres sin datos sensibles.
  - Emitir log estructurado con `etapa=GAP_DETECTION`.
  - **Cubre:** R1, R2, R3, R4, R22, R23, R24, R25, R26, R27, R29, R30, R31, R32, R33, R36.

- [ ] **T11 — Implementar orquestador del pipeline `AuditPipelineOrchestratorImpl`.**
  - Crear `service/impl/AuditPipelineOrchestratorImpl.java` implementando `IAuditPipelineTrigger` con `@Service` y `@Primary`.
  - Invocar `IKnowledgeGapService.analyzeAndPersistGaps(chatId, conversation)`.
  - **Cubre:** R1, R31.

- [ ] **T12 — Crear pruebas unitarias completas en `KnowledgeGapServiceImplTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/KnowledgeGapServiceImplTest.java` con Mockito y reloj controlado.
  - Probar: extracción de turnos en orden cronológico, filtrado de mensajes `SYSTEM`, turnos de usuario sin respuesta, persistencia de gaps y actualización de `AuditResult` en `PROCESSING`, caso con 0 gaps detectados, reemplazo de gaps en reintento, captura de excepción del cliente LLM con actualización a `FAILED` y `errorMessage` truncado, y emisión de log estructurado sin PII.
  - **Cubre:** R1, R2, R3, R4, R22, R23, R24, R25, R26, R27, R29, R30, R31, R32, R33.

---

## Fase 5 — Reglas de Arquitectura y Verificación Integral

- [ ] **T13 — Actualizar y extender `ArchitectureRulesTest` con ArchUnit.**
  - En `src/test/java/ec/com/technoloqie/auditbot/api/ArchitectureRulesTest.java`:
    - Ajustar `shouldKeepConsumptionIndependentFromAnalyzers` para que la restricción aplique a `integration.rabbitmq..` y al flujo de admisión, permitiendo que `service.impl` invoque a `analyzer..`.
    - Agregar regla `shouldKeepKnowledgeGapAnalyzerPure`: verificar que `analyzer..` no importe paquetes de Spring, JPA, AMQP ni Spring AI.
    - Agregar regla `shouldConfineSpringAiInsideOpenAiIntegrationPackage`: verificar que clases fuera de `integration.openai..` y `config..` no dependan de `org.springframework.ai..`.
    - Verificar inyección por constructor general en el proyecto.
  - **Cubre:** R34, R35, R36.

- [ ] **T14 — Ejecución de verificación completa del build (`./mvnw clean verify`).**
  - Ejecutar `PROFILE=local JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./mvnw clean verify`.
  - Confirmar `BUILD SUCCESS` con 100% de pruebas unitarias, de integración y de arquitectura pasando en verde.
  - **Cubre:** R37.
