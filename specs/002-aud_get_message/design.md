# Design — US-02 Consumir mensajes de auditoría

## 1. Resumen

Se implementará el lado consumidor del contrato que US-01 ya publica. Un listener AMQP recibe el mensaje crudo de `conversation.audit.queue`, comprueba tamaño y guardia de redelivery, deserializa con el `ObjectMapper` de Jackson 2 existente y delega la admisión en un servicio transaccional que valida el contrato, reserva el `chatId` mediante una restricción única e inscribe la auditoría en estado inicial.

La feature termina cuando la conversación queda admitida y persistida en `audit_result`. No ejecuta analyzers, no llama a Ollama ni Spring AI, no expone endpoints y no promueve ningún registro a `COMPLETED`.

Esta historia es la primera que necesita persistencia, por lo que reintroduce JPA con datasource, migración Flyway y perfil `local` autocontenido.

## 2. Flujo de consumo

```text
conversation.audit.queue
    |
    v
ConversationAuditMessageListener            [integration.rabbitmq]
    | MDC(chatId, eventId)
    | 1. tamaño <= auditbot.messaging.max-payload-size        (R24, R25)
    | 2. guardia x-death / redelivered                        (R48)
    | 3. AuditMessageDeserializer.deserialize(body)            (R8, R41)
    | 4. lectura del header x-audit-retry                      (R33)
    v
IConversationAuditIntakeService.admit(request, retryRequested) [service]
    |
    v
ConversationAuditIntakeServiceImpl.admit(...)  @Transactional  (R39)
    | a. ConversationAuditRequestValidator.validate(request)    (R13, R14)
    | b. AuditResultMapper.toClaim(request) -> AuditResult      (R26)
    | c. IAuditResultRepository.saveAndFlush(claim)             (R28, R29)
    |      +-- DataIntegrityViolationException -> duplicado     (R28)
    | d. resolución de duplicado / reintento explícito          (R30, R33, R35)
    | e. PENDING -> PROCESSING                                  (R36, R37)
    | f. IAuditPipelineTrigger.trigger(chatId)  [no-op]         (R59)
    v
AuditIntakeResultDto (ADMITTED | DUPLICATE_SKIPPED | RETRY_ADMITTED)
    |
    v
ConversationAuditMessageListener
    +--> retorno normal ......... basicAck del contenedor       (R3, R12)
    +--> InvalidAuditMessageException
    |        -> AuditMessageDeadLetterPublisher (headers)       (R22, R23)
    |        -> AmqpRejectAndDontRequeueException               (R43, R44)
    +--> AuditProcessingException
             -> se propaga: retry acotado del contenedor        (R45)
             -> agotado: DLX -> conversation.audit.dlq          (R46, R47)
```

### Frontera de cada capa

| Capa | Paquete | Puede | No puede |
|---|---|---|---|
| Listener / adaptador | `api.integration.rabbitmq` | usar Spring AMQP y el cliente RabbitMQ, leer headers, deserializar, clasificar excepciones, publicar a la DLQ | contener reglas de negocio, tocar repositorios, abrir transacciones (R10, R40) |
| Servicio | `api.service`, `api.service.impl` | validar, orquestar el claim, decidir duplicado/reintento, abrir la transacción | importar Spring AMQP o el cliente RabbitMQ (R61) |
| Repositorio | `api.repository` | usar Spring Data JPA | contener lógica de decisión o anotaciones `@Transactional` (R40) |
| Modelo | `api.model` | usar anotaciones JPA | contener lógica (contenedor de datos puro) |
| Mapper | `api.mapper` | convertir DTO → entidad de claim | copiar `messages[].content` a la entidad (R53) |

El flujo respeta `docs/structure.md`: Listener (adaptador) → Service → Repository, sin saltos de capa. No se toca `controller` (R60) ni `analyzer` (R58).

## 3. Archivos

### 3.1 Crear — producción

