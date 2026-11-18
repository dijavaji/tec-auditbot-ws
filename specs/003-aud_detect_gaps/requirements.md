# Requirements — US-03 Detectar preguntas sin respuesta (`aud_detect_gaps`)

## Propósito

Definir la detección automática de brechas de conocimiento (knowledge gaps) en las conversaciones admitidas de chatbots empresariales. La feature procesa los turnos de diálogo entre usuario y asistente, consulta la IA mediante el cliente compatible OpenAI de Spring AI apuntando a Ollama, agrupa deterministamente las dudas no resueltas por similitud léxica/semántica, persiste los hallazgos en `audit_gap` y actualiza la auditoría en `audit_result` manteniendo el estado `PROCESSING` para las etapas posteriores del pipeline.

## Glosario

- **Turno de diálogo (Turn):** par ordenado compuesto por una consulta emitida por un usuario (`role = USER`) y la respuesta inmediata subsiguiente emitida por el chatbot (`role = ASSISTANT`), o una consulta de usuario sin respuesta final.
- **Knowledge Gap (Brecha de conocimiento):** consulta de usuario frente a la cual el asistente admite desconocimiento, responde con evasivas o fuera de contexto, pide disculpas reiteradas sin resolver, o no satisface el requerimiento central.
- **Score de confianza (`confidenceScore`):** valor numérico decimal en escala `0.000` a `1.000` retornado por la IA que cuantifica la certeza de que el turno constituye un gap de conocimiento.
- **Umbral de confianza mínimo (`min-confidence`):** cota mínima configurable (`auditbot.ai.gaps.min-confidence`, por defecto `0.600`) requerida para considerar un candidato a gap de conocimiento.
- **Pregunta canónica (`questionText`):** formulación más representativa y completa de una consulta dentro de un grupo de preguntas redundantes o paráfrasis.
- **Similitud Jaccard:** coeficiente de similitud de conjuntos basado en la intersección sobre la unión de tokens léxicos normalizados entre dos consultas.
- **Umbral de similitud (`similarity-threshold`):** valor mínimo de coincidencia Jaccard (`auditbot.ai.gaps.similarity-threshold`, por defecto `0.70`) para consolidar dos preguntas dentro del mismo grupo de gap.
- **Estado transicional `PROCESSING`:** estado en `audit_result` que indica que la conversación continúa su flujo por las etapas del pipeline de auditoría (US-03 a US-06).
- **Error irrecuperable de IA:** fallo por timeout, caída de red, servicio indisponible o respuesta JSON malformada por parte del endpoint LLM tras agotar sus reintentos.

---

## Requisitos funcionales

### A. Extracción y filtrado de turnos de conversación

## R1 — Extracción cronológica de turnos
CUANDO el pipeline de auditoría inicia para una conversación admitida, el sistema DEBE reconstruir los turnos de diálogo en estricto orden cronológico respetando `messages[].timestamp`.

**Verificación:** `KnowledgeGapServiceImplTest#shouldExtractTurnsInChronologicalOrder`.

## R2 — Filtrado de mensajes con rol `SYSTEM`
El sistema NO DEBE evaluar mensajes con rol `SYSTEM` como consultas candidatas a gap de usuario.

**Verificación:** `KnowledgeGapServiceImplTest#shouldFilterOutSystemMessages`.

## R3 — Emparejamiento de consulta y respuesta
CUANDO un mensaje de rol `USER` es seguido por un mensaje de rol `ASSISTANT`, el sistema DEBE asociar ambos mensajes en un único turno de diálogo para su evaluación contextual.

**Verificación:** `KnowledgeGapServiceImplTest#shouldPairUserAndAssistantMessagesIntoTurn`.

## R4 — Turno de usuario sin respuesta final
CUANDO el último mensaje de la conversación pertenece a un emisor con rol `USER` sin respuesta subsiguiente del asistente, el sistema DEBE generar un turno con respuesta vacía y clasificarlo para evaluación.

**Verificación:** `KnowledgeGapServiceImplTest#shouldHandleUserMessageWithoutSubsequentResponse`.

---

### B. Clasificación asistida por IA (Spring AI OpenAI-compatible)

