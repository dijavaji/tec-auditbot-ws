# Implementación US-01 — Publicar solicitud de auditoría genérica

## Resumen

Se implementó el puerto y adaptador de publicación de `conversation.audit.requested` hacia RabbitMQ, siguiendo `specs/001-aud_push_msg/{requirements,design,tasks}.md`. La feature cubre exclusivamente la publicación (contrato, validación, topología durable, publisher confirms/returns); no se implementó consumo, DLQ, analyzers ni endpoints HTTP.

Todas las tareas T1–T13 completadas con evidencia verde: `./mvnw test` y `./mvnw clean verify` finalizan con `BUILD SUCCESS`, 37/37 tests en verde.

## Archivos creados

- `src/main/java/ec/com/technoloqie/auditbot/api/dto/{ConversationAuditRequestDto,AuditConversationDto,AuditMessageDto}.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/service/IConversationAuditPublisher.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/service/ConversationAuditRequestValidator.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/commons/exception/{InvalidAuditRequestException,AuditPublicationException}.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/{AuditMessagingProperties,RabbitMqConfiguration,RabbitMqConversationAuditPublisher}.java`
- `src/test/java/ec/com/technoloqie/auditbot/api/service/ConversationAuditRequestValidatorTest.java`
- `src/test/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/{RabbitMqConversationAuditPublisherTest,AuditMessagingPropertiesTest,ApplicationRabbitMqConfigurationTest,RabbitMqTopologyIntegrationTest,RabbitMqConversationAuditPublisherIntegrationTest}.java`
- `src/test/java/ec/com/technoloqie/auditbot/api/ArchitectureRulesTest.java`

## Archivos modificados

- `pom.xml`: dependencias de test `testcontainers-rabbitmq:2.0.5`, `testcontainers junit-jupiter:1.20.4`, `archunit-junit5:1.4.1`; dependencia de producción `jackson-datatype-jsr310` (requerida para serializar `java.time.Instant`). `spring-boot-starter-data-jpa` comentada (decisión del usuario, fuera del alcance de US-01: el proyecto no tiene datasource configurado en ningún perfil).
- `src/main/resources/application.properties`: propiedades `auditbot.messaging.*` y publisher confirms/returns/mandatory.
- `src/main/resources/application-prod.properties`: conexión RabbitMQ vía variables de entorno.
- `docs/data-model.md`: sección de contrato de mensajería `conversation.audit.requested` v1.0.

## Comandos y evidencia

```bash
JAVA_HOME=<jdk17> PROFILE=local ./mvnw test
# Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS

JAVA_HOME=<jdk17> PROFILE=local ./mvnw clean verify
# Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS (jar empaquetado en target/tec-auditbot-ws-0.0.1-SNAPSHOT.jar)
```

Todos los tests específicos de US-01 (36 métodos en 7 clases) más el smoke test de la aplicación (`TecAuditbotWsApplicationTests`) pasan en verde:

| Clase | Métodos | Resultado |
|---|---|---|
| `ConversationAuditRequestValidatorTest` | 9 | 9/9 OK |
| `RabbitMqConversationAuditPublisherTest` | 11 | 11/11 OK |
| `AuditMessagingPropertiesTest` | 4 | 4/4 OK |
| `ApplicationRabbitMqConfigurationTest` | 1 | 1/1 OK |
| `ArchitectureRulesTest` | 4 | 4/4 OK |
| `RabbitMqTopologyIntegrationTest` | 3 | 3/3 OK |
| `RabbitMqConversationAuditPublisherIntegrationTest` | 4 | 4/4 OK |
| `TecAuditbotWsApplicationTests` (smoke test, no específico de US-01) | 1 | 1/1 OK |

## Desviaciones respecto al diseño original

### 1. Testcontainers/Docker no disponible

El diseño (`design.md` §7-8) especifica Testcontainers con imagen fijada `rabbitmq:4.1.0-management` para `RabbitMqTopologyIntegrationTest` y `RabbitMqConversationAuditPublisherIntegrationTest`. En este entorno, `docker.sock` no es accesible para el usuario del proceso (`permission denied`, usuario no pertenece al grupo `docker`).

Por indicación explícita del usuario, se usó en su lugar una instancia real de RabbitMQ ya levantada en el entorno (`127.0.0.1:5672`, management UI en `127.0.0.1:15672`, credenciales `rabbitmq`/`Password.1`). Ambas clases de integración:
- usan un contexto Spring mínimo (`RabbitAutoConfiguration` + `RabbitMqConfiguration`, sin cargar `TecAuditbotWsApplication` completa),
- declaran exchange/cola/routing-key con sufijo `UUID` único por ejecución para aislarse de otras pruebas,
- limpian sus recursos en `@AfterAll` (verificado manualmente contra la API de management que no quedan colas/exchanges huérfanos tras la ejecución).

Esta desviación no cambia la cobertura de R1, R3, R5, R6, R7, R8, R16, R19: se verifica contra un broker AMQP real, no simulado.

### 2. `TecAuditbotWsApplicationTests.contextLoads` — resuelto

El test de smoke `contextLoads` (existente antes de esta sesión) carga el contexto completo de Spring Boot, que incluía `spring-boot-starter-data-jpa`. El proyecto no tenía configurado ningún driver JDBC ni `spring.datasource.*` en ningún perfil, por lo que Hikari fallaba con `Failed to determine a suitable driver class`.

Se confirmó que este fallo era preexistente ejecutando el mismo test con `git stash` revirtiendo temporalmente los cambios de esta sesión: el fallo persistía de forma idéntica sin ninguno de los cambios de US-01.

**Resolución (decisión del usuario):** se comentó la dependencia `spring-boot-starter-data-jpa` en `pom.xml`, ya que ninguna feature implementada hasta ahora la usa. Esto es una decisión de infraestructura fuera del alcance funcional de US-01, aplicada explícitamente por el usuario.

