# Requirements — US-05 Identificar leads no calificados (`aud_detect_leads`)

## Propósito

Definir la detección y evaluación automática de oportunidades comerciales y de venta en conversaciones de chatbots empresariales. Esta funcionalidad procesa el diálogo completo de la conversación, invoca el modelo de IA mediante el adaptador Spring AI OpenAI-compatible en lote para clasificar la intención comercial del usuario, la intensidad del interés (`lead_score`), y la consecución o ausencia de un cierre efectivo (`closure_achieved`). Un analizador de dominio puro (`LeadAnalyzer`) evalúa las reglas deterministas para dictaminar si constituye una oportunidad de venta perdida (`lost_lead_detected`), valida el score y normaliza el motivo descriptivo (`lost_lead_reason`). Finalmente, el servicio transaccional (`LeadDetectionServiceImpl`) persiste los resultados en `audit_result`, manteniendo el estado en `PROCESSING` para la siguiente etapa de recomendaciones (US-06).

---

## Glosario

- **Intención Comercial / de Compra (`commercialIntentDetected`):** indicador booleano devuelto por la IA que señala si el usuario expresó interés en adquirir productos, contratar servicios, solicitar precios, cotizaciones, demostraciones o planes comerciales.
- **Cierre Efectivo (`closureAchieved`):** indicador booleano devuelto por la IA que señala si la interacción concluyó satisfactoriamente en términos comerciales (se capturaron datos de contacto como email o teléfono, se derivó exitosamente a un agente comercial humano, o se concretó la venta).
- **Lead Calificado Perdido (`lost_lead_detected`):** bandera booleana persistida en `audit_result` que se activa en `true` cuando se detecta intención comercial (`commercialIntentDetected == true`) pero NO se alcanzó un cierre (`closureAchieved == false`).
- **Puntaje de Lead (`lead_score`):** valor decimal en el rango cerrado `0.000` a `1.000` (`DECIMAL(4,3)`) que cuantifica el grado de interés y valor comercial del prospecto. Si no hubo intención comercial, su valor es `0.000`.
- **Motivo de Pérdida (`lost_lead_reason`):** texto explicativo de hasta 500 caracteres almacenado en `audit_result` que describe por qué se perdió la oportunidad de negocio. Es `null` si no es una oportunidad perdida.
- **Analizador Puro (`LeadAnalyzer`):** componente en `api.analyzer` 100% desacoplado de frameworks que aplica reglas lógicas, validación de rangos numéricos y saneamiento de cadenas.
- **Estado Transicional `PROCESSING`:** estado en `audit_result` que indica que la auditoría continúa en ejecución hacia la etapa US-06 (recomendaciones).

---

## Requisitos funcionales

### A. Preparación y filtrado de la conversación

## R1 — Identificación de mensajes evaluables
CUANDO se inicia la evaluación de leads para una conversación, el sistema DEBE consolidar la secuencia cronológica de mensajes de usuario (`USER`) y asistente (`ASSISTANT`) para proporcionar el contexto conversacional completo.

**Verificación:** `LeadDetectionServiceImplTest#shouldProvideOrderedConversationContext`.

## R2 — Exclusión de mensajes de sistema
El sistema NO DEBE enviar mensajes con rol `SYSTEM` al modelo de evaluación comercial.

**Verificación:** `LeadDetectionServiceImplTest#shouldFilterOutSystemMessages`.

## R3 — Conversación sin mensajes de usuario
CUANDO una conversación no contiene ningún mensaje emitido por un usuario con rol `USER`, el sistema DEBE omitir la llamada a la IA y persistir `lostLeadDetected = false`, `leadScore = 0.000` y `lostLeadReason = null`.

**Verificación:** `LeadDetectionServiceImplTest#shouldHandleConversationWithoutUserMessages`.

---

### B. Evaluación comercial con Spring AI OpenAI-compatible