## R5 — Invocación mediante cliente compatible OpenAI
CUANDO se evalúan los turnos de una conversación, el sistema DEBE invocar el endpoint compatible OpenAI (`/v1`) a través del adaptador `OpenAiKnowledgeGapAdapter` usando `ChatClient` de Spring AI.

**Verificación:** `OpenAiKnowledgeGapAdapterTest#shouldInvokeChatClientWithFormattedPrompt`.

## R6 — System prompt estricto para detección de gaps
CUANDO se formula la petición a la IA, el sistema DEBE aplicar un system prompt estricto que instruya clasificar si cada turno evidencia desconocimiento, evasivas, incapacidad o disculpas sin resolución por parte del bot.

**Verificación:** `OpenAiKnowledgeGapAdapterTest#shouldIncludeStrictSystemPrompt`.

## R7 — Salida estructurada de clasificación
CUANDO el modelo responde, el sistema DEBE recibir y deserializar la respuesta en un objeto estructurado que contenga para cada turno: `messageId`, `questionText`, `isGap` (booleano), `confidenceScore` (decimal) y `reason` (texto explicativo).

**Verificación:** `OpenAiKnowledgeGapAdapterTest#shouldDeserializeStructuredJsonResponse`.

## R8 — Descarte de turnos no clasificados como gap
CUANDO un turno es clasificado con `isGap = false`, el sistema DEBE descartar dicho turno del flujo de persistencia de gaps.

**Verificación:** `OpenAiKnowledgeGapAdapterTest#shouldFilterOutNonGapTurns`.

---

### C. Validación de umbrales y filtrado de confianza

## R9 — Umbral mínimo de confianza configurable
El sistema DEBE resolver el umbral mínimo de confianza desde la propiedad `auditbot.ai.gaps.min-confidence`, adoptando `0.600` si no se configura explícitamente.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldRespectConfiguredMinConfidenceThreshold`.

## R10 — Descarte por score inferior al umbral
CUANDO un turno es marcado como `isGap = true` pero su `confidenceScore` es estrictamente menor al umbral mínimo, el sistema DEBE descartar dicho candidato y no considerarlo para agrupación ni persistencia.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldDiscardGapsBelowMinConfidence`.

---

### D. Agrupación por similitud y deduplicación intra-conversación

## R11 — Normalización léxica determinista
CUANDO el analizador compara consultas, el sistema DEBE normalizar los textos convirtiéndolos a minúsculas, retirando signos de puntuación (`¿?¡!.,;:`) y eliminando tildes y diacríticos.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldNormalizeQuestionTextDeterministically`.

## R12 — Filtrado de stopwords
CUANDO se tokenizan las preguntas normalizadas, el sistema DEBE remover stopwords básicas del idioma español (`de`, `la`, `el`, `en`, `un`, `una`, `que`, `por`, `para`, etc.).

**Verificación:** `KnowledgeGapAnalyzerTest#shouldRemoveStopwordsDuringTokenization`.

## R13 — Cálculo de similitud Jaccard
CUANDO se comparan dos consultas normalizadas $A$ y $B$, el sistema DEBE calcular el coeficiente Jaccard como $\frac{|A \cap B|}{|A \cup B|}$.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldCalculateCorrectJaccardSimilarity`.

## R14 — Umbral de similitud configurable
El sistema DEBE resolver el umbral de similitud desde la propiedad `auditbot.ai.gaps.similarity-threshold`, adoptando `0.70` si no se configura explícitamente.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldRespectConfiguredSimilarityThreshold`.

## R15 — Consolidación de preguntas redundantes
CUANDO dos o más consultas de la misma conversación presentan una similitud Jaccard mayor o igual al umbral configurado, el sistema DEBE consolidarlas en un único grupo de gap de conocimiento.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldConsolidateSimilarQuestionsIntoSingleGap`.

## R16 — Preservación del primer `messageId` cronológico
CUANDO se consolida un grupo de gaps con múltiples ocurrencias, el sistema DEBE asociar al grupo el `messageId` correspondiente a la primera ocurrencia cronológica en la conversación.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldPreserveFirstChronologicalMessageId`.

