# Historial de sesiones

> Bitácora append-only. Cada sesión agrega un bloque al final.

---

## Sesión: US-01 (`aud_push_msg`) — Publicar solicitud de auditoría genérica

- **Feature:** US-01
- **Estado final:** `done`
- **Resultado:** implementación completa de T1–T13 de `specs/001-aud_push_msg/tasks.md`. `./mvnw test` y `./mvnw clean verify` finalizan con `BUILD SUCCESS`, 37/37 tests en verde.

### Resumen de lo implementado

- DTOs inmutables del contrato `conversation.audit.requested` v1.0 (`ConversationAuditRequestDto`, `AuditConversationDto`, `AuditMessageDto`).
- `ConversationAuditRequestValidator` (lógica pura, sin Spring/RabbitMQ) con 9 reglas de validación.
- Excepciones `InvalidAuditRequestException` y `AuditPublicationException` sin fuga de contenido sensible.
- Topología durable de RabbitMQ (`RabbitMqConfiguration`) y propiedades tipadas (`AuditMessagingProperties`).
- Adaptador de publicación `RabbitMqConversationAuditPublisher` con publisher confirms correlacionados, publisher returns, manejo de NACK/timeout/interrupción/fallos de serialización y envío.
- 37 tests en 8 clases: unitarios, contexto Spring, ArchUnit, e integración contra RabbitMQ real.
- Documentación del contrato operativo en `docs/data-model.md`.

### Desviaciones respecto al diseño original (autorizadas explícitamente por el usuario)

1. **Testcontainers → RabbitMQ real:** Docker no accesible en el entorno de ejecución (`permission denied` en `docker.sock`). Los tests de integración (`RabbitMqTopologyIntegrationTest`, `RabbitMqConversationAuditPublisherIntegrationTest`) usan un broker RabbitMQ real ya disponible en `127.0.0.1:5672`, con nombres de exchange/cola/routing-key únicos por ejecución y limpieza verificada vía management API.
2. **`spring-boot-starter-data-jpa` comentada en `pom.xml`:** el proyecto tenía esta dependencia sin ningún datasource configurado, lo que rompía `TecAuditbotWsApplicationTests.contextLoads` (fallo preexistente, confirmado con `git stash` temporal, no relacionado con US-01). El usuario decidió comentarla ya que ninguna feature implementada la usa aún.
3. **Bean `ObjectMapper` (Jackson 2) agregado en `RabbitMqConfiguration`:** al resolver el punto anterior se destapó que Spring Boot 4 auto-configura por defecto un `ObjectMapper` de Jackson 3, incompatible con el Jackson 2 usado por el contrato de esta feature. Se agregó un bean explícito con `@ConditionalOnMissingBean` para no romper otros contextos (como los de test) que ya provean uno.

### Archivos modificados/creados

Ver matriz completa de trazabilidad R1–R29 → test y detalle de archivos en `progress/impl_aud_push_msg.md`.

### Estado de `feature_list.json`

`US-01` marcada como `"status": "done"`.

---

## Sesión: US-02 (`aud_get_message`) — Consumir mensajes de auditoría

- **Feature:** US-02
- **Estado final:** `done`
- **Resultado:** implementación completa de T1–T23 de `specs/002-aud_get_message/tasks.md`. `PROFILE=local ./mvnw test` y `PROFILE=local ./mvnw clean verify` finalizan con `BUILD SUCCESS`, 101/101 tests en verde (37 de US-01 + 64 nuevos de US-02).

### Resumen de lo implementado

- JPA, MySQL driver, Flyway y H2 integrados con Spring Boot 4 (`spring-boot-starter-flyway`, `flyway-mysql`).
- Migración `V1__create_audit_result.sql` con tabla `audit_result`, índice `idx_audit_result_status` y unicidad `uk_audit_result_chat_id`.
- Modelo `AuditResult`, catálogo `AuditStatus` y repositorio `IAuditResultRepository`.
- Topología dead-letter en RabbitMQ (`auditbot.dlx`, `conversation.audit.dlq`, routing key `conversation.audit.failed`) y dead-letter arguments en `conversation.audit.queue`.
- Excepciones tipadas (`InvalidAuditMessageException`, `AuditProcessingException`) y cabeceras de diagnóstico en `AuditMessageHeaders`.
- Deserializador manual con Jackson 2 y preservación de `Instant` en UTC.
- Servicio de admisión `ConversationAuditIntakeServiceImpl` con validación previa, inserción atómica de claim, idempotencia y reintento explícito (`x-audit-retry`), desacoplado de analyzers vía `NoOpAuditPipelineTrigger`.
- Adaptador delgado `ConversationAuditMessageListener` con MDC, guardias de payload y redelivery, y log estructurado de nivel/outcome.
- Smoke test operativo manual (T21) verificado con publicación real: admisión y registro en MySQL local, descarte a DLQ de mensajes inválidos con cabeceras de diagnóstico.
- Documentación de arquitectura y contrato operativo en `docs/data-model.md`.