## R4 — Invocación en llamada única por conversación
CUANDO se analiza la oportunidad comercial de una conversación con mensajes de usuario, el sistema DEBE invocar al modelo de IA en una única llamada estructurada enviando el diálogo completo.

**Verificación:** `OpenAiLeadDetectionAdapterTest#shouldSendConversationInSingleCall`.

## R5 — Prompt del sistema para detección de leads
CUANDO se realiza la petición al modelo de IA, el sistema DEBE aplicar un system prompt estricto que instruya identificar intención de compra, puntaje de calidad del lead entre 0 y 1, estado de cierre de la oportunidad y motivo de pérdida.

**Verificación:** `OpenAiLeadDetectionAdapterTest#shouldApplyStrictCommercialPrompt`.

## R6 — Deserialización de salida estructurada
CUANDO el modelo responde, el sistema DEBE deserializar la respuesta en un DTO estructurado conteniendo `commercialIntentDetected` (boolean), `leadScore` (BigDecimal/double), `closureAchieved` (boolean) y `reason` (String).

**Verificación:** `OpenAiLeadDetectionAdapterTest#shouldDeserializeStructuredLeadResponse`.

## R7 — Timeout configurable de la llamada a IA
CUANDO se invoca al modelo de IA, el sistema DEBE interrumpir la espera si se supera el tiempo límite configurado en `auditbot.ai.openai.timeout-seconds` y lanzar una excepción controlada.

**Verificación:** `OpenAiLeadDetectionAdapterTest#shouldTimeoutWhenConfiguredTimeExceeded`.

---

### C. Reglas de negocio y análisis puro (`LeadAnalyzer`)

## R8 — Condición de oportunidad de venta perdida
CUANDO `commercialIntentDetected` es `true` y `closureAchieved` es `false`, el sistema DEBE evaluar `lostLeadDetected = true`.

**Verificación:** `LeadAnalyzerTest#shouldDetectLostLeadWhenIntentTrueAndClosureFalse`.

## R9 — Condición de ausencia de intención comercial
CUANDO `commercialIntentDetected` es `false`, el sistema DEBE evaluar `lostLeadDetected = false`, `leadScore = 0.000` y `lostLeadReason = null`, independientemente del valor de `closureAchieved`.

**Verificación:** `LeadAnalyzerTest#shouldSetZeroScoreAndNullReasonWhenNoCommercialIntent`.

## R10 — Condición de oportunidad comercial cerrada con éxito
CUANDO `commercialIntentDetected` es `true` y `closureAchieved` es `true`, el sistema DEBE evaluar `lostLeadDetected = false`, preservar el `leadScore` evaluado y establecer `lostLeadReason = null`.

**Verificación:** `LeadAnalyzerTest#shouldNotMarkLostLeadWhenClosureAchieved`.

## R11 — Acotamiento del puntaje de lead (`leadScore`)
CUANDO el modelo retorne un `leadScore` menor a `0.000` o mayor a `1.000`, el sistema DEBE acotar (clamp) el valor resultante exactamente a `0.000` o `1.000` con escala decimal de 3 dígitos.

**Verificación:** `LeadAnalyzerTest#shouldClampLeadScoreToValidRange`.

## R12 — Saneamiento y truncado del motivo de pérdida
CUANDO se evalúa un `lostLeadReason` no nulo cuya longitud excede 500 caracteres, el sistema DEBE truncar el texto a exactamente 500 caracteres.

**Verificación:** `LeadAnalyzerTest#shouldTruncateLostLeadReasonTo500Chars`.

## R13 — Saneamiento de espacios en blanco
CUANDO se recibe un `lostLeadReason` que contiene espacios en blanco al inicio o al final, el sistema DEBE aplicar trim al texto.

**Verificación:** `LeadAnalyzerTest#shouldTrimLostLeadReason`.

---

### D. Persistencia y esquema de datos

