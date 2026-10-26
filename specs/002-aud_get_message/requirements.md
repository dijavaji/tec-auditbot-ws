# Requirements — US-02 Consumir mensajes de auditoría

## Propósito

Definir el consumo fiable, validado e idempotente de los eventos `conversation.audit.requested` que llegan a `conversation.audit.queue`, hasta dejar cada conversación admitida persistida como auditoría en estado inicial. Esta especificación cubre exclusivamente la admisión: la publicación corresponde a US-01 y el pipeline de análisis a US-03 y siguientes.

## Glosario

- **Mensaje válido:** cuerpo AMQP deserializable al contrato `1.0` que además supera todas las reglas de `ConversationAuditRequestValidator`.
- **Admisión:** creación del registro de control en `audit_result` para un `chatId` y su promoción a estado de proceso.
- **Claim:** inserción del registro de `audit_result` que reserva un `chatId` frente a consumos concurrentes.
- **Duplicado:** mensaje cuyo `chatId` ya tiene registro en `audit_result` y que no solicita reintento explícito.
- **Reintento explícito:** consumo marcado con el header AMQP `x-audit-retry=true`.
- **Error permanente:** fallo que no cambia al repetir el mismo mensaje (JSON malformado, esquema inválido, payload sobredimensionado, `schemaVersion` no soportada).
- **Error transitorio:** fallo de infraestructura que puede desaparecer al repetir el mismo mensaje (base de datos no disponible, timeout, deadlock).
- **Contenido sensible:** cuerpo JSON completo, valor de `messages[].content` o credenciales del broker o de la base de datos.
- **DLQ:** cola de parking `conversation.audit.dlq`, alcanzada a través del exchange `auditbot.dlx` con routing key `conversation.audit.failed`.

## Requisitos funcionales

### A. Consumo desde `conversation.audit.queue`

## R1 — Listener registrado
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE registrar un listener con id `conversationAuditListener` sobre la cola resuelta desde `auditbot.messaging.queue`.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldConsumeValidMessageAndPersistAudit`.

## R2 — Nombre de cola configurable
El sistema NO DEBE fijar el nombre de la cola de consumo como literal en código Java.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldConsumeFromConfiguredQueueName`.

## R3 — Acuse automático
El sistema DEBE consumir con acuse automático gestionado por el contenedor mediante `spring.rabbitmq.listener.simple.acknowledge-mode=auto`.

**Verificación:** `ApplicationRabbitMqConfigurationTest#shouldBindListenerContainerSettings`.

## R4 — Rechazo sin requeue
El sistema DEBE configurar `spring.rabbitmq.listener.simple.default-requeue-rejected=false`.

**Verificación:** `ApplicationRabbitMqConfigurationTest#shouldBindListenerContainerSettings`.

## R5 — Valores por defecto del contenedor
El sistema DEBE arrancar el contenedor de listeners con `prefetch=1`, `concurrency=2` y `max-concurrency=4`.

**Verificación:** `ApplicationRabbitMqConfigurationTest#shouldBindListenerContainerSettings`.

## R6 — Ajuste del contenedor por configuración
El sistema DEBE permitir sobrescribir `prefetch`, `concurrency` y `max-concurrency` mediante propiedades `spring.rabbitmq.listener.simple.*`.

**Verificación:** `ApplicationRabbitMqConfigurationTest#shouldOverrideListenerContainerSettings`.

## R7 — Mensaje crudo en la frontera
CUANDO llega un mensaje a la cola de consumo, el sistema DEBE entregarlo al listener como `org.springframework.amqp.core.Message` sin conversión previa por un `MessageConverter`.

**Verificación:** `ConversationAuditMessageListenerTest#shouldDelegateValidMessageToIntakeService`.

## R8 — Deserialización con Jackson 2
CUANDO el listener deserializa el cuerpo, el sistema DEBE usar el bean `auditObjectMapper` de Jackson 2 y preservar los `Instant` en UTC.

**Verificación:** `AuditMessageDeserializerTest#shouldDeserializeVersionOneContract` y `AuditMessageDeserializerTest#shouldPreserveInstantsInUtc`.

## R9 — Delegación al puerto de admisión
CUANDO el listener obtiene un mensaje deserializado, el sistema DEBE delegar la admisión en `IConversationAuditIntakeService`.

**Verificación:** `ConversationAuditMessageListenerTest#shouldDelegateValidMessageToIntakeService`.

