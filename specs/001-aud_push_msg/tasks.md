# Tasks — US-01 Publicar solicitud de auditoría genérica

> Ejecutar en orden. Cada tarea se marca `[x]` solo después de completar su implementación y pruebas asociadas. No implementar consumo, DLQ, analyzers ni endpoints HTTP.

- [ ] **T1 — Preparar dependencias y configuración base.**
  - Añadir a `pom.xml` `org.testcontainers:testcontainers-rabbitmq:2.0.5` y `com.tngtech.archunit:archunit-junit5:1.4.1` con scope `test`.
  - Añadir a `application.properties` nombres de topología, timeout `5s`, límite `1MB`, confirms correlacionados, returns, mandatory y retry deshabilitado.
  - Añadir a `application-prod.properties` host, puerto, usuario, contraseña y SSL mediante variables de entorno, sin secretos reales.
  - Ejecutar `./mvnw test -DskipTests` para confirmar resolución y compilación inicial.
  - **Cubre:** R21, R22, R23, R28.

- [ ] **T2 — Crear el contrato DTO inmutable.**
  - Crear `ConversationAuditRequestDto`, `AuditConversationDto` y `AuditMessageDto` como records bajo `api.dto` con las firmas del diseño.
  - Aplicar copia defensiva de `messages` sin ocultar listas nulas que deba reportar el validador.
  - Añadir Javadoc al contrato público y mantener nombres JSON idénticos al contrato `1.0`.
  - **Cubre:** R2, R3, R9, R10, R14.

- [ ] **T3 — Implementar validación y excepciones del contrato.**
  - Crear `InvalidAuditRequestException` y `AuditPublicationException` bajo `api.commons.exception` sin almacenar payloads.
  - Crear `ConversationAuditRequestValidator` bajo `api.service` sin imports Spring/RabbitMQ.
  - Validar campos obligatorios, strings no blank, versión `1.0`, roles, lista no vacía, IDs únicos, orden cronológico y rango de conversación.
  - Acumular rutas inválidas en un único error sin incluir valores de contenido.
  - **Cubre:** R3, R9, R10, R11, R12, R13, R14, R20, R26.

- [ ] **T4 — Implementar propiedades y topología durable.**
  - Crear `AuditMessagingProperties` con defaults y validación de nombres, timeout y tamaño.
  - Crear `RabbitMqConfiguration` con `DirectExchange`, `Queue`, `Binding`, validador y `Clock` UTC.
  - Confirmar que exchange, cola y binding usan únicamente propiedades tipadas y que Spring puede declararlos mediante `RabbitAdmin`.
  - **Cubre:** R5, R6, R7, R21, R22, R23, R26.

- [ ] **T5 — Implementar el puerto y adaptador de publicación.**
  - Crear `IConversationAuditPublisher` bajo `api.service`.
  - Crear `RabbitMqConversationAuditPublisher` bajo `api.integration.rabbitmq` con inyección por constructor.
  - Validar, serializar una vez, comprobar bytes, construir propiedades AMQP y publicar con `CorrelationData(eventId)`.
  - Esperar confirm hasta el timeout y tratar NACK, returned message, timeout, interrupción, serialización y envío como `AuditPublicationException`.
  - Restaurar el interrupt flag cuando corresponda.
  - Registrar solo metadata permitida y no añadir retry, endpoint, consumo ni análisis.
  - **Cubre:** R1, R2, R4, R15, R16, R17, R18, R19, R20, R24, R25, R27, R28, R29.

- [ ] **T6 — Probar exhaustivamente el validador.**
  - Crear `ConversationAuditRequestValidatorTest` con fixture válido reutilizable y casos parametrizados.
  - Implementar `shouldRejectUnsupportedSchemaVersion`, `shouldRejectEveryMissingRequiredField`, `shouldRejectUnsupportedMessageRole`, `shouldRejectDuplicatedMessageIds`, `shouldRejectMessagesOutOfOrder`, `shouldRejectEndBeforeStart` y `shouldRejectConversationWithoutMessages`.
  - Verificar en cada error las rutas reportadas y ausencia de `content` en el mensaje de excepción.
  - Ejecutar `./mvnw test -Dtest=ConversationAuditRequestValidatorTest`.
  - **Cubre:** R3, R9, R10, R11, R12, R13, R14.

- [ ] **T7 — Probar unitariamente el adaptador.**
  - Crear `RabbitMqConversationAuditPublisherTest` con `ObjectMapper` real para el camino feliz y mocks solo en límites externos/fallos.
  - Implementar los métodos: `shouldSerializeVersionOneContractExactlyOnce`, `shouldSetRequiredAmqpProperties`, `shouldRejectPayloadLargerThanConfiguredLimit`, `shouldCompleteAfterPositiveConfirm`, `shouldFailOnNegativeConfirm`, `shouldFailWhenConfirmTimesOut`, `shouldWrapSerializationFailure`, `shouldWrapBrokerSendFailure`, `shouldLogMetadataWithoutConversationContent` y `shouldPropagateFailureWithoutHiddenRetry`.
  - Añadir caso de interrupción que compruebe restauración del interrupt flag y limpieza posterior del hilo de test.
  - Verificar que los errores nunca contienen el payload ni `messages[].content`.
  - Ejecutar `./mvnw test -Dtest=RabbitMqConversationAuditPublisherTest`.
  - **Cubre:** R2, R4, R15, R16, R17, R18, R20, R24, R28, R29.

