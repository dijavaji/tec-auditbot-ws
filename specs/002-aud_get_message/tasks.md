# Tasks — US-02 Consumir mensajes de auditoría

> Ejecutar en orden de dependencia. Cada tarea se marca `[x]` solo después de completar su implementación y sus pruebas asociadas. No implementar analyzers, llamadas a Ollama, endpoints REST ni consumidor de la DLQ.
> Exportar `PROFILE=local` en todos los comandos Maven: `application.properties` declara `spring.profiles.active=${PROFILE}`.

## Infraestructura y persistencia

- [ ] **T1 — Reintroducir JPA, driver, Flyway y H2 en `pom.xml`.**
  - Descomentar `org.springframework.boot:spring-boot-starter-data-jpa`.
  - Añadir `com.mysql:mysql-connector-j` (`runtime`), `org.flywaydb:flyway-core`, `org.flywaydb:flyway-mysql` y `com.h2database:h2` (`test`), con versiones gestionadas por el parent `spring-boot-starter-parent:4.0.7`.
  - No añadir MapStruct ni ninguna dependencia que no se use.
  - Ejecutar `PROFILE=local ./mvnw clean install -Dmaven.test.skip=true` y confirmar resolución y compilación.
  - **Cubre:** R26, R62, R70.

- [ ] **T2 — Configurar el contenedor de listeners y la topología de dead-letter en `application.properties`.**
  - Añadir `auditbot.messaging.dead-letter-exchange`, `dead-letter-queue`, `dead-letter-routing-key` y `max-redeliveries=3`.
  - Añadir `spring.rabbitmq.listener.simple.acknowledge-mode=auto`, `default-requeue-rejected=false`, `prefetch=1`, `concurrency=2`, `max-concurrency=4`, `retry.enabled=true`, `retry.max-attempts=3`, `retry.initial-interval=1s`, `retry.multiplier=2`, `retry.max-interval=10s`.
  - Añadir `spring.jpa.open-in-view=false`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`, `spring.flyway.enabled=true` y `spring.flyway.locations=classpath:db/migration`.
  - No modificar las claves de publicación de US-01 ni `auditbot.messaging.max-payload-size`: el consumidor comparte el mismo límite.
  - **Cubre:** R3, R4, R5, R45, R64, R65, R67, R68.

- [ ] **T3 — Configurar el datasource por perfil.**
  - Añadir a `application-prod.properties` `spring.datasource.url`, `username`, `password` por variables de entorno y `spring.jpa.hibernate.ddl-auto=validate`.
  - Crear `application-local.properties` con los mismos valores resueltos a defaults de desarrollo y `ddl-auto=validate`.
  - Verificar que no se versiona ninguna credencial real.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=TecAuditbotWsApplicationTests` y confirmar que `contextLoads` vuelve a pasar con JPA activo.
  - **Cubre:** R71, R72, R73.

- [ ] **T4 — Crear la migración `V1__create_audit_result.sql`.**
  - Crear `src/main/resources/db/migration/V1__create_audit_result.sql` con la tabla `audit_result` según §7.1 del `design.md`.
  - Declarar `CONSTRAINT uk_audit_result_chat_id UNIQUE (chat_id)` y el índice `idx_audit_result_status`.
  - Declarar como nullable las columnas de US-03 a US-05 (`frustration_detected`, `lost_lead_detected`, `lead_score`, `gaps_count`) para evitar una migración por historia. Sin datos semilla.
  - **Cubre:** R27, R70.

- [ ] **T5 — Crear entidad, enum y repositorio.**
  - Crear `model/AuditResult.java` como contenedor de datos puro con `@Table` y `@UniqueConstraint(name = "uk_audit_result_chat_id", columnNames = "chat_id")`.
  - Crear `model/AuditStatus.java` con `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` y mapear `status` con `@Enumerated(EnumType.STRING)`.
  - Crear `repository/IAuditResultRepository.java` extendiendo `JpaRepository<AuditResult, String>` con `Optional<AuditResult> findByChatId(String chatId)`; sin `@Transactional`.
  - Confirmar que ningún tipo JPA sale de `model` y `repository`.
  - **Cubre:** R26, R27, R36, R38, R62.

