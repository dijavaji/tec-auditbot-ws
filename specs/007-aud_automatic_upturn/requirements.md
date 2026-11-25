# Requirements — US-07 Generar recomendaciones de mejora (`aud_automatic_upturn`)

## Propósito

Definir la generación automática de recomendaciones de mejora para chatbots empresariales a partir de los hallazgos analizados en las etapas previas del pipeline de auditoría (brechas de conocimiento, detección de frustración y oportunidades de venta perdidas). Esta funcionalidad consolida los hallazgos previos y el diálogo conversacional, invoca al modelo de IA mediante el adaptador Spring AI OpenAI-compatible para sugerir nuevas preguntas frecuentes (`NEW_FAQ`) o flujos conversacionales alternativos (`ALTERNATIVE_FLOW`), procesa y valida las sugerencias con un analizador puro (`RecommendationAnalyzer`), persiste las entidades resultantes en `audit_recommendation`, y culmina el ciclo del pipeline promoviendo el estado del agregado raíz `AuditResult` a `COMPLETED`.

---

## Glosario

- **Recomendación de Mejora (`AuditRecommendation`):** entidad de dominio que representa una sugerencia accionable formulada por la IA para enriquecer la base de conocimiento o corregir los flujos conversacionales del chatbot auditado.
- **Tipo de Recomendación (`RecommendationType`):** catálogo tipificado que clasifica la sugerencia en `NEW_FAQ` (nueva pregunta frecuente ante gaps de información) o `ALTERNATIVE_FLOW` (ajuste en el diálogo o derivación ante frustración o leads perdidos).
- **Mensaje Origen (`originMessageId`):** identificador opcional del mensaje puntual de la conversación que motivó la sugerencia de mejora.
- **Hallazgos Previos (`AuditFindingsContextDto`):** consolidación de datos derivados de US-03 (`gapsCount`, lista de preguntas sin respuesta), US-04 (`frustrationDetected`, sentimientos negativos) y US-05 (`lostLeadDetected`, `leadScore`, `lostLeadReason`).
- **Analizador Puro (`RecommendationAnalyzer`):** componente en `api.analyzer` libre de dependencias de frameworks que aplica saneamiento de texto, normalización de enums, deduplicación y validación referencial lógica de mensajes origen.
- **Estado Final `COMPLETED`:** estado terminal exitoso del registro `audit_result` indicando que todas las etapas analíticas del pipeline han concluido satisfactoriamente.
- **Fecha de Auditoría (`audited_date`):** marca de tiempo UTC que registra el instante exacto de culminación del proceso completo de auditoría.

---

## Requisitos funcionales

### A. Preparación y consolidación de contexto

## R1 — Invocación secuencial en el orquestador
CUANDO finaliza la etapa de detección de leads (US-05) y el registro `audit_result` no está en estado `FAILED`, el sistema DEBE invocar la etapa de generación de recomendaciones de mejora.

**Verificación:** `AuditPipelineOrchestratorTest#shouldTriggerRecommendationServiceAfterLeadDetectionSuccess`.

## R2 — Omisión ante fallo en etapas previas
SI el registro `audit_result` se encuentra en estado `FAILED` tras las etapas precedentes ENTONCES el sistema NO DEBE ejecutar la etapa de recomendaciones.

**Verificación:** `AuditPipelineOrchestratorTest#shouldSkipRecommendationsWhenPreviousStageFailed`.

## R3 — Consolidación de hallazgos previos y contexto conversacional
CUANDO se inicia la generación de recomendaciones para una conversación, el sistema DEBE consolidar los hallazgos de las etapas previas (`gapsCount`, textos de preguntas sin responder, `frustrationDetected`, `lostLeadDetected`, `leadScore`, `lostLeadReason`) junto con la secuencia cronológica de mensajes de usuario y asistente.

**Verificación:** `RecommendationServiceImplTest#shouldConsolidatePreviousFindingsAndConversation`.

## R4 — Exclusión de mensajes de sistema
El sistema NO DEBE enviar mensajes con rol `SYSTEM` al modelo de generación de recomendaciones.

**Verificación:** `RecommendationServiceImplTest#shouldFilterOutSystemMessages`.