Al resolver esto se destapó un segundo problema, también preexistente pero enmascarado por el anterior: Spring Boot 4 auto-configura por defecto un `ObjectMapper` de **Jackson 3** (`tools.jackson.databind.ObjectMapper`), no de Jackson 2. El código de este servicio usa `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2, requerido por `jackson-databind` y `jackson-datatype-jsr310` declarados explícitamente en `pom.xml`). Al cargar el contexto completo de la aplicación, Spring Boot no auto-configuraba ningún bean de ese tipo, y `RabbitMqConversationAuditPublisher` fallaba al inyectarlo.

**Resolución:** se agregó un bean `ObjectMapper` (Jackson 2, con `JavaTimeModule` registrado) en `RabbitMqConfiguration`, anotado con `@ConditionalOnMissingBean` para no interferir si el contexto de un test ya provee uno propio.

Adicionalmente se detectó y se trabajó alrededor de otro problema preexistente: `spring.profiles.active=${PROFILE}` (sin cambios, preexistente) requiere la variable de entorno `PROFILE` definida; sin ella, cualquier test `@SpringBootTest` con la aplicación completa falla en el arranque antes de llegar a evaluar cualquier bean. Se ejecutaron los comandos de verificación con `PROFILE=local`. Esta línea no se modificó porque está fuera del alcance de US-01.

**Conclusión:** con ambas resoluciones, `./mvnw test` y `./mvnw clean verify` finalizan con `BUILD SUCCESS` y 37/37 tests en verde.

## Trazabilidad Rn → test

| Requirement | Test |
|---|---|
| R1 | `RabbitMqConversationAuditPublisherIntegrationTest#shouldRouteValidRequestToAuditQueue` |
| R2 | `RabbitMqConversationAuditPublisherTest#shouldSerializeVersionOneContractExactlyOnce` |
| R3 | `ConversationAuditRequestValidatorTest#shouldRejectUnsupportedSchemaVersion`, `RabbitMqConversationAuditPublisherIntegrationTest#shouldPublishVersionOneHeadersAndBody` |
| R4 | `RabbitMqConversationAuditPublisherTest#shouldSetRequiredAmqpProperties` |
| R5 | `RabbitMqTopologyIntegrationTest#shouldDeclareDurableDirectExchange` |
| R6 | `RabbitMqTopologyIntegrationTest#shouldDeclareDurableAuditQueue` |
| R7 | `RabbitMqTopologyIntegrationTest#shouldBindAuditQueueWithRequestedRoutingKey` |
| R8 | `RabbitMqConversationAuditPublisherIntegrationTest#shouldKeepPersistentMessageUntilConsumed` |
| R9 | `ConversationAuditRequestValidatorTest#shouldRejectEveryMissingRequiredField` |
| R10 | `ConversationAuditRequestValidatorTest#shouldRejectUnsupportedMessageRole` |
| R11 | `ConversationAuditRequestValidatorTest#shouldRejectDuplicatedMessageIds` |
| R12 | `ConversationAuditRequestValidatorTest#shouldRejectMessagesOutOfOrder` |
| R13 | `ConversationAuditRequestValidatorTest#shouldRejectEndBeforeStart` |
| R14 | `ConversationAuditRequestValidatorTest#shouldRejectConversationWithoutMessages` |
| R15 | `RabbitMqConversationAuditPublisherTest#shouldRejectPayloadLargerThanConfiguredLimit` |
| R16 | `RabbitMqConversationAuditPublisherTest#shouldCompleteAfterPositiveConfirm` |
| R17 | `RabbitMqConversationAuditPublisherTest#shouldFailOnNegativeConfirm` |
| R18 | `RabbitMqConversationAuditPublisherTest#shouldFailWhenConfirmTimesOut` |
| R19 | `RabbitMqConversationAuditPublisherIntegrationTest#shouldFailWhenMandatoryMessageIsUnroutable` |
| R20 | `RabbitMqConversationAuditPublisherTest#shouldWrapSerializationFailure`, `#shouldWrapBrokerSendFailure` |
| R21 | `AuditMessagingPropertiesTest#shouldUseFiveSecondDefaultConfirmTimeout`, `#shouldBindCustomConfirmTimeout` |
| R22 | `AuditMessagingPropertiesTest#shouldUseOneMegabyteDefaultPayloadLimit`, `#shouldBindCustomPayloadLimit` |
| R23 | `ApplicationRabbitMqConfigurationTest#shouldBindBrokerConnectionFromEnvironment` |
| R24 | `RabbitMqConversationAuditPublisherTest#shouldLogMetadataWithoutConversationContent` |
| R25 | `ArchitectureRulesTest#shouldNotExposeAuditRequestHttpEndpoint` |
| R26 | `ArchitectureRulesTest#shouldKeepRabbitMqTypesInsideAdapterBoundary`, `#shouldKeepDtoServiceAndValidatorFreeOfSpringAmqpAndRabbitClient` |
| R27 | `ArchitectureRulesTest#shouldKeepPublicationIndependentFromAuditProcessing` |
| R28 | `RabbitMqConversationAuditPublisherTest#shouldPropagateFailureWithoutHiddenRetry` |
| R29 | `RabbitMqConversationAuditPublisherTest#shouldSerializeVersionOneContractExactlyOnce` |

## Revisión de límites

- No se modificó `controller/`, `analyzer/`, `model/` ni `repository/`.
- No se agregaron endpoints HTTP nuevos.
- No hay payloads ni credenciales en código fuente ni en logs (verificado por `RabbitMqConversationAuditPublisherTest#shouldLogMetadataWithoutConversationContent` y revisión manual de `application*.properties`).