## Topología AMQP y contratos internos

- [ ] **T6 — Ampliar propiedades y declarar la topología de dead-letter.**
  - Añadir a `AuditMessagingProperties` los campos `deadLetterExchange`, `deadLetterQueue`, `deadLetterRoutingKey` y `maxRedeliveries` con sus defaults, getters/setters y comprobaciones en `validate()`.
  - Añadir a `RabbitMqConfiguration` los beans `auditDeadLetterExchange`, `auditDeadLetterQueue` (sin argumentos de dead-letter) y `auditDeadLetterBinding`.
  - Redeclarar el bean `auditQueue` con `QueueBuilder.durable(...)` y los argumentos `x-dead-letter-exchange` y `x-dead-letter-routing-key`, manteniéndola no exclusiva y no auto-delete.
  - No tocar `auditExchange`, `auditRequestBinding`, `conversationAuditRequestValidator`, `auditPublicationClock` ni `auditObjectMapper`.
  - **Cubre:** R17, R18, R19, R20, R21, R64, R65.

- [ ] **T7 — Crear excepciones nuevas y constantes de headers.**
  - Crear `commons/exception/InvalidAuditMessageException.java` (permanente) con razón y rutas de campos inválidos, sin payload.
  - Crear `commons/exception/AuditProcessingException.java` (transitoria) con `chatId` y causa.
  - Crear `commons/util/AuditMessageHeaders.java` como clase `final` con constructor privado y constantes `UPPER_SNAKE_CASE` para `x-audit-retry`, `x-audit-failure-reason`, `x-audit-invalid-fields`, `x-audit-failed-at` y `x-death`.
  - Reutilizar `InvalidAuditRequestException` como causa del error permanente; no usar `AuditPublicationException` en el consumo.
  - **Cubre:** R41, R42, R50.

- [ ] **T8 — Crear el mapper manual y el DTO de resultado.**
  - Crear `mapper/AuditResultMapper.java` con `toClaim(ConversationAuditRequestDto request, Instant now)`, sin copiar `messages[].content`.
  - Crear `dto/AuditIntakeResultDto.java` como record con el desenlace (`ADMITTED`, `DUPLICATE_SKIPPED`, `RETRY_ADMITTED`), `chatId` y `auditId`.
  - No introducir MapStruct.
  - **Cubre:** R26, R53.

- [ ] **T9 — Crear el punto de extensión del pipeline.**
  - Crear `service/IAuditPipelineTrigger.java` con `void trigger(String chatId)`.
  - Crear `service/impl/NoOpAuditPipelineTrigger.java` con log `DEBUG` y Javadoc indicando que US-03 la sustituye.
  - Confirmar que no importa Spring AI, Ollama ni ningún analyzer.
  - **Cubre:** R58, R59.

## Servicio de admisión e idempotencia

- [ ] **T10 — Implementar el puerto y el servicio de admisión transaccional.**
  - Crear `service/IConversationAuditIntakeService.java` con `AuditIntakeResultDto admit(ConversationAuditRequestDto request, boolean retryRequested)`, sin tipos AMQP.
  - Crear `service/impl/ConversationAuditIntakeServiceImpl.java` con inyección por constructor y `@Transactional` únicamente en `admit(...)`.
  - Validar con `ConversationAuditRequestValidator` antes de cualquier escritura y sin duplicar reglas.
  - Reservar el `chatId` insertando el claim y tratar `DataIntegrityViolationException` como duplicado; no consultar existencia antes de insertar.
  - Resolver duplicado sin reintento (descarte), reintento explícito sobre `FAILED` o `COMPLETED` (a `PROCESSING`, `attempt_count + 1`, `error_message` limpio, `updated_date` actualizado) y reintento sobre `PROCESSING` (descarte).
  - Persistir la primera admisión en `PENDING` y promoverla a `PROCESSING`; nunca establecer `COMPLETED`.
  - Envolver fallos de acceso a datos en `AuditProcessingException`; al aparcar un mensaje con claim, dejar el registro en `FAILED` con `error_message` legible truncado a 500 caracteres y sin contenido sensible.
  - Invocar `IAuditPipelineTrigger` y nada más: sin analyzers.
  - **Cubre:** R9, R13, R14, R28, R29, R30, R31, R33, R34, R35, R36, R37, R38, R39, R42, R49, R50, R58, R69.