## R5 — Conversación limpia sin hallazgos adversos
CUANDO una conversación no presenta brechas de conocimiento (`gapsCount == 0`), no tiene frustración detectada (`frustrationDetected == false`) y no presenta oportunidad de venta perdida (`lostLeadDetected == false`), el sistema DEBE omitir la llamada al modelo de IA, persistir cero recomendaciones y promover directamente `audit_result` a `COMPLETED`.

**Verificación:** `RecommendationServiceImplTest#shouldCompleteWithoutAiCallWhenNoFindingsExist`.

---

### B. Generación de recomendaciones con Spring AI OpenAI-compatible

## R6 — Invocación en llamada estructurada única
CUANDO existan hallazgos adversos en la conversación auditada, el sistema DEBE invocar al modelo de IA en una única llamada estructurada transmitiendo el diálogo y los hallazgos consolidados.

**Verificación:** `OpenAiRecommendationAdapterTest#shouldSendFindingsAndDialogueInSingleCall`.

## R7 — Prompt de sistema para recomendaciones
CUANDO se realiza la petición al modelo de IA, el sistema DEBE aplicar un system prompt estricto que instruya generar recomendaciones clasificadas exclusivamente en `NEW_FAQ` o `ALTERNATIVE_FLOW`, asociando el identificador del mensaje causante siempre que sea identificable.

**Verificación:** `OpenAiRecommendationAdapterTest#shouldApplyStrictRecommendationPrompt`.

## R8 — Deserialización de candidatos de recomendación
CUANDO el modelo responde, el sistema DEBE deserializar la respuesta en un DTO estructurado conteniendo una lista de candidatos (`RecommendationCandidateDto`) con los atributos `type` (String), `suggestedText` (String) y `originMessageId` (String).

**Verificación:** `OpenAiRecommendationAdapterTest#shouldDeserializeStructuredRecommendationResponse`.

## R9 — Timeout configurable de la llamada a IA
CUANDO se invoca al modelo de IA, el sistema DEBE interrumpir la espera si se supera el tiempo límite configurado en `auditbot.ai.openai.timeout-seconds` y lanzar una excepción controlada.

**Verificación:** `OpenAiRecommendationAdapterTest#shouldTimeoutWhenConfiguredTimeExceeded`.

---

### C. Reglas de negocio y análisis puro (`RecommendationAnalyzer`)

## R10 — Normalización y filtrado del tipo de recomendación
CUANDO el analizador evalúa un candidato de recomendación, el sistema DEBE mapear su campo `type` de forma case-insensitive a los valores válidos del enum `RecommendationType` (`NEW_FAQ` o `ALTERNATIVE_FLOW`), descartando aquellos candidatos con tipos inválidos o no reconocidos.

**Verificación:** `RecommendationAnalyzerTest#shouldNormalizeAndFilterRecommendationTypes`.

## R11 — Saneamiento y validación de texto sugerido
CUANDO se evalúa un candidato de recomendación, el sistema DEBE aplicar trim al `suggestedText` y DEBE descartar cualquier candidato cuyo texto resultante sea nulo o esté vacío.

**Verificación:** `RecommendationAnalyzerTest#shouldRejectBlankOrNullSuggestedText`.

## R12 — Validación de referencia de mensaje origen
CUANDO un candidato incluye un `originMessageId`, el sistema DEBE verificar si coincide con un `messageId` existente en los mensajes evaluados de la conversación; si no coincide, el sistema DEBE establecer `originMessageId = null`.

**Verificación:** `RecommendationAnalyzerTest#shouldSetOriginMessageIdToNullWhenNotFoundInConversation`.

## R13 — Deduplicación de recomendaciones idénticas
CUANDO existan múltiples candidatos con idéntico `type` e idéntico `suggestedText` (ignorando diferencias de espacios iniciales o finales), el sistema DEBE retener únicamente la primera aparición y descartar los duplicados.

**Verificación:** `RecommendationAnalyzerTest#shouldDeduplicateIdenticalRecommendations`.

---

### D. Persistencia y esquema relacional

