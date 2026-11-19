# Implementación US-04 — Detectar frustración del usuario (`aud_sentiment_analisys`)

## Resumen

Se implementó la segunda etapa del pipeline analítico con IA para la clasificación de sentimiento en mensajes de usuario, cálculo de la tendencia emocional durante la conversación y determinación de la bandera de frustración (`frustration_detected`), siguiendo `specs/004-aud_sentiment_analisys/{requirements,design,tasks}.md`.

La feature cubre:
1. Extracción y ordenación cronológica estricta de mensajes con rol `USER`, descartando mensajes `SYSTEM` y `ASSISTANT`, asignando numeración secuencial correlativa (`sequenceOrder = 1, 2, ...`).
2. Integración en lote con Spring AI utilizando el starter OpenAI (`spring-ai-starter-model-openai`) conectado a la API compatible de Ollama (`/v1`), con system prompt estricto de 4 categorías (`POSITIVE`, `NEUTRAL`, `NEGATIVE`, `FRUSTRATED`), formato delimitado `[ID: ...] Mensaje: "..."` y autocompletado determinista con fallback `NEUTRAL` (score `0.000`) para garantizar la cobertura íntegra de la conversación.
3. Analizador de negocio puro (`SentimentAnalyzer`) sin dependencias de frameworks, con asignación escalar de polaridad, partición determinista de mitades cronológicas para el cálculo de $\Delta P$, categorización de tendencia (`IMPROVING`, `WORSENING`, `STABLE`) y evaluación disyuntiva de frustración basada en 3 criterios (pico individual $\ge 0.70$, ratio de negatividad acumulada $\ge 40\%$ y secuencia de negativos consecutivos $\ge 2$).
4. Persistencia relacional de los sentimientos en la tabla `audit_sentiment` mediante Flyway (`V3__create_audit_sentiment.sql`), actualización de `frustration_detected` y `updated_date` en `audit_result` manteniendo el estado `PROCESSING`.
5. Orquestación secuencial en `AuditPipelineOrchestratorImpl`: brechas de conocimiento (`US-03`) $\rightarrow$ verificación de estado no fallido $\rightarrow$ análisis de sentimiento (`US-04`).
6. Manejo controlado de fallos del modelo LLM (timeout / caída de red) actualizando `AuditResult` a `status = FAILED` con mensaje descriptivo truncado a 500 caracteres prefijado con `[AI_ERROR]`, log estructurado de etapa y detención segura del pipeline.
7. Aislamiento y pureza arquitectónica verificada con ArchUnit (15 reglas en verde).

Todas las tareas T1–T14 de `specs/004-aud_sentiment_analisys/tasks.md` fueron completadas con evidencia en verde: `./mvnw clean verify` finaliza con `BUILD SUCCESS` y 157/157 tests pasando al 100%.

---

## Archivos creados