## Listener y dead-lettering

- [ ] **T11 — Implementar deserializador y publicador de DLQ.**
  - Crear `integration/rabbitmq/AuditMessageDeserializer.java` sobre el bean `auditObjectMapper` de Jackson 2, preservando `Instant` en UTC y traduciendo cualquier fallo a `InvalidAuditMessageException`.
  - Crear `integration/rabbitmq/AuditMessageDeadLetterPublisher.java` que publique en `auditbot.dlx` con routing key `conversation.audit.failed` y los headers `x-audit-failure-reason`, `x-audit-invalid-fields` y `x-audit-failed-at` (ISO-8601 UTC, reloj `auditPublicationClock`).
  - Verificar que ningún header contiene el cuerpo completo ni `messages[].content`.
  - **Cubre:** R8, R22, R23, R41.

- [ ] **T12 — Implementar `ConversationAuditMessageListener`.**
  - Crear `integration/rabbitmq/ConversationAuditMessageListener.java` con `@Slf4j`, `@Component`, inyección por constructor y `@RabbitListener(id = "conversationAuditListener", queues = "#{auditQueue.name}")`; sin el nombre de la cola como literal.
  - Colocar `chatId` y `eventId` en el MDC al iniciar y limpiarlo en `finally`.
  - Comprobar el tamaño del cuerpo contra `auditbot.messaging.max-payload-size` antes de deserializar y aparcar el mensaje sobredimensionado sin reintentos.
  - Aplicar la guardia de redelivery: `redelivered=true` y conteo de `x-death` igual o mayor que `auditbot.messaging.max-redeliveries` enrutan a la DLQ sin procesar.
  - Leer el header `x-audit-retry` y propagarlo al servicio; delegar la admisión en `IConversationAuditIntakeService` sin reglas de negocio, sin repositorios y sin `@Transactional`.
  - Traducir `InvalidAuditMessageException` en publicación a la DLQ más `AmqpRejectAndDontRequeueException`, sin consumir intentos; propagar `AuditProcessingException` para el retry acotado del contenedor.
  - No acusar el mensaje cuando la transacción de admisión no se confirma.
  - Registrar exactamente una línea por resultado con `resultado`, `chatId`, `eventId`, `messageId`, `queue`, `intento` y `assistantId`, con niveles `INFO`/`WARN`/`ERROR` y sin contenido sensible; sin `System.out.println()` ni `e.printStackTrace()`.
  - No añadir ni modificar endpoints HTTP.
  - **Cubre:** R1, R2, R7, R10, R12, R15, R16, R24, R25, R32, R43, R44, R45, R46, R47, R48, R51, R52, R54, R55, R56, R57, R60, R74.

## Tests unitarios

