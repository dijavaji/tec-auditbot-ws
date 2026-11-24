# Resumen de Implementación — US-05: Identificar leads no calificados (`aud_detect_leads`)

- **Feature:** US-05 — `aud_detect_leads`
- **Spec:** `specs/005-aud_detect_leads/{requirements,design,tasks}.md`
- **Estado final:** `done`
- **Resultado de verificación:** `./mvnw test` y `./mvnw clean verify` finalizan con `BUILD SUCCESS`, **186/186 tests en verde (0 fallos, 0 errores, 0 omitidos)**.

---

## 1. Matriz de trazabilidad Requirements (EARS) ↔ Tests

| Requisito | Descripción | Prueba de verificación | Estado |
|---|---|---|---|
| **R1** | Consolidar contexto cronológico de mensajes evaluables (USER y ASSISTANT) | `LeadDetectionServiceImplTest#shouldProvideOrderedConversationContext` | ✅ VERDE |
| **R2** | Excluir mensajes de rol SYSTEM de la llamada de evaluación comercial | `LeadDetectionServiceImplTest#shouldFilterOutSystemMessages` | ✅ VERDE |
| **R3** | Conversación sin mensajes de usuario persiste valores neutros y omite IA | `LeadDetectionServiceImplTest#shouldHandleConversationWithoutUserMessages` | ✅ VERDE |
| **R4** | Invocación en lote único por conversación enviando diálogo completo | `OpenAiLeadDetectionAdapterTest#shouldSendConversationInSingleCall` | ✅ VERDE |
| **R5** | Aplicación de system prompt estricto con los cuatro campos comerciales | `OpenAiLeadDetectionAdapterTest#shouldApplyStrictCommercialPrompt` | ✅ VERDE |
| **R6** | Deserialización estructurada a `LeadAnalysisResponseDto` | `OpenAiLeadDetectionAdapterTest#shouldDeserializeStructuredLeadResponse` | ✅ VERDE |
| **R7** | Control de timeout configurable (`auditbot.ai.openai.timeout-seconds`) | `OpenAiLeadDetectionAdapterTest#shouldTimeoutWhenConfiguredTimeExceeded` | ✅ VERDE |
| **R8** | Oportunidad perdida cuando `commercialIntentDetected=true` y `closureAchieved=false` | `LeadAnalyzerTest#shouldDetectLostLeadWhenIntentTrueAndClosureFalse` | ✅ VERDE |
| **R9** | Ausencia de intención comercial (`false`) fija `score=0.000` y `reason=null` | `LeadAnalyzerTest#shouldSetZeroScoreAndNullReasonWhenNoCommercialIntent` | ✅ VERDE |
| **R10** | Oportunidad cerrada (`closure=true`) no es venta perdida pero conserva `leadScore` | `LeadAnalyzerTest#shouldNotMarkLostLeadWhenClosureAchieved` | ✅ VERDE |
| **R11** | Clamping determinista de `leadScore` en el rango `[0.000, 1.000]` | `LeadAnalyzerTest#shouldClampNegativeScoreToZero`, `#shouldClampOverflowScoreToOne`, `#shouldTreatNullScoreAsZero` | ✅ VERDE |
| **R12** | Truncado de `lostLeadReason` a un máximo de 500 caracteres | `LeadAnalyzerTest#shouldTruncateLostLeadReasonTo500Chars` | ✅ VERDE |
| **R13** | Saneamiento y trim de espacios en blanco en `lostLeadReason` | `LeadAnalyzerTest#shouldTrimLostLeadReason` | ✅ VERDE |
| **R14** | Migración Flyway `V4__add_lost_lead_reason.sql` a `audit_result` | `AuditResultRepositoryLeadTest#shouldVerifyFlywayMigrationV4` | ✅ VERDE |
| **R15** | Mapeo JPA de `lostLeadReason` en entidad `AuditResult` | `AuditResultRepositoryLeadTest#shouldPersistAndRetrieveLostLeadFields` | ✅ VERDE |
| **R16** | Actualización atómica de campos de lead en `audit_result` | `LeadDetectionServiceImplTest#shouldUpdateAuditResultFieldsAtomically` | ✅ VERDE |
| **R17** | Preservación de estado en `PROCESSING` tras persistir leads | `LeadDetectionServiceImplTest#shouldKeepStatusInProcessing` | ✅ VERDE |
| **R18** | Idempotencia en reintentos con sobreescritura limpia | `LeadDetectionServiceImplTest#shouldOverwritePreviousLeadResultsOnRetry` | ✅ VERDE |
| **R19** | Invocación secuencial en `AuditPipelineOrchestratorImpl` tras etapa de sentimiento | `AuditPipelineOrchestratorImplTest#shouldTriggerAllStagesSequentiallyWhenSuccessful` | ✅ VERDE |
| **R20** | Omisión de etapas posteriores si `AuditResult` está en `FAILED` | `AuditPipelineOrchestratorImplTest#shouldNotTriggerLeadsIfSentimentFailed`, `#shouldNotTriggerSentimentOrLeadsIfGapsFailed` | ✅ VERDE |
| **R21** | Manejo de fallos en LLM marcando `FAILED` con prefijo `[AI_ERROR]` | `LeadDetectionServiceImplTest#shouldMarkAuditFailedOnAiError` | ✅ VERDE |
| **R22** | Logging estructurado con `chatId`, `etapa=LEAD_DETECTION` y métricas | `LeadDetectionServiceImplTest#shouldEmitStructuredLog` | ✅ VERDE |
| **R23** | Pureza de dominio de `LeadAnalyzer` (libre de frameworks) | `ArchitectureRulesTest#shouldKeepLeadAnalyzerPure` | ✅ VERDE |
| **R24** | Confinamiento de Spring AI en `integration.openai` | `ArchitectureRulesTest#shouldConfineLeadAiClientToOpenAiPackage` | ✅ VERDE |

