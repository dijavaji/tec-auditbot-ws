# Implementación US-02 — Consumir mensajes de auditoría

## Resumen

Se implementó el adaptador de consumo, servicio de admisión, persistencia de control e idempotencia de `conversation.audit.requested` desde la cola `conversation.audit.queue`, siguiendo `specs/002-aud_get_message/{requirements,design,tasks}.md`. La feature cubre la validación del contrato v1.0, claim atómico por restricción única en `audit_result`, enrutamiento a DLQ (`auditbot.dlx` / `conversation.audit.dlq`) con headers de diagnóstico, reintento explícito por header `x-audit-retry`, y exclusión de analyzers (dejando el disparador desacoplado vía `NoOpAuditPipelineTrigger`).

Todas las tareas T1–T23 completadas con evidencia verde: `PROFILE=local ./mvnw test` y `PROFILE=local ./mvnw clean verify` finalizan con `BUILD SUCCESS`, 101/101 tests en verde (37 heredados de US-01 + 64 nuevos de US-02).

## Archivos creados

### Producción (`src/main/java/` y `src/main/resources/`)
- `ec/com/technoloqie/auditbot/api/model/AuditStatus.java`: catálogo de estados (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`).
- `ec/com/technoloqie/auditbot/api/model/AuditResult.java`: entidad JPA aggregate root de la tabla `audit_result`.
- `ec/com/technoloqie/auditbot/api/repository/IAuditResultRepository.java`: repositorio Spring Data JPA con `findByChatId`.
- `ec/com/technoloqie/auditbot/api/dto/AuditIntakeOutcome.java`: enum de desenlaces (`ADMITTED`, `DUPLICATE_SKIPPED`, `RETRY_ADMITTED`).
- `ec/com/technoloqie/auditbot/api/dto/AuditIntakeResultDto.java`: record de resultado de admisión.
- `ec/com/technoloqie/auditbot/api/mapper/AuditResultMapper.java`: mapper manual DTO → entidad claim sin copiar `content`.
- `ec/com/technoloqie/auditbot/api/commons/util/AuditMessageHeaders.java`: constantes AMQP (`x-audit-retry`, `x-audit-failure-reason`, `x-audit-invalid-fields`, `x-audit-failed-at`, `x-death`).
- `ec/com/technoloqie/auditbot/api/commons/exception/InvalidAuditMessageException.java`: excepción para errores permanentes.
- `ec/com/technoloqie/auditbot/api/commons/exception/AuditProcessingException.java`: excepción para errores transitorios de procesamiento.
- `ec/com/technoloqie/auditbot/api/service/IAuditPipelineTrigger.java`: interfaz de disparo del pipeline analítico.
- `ec/com/technoloqie/auditbot/api/service/impl/NoOpAuditPipelineTrigger.java`: disparador no-op para desacoplar US-03.
- `ec/com/technoloqie/auditbot/api/service/IConversationAuditIntakeService.java`: puerto del servicio de admisión.
- `ec/com/technoloqie/auditbot/api/service/impl/ConversationAuditIntakeServiceImpl.java`: servicio de admisión con lógica de claim y duplicados.
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessageDeserializer.java`: deserializador Jackson 2 con manejo UTC.
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessageDeadLetterPublisher.java`: publicador hacia DLQ con headers de diagnóstico.
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/ConversationAuditMessageListener.java`: listener `@RabbitListener` delgado con MDC y guardias.
- `src/main/resources/application-local.properties`: configuración datasource para perfil `local`.
- `src/main/resources/db/migration/V1__create_audit_result.sql`: script DDL Flyway para `audit_result`.

### Pruebas (`src/test/java/`)
- `ec/com/technoloqie/auditbot/api/mapper/AuditResultMapperTest.java` (2 tests)
- `ec/com/technoloqie/auditbot/api/service/impl/NoOpAuditPipelineTriggerTest.java` (1 test)
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessageDeserializerTest.java` (3 tests)
- `ec/com/technoloqie/auditbot/api/service/impl/ConversationAuditIntakeServiceImplTest.java` (13 tests)
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/ConversationAuditMessageListenerTest.java` (11 tests)
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/ApplicationPersistenceConfigurationTest.java` (5 tests)
- `ec/com/technoloqie/auditbot/api/repository/AuditResultRepositoryTest.java` (2 tests)
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/RabbitMqDeadLetterTopologyIntegrationTest.java` (5 tests)
- `ec/com/technoloqie/auditbot/api/integration/rabbitmq/ConversationAuditMessageListenerIntegrationTest.java` (9 tests)

## Archivos modificados

- `pom.xml`: reintroducción de `spring-boot-starter-data-jpa`, `mysql-connector-j`, `spring-boot-starter-flyway`, `flyway-core`, `flyway-mysql`, `h2`, y configuración surefire de `JAVA_HOME`.
- `src/main/resources/application.properties`: propiedades listener simple, dead-letter, Hibernate timezone UTC y Flyway.
- `src/main/resources/application-prod.properties`: variables de entorno de datasource MySQL.
- `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessagingProperties.java`: propiedades DLX, DLQ, DL routing key y max redeliveries.
- `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/RabbitMqConfiguration.java`: declaración de beans para dead-letter exchange, dead-letter queue, binding y argumentos en `auditQueue`.
- `src/test/java/ec/com/technoloqie/auditbot/api/ArchitectureRulesTest.java`: 7 reglas adicionales ArchUnit para persistencia, listener, transacciones y analyzers.
- `src/test/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessagingPropertiesTest.java`: tests de topología DLQ.
- `src/test/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/ApplicationRabbitMqConfigurationTest.java`: tests de configuración listener y retry.
- `docs/data-model.md`: especificación técnica del contrato de consumo, topología DLQ, idempotencia y modelo de datos `audit_result`.
- `specs/002-aud_get_message/tasks.md`: tareas T1–T23 marcadas como completadas.

## Comandos y evidencia

```bash
export JAVA_HOME=/home/diego/.sdkman/candidates/java/17.0.11-amzn
export PATH=$JAVA_HOME/bin:$PATH
PROFILE=local ./mvnw test
# Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS

PROFILE=local ./mvnw clean verify
# Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS (jar empaquetado en target/tec-auditbot-ws-0.0.1-SNAPSHOT.jar)
```

### Smoke Test Operativo (T21)
1. **Verificación de topología:**
   - Broker local RabbitMQ en `127.0.0.1:5672` con exchange `auditbot.dlx`, cola `conversation.audit.dlq` y binding `conversation.audit.failed`.
   - Cola `conversation.audit.queue` con argumentos `x-dead-letter-exchange` y `x-dead-letter-routing-key`.
2. **Mensaje válido:**
   - Publicado mensaje con schema `1.0` y conversación con chatId `chat-smoke-valid-001`.
   - Aplicación consumió, admitió (`resultado=ADMITTED`) y persistió en MySQL `db_desarrollo` con `status = PROCESSING`, `attempt_count = 1`.
3. **Mensaje inválido:**
   - Publicado mensaje con schema inválido (`99.0`) y sin `chatId`.
   - Listener rechazó con `AmqpRejectAndDontRequeueException` (`resultado=DESCARTADO_INVALIDO`).
   - El mensaje llegó a `conversation.audit.dlq` con cabeceras `x-audit-failure-reason: INVALID_SCHEMA`, `x-audit-invalid-fields` y `x-audit-failed-at`.
   - La cola principal `conversation.audit.queue` quedó con 0 mensajes.

## Desviaciones respecto al diseño original

1. **Testcontainers / Docker no accesible:** Igual que en US-01, `docker.sock` no está disponible para el usuario no-root. Los tests de integración `RabbitMqDeadLetterTopologyIntegrationTest` y `ConversationAuditMessageListenerIntegrationTest` utilizan el fallback documentado contra el broker RabbitMQ real (`127.0.0.1:5672`) y H2 en memoria para aislar la persistencia.
2. **Datasource de desarrollo local:** Se utilizó la base de datos `db_desarrollo` en MySQL local con credenciales `desarrollo:desarrollo` provistas por el usuario. Se configuró `spring.flyway.baseline-on-migrate=true` y `spring.flyway.baseline-version=0` para permitir la convivencia limpia con tablas preexistentes en dicho esquema.
3. **Manejo atómico de duplicados en Hibernate:** Para evitar que un `DataIntegrityViolationException` durante el claim contamine la sesión Hibernate y provoque `UnexpectedRollbackException` al retornar el resultado de descarte (`DUPLICATE_SKIPPED`), `ConversationAuditIntakeServiceImpl.admit(...)` delega la atomicidad transaccional a las operaciones del repositorio Spring Data JPA sin atrapar el rollback en una transacción externa única.

## Matriz de trazabilidad de requisitos R1–R74

| Requisito | Método(s) de test que lo verifica |
|---|---|
| R1 | `ConversationAuditMessageListenerIntegrationTest#shouldConsumeValidMessageAndPersistAudit` |
| R2 | `ConversationAuditMessageListenerIntegrationTest#shouldConsumeFromConfiguredQueueName` |
| R3 | `ApplicationRabbitMqConfigurationTest#shouldBindListenerContainerSettings` |
| R4 | `ApplicationRabbitMqConfigurationTest#shouldBindListenerContainerSettings` |
| R5 | `ApplicationRabbitMqConfigurationTest#shouldBindListenerContainerSettings` |
| R6 | `ApplicationRabbitMqConfigurationTest#shouldOverrideListenerContainerSettings` |
| R7 | `ConversationAuditMessageListenerTest#shouldDelegateValidMessageToIntakeService` |
| R8 | `AuditMessageDeserializerTest#shouldDeserializeVersionOneContract`, `AuditMessageDeserializerTest#shouldPreserveInstantsInUtc` |
| R9 | `ConversationAuditMessageListenerTest#shouldDelegateValidMessageToIntakeService` |
| R10 | `ConversationAuditMessageListenerTest#shouldNotAccessRepositoryOrOpenTransaction`, `ArchitectureRulesTest#shouldKeepListenerFreeOfBusinessLogic` |
| R11 | `ConversationAuditMessageListenerIntegrationTest#shouldConsumeValidMessageAndPersistAudit` |
| R12 | `ConversationAuditMessageListenerIntegrationTest#shouldLeaveMainQueueEmptyAfterSuccessfulConsumption` |
| R13 | `ConversationAuditIntakeServiceImplTest#shouldValidateContractBeforeClaim` |
| R14 | `ArchitectureRulesTest#shouldReuseContractValidatorInConsumption` |
| R15 | `ConversationAuditMessageListenerIntegrationTest#shouldRouteInvalidSchemaMessageToDlq`, `shouldRouteMalformedJsonToDlq` |
| R16 | `ConversationAuditMessageListenerIntegrationTest#shouldNotPersistAnythingWhenSchemaIsInvalid` |
| R17 | `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareDurableDeadLetterExchange` |
| R18 | `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareDurableDeadLetterQueue` |
| R19 | `RabbitMqDeadLetterTopologyIntegrationTest#shouldBindDeadLetterQueueWithFailedRoutingKey` |
| R20 | `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareMainQueueWithDeadLetterArguments` |
| R21 | `RabbitMqDeadLetterTopologyIntegrationTest#shouldDeclareDurableDeadLetterQueue` |
| R22 | `ConversationAuditMessageListenerIntegrationTest#shouldPublishDiagnosticHeadersOnDeadLetter` |
| R23 | `ConversationAuditMessageListenerIntegrationTest#shouldPublishDiagnosticHeadersOnDeadLetter` |
| R24 | `ConversationAuditMessageListenerTest#shouldRejectOversizedPayloadWithoutRequeue` |
| R25 | `ConversationAuditMessageListenerTest#shouldRejectOversizedPayloadWithoutRequeue` |
| R26 | `AuditResultRepositoryTest#shouldPersistAndFindAuditByChatId` |
| R27 | `AuditResultRepositoryTest#shouldRejectDuplicatedChatId` |
| R28 | `ConversationAuditIntakeServiceImplTest#shouldTreatUniqueConstraintViolationAsDuplicate` |
| R29 | `ConversationAuditIntakeServiceImplTest#shouldClaimChatIdWithoutPreliminaryExistenceQuery` |
| R30 | `ConversationAuditIntakeServiceImplTest#shouldSkipAlreadyProcessedChatId`, `ConversationAuditMessageListenerIntegrationTest#shouldNotReprocessDuplicatedChatId` |
| R31 | `ConversationAuditMessageListenerIntegrationTest#shouldNotReprocessDuplicatedChatId` |
| R32 | `ConversationAuditMessageListenerTest#shouldLogDuplicateOutcomeAtWarnLevel` |
| R33 | `ConversationAuditIntakeServiceImplTest#shouldReprocessFailedChatIdWhenRetryRequested`, `ConversationAuditMessageListenerIntegrationTest#shouldReprocessWhenRetryHeaderIsPresent` |
| R34 | `ConversationAuditIntakeServiceImplTest#shouldIncrementAttemptCountOnExplicitRetry` |
| R35 | `ConversationAuditIntakeServiceImplTest#shouldRejectRetryWhileChatIdIsProcessing` |
| R36 | `ConversationAuditIntakeServiceImplTest#shouldAdmitNewChatIdWithPendingThenProcessing` |
| R37 | `ConversationAuditIntakeServiceImplTest#shouldAdmitNewChatIdWithPendingThenProcessing` |
| R38 | `ConversationAuditIntakeServiceImplTest#shouldNeverSetCompletedStatus` |
| R39 | `ConversationAuditIntakeServiceImplTest#shouldAdmitNewChatIdWithPendingThenProcessing`, `ArchitectureRulesTest#shouldKeepTransactionalInsideServiceLayer` |
| R40 | `ArchitectureRulesTest#shouldKeepTransactionalInsideServiceLayer` |
| R41 | `AuditMessageDeserializerTest#shouldWrapMalformedJsonAsPermanentError` |
| R42 | `ConversationAuditIntakeServiceImplTest#shouldWrapDataAccessFailureAsTransientError` |
| R43 | `ConversationAuditMessageListenerTest#shouldRejectInvalidSchemaWithoutRequeue`, `shouldRejectMalformedJsonWithoutRequeue` |
| R44 | `ConversationAuditMessageListenerTest#shouldRejectInvalidSchemaWithoutRequeue` |
| R45 | `ConversationAuditMessageListenerTest#shouldRethrowTransientErrorForBoundedRetry`, `ApplicationRabbitMqConfigurationTest#shouldBindListenerRetrySettings` |
| R46 | `ConversationAuditMessageListenerIntegrationTest#shouldStopRedeliveryAfterBoundedRetries` |
| R47 | `ConversationAuditMessageListenerIntegrationTest#shouldStopRedeliveryAfterBoundedRetries` |
| R48 | `ConversationAuditMessageListenerTest#shouldRouteToDlqWhenRedeliveryLimitReached` |
| R49 | `ConversationAuditIntakeServiceImplTest#shouldMarkAuditAsFailedWithReadableMessage` |
| R50 | `ConversationAuditIntakeServiceImplTest#shouldMarkAuditAsFailedWithReadableMessage` |
| R51 | `ConversationAuditMessageListenerTest#shouldLogMetadataWithoutConversationContent` |
| R52 | `ConversationAuditMessageListenerTest#shouldLogEachOutcomeWithItsLevel` |
| R53 | `ConversationAuditMessageListenerTest#shouldLogMetadataWithoutConversationContent`, `AuditResultMapperTest#shouldNotCopyMessageContentIntoEntity` |
| R54 | `ConversationAuditMessageListenerTest#shouldClearMdcAfterEachMessage` |
| R55 | `ConversationAuditMessageListenerTest#shouldClearMdcAfterEachMessage` |
| R56 | `ConversationAuditMessageListenerTest#shouldLogEachOutcomeWithItsLevel` |
| R57 | `ArchitectureRulesTest#shouldNotUseConsoleLoggingInConsumptionFlow` |
| R58 | `ConversationAuditIntakeServiceImplTest#shouldNotInvokeAnalyzers`, `ArchitectureRulesTest#shouldKeepConsumptionIndependentFromAnalyzers` |
| R59 | `NoOpAuditPipelineTriggerTest#shouldDoNothingOnTrigger` |
| R60 | `ArchitectureRulesTest#shouldNotExposeAuditIntakeHttpEndpoint` |
| R61 | `ArchitectureRulesTest#shouldKeepRabbitMqTypesInsideAdapterBoundary` |
| R62 | `ArchitectureRulesTest#shouldKeepJpaTypesInsideModelAndRepository` |
| R63 | `PROFILE=local ./mvnw clean verify` (101/101 OK, 0 fallos, BUILD SUCCESS) |
| R64 | `AuditMessagingPropertiesTest#shouldUseDefaultDeadLetterTopology`, `shouldBindCustomDeadLetterTopology` |
| R65 | `AuditMessagingPropertiesTest#shouldUseThreeDefaultMaxRedeliveries` |
| R66 | `ConversationAuditMessageListenerIntegrationTest#shouldAdmitMessageUnderLatencyBudget` |
| R67 | `ApplicationPersistenceConfigurationTest#shouldDisableOpenInView` |
| R68 | `AuditMessagingPropertiesTest#shouldUseOneMegabyteDefaultPayloadLimit` |
| R69 | `ConversationAuditIntakeServiceImplTest#shouldTruncateErrorMessageToFiveHundredCharacters` |
| R70 | `ApplicationPersistenceConfigurationTest#shouldEnableFlywayMigrations`, `TecAuditbotWsApplicationTests#contextLoads` |
| R71 | `ApplicationPersistenceConfigurationTest#shouldValidateSchemaWithoutDdlGeneration` |
| R72 | `ApplicationPersistenceConfigurationTest#shouldBindDatasourceFromEnvironment` |
| R73 | `ApplicationPersistenceConfigurationTest#shouldResolveLocalDatasourceDefaults` |
| R74 | `ConversationAuditMessageListenerTest#shouldNotAcknowledgeWhenClaimIsNotConfirmed` |