| Ruta | Responsabilidad |
|---|---|
| `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/ConversationAuditMessageListener.java` | `@Component` con `@RabbitListener`. Adaptador delgado: MDC, límite de tamaño, guardia de redelivery, deserialización, delegación y clasificación de excepciones. Inyección por constructor. |
| `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessageDeadLetterPublisher.java` | Envía a `auditbot.dlx` / `conversation.audit.failed` con los headers de diagnóstico. |
| `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessageDeserializer.java` | Envuelve el `ObjectMapper` de Jackson 2 y traduce todo fallo a `InvalidAuditMessageException`. |
| `src/main/java/ec/com/technoloqie/auditbot/api/service/IConversationAuditIntakeService.java` | Puerto de admisión, sin tipos AMQP. |
| `src/main/java/ec/com/technoloqie/auditbot/api/service/impl/ConversationAuditIntakeServiceImpl.java` | `@Service` con el único método `@Transactional` de la feature. |
| `src/main/java/ec/com/technoloqie/auditbot/api/service/IAuditPipelineTrigger.java` | Punto de extensión para US-03 y siguientes. |
| `src/main/java/ec/com/technoloqie/auditbot/api/service/impl/NoOpAuditPipelineTrigger.java` | Implementación no-op documentada. |
| `src/main/java/ec/com/technoloqie/auditbot/api/model/AuditResult.java` | `@Entity` de `audit_result`. |
| `src/main/java/ec/com/technoloqie/auditbot/api/model/AuditStatus.java` | Enum `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. |
| `src/main/java/ec/com/technoloqie/auditbot/api/repository/IAuditResultRepository.java` | `JpaRepository<AuditResult, String>` con `findByChatId`. |
| `src/main/java/ec/com/technoloqie/auditbot/api/mapper/AuditResultMapper.java` | Mapper manual DTO → entidad de claim. |
| `src/main/java/ec/com/technoloqie/auditbot/api/dto/AuditIntakeResultDto.java` | Record con el desenlace de la admisión. |
| `src/main/java/ec/com/technoloqie/auditbot/api/commons/exception/InvalidAuditMessageException.java` | Error permanente. |
| `src/main/java/ec/com/technoloqie/auditbot/api/commons/exception/AuditProcessingException.java` | Error transitorio. |
| `src/main/java/ec/com/technoloqie/auditbot/api/commons/util/AuditMessageHeaders.java` | Constantes `UPPER_SNAKE_CASE` de los headers AMQP. |
| `src/main/resources/db/migration/V1__create_audit_result.sql` | Migración de la tabla de control. |
| `src/main/resources/application-local.properties` | Datasource local y ajustes de desarrollo. |

### 3.2 Modificar

| Ruta | Cambio |
|---|---|
| `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/RabbitMqConfiguration.java` | Añadir `auditDeadLetterExchange`, `auditDeadLetterQueue`, `auditDeadLetterBinding`; redeclarar el bean `auditQueue` con `QueueBuilder` y los argumentos de dead-letter. No tocar `auditExchange`, `auditRequestBinding`, `conversationAuditRequestValidator`, `auditPublicationClock` ni `auditObjectMapper`. |
| `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessagingProperties.java` | Añadir `deadLetterExchange`, `deadLetterQueue`, `deadLetterRoutingKey` y `maxRedeliveries` con getters/setters y sus comprobaciones en `validate()`. |
| `pom.xml` | Descomentar `spring-boot-starter-data-jpa`; añadir `com.mysql:mysql-connector-j` (`runtime`), `org.flywaydb:flyway-core`, `org.flywaydb:flyway-mysql` y `com.h2database:h2` (`test`), con versiones gestionadas por el parent Spring Boot `4.0.7`. |
| `src/main/resources/application.properties` | Claves del listener, dead-letter, JPA y Flyway (§8.1). |
| `src/main/resources/application-prod.properties` | Datasource MySQL por variables de entorno y `ddl-auto=validate` (§8.2). |
| `src/test/java/ec/com/technoloqie/auditbot/api/ArchitectureRulesTest.java` | Añadir las reglas nuevas de frontera y de flujo de consumo (§9.1). |
| `src/test/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessagingPropertiesTest.java` | Añadir defaults y overrides de las propiedades nuevas. |
| `src/test/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/ApplicationRabbitMqConfigurationTest.java` | Añadir binding del contenedor de listeners y de su retry. |
| `docs/data-model.md` | Documentar entidad de control, catálogo de estados, semántica de idempotencia, header de reintento y topología DLX/DLQ. |

### 3.3 No modificar

- `ConversationAuditRequestDto`, `AuditConversationDto`, `AuditMessageDto` (contrato `1.0` estable).
- `ConversationAuditRequestValidator`, `IConversationAuditPublisher`, `RabbitMqConversationAuditPublisher`.
- `AuditChatRestController`, `TecAuditbotWsApplication`.
- `specs/001-aud_push_msg/**`.

## 4. Firmas nuevas

### 4.1 Listener

```java
@Slf4j
@Component
public class ConversationAuditMessageListener {

    public ConversationAuditMessageListener(
            AuditMessageDeserializer deserializer,
            IConversationAuditIntakeService intakeService,
            AuditMessageDeadLetterPublisher deadLetterPublisher,
            AuditMessagingProperties properties);

    @RabbitListener(id = "conversationAuditListener", queues = "#{auditQueue.name}")
    public void onMessage(Message message);
}
```

`queues = "#{auditQueue.name}"` resuelve el nombre desde el bean `auditQueue`, alimentado por `auditbot.messaging.queue`; el literal no aparece en el código (R1, R2).

### 4.2 Deserializador y publicador de DLQ

```java
@Component
public class AuditMessageDeserializer {
    public AuditMessageDeserializer(ObjectMapper objectMapper);
    public ConversationAuditRequestDto deserialize(byte[] body);
}

@Component
public class AuditMessageDeadLetterPublisher {
    public AuditMessageDeadLetterPublisher(RabbitTemplate rabbitTemplate,
            AuditMessagingProperties properties, Clock clock);
    public void publish(Message original, String failureReason, List<String> invalidFields);
}
```

`AuditMessageDeserializer` recibe el bean `auditObjectMapper` ya existente (Jackson 2 con `JavaTimeModule`). `AuditMessageDeadLetterPublisher` reutiliza el bean `auditPublicationClock` para `x-audit-failed-at`.

### 4.3 Servicio de admisión

```java
public interface IConversationAuditIntakeService {
    AuditIntakeResultDto admit(ConversationAuditRequestDto request, boolean retryRequested);
}

@Slf4j
@Service
public class ConversationAuditIntakeServiceImpl implements IConversationAuditIntakeService {

    public ConversationAuditIntakeServiceImpl(
            IAuditResultRepository auditResultRepository,
            ConversationAuditRequestValidator validator,
            AuditResultMapper auditResultMapper,
            IAuditPipelineTrigger auditPipelineTrigger,
            Clock clock);

    @Override
    @Transactional
    public AuditIntakeResultDto admit(ConversationAuditRequestDto request, boolean retryRequested);
}
```

### 4.4 Punto de extensión

```java
public interface IAuditPipelineTrigger {
    void trigger(String chatId);
}

@Slf4j
@Component
public class NoOpAuditPipelineTrigger implements IAuditPipelineTrigger {
    @Override
    public void trigger(String chatId);
}
```

`NoOpAuditPipelineTrigger` solo registra a nivel `DEBUG` que el pipeline aún no existe. Su Javadoc indica que US-03 la sustituye.

### 4.5 Modelo, repositorio y mapper

```java
public enum AuditStatus { PENDING, PROCESSING, COMPLETED, FAILED }

@Entity
@Table(name = "audit_result",
        uniqueConstraints = @UniqueConstraint(name = "uk_audit_result_chat_id",
                columnNames = "chat_id"))
public class AuditResult { /* getters y setters, sin lógica */ }

