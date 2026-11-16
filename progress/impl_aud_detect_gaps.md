# Implementación US-03 — Detectar preguntas sin respuesta (`aud_detect_gaps`)

## Resumen

Se implementó la primera etapa del pipeline analítico con IA para la detección, clasificación, agrupación y persistencia de brechas de conocimiento (knowledge gaps) en conversaciones auditadas, siguiendo `specs/003-aud_detect_gaps/{requirements,design,tasks}.md`.

La feature cubre:
1. Reconstrucción de turnos cronológicos de diálogo usuario-asistente descartando mensajes con rol `SYSTEM` y soportando turnos sin respuesta final.
2. Integración con Spring AI utilizando el starter OpenAI (`spring-ai-starter-model-openai`) conectado a la API compatible de Ollama (`/v1`), inyectando `ChatClient` configurado con `OpenAiChatOptions` (`temperature = 0.1`), prompt estructurado y salida JSON (`GapAnalysisResponseDto`).
3. Analizador de negocio puro (`KnowledgeGapAnalyzer`) sin dependencias de frameworks, con normalización léxica, tokenización sin stopwords en español, cálculo de similitud de conjuntos Jaccard y consolidación determinista de paráfrasis seleccionando la pregunta canónica con mayor contenido informativo y el primer `messageId` cronológico.
4. Persistencia relacional de los gaps detectados en la tabla `audit_gap` mediante Flyway (`V2__create_audit_gap.sql`), actualización de `gaps_count` y `updated_date` en `audit_result` manteniendo el estado `PROCESSING`.
5. Manejo controlado de fallos del modelo LLM (timeout / caída de red) actualizando `AuditResult` a `status = FAILED` con mensaje descriptivo truncado a 500 caracteres prefijado con `[AI_ERROR]`, registro estructurado de logs y retorno sin relanzar excepciones para no bloquear la cola AMQP.
6. Aislamiento y pureza arquitectónica verificada con ArchUnit (14 reglas en verde).

Todas las tareas T1–T14 de `specs/003-aud_detect_gaps/tasks.md` fueron completadas con evidencia en verde: `./mvnw clean verify` finaliza con `BUILD SUCCESS` y 124/124 tests pasando al 100%.

---

## Archivos creados

### Producción (`src/main/java/` y `src/main/resources/`)
- `db/migration/V2__create_audit_gap.sql`: script DDL Flyway para la tabla `audit_gap` con clave primaria, clave foránea `fk_audit_gap_audit_result` (`ON DELETE CASCADE`) e índice `idx_audit_gap_audit_id`.
- `ec/com/technoloqie/auditbot/api/model/AuditGap.java`: entidad JPA para representar gaps individuales asociados a `AuditResult`.
- `ec/com/technoloqie/auditbot/api/repository/IAuditGapRepository.java`: repositorio Spring Data JPA con `findByAuditResult_AuditId` y `deleteByAuditResult_AuditId`.
- `ec/com/technoloqie/auditbot/api/dto/ConversationTurnDto.java`: record para representar turnos ordenados de usuario-asistente.
- `ec/com/technoloqie/auditbot/api/dto/KnowledgeGapCandidateDto.java`: record de clasificación de IA con score y motivo.
- `ec/com/technoloqie/auditbot/api/dto/GapAnalysisResponseDto.java`: record de respuesta estructurada devuelta por el cliente de IA.
- `ec/com/technoloqie/auditbot/api/dto/KnowledgeGapItemDto.java`: record de gap consolidado tras deduplicación.
- `ec/com/technoloqie/auditbot/api/analyzer/KnowledgeGapAnalyzer.java`: componente de lógica pura de normalización, tokenización, stopwords y Jaccard.
- `ec/com/technoloqie/auditbot/api/config/OpenAiConfiguration.java`: configuración Spring con beans `ChatClient` y `KnowledgeGapAnalyzer`.
- `ec/com/technoloqie/auditbot/api/integration/openai/IKnowledgeGapAiClient.java`: puerto anti-corrupción para invocación de IA.
- `ec/com/technoloqie/auditbot/api/integration/openai/OpenAiKnowledgeGapAdapter.java`: adaptador que invoca a `ChatClient` con formato delimitado y control de timeout.
- `ec/com/technoloqie/auditbot/api/service/IKnowledgeGapService.java`: puerto del servicio de aplicación de gaps.
- `ec/com/technoloqie/auditbot/api/service/impl/KnowledgeGapServiceImpl.java`: servicio transaccional orquestador de turnos, IA, análisis y persistencia.
- `ec/com/technoloqie/auditbot/api/service/impl/AuditPipelineOrchestratorImpl.java`: orquestador primario `@Primary` de `IAuditPipelineTrigger`.