### Producción (`src/main/java/` y `src/main/resources/`)
- `db/migration/V3__create_audit_sentiment.sql`: script DDL Flyway para la tabla `audit_sentiment` con clave primaria, clave foránea `fk_audit_sentiment_audit_result` (`ON DELETE CASCADE`) e índice `idx_audit_sentiment_audit_id`.
- `ec/com/technoloqie/auditbot/api/model/SentimentLabel.java`: enum de catálogo de sentimientos (`POSITIVE`, `NEUTRAL`, `NEGATIVE`, `FRUSTRATED`).
- `ec/com/technoloqie/auditbot/api/model/SentimentTrend.java`: enum de tendencia emocional (`IMPROVING`, `WORSENING`, `STABLE`).
- `ec/com/technoloqie/auditbot/api/model/AuditSentiment.java`: entidad JPA para representar sentimientos individuales asociados a `AuditResult`.
- `ec/com/technoloqie/auditbot/api/repository/IAuditSentimentRepository.java`: repositorio Spring Data JPA con `findByAuditResult_AuditId` y `deleteByAuditResult_AuditId`.
- `ec/com/technoloqie/auditbot/api/dto/SentimentItemClassificationDto.java`: record de clasificación devuelta por el modelo de IA.
- `ec/com/technoloqie/auditbot/api/dto/SentimentAnalysisResponseDto.java`: record de respuesta estructurada devuelta por el cliente de IA.
- `ec/com/technoloqie/auditbot/api/dto/ProcessedSentimentDto.java`: record de sentimiento procesado con `sequenceOrder`.
- `ec/com/technoloqie/auditbot/api/dto/SentimentEvaluationResultDto.java`: record con el resultado integral del análisis.
- `ec/com/technoloqie/auditbot/api/analyzer/SentimentAnalyzer.java`: componente de lógica pura para cálculo de tendencia y evaluación de frustración.
- `ec/com/technoloqie/auditbot/api/integration/openai/ISentimentAiClient.java`: puerto anti-corrupción para invocación de IA.
- `ec/com/technoloqie/auditbot/api/integration/openai/OpenAiSentimentAdapter.java`: adaptador que invoca a `ChatClient` con formato batch delimitado y control de timeout.
- `ec/com/technoloqie/auditbot/api/service/ISentimentAnalysisService.java`: puerto del servicio de aplicación de sentimientos.
- `ec/com/technoloqie/auditbot/api/service/impl/SentimentAnalysisServiceImpl.java`: servicio transaccional orquestador de mensajes, IA, análisis y persistencia.

### Pruebas (`src/test/java/`)
- `ec/com/technoloqie/auditbot/api/repository/AuditSentimentRepositoryTest.java`: 3 tests de integración JPA con H2/MySQL.
- `ec/com/technoloqie/auditbot/api/analyzer/SentimentAnalyzerTest.java`: 12 tests unitarios de lógica pura.
- `ec/com/technoloqie/auditbot/api/integration/openai/OpenAiSentimentAdapterTest.java`: 7 tests unitarios del adaptador con mocks.
- `ec/com/technoloqie/auditbot/api/service/impl/SentimentAnalysisServiceImplTest.java`: 9 tests unitarios del servicio de negocio.
- `ec/com/technoloqie/auditbot/api/service/impl/AuditPipelineOrchestratorImplTest.java`: 2 tests unitarios de la orquestación secuencial.

---

## Archivos modificados

- `src/main/resources/application.properties`: propiedades de sentimiento (`frustration-score-threshold=0.70`, `negative-ratio-threshold=0.40`, `consecutive-negative-threshold=2`).
- `src/main/java/ec/com/technoloqie/auditbot/api/model/AuditResult.java`: relación `@OneToMany(mappedBy = "auditResult", cascade = CascadeType.ALL)` hacia `AuditSentiment`.
- `src/main/java/ec/com/technoloqie/auditbot/api/config/OpenAiConfiguration.java`: declaración de bean para `SentimentAnalyzer`.
- `src/main/java/ec/com/technoloqie/auditbot/api/service/impl/AuditPipelineOrchestratorImpl.java`: orquestación de `ISentimentAnalysisService` tras `knowledgeGapService`.
- `src/test/java/ec/com/technoloqie/auditbot/api/ArchitectureRulesTest.java`: adición de regla `shouldKeepSentimentAnalyzerPure`.
- `src/test/java/ec/com/technoloqie/auditbot/api/TecAuditbotWsApplicationTests.java`: configuración de `@TestPropertySource` con H2 embebido.

---

## Matriz de trazabilidad