### Desviaciones respecto al diseño original (autorizadas)

1. **Testcontainers / Docker:** fallback sobre RabbitMQ local (`127.0.0.1:5672`) y H2 en memoria para integración.
2. **Datasource de desarrollo local:** conexión configurada hacia base de datos existente `db_desarrollo` con `desarrollo:desarrollo` y Flyway `baseline-on-migrate=true` con `baseline-version=0`.
3. **Manejo de duplicados en JPA/Hibernate:** para evitar la invalidación de la sesión/transacción por violación de unicidad (`UnexpectedRollbackException`), la atomicidad del claim y la consulta/actualización de duplicados se gestiona de forma aislada a nivel de repositorio.

### Archivos modificados/creados

Ver detalle completo y matriz de trazabilidad R1–R74 en `progress/impl_aud_get_message.md`.

### Estado de `feature_list.json`

`US-02` marcada como `"status": "done"`.

---

## Sesión 2026-11-17 — US-03: aud_detect_gaps (Detectar preguntas sin respuesta)

- **Feature:** US-03 — `aud_detect_gaps`
- **Spec:** `specs/003-aud_detect_gaps/{requirements,design,tasks}.md`
- **Implementación:** `progress/impl_aud_detect_gaps.md`
- **Resultado:** COMPLETO (`BUILD SUCCESS`, 124 tests pasando al 100%, 14 reglas ArchUnit en verde).

### Logros principales

- Migración Flyway `V2__create_audit_gap.sql`, entidad JPA `AuditGap` y repositorio `IAuditGapRepository` con eliminación por `auditId` y relación `@ManyToOne` hacia `AuditResult`.
- DTOs inmutables: `ConversationTurnDto`, `KnowledgeGapCandidateDto`, `GapAnalysisResponseDto` y `KnowledgeGapItemDto`.
- Analizador puro de dominio `KnowledgeGapAnalyzer` (100% libre de dependencias de Spring, JPA, AMQP o AI): normalización diacrítica, tokenización con stopwords en español, cálculo Jaccard y deduplicación greedy determinista con preservación del primer `messageId` cronológico y selección de pregunta canónica con mayor contenido informativo.
- Configuración de Spring AI con starter OpenAI (`spring-ai-starter-model-openai`) conectado a endpoint compatible `/v1` de Ollama, puerto `IKnowledgeGapAiClient` y adaptador `OpenAiKnowledgeGapAdapter` con prompt estructurado, salida JSON y timeout configurable.
- Refinamiento de `IAuditPipelineTrigger` a `trigger(String chatId, AuditConversationDto conversation)` y creación de `AuditPipelineOrchestratorImpl` con `@Primary`.
- Servicio transaccional `KnowledgeGapServiceImpl` con reconstrucción de turnos cronológicos usuario-asistente, filtrado de mensajes `SYSTEM`, persistencia en `audit_gap`, actualización en `PROCESSING`, manejo defensivo de reintentos, manejo controlado de fallos de IA marcando `FAILED` con prefijo `[AI_ERROR]` y emisión de log estructurado `etapa=GAP_DETECTION`.
- 14 reglas de arquitectura ArchUnit en `ArchitectureRulesTest` verificadas, incluyendo pureza de `analyzer..`, confinamiento de Spring AI en `integration.openai..` y prohibición de `@Autowired` en atributos.

### Archivos modificados/creados

Ver detalle completo y matriz de trazabilidad R1–R37 en `progress/impl_aud_detect_gaps.md`.

### Estado de `feature_list.json`

`US-03` marcada como `"status": "done"`.

---

## Sesión 2026-11-17 — US-04: aud_sentiment_analisys (Detectar frustración del usuario)

- **Feature:** US-04 — `aud_sentiment_analisys`
- **Spec:** `specs/004-aud_sentiment_analisys/{requirements,design,tasks}.md`
- **Implementación:** `progress/impl_aud_sentiment_analisys.md`
- **Resultado:** COMPLETO (`BUILD SUCCESS`, 157 tests pasando al 100%, 15 reglas ArchUnit en verde).

### Logros principales

