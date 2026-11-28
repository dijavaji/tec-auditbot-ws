# Resumen de Implementación — US-07: Generar recomendaciones de mejora (`aud_automatic_upturn`)

- **Feature:** US-07 — `aud_automatic_upturn`
- **Spec:** `specs/007-aud_automatic_upturn/{requirements,design,tasks}.md`
- **Estado final:** `done`
- **Resultado de verificación:** `./mvnw test` y `./mvnw package` finalizan con `BUILD SUCCESS`, **225/225 tests en verde (0 fallos, 0 errores, 0 omitidos)**.

---

## 1. Matriz de trazabilidad Requirements (EARS) ↔ Tests

| Requisito | Descripción | Prueba de verificación | Estado |
|---|---|---|---|
| **R1** | Invocación secuencial en el orquestador tras etapa de leads | `AuditPipelineOrchestratorImplTest#shouldTriggerAllStagesSequentiallyWhenSuccessful` | ✅ VERDE |
| **R2** | Omisión de recomendaciones ante fallo en etapas previas | `AuditPipelineOrchestratorImplTest#shouldNotTriggerRecommendationsIfLeadsFailed`, `#shouldNotTriggerSentimentOrLeadsOrRecommendationsIfGapsFailed`, `#shouldNotTriggerLeadsOrRecommendationsIfSentimentFailed` | ✅ VERDE |
| **R3** | Consolidación de hallazgos previos y contexto conversacional | `RecommendationServiceImplTest#shouldConsolidatePreviousFindingsAndConversation` | ✅ VERDE |
| **R4** | Exclusión de mensajes de sistema (rol `SYSTEM`) | `RecommendationServiceImplTest#shouldFilterOutSystemMessages` | ✅ VERDE |
| **R5** | Conversación limpia sin hallazgos adversos finaliza en COMPLETED sin llamar a IA | `RecommendationServiceImplTest#shouldCompleteWithoutAiCallWhenNoFindingsExist` | ✅ VERDE |
| **R6** | Invocación en llamada estructurada única con diálogo y hallazgos | `OpenAiRecommendationAdapterTest#shouldSendFindingsAndDialogueInSingleCall` | ✅ VERDE |
| **R7** | Prompt de sistema estricto con tipos `NEW_FAQ` y `ALTERNATIVE_FLOW` | `OpenAiRecommendationAdapterTest#shouldApplyStrictRecommendationPrompt` | ✅ VERDE |
| **R8** | Deserialización estructurada de candidatos de recomendación | `OpenAiRecommendationAdapterTest#shouldDeserializeStructuredRecommendationResponse` | ✅ VERDE |
| **R9** | Timeout configurable de la llamada a IA (`auditbot.ai.openai.timeout-seconds`) | `OpenAiRecommendationAdapterTest#shouldTimeoutWhenConfiguredTimeExceeded` | ✅ VERDE |
| **R10** | Normalización y filtrado case-insensitive del tipo de recomendación | `RecommendationAnalyzerTest#shouldNormalizeAndFilterRecommendationTypes` | ✅ VERDE |
| **R11** | Saneamiento y validación de texto sugerido (trim y descarte de vacíos) | `RecommendationAnalyzerTest#shouldRejectBlankOrNullSuggestedText` | ✅ VERDE |
| **R12** | Validación de referencia de mensaje origen contra la conversación | `RecommendationAnalyzerTest#shouldSetOriginMessageIdToNullWhenNotFoundInConversation` | ✅ VERDE |
| **R13** | Deduplicación de recomendaciones idénticas | `RecommendationAnalyzerTest#shouldDeduplicateIdenticalRecommendations` | ✅ VERDE |
| **R14** | Migración Flyway `V5__create_audit_recommendation.sql` | `AuditRecommendationRepositoryTest#shouldPersistAndRetrieveRecommendationEntity` | ✅ VERDE |
| **R15** | Mapeo JPA de la entidad `AuditRecommendation` | `AuditRecommendationRepositoryTest#shouldPersistAndRetrieveRecommendationEntity` | ✅ VERDE |
| **R16** | Mapeo de relación `@OneToMany` en `AuditResult` con cascada | `AuditRecommendationRepositoryTest#shouldCascadeRecommendationsFromAuditResult` | ✅ VERDE |
| **R17** | Persistencia de recomendaciones generadas en base de datos | `RecommendationServiceImplTest#shouldPersistAllValidRecommendations` | ✅ VERDE |
| **R18** | Transición a estado final `COMPLETED` y registro de `audited_date` | `RecommendationServiceImplTest#shouldPersistAllValidRecommendations`, `#shouldCompleteWithoutAiCallWhenNoFindingsExist` | ✅ VERDE |
| **R19** | Idempotencia y limpieza previa en reintentos | `AuditRecommendationRepositoryTest#shouldFindByAuditIdAndDeleteByAuditId`, `RecommendationServiceImplTest#shouldDeletePreviousRecommendationsOnRetry` | ✅ VERDE |
| **R20** | Manejo controlado de fallos en IA marcando `status = FAILED` con `[AI_ERROR]` | `RecommendationServiceImplTest#shouldMarkAuditFailedOnAiError` | ✅ VERDE |
| **R21** | Emisión de log estructurado con métricas de duración y resultados | `RecommendationServiceImplTest#shouldPersistAllValidRecommendations`, `#shouldCompleteWithoutAiCallWhenNoFindingsExist` | ✅ VERDE |
| **R22** | Pureza de dominio de `RecommendationAnalyzer` (libre de frameworks) | `ArchitectureRulesTest#shouldKeepRecommendationAnalyzerPure`, `RecommendationAnalyzerTest` | ✅ VERDE |
| **R23** | Confinamiento de Spring AI en paquete `integration.openai` | `ArchitectureRulesTest#shouldConfineSpringAiInsideOpenAiIntegrationPackage` | ✅ VERDE |

