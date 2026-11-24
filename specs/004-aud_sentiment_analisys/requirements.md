# Requirements — US-04 Detectar frustración del usuario (`aud_sentiment_analisys`)

## Propósito

Definir el análisis automático del sentimiento expresado en los mensajes emitidos por los usuarios en conversaciones de chatbots empresariales, el cálculo de la tendencia emocional a lo largo del diálogo y la determinación del indicador global de frustración (`frustration_detected`). Esta funcionalidad procesa la secuencia cronológica de mensajes de usuario, invoca el modelo de IA mediante el adaptador Spring AI OpenAI-compatible en lote, evalúa las reglas de tendencia y frustración mediante un analizador de dominio puro, persiste los resultados en `audit_sentiment`, actualiza `audit_result` y preserva el estado en `PROCESSING` para las siguientes etapas del pipeline (US-05 y US-06).

---

## Glosario

- **Mensaje de Usuario (`USER`):** mensaje emitido por el cliente o usuario final en el chatbot, sujeto a análisis de sentimiento.
- **Mensaje de Asistente (`ASSISTANT`) / Sistema (`SYSTEM`):** mensajes emitidos por el chatbot o el middleware del sistema, excluidos del almacenamiento de sentimientos individuales.
- **Etiqueta de Sentimiento (`SentimentLabel`):** clasificación categórica asignada por la IA entre las cuatro permitidas: `POSITIVE`, `NEUTRAL`, `NEGATIVE`, `FRUSTRATED`.
- **Score de Confianza (`score`):** valor numérico decimal en escala `0.000` a `1.000` devuelto por el modelo que cuantifica la certeza de la clasificación de sentimiento.
- **Orden Secuencial (`sequenceOrder`):** entero incremental ($1, 2, \dots, N$) que representa la posición temporal de cada mensaje de usuario dentro del chat.
- **Polaridad Numérica ($P_i$):** valor escalar asignado para cálculos analíticos: $\text{POSITIVE} = +1.0$, $\text{NEUTRAL} = 0.0$, $\text{NEGATIVE} = -0.5$, $\text{FRUSTRATED} = -1.0$.
- **Tendencia de Sentimiento (`SentimentTrend`):** evolución emocional neta de la conversación, categorizada como `IMPROVING` (mejora), `WORSENING` (empeora) o `STABLE` (estable).
- **Indicador de Frustración (`frustration_detected`):** bandera booleana a nivel de conversación (`AuditResult`) que determina si el diálogo presentó niveles inaceptables de fricción o insatisfacción del cliente.
- **Umbral de Confianza de Frustración (`frustration-score-threshold`):** valor mínimo de confianza (`auditbot.ai.sentiment.frustration-score-threshold`, por defecto `0.700`) para activar frustración por mensaje individual.
- **Umbral de Ratio Negativo (`negative-ratio-threshold`):** proporción mínima de mensajes negativos o frustrados (`auditbot.ai.sentiment.negative-ratio-threshold`, por defecto `0.400`) para marcar frustración global.
- **Umbral de Negativos Consecutivos (`consecutive-negative-threshold`):** cantidad mínima de mensajes negativos o frustrados seguidos (`auditbot.ai.sentiment.consecutive-negative-threshold`, por defecto `2`) para activar la frustración.
- **Estado Transicional `PROCESSING`:** estado en `audit_result` que indica que el pipeline continúa activo hacia etapas posteriores.

---

## Requisitos funcionales

### A. Extracción y secuenciación de mensajes de usuario

## R1 — Filtrado exclusivo de mensajes de usuario
CUANDO se inicia el análisis de sentimiento para una conversación, el sistema DEBE seleccionar exclusivamente los mensajes con rol `USER`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldFilterOnlyUserMessages`.

## R2 — Exclusión de roles no evaluables
El sistema NO DEBE persistir registros de sentimiento para mensajes cuyo rol sea `SYSTEM` o `ASSISTANT`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldExcludeSystemAndAssistantRoles`.

## R3 — Ordenación cronológica
CUANDO se extraen los mensajes de usuario, el sistema DEBE ordenarlos cronológicamente en orden ascendente según su marca de tiempo `occurredAt`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldOrderUserMessagesChronologically`.

## R4 — Asignación de secuencia correlativa
CUANDO se prepara la lista ordenada de mensajes de usuario, el sistema DEBE asignar un `sequenceOrder` secuencial entero estricto iniciando en `1` e incrementando de uno en uno.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldAssignSequentialOrderStartingAtOne`.

## R5 — Conversación sin mensajes de usuario
CUANDO una conversación no contiene ningún mensaje con rol `USER`, el sistema DEBE omitir la llamada a la IA y persistir `frustrationDetected = false`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldHandleConversationWithoutUserMessages`.

---

### B. Clasificación de sentimientos asistida por IA (Spring AI OpenAI-compatible)

## R6 — Invocación en lote por conversación
CUANDO se clasifican los mensajes de usuario de una conversación, el sistema DEBE enviar todos los mensajes en una única petición agrupada al modelo de IA vía `ChatClient`.

**Verificación:** `OpenAiSentimentAdapterTest#shouldSendUserMessagesInSingleBatchRequest`.

## R7 — Prompt estricto del sistema
CUANDO se formula la llamada al modelo, el sistema DEBE aplicar un system prompt estricto que instruya clasificar cada mensaje exclusivamente en las etiquetas `POSITIVE`, `NEUTRAL`, `NEGATIVE` o `FRUSTRATED`.

**Verificación:** `OpenAiSentimentAdapterTest#shouldIncludeStrictSentimentSystemPrompt`.

## R8 — Deserialización de salida estructurada
CUANDO el modelo responde, el sistema DEBE deserializar la respuesta en una lista de registros que contengan `messageId`, `label` y `score`.

**Verificación:** `OpenAiSentimentAdapterTest#shouldDeserializeStructuredOutput`.

## R9 — Validación de score de clasificación
CUANDO se recibe el score numérico para un mensaje, el sistema DEBE validar que se encuentre en el rango cerrado entre `0.000` y `1.000`.

**Verificación:** `SentimentAnalyzerTest#shouldValidateConfidenceScoreWithinRange`.

---

### C. Análisis de sentimiento y cálculo de tendencia emocional

## R10 — Mapeo de polaridad escalar
CUANDO el analizador puro procesa una clasificación, el sistema DEBE asociar cada etiqueta a su polaridad escalar base: `POSITIVE` $\rightarrow +1.0$, `NEUTRAL` $\rightarrow 0.0$, `NEGATIVE` $\rightarrow -0.5$ y `FRUSTRATED` $\rightarrow -1.0$.

**Verificación:** `SentimentAnalyzerTest#shouldMapLabelsToScalarPolarity`.

## R11 — Tendencia estable para conversaciones de un único mensaje
CUANDO la conversación contiene exactamente un mensaje de usuario, el sistema DEBE clasificar la tendencia emocional como `STABLE`.

**Verificación:** `SentimentAnalyzerTest#shouldSetStableTrendForSingleMessageConversation`.

## R12 — Cálculo de tendencia por división en dos mitades
CUANDO la conversación contiene dos o más mensajes de usuario, el sistema DEBE calcular la diferencia $\Delta P$ entre la polaridad promedio de la segunda mitad cronológica y la primera mitad cronológica.

**Verificación:** `SentimentAnalyzerTest#shouldCalculatePolarityDeltaBetweenHalves`.

## R13 — Tendencia de mejora (IMPROVING)
CUANDO la diferencia de polaridad $\Delta P \ge +0.20$, el sistema DEBE clasificar la tendencia de la conversación como `IMPROVING`.

**Verificación:** `SentimentAnalyzerTest#shouldDetectImprovingTrendWhenDeltaIsPositiveAndSignificant`.

## R14 — Tendencia de empeoramiento (WORSENING)
CUANDO la diferencia de polaridad $\Delta P \le -0.20$, el sistema DEBE clasificar la tendencia de la conversación como `WORSENING`.

**Verificación:** `SentimentAnalyzerTest#shouldDetectWorseningTrendWhenDeltaIsNegativeAndSignificant`.