- Migración Flyway `V3__create_audit_sentiment.sql`, enums `SentimentLabel` (`POSITIVE`, `NEUTRAL`, `NEGATIVE`, `FRUSTRATED`), `SentimentTrend` (`IMPROVING`, `WORSENING`, `STABLE`), entidad JPA `AuditSentiment` y repositorio `IAuditSentimentRepository` con búsqueda/eliminación por `auditId` y relación `@ManyToOne` hacia `AuditResult`.
- DTOs inmutables: `SentimentItemClassificationDto`, `SentimentAnalysisResponseDto`, `ProcessedSentimentDto` y `SentimentEvaluationResultDto`.
- Analizador puro de dominio `SentimentAnalyzer` (100% libre de dependencias de Spring, JPA, AMQP o AI): ponderación escalar de polaridad, partición determinista de mitades cronológicas para cálculo de $\Delta P$, clasificación de tendencia (`IMPROVING`, `WORSENING`, `STABLE`) y evaluación disyuntiva de frustración (pico $\ge 0.70$, ratio negativo $\ge 40\%$, consecutivos negativos $\ge 2$).
- Puerto `ISentimentAiClient` y adaptador `OpenAiSentimentAdapter` en `integration.openai` utilizando `ChatClient` de Spring AI con batching de mensajes de usuario en una única llamada, prompt estructurado, control de timeout (`CompletableFuture`) y fallback determinista `NEUTRAL` (score `0.000`) ante omisiones del modelo.
- Servicio transaccional `SentimentAnalysisServiceImpl` con extracción cronológica de mensajes `USER`, exclusión de `SYSTEM`/`ASSISTANT`, caso borde sin mensajes de usuario (`frustrationDetected = false`, `status = PROCESSING`), persistencia en lote de sentimientos, actualización atómica de `AuditResult`, manejo idempotente en reintentos y captura controlada de errores de IA marcando `FAILED` con mensaje truncado y prefijo `[AI_ERROR]`.
- Conexión secuencial en `AuditPipelineOrchestratorImpl`: brechas de conocimiento (`US-03`) $\rightarrow$ verificación de estado no fallido $\rightarrow$ análisis de sentimiento (`US-04`).
- Regla ArchUnit `shouldKeepSentimentAnalyzerPure` agregada en `ArchitectureRulesTest` (15 reglas en verde).
- Ejecución completa de `./mvnw clean verify` finalizada en verde (157 tests, 0 fallos).

### Archivos modificados/creados

Ver detalle completo y matriz de trazabilidad R1–R37 en `progress/impl_aud_sentiment_analisys.md`.

### Estado de `feature_list.json`

`US-04` marcada como `"status": "done"`.

---

## Sesión 2026-11-18 — US-05: aud_detect_leads (Identificar leads no calificados)

- **Feature:** US-05 — `aud_detect_leads`
- **Spec:** `specs/005-aud_detect_leads/{requirements,design,tasks}.md`
- **Implementación:** `progress/impl_aud_detect_leads.md`
- **Resultado:** COMPLETO (`BUILD SUCCESS`, 186 tests pasando al 100%, 16 reglas ArchUnit en verde).

### Logros principales

- Migración Flyway `V4__add_lost_lead_reason.sql` añadiendo la columna `lost_lead_reason VARCHAR(500) NULL` a `audit_result` y actualización de la entidad JPA `AuditResult`.
- DTOs inmutables: `LeadAnalysisResponseDto` (salida estructurada de IA) y `LeadEvaluationResultDto` (resultado evaluado por el dominio).
- Analizador de dominio puro `LeadAnalyzer` (100% libre de frameworks, Spring, JPA, AMQP o Spring AI): regla disyuntiva de oportunidad de venta perdida (`lostLeadDetected = commercialIntent && !closure`), acotamiento (clamping) de `leadScore` en `[0.000, 1.000]`, asignación neutra ante ausencia de interés comercial (`score = 0.000`, `reason = null`), y saneamiento/truncado de `lostLeadReason` a 500 caracteres.
- Puerto `ILeadDetectionAiClient` y adaptador `OpenAiLeadDetectionAdapter` en `integration.openai` utilizando `ChatClient` de Spring AI con prompt estructurado comercial, control de timeout (`CompletableFuture`), y procesamiento eficiente del diálogo en una única llamada por conversación.
- Servicio de aplicación transaccional `LeadDetectionServiceImpl`: extracción cronológica descartando mensajes `SYSTEM`, manejo defensivo de casos borde sin mensajes de usuario (`lostLeadDetected = false`, `leadScore = 0.000`, omisión de llamada a IA), persistencia atómica de resultados en `AuditResult` preservando `status = PROCESSING`, manejo idempotente en reintentos y captura controlada de fallos de IA marcando `FAILED` con prefijo `[AI_ERROR]` sin romper el middleware.
- Incorporación de la etapa 3 en `AuditPipelineOrchestratorImpl`: gaps (`US-03`) $\rightarrow$ sentimiento (`US-04`) $\rightarrow$ leads (`US-05`), omitiendo etapas subsecuentes si alguna previa marca `FAILED`.
- Regla ArchUnit `shouldKeepLeadAnalyzerPure` añadida en `ArchitectureRulesTest` (16 reglas en verde).
- Ejecución completa de pruebas `./mvnw test` con 186 tests pasando al 100% (29 tests nuevos para US-05).

### Archivos modificados/creados

Ver detalle completo y matriz de trazabilidad R1–R24 en `progress/impl_aud_detect_leads.md`.

### Estado de `feature_list.json`

`US-05` marcada como `"status": "done"`.