- [ ] **T13 — Probar deserializador, mapper y trigger no-op.**
  - Crear `AuditMessageDeserializerTest` con `shouldDeserializeVersionOneContract`, `shouldPreserveInstantsInUtc` y `shouldWrapMalformedJsonAsPermanentError`.
  - Crear `AuditResultMapperTest` con `shouldMapConversationToClaimEntity` y `shouldNotCopyMessageContentIntoEntity`.
  - Crear `NoOpAuditPipelineTriggerTest` con `shouldDoNothingOnTrigger`.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=AuditMessageDeserializerTest,AuditResultMapperTest,NoOpAuditPipelineTriggerTest`.
  - **Cubre:** R8, R41, R53, R59.

- [ ] **T14 — Probar el servicio de admisión.**
  - Crear `ConversationAuditIntakeServiceImplTest` con `shouldAdmitNewChatIdWithPendingThenProcessing`, `shouldSkipAlreadyProcessedChatId`, `shouldTreatUniqueConstraintViolationAsDuplicate`, `shouldClaimChatIdWithoutPreliminaryExistenceQuery`, `shouldReprocessFailedChatIdWhenRetryRequested`, `shouldIncrementAttemptCountOnExplicitRetry`, `shouldRejectRetryWhileChatIdIsProcessing`, `shouldValidateContractBeforeClaim`, `shouldWrapDataAccessFailureAsTransientError`, `shouldMarkAuditAsFailedWithReadableMessage`, `shouldTruncateErrorMessageToFiveHundredCharacters`, `shouldNeverSetCompletedStatus` y `shouldNotInvokeAnalyzers`.
  - Usar Mockito puro sobre `IAuditResultRepository`; verificar en `shouldValidateContractBeforeClaim` que no hay interacción con el repositorio cuando el contrato es inválido.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=ConversationAuditIntakeServiceImplTest`.
  - **Cubre:** R13, R28, R29, R30, R33, R34, R35, R36, R37, R38, R39, R42, R49, R50, R58, R69.

- [ ] **T15 — Probar el listener.**
  - Crear `ConversationAuditMessageListenerTest` con `shouldDelegateValidMessageToIntakeService`, `shouldRejectMalformedJsonWithoutRequeue`, `shouldRejectInvalidSchemaWithoutRequeue`, `shouldRejectOversizedPayloadWithoutRequeue`, `shouldRethrowTransientErrorForBoundedRetry`, `shouldRouteToDlqWhenRedeliveryLimitReached`, `shouldPropagateRetryHeaderToIntakeService`, `shouldNotAccessRepositoryOrOpenTransaction`, `shouldClearMdcAfterEachMessage`, `shouldLogMetadataWithoutConversationContent`, `shouldLogEachOutcomeWithItsLevel`, `shouldLogDuplicateOutcomeAtWarnLevel` y `shouldNotAcknowledgeWhenClaimIsNotConfirmed`.
  - Capturar los logs para comprobar claves, niveles y ausencia de `messages[].content`.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=ConversationAuditMessageListenerTest`.
  - **Cubre:** R7, R9, R10, R24, R25, R32, R43, R44, R45, R48, R51, R52, R53, R54, R55, R56, R74.

- [ ] **T16 — Probar el binding de propiedades y de configuración.**
  - Ampliar `AuditMessagingPropertiesTest` con `shouldUseDefaultDeadLetterTopology`, `shouldBindCustomDeadLetterTopology` y `shouldUseThreeDefaultMaxRedeliveries`, sin romper los cuatro casos existentes.
  - Ampliar `ApplicationRabbitMqConfigurationTest` con `shouldBindListenerContainerSettings`, `shouldOverrideListenerContainerSettings` y `shouldBindListenerRetrySettings`.
  - Crear `ApplicationPersistenceConfigurationTest` con `shouldBindDatasourceFromEnvironment`, `shouldResolveLocalDatasourceDefaults`, `shouldDisableOpenInView`, `shouldEnableFlywayMigrations` y `shouldValidateSchemaWithoutDdlGeneration`, sin credenciales reales.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=AuditMessagingPropertiesTest,ApplicationRabbitMqConfigurationTest,ApplicationPersistenceConfigurationTest`.
  - **Cubre:** R3, R4, R5, R6, R45, R64, R65, R67, R68, R70, R71, R72, R73.