## R10 — Adaptador delgado
El listener NO DEBE contener reglas de negocio, acceder a repositorios ni abrir transacciones.

**Verificación:** `ConversationAuditMessageListenerTest#shouldNotAccessRepositoryOrOpenTransaction` y `ArchitectureRulesTest#shouldKeepListenerFreeOfBusinessLogic`.

## R11 — Persistencia de la admisión
CUANDO el sistema consume un mensaje válido y no duplicado, el sistema DEBE persistir un registro de auditoría para ese `chatId`.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldConsumeValidMessageAndPersistAudit`.

## R12 — Acuse del mensaje consumido
CUANDO el método del listener retorna sin excepción, el sistema DEBE acusar el mensaje dejando `conversation.audit.queue` sin mensajes pendientes.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldLeaveMainQueueEmptyAfterSuccessfulConsumption`.

### B. Validación de esquema y enrutamiento a DLQ

## R13 — Validación previa a efectos laterales
CUANDO el sistema obtiene un mensaje deserializado, el sistema DEBE validarlo con `ConversationAuditRequestValidator` antes de cualquier escritura en base de datos.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldValidateContractBeforeClaim`.

## R14 — Reglas de validación no duplicadas
El sistema NO DEBE reimplementar las reglas de validación del contrato `1.0` en clases nuevas.

**Verificación:** `ArchitectureRulesTest#shouldReuseContractValidatorInConsumption`.

## R15 — Enrutamiento de mensajes inválidos
SI el cuerpo no es deserializable al contrato `1.0` o `ConversationAuditRequestValidator` lo rechaza ENTONCES el sistema DEBE enrutarlo a `conversation.audit.dlq` sin reintentos.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldRouteInvalidSchemaMessageToDlq` y `ConversationAuditMessageListenerIntegrationTest#shouldRouteMalformedJsonToDlq`.

## R16 — Cero escrituras ante mensaje inválido
SI el sistema descarta un mensaje antes de crear el claim ENTONCES el sistema NO DEBE escribir ningún registro en `audit_result`.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldNotPersistAnythingWhenSchemaIsInvalid`.

## R17 — Dead letter exchange durable
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE declarar `auditbot.dlx` como exchange directo, durable y no auto-delete.

**Verificación:** `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareDurableDeadLetterExchange`.

## R18 — Dead letter queue durable
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE declarar `conversation.audit.dlq` como cola durable, no exclusiva y no auto-delete.

**Verificación:** `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareDurableDeadLetterQueue`.

## R19 — Binding de la DLQ
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE enlazar `conversation.audit.dlq` a `auditbot.dlx` mediante `conversation.audit.failed`.

**Verificación:** `RabbitMqDeadLetterTopologyIntegrationTest#shouldBindDeadLetterQueueWithFailedRoutingKey`.

## R20 — Argumentos de dead-letter en la cola principal
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE declarar `conversation.audit.queue` con los argumentos `x-dead-letter-exchange=auditbot.dlx` y `x-dead-letter-routing-key=conversation.audit.failed`, manteniéndola durable, no exclusiva y no auto-delete.

**Verificación:** `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareMainQueueWithDeadLetterArguments`.

## R21 — DLQ como cola de parking
El sistema NO DEBE declarar argumentos de dead-letter en `conversation.audit.dlq`.

**Verificación:** `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareDurableDeadLetterQueue`.

## R22 — Headers de diagnóstico
CUANDO el sistema envía un mensaje a la DLQ, el sistema DEBE establecer los headers `x-audit-failure-reason`, `x-audit-invalid-fields` cuando existan rutas de campos inválidos y `x-audit-failed-at` en ISO-8601 UTC.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldPublishDiagnosticHeadersOnDeadLetter`.

## R23 — Headers sin contenido sensible
Los headers de diagnóstico NO DEBEN contener el cuerpo JSON completo ni `messages[].content`.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldPublishDiagnosticHeadersOnDeadLetter`.

## R24 — Payload sobredimensionado
SI el cuerpo recibido excede `auditbot.messaging.max-payload-size` ENTONCES el sistema DEBE enrutarlo a la DLQ sin reintentos.

**Verificación:** `ConversationAuditMessageListenerTest#shouldRejectOversizedPayloadWithoutRequeue`.

## R25 — Comprobación del límite antes de deserializar
SI el cuerpo recibido excede `auditbot.messaging.max-payload-size` ENTONCES el sistema NO DEBE deserializar el cuerpo.