## R15 — Tendencia neutra (STABLE)
CUANDO la diferencia de polaridad $\Delta P$ satisface $-0.20 < \Delta P < +0.20$, el sistema DEBE clasificar la tendencia de la conversación como `STABLE`.

**Verificación:** `SentimentAnalyzerTest#shouldDetectStableTrendWhenDeltaIsWithinThreshold`.

---

### D. Detección y marcado de frustración global

## R16 — Detección por pico individual de frustración
CUANDO al menos un mensaje de usuario tiene etiqueta `FRUSTRATED` con `score` mayor o igual al umbral configurable `frustration-score-threshold`, el sistema DEBE determinar `frustrationDetected = true`.

**Verificación:** `SentimentAnalyzerTest#shouldDetectFrustrationOnSingleHighConfidencePeak`.

## R17 — Detección por ratio de negatividad acumulada
CUANDO el porcentaje de mensajes de usuario con etiqueta `NEGATIVE` o `FRUSTRATED` sobre el total supera el umbral configurable `negative-ratio-threshold`, el sistema DEBE determinar `frustrationDetected = true`.

**Verificación:** `SentimentAnalyzerTest#shouldDetectFrustrationWhenNegativeRatioExceedsThreshold`.

## R18 — Detección por mensajes negativos consecutivos
CUANDO existen al menos $K$ mensajes consecutivos de usuario con etiqueta `NEGATIVE` o `FRUSTRATED`, donde $K$ es igual al umbral configurable `consecutive-negative-threshold`, el sistema DEBE determinar `frustrationDetected = true`.

**Verificación:** `SentimentAnalyzerTest#shouldDetectFrustrationOnConsecutiveNegativeMessages`.

## R19 — Ausencia de frustración
CUANDO ninguna de las condiciones de pico, ratio o mensajes consecutivos se satisface, el sistema DEBE determinar `frustrationDetected = false`.

**Verificación:** `SentimentAnalyzerTest#shouldNotDetectFrustrationWhenCriteriaAreNotMet`.

---

### E. Modelo de datos, persistencia y transaccionalidad

## R20 — Migración Flyway V3
El sistema DEBE proveer el script Flyway `V3__create_audit_sentiment.sql` creando la tabla `audit_sentiment` con clave foránea referenciando a `audit_result(audit_id)` con `ON DELETE CASCADE` e índice sobre `audit_id`.

**Verificación:** `AuditSentimentRepositoryTest#shouldExecuteFlywayV3AndEnforceForeignKey`.

## R21 — Mapeo relacional de AuditSentiment
El sistema DEBE mapear la entidad JPA `AuditSentiment` conteniendo `sentimentId`, `auditResult`, `messageId`, `label`, `score`, `sequenceOrder` y `createdDate`.

**Verificación:** `AuditSentimentRepositoryTest#shouldPersistAndRetrieveAuditSentiment`.

## R22 — Persistencia en lote de sentimientos
CUANDO finaliza el análisis de sentimientos de una conversación, el sistema DEBE persistir todos los registros `AuditSentiment` asociados a la auditoría mediante `saveAll`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldPersistAllSentimentRecords`.

## R23 — Actualización de bandera y timestamp en AuditResult
CUANDO finaliza la persistencia de los sentimientos, el sistema DEBE actualizar en `AuditResult` los campos `frustrationDetected` con el resultado booleano y `updatedDate` con la fecha y hora actual.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldUpdateAuditResultWithFrustrationFlag`.

## R24 — Preservación del estado PROCESSING
CUANDO la etapa de análisis de sentimiento concluye satisfactoriamente, el sistema DEBE mantener el campo `status` de `AuditResult` en `PROCESSING`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldKeepStatusInProcessingUponSuccess`.

## R25 — Idempotencia en reintentos
CUANDO se procesa una conversación que ya cuenta con registros previos en `audit_sentiment`, el sistema DEBE eliminar los registros anteriores de esa auditoría antes de persistir los nuevos.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldDeletePreviousSentimentsOnRetry`.

## R26 — Transaccionalidad atómica
El sistema DEBE ejecutar la limpieza de sentimientos previos, la inserción de nuevos registros y la actualización de `AuditResult` bajo una única transacción de base de datos (`@Transactional`).