public interface IAuditResultRepository extends JpaRepository<AuditResult, String> {
    Optional<AuditResult> findByChatId(String chatId);
}

@Component
public class AuditResultMapper {
    public AuditResult toClaim(ConversationAuditRequestDto request, Instant now);
}
```

### 4.6 DTO de resultado y constantes

```java
public record AuditIntakeResultDto(AuditIntakeOutcome outcome, String chatId, String auditId) {
    public enum AuditIntakeOutcome { ADMITTED, DUPLICATE_SKIPPED, RETRY_ADMITTED }
}

public final class AuditMessageHeaders {
    public static final String X_AUDIT_RETRY = "x-audit-retry";
    public static final String X_AUDIT_FAILURE_REASON = "x-audit-failure-reason";
    public static final String X_AUDIT_INVALID_FIELDS = "x-audit-invalid-fields";
    public static final String X_AUDIT_FAILED_AT = "x-audit-failed-at";
    public static final String X_DEATH = "x-death";

    private AuditMessageHeaders() { }
}
```

### 4.7 Propiedades ampliadas

```java
@ConfigurationProperties(prefix = "auditbot.messaging")
public class AuditMessagingProperties {
    // existentes: exchange, routingKey, queue, confirmTimeout, maxPayloadSize
    private String deadLetterExchange = "auditbot.dlx";
    private String deadLetterQueue = "conversation.audit.dlq";
    private String deadLetterRoutingKey = "conversation.audit.failed";
    private int maxRedeliveries = 3;
    // getters, setters y comprobaciones adicionales en validate()
}
```

`validate()` añade: los tres nombres de dead-letter no pueden estar en blanco y `maxRedeliveries` debe ser mayor que cero.

## 5. Excepciones

### 5.1 Reutilizadas

- `InvalidAuditRequestException` (`api.commons.exception`): la lanza `ConversationAuditRequestValidator` con las rutas de campos inválidos. En el consumo se envuelve como causa de un error permanente y sus rutas alimentan el header `x-audit-invalid-fields`.
- `AuditPublicationException`: pertenece al lado productor y **no** se usa en el consumo.

### 5.2 Nuevas

```java
public final class InvalidAuditMessageException extends RuntimeException {
    public InvalidAuditMessageException(String reason);
    public InvalidAuditMessageException(String reason, List<String> invalidFields, Throwable cause);
    public String getReason();
    public List<String> getInvalidFields();
}