**Verificación:** `ConversationAuditMessageListenerTest#shouldRejectOversizedPayloadWithoutRequeue`.

### C. Idempotencia por `chatId`

## R26 — Registro de control persistido
CUANDO el sistema admite una conversación, el sistema DEBE persistir el registro de control en la tabla `audit_result` mediante la entidad `AuditResult`.

**Verificación:** `AuditResultRepositoryTest#shouldPersistAndFindAuditByChatId`.

## R27 — Restricción única por `chatId`
El sistema DEBE declarar la restricción única `uk_audit_result_chat_id` sobre la columna `chat_id`.

**Verificación:** `AuditResultRepositoryTest#shouldRejectDuplicatedChatId`.

## R28 — Detección atómica de duplicados
SI la inserción del claim viola la restricción única de `chat_id` ENTONCES el sistema DEBE tratar el `chatId` como ya procesado.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldTreatUniqueConstraintViolationAsDuplicate`.

## R29 — Sin consulta previa de existencia
El sistema NO DEBE decidir la duplicidad de un `chatId` con una consulta de existencia previa a la inserción del claim.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldClaimChatIdWithoutPreliminaryExistenceQuery`.

## R30 — Duplicado descartado
SI llega un mensaje cuyo `chatId` ya tiene registro en estado `PENDING`, `PROCESSING` o `COMPLETED` y no se solicitó reintento explícito ENTONCES el sistema DEBE descartarlo y acusarlo sin volver a analizarlo.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldSkipAlreadyProcessedChatId` y `ConversationAuditMessageListenerIntegrationTest#shouldNotReprocessDuplicatedChatId`.

## R31 — Duplicado no es error
SI el sistema detecta un duplicado ENTONCES el sistema NO DEBE enrutar el mensaje a la DLQ.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldNotReprocessDuplicatedChatId`.

## R32 — Registro del duplicado
SI el sistema detecta un duplicado ENTONCES el sistema DEBE registrar una línea de log de nivel `WARN` con el `chatId` y el `eventId` recibidos.

**Verificación:** `ConversationAuditMessageListenerTest#shouldLogDuplicateOutcomeAtWarnLevel`.

## R33 — Reintento explícito admitido
DONDE el mensaje incluye el header `x-audit-retry=true` y el `chatId` tiene registro en estado `FAILED` o `COMPLETED`, el sistema DEBE reprocesarlo estableciendo su `status` en `PROCESSING`.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldReprocessFailedChatIdWhenRetryRequested` y `ConversationAuditMessageListenerIntegrationTest#shouldReprocessWhenRetryHeaderIsPresent`.

## R34 — Contabilidad del reintento
DONDE el sistema admite un reintento explícito, el sistema DEBE incrementar `attempt_count`, limpiar `error_message` y actualizar `updated_date` del registro.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldIncrementAttemptCountOnExplicitRetry`.

## R35 — Reintento sobre procesamiento en curso
SI el `chatId` tiene registro en estado `PROCESSING` y el mensaje incluye `x-audit-retry=true` ENTONCES el sistema NO DEBE reprocesarlo.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldRejectRetryWhileChatIdIsProcessing`.

## R36 — Estado inicial de la admisión
CUANDO el sistema admite un `chatId` por primera vez, el sistema DEBE persistir el registro con `status = PENDING`.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldAdmitNewChatIdWithPendingThenProcessing`.

## R37 — Promoción a procesamiento
CUANDO el sistema completa la admisión de un `chatId`, el sistema DEBE promover su registro a `status = PROCESSING`.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldAdmitNewChatIdWithPendingThenProcessing`.

## R38 — Sin estado completado
El sistema NO DEBE establecer `status = COMPLETED` en ningún registro de `audit_result`.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldNeverSetCompletedStatus`.

## R39 — Transacción única de admisión
CUANDO el sistema ejecuta el claim y la transición de estado, el sistema DEBE hacerlo dentro de un único método `@Transactional` de `ConversationAuditIntakeServiceImpl`.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldAdmitNewChatIdWithPendingThenProcessing` y `ArchitectureRulesTest#shouldKeepTransactionalInsideServiceLayer`.

## R40 — Frontera transaccional
El sistema NO DEBE anotar con `@Transactional` clases de `integration`, de `repository` ni el listener de consumo.

**Verificación:** `ArchitectureRulesTest#shouldKeepTransactionalInsideServiceLayer`.

### D. Clasificación de errores y política de reintentos