---

## 2. Archivos modificados y creados

### Código de producción (`src/main/`)
- `resources/db/migration/V4__add_lost_lead_reason.sql`: Migración Flyway para columna `lost_lead_reason`.
- `model/AuditResult.java`: Campo `lostLeadReason` con longitud 500 y getter/setter Lombok.
- `dto/LeadAnalysisResponseDto.java`: DTO record para respuesta estructurada del modelo.
- `dto/LeadEvaluationResultDto.java`: DTO record con resultado analítico consolidado.
- `analyzer/LeadAnalyzer.java`: Analizador puro sin dependencias de Spring.
- `config/OpenAiConfiguration.java`: Registro de `@Bean LeadAnalyzer`.
- `integration/openai/ILeadDetectionAiClient.java`: Puerto anti-corrupción.
- `integration/openai/OpenAiLeadDetectionAdapter.java`: Adaptador con Spring AI `ChatClient` y timeout.
- `service/ILeadDetectionService.java`: Interfaz del servicio de leads.
- `service/impl/LeadDetectionServiceImpl.java`: Servicio transaccional y resiliente.
- `service/impl/AuditPipelineOrchestratorImpl.java`: Inyección de leads como 3ª etapa.

### Pruebas automáticas (`src/test/`)
- `analyzer/LeadAnalyzerTest.java`: 11 tests unitarios de lógica pura.
- `integration/openai/OpenAiLeadDetectionAdapterTest.java`: 6 tests unitarios con mocks de `ChatClient`.
- `repository/AuditResultRepositoryLeadTest.java`: 2 tests de integración JPA/H2.
- `service/impl/LeadDetectionServiceImplTest.java`: 8 tests unitarios de servicio y resiliencia.
- `service/impl/AuditPipelineOrchestratorImplTest.java`: 3 tests de orquestación secuencial.
- `ArchitectureRulesTest.java`: Regla ArchUnit `shouldKeepLeadAnalyzerPure` (16 reglas verificadas).