## R14 — Migración Flyway de esquema para recomendaciones
El sistema DEBE aplicar la migración `V5__create_audit_recommendation.sql` creando la tabla `audit_recommendation` con primary key `recommendation_id`, foreign key en cascada `fk_audit_recommendation_audit_result` sobre `audit_result(audit_id)`, y el índice `idx_audit_recommendation_audit_id`.

**Verificación:** `AuditRecommendationRepositoryTest#shouldVerifyFlywayMigrationV5`.

## R15 — Mapeo JPA de la entidad `AuditRecommendation`
El sistema DEBE mapear la entidad `AuditRecommendation` en el paquete `model` vinculada a la tabla `audit_recommendation`, con su clave foránea mapeada con relación `@ManyToOne(fetch = FetchType.LAZY)`.

**Verificación:** `AuditRecommendationRepositoryTest#shouldPersistAndRetrieveRecommendationEntity`.

## R16 — Mapeo de relación inversa en `AuditResult`
El sistema DEBE mapear en la entidad aggregate root `AuditResult` la colección de recomendaciones mediante `@OneToMany(mappedBy = "auditResult", cascade = CascadeType.ALL, orphanRemoval = true)`.

**Verificación:** `AuditRecommendationRepositoryTest#shouldCascadeRecommendationsFromAuditResult`.

## R17 — Persistencia de recomendaciones generadas
CUANDO el análisis produce una o más recomendaciones válidas, el sistema DEBE persistirlas en la tabla `audit_recommendation` asociadas al `audit_id` de la conversación analizada con fecha de creación en UTC.

**Verificación:** `RecommendationServiceImplTest#shouldPersistAllValidRecommendations`.

## R18 — Transición a estado final `COMPLETED`
CUANDO finaliza exitosamente la persistencia de la etapa de recomendaciones (con cero o más registros), el sistema DEBE actualizar en `audit_result` los campos `status = AuditStatus.COMPLETED`, `audited_date = Instant.now()` y `updated_date = Instant.now()` en una única transacción atómica.

**Verificación:** `RecommendationServiceImplTest#shouldTransitionAuditResultToCompletedWithAuditedDate`.

## R19 — Idempotencia y limpieza en reintentos
CUANDO se procesa una conversación para la cual ya existían recomendaciones persistidas previamente, el sistema DEBE eliminar las recomendaciones anteriores antes de persistir el nuevo conjunto generado.

**Verificación:** `RecommendationServiceImplTest#shouldDeletePreviousRecommendationsOnRetry`.

---

### E. Resiliencia, orquestación y observabilidad

## R20 — Manejo controlado de fallos en IA
SI la invocación al modelo de IA falla por error de red, timeout o respuesta malformada ENTONCES el sistema DEBE marcar `audit_result` con `status = FAILED`, registrar `error_message` prefijado con `[AI_ERROR] ` (truncado a máximo 500 caracteres), actualizar `updated_date` y no propagar excepción no controlada hacia el pipeline.

**Verificación:** `RecommendationServiceImplTest#shouldMarkAuditFailedOnAiError`.

## R21 — Emisión de log estructurado
CUANDO culmina la etapa de recomendaciones (sea con resultado exitoso o fallido), el sistema DEBE emitir un log estructurado con `@Slf4j` conteniendo `chatId`, `assistantId`, `etapa=RECOMMENDATIONS`, `resultado`, `recomendacionesGeneradas` y `duracionMs`.

**Verificación:** `RecommendationServiceImplTest#shouldEmitStructuredLog`.

---

### F. Conformidad arquitectónica

## R22 — Pureza del analizador de recomendaciones
El sistema DEBE mantener la clase `RecommendationAnalyzer` en el paquete `analyzer` libre de dependencias y anotaciones de Spring, JPA, Spring AI o RabbitMQ.

**Verificación:** `ArchitectureRulesTest#shouldKeepRecommendationAnalyzerPure`.

## R23 — Confinamiento de componentes de IA
El sistema DEBE confinar los tipos `IRecommendationAiClient` y `OpenAiRecommendationAdapter` exclusivamente al paquete `integration.openai`.

**Verificación:** `ArchitectureRulesTest#shouldConfineRecommendationAiClientToOpenAiPackage`.