### Pruebas (`src/test/java/`)
- `ec/com/technoloqie/auditbot/api/repository/AuditGapRepositoryTest.java`: 3 tests de integración JPA con H2/MySQL.
- `ec/com/technoloqie/auditbot/api/analyzer/KnowledgeGapAnalyzerTest.java`: 7 tests unitarios de lógica pura.
- `ec/com/technoloqie/auditbot/api/integration/openai/OpenAiKnowledgeGapAdapterTest.java`: 4 tests unitarios del adaptador con mocks.
- `ec/com/technoloqie/auditbot/api/service/impl/KnowledgeGapServiceImplTest.java`: 5 tests unitarios del servicio de negocio.
- `ec/com/technoloqie/auditbot/api/service/impl/AuditPipelineOrchestratorImplTest.java`: 1 test unitario del orquestador.

---

## Archivos modificados

- `pom.xml`: dependencia activa de `spring-ai-starter-model-openai`.
- `src/main/resources/application.properties`: propiedades Spring AI OpenAI y parámetros de gaps (`min-confidence=0.60`, `similarity-threshold=0.70`, timeout).
- `src/main/java/ec/com/technoloqie/auditbot/api/model/AuditResult.java`: relación `@OneToMany(mappedBy = "auditResult", cascade = CascadeType.ALL)` hacia `AuditGap`.
- `src/main/java/ec/com/technoloqie/auditbot/api/service/IAuditPipelineTrigger.java`: firma enriquecida `trigger(String chatId, AuditConversationDto conversation)`.
- `src/main/java/ec/com/technoloqie/auditbot/api/service/impl/NoOpAuditPipelineTrigger.java`: fallback desacoplado `@ConditionalOnMissingBean(IAuditPipelineTrigger.class)`.
- `src/main/java/ec/com/technoloqie/auditbot/api/service/impl/ConversationAuditIntakeServiceImpl.java`: propagación de `conversation` en la llamada al trigger.
- `src/test/java/ec/com/technoloqie/auditbot/api/ArchitectureRulesTest.java`: reglas ArchUnit ampliadas para permitir `service` → `analyzer`, validar pureza de `analyzer`, confinamiento de Spring AI en `integration.openai` y prohibición de `@Autowired` en atributos.
- `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/ConversationAuditIntakeServiceImplTest.java`: actualización de firmas en tests de admisión.
- `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/NoOpAuditPipelineTriggerTest.java`: actualización de firma en test de trigger no-op.
- `src/test/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/ConversationAuditMessageListenerIntegrationTest.java`: aislamiento con mock del trigger para desacoplar el consumo AMQP de los analizadores posteriores.
- `docs/tech.md` y `docs/structure.md`: actualización documental del stack y estructura de paquetes (`integration.openai`).
- `specs/003-aud_detect_gaps/tasks.md`: tareas T1–T14 marcadas como completadas `[x]`.

---

## Comandos y evidencia

```bash
PROFILE=local JAVA_TOOL_OPTIONS="-Xmx1024m" JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./mvnw clean verify
# Results:
# Tests run: 124, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS (Total time: 06:29 min)
```

---

## Matriz de trazabilidad de requisitos R1–R37