## R14 — Migración Flyway de esquema
El sistema DEBE aplicar la migración `V4__add_lost_lead_reason.sql` añadiendo la columna `lost_lead_reason VARCHAR(500) NULL` a la tabla `audit_result`.

**Verificación:** `AuditResultRepositoryLeadTest#shouldVerifyFlywayMigrationV4`.

## R15 — Mapeo en entidad raíz `AuditResult`
El sistema DEBE mapear en la entidad `AuditResult` el atributo `lostLeadReason` asociado a la columna `lost_lead_reason`.

**Verificación:** `AuditResultRepositoryLeadTest#shouldPersistAndRetrieveLostLeadFields`.

## R16 — Actualización atómica de resultados de lead
CUANDO culmina la evaluación de leads para una conversación, el sistema DEBE actualizar en `audit_result` los campos `lost_lead_detected`, `lead_score`, `lost_lead_reason` y `updated_date` en una única transacción.

**Verificación:** `LeadDetectionServiceImplTest#shouldUpdateAuditResultFieldsAtomically`.

## R17 — Preservación del estado transicional
CUANDO se persisten los resultados de leads sin fallos, el sistema DEBE mantener el campo `status` en `PROCESSING`.

**Verificación:** `LeadDetectionServiceImplTest#shouldKeepStatusInProcessing`.

## R18 — Idempotencia ante reintentos
CUANDO se recibe una re-ejecución de auditoría para un `chatId` previamente analizado, el sistema DEBE sobreescribir los valores de `lost_lead_detected`, `lead_score` y `lost_lead_reason` con los nuevos cálculos.

**Verificación:** `LeadDetectionServiceImplTest#shouldOverwritePreviousLeadResultsOnRetry`.

---

### E. Orquestación del pipeline y manejo de fallos

## R19 — Invocación secuencial en el orquestador
CUANDO finaliza el análisis de sentimiento (US-04) y el registro `audit_result` no está en estado `FAILED`, el sistema DEBE invocar inmediatamente la etapa de detección de leads.

**Verificación:** `AuditPipelineOrchestratorTest#shouldTriggerLeadDetectionAfterSentimentSuccess`.

## R20 — Omisión ante fallo previo
SI el registro `audit_result` se encuentra en estado `FAILED` tras las etapas previas ENTONCES el sistema NO DEBE ejecutar la detección de leads.

**Verificación:** `AuditPipelineOrchestratorTest#shouldSkipLeadDetectionWhenPreviousStageFailed`.

## R21 — Manejo de fallo en llamada de IA
SI la invocación al modelo de IA falla por error de red, timeout o respuesta malformada ENTONCES el sistema DEBE marcar `audit_result` con `status = FAILED`, registrar `error_message` con prefijo `[AI_ERROR]` y no propagar excepción no controlada hacia el pipeline.

**Verificación:** `LeadDetectionServiceImplTest#shouldMarkAuditFailedOnAiError`.

## R22 — Observabilidad y logging estructurado
CUANDO se completa la etapa de leads (con éxito o con error), el sistema DEBE registrar un log con `@Slf4j` conteniendo `chatId`, `etapa=LEAD_DETECTION`, `lostLeadDetected` y `leadScore`.

**Verificación:** `LeadDetectionServiceImplTest#shouldEmitStructuredLog`.

---

### F. Conformidad arquitectónica

## R23 — Pureza del analizador de leads
El sistema DEBE mantener la clase `LeadAnalyzer` en el paquete `analyzer` sin dependencias ni imports de Spring, JPA, RabbitMQ o Spring AI.

**Verificación:** `ArchitectureRulesTest#shouldKeepLeadAnalyzerPure`.

## R24 — Confinamiento del cliente de IA
El sistema DEBE confinar los tipos `ILeadDetectionAiClient` y `OpenAiLeadDetectionAdapter` exclusivamente al paquete `integration.openai`.

**Verificación:** `ArchitectureRulesTest#shouldConfineLeadAiClientToOpenAiPackage`.