- [ ] **T8 — Probar binding de propiedades y conexión externa.**
  - Crear `AuditMessagingPropertiesTest` con `ApplicationContextRunner` para defaults y overrides.
  - Implementar `shouldUseFiveSecondDefaultConfirmTimeout`, `shouldBindCustomConfirmTimeout`, `shouldUseOneMegabyteDefaultPayloadLimit` y `shouldBindCustomPayloadLimit`.
  - Crear `ApplicationRabbitMqConfigurationTest#shouldBindBrokerConnectionFromEnvironment` sin usar credenciales reales.
  - Ejecutar `./mvnw test -Dtest=AuditMessagingPropertiesTest,ApplicationRabbitMqConfigurationTest`.
  - **Cubre:** R21, R22, R23.

- [ ] **T9 — Probar límites arquitectónicos.**
  - Crear `ArchitectureRulesTest` con ArchUnit.
  - Implementar `shouldNotExposeAuditRequestHttpEndpoint`, `shouldKeepRabbitMqTypesInsideAdapterBoundary` y `shouldKeepPublicationIndependentFromAuditProcessing`.
  - Verificar que DTO, servicio y validador no dependan de Spring AMQP ni cliente RabbitMQ.
  - Ejecutar `./mvnw test -Dtest=ArchitectureRulesTest`.
  - **Cubre:** R25, R26, R27.

- [ ] **T10 — Probar la topología con RabbitMQ real aislado.**
  - Crear `RabbitMqTopologyIntegrationTest` con Testcontainers e imagen fijada `rabbitmq:4.1.0-management`.
  - Configurar conexión mediante `@DynamicPropertySource` y no depender de broker externo.
  - Implementar `shouldDeclareDurableDirectExchange`, `shouldDeclareDurableAuditQueue` y `shouldBindAuditQueueWithRequestedRoutingKey`.
  - Aislar/purgar recursos entre pruebas.
  - Ejecutar `./mvnw test -Dtest=RabbitMqTopologyIntegrationTest` con Docker disponible.
  - **Cubre:** R5, R6, R7.

- [ ] **T11 — Probar publicación, routing y persistencia con RabbitMQ.**
  - Crear `RabbitMqConversationAuditPublisherIntegrationTest` reutilizando una infraestructura Testcontainers aislada.
  - Implementar `shouldRouteValidRequestToAuditQueue`, `shouldPublishVersionOneHeadersAndBody`, `shouldKeepPersistentMessageUntilConsumed` y `shouldFailWhenMandatoryMessageIsUnroutable`.
  - Verificar JSON, headers, exchange/routing, `deliveryMode=2`, conteo en cola sin consumidor y consumo posterior.
  - Ejecutar `./mvnw test -Dtest=RabbitMqConversationAuditPublisherIntegrationTest` con Docker disponible.
  - **Cubre:** R1, R3, R4, R8, R16, R19.

- [ ] **T12 — Documentar el contrato operativo.**
  - Actualizar `docs/data-model.md` con el evento `conversation.audit.requested` versión `1.0`, tabla completa de campos, propiedades AMQP, reglas de evolución y semántica at-least-once.
  - Documentar variables de entorno RabbitMQ, defaults no sensibles, límite y timeout sin duplicar secretos.
  - Confirmar que no se documenta consumo, DLQ o procesamiento como parte de US-01.
  - **Cubre:** R2, R3, R4, R21, R22, R23, R28.

- [ ] **T13 — Ejecutar verificación final y registrar trazabilidad.**
  - Ejecutar `./mvnw test` y después `./mvnw clean verify`; ambos deben finalizar con código `0` y `BUILD SUCCESS`.
  - Crear `progress/impl_aud_push_msg.md` con resumen, comandos/evidencia y matriz completa R1–R29 → métodos de test.
  - Revisar que no haya cambios en `controller`, `analyzer`, `model` o `repository`, ni payloads/credenciales en código o logs.
  - Marcar las tareas anteriores `[x]` únicamente con evidencia verde; dejar esta tarea pendiente si Docker o cualquier prueba falla.
  - **Cubre:** R1–R29.

## Comprobación de cobertura de tareas

| Requirements | Tareas principales |
|---|---|
| R1–R4 | T2, T5, T7, T11, T12 |
| R5–R8 | T4, T10, T11 |
| R9–R14 | T2, T3, T6 |
| R15–R20 | T3, T5, T7, T11 |
| R21–R24 | T1, T4, T5, T7, T8, T12 |
| R25–R29 | T1, T3, T5, T7, T9, T12 |
| R1–R29 cierre | T13 |