**Verificación:** `SentimentAnalysisServiceImplTest#shouldExecuteUnderTransactionalBoundary`.

---

### F. Integración en el pipeline y orquestación

## R27 — Ejecución secuencial posterior a gaps
CUANDO la etapa de detección de gaps (`US-03`) finaliza con éxito, el orquestador `AuditPipelineOrchestratorImpl` DEBE invocar inmediatamente a `ISentimentAnalysisService`.

**Verificación:** `AuditPipelineOrchestratorImplTest#shouldTriggerSentimentAnalysisAfterSuccessfulGaps`.

## R28 — Detención ante fallo en gaps
SI la etapa de detección de gaps (`US-03`) falla y marca la auditoría en `FAILED`, ENTONCES el orquestador NO DEBE invocar el análisis de sentimiento.

**Verificación:** `AuditPipelineOrchestratorImplTest#shouldNotTriggerSentimentAnalysisIfGapsFailed`.

---

### G. Resiliencia, timeouts y gestión de fallos

## R29 — Timeout en la invocación de IA
CUANDO se realiza la petición al adaptador de Spring AI, el sistema DEBE aplicar un timeout configurable `auditbot.ai.openai.timeout-seconds` (por defecto `30` segundos).

**Verificación:** `OpenAiSentimentAdapterTest#shouldEnforceConfiguredTimeout`.

## R30 — Marcación en FAILED ante error irrecuperable
SI la invocación de IA falla por timeout, servicio indisponible o error de formato, ENTONCES el sistema DEBE actualizar el registro `AuditResult` con `status = FAILED`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldMarkAuditResultAsFailedOnAiError`.

## R31 — Registro de mensaje de error truncado
CUANDO la invocación de IA falla, el sistema DEBE registrar en `AuditResult.errorMessage` una descripción legible con prefijo `[AI_ERROR]` truncada a un máximo de 500 caracteres.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldTruncateErrorMessageTo500Characters`.

---

### H. Observabilidad, privacidad y auditoría

## R32 — Log estructurado de etapa
CUANDO la etapa de análisis de sentimiento finaliza, el sistema DEBE registrar una línea de log estructurada conteniendo `etapa=SENTIMENT_ANALYSIS`, `chatId`, `assistantId`, `mensajesEvaluados`, `frustrationDetected`, `tendencia`, `duracionMs` y `resultado`.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldLogStructuredMetrics`.

## R33 — Privacidad de datos de usuario en logs
El sistema NO DEBE volcar el contenido textual íntegro de los mensajes ni datos personales en logs informativos o trazas de error.

**Verificación:** `SentimentAnalysisServiceImplTest#shouldNotLogUserMessageContents`.

---

### I. Límites arquitectónicos y calidad de código

## R34 — Pureza del analizador SentimentAnalyzer
El sistema DEBE mantener a `SentimentAnalyzer` dentro de `ec.com.technoloqie.auditbot.api.analyzer` libre de dependencias de Spring, JPA, RabbitMQ o Spring AI.

**Verificación:** `ArchitectureRulesTest#shouldKeepAnalyzersPure`.

## R35 — Confinamiento de Spring AI en paquete de integración
El sistema DEBE confinar el uso de componentes de Spring AI exclusivamente dentro de `ec.com.technoloqie.auditbot.api.integration.openai` y clases de configuración en `config`.

**Verificación:** `ArchitectureRulesTest#shouldConfineSpringAiInsideOpenAiIntegrationPackage`.

## R36 — Inyección exclusiva por constructor
El sistema DEBE utilizar inyección de dependencias exclusivamente a través de constructores, prohibiendo `@Autowired` en campos.

**Verificación:** `ArchitectureRulesTest#shouldEnforceConstructorInjection`.

## R37 — Ejecución exitosa de suite Maven
CUANDO se ejecuta `./mvnw clean verify`, el sistema DEBE finalizar con código de salida `0` y el 100% de las pruebas automatizadas en estado exitoso.

**Verificación:** Ejecución del comando de build en el ciclo de cierre.