## R41 — Señalización de error permanente
SI ocurre un error permanente ENTONCES el sistema DEBE señalarlo con `InvalidAuditMessageException`.

**Verificación:** `AuditMessageDeserializerTest#shouldWrapMalformedJsonAsPermanentError`.

## R42 — Señalización de error transitorio
SI ocurre un error transitorio ENTONCES el sistema DEBE señalarlo con `AuditProcessingException`.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldWrapDataAccessFailureAsTransientError`.

## R43 — Rechazo definitivo del error permanente
SI el listener recibe un error permanente ENTONCES el sistema DEBE lanzar `AmqpRejectAndDontRequeueException`.

**Verificación:** `ConversationAuditMessageListenerTest#shouldRejectInvalidSchemaWithoutRequeue` y `ConversationAuditMessageListenerTest#shouldRejectMalformedJsonWithoutRequeue`.

## R44 — Error permanente sin reintentos
SI el listener recibe un error permanente ENTONCES el sistema NO DEBE consumir intentos de reintento antes de enviar el mensaje a la DLQ.

**Verificación:** `ConversationAuditMessageListenerTest#shouldRejectInvalidSchemaWithoutRequeue`.

## R45 — Reintento acotado del error transitorio
SI el listener recibe un error transitorio ENTONCES el sistema DEBE reintentar el mensaje con el retry del contenedor configurado en `max-attempts=3`, `initial-interval=1s`, `multiplier=2` y `max-interval=10s`.

**Verificación:** `ConversationAuditMessageListenerTest#shouldRethrowTransientErrorForBoundedRetry` y `ApplicationRabbitMqConfigurationTest#shouldBindListenerRetrySettings`.

## R46 — Reintentos agotados
CUANDO se agotan los intentos de reintento de un mensaje, el sistema DEBE enrutarlo a la DLQ con `x-audit-failure-reason` indicando reintentos agotados.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldStopRedeliveryAfterBoundedRetries`.

## R47 — Sin requeue ilimitado
El sistema NO DEBE devolver a `conversation.audit.queue` un mensaje que ya rechazó.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldStopRedeliveryAfterBoundedRetries`.

## R48 — Guardia de redelivery
SI el mensaje llega con `redelivered=true` y el conteo acumulado en el header `x-death` alcanza `auditbot.messaging.max-redeliveries` ENTONCES el sistema DEBE enrutarlo a la DLQ sin procesarlo.

**Verificación:** `ConversationAuditMessageListenerTest#shouldRouteToDlqWhenRedeliveryLimitReached`.

## R49 — Estado del mensaje aparcado
SI un mensaje con claim creado termina en la DLQ ENTONCES el sistema DEBE dejar su registro `audit_result` en `status = FAILED` con `error_message` legible.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldMarkAuditAsFailedWithReadableMessage`.

## R50 — `error_message` sin contenido sensible
El valor persistido en `error_message` NO DEBE contener contenido sensible.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldMarkAuditAsFailedWithReadableMessage`.

### E. Observabilidad

## R51 — Línea única de resultado
CUANDO termina el consumo de un mensaje, el sistema DEBE registrar exactamente una línea de log con las claves `resultado`, `chatId`, `eventId`, `messageId`, `queue`, `intento` y `assistantId`.

**Verificación:** `ConversationAuditMessageListenerTest#shouldLogMetadataWithoutConversationContent`.

## R52 — Niveles de log por desenlace
CUANDO registra el resultado del consumo, el sistema DEBE usar nivel `INFO` para admisión correcta, `WARN` para duplicado y error permanente, y `ERROR` para error transitorio con reintentos agotados.

**Verificación:** `ConversationAuditMessageListenerTest#shouldLogEachOutcomeWithItsLevel`.

## R53 — Logs y excepciones sin contenido sensible
El sistema NO DEBE incluir contenido sensible en logs ni en mensajes de excepción.

**Verificación:** `ConversationAuditMessageListenerTest#shouldLogMetadataWithoutConversationContent` y `AuditResultMapperTest#shouldNotCopyMessageContentIntoEntity`.

## R54 — Correlación por MDC
CUANDO el listener inicia el consumo de un mensaje, el sistema DEBE colocar `chatId` y `eventId` en el MDC.

**Verificación:** `ConversationAuditMessageListenerTest#shouldClearMdcAfterEachMessage`.

## R55 — Limpieza del MDC
CUANDO el listener termina el consumo de un mensaje, el sistema DEBE limpiar el MDC en un bloque `finally`.