## R17 — Selección de la pregunta canónica
CUANDO se consolida un grupo de gaps, el sistema DEBE seleccionar como `questionText` canónico aquel texto que contenga la mayor cantidad de tokens informativos (o mayor longitud informativa no trivial).

**Verificación:** `KnowledgeGapAnalyzerTest#shouldSelectMostInformativeCanonicalQuestion`.

## R18 — Asignación del score consolidado
CUANDO se consolida un grupo de gaps, el sistema DEBE asignar al grupo el `confidenceScore` máximo entre todas las ocurrencias del grupo, redondeado a 3 posiciones decimales.

**Verificación:** `KnowledgeGapAnalyzerTest#shouldAssignMaxConfidenceScoreToConsolidatedGap`.

---

### E. Persistencia y actualización del estado de auditoría

## R19 — Migración Flyway `V2__create_audit_gap.sql`
El sistema DEBE proveer la migración Flyway `V2__create_audit_gap.sql` con la definición de la tabla `audit_gap`, clave primaria `gap_id`, clave foránea `fk_audit_gap_audit_result` con `ON DELETE CASCADE` hacia `audit_result(audit_id)` e índice `idx_audit_gap_audit_id`.

**Verificación:** `AuditGapRepositoryTest#shouldVerifySchemaAndForeignConstraint`.

## R20 — Entidad JPA `AuditGap`
El sistema DEBE mapear la entidad JPA `AuditGap` en el paquete `model` con campos `gapId` (UUID string), `auditResult` (`@ManyToOne`), `messageId`, `questionText`, `confidenceScore` (`BigDecimal(4,3)`) y `createdDate` (`Instant`).

**Verificación:** `AuditGapRepositoryTest#shouldPersistAndRetrieveAuditGap`.

## R21 — Repositorio JPA `IAuditGapRepository`
El sistema DEBE proveer la interfaz `IAuditGapRepository` en el paquete `repository` con métodos de consulta `List<AuditGap> findByAuditResult_AuditId(String auditId)` y `void deleteByAuditResult_AuditId(String auditId)`.

**Verificación:** `AuditGapRepositoryTest#shouldFindByAuditIdAndDeleteByAuditId`.

## R22 — Persistencia de registros de gaps consolidados
CUANDO se identifican uno o más grupos de gaps consolidados, el sistema DEBE persistir cada grupo como un registro individual en la tabla `audit_gap`.

**Verificación:** `KnowledgeGapServiceImplTest#shouldPersistAllConsolidatedGaps`.

## R23 — Actualización de `gapsCount` en `AuditResult`
CUANDO finaliza el análisis de gaps, el sistema DEBE actualizar el campo `gapsCount` en la entidad `AuditResult` con la cantidad exacta de registros de gap persistidos.

**Verificación:** `KnowledgeGapServiceImplTest#shouldUpdateGapsCountInAuditResult`.

## R24 — Actualización de `updatedDate`
CUANDO se actualiza `AuditResult`, el sistema DEBE refrescar el campo `updatedDate` con el reloj del sistema en UTC.

**Verificación:** `KnowledgeGapServiceImplTest#shouldRefreshUpdatedDateOnAuditResult`.

## R25 — Mantenimiento del estado en `PROCESSING`
CUANDO el análisis de gaps concluye satisfactoriamente, el sistema DEBE mantener el campo `status` de `AuditResult` en `PROCESSING`.

**Verificación:** `KnowledgeGapServiceImplTest#shouldKeepAuditResultInProcessingStatus`.

## R26 — Caso de conversación sin gaps detectados
CUANDO una conversación es analizada y no se detecta ningún gap (o todos quedan por debajo del umbral), el sistema DEBE actualizar `gapsCount = 0`, no insertar filas en `audit_gap` y mantener el estado en `PROCESSING`.

**Verificación:** `KnowledgeGapServiceImplTest#shouldHandleZeroGapsDetectedCleanly`.

## R27 — Idempotencia y limpieza de gaps en reprocesamiento
CUANDO se ejecuta un reintento explícito (`x-audit-retry = true`) sobre un `chatId` previamente analizado, el sistema DEBE eliminar los registros existentes en `audit_gap` vinculados a ese `audit_id` antes de persistir los nuevos hallazgos.