public final class AuditProcessingException extends RuntimeException {
    public AuditProcessingException(String chatId, String reason, Throwable cause);
    public String getChatId();
}
```

Ninguna almacena el payload ni `messages[].content`.

### 5.3 Criterio de clasificación

| Criterio | Permanente (`InvalidAuditMessageException`) | Transitorio (`AuditProcessingException`) |
|---|---|---|
| Naturaleza | El mismo mensaje nunca será válido | El mismo mensaje puede tener éxito más tarde |
| Origen típico | JSON malformado, esquema inválido, payload sobredimensionado, `schemaVersion` no soportada | base de datos no disponible, timeout de conexión, deadlock, fallo de infraestructura |
| Traducción en el listener | `AmqpRejectAndDontRequeueException` | se propaga tal cual |
| Efecto AMQP | `basicNack(requeue=false)` inmediato hacia la DLX | retry acotado del contenedor y, si se agota, DLX |
| Intentos consumidos | ninguno | hasta `max-attempts=3` |
| Log | `WARN` | `ERROR` al agotarse |

Regla operativa: cualquier excepción que herede de `DataAccessException` se envuelve como transitoria; el resto de fallos originados en la validación o la deserialización son permanentes.

### 5.4 Tabla de manejo de errores

| Condición | Resultado |
|---|---|
| Cuerpo mayor que `max-payload-size` | DLQ inmediata, sin deserializar, sin claim |
| JSON no deserializable | DLQ inmediata, sin claim |
| Validador rechaza el contrato | DLQ inmediata con `x-audit-invalid-fields`, sin claim |
| `x-death` alcanza `max-redeliveries` | DLQ inmediata, sin procesar |
| `chatId` duplicado sin reintento | descarte con `WARN`, acuse normal, sin DLQ |
| `chatId` en `PROCESSING` con `x-audit-retry=true` | descarte con `WARN`, acuse normal, sin DLQ |
| Fallo de acceso a datos | `AuditProcessingException`, retry acotado |
| Retry agotado con claim creado | registro en `FAILED` con `error_message`, mensaje en DLQ |
| Admisión correcta | registro en `PROCESSING`, log `INFO`, acuse |

## 6. Topología AMQP resultante

| Elemento | Valor | Propiedad |
|---|---|---|
| Exchange principal | `auditbot.exchange` (direct, durable, no auto-delete) | `auditbot.messaging.exchange` |
| Cola principal | `conversation.audit.queue` (durable, no exclusiva, no auto-delete) | `auditbot.messaging.queue` |
| Routing key principal | `conversation.audit.requested` | `auditbot.messaging.routing-key` |
| Dead letter exchange | `auditbot.dlx` (direct, durable, no auto-delete) | `auditbot.messaging.dead-letter-exchange` |
| Dead letter queue | `conversation.audit.dlq` (durable, no exclusiva, no auto-delete) | `auditbot.messaging.dead-letter-queue` |
| Dead letter routing key | `conversation.audit.failed` | `auditbot.messaging.dead-letter-routing-key` |

Declaración de la cola principal:

```java
@Bean
public Queue auditQueue(AuditMessagingProperties properties) {
    properties.validate();
    return QueueBuilder.durable(properties.getQueue())
            .withArgument("x-dead-letter-exchange", properties.getDeadLetterExchange())
            .withArgument("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey())
            .build();
}
```

`conversation.audit.dlq` se declara sin argumentos de dead-letter: es una cola de parking y ningún mensaje puede volver por sí solo al flujo de consumo (R21).

### Headers de diagnóstico

| Header | Contenido |
|---|---|
| `x-audit-failure-reason` | razón técnica corta (`MALFORMED_JSON`, `INVALID_SCHEMA`, `OVERSIZED_PAYLOAD`, `REDELIVERY_LIMIT_REACHED`, `RETRIES_EXHAUSTED`) |
| `x-audit-invalid-fields` | rutas de campos inválidos separadas por coma, solo cuando el validador las reporta |
| `x-audit-failed-at` | instante ISO-8601 UTC del descarte |

Ningún header transporta el cuerpo completo ni `messages[].content`.

## 7. Modelo de datos

### 7.1 Entidad `AuditResult`

| Campo Java | Columna | Tipo | Reglas |
|---|---|---|---|
| `auditId` | `audit_id` | `CHAR(36)` PK | UUID generado en la admisión |
| `chatId` | `chat_id` | `VARCHAR(128)` | obligatorio, único (`uk_audit_result_chat_id`) |
| `assistantId` | `assistant_id` | `VARCHAR(128)` | obligatorio, tomado de `conversation.assistantId` |
| `status` | `status` | `VARCHAR(20)` | obligatorio, `@Enumerated(EnumType.STRING)` |
| `eventId` | `event_id` | `CHAR(36)` | último `eventId` que produjo una admisión |
| `attemptCount` | `attempt_count` | `INT` | mayor o igual a 1 |
| `errorMessage` | `error_message` | `VARCHAR(500)` NULL | solo con `status = FAILED`, truncado a 500 caracteres |
| `frustrationDetected` | `frustration_detected` | `TINYINT(1)` NULL | la escribe US-04 |
| `lostLeadDetected` | `lost_lead_detected` | `TINYINT(1)` NULL | la escribe US-05 |
| `leadScore` | `lead_score` | `DECIMAL(4,3)` NULL | la escribe US-05 |
| `gapsCount` | `gaps_count` | `INT` NULL | la escribe US-03 |
| `createdDate` | `created_date` | `DATETIME(6)` | obligatorio, primera admisión |
| `updatedDate` | `updated_date` | `DATETIME(6)` | última transición de estado |
| `auditedDate` | `audited_date` | `DATETIME(6)` NULL | la escribe el pipeline al completar |

Decisión: las cuatro columnas de US-03 a US-05 se declaran ya como nullable para evitar una migración por historia, como se dejó planteado en `progress/current.md`. US-02 no las escribe. Las relaciones `auditGaps`, `auditSentiments` y `auditRecommendations` de `docs/data-model.md` se añadirán en las historias que creen esas entidades.

### 7.2 Enum `AuditStatus`

`PENDING` → `PROCESSING` son las únicas transiciones que escribe US-02, más `PROCESSING` → `FAILED` cuando el mensaje termina aparcado. `COMPLETED` queda declarado en el catálogo para US-06 y US-08, pero US-02 nunca lo establece (R38).

### 7.3 Migración Flyway

`src/main/resources/db/migration/V1__create_audit_result.sql`:

- `CREATE TABLE audit_result` con las columnas de §7.1.
- `CONSTRAINT uk_audit_result_chat_id UNIQUE (chat_id)`.
- `INDEX idx_audit_result_status (status)` para US-07 y US-08.
- Sin datos semilla.

Flyway gestiona el esquema; Hibernate solo lo valida (R70, R71).

## 8. Configuración

### 8.1 `application.properties` (añadir)

```properties
#messaging - consumo de auditoria (US-02)
auditbot.messaging.dead-letter-exchange=auditbot.dlx
auditbot.messaging.dead-letter-queue=conversation.audit.dlq
auditbot.messaging.dead-letter-routing-key=conversation.audit.failed
auditbot.messaging.max-redeliveries=3

spring.rabbitmq.listener.simple.acknowledge-mode=auto
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.concurrency=2
spring.rabbitmq.listener.simple.max-concurrency=4
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.retry.initial-interval=1s
spring.rabbitmq.listener.simple.retry.multiplier=2
spring.rabbitmq.listener.simple.retry.max-interval=10s

#persistencia (US-02)
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

Las claves de publicación de US-01 (`publisher-confirm-type`, `publisher-returns`, `template.mandatory`, `template.retry.enabled`) y `auditbot.messaging.max-payload-size` no se modifican: el consumidor comparte el mismo límite de tamaño que el productor (R68).

### 8.2 `application-prod.properties` (añadir)

```properties
#MySQL - conexion externa (US-02)
spring.datasource.url=${MYSQL_URL}
spring.datasource.username=${MYSQL_USERNAME}
spring.datasource.password=${MYSQL_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
```

### 8.3 `application-local.properties` (crear)

```properties
spring.datasource.url=${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/auditbot?useSSL=false&serverTimezone=UTC}
spring.datasource.username=${MYSQL_USERNAME:auditbot}
spring.datasource.password=${MYSQL_PASSWORD:auditbot}
spring.jpa.hibernate.ddl-auto=validate
```

Ningún valor real de credencial se versiona; los defaults locales son de desarrollo y así quedan documentados.

### 8.4 `pom.xml`

Cambios descritos, no aplicados en esta fase:

- descomentar `org.springframework.boot:spring-boot-starter-data-jpa`;
- añadir `com.mysql:mysql-connector-j` con scope `runtime`;
- añadir `org.flywaydb:flyway-core` y `org.flywaydb:flyway-mysql`;
- añadir `com.h2database:h2` con scope `test`.

Todas con versión gestionada por el parent `spring-boot-starter-parent:4.0.7`. No se añade MapStruct ni ninguna dependencia adicional.

### 8.5 Paso operativo obligatorio — migración de la cola

`conversation.audit.queue` ya existe en el broker declarada **sin** argumentos de dead-letter. Antes del primer arranque con los cambios de US-02:

1. verificar en la consola de management que la cola está vacía (`messages = 0`);
2. eliminar la cola;
3. arrancar la aplicación para que `RabbitAdmin` la redeclare con los argumentos nuevos, o declararla manualmente con `x-dead-letter-exchange=auditbot.dlx` y `x-dead-letter-routing-key=conversation.audit.failed`.

Si se omite, la declaración falla con `PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'` (406) y el contenedor de listeners no arranca.

## 9. Estrategia de pruebas

Framework: JUnit 5 y Mockito para lo unitario, `@DataJpaTest` con H2 para el repositorio y Testcontainers RabbitMQ para mensajería, según `docs/verification.md`.

### 9.1 Unitarias

| Clase | Métodos |
|---|---|
| `ConversationAuditMessageListenerTest` | `shouldDelegateValidMessageToIntakeService`, `shouldRejectMalformedJsonWithoutRequeue`, `shouldRejectInvalidSchemaWithoutRequeue`, `shouldRejectOversizedPayloadWithoutRequeue`, `shouldRethrowTransientErrorForBoundedRetry`, `shouldRouteToDlqWhenRedeliveryLimitReached`, `shouldPropagateRetryHeaderToIntakeService`, `shouldNotAccessRepositoryOrOpenTransaction`, `shouldClearMdcAfterEachMessage`, `shouldLogMetadataWithoutConversationContent`, `shouldLogEachOutcomeWithItsLevel`, `shouldLogDuplicateOutcomeAtWarnLevel`, `shouldNotAcknowledgeWhenClaimIsNotConfirmed` |
| `ConversationAuditIntakeServiceImplTest` | `shouldAdmitNewChatIdWithPendingThenProcessing`, `shouldSkipAlreadyProcessedChatId`, `shouldTreatUniqueConstraintViolationAsDuplicate`, `shouldClaimChatIdWithoutPreliminaryExistenceQuery`, `shouldReprocessFailedChatIdWhenRetryRequested`, `shouldIncrementAttemptCountOnExplicitRetry`, `shouldRejectRetryWhileChatIdIsProcessing`, `shouldValidateContractBeforeClaim`, `shouldWrapDataAccessFailureAsTransientError`, `shouldMarkAuditAsFailedWithReadableMessage`, `shouldTruncateErrorMessageToFiveHundredCharacters`, `shouldNeverSetCompletedStatus`, `shouldNotInvokeAnalyzers` |
| `AuditMessageDeserializerTest` | `shouldDeserializeVersionOneContract`, `shouldPreserveInstantsInUtc`, `shouldWrapMalformedJsonAsPermanentError` |
| `AuditResultMapperTest` | `shouldMapConversationToClaimEntity`, `shouldNotCopyMessageContentIntoEntity` |
| `NoOpAuditPipelineTriggerTest` | `shouldDoNothingOnTrigger` |
| `AuditMessagingPropertiesTest` (ampliar) | `shouldUseDefaultDeadLetterTopology`, `shouldBindCustomDeadLetterTopology`, `shouldUseThreeDefaultMaxRedeliveries`; se conserva el método existente `shouldUseOneMegabyteDefaultPayloadLimit`, que cubre el límite de tamaño compartido con el productor |
| `ApplicationRabbitMqConfigurationTest` (ampliar) | `shouldBindListenerContainerSettings`, `shouldOverrideListenerContainerSettings`, `shouldBindListenerRetrySettings` |
| `ApplicationPersistenceConfigurationTest` (nueva) | `shouldBindDatasourceFromEnvironment`, `shouldResolveLocalDatasourceDefaults`, `shouldDisableOpenInView`, `shouldEnableFlywayMigrations`, `shouldValidateSchemaWithoutDdlGeneration` |
| `ArchitectureRulesTest` (ampliar) | `shouldKeepJpaTypesInsideModelAndRepository`, `shouldKeepListenerFreeOfBusinessLogic`, `shouldKeepConsumptionIndependentFromAnalyzers`, `shouldNotExposeAuditIntakeHttpEndpoint`, `shouldKeepTransactionalInsideServiceLayer`, `shouldNotUseConsoleLoggingInConsumptionFlow`, `shouldReuseContractValidatorInConsumption` |

Los métodos ya existentes de `ArchitectureRulesTest` (`shouldKeepRabbitMqTypesInsideAdapterBoundary` entre ellos) se mantienen sin cambios y siguen cubriendo la frontera AMQP.

### 9.2 Integración

| Clase | Métodos |
|---|---|
| `AuditResultRepositoryTest` (`@DataJpaTest` con H2) | `shouldPersistAndFindAuditByChatId`, `shouldRejectDuplicatedChatId` |
| `RabbitMqDeadLetterTopologyIntegrationTest` (Testcontainers) | `shouldDeclareDurableDeadLetterExchange`, `shouldDeclareDurableDeadLetterQueue`, `shouldBindDeadLetterQueueWithFailedRoutingKey`, `shouldDeclareMainQueueWithDeadLetterArguments` |
| `ConversationAuditMessageListenerIntegrationTest` (Testcontainers y `@SpringBootTest`) | `shouldConsumeValidMessageAndPersistAudit`, `shouldConsumeFromConfiguredQueueName`, `shouldRouteInvalidSchemaMessageToDlq`, `shouldRouteMalformedJsonToDlq`, `shouldNotPersistAnythingWhenSchemaIsInvalid`, `shouldPublishDiagnosticHeadersOnDeadLetter`, `shouldNotReprocessDuplicatedChatId`, `shouldReprocessWhenRetryHeaderIsPresent`, `shouldStopRedeliveryAfterBoundedRetries`, `shouldLeaveMainQueueEmptyAfterSuccessfulConsumption`, `shouldAdmitMessageUnderLatencyBudget` |

Los tests de mensajería reutilizan `RabbitMqConversationAuditPublisher` para inyectar mensajes reales, declaran recursos sufijados con `UUID` por ejecución y los limpian en `@AfterAll`.

### 9.3 Desviación de entorno conocida

Docker no está accesible en el entorno de desarrollo actual (`docker.sock` permission denied), tal como quedó registrado en las desviaciones T10 y T11 de `specs/001-aud_push_msg/tasks.md` y en `progress/impl_aud_push_msg.md`. Testcontainers se mantiene como objetivo; si Docker sigue sin estar disponible se aplica el mismo fallback ya aprobado en US-01: broker real en `127.0.0.1:5672`, recursos sufijados por `UUID` y limpieza en `@AfterAll`, documentando la desviación en `tasks.md` y en `progress/impl_aud_get_message.md`.

### 9.4 Evidencia

```bash
PROFILE=local ./mvnw test
PROFILE=local ./mvnw clean verify
```

Ambos deben terminar en `BUILD SUCCESS`. La variable `PROFILE` es obligatoria por `spring.profiles.active=${PROFILE}` en `application.properties`.

Smoke test manual de nivel 3: aplicación arriba con `PROFILE=local`, publicación de un mensaje válido y verificación del registro en `audit_result`; publicación de un mensaje inválido y verificación de que aparece en `conversation.audit.dlq` con los headers de diagnóstico y de que la cola principal queda vacía.

## 10. Riesgos operativos y mitigación

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Redeclaración de `conversation.audit.queue` con argumentos nuevos: `PRECONDITION_FAILED - inequivalent arg` (406) | El contenedor de listeners no arranca y la aplicación queda sin consumir | Paso operativo de §8.5 antes del primer arranque: verificar cola vacía, eliminarla y dejar que `RabbitAdmin` la redeclare. El paso se ejecuta primero en el entorno de pruebas y se documenta en `progress/impl_aud_get_message.md` |
| Reintroducción de JPA sin datasource previo: `Failed to determine a suitable driver class` al cargar el contexto (fallo real de US-01) | `TecAuditbotWsApplicationTests.contextLoads` y todo `@SpringBootTest` completo fallan al arrancar | Descomentar `spring-boot-starter-data-jpa` y, en el mismo cambio, añadir driver MySQL, Flyway, `application-local.properties` y H2 de test. La primera task de infraestructura no se cierra hasta que `contextLoads` vuelve a pasar |
| `spring.profiles.active=${PROFILE}` sin la variable de entorno definida y sin `application-local.properties` | Cualquier arranque o test de contexto completo falla antes de evaluar beans | Crear `application-local.properties` con defaults de desarrollo y ejecutar siempre con `PROFILE=local`; documentar la variable en `docs/data-model.md` y en las tasks de verificación |
| Docker no disponible para Testcontainers | Los tests de mensajería no pueden ejecutarse como exige `docs/verification.md` | Fallback ya aprobado en US-01 contra broker real con recursos sufijados por `UUID` y limpieza en `@AfterAll`, documentando la desviación |
| Bean `auditObjectMapper` (Jackson 2) sustituido o ignorado al introducir un `MessageConverter` | La deserialización de `Instant` vuelve a romperse y todo mensaje válido acaba en la DLQ | No se registra `Jackson2JsonMessageConverter`; el listener recibe el mensaje crudo y usa explícitamente el bean existente |
| DLQ con payloads completos accesible por terceros | Exposición de contenido de conversaciones en reposo | La DLQ se documenta como dato sensible y su acceso se restringe a la cuenta de servicio del broker; el cifrado de campos sigue fuera de alcance |
| `chatId` en `PROCESSING` tras un reinicio a mitad de proceso | Registro huérfano que nunca avanza | Estado observable documentado para US-08; el barrido de huérfanos queda para una historia posterior de operación |

## 11. Alternativas descartadas

### Ack manual con `Channel` y `deliveryTag`

Descartado. El contenedor hace `basicAck` cuando el método del listener retorna sin excepción y `basicNack` cuando lanza, y el claim de idempotencia se confirma en una transacción de base de datos independiente del canal AMQP: una redelivery por ack perdido queda absorbida por la idempotencia. El ack manual añadiría manejo explícito de canal y tag, más código y más formas de dejar mensajes sin acusar, sin ninguna garantía adicional.

### `Jackson2JsonMessageConverter` en lugar de deserialización manual

Descartado. Con conversor, un cuerpo malformado llega envuelto en `ListenerExecutionFailedException` desde la infraestructura, donde el enrutamiento a DLQ es menos explícito y más difícil de testear. Además Spring Boot 4 autoconfigura un `ObjectMapper` de Jackson 3 incompatible con el contrato: al deserializar manualmente con el bean `auditObjectMapper` de Jackson 2 el fallo entra en el mismo camino de clasificación de errores del listener.

### `existsByChatId()` previo al `save()`

Descartado. Con `concurrency > 1` o varias instancias del servicio, comprobar y luego insertar deja una ventana de condición de carrera. La restricción única `uk_audit_result_chat_id` es la única fuente de verdad atómica: se intenta insertar y la violación de unicidad se interpreta como duplicado.

### MapStruct para el mapeo DTO → entidad

Descartado. Añadir un segundo procesador de anotaciones junto a la configuración explícita de Lombok en `pom.xml` es riesgo innecesario para un mapeo de pocos campos. Se usa un mapper manual en `api.mapper`, testeable sin contexto Spring.

### Versionar la cola a `conversation.audit.queue.v2`

Descartado. Evitaría el `PRECONDITION_FAILED` sin paso operativo, pero rompería el contrato operativo ya publicado en `docs/data-model.md` y obligaría a cambiar el binding del productor de US-01, que esta historia no debe tocar. Se prefiere el paso de migración controlado de §8.5.

### Consumidor de la DLQ o replay automático

Descartado en US-02. El reproceso se limita al mecanismo explícito por mensaje con el header `x-audit-retry`; un consumidor de la DLQ reintroduciría el riesgo de bucles y pertenece a una historia de operación posterior.