**Verificación:** `ConversationAuditMessageListenerTest#shouldClearMdcAfterEachMessage`.

## R56 — Logger de Lombok
El sistema DEBE registrar los eventos de consumo mediante el logger provisto por `@Slf4j`.

**Verificación:** `ConversationAuditMessageListenerTest#shouldLogEachOutcomeWithItsLevel`.

## R57 — Sin salida por consola
El sistema NO DEBE usar `System.out.println()` ni `e.printStackTrace()` en las clases del flujo de consumo.

**Verificación:** `ArchitectureRulesTest#shouldNotUseConsoleLoggingInConsumptionFlow`.

### F. Límites arquitectónicos y cierre

## R58 — Sin pipeline de análisis
El sistema NO DEBE invocar analyzers, Spring AI u Ollama, ni crear entidades `AuditGap`, `AuditSentiment` o `AuditRecommendation` durante el consumo.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldNotInvokeAnalyzers` y `ArchitectureRulesTest#shouldKeepConsumptionIndependentFromAnalyzers`.

## R59 — Punto de extensión del pipeline
El sistema DEBE exponer el punto de extensión `IAuditPipelineTrigger` con la implementación no-op `NoOpAuditPipelineTrigger`.

**Verificación:** `NoOpAuditPipelineTriggerTest#shouldDoNothingOnTrigger`.

## R60 — Sin superficie HTTP nueva
El sistema NO DEBE añadir ni modificar endpoints HTTP en esta feature.

**Verificación:** `ArchitectureRulesTest#shouldNotExposeAuditIntakeHttpEndpoint`.

## R61 — Frontera AMQP
El código fuera de `integration.rabbitmq` y `config` NO DEBE importar tipos de Spring AMQP o del cliente RabbitMQ.

**Verificación:** `ArchitectureRulesTest#shouldKeepRabbitMqTypesInsideAdapterBoundary`.

## R62 — Frontera JPA
El código fuera de `model` y `repository` NO DEBE importar tipos JPA.

**Verificación:** `ArchitectureRulesTest#shouldKeepJpaTypesInsideModelAndRepository`.

## R63 — Verificación completa
El sistema DEBE mantener `./mvnw clean verify` finalizando con `BUILD SUCCESS` y todos los tests verdes, incluidos los de US-01.

**Verificación:** ejecución de la suite completa con `./mvnw clean verify`, incluido `TecAuditbotWsApplicationTests#contextLoads` con JPA activo; matriz final `R<n>` → método de test en `progress/impl_aud_get_message.md`.

## Requisitos no funcionales

## R64 — Configuración de la topología de dead-letter
El sistema DEBE permitir configurar la topología de dead-letter mediante `auditbot.messaging.dead-letter-exchange`, `auditbot.messaging.dead-letter-queue` y `auditbot.messaging.dead-letter-routing-key`, con los valores por defecto `auditbot.dlx`, `conversation.audit.dlq` y `conversation.audit.failed`.

**Verificación:** `AuditMessagingPropertiesTest#shouldUseDefaultDeadLetterTopology` y `AuditMessagingPropertiesTest#shouldBindCustomDeadLetterTopology`.

## R65 — Límite de redeliveries configurable
El sistema DEBE permitir configurar `auditbot.messaging.max-redeliveries` con valor por defecto `3`.

**Verificación:** `AuditMessagingPropertiesTest#shouldUseThreeDefaultMaxRedeliveries`.

## R66 — Latencia de admisión
CUANDO el sistema admite un mensaje válido sin pipeline de IA, el sistema DEBE completar la admisión en menos de 200 ms p95.

**Verificación:** `ConversationAuditMessageListenerIntegrationTest#shouldAdmitMessageUnderLatencyBudget`.

## R67 — Sin sesión abierta en la vista
El sistema DEBE configurar `spring.jpa.open-in-view=false`.

**Verificación:** `ApplicationPersistenceConfigurationTest#shouldDisableOpenInView`.

## R68 — Límite de tamaño compartido
El sistema NO DEBE declarar una propiedad de tamaño máximo distinta de `auditbot.messaging.max-payload-size` para el consumo.

**Verificación:** `AuditMessagingPropertiesTest#shouldUseOneMegabyteDefaultPayloadLimit`.

## R69 — Longitud de `error_message`
CUANDO el sistema persiste `error_message`, el sistema DEBE truncarlo a 500 caracteres añadiendo un sufijo explícito de truncado.