- [ ] **T17 — Ampliar las reglas arquitectónicas.**
  - Añadir a `ArchitectureRulesTest` los métodos `shouldKeepJpaTypesInsideModelAndRepository`, `shouldKeepListenerFreeOfBusinessLogic`, `shouldKeepConsumptionIndependentFromAnalyzers`, `shouldNotExposeAuditIntakeHttpEndpoint`, `shouldKeepTransactionalInsideServiceLayer`, `shouldNotUseConsoleLoggingInConsumptionFlow` y `shouldReuseContractValidatorInConsumption`.
  - Mantener sin cambios los métodos existentes, incluido `shouldKeepRabbitMqTypesInsideAdapterBoundary`.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=ArchitectureRulesTest`.
  - **Cubre:** R10, R14, R40, R57, R58, R60, R61, R62.

## Tests de integración

- [ ] **T18 — Probar el repositorio con H2.**
  - Crear `AuditResultRepositoryTest` con `@DataJpaTest` y H2, sin mocks del `EntityManager`.
  - Implementar `shouldPersistAndFindAuditByChatId` y `shouldRejectDuplicatedChatId` (violación de `uk_audit_result_chat_id`).
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=AuditResultRepositoryTest`.
  - **Cubre:** R26, R27.

- [ ] **T19 — Probar la topología de dead-lettering con RabbitMQ real.**
  - Crear `RabbitMqDeadLetterTopologyIntegrationTest` con Testcontainers e imagen fijada, o con el fallback de entorno documentado en §9.3 del `design.md`.
  - Implementar `shouldDeclareDurableDeadLetterExchange`, `shouldDeclareDurableDeadLetterQueue`, `shouldBindDeadLetterQueueWithFailedRoutingKey` y `shouldDeclareMainQueueWithDeadLetterArguments`.
  - Verificar en `shouldDeclareDurableDeadLetterQueue` que la DLQ no tiene argumentos de dead-letter.
  - Usar recursos sufijados con `UUID` y limpiarlos en `@AfterAll`.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=RabbitMqDeadLetterTopologyIntegrationTest`.
  - **Cubre:** R17, R18, R19, R20, R21.

- [ ] **T20 — Probar el consumo end-to-end con RabbitMQ real.**
  - Crear `ConversationAuditMessageListenerIntegrationTest` con `@SpringBootTest`, reutilizando `RabbitMqConversationAuditPublisher` para inyectar mensajes reales.
  - Implementar `shouldConsumeValidMessageAndPersistAudit`, `shouldConsumeFromConfiguredQueueName`, `shouldRouteInvalidSchemaMessageToDlq`, `shouldRouteMalformedJsonToDlq`, `shouldNotPersistAnythingWhenSchemaIsInvalid`, `shouldPublishDiagnosticHeadersOnDeadLetter`, `shouldNotReprocessDuplicatedChatId`, `shouldReprocessWhenRetryHeaderIsPresent`, `shouldStopRedeliveryAfterBoundedRetries`, `shouldLeaveMainQueueEmptyAfterSuccessfulConsumption` y `shouldAdmitMessageUnderLatencyBudget`.
  - Verificar el conteo de la cola principal, el contenido de la DLQ, los headers de diagnóstico, el registro en `audit_result` y el presupuesto de 200 ms p95 de la admisión.
  - Documentar la desviación de entorno si Docker sigue sin estar disponible.
  - Ejecutar `PROFILE=local ./mvnw test -Dtest=ConversationAuditMessageListenerIntegrationTest`.
  - **Cubre:** R1, R2, R11, R12, R15, R16, R22, R23, R30, R31, R33, R46, R47, R66.

## Cierre

- [ ] **T21 — Ejecutar el paso operativo de la cola y el smoke test manual.**
  - Verificar en la consola de management que `conversation.audit.queue` está vacía, eliminarla y arrancar la aplicación para que `RabbitAdmin` la redeclare con los argumentos de dead-letter; confirmar que no aparece `PRECONDITION_FAILED - inequivalent arg`.
  - Levantar la aplicación con `PROFILE=local`, publicar un mensaje válido y comprobar el registro en `audit_result` con `status = PROCESSING`.
  - Publicar un mensaje inválido y comprobar que aparece en `conversation.audit.dlq` con los headers de diagnóstico y que la cola principal queda vacía.
  - Registrar la evidencia del paso operativo en `progress/impl_aud_get_message.md`.
  - **Cubre:** R15, R20, R22, R70, R73.

- [ ] **T22 — Documentar el contrato operativo del consumo.**
  - Actualizar `docs/data-model.md` con la entidad de control `AuditResult` usada por el consumo, el catálogo de `AuditStatus`, la restricción única `uk_audit_result_chat_id` y la semántica de idempotencia at-least-once.
  - Documentar el header `x-audit-retry`, los headers de diagnóstico de la DLQ y la topología `auditbot.dlx` / `conversation.audit.dlq` / `conversation.audit.failed`.
  - Documentar las variables de entorno del datasource, la variable `PROFILE` y el paso operativo de migración de la cola, sin duplicar secretos.
  - **Cubre:** R22, R26, R27, R33, R64, R70, R72.

- [ ] **T23 — Verificación final y matriz de trazabilidad.**
  - Ejecutar `PROFILE=local ./mvnw test` y después `PROFILE=local ./mvnw clean verify`; ambos deben terminar con código `0` y `BUILD SUCCESS`, sin regresiones en los 37 tests de US-01.
  - Crear `progress/impl_aud_get_message.md` con resumen, comandos, evidencia, desviaciones y la matriz completa R1–R74 → método de test.
  - Revisar que no hay cambios en `controller`, `analyzer` ni en el contrato `1.0`, ni credenciales o payloads en código y logs.
  - Dejar esta tarea pendiente si cualquier test falla.
  - **Cubre:** R63 y cierre de R1–R74.

## Comprobación de cobertura `R<n>` → `T<n>`

| Requirement | Tareas |
|---|---|
| R1 | T12, T20 |
| R2 | T12, T20 |
| R3 | T2, T16 |
| R4 | T2, T16 |
| R5 | T2, T16 |
| R6 | T16 |
| R7 | T12, T15 |
| R8 | T11, T13 |
| R9 | T10, T15 |
| R10 | T12, T15, T17 |
| R11 | T20 |
| R12 | T12, T20 |
| R13 | T10, T14 |
| R14 | T10, T17 |
| R15 | T12, T20, T21 |
| R16 | T12, T20 |
| R17 | T6, T19 |
| R18 | T6, T19 |
| R19 | T6, T19 |
| R20 | T6, T19, T21 |
| R21 | T6, T19 |
| R22 | T11, T20, T21, T22 |
| R23 | T11, T20 |
| R24 | T12, T15 |
| R25 | T12, T15 |
| R26 | T1, T5, T8, T18, T22 |
| R27 | T4, T5, T18, T22 |
| R28 | T10, T14 |
| R29 | T10, T14 |
| R30 | T10, T14, T20 |
| R31 | T10, T20 |
| R32 | T12, T15 |
| R33 | T10, T14, T20, T22 |
| R34 | T10, T14 |
| R35 | T10, T14 |
| R36 | T5, T10, T14 |
| R37 | T10, T14 |
| R38 | T5, T10, T14 |
| R39 | T10, T14 |
| R40 | T17 |
| R41 | T7, T11, T13 |
| R42 | T7, T10, T14 |
| R43 | T12, T15 |
| R44 | T12, T15 |
| R45 | T2, T12, T15, T16 |
| R46 | T12, T20 |
| R47 | T12, T20 |
| R48 | T12, T15 |
| R49 | T10, T14 |
| R50 | T7, T10, T14 |
| R51 | T12, T15 |
| R52 | T12, T15 |
| R53 | T8, T13, T15 |
| R54 | T12, T15 |
| R55 | T12, T15 |
| R56 | T12, T15 |
| R57 | T12, T17 |
| R58 | T9, T10, T14, T17 |
| R59 | T9, T13 |
| R60 | T12, T17 |
| R61 | T17 |
| R62 | T1, T5, T17 |
| R63 | T23 |
| R64 | T2, T6, T16, T22 |
| R65 | T2, T6, T16 |
| R66 | T20 |
| R67 | T2, T16 |
| R68 | T2, T16 |
| R69 | T10, T14 |
| R70 | T1, T4, T16, T21, T22 |
| R71 | T3, T16 |
| R72 | T3, T16, T22 |
| R73 | T3, T16, T21 |
| R74 | T12, T15 |