| Requisito | Clase y método de prueba | Estado |
|---|---|---|
| **R1** | `SentimentAnalysisServiceImplTest#shouldFilterOnlyUserMessages` | PASS |
| **R2** | `SentimentAnalysisServiceImplTest#shouldExcludeSystemAndAssistantRoles` | PASS |
| **R3** | `SentimentAnalysisServiceImplTest#shouldOrderUserMessagesChronologically` | PASS |
| **R4** | `SentimentAnalysisServiceImplTest#shouldPersistAllSentimentRecords` | PASS |
| **R5** | `SentimentAnalysisServiceImplTest#shouldHandleConversationWithoutUserMessages` | PASS |
| **R6** | `OpenAiSentimentAdapterTest#shouldSendUserMessagesInSingleBatchRequest` | PASS |
| **R7** | `OpenAiSentimentAdapterTest#shouldIncludeStrictSentimentSystemPrompt` | PASS |
| **R8** | `OpenAiSentimentAdapterTest#shouldDeserializeStructuredOutput` | PASS |
| **R9** | `SentimentAnalyzerTest#shouldValidateConfidenceScoreWithinRange` | PASS |
| **R10** | `SentimentAnalyzerTest#shouldMapLabelsToScalarPolarity` | PASS |
| **R11** | `SentimentAnalyzerTest#shouldSetStableTrendForSingleMessageConversation` | PASS |
| **R12** | `SentimentAnalyzerTest#shouldCalculatePolarityDeltaBetweenHalves` | PASS |
| **R13** | `SentimentAnalyzerTest#shouldDetectImprovingTrendWhenDeltaIsPositiveAndSignificant` | PASS |
| **R14** | `SentimentAnalyzerTest#shouldDetectWorseningTrendWhenDeltaIsNegativeAndSignificant` | PASS |
| **R15** | `SentimentAnalyzerTest#shouldDetectStableTrendWhenDeltaIsWithinThreshold` | PASS |
| **R16** | `SentimentAnalyzerTest#shouldDetectFrustrationOnSingleHighConfidencePeak` | PASS |
| **R17** | `SentimentAnalyzerTest#shouldDetectFrustrationWhenNegativeRatioExceedsThreshold` | PASS |
| **R18** | `SentimentAnalyzerTest#shouldDetectFrustrationOnConsecutiveNegativeMessages` | PASS |
| **R19** | `SentimentAnalyzerTest#shouldNotDetectFrustrationWhenCriteriaAreNotMet` | PASS |
| **R20** | `AuditSentimentRepositoryTest#shouldCascadeDeleteSentimentsWhenAuditResultIsDeleted` | PASS |
| **R21** | `AuditSentimentRepositoryTest#shouldPersistAndRetrieveAuditSentiment` | PASS |
| **R22** | `SentimentAnalysisServiceImplTest#shouldPersistAllSentimentRecords` | PASS |
| **R23** | `SentimentAnalysisServiceImplTest#shouldPersistAllSentimentRecords` | PASS |
| **R24** | `SentimentAnalysisServiceImplTest#shouldPersistAllSentimentRecords` | PASS |
| **R25** | `AuditSentimentRepositoryTest#shouldFindByAuditIdAndDeleteByAuditId` | PASS |
| **R26** | `SentimentAnalysisServiceImplTest#shouldPersistAllSentimentRecords` | PASS |
| **R27** | `AuditPipelineOrchestratorImplTest#shouldTriggerSentimentAnalysisAfterSuccessfulGaps` | PASS |
| **R28** | `AuditPipelineOrchestratorImplTest#shouldNotTriggerSentimentAnalysisIfGapsFailed` | PASS |
| **R29** | `OpenAiSentimentAdapterTest#shouldEnforceConfiguredTimeout` | PASS |
| **R30** | `SentimentAnalysisServiceImplTest#shouldMarkAuditResultAsFailedOnAiError` | PASS |
| **R31** | `SentimentAnalysisServiceImplTest#shouldTruncateErrorMessageTo500Characters` | PASS |
| **R32** | `SentimentAnalysisServiceImplTest#shouldPersistAllSentimentRecords` | PASS |
| **R33** | `SentimentAnalysisServiceImplTest#shouldMarkAuditResultAsFailedOnAiError` | PASS |
| **R34** | `ArchitectureRulesTest#shouldKeepSentimentAnalyzerPure` | PASS |
| **R35** | `ArchitectureRulesTest#shouldConfineSpringAiInsideOpenAiIntegrationPackage` | PASS |
| **R36** | `ArchitectureRulesTest#shouldEnforceConstructorInjection` | PASS |
| **R37** | Suite Maven `./mvnw clean verify` (157 tests, 0 fallos) | PASS |