---

## 2. Archivos modificados y creados

### Código de producción (`src/main/`)
- `resources/db/migration/V5__create_audit_recommendation.sql`: Migración Flyway para tabla `audit_recommendation`.
- `model/RecommendationType.java`: Enum para catálogo de recomendaciones (`NEW_FAQ`, `ALTERNATIVE_FLOW`).
- `model/AuditRecommendation.java`: Entidad JPA para persistencia de recomendaciones.
- `model/AuditResult.java`: Relación `@OneToMany` hacia `AuditRecommendation`.
- `repository/IAuditRecommendationRepository.java`: Repositorio Spring Data JPA.
- `dto/AuditFindingsContextDto.java`: Record DTO para consolidación de hallazgos previos.
- `dto/RecommendationCandidateDto.java`: Record DTO para candidato individual retornado por IA.
- `dto/RecommendationAnalysisResponseDto.java`: Record DTO contenedor de candidatos.
- `dto/RecommendationItemDto.java`: Record DTO para recomendación validada y tipificada.
- `dto/RecommendationEvaluationResultDto.java`: Record DTO con lista de recomendaciones procesadas.
- `analyzer/RecommendationAnalyzer.java`: Analizador puro sin dependencias de frameworks.
- `config/OpenAiConfiguration.java`: Registro de `@Bean RecommendationAnalyzer`.
- `integration/openai/IRecommendationAiClient.java`: Puerto anti-corrupción para recomendaciones.
- `integration/openai/OpenAiRecommendationAdapter.java`: Adaptador con Spring AI `ChatClient`.
- `service/IRecommendationService.java`: Interfaz de servicio de recomendaciones.
- `service/impl/RecommendationServiceImpl.java`: Implementación transaccional y transición a `COMPLETED`.
- `service/impl/AuditPipelineOrchestratorImpl.java`: Invocación secuencial de etapa 4 en el pipeline.

### Código de pruebas (`src/test/`)
- `repository/AuditRecommendationRepositoryTest.java`: Pruebas de integración JPA con H2 en memoria.
- `analyzer/RecommendationAnalyzerTest.java`: Pruebas unitarias de lógica pura.
- `integration/openai/OpenAiRecommendationAdapterTest.java`: Pruebas de adaptador y prompt con mock `ChatClient`.
- `service/impl/RecommendationServiceImplTest.java`: Pruebas unitarias del servicio transaccional.
- `service/impl/AuditPipelineOrchestratorImplTest.java`: Pruebas de orquestación de la etapa 4.
- `ArchitectureRulesTest.java`: Regla ArchUnit `shouldKeepRecommendationAnalyzerPure` (18 reglas en verde).