**Verificación:** `KnowledgeGapServiceImplTest#shouldReplaceExistingGapsOnRetry`.

---

### F. Resiliencia, timeouts y tolerancia a fallos

## R28 — Timeout configurable de llamada al LLM
El sistema DEBE limitar el tiempo de respuesta de las peticiones a la API del modelo de lenguaje mediante la propiedad `auditbot.ai.openai.timeout-seconds` con un valor por defecto de 30 segundos.

**Verificación:** `OpenAiKnowledgeGapAdapterTest#shouldTimeoutWhenModelDoesNotRespondInTime`.

## R29 — Marcación a `FAILED` ante fallo del LLM
SI la llamada al cliente LLM expira por timeout, arroja error de comunicación HTTP o entrega una respuesta malformada irrecuperable ENTONCES el sistema DEBE abortar la persistencia de gaps y actualizar el registro `AuditResult` con `status = FAILED`.

**Verificación:** `KnowledgeGapServiceImplTest#shouldMarkAuditAsFailedOnAiClientError`.

## R30 — Registro de mensaje de error legible
CUANDO una auditoría falla por error del cliente LLM, el sistema DEBE registrar en el campo `errorMessage` de `AuditResult` una descripción legible, truncada a un máximo de 500 caracteres y libre de datos sensibles.

**Verificación:** `KnowledgeGapServiceImplTest#shouldRecordTruncatedErrorMessageOnFailure`.

## R31 — Transaccionalidad de la fase de análisis
El servicio de análisis DEBE ejecutar la persistencia de gaps y la actualización de `AuditResult` dentro de una transacción atómica (`@Transactional`).

**Verificación:** `KnowledgeGapServiceImplTest#shouldRollbackGapsWhenTransactionFails`.

---

### G. Observabilidad y privacidad

## R32 — Log estructurado de fin de etapa
CUANDO concluye la ejecución de la etapa de gaps (con éxito o con fallo), el sistema DEBE emitir una línea de log estructurada con las claves `etapa=GAP_DETECTION`, `chatId`, `assistantId`, `gapsDetectados`, `mensajesAnalizados`, `duracionMs` y `resultado` (`OK` o `FAILED`).

**Verificación:** `KnowledgeGapServiceImplTest#shouldEmitStructuredLogOnStageCompletion`.

## R33 — Protección de privacidad en logs y excepciones
El sistema NO DEBE volcar el contenido íntegro de los mensajes de la conversación ni datos confidenciales en los logs de la aplicación ni en los mensajes de excepción.

**Verificación:** `KnowledgeGapServiceImplTest#shouldNotLeakPrivateMessageContentsInLogs`.

---

### H. Límites arquitectónicos y estándares

## R34 — Pureza del analizador de gaps
La clase `KnowledgeGapAnalyzer` en `ec.com.technoloqie.auditbot.api.analyzer` NO DEBE depender de anotaciones de Spring (`@Component`, `@Service`, etc.), ni de JPA, ni de AMQP, ni de clases de Spring AI.

**Verificación:** `ArchitectureRulesTest#shouldKeepKnowledgeGapAnalyzerPure`.

## R35 — Confinamiento de Spring AI en `integration.openai`
Las clases y dependencias directas de Spring AI (`ChatClient`, `ChatClient.Builder`, `OpenAiChatModel`, etc.) DEBEN aislarse exclusivamente dentro del paquete `ec.com.technoloqie.auditbot.api.integration.openai`.

**Verificación:** `ArchitectureRulesTest#shouldConfineSpringAiInsideOpenAiIntegrationPackage`.

## R36 — Inyección exclusiva por constructor
Todos los componentes de servicio y adaptadores de integración DEBEN utilizar inyección por constructor y NO DEBEN utilizar la anotación `@Autowired` en atributos.

**Verificación:** `ArchitectureRulesTest#shouldEnforceConstructorInjection`.

## R37 — Verificación completa del build
CUANDO se ejecuta `./mvnw clean verify` con el perfil `local`, el sistema DEBE finalizar con `BUILD SUCCESS` y el 100% de los tests unitarios, de repositorio, de integración y de arquitectura en verde.

**Verificación:** Ejecución integral del pipeline de build Maven.