**Verificación:** `ConversationAuditIntakeServiceImplTest#shouldTruncateErrorMessageToFiveHundredCharacters`.

## R70 — Esquema gestionado por migración
El sistema DEBE crear la tabla `audit_result` mediante la migración Flyway `V1__create_audit_result.sql`.

**Verificación:** `ApplicationPersistenceConfigurationTest#shouldEnableFlywayMigrations` y smoke test manual de nivel 3 descrito en `design.md`.

## R71 — Esquema no modificado por Hibernate
MIENTRAS el perfil activo es `prod`, el sistema NO DEBE modificar el esquema de base de datos desde Hibernate.

**Verificación:** `ApplicationPersistenceConfigurationTest#shouldValidateSchemaWithoutDdlGeneration`.

## R72 — Credenciales externas
El sistema DEBE obtener url, usuario y contraseña del datasource desde variables de entorno sin credenciales versionadas.

**Verificación:** `ApplicationPersistenceConfigurationTest#shouldBindDatasourceFromEnvironment`.

## R73 — Perfil local autocontenido
DONDE el perfil activo es `local`, el sistema DEBE resolver el datasource desde `application-local.properties` con valores por defecto de desarrollo.

**Verificación:** `ApplicationPersistenceConfigurationTest#shouldResolveLocalDatasourceDefaults`.

## R74 — Sin acuse sin claim confirmado
SI la transacción de admisión no se confirma ENTONCES el sistema NO DEBE acusar el mensaje como consumido.

**Verificación:** `ConversationAuditMessageListenerTest#shouldNotAcknowledgeWhenClaimIsNotConfirmed`.

## Matriz de cobertura

Mapeo de los 32 criterios de aceptación de `tmp/US-02-enriched-us.md` a los requirements de este documento. Ningún criterio queda huérfano.

| Criterio | Requirements |
|---|---|
| CA-01 | R1, R2 |
| CA-02 | R3, R4, R12 |
| CA-03 | R5, R6 |
| CA-04 | R7, R8 |
| CA-05 | R9, R10 |
| CA-06 | R11, R12 |
| CA-07 | R13, R14 |
| CA-08 | R15, R16 |
| CA-09 | R17, R18, R19, R20, R64 |
| CA-10 | R21 |
| CA-11 | R22, R23 |
| CA-12 | R24, R25, R68 |
| CA-13 | R26, R27, R70 |
| CA-14 | R28, R29 |
| CA-15 | R30, R31, R32 |
| CA-16 | R33, R34, R35 |
| CA-17 | R36, R37, R38 |
| CA-18 | R39, R40, R67 |
| CA-19 | R41, R42 |
| CA-20 | R43, R44 |
| CA-21 | R45, R46 |
| CA-22 | R4, R47 |
| CA-23 | R48, R65 |
| CA-24 | R16, R49, R50 |
| CA-25 | R51, R52 |
| CA-26 | R23, R50, R53 |
| CA-27 | R54, R55 |
| CA-28 | R56, R57 |
| CA-29 | R58, R59 |
| CA-30 | R60 |
| CA-31 | R61, R62 |
| CA-32 | R63 |

Requirements derivados de los requisitos no funcionales y de configuración de la historia (§7 y §9), sin criterio numerado propio:

| Origen en la historia | Requirements |
|---|---|
| §9.1 Rendimiento y throughput | R66, R67 |
| §9.2 Resiliencia | R74 |
| §9.3 Seguridad y privacidad | R71, R72 |
| §9.4 Límites de tamaño | R68, R69 |
| §7.2 y §7.3 Configuración por perfil | R72, R73 |
| §6.3 Migración | R70 |

## Fuera de alcance

- Detección de gaps, análisis de sentimiento, detección de leads y motor de recomendaciones (US-03 a US-06).
- Cualquier llamada a Ollama o Spring AI y las entidades `AuditGap`, `AuditSentiment` y `AuditRecommendation`.
- Endpoints REST de consulta de auditorías (US-07) y exposición de `status` o `errorMessage` por API (US-08).
- Consumidor de la DLQ, reproceso masivo, reprocesamiento programado y herramientas de replay.
- Barrido de registros huérfanos en estado `PROCESSING` tras un reinicio.
- Autenticación, autorización, cifrado de campos del payload, alta disponibilidad y escalamiento horizontal formal.
- Garantía exactly-once: la semántica sigue siendo at-least-once absorbida por idempotencia.
- Cambios al contrato `1.0` o a la lógica de publicación de US-01.