| Requisito | Método(s) de test que lo verifica |
|---|---|
| **R1** | `KnowledgeGapServiceImplTest#shouldExtractTurnsCorrectly` |
| **R2** | `KnowledgeGapServiceImplTest#shouldExtractTurnsCorrectly` |
| **R3** | `KnowledgeGapServiceImplTest#shouldExtractTurnsCorrectly` |
| **R4** | `KnowledgeGapServiceImplTest#shouldExtractTurnsCorrectly` |
| **R5** | `OpenAiKnowledgeGapAdapterTest#shouldInvokeChatClientAndReturnStructuredResponse` |
| **R6** | `OpenAiKnowledgeGapAdapterTest#shouldInvokeChatClientAndReturnStructuredResponse` |
| **R7** | `OpenAiKnowledgeGapAdapterTest#shouldInvokeChatClientAndReturnStructuredResponse` |
| **R8** | `OpenAiKnowledgeGapAdapterTest#shouldReturnEmptyResponseWhenTurnsListIsEmptyOrNull` |
| **R9** | `KnowledgeGapAnalyzerTest#shouldDiscardGapsBelowMinConfidence` |
| **R10** | `KnowledgeGapAnalyzerTest#shouldDiscardGapsBelowMinConfidence` |
| **R11** | `KnowledgeGapAnalyzerTest#shouldNormalizeQuestionTextDeterministically` |
| **R12** | `KnowledgeGapAnalyzerTest#shouldRemoveStopwordsDuringTokenization` |
| **R13** | `KnowledgeGapAnalyzerTest#shouldCalculateCorrectJaccardSimilarity` |
| **R14** | `KnowledgeGapAnalyzerTest#shouldConsolidateSimilarQuestionsIntoSingleGap` |
| **R15** | `KnowledgeGapAnalyzerTest#shouldConsolidateSimilarQuestionsIntoSingleGap` |
| **R16** | `KnowledgeGapAnalyzerTest#shouldConsolidateSimilarQuestionsIntoSingleGap` |
| **R17** | `KnowledgeGapAnalyzerTest#shouldConsolidateSimilarQuestionsIntoSingleGap` |
| **R18** | `KnowledgeGapAnalyzerTest#shouldConsolidateSimilarQuestionsIntoSingleGap` |
| **R19** | `AuditGapRepositoryTest#shouldCascadeDeleteGapsWhenAuditResultIsDeleted` |
| **R20** | `AuditGapRepositoryTest#shouldPersistAndRetrieveAuditGap` |
| **R21** | `AuditGapRepositoryTest#shouldFindByAuditIdAndDeleteByAuditId` |
| **R22** | `KnowledgeGapServiceImplTest#shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing` |
| **R23** | `KnowledgeGapServiceImplTest#shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing` |
| **R24** | `KnowledgeGapServiceImplTest#shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing` |
| **R25** | `KnowledgeGapServiceImplTest#shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing` |
| **R26** | `KnowledgeGapServiceImplTest#shouldHandleZeroGapsDetectedCleanly` |
| **R27** | `KnowledgeGapServiceImplTest#shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing` |
| **R28** | `OpenAiKnowledgeGapAdapterTest#shouldTimeoutWhenModelDoesNotRespondInTime` |
| **R29** | `KnowledgeGapServiceImplTest#shouldMarkAuditAsFailedOnAiClientError` |
| **R30** | `KnowledgeGapServiceImplTest#shouldMarkAuditAsFailedOnAiClientError` |
| **R31** | `KnowledgeGapServiceImplTest#shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing` |
| **R32** | `KnowledgeGapServiceImplTest#shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing` |
| **R33** | `KnowledgeGapServiceImplTest#shouldMarkAuditAsFailedOnAiClientError` |
| **R34** | `ArchitectureRulesTest#shouldKeepKnowledgeGapAnalyzerPure` |
| **R35** | `ArchitectureRulesTest#shouldConfineSpringAiInsideOpenAiIntegrationPackage` |
| **R36** | `ArchitectureRulesTest#shouldEnforceConstructorInjection` |
| **R37** | Suite completa Maven (`BUILD SUCCESS`, 124 tests) |
